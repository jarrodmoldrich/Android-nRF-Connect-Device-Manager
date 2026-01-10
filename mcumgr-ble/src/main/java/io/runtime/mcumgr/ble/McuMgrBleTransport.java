/*
 * Copyright (c) 2018, Nordic Semiconductor
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by Jarrod Moldrich, 2026
 */

package io.runtime.mcumgr.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.runtime.mcumgr.McuMgrCallback;
import io.runtime.mcumgr.McuMgrHeader;
import io.runtime.mcumgr.McuMgrScheme;
import io.runtime.mcumgr.McuMgrTransport;
import io.runtime.mcumgr.ble.callback.SmpProtocolSession;
import io.runtime.mcumgr.ble.callback.SmpTransaction;
import io.runtime.mcumgr.ble.callback.TransactionTimeoutException;
import io.runtime.mcumgr.ble.exception.McuMgrDisconnectedException;
import io.runtime.mcumgr.ble.util.ResultCondition;
import io.runtime.mcumgr.exception.InsufficientMtuException;
import io.runtime.mcumgr.exception.McuMgrErrorException;
import io.runtime.mcumgr.exception.McuMgrException;
import io.runtime.mcumgr.exception.McuMgrTimeoutException;
import io.runtime.mcumgr.response.McuMgrResponse;
import io.runtime.mcumgr.util.CBOR;

/**
 * The McuMgrBleTransport is an implementation for the {@link McuMgrScheme#BLE} transport scheme.
 * <p>
 * <b>External BLE Management:</b> This transport assumes that BLE connection, service discovery,
 * and characteristic setup (including enabling notifications) are managed externally. The
 * {@link BluetoothGatt}, SMP characteristic, and negotiated MTU must be provided in the constructor.
 * <p>
 * Call {@link #didDisconnect()} when the connection is lost, and * {@link #didReconnect(int)} after
 * reconnection to resume operations. Forward SMP characteristic notifications via
 * {@link #handleNotification(byte[])}.
 */
@SuppressWarnings("unused")
public class McuMgrBleTransport implements McuMgrTransport {

    private static final Logger LOG = LoggerFactory.getLogger(McuMgrBleTransport.class);

    public interface SmpWriteCallback {
        void writeNoResponse(byte[] data);
    }

    /**
     * The SMP service UUID.
     *
     * @deprecated Use {@link DefaultMcuMgrUuidConfig#SMP_SERVICE_UUID} instead.
     */
    @Deprecated
    public final static UUID SMP_SERVICE_UUID = DefaultMcuMgrUuidConfig.SMP_SERVICE_UUID;

    /**
     * Simple Management Protocol write characteristic.
     */
    private SmpWriteCallback mSmpCharacteristicWrite;

    /**
     * The chunk size for splitting large payloads (MTU - 3 for ATT header).
     */
    private int mChunkSize;

    /**
     * The maximum packet length supported by the target device.
     * This may be greater than MTU size.
     * For packets longer than this value an {@link InsufficientMtuException} will be thrown.
     * Packets longer than MTU, but shorter than this value will be split.
     * Splitting packets must be supported by SMP Server on the target device.
     */
    private int mMaxPacketLength;

    /**
     * Flag indicating should low-level logging be enabled. Default to false.
     * Call {@link #setLoggingEnabled(boolean)} to change.
     */
    private boolean mLoggingEnabled;

    /**
     * The protocol layer session allows for asynchronous requests and responses
     * by using the sequence number to match transactions.
     * The session object is set when the device connects and the SMP service is
     * initialized. When the device disconnects, the protocol session is closed
     * and this variable is set to null.
     */
    private SmpProtocolSession mSmpProtocol;

    /**
     * The handler used for {@link SmpProtocolSession} callbacks.
     */
    private final Handler mHandler;

    /**
     * Construct a McuMgrBleTransport object with external BLE management.
     * <p>
     * Uses the main thread for callbacks.
     *
     * @param device the device to connect to and communicate with.
     * @param gatt the BluetoothGatt connection from the external BLE manager.
     * @param smpCharacteristic the SMP characteristic from service discovery.
     * @param mtu the negotiated MTU size from the external BLE connection.
     */
    public McuMgrBleTransport(@NonNull SmpWriteCallback smpCharacteristic, int mtu) {
        this(smpCharacteristic, mtu, new Handler(Looper.getMainLooper()));
    }

    /**
     * Construct a McuMgrBleTransport object with a handler for asynchronous callbacks.
     *
     * @param device the device to connect to and communicate with.
     * @param gatt the BluetoothGatt connection from the external BLE manager.
     * @param smpCharacteristic the SMP characteristic from service discovery.
     * @param mtu the negotiated MTU size from the external BLE connection.
     * @param handler the handler to run {@link McuMgrCallback}s on.
     */
    public McuMgrBleTransport(@NonNull SmpWriteCallback smpCharacteristic,
                              int mtu,
                              @NonNull Handler handler) {
        mSmpCharacteristicWrite = smpCharacteristic;
        mHandler = handler;
        initializeGatt(mtu);
    }

    //*******************************************************************************************
    // Maximum SMP packet length.
    //*******************************************************************************************

    /**
     * In order to send packets longer than MTU size, this library supports automatic segmentation
     * of packets into at-most-MTU size chunks. This feature must be also supported by the target
     * device, as it must reassembly received chunks into full SMP packet, based on the length field
     * from the {@link io.runtime.mcumgr.McuMgrHeader}, included in the first segment.
     * <p>
     * This method sets the maximum packet length supported by the target device.
     * By default, this is be set to MTU - 3, which means that each BLE packet will contain the full
     * SMP packet (header + CBOR-encoded data). For devices supporting reading McuMgr parameters
     * (nRF Connect SDK 2.0+) this value is automatically obtained after connection using
     * {@link DefaultManager#params()}.
     * <p>
     * Keep in mind, that before Android 5 requesting higher MTU was not supported. Setting the
     * maximum length to a greater value is required on those devices in order to upgrade
     * the firmware, send file or send any other SMP packet that is longer than 20 bytes.
     *
     * @since 1.3
     * @param maxLength the maximum packet length.
     */
    public final void setMaxPacketLength(final int maxLength) {
        mMaxPacketLength = maxLength;
    }

    /**
     * Returns the maximum length of a SMP packet that can be transmitted over by the transport.
     * @return the maximum
     */
    public final int getMaxPacketLength() {
        return mMaxPacketLength;
    }

    //*******************************************************************************************
    // Logging
    //*******************************************************************************************

    /**
     * Allows to enable low-level logging. If enabled, all BLE events will be logged.
     *
     * @param enabled true to enable logging, false to disable (default).
     */
    public void setLoggingEnabled(boolean enabled) {
        mLoggingEnabled = enabled;
    }

    /**
     * Returns the minimum log priority. Log messages with lower priority will not be logged.
     * @return Log.VERBOSE if logging is enabled, Log.WARN otherwise.
     */
    public int getMinLogPriority() {
        return mLoggingEnabled ? Log.VERBOSE : Log.WARN;
    }

    /**
     * Logs a message using SLF4J.
     * @param priority the log priority (Log.DEBUG, Log.INFO, etc.)
     * @param message the message to log
     */
    public void log(int priority, @NonNull String message) {
        switch (priority) {
            case Log.DEBUG: {
                LOG.debug(message);
                break;
            }
            case Log.INFO: {
                LOG.info(message);
                break;
            }
            case Log.WARN: {
                LOG.warn(message);
                break;
            }
            case Log.ASSERT:
            case Log.ERROR: {
                LOG.error(message);
                break;
            }
            case Log.VERBOSE: {
                LOG.trace(message);
                break;
            }
        }
    }

    //*******************************************************************************************
    // Mcu Manager Transport
    //*******************************************************************************************

    @NonNull
    @Override
    public McuMgrScheme getScheme() {
        return McuMgrScheme.BLE;
    }

    @NonNull
    @Override
    public <T extends McuMgrResponse> T send(@NonNull final byte[] payload,
                                             long timeout,
                                             @NonNull final Class<T> responseType)
            throws McuMgrException {
        final ResultCondition<T> condition = new ResultCondition<>(false);
        send(payload, timeout, responseType, new McuMgrCallback<>() {
            @Override
            public void onResponse(@NonNull T response) {
                condition.open(response);
            }

            @Override
            public void onError(@NonNull McuMgrException error) {
                condition.openExceptionally(error);
            }
        });
        return condition.block();
    }

    @Override
    public <T extends McuMgrResponse> void send(@NonNull final byte[] payload,
                                                final long timeout,
                                                @NonNull final Class<T> responseType,
                                                @NonNull final McuMgrCallback<T> callback) {
        // Connection is managed externally - verify we're ready to send
        final SmpProtocolSession session = mSmpProtocol;
        if (session == null) {
            callback.onError(new McuMgrDisconnectedException());
            return;
        }

        // Ensure the MTU is sufficient. Packets longer than MTU, but shorter
        // than few MTU lengths can be split automatically.
        if (mMaxPacketLength < payload.length) {
            callback.onError(new InsufficientMtuException(payload.length, mMaxPacketLength));
            return;
        }

        // Send a new transaction to the protocol layer
        session.send(payload, timeout, new SmpTransaction() {
            @Override
            public void send(@NonNull byte[] data) {
                // Check if disconnected - gatt/characteristic may be null if didDisconnect() was called
                // while this transaction was pending in the protocol session.
                final SmpWriteCallback characteristic = mSmpCharacteristicWrite;
                if (characteristic == null) {
                    log(Log.WARN, "Write aborted - disconnected");
                    return;
                }

                if (getMinLogPriority() <= Log.INFO) {
                    try {
                        log(Log.INFO, "Sending (" + payload.length + " bytes) "
                                + McuMgrHeader.fromBytes(payload) + " CBOR "
                                + CBOR.toString(payload, McuMgrHeader.HEADER_LENGTH));
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                // Write data, splitting into chunks if needed
                writeWithSplitting(characteristic, payload);
            }

            @Override
            public void onResponse(@NonNull byte[] data) {
                try {
                    T response = McuMgrResponse.buildResponse(McuMgrScheme.BLE, data, responseType);
                    if (response.isSuccess()) {
                        callback.onResponse(response);
                    } else {
                        callback.onError(new McuMgrErrorException(response));
                    }
                } catch (final Exception e) {
                    callback.onError(new McuMgrException(e));
                }
            }

            @Override
            public void onFailure(@NonNull Throwable e) {
                if (e instanceof McuMgrException) {
                    callback.onError((McuMgrException) e);
                } else if (e instanceof TransactionTimeoutException) {
                    callback.onError(new McuMgrTimeoutException(e));
                } else {
                    callback.onError(new McuMgrException(e));
                }
            }
        });
    }

    @Override
    public void connect(@Nullable final ConnectionCallback callback) {
        // Connection is managed externally. SMP characteristic is always set in constructor.
        if (callback != null) {
            callback.onConnected();
        }
    }

    /**
     * Initializes the GATT connection, SMP characteristic and protocol session.
     *
     * @param gatt The BluetoothGatt connection.
     * @param smpCharacteristic The SMP characteristic from service discovery.
     * @param mtu The negotiated MTU size.
     */
    private void initializeGatt(int mtu) {
        mChunkSize = mtu - 3; // 3 bytes for ATT header
        mMaxPacketLength = Math.max(mChunkSize, mMaxPacketLength);
        mSmpProtocol = new SmpProtocolSession(mHandler);
        log(Log.INFO, "SMP transport initialized with MTU: " + mtu + ", chunk size: " + mChunkSize);
    }

    /**
     * Called by external BLE manager to forward SMP characteristic notifications.
     * The external BLE manager should call this when it receives a notification
     * on the SMP characteristic.
     *
     * @param data The notification data.
     */
    public void handleNotification(@NonNull byte[] data) {
        final SmpProtocolSession session = mSmpProtocol;
        if (session == null) {
            log(Log.WARN, "Received notification but protocol session is null");
            return;
        }
        if (getMinLogPriority() <= Log.INFO) {
            try {
                log(Log.INFO, "Received "
                        + McuMgrHeader.fromBytes(data) + " CBOR "
                        + CBOR.toString(data, McuMgrHeader.HEADER_LENGTH));
            } catch (Exception e) {
                // Ignore
            }
        }
        session.receive(data);
    }

    /**
     * Called by external BLE manager when the connection is lost.
     * This cancels all pending operations and clears the protocol session.
     */
    public void didDisconnect() {
        log(Log.INFO, "didDisconnect() - cancelling all pending operations");

        // Close the protocol session first to fail all pending transactions.
        // This must happen before nulling gatt/characteristics to prevent race conditions
        // where a transaction's send() callback accesses null references.
        final SmpProtocolSession session = mSmpProtocol;
        mSmpProtocol = null;
        if (session != null) {
            session.close(new McuMgrDisconnectedException());
        }

        // Clear gatt and characteristics after closing the session
        mSmpCharacteristicWrite = null;
        mChunkSize = 0;
        mMaxPacketLength = 0;

        notifyDisconnected();
    }

    /**
     * Called by external BLE manager after reconnection and service discovery.
     * This re-initializes the SMP protocol session for new operations.
     *
     * @param mtu The negotiated MTU size.
     */
    public void didReconnect(int mtu) {
        log(Log.INFO, "didReconnect() - reinitializing protocol session");
        initializeGatt(mtu);
        notifyConnected();
    }

    @Override
    public void release() {
        log(Log.DEBUG, "release() called - connection managed externally");
        didDisconnect();
    }

    //*******************************************************************************************
    // BLE Write Operations
    //*******************************************************************************************

    /**
     * Writes data to the characteristic, splitting into chunks if the payload exceeds the chunk size.
     *
     * @param gatt The BluetoothGatt connection.
     * @param characteristic The characteristic to write to.
     * @param data The data to write.
     */
    @SuppressLint("MissingPermission")
    private void writeWithSplitting(@NonNull SmpWriteCallback characteristic,
                                    @NonNull byte[] data) {
        final int chunkSize = mChunkSize;
        if (chunkSize <= 0) {
            log(Log.WARN, "Invalid chunk size: " + chunkSize);
            return;
        }

        int offset = 0;
        while (offset < data.length) {
            final int end = Math.min(offset + chunkSize, data.length);
            final byte[] chunk = Arrays.copyOfRange(data, offset, end);
            characteristic.writeNoResponse(chunk);
            offset = end;
        }
    }

    //*******************************************************************************************
    // Manager Connection Observers
    //*******************************************************************************************

    private final List<ConnectionObserver> mConnectionObservers = new LinkedList<>();

    @Override
    public synchronized void addObserver(@NonNull final ConnectionObserver observer) {
        mConnectionObservers.add(observer);
    }

    @Override
    public synchronized void removeObserver(@NonNull final ConnectionObserver observer) {
        mConnectionObservers.remove(observer);
    }

    private synchronized void notifyConnected() {
        for (ConnectionObserver o : mConnectionObservers) {
            o.onConnected();
        }
    }

    private synchronized void notifyDisconnected() {
        for (ConnectionObserver o : mConnectionObservers) {
            o.onDisconnected();
        }
    }
}

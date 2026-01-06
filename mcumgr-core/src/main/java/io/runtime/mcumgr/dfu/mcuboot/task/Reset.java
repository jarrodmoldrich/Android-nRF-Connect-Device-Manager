/*
 * Copyright (c) 2017-2018 Runtime Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by Jarrod Moldrich, 2026
 */

package io.runtime.mcumgr.dfu.mcuboot.task;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.mcumgr.McuMgrCallback;
import io.runtime.mcumgr.McuMgrTransport;
import io.runtime.mcumgr.dfu.mcuboot.FirmwareUpgradeManager.Settings;
import io.runtime.mcumgr.dfu.mcuboot.FirmwareUpgradeManager.State;
import io.runtime.mcumgr.exception.McuMgrErrorException;
import io.runtime.mcumgr.exception.McuMgrException;
import io.runtime.mcumgr.managers.DefaultManager;
import io.runtime.mcumgr.response.dflt.McuMgrOsResponse;
import io.runtime.mcumgr.task.TaskManager;

class Reset extends FirmwareUpgradeTask {
	private final static Logger LOG = LoggerFactory.getLogger(Reset.class);

	private final boolean mNoSwap;

	Reset(final boolean noSwap) {
		this.mNoSwap = noSwap;
	}

	@Override
	@NotNull
	public State getState() {
		return State.RESET;
	}

	@Override
	public int getPriority() {
		return PRIORITY_RESET;
	}

	@Override
	public void start(@NotNull final TaskManager<Settings, State> performer) {
		final McuMgrTransport transport = performer.getTransport();

		transport.addObserver(new McuMgrTransport.ConnectionObserver() {
			private boolean disconnected = false;

			@Override
			public void onConnected() {
				// When BLE is managed externally, wait for reconnection before completing.
				// This is signaled via didReconnect() which calls notifyConnected().
				if (disconnected) {
					LOG.info("Device reconnected after reset");
					transport.removeObserver(this);
					performer.onTaskCompleted(Reset.this);
				}
			}

			@Override
			public void onDisconnected() {
				LOG.info("Device disconnected");
				disconnected = true;

				// If there is no swap, we're done. No need to wait for reconnection.
				if (mNoSwap) {
					transport.removeObserver(this);
					performer.onTaskCompleted(Reset.this);
					return;
				}

				// For externally managed BLE, we wait for onConnected() to be called
				// when the external BLE manager reconnects and calls didReconnect().
				// The swap time waiting is handled by the external BLE manager.
				LOG.trace("Waiting for external BLE manager to reconnect...");
			}
		});

		final DefaultManager manager = new DefaultManager(transport);
		manager.reset(new McuMgrCallback<>() {
			@Override
			public void onResponse(@NotNull final McuMgrOsResponse response) {
				// Check for an error return code.
				if (!response.isSuccess()) {
					performer.onTaskFailed(Reset.this, new McuMgrErrorException(response.getReturnCode()));
					return;
				}
				LOG.trace("Reset request success. Waiting for disconnect...");
			}

			@Override
			public void onError(@NotNull final McuMgrException error) {
				// A McuMgrDisconnectedException may be returned if the device has disconnected
				// before the response was received. In this case, assume that the reset
				// was successful and the device is now disconnected.
				// See: https://github.com/NordicSemiconductor/Android-nRF-Connect-Device-Manager/issues/242
			}
		});
	}
}

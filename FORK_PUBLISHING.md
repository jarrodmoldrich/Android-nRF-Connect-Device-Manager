# Publishing Your Fork to Maven Central

This fork has been configured to publish to Maven Central under your own namespace. Here's how to set it up for your fork.

## One-Time Setup

### 1. Update Fork Configuration

Edit `fork.config` and set your details:
```properties
GITHUB_USER=your-github-username
MAVEN_GROUP_ID=io.github.your-github-username
VERSION=2.7.4-KMS-rev5
DEVELOPER_NAME="Your Name"
DEVELOPER_EMAIL="your@email.com"
```

### 2. Register Maven Central Namespace

1. Go to https://central.sonatype.com/ and create an account
2. Verify your namespace: `io.github.your-github-username`
   - Add a GitHub repository verification (they'll provide a repo name to create)
3. Generate a User Token (Account → Generate User Token)

### 3. Set Up GPG Signing

```bash
# Install GPG
brew install gnupg

# Generate key
gpg --gen-key
# Use your name and email, set a passphrase

# Upload to keyserver
gpg --list-keys  # Get your key ID
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Configure GPG for terminal
echo 'export GPG_TTY=$(tty)' >> ~/.zshrc
source ~/.zshrc
```

### 4. Create Publishing Credentials

```bash
cp publishing.properties.template publishing.properties
```

Edit `publishing.properties`:
```properties
mavenCentralUsername=AbCd1234  # From your User Token
mavenCentralPassword=your-token-password
signing.gnupg.keyName=YOUR_GPG_KEY_ID
```

### 5. Configure Java 21

Edit `gradle.properties` and uncomment:
```properties
org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

Or install Java 21: `brew install openjdk@21`

## Publishing a Release

### Quick Publish (Automated)

```bash
./create-bundle.sh
```

This creates `mcumgr-VERSION-bundle.zip` in the current directory.

### Upload to Maven Central

1. Go to https://central.sonatype.com/publishing
2. Click "Upload Bundle"
3. Select your bundle ZIP
4. Set deployment name (e.g., `mcumgr-2.7.4-KMS-rev5`)
5. Click "Publish"

Wait 15-30 minutes for sync to Maven Central.

## Using Your Published Library

Once published, users can add:

```kotlin
dependencies {
    implementation("io.github.your-github-username:mcumgr-core:VERSION")
    implementation("io.github.your-github-username:mcumgr-ble:VERSION")
}
```

## Updating the Version

1. Edit `fork.config` and change `VERSION`
2. Run `./create-bundle.sh`
3. Upload the new bundle

## File Reference

- **fork.config** - Your fork's identity: GitHub user, Maven namespace, version (committed to git)
- **publishing.properties** - Maven Central credentials and GPG key (gitignored, never commit)
- **create-bundle.sh** - Builds and bundles for Maven Central upload

## Troubleshooting

**GPG signing errors**: Run `export GPG_TTY=$(tty)` before building

**Gradle daemon lock**: Run `./gradlew --stop` and try again

**401 authentication**: Verify your User Token in publishing.properties

**Java version error**: Install Java 21 or set gradle.properties to use Android Studio's JDK

## Additional Documentation

- Central Portal: https://central.sonatype.org/publish/publish-guide/
- GPG Guide: https://central.sonatype.org/publish/requirements/gpg/

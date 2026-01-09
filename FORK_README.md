# Fork Publishing Configuration

This project is configured for easy forking and publishing to Maven Central under your own namespace.

## Quick Start for Forkers

1. **Edit `fork.config`** with your details:
   ```properties
   GITHUB_USER=your-username
   MAVEN_GROUP_ID=io.github.your-username
   ```

2. **Create `publishing.properties`** from template and set version + credentials

3. **Follow the guide** in [FORK_PUBLISHING.md](FORK_PUBLISHING.md)

4. **Publish** with `./create-bundle.sh`

## Key Files

- **`fork.config`** - Your fork identity (GitHub user, Maven namespace) - **commit this**
- **`publishing.properties`** - Version and credentials - **never commit** (gitignored)
- **`create-bundle.sh`** - Automated bundle creation
- **`FORK_PUBLISHING.md`** - Complete setup guide

All GitHub URLs, Maven coordinates, and POM metadata automatically update based on `fork.config`.

## For the Current Fork

This fork publishes as:
- Group: `io.github.jarrodmoldrich`
- Artifacts: `mcumgr-core`, `mcumgr-ble`
- Repository: https://github.com/jarrodmoldrich/Android-nRF-Connect-Device-Manager

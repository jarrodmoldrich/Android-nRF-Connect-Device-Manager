#!/bin/bash
# Create Maven Central deployment bundle
# Reads configuration from fork.config

set -e  # Exit on error

echo "========================================="
echo "Maven Central Bundle Creator"
echo "========================================="
echo ""

# Load fork configuration
if [ ! -f "fork.config" ]; then
    echo "❌ Error: fork.config not found"
    echo "Please create fork.config with your settings"
    exit 1
fi

if [ ! -f "publishing.properties" ]; then
    echo "❌ Error: publishing.properties not found"
    echo "Please create it from publishing.properties.template"
    exit 1
fi

source fork.config

# VERSION comes from fork.config
if [ -z "$VERSION" ]; then
    echo "❌ Error: VERSION not set in fork.config"
    exit 1
fi

GROUP_PATH=$(echo "$MAVEN_GROUP_ID" | tr '.' '/')
BUNDLE_DIR="./mcumgr-bundle"
BUNDLE_ZIP="./mcumgr-${VERSION}-bundle.zip"

echo "📦 Publishing as: $MAVEN_GROUP_ID"
echo "📦 GitHub: https://github.com/$GITHUB_USER/Android-nRF-Connect-Device-Manager"
echo "📦 Version: $VERSION"
echo ""

# Step 1: Clean and build
echo "Step 1: Building and publishing to Maven local..."
./gradlew clean build publishToMavenLocal

# Step 2: Create bundle directory
echo ""
echo "Step 2: Creating bundle directory..."
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR"

# Step 3: Copy artifacts with Maven structure
echo "Step 3: Copying artifacts..."
# Only copy the version directories, not parent metadata
mkdir -p "$BUNDLE_DIR/$GROUP_PATH/mcumgr-core"
mkdir -p "$BUNDLE_DIR/$GROUP_PATH/mcumgr-ble"
cp -r ~/.m2/repository/$GROUP_PATH/mcumgr-core/$VERSION "$BUNDLE_DIR/$GROUP_PATH/mcumgr-core/"
cp -r ~/.m2/repository/$GROUP_PATH/mcumgr-ble/$VERSION "$BUNDLE_DIR/$GROUP_PATH/mcumgr-ble/"

# Step 4: Generate checksums
echo "Step 4: Generating MD5 and SHA1 checksums..."
cd "$BUNDLE_DIR"
find . -type f ! -name "*.md5" ! -name "*.sha1" | while read file; do
    # Generate MD5
    md5 -q "$file" > "$file.md5"
    # Generate SHA1
    shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"
done

# Step 5: Create ZIP bundle
echo "Step 5: Creating bundle ZIP..."
rm -f "$BUNDLE_ZIP"
zip -r "$BUNDLE_ZIP" io/ > /dev/null

# Cleanup
rm -rf "$BUNDLE_DIR"

echo ""
echo "========================================="
echo "✅ Bundle created successfully!"
echo "========================================="
echo ""
echo "Bundle location: $BUNDLE_ZIP"
echo "Bundle size: $(du -h "$BUNDLE_ZIP" | awk '{print $1}')"
echo ""
echo "Next steps:"
echo "1. Go to https://central.sonatype.com/publishing"
echo "2. Click 'Upload Bundle'"
echo "3. Upload: $BUNDLE_ZIP"
echo "4. Click 'Publish'"
echo ""
echo "Artifacts will sync to Maven Central within 15-30 minutes."

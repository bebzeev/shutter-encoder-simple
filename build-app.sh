#!/bin/bash
set -e

# ============================================================================
# build-app.sh — Compile and package Shutter Encoder as a macOS .app bundle
#
# Usage:
#   ./build-app.sh
#
# Prerequisites (macOS only):
#   - Java 21+ JDK (for javac)
#   - A JRE directory at ./JRE (custom jlink build, see README)
#   - FFmpeg and other binaries in ./Library/
#   - sips (ships with macOS) for icon conversion
#
# Output:
#   ./dist/Shutter Encoder.app
#
# Re-run this script after every code change to rebuild the .app.
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="Shutter Encoder"
BUNDLE_ID="com.shutterencoder.app"
VERSION="20.0"
MAIN_CLASS="application.Shutter"

BUILD_DIR="$SCRIPT_DIR/build"
DIST_DIR="$SCRIPT_DIR/dist"
APP_DIR="$DIST_DIR/${APP_NAME}.app"
CONTENTS="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS/MacOS"
RESOURCES="$CONTENTS/Resources"
JAVA_DIR="$CONTENTS/Java"

# ── Clean previous build ────────────────────────────────────────────────────

echo "==> Cleaning previous build..."
rm -rf "$BUILD_DIR" "$APP_DIR"
mkdir -p "$BUILD_DIR" "$MACOS_DIR" "$RESOURCES" "$JAVA_DIR"

# ── Compile ─────────────────────────────────────────────────────────────────

echo "==> Compiling Java sources..."
find src -name "*.java" > /tmp/shutter-sources.txt
javac -cp "lib/*:src" -d "$BUILD_DIR" -sourcepath src @/tmp/shutter-sources.txt

# Copy resources into build classes (so they're included in the JAR)
cp -r src/contents "$BUILD_DIR/"

# ── Create JAR ──────────────────────────────────────────────────────────────

echo "==> Creating JAR..."
cat > "$BUILD_DIR/MANIFEST.MF" <<MANIFEST
Manifest-Version: 1.0
Main-Class: ${MAIN_CLASS}
Class-Path: .
MANIFEST

jar cfm "$JAVA_DIR/${APP_NAME}.jar" "$BUILD_DIR/MANIFEST.MF" -C "$BUILD_DIR" .

# ── Copy dependencies ───────────────────────────────────────────────────────

echo "==> Copying libraries..."
cp lib/*.jar "$JAVA_DIR/"

echo "==> Copying language files..."
cp -r Languages "$JAVA_DIR/"

echo "==> Copying fonts..."
cp -r fonts "$JAVA_DIR/"

# Library/ (ffmpeg, yt-dlp, etc.) — only copy if present
if [ -d "Library" ] && [ "$(ls -A Library 2>/dev/null)" ]; then
    echo "==> Copying Library binaries..."
    cp -r Library "$JAVA_DIR/"
fi

# JRE — only copy if present
if [ -d "JRE" ]; then
    echo "==> Bundling JRE..."
    cp -r JRE "$CONTENTS/JRE"
else
    echo "⚠  No JRE directory found. The app will use the system Java."
    echo "   To bundle a JRE, create one with: jlink --compress 0 --strip-debug \\"
    echo "     --no-header-files --no-man-pages \\"
    echo "     --add-modules java.base,java.datatransfer,java.desktop,java.logging,java.security.sasl,java.xml,jdk.crypto.ec \\"
    echo "     --output JRE"
fi

# ── Generate .icns icon ─────────────────────────────────────────────────────

echo "==> Generating app icon..."
if command -v sips &>/dev/null && command -v iconutil &>/dev/null; then
    ICONSET="$BUILD_DIR/icon.iconset"
    mkdir -p "$ICONSET"
    SOURCE_ICON="logo.png"

    for SIZE in 16 32 64 128 256 512; do
        sips -z $SIZE $SIZE "$SOURCE_ICON" --out "$ICONSET/icon_${SIZE}x${SIZE}.png" &>/dev/null
        DOUBLE=$((SIZE * 2))
        if [ $DOUBLE -le 1024 ]; then
            sips -z $DOUBLE $DOUBLE "$SOURCE_ICON" --out "$ICONSET/icon_${SIZE}x${SIZE}@2x.png" &>/dev/null
        fi
    done

    iconutil -c icns -o "$RESOURCES/AppIcon.icns" "$ICONSET" 2>/dev/null || true
    rm -rf "$ICONSET"
else
    echo "   (sips/iconutil not available — skipping .icns generation, icon will be missing)"
fi

# ── Info.plist ──────────────────────────────────────────────────────────────

echo "==> Writing Info.plist..."
cat > "$CONTENTS/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>${APP_NAME}</string>
    <key>CFBundleDisplayName</key>
    <string>${APP_NAME}</string>
    <key>CFBundleIdentifier</key>
    <string>${BUNDLE_ID}</string>
    <key>CFBundleVersion</key>
    <string>${VERSION}</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleSignature</key>
    <string>????</string>
    <key>CFBundleExecutable</key>
    <string>launcher</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
    <key>CFBundleDocumentTypes</key>
    <array>
        <dict>
            <key>CFBundleTypeExtensions</key>
            <array>
                <string>mp4</string>
                <string>mov</string>
                <string>mkv</string>
                <string>avi</string>
                <string>mp3</string>
                <string>wav</string>
                <string>flac</string>
                <string>aac</string>
            </array>
            <key>CFBundleTypeName</key>
            <string>Media File</string>
            <key>CFBundleTypeRole</key>
            <string>Viewer</string>
        </dict>
    </array>
</dict>
</plist>
PLIST

# ── Launcher script ────────────────────────────────────────────────────────

echo "==> Writing launcher script..."
cat > "$MACOS_DIR/launcher" <<'LAUNCHER'
#!/bin/bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Load VM options from config.properties if present
VM_OPTS=""
if [ -f "$DIR/Java/config.properties" ]; then
    while IFS= read -r line; do
        [[ "$line" =~ ^# ]] && continue
        [[ -z "$line" ]] && continue
        VM_OPTS="$VM_OPTS $line"
    done < "$DIR/Java/config.properties"
fi

# Use bundled JRE if available, otherwise fall back to system java
if [ -d "$DIR/JRE" ]; then
    JAVA="$DIR/JRE/bin/java"
else
    JAVA="java"
fi

# Build classpath from all JARs
CP="$DIR/Java"
for jar in "$DIR/Java"/*.jar; do
    CP="$CP:$jar"
done

cd "$DIR/Java"
exec "$JAVA" \
    -Xmx2g \
    -Dapple.laf.useScreenMenuBar=true \
    "-Dapple.awt.application.name=Shutter Encoder" \
    $VM_OPTS \
    -cp "$CP" \
    application.Shutter "$@"
LAUNCHER
chmod +x "$MACOS_DIR/launcher"

# ── Copy config.properties ──────────────────────────────────────────────────

if [ -f "config.properties" ]; then
    cp config.properties "$JAVA_DIR/"
fi

# ── Done ────────────────────────────────────────────────────────────────────

echo ""
echo "✓ Built: $APP_DIR"
echo ""
echo "  To run:  open \"$APP_DIR\""
echo ""
if [ ! -d "JRE" ]; then
    echo "  NOTE: No bundled JRE. Install Java 25 or create a JRE with jlink."
fi
if [ ! -d "Library/ffmpeg" ] && [ ! -f "Library/ffmpeg" ]; then
    echo "  NOTE: No ffmpeg in Library/. Download from https://ffmpeg.org"
fi

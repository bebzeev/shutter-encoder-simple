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

APP_NAME="Shutter Encoder Simple"
BUNDLE_ID="com.shutterencoder.simple"
VERSION="1.0"
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
    <key>NSDesktopFolderUsageDescription</key>
    <string>Read and convert media files from Desktop.</string>
    <key>NSDocumentsFolderUsageDescription</key>
    <string>Read and convert media files from Documents.</string>
    <key>NSDownloadsFolderUsageDescription</key>
    <string>Read and convert media files from Downloads.</string>
    <key>NSRemovableVolumesUsageDescription</key>
    <string>Read and convert media files from external drives.</string>
    <key>NSCameraUsageDescription</key>
    <string>Required by AVFoundation for HDR-to-SDR colorspace conversion.</string>
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

# ── Compile Swift HDR helper ────────────────────────────────────────────────
# This native Mach-O does HDR→SDR via AVAssetExportSession. Bundling it inside
# the .app gives Strip HDR a path that doesn't depend on /usr/bin/avconvert,
# which fails when invoked from a Java subprocess (no Cocoa drag-drop TCC).

SDR_HELPER_SRC="$SCRIPT_DIR/helper-src/sdr-helper.swift"
SDR_HELPER_BIN="$MACOS_DIR/sdr-helper"

if [ -f "$SDR_HELPER_SRC" ]; then
    if command -v xcrun &>/dev/null; then
        echo "==> Compiling Swift sdr-helper..."
        xcrun swiftc -O -o "$SDR_HELPER_BIN" "$SDR_HELPER_SRC" 2>/dev/null || {
            echo "  ⚠ Failed to compile sdr-helper. Strip HDR will be unavailable."
            rm -f "$SDR_HELPER_BIN"
        }
        if [ -f "$SDR_HELPER_BIN" ]; then
            chmod +x "$SDR_HELPER_BIN"
        fi
    else
        echo "  ⚠ xcrun not found; skipping sdr-helper compile."
    fi
fi

# ── Ad-hoc sign on macOS ────────────────────────────────────────────────────
# Apple Silicon requires signed Mach-O binaries to execute. Sign every native
# binary inside the bundle, then sign the app itself.

if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "==> Ad-hoc signing native binaries and app bundle..."
    # Strip quarantine attrs that get added when binaries are downloaded
    xattr -cr "$APP_DIR" 2>/dev/null || true
    # Sign every Mach-O inside the bundle
    find "$APP_DIR" -type f \( -name "ffmpeg" -o -name "ffprobe" -o -name "yt-dlp*" -o -name "sdr-helper" -o -name "*.dylib" \) -print0 \
        | xargs -0 -n1 -I{} codesign --force --options runtime --timestamp=none -s - "{}" 2>/dev/null || true
    # Sign the JRE's java launcher and any of its bundled dylibs
    find "$APP_DIR/Contents/JRE" -type f \( -perm +111 -o -name "*.dylib" \) -print0 2>/dev/null \
        | xargs -0 -n1 -I{} codesign --force -s - "{}" 2>/dev/null || true
    # Sign the whole app last so the seal includes everything
    codesign --force --deep -s - "$APP_DIR" 2>/dev/null || true
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

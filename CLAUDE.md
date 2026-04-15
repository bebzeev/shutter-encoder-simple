# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Shutter Encoder is a Java Swing desktop application for media transcoding built on FFmpeg. This fork adds a **simplified "Simple converter" mode** for non-technical performance marketing users, exposing only MP4 (H.264), MOV, MP3, and WAV formats with minimal controls.

## Build & Compile

No Maven/Gradle — compile directly with javac against JARs in `lib/`:

```bash
# Compile all sources
find src -name "*.java" > /tmp/sources.txt
javac -cp "lib/*:src" -d /tmp/shutter-build -sourcepath src @/tmp/sources.txt

# Compile specific files (faster iteration)
javac -cp "lib/*:src" -d /tmp/shutter-build -sourcepath src src/application/Shutter.java src/application/SimpleEncoderPanel.java src/functions/SimpleEncoder.java
```

There are no automated tests. Verify changes by compiling and running the application.

Runtime requires FFmpeg and other binaries in `Library/` and a Java 25 JRE (custom jlink build).

## Architecture

### Package Structure

- **`application/`** — Main UI classes. `Shutter.java` (~26K lines) is the monolithic main class containing all UI component declarations, layout, event handling, and application state as static fields.
- **`functions/`** — Encoding function implementations. Each class (e.g., `VideoEncoders`, `AudioEncoders`, `SimpleEncoder`) runs encoding in a background thread and calls `FFMPEG.run()` with constructed command strings.
- **`settings/`** — Helper classes that build FFmpeg filter/option strings from UI state (e.g., `AudioSettings.setEQ()`, `Image.setScale()`, `Colorimetry.setColor()`). Also includes `FunctionUtils` for shared operations (file handling, output destination, mail, cleanup).
- **`library/`** — Wrappers around external binaries (`FFMPEG`, `FFPROBE`, `NCNN`, `WHISPER`, etc.). Each launches a `ProcessBuilder`, captures stdout/stderr, and parses progress.

### How Functions Work (critical pattern)

Adding a new function requires changes in multiple places in `Shutter.java`:

1. **Function list** (~line 3897): Add to `functionsList` ArrayList. Items containing ":" are section headers; others are selectable functions.
2. **Button routing** (~line 3545): Add an `else if` in `btnStart`'s ActionListener to call `YourFunction.main()`.
3. **`changeFunction()`** (~line 19744): Add the function name to the condition block so `changeWidth()` and `changeSections()` are called.
4. **`changeSections()`** (~line 20600): Add a block that shows/hides the appropriate right-side panels (grpResolution, grpBitrate, grpSetAudio, grpAdvanced, etc.) and configures their contents.
5. **`changeFilters()`** (~line 23998): Optionally configure the filter combo dropdown for this function.

### UI Panel System

The right side of the window contains ~15 collapsible panels (grpResolution, grpBitrate, grpSetAudio, grpAdvanced, grpColorimetry, grpCrop, etc.). `changeSections()` controls which panels are visible and how they're configured for each function. Panels animate in/out when switching functions.

### Encoding Command Flow

1. Function class (e.g., `VideoEncoders.main()`) iterates over `Shutter.list` (the file list model)
2. Calls `FunctionUtils.analyze()` → `FFPROBE` to probe the file
3. Builds command string parts: codec, bitrate, resolution, filters, audio, etc.
4. Calls `FFMPEG.run(commandString)` which launches the ffmpeg process
5. Monitors `FFMPEG.runProcess.isAlive()` in a polling loop
6. Calls `lastActions()` for cleanup, then `endOfFunction()` when all files are done

### State Management

Nearly all UI state is **public static fields on `Shutter.java`** — combo boxes, checkboxes, text fields, labels, panels. Functions and settings classes read these directly (e.g., `Shutter.comboResolution.getSelectedItem()`). There is no MVC separation.

### Internationalization

Language strings are in `Languages/*.properties` (25 locales). Access via `Shutter.language.getProperty("keyName")`. Function names in the combo box are localized, so comparisons use `language.getProperty("functionXxx").equals(function)` rather than hardcoded strings. Codec names (H.264, MP3, etc.) are not localized.

## Simple Encoder (Current Branch Feature)

The simplified encoder added in this branch consists of:

- **`src/application/SimpleEncoderPanel.java`** — Right-side settings panel with format, quality slider, dimensions, audio toggle, suffix, and open-folder checkbox.
- **`src/functions/SimpleEncoder.java`** — Encoding logic using H.264 (high profile, level 4.1, yuv420p, faststart) for video and libmp3lame/PCM for audio. Quality slider maps to CRF 16-32.
- **`Languages/en.properties`** — Keys prefixed with `simpleEncoder*` and `itemSimpleEncoder`.
- **`Shutter.java` integration points**: function list, btnStart routing, changeFunction(), changeSections(), disableAll()/enableAll().

The button label changes to "Convert" (from "Start function") when the simple encoder is selected. The combo item is `language.getProperty("simpleEncoder")` = "Simple converter".

## Key Constants & Paths

- `Shutter.extendedWidth` = 1350 (full window width with settings panels)
- `Shutter.minHeight` = 731
- Settings panels are at x=658, width=312
- User settings: `~/Shutter Encoder/settings.xml`
- FFmpeg binary: `Library/ffmpeg` (relative to JAR location)

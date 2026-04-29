/*******************************************************************************************
* Copyright (C) 2026 PACIFICO PAUL
*
* Simplified encoder driving its UI from SimpleFrame (sage horizontal layout).
* Supports MP4 (H.264), MOV (ProRes 422 / 4444+α), MP3, WAV, with optional HDR→SDR.
********************************************************************************************/

package functions;

import java.awt.Desktop;
import java.io.File;

import application.Ftp;
import application.RenderQueue;
import application.Settings;
import application.Shutter;
import application.SimpleFrame;
import application.Utils;
import application.VideoPlayer;
import library.FFMPEG;
import library.FFPROBE;
import settings.FunctionUtils;
import settings.InputAndOutput;
import settings.Timecode;

public class SimpleEncoder extends Shutter {

    /** Index of the file currently being encoded (used for progress display). */
    public static volatile int currentFileIndex = -1;

    public static void main() {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {

                if (scanIsRunning == false)
                    FunctionUtils.completed = 0;

                lblFilesEnded.setText(FunctionUtils.completedFiles(FunctionUtils.completed));

                resetStripHdrNotice();
                boolean hadError = false;
                int failedCount = 0;
                int total = list.getSize();

                for (int i = 0; i < list.getSize(); i++)
                {
                    currentFileIndex = i;
                    SimpleFrame.DropZone.setFileEncoding(i);
                    if (btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")))
                    {
                        boolean isSelected = false;

                        for (String input : Shutter.fileList.getSelectedValuesList())
                        {
                            if (list.getElementAt(i).equals(input))
                            {
                                isSelected = true;
                            }
                        }

                        if (isSelected == false)
                        {
                            continue;
                        }
                    }

                    File file = FunctionUtils.setInputFile(new File(list.getElementAt(i)));

                    if (file == null)
                        break;

                    FFMPEG.error = false;

                    try {

                        String fileName = file.getName();
                        String extension = fileName.substring(fileName.lastIndexOf("."));

                        lblCurrentEncoding.setText(fileName);

                        if (FunctionUtils.analyze(file, false) == false) {
                            SimpleFrame.DropZone.setFileDone(i, false);
                            hadError = true;
                            failedCount++;
                            continue;
                        }

                        String format = SimpleFrame.selectedFormat;
                        boolean isAudioOnly = format.equals("MP3") || format.equals("WAV");
                        boolean isStripHDR = SimpleFrame.MODE_STRIP_HDR.equals(SimpleFrame.currentMode);

                        if (isStripHDR)
                        {
                            encodeStripHDR(file, fileName, extension);
                        }
                        else if (isAudioOnly)
                        {
                            encodeAudio(file, fileName, extension, format);
                        }
                        else
                        {
                            encodeVideo(file, fileName, extension, format);
                        }

                        SimpleFrame.DropZone.setFileDone(i, !FFMPEG.error);
                        if (FFMPEG.error) { hadError = true; failedCount++; }

                    } catch (InterruptedException e) {
                        FFMPEG.error = true;
                        hadError = true;
                        failedCount++;
                        SimpleFrame.DropZone.setFileDone(i, false);
                    }
                }
                currentFileIndex = -1;

                if (btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")))
                {
                    VideoPlayer.videoPath = null;
                    VideoPlayer.setMedia();
                    do {
                        try { Thread.sleep(10); } catch (InterruptedException e) {}
                    } while (VideoPlayer.loadMedia.isAlive());
                    RenderQueue.frame.toFront();
                }
                else
                {
                    // If anything failed, suppress the legacy ding/error-dialog combo so
                    // the user doesn't hear "ding + boop boop" or get a dark popup.
                    boolean suppressLegacySound = (failedCount > 0);
                    boolean prevDisable = false;
                    StringBuilder savedErrors = null;
                    if (suppressLegacySound) {
                        prevDisable = Settings.btnDisableSound.isSelected();
                        Settings.btnDisableSound.setSelected(true);
                        // Also clear errorList so endOfFunction doesn't show its dark JOptionPane.
                        if (errorList != null) {
                            savedErrors = new StringBuilder(errorList.toString());
                            errorList.setLength(0);
                        }
                    }

                    endOfFunction();

                    if (suppressLegacySound) {
                        Settings.btnDisableSound.setSelected(prevDisable);
                        // restore (mostly bookkeeping; nothing else reads it after this point)
                        if (savedErrors != null && errorList != null) {
                            errorList.setLength(0);
                            errorList.append(savedErrors);
                        }
                    }

                    // Per-file status rows already say FAILED / DONE; play a single
                    // sound from macOS's UI sound library to signal overall outcome.
                    if (!cancelled) {
                        if (failedCount == total && total > 0) {
                            playMacSound("Basso");   // whole batch failed → low buzz
                        } else if (failedCount > 0) {
                            playMacSound("Funk");    // partial failure → single chirp
                        }
                    }
                }
            }
        });
        thread.start();
    }

    /** Play one of macOS's bundled UI sounds (no Java audio plumbing required). */
    private static void playMacSound(String name) {
        try {
            new ProcessBuilder("/usr/bin/afplay",
                "/System/Library/Sounds/" + name + ".aiff")
                .redirectErrorStream(true)
                .start();
        } catch (java.io.IOException ignore) {}
    }


    private static void encodeVideo(File file, String fileName, String extension, String format) throws InterruptedException {

        if (FFPROBE.audioOnly)
        {
            FFPROBE.imageWidth = 1920;
            FFPROBE.imageHeight = 1080;
            FFPROBE.imageResolution = "1920x1080";
        }

        InputAndOutput.getInputAndOutput(VideoPlayer.getFileList(file.toString(), FFPROBE.totalLength));

        String labelOutput = getOutputFolder(file);

        String prefix = SimpleFrame.prefixText == null ? "" : SimpleFrame.prefixText.trim();
        String suffix = SimpleFrame.suffixText == null ? "" : SimpleFrame.suffixText.trim();

        String container = format.equals("MOV") ? ".mov" : ".mp4";

        String fileOutputName = labelOutput.replace("\\", "/") + "/" + prefix + fileName.replace(extension, suffix + container);

        File fileOut = new File(fileOutputName);
        if (fileOut.exists())
        {
            fileOut = FunctionUtils.fileReplacement(labelOutput, fileName, extension, suffix + "_", container);

            if (fileOut == null) { cancelled = true; return; }
            if (fileOut.toString().equals("skip")) return;
        }

        // ── HDR → SDR pre-pass via Apple's avconvert ───────────────────────
        // For color-accurate SDR conversion (matching native macOS / QuickTime
        // playback colors), pipe through /usr/bin/avconvert at PresetHighestQuality
        // first, then let ffmpeg do the final container/codec/quality encode.
        // If avconvert fails for any reason, fall back to ffmpeg's zscale+tonemap
        // so SDR conversion still works (with slightly different colors).
        File ffmpegInput = file;
        File sdrTemp = null;
        boolean useFfmpegTonemapFallback = false;
        if (SimpleFrame.stripHDR)
        {
            if (isMac()) {
                sdrTemp = runAvconvertSDR(file);
            }
            if (sdrTemp != null) {
                ffmpegInput = sdrTemp;
            } else {
                logSimple("Falling back to ffmpeg zscale+hable tonemap for SDR conversion.");
                useFfmpegTonemapFallback = true;
            }
        }

        int qualityValue = SimpleFrame.qualityValue;

        // ── codec / pix_fmt ──────────────────────────────────────────────
        String videoCodec;
        String bitrate;
        String outPixFmt;
        boolean isMOV = format.equals("MOV");
        boolean useAlpha = isMOV && SimpleFrame.alphaChannel;
        if (isMOV) {
            if (useAlpha) {
                // ProRes 4444 with alpha
                videoCodec = " -c:v prores_ks -profile:v 4 -vendor apl0";
                outPixFmt  = "yuva444p10le";
            } else {
                // ProRes 422 HQ
                videoCodec = " -c:v prores_ks -profile:v 3 -vendor apl0";
                outPixFmt  = "yuv422p10le";
            }
            bitrate = ""; // profile-driven
        } else {
            // MP4 / H.264
            int crf = mapQualityToCRF(qualityValue);
            videoCodec = " -c:v libx264 -preset medium -profile:v high -level 4.1";
            bitrate    = " -crf " + crf;
            outPixFmt  = "yuv420p";
        }

        // ── filter chain ─────────────────────────────────────────────────
        // - When SDR was already done by avconvert: just optional resize.
        // - When avconvert was unavailable AND user wants SDR: ffmpeg's
        //   zscale → linear → bt709 → tonemap=hable → bt709 chain (less perfect
        //   colors than avconvert but functional).
        String tonemapChain = "";
        if (useFfmpegTonemapFallback) {
            tonemapChain =
                "zscale=t=linear:npl=100," +
                "format=gbrpf32le," +
                "zscale=p=bt709," +
                "tonemap=tonemap=hable:desat=0," +
                "zscale=t=bt709:m=bt709:r=tv," +
                "format=" + outPixFmt;
        }
        String scaleChain = getScaleChain();

        StringBuilder filter = new StringBuilder();
        if (!tonemapChain.isEmpty()) filter.append(tonemapChain);
        if (!scaleChain.isEmpty()) {
            if (filter.length() > 0) filter.append(",");
            filter.append(scaleChain);
        }

        String vfArg;
        if (filter.length() > 0) {
            vfArg = " -vf \"" + filter + "\"";
            // tonemapChain already ends with format=... so don't double-set pix_fmt
            if (tonemapChain.isEmpty()) vfArg += " -pix_fmt " + outPixFmt;
        } else {
            vfArg = " -pix_fmt " + outPixFmt;
        }

        // ── audio ────────────────────────────────────────────────────────
        String audio;
        if (SimpleFrame.includeAudio)
        {
            int audioBitrate = getAudioBitrate(qualityValue);
            if (isMOV) {
                // PCM 16-bit for ProRes mov
                audio = " -c:a pcm_s16le -ar 48000";
            } else {
                audio = " -c:a aac -b:a " + audioBitrate + "k -ar 48000";
            }
        }
        else
        {
            audio = " -an";
        }

        // ── container flags ──────────────────────────────────────────────
        String containerFlags = isMOV ? "" : " -movflags +faststart";

        String timecode = Timecode.setTimecode(file);

        String cmd;
        if (sdrTemp != null) {
            // avconvert succeeded → stream-copy (remux only) so we preserve
            // Apple's exact pixel data and color metadata. This matches the
            // colors of the reference HDRStrip.swift tool exactly.
            // Quality slider, dimensions, and ProRes settings are intentionally
            // bypassed in this mode — they would require a re-encode that
            // shifts the colors.
            String audioArg = SimpleFrame.includeAudio ? " -c:a copy" : " -an";
            cmd = " -c:v copy" + audioArg + containerFlags + timecode + " -y ";
        } else {
            cmd = videoCodec + bitrate + vfArg + audio + containerFlags + timecode + " -y ";
        }

        try {
            FFMPEG.run(InputAndOutput.inPoint + " -i " + '"' + ffmpegInput.toString() + '"' + InputAndOutput.outPoint + cmd + '"' + fileOut + '"');

            // Wait for the FFmpeg run thread to finish, polling progressBar1 to update
            // the current file's row UI.
            do {
                Thread.sleep(150);
                try {
                    int max = Shutter.progressBar1.getMaximum();
                    int val = Shutter.progressBar1.getValue();
                    if (max > 0 && currentFileIndex >= 0) {
                        int pct = (int) Math.min(100, val * 100L / max);
                        SimpleFrame.DropZone.setFileProgress(currentFileIndex, pct);
                    }
                } catch (Exception ignore) {}
            } while (FFMPEG.runProcess.isAlive());

            if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
            {
                if (lastActions(file, fileName, fileOut, labelOutput))
                    return;
            }
        } finally {
            // Always clean up the avconvert temp file
            if (sdrTemp != null && sdrTemp.exists()) sdrTemp.delete();
        }
    }

    /**
     * STRIP HDR mode: run Apple's avconvert and move its output to the user's
     * destination. No ffmpeg, no re-encode — output is byte-identical to the
     * reference HDRStrip.swift tool's colors. If avconvert refuses to run (a
     * macOS TCC restriction when invoked from an ad-hoc-signed .app),
     * the file is marked FAILED and a one-shot notice surfaces the cause so
     * the user can grant Full Disk Access and retry.
     */
    private static volatile boolean stripHdrTccNoticeShown = false;

    private static void encodeStripHDR(File file, String fileName, String extension) throws InterruptedException {

        InputAndOutput.getInputAndOutput(VideoPlayer.getFileList(file.toString(), FFPROBE.totalLength));

        String labelOutput = getOutputFolder(file);
        String prefix = SimpleFrame.prefixText == null ? "" : SimpleFrame.prefixText.trim();
        String suffix = SimpleFrame.suffixText == null ? "" : SimpleFrame.suffixText.trim();
        String container = ".mov";

        String fileOutputName = labelOutput.replace("\\", "/") + "/" + prefix + fileName.replace(extension, suffix + container);
        File fileOut = new File(fileOutputName);
        if (fileOut.exists()) {
            fileOut = FunctionUtils.fileReplacement(labelOutput, fileName, extension, suffix + "_", container);
            if (fileOut == null) { cancelled = true; return; }
            if (fileOut.toString().equals("skip")) return;
        }

        // Write avconvert output **directly** to the destination — no /var/folders
        // staging. The destination path lives in the same directory as the source,
        // which inherited the user-selected file TCC token from drag-drop, so
        // AVFoundation has the access it needs without Full Disk Access.
        boolean ok = runAvconvertDirect(file, fileOut);

        if (!ok) {
            FFMPEG.error = true;
            if (!stripHdrTccNoticeShown) {
                stripHdrTccNoticeShown = true;
                javax.swing.SwingUtilities.invokeLater(() ->
                    application.SimpleFrame.showSageNotice(
                        "STRIP HDR couldn't run avconvert on this file. See:\n" +
                        "~/Library/Logs/Shutter Encoder Simple/log.txt"));
            }
            return;
        }

        if (currentFileIndex >= 0) {
            application.SimpleFrame.DropZone.setFileProgress(currentFileIndex, 100);
        }
        if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false) {
            lastActions(file, fileName, fileOut, labelOutput);
        }
    }

    /**
     * Run the bundled sdr-helper Swift binary (Contents/MacOS/sdr-helper) which
     * uses AVAssetExportSession at PresetHighestQuality natively. This succeeds
     * where /usr/bin/avconvert fails when invoked from a Java subprocess —
     * because the helper's Cocoa context can open NSURLs that AVFoundation
     * accepts. Output is byte-identical to what the reference HDRStrip.swift
     * tool produces.
     */
    private static boolean runAvconvertDirect(File source, File destination) {
        File helper = locateSdrHelper();
        if (helper == null || !helper.canExecute()) {
            logSimple("sdr-helper not found or not executable. Expected at Contents/MacOS/sdr-helper.");
            return false;
        }

        Process p = null;
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                helper.getAbsolutePath(),
                source.getAbsolutePath(),
                destination.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            p = pb.start();

            final Process pRef = p;
            Thread reader = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(pRef.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (output) { output.append(line).append('\n'); }
                        // Helper prints "progress: NN% complete" lines we can mirror
                        // into the per-file progress bar in the UI.
                        int idx = line.indexOf("progress:");
                        if (idx >= 0) {
                            String rest = line.substring(idx + "progress:".length());
                            int pctEnd = rest.indexOf("%");
                            if (pctEnd > 0) {
                                try {
                                    int pct = Integer.parseInt(rest.substring(0, pctEnd).trim());
                                    if (currentFileIndex >= 0) {
                                        application.SimpleFrame.DropZone.setFileProgress(currentFileIndex, pct);
                                    }
                                } catch (NumberFormatException ignore) {}
                            }
                        }
                    }
                } catch (java.io.IOException ignore) {}
            }, "sdr-helper-reader");
            reader.setDaemon(true);
            reader.start();

            // Generous timeout — long videos can take a while at PresetHighestQuality
            boolean done = p.waitFor(15, java.util.concurrent.TimeUnit.MINUTES);
            if (!done) {
                p.destroyForcibly();
                logSimple("sdr-helper TIMEOUT  source=" + source.getAbsolutePath());
                if (destination.exists() && destination.length() == 0) destination.delete();
                return false;
            }
            try { reader.join(500); } catch (InterruptedException ignore) {}

            int exit = p.exitValue();
            if (exit == 0 && destination.exists() && destination.length() > 0) {
                return true;
            }
            logSimple("sdr-helper FAILED  exit=" + exit
                + "  source=" + source.getAbsolutePath()
                + "  dest=" + destination.getAbsolutePath()
                + "  destExists=" + destination.exists()
                + "  destBytes=" + (destination.exists() ? destination.length() : -1)
                + "\n----- helper output -----\n"
                + output
                + "----- end -----");
            if (destination.exists() && destination.length() == 0) destination.delete();
            return false;

        } catch (java.io.IOException | InterruptedException e) {
            if (p != null) p.destroyForcibly();
            logSimple("sdr-helper exception: " + e.getMessage());
            return false;
        }
    }

    /** Locate Contents/MacOS/sdr-helper relative to the running .jar / classes dir. */
    private static File locateSdrHelper() {
        try {
            java.net.URI uri = SimpleEncoder.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File baseFile = new File(uri);
            // baseFile is either Contents/Java/Shutter Encoder Simple.jar (packaged)
            // or .../build (dev).
            File parent = baseFile.isFile() ? baseFile.getParentFile() : baseFile;
            File contents = parent.getParentFile();
            if (contents != null) {
                File helper = new File(contents, "MacOS/sdr-helper");
                if (helper.exists()) return helper;
            }
        } catch (Exception ignore) {}
        // Dev fallback — running from source / IDE
        File dev = new File("sdr-helper");
        if (dev.exists()) return dev;
        return null;
    }

    /** Reset the once-per-batch TCC notice flag — call at the start of each main() run. */
    static void resetStripHdrNotice() { stripHdrTccNoticeShown = false; }

    private static void encodeAudio(File file, String fileName, String extension, String format) throws InterruptedException {

        InputAndOutput.getInputAndOutput(VideoPlayer.getFileList(file.toString(), FFPROBE.totalLength));

        String labelOutput = getOutputFolder(file);

        String prefix = SimpleFrame.prefixText == null ? "" : SimpleFrame.prefixText.trim();
        String suffix = SimpleFrame.suffixText == null ? "" : SimpleFrame.suffixText.trim();

        String container;
        String audioCodec;

        if (format.equals("MP3"))
        {
            container = ".mp3";
            int qualityValue = SimpleFrame.qualityValue;
            int bitrate = getMP3Bitrate(qualityValue);
            audioCodec = " -c:a libmp3lame -b:a " + bitrate + "k -vn -write_id3v2 1";
        }
        else // WAV
        {
            container = ".wav";
            audioCodec = " -c:a pcm_s16le -vn -write_bext 1 -write_id3v2 1";
        }

        String fileOutputName = labelOutput.replace("\\", "/") + "/" + prefix + fileName.replace(extension, suffix + container);

        File fileOut = new File(fileOutputName);
        if (fileOut.exists())
        {
            fileOut = FunctionUtils.fileReplacement(labelOutput, fileName, extension, suffix + "_", container);

            if (fileOut == null) { cancelled = true; return; }
            if (fileOut.toString().equals("skip")) return;
        }

        String timecode = Timecode.setTimecode(file);

        String cmd = audioCodec + timecode + " -y ";

        FFMPEG.run(InputAndOutput.inPoint + " -i " + '"' + file.toString() + '"' + InputAndOutput.outPoint + cmd + '"' + fileOut + '"');

        // Wait for the FFmpeg run thread to finish, polling progressBar1 to update
        // the current file's row UI.
        do {
            Thread.sleep(150);
            try {
                int max = Shutter.progressBar1.getMaximum();
                int val = Shutter.progressBar1.getValue();
                if (max > 0 && currentFileIndex >= 0) {
                    int pct = (int) Math.min(100, val * 100L / max);
                    SimpleFrame.DropZone.setFileProgress(currentFileIndex, pct);
                }
            } catch (Exception ignore) {}
        } while (FFMPEG.runProcess.isAlive());

        if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
        {
            if (lastActions(file, fileName, fileOut, labelOutput))
                return;
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /**
     * Runs Apple's avconvert at PresetHighestQuality to produce an SDR .mov from
     * an HDR (or any) source. Returns the temp file path on success, or null on
     * failure (with FFMPEG.error left to be set by the caller).
     *
     * Using avconvert instead of ffmpeg's tonemap=hable matches the colors
     * produced by macOS-native tools — see /example/HDRStrip.swift for the same
     * approach.
     */
    private static File runAvconvertSDR(File source) {
        // Stage the source under /var/folders/.../T/ — even though that hasn't
        // proved to be the avconvert blocker on this machine, having a clean
        // path under the user's temp dir is universally readable.
        File stagedSource = stageSourceToTmp(source);
        if (stagedSource == null) return null;

        try {
            // Just try PresetHighestQuality once with a hard timeout. Other presets
            // tend to hang AVFoundation under the same conditions, and the ffmpeg
            // tonemap fallback covers the failure case anyway.
            return tryAvconvertWithPreset(stagedSource, "PresetHighestQuality");
        } finally {
            if (stagedSource != null && stagedSource.exists()) stagedSource.delete();
        }
    }

    /** Copy `source` to a fresh file in the system temp dir, preserving extension. */
    private static File stageSourceToTmp(File source) {
        try {
            String name = source.getName();
            int dot = name.lastIndexOf(".");
            String ext = (dot > 0) ? name.substring(dot) : ".mov";
            File staged = File.createTempFile("shutter-input-", ext);
            java.nio.file.Files.copy(source.toPath(), staged.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return staged;
        } catch (java.io.IOException e) {
            logSimple("stageSourceToTmp failed: " + e.getMessage());
            return null;
        }
    }

    private static File tryAvconvertWithPreset(File source, String preset) {
        File tmp = null;
        StringBuilder output = new StringBuilder();
        Process p = null;
        try {
            tmp = File.createTempFile("shutter-sdr-", ".mov");
            tmp.delete(); // avconvert wants to write a fresh file (with --replace just in case)

            ProcessBuilder pb = new ProcessBuilder(
                "/usr/bin/avconvert",
                "-s", source.getAbsolutePath(),
                "-p", preset,
                "-o", tmp.getAbsolutePath(),
                "--replace"
            );
            pb.redirectErrorStream(true);
            // Critical: discard stdin so avconvert/AVFoundation doesn't stall waiting
            // on a stdin handle inherited from the JVM. This was making the second
            // preset attempt hang indefinitely from inside the .app bundle.
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            p = pb.start();

            // Drain stdout/stderr so the process doesn't block on a full pipe.
            final Process pRef = p;
            Thread reader = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(pRef.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (output) { output.append(line).append('\n'); }
                    }
                } catch (java.io.IOException ignore) {}
            }, "avconvert-reader");
            reader.setDaemon(true);
            reader.start();

            // Hard timeout — if avconvert hasn't finished in 3 minutes, kill it
            // and let the ffmpeg tonemap fallback take over.
            boolean done = p.waitFor(3, java.util.concurrent.TimeUnit.MINUTES);
            if (!done) {
                p.destroyForcibly();
                try { reader.join(500); } catch (InterruptedException ignore) {}
                logSimple("avconvert TIMEOUT  preset=" + preset
                    + "  killed after 3 min  source=" + source.getAbsolutePath());
                if (tmp.exists()) tmp.delete();
                return null;
            }
            try { reader.join(500); } catch (InterruptedException ignore) {}

            int exit = p.exitValue();
            if (exit == 0 && tmp.exists() && tmp.length() > 0) {
                return tmp;
            }
            // Failure: log everything we know
            logSimple("avconvert FAILED  preset=" + preset
                + "  exit=" + exit
                + "  source=" + source.getAbsolutePath()
                + "  tempExists=" + tmp.exists()
                + "  tempBytes=" + (tmp.exists() ? tmp.length() : -1)
                + "\n----- avconvert output -----\n"
                + output
                + "----- end -----");
            if (tmp.exists()) tmp.delete();
            return null;

        } catch (java.io.IOException | InterruptedException e) {
            if (p != null) p.destroyForcibly();
            logSimple("avconvert exception  preset=" + preset + "  error=" + e.getMessage());
            if (tmp != null && tmp.exists()) tmp.delete();
            return null;
        }
    }

    /** Append a line to ~/Library/Logs/Shutter Encoder Simple/log.txt + stderr. */
    static void logSimple(String msg) {
        String stamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String line = "[" + stamp + "] " + msg;
        System.err.println(line);
        try {
            File dir = new File(System.getProperty("user.home"), "Library/Logs/Shutter Encoder Simple");
            dir.mkdirs();
            File log = new File(dir, "log.txt");
            try (java.io.FileWriter w = new java.io.FileWriter(log, true)) {
                w.write(line);
                w.write('\n');
            }
        } catch (Exception ignore) {}
    }

    private static String getOutputFolder(File file) {

        if (caseChangeFolder1.isSelected())
        {
            return lblDestination1.getText();
        }
        return file.getParent();
    }

    private static int mapQualityToCRF(int quality) {
        // 0 → CRF 32 (low), 100 → CRF 16 (high)
        return 32 - (int) Math.round(quality * 16.0 / 100.0);
    }

    private static int getAudioBitrate(int quality) {
        if (quality >= 75) return 256;
        if (quality >= 50) return 192;
        if (quality >= 25) return 128;
        return 96;
    }

    private static int getMP3Bitrate(int quality) {
        // Slider snaps to 25-step ticks → 5 buckets: 0/25/50/75/100 → 64/128/192/256/320 kbps
        if (quality >= 100) return 320;
        if (quality >= 75)  return 256;
        if (quality >= 50)  return 192;
        if (quality >= 25)  return 128;
        return 64;
    }

    /**
     * Returns the scale=...:flags=lanczos filter chain (no leading -vf), or empty if Keep original.
     *
     * Dimension options are height-based ("1080p", "720p", etc.). To work for both landscape
     * and portrait sources, the target value is applied to the SHORT edge — so a 720p
     * portrait stays portrait at 720×1280, and 720p landscape becomes 1280×720.
     * Aspect ratio is preserved automatically (-2 lets ffmpeg pick the other dim, even).
     */
    private static String getScaleChain() {

        String selected = SimpleFrame.dimensionsText == null ? "" : SimpleFrame.dimensionsText.trim();

        if (selected.isEmpty() || selected.equalsIgnoreCase("Keep original")) {
            return "";
        }

        // Pull the first integer (e.g. "1080p" → 1080, "4K (2160p)" → first match is 4 — guard against this)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3,5})").matcher(selected);
        Integer target = null;
        if (m.find()) {
            try { target = Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        if (target == null) return "";

        // Decide which axis the target applies to based on source orientation.
        // FFPROBE.imageWidth / imageHeight are populated by FunctionUtils.analyze.
        boolean isPortrait = FFPROBE.imageHeight > FFPROBE.imageWidth;

        if (isPortrait) {
            // Short edge is width — set width=target, height=auto
            return "scale=" + target + ":-2:flags=lanczos";
        } else {
            // Short edge is height — set height=target, width=auto
            return "scale=-2:" + target + ":flags=lanczos";
        }
    }

    /** Public helpers used by SimpleFrame's bitrate readout. */
    public static int estimateBitrate(String format, int quality) {
        if (format.equals("MP3")) return getMP3Bitrate(quality);
        if (format.equals("WAV")) return 1536;
        if (format.equals("MOV")) {
            return SimpleFrame.alphaChannel ? 110000 : 45000;
        }
        // H.264
        int crf = mapQualityToCRF(quality);
        double approxBitrate = 20000 * Math.pow(0.88, crf - 16);
        return (int) approxBitrate;
    }

    public static String estimateFileSize(String format, int quality, long durationMs) {
        if (durationMs <= 0) return "-";
        int bitrateKbps = estimateBitrate(format, quality);
        if (!format.equals("MP3") && !format.equals("WAV") && SimpleFrame.includeAudio) {
            bitrateKbps += getAudioBitrate(quality);
        }
        double sizeBytes = (double) bitrateKbps * 1000.0 / 8.0 * (durationMs / 1000.0);
        double sizeMB = sizeBytes / (1024.0 * 1024.0);
        if (sizeMB < 1)    return String.format("%.1f MB", sizeMB);
        if (sizeMB < 1024) return String.format("%.0f MB", sizeMB);
        return String.format("%.1f GB", sizeMB / 1024.0);
    }

    private static boolean lastActions(File file, String fileName, File fileOut, String output) {

        if (FunctionUtils.cleanFunction(file, fileName, fileOut, output))
            return true;

        FunctionUtils.addFileForMail(fileName);
        Ftp.sendToFtp(fileOut);
        Utils.copyFile(fileOut);

        if (SimpleFrame.openFolderWhenDone && cancelled == false && FFMPEG.error == false)
        {
            try {
                Desktop.getDesktop().open(new File(output));
            } catch (Exception e) {}
        }

        if (Shutter.scanIsRunning)
        {
            FunctionUtils.moveScannedFiles(file);
            SimpleEncoder.main();
            return true;
        }

        return false;
    }
}

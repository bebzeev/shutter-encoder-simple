/*******************************************************************************************
* Copyright (C) 2026 PACIFICO PAUL
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*
********************************************************************************************/

package functions;

import java.awt.Desktop;
import java.io.File;

import application.Ftp;
import application.RenderQueue;
import application.Settings;
import application.Shutter;
import application.SimpleEncoderPanel;
import application.Utils;
import application.VideoPlayer;
import library.FFMPEG;
import library.FFPROBE;
import settings.FunctionUtils;
import settings.InputAndOutput;
import settings.Timecode;

/**
 * Simplified encoder for performance marketing users.
 * Supports: MP4 (H.264), MOV, MP3, WAV
 */
public class SimpleEncoder extends Shutter {

	public static void main() {

		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {

				if (scanIsRunning == false)
					FunctionUtils.completed = 0;

				lblFilesEnded.setText(FunctionUtils.completedFiles(FunctionUtils.completed));

				for (int i = 0; i < list.getSize(); i++)
				{
					// Render queue only accepts selected files
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

					try {

						String fileName = file.getName();
						String extension = fileName.substring(fileName.lastIndexOf("."));

						lblCurrentEncoding.setText(fileName);

						// Data analyze
						if (FunctionUtils.analyze(file, false) == false)
							continue;

						String format = SimpleEncoderPanel.comboFormat.getSelectedItem().toString();
						boolean isAudioOnly = format.equals("MP3") || format.equals("WAV");

						if (isAudioOnly)
						{
							encodeAudio(file, fileName, extension, format);
						}
						else
						{
							encodeVideo(file, fileName, extension, format);
						}

					} catch (InterruptedException e) {
						FFMPEG.error = true;
					}
				}

				if (btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")))
				{
					VideoPlayer.videoPath = null;
					VideoPlayer.setMedia();
					do {
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {}
					} while (VideoPlayer.loadMedia.isAlive());
					RenderQueue.frame.toFront();
				}
				else
				{
					endOfFunction();
				}
			}
		});
		thread.start();
	}

	private static void encodeVideo(File file, String fileName, String extension, String format) throws InterruptedException {

		if (FFPROBE.audioOnly)
		{
			FFPROBE.imageWidth = 1920;
			FFPROBE.imageHeight = 1080;
			FFPROBE.imageResolution = "1920x1080";
		}

		// InOut
		InputAndOutput.getInputAndOutput(VideoPlayer.getFileList(file.toString(), FFPROBE.totalLength));

		// Output folder
		String labelOutput = getOutputFolder(file);

		// Suffix
		String suffix = SimpleEncoderPanel.txtSuffix.getText().trim();

		// Container
		String container = format.equals("MOV") ? ".mov" : ".mp4";

		// Output name
		String fileOutputName = labelOutput.replace("\\", "/") + "/" + fileName.replace(extension, suffix + container);

		// File output
		File fileOut = new File(fileOutputName);
		if (fileOut.exists())
		{
			fileOut = FunctionUtils.fileReplacement(labelOutput, fileName, extension, suffix + "_", container);

			if (fileOut == null)
			{
				cancelled = true;
				return;
			}
			else if (fileOut.toString().equals("skip"))
			{
				return;
			}
		}

		// Build FFmpeg command
		int qualityValue = SimpleEncoderPanel.sliderQuality.getValue();
		int crf = mapQualityToCRF(qualityValue);

		// Video codec - H.264 with maximum compatibility
		String videoCodec = " -c:v libx264 -preset medium -profile:v high -level 4.1 -pix_fmt yuv420p";
		String bitrate = " -crf " + crf;

		// Resolution
		String resolution = getResolution();

		// Audio
		String audio;
		if (SimpleEncoderPanel.chkIncludeAudio.isSelected())
		{
			int audioBitrate = getAudioBitrate(qualityValue);
			audio = " -c:a aac -b:a " + audioBitrate + "k -ar 48000";
		}
		else
		{
			audio = " -an";
		}

		// Fast start for web compatibility
		String fastStart = " -movflags +faststart";

		// Timecode
		String timecode = Timecode.setTimecode(file);

		String cmd = videoCodec + bitrate + resolution + audio + fastStart + timecode + " -y ";

		// Run FFmpeg
		FFMPEG.run(InputAndOutput.inPoint + " -i " + '"' + file.toString() + '"' + InputAndOutput.outPoint + cmd + '"' + fileOut + '"');

		do {
			Thread.sleep(100);
		} while (FFMPEG.runProcess.isAlive());

		if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
		{
			if (lastActions(file, fileName, fileOut, labelOutput))
				return;
		}
	}

	private static void encodeAudio(File file, String fileName, String extension, String format) throws InterruptedException {

		// InOut
		InputAndOutput.getInputAndOutput(VideoPlayer.getFileList(file.toString(), FFPROBE.totalLength));

		// Output folder
		String labelOutput = getOutputFolder(file);

		// Suffix
		String suffix = SimpleEncoderPanel.txtSuffix.getText().trim();

		// Container and codec
		String container;
		String audioCodec;

		if (format.equals("MP3"))
		{
			container = ".mp3";
			int qualityValue = SimpleEncoderPanel.sliderQuality.getValue();
			int bitrate = getMP3Bitrate(qualityValue);
			audioCodec = " -c:a libmp3lame -b:a " + bitrate + "k -vn -write_id3v2 1";
		}
		else // WAV
		{
			container = ".wav";
			audioCodec = " -c:a pcm_s16le -vn -write_bext 1 -write_id3v2 1";
		}

		// Output name
		String fileOutputName = labelOutput.replace("\\", "/") + "/" + fileName.replace(extension, suffix + container);

		// File output
		File fileOut = new File(fileOutputName);
		if (fileOut.exists())
		{
			fileOut = FunctionUtils.fileReplacement(labelOutput, fileName, extension, suffix + "_", container);

			if (fileOut == null)
			{
				cancelled = true;
				return;
			}
			else if (fileOut.toString().equals("skip"))
			{
				return;
			}
		}

		// Timecode
		String timecode = Timecode.setTimecode(file);

		String cmd = audioCodec + timecode + " -y ";

		// Run FFmpeg
		FFMPEG.run(InputAndOutput.inPoint + " -i " + '"' + file.toString() + '"' + InputAndOutput.outPoint + cmd + '"' + fileOut + '"');

		do {
			Thread.sleep(100);
		} while (FFMPEG.runProcess.isAlive());

		if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
		{
			if (lastActions(file, fileName, fileOut, labelOutput))
				return;
		}
	}

	private static String getOutputFolder(File file) {

		if (caseChangeFolder1.isSelected())
		{
			return lblDestination1.getText();
		}
		else
		{
			return file.getParent();
		}
	}

	/**
	 * Maps the quality slider (0-100) to H.264 CRF values.
	 * CRF 0 = lossless, 51 = worst. We map 0-100 slider to CRF 32-16.
	 * Lower CRF = higher quality.
	 */
	private static int mapQualityToCRF(int quality) {
		// quality 0 = CRF 32 (low quality), quality 100 = CRF 16 (high quality)
		return 32 - (int) Math.round(quality * 16.0 / 100.0);
	}

	/**
	 * Gets audio bitrate based on quality slider for video formats.
	 */
	private static int getAudioBitrate(int quality) {
		if (quality >= 75)
			return 256;
		else if (quality >= 50)
			return 192;
		else if (quality >= 25)
			return 128;
		else
			return 96;
	}

	/**
	 * Gets MP3 bitrate based on quality slider.
	 */
	private static int getMP3Bitrate(int quality) {
		if (quality >= 75)
			return 320;
		else if (quality >= 50)
			return 192;
		else if (quality >= 25)
			return 128;
		else
			return 96;
	}

	/**
	 * Gets resolution filter string from the dimensions combo.
	 */
	private static String getResolution() {

		String selected = SimpleEncoderPanel.comboDimensions.getSelectedItem().toString();

		if (selected.equals(Shutter.language.getProperty("simpleEncoderKeepOriginal")))
		{
			return "";
		}

		// Parse WxH format
		if (selected.contains("x"))
		{
			String[] parts = selected.split("x");
			return " -vf scale=" + parts[0] + ":" + parts[1] + ":flags=lanczos" + " -sws_flags lanczos";
		}

		// Parse "auto:H" format (e.g. "1920:auto")
		if (selected.contains(":"))
		{
			String[] parts = selected.split(":");
			if (parts[0].equals("auto"))
				return " -vf scale=-2:" + parts[1] + ":flags=lanczos";
			else
				return " -vf scale=" + parts[0] + ":-2:flags=lanczos";
		}

		return "";
	}

	/**
	 * Estimates the video bitrate in kbps for display purposes.
	 */
	public static int estimateBitrate(String format, int quality) {

		if (format.equals("MP3"))
		{
			return getMP3Bitrate(quality);
		}
		else if (format.equals("WAV"))
		{
			return 1536; // 16-bit 48kHz stereo
		}
		else
		{
			// Video: approximate average bitrate from CRF
			// CRF 16 ~ 15000kbps, CRF 32 ~ 500kbps (rough estimates for 1080p)
			int crf = mapQualityToCRF(quality);
			double approxBitrate = 20000 * Math.pow(0.88, crf - 16);
			return (int) approxBitrate;
		}
	}

	/**
	 * Estimates file size in MB.
	 */
	public static String estimateFileSize(String format, int quality, long durationMs) {

		if (durationMs <= 0)
			return "-";

		int bitrateKbps = estimateBitrate(format, quality);

		if (!format.equals("MP3") && !format.equals("WAV"))
		{
			// Add audio bitrate
			if (SimpleEncoderPanel.chkIncludeAudio.isSelected())
			{
				bitrateKbps += getAudioBitrate(quality);
			}
		}

		double sizeBytes = (double) bitrateKbps * 1000.0 / 8.0 * (durationMs / 1000.0);
		double sizeMB = sizeBytes / (1024.0 * 1024.0);

		if (sizeMB < 1)
			return String.format("%.1f MB", sizeMB);
		else if (sizeMB < 1024)
			return String.format("%.0f MB", sizeMB);
		else
			return String.format("%.1f GB", sizeMB / 1024.0);
	}

	private static boolean lastActions(File file, String fileName, File fileOut, String output) {

		if (FunctionUtils.cleanFunction(file, fileName, fileOut, output))
			return true;

		// Sending processes
		FunctionUtils.addFileForMail(fileName);
		Ftp.sendToFtp(fileOut);
		Utils.copyFile(fileOut);

		// Open destination folder
		if (SimpleEncoderPanel.chkOpenFolder.isSelected() && cancelled == false && FFMPEG.error == false)
		{
			try {
				Desktop.getDesktop().open(new File(output));
			} catch (Exception e) {}
		}

		// Watch folder
		if (Shutter.scanIsRunning)
		{
			FunctionUtils.moveScannedFiles(file);
			SimpleEncoder.main();
			return true;
		}

		return false;
	}
}

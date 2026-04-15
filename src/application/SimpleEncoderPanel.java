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

package application;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.formdev.flatlaf.ui.FlatLineBorder;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import library.FFPROBE;

/**
 * Simplified encoder settings panel for performance marketing users.
 * Provides only the essential controls: format, quality, dimensions, audio toggle.
 */
@SuppressWarnings("serial")
public class SimpleEncoderPanel extends Shutter {

	public static JPanel panel;

	// Format
	public static JComboBox<String> comboFormat;

	// Quality
	public static JSlider sliderQuality;
	private static JLabel lblQualityValue;
	private static JLabel lblBitratePreview;
	private static JLabel lblFileSizeEstimate;

	// Dimensions
	public static JComboBox<String> comboDimensions;

	// Audio toggle
	public static JCheckBox chkIncludeAudio;

	// Suffix
	public static JTextField txtSuffix;

	// Open folder at end
	public static JCheckBox chkOpenFolder;

	public static void init() {

		panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(Utils.c25);
				g2.fillRoundRect(2, 9, getWidth() - 4, getHeight() - 11, 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		panel.setLayout(null);
		panel.setVisible(false);
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createTitledBorder(
				new FlatLineBorder(new Insets(0, 0, 0, 0), Utils.c42, 1, 10),
				language.getProperty("simpleEncoder") + " ",
				0, 0, new Font(boldFont, Font.PLAIN, 13),
				new Color(235, 235, 240)));
		panel.setBackground(Utils.c30);
		panel.setBounds(658, 30, 312, 310);
		frame.getContentPane().add(panel);

		int y = 22;
		int labelX = 12;
		int controlX = 120;
		int controlWidth = 180;
		int rowHeight = 28;

		// --- Format ---
		JLabel lblFormat = new JLabel(language.getProperty("simpleEncoderFormat"));
		lblFormat.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblFormat.setBounds(labelX, y, 100, 16);
		panel.add(lblFormat);

		comboFormat = new JComboBox<String>();
		comboFormat.setName("comboSimpleFormat");
		comboFormat.setModel(new DefaultComboBoxModel<String>(new String[] {
				"MP4 (H.264)", "MOV", "MP3", "WAV" }));
		comboFormat.setSelectedIndex(0);
		comboFormat.setFont(new Font(mainFont, Font.PLAIN, 11));
		comboFormat.setBounds(controlX, y - 3, controlWidth, 22);
		panel.add(comboFormat);

		y += rowHeight;

		// --- Quality slider ---
		JLabel lblQuality = new JLabel(language.getProperty("simpleEncoderQuality"));
		lblQuality.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblQuality.setBounds(labelX, y, 100, 16);
		panel.add(lblQuality);

		sliderQuality = new JSlider(0, 100, 65);
		sliderQuality.setName("sliderSimpleQuality");
		sliderQuality.setFont(new Font(mainFont, Font.PLAIN, 10));
		sliderQuality.setOpaque(false);
		sliderQuality.setBounds(controlX, y - 3, controlWidth, 22);
		panel.add(sliderQuality);

		y += rowHeight - 4;

		lblQualityValue = new JLabel(getQualityLabel(65));
		lblQualityValue.setFont(new Font(mainFont, Font.PLAIN, 11));
		lblQualityValue.setForeground(Utils.themeColor);
		lblQualityValue.setHorizontalAlignment(SwingConstants.LEFT);
		lblQualityValue.setBounds(controlX, y, controlWidth, 16);
		panel.add(lblQualityValue);

		y += 20;

		// --- Bitrate preview ---
		JLabel lblBitrateLbl = new JLabel(language.getProperty("simpleEncoderBitratePreview"));
		lblBitrateLbl.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblBitrateLbl.setBounds(labelX, y, 110, 16);
		panel.add(lblBitrateLbl);

		lblBitratePreview = new JLabel(formatBitrate(getSelectedFormat(), 65));
		lblBitratePreview.setFont(new Font(mainFont, Font.PLAIN, 11));
		lblBitratePreview.setForeground(Color.LIGHT_GRAY);
		lblBitratePreview.setBounds(controlX, y, controlWidth, 16);
		panel.add(lblBitratePreview);

		y += 20;

		// --- File size estimate ---
		JLabel lblSizeLbl = new JLabel(language.getProperty("simpleEncoderFileSize"));
		lblSizeLbl.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblSizeLbl.setBounds(labelX, y, 110, 16);
		panel.add(lblSizeLbl);

		lblFileSizeEstimate = new JLabel("-");
		lblFileSizeEstimate.setFont(new Font(mainFont, Font.PLAIN, 11));
		lblFileSizeEstimate.setForeground(Color.LIGHT_GRAY);
		lblFileSizeEstimate.setBounds(controlX, y, controlWidth, 16);
		panel.add(lblFileSizeEstimate);

		y += rowHeight;

		// --- Dimensions ---
		JLabel lblDimensions = new JLabel(language.getProperty("simpleEncoderDimensions"));
		lblDimensions.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblDimensions.setBounds(labelX, y, 100, 16);
		panel.add(lblDimensions);

		comboDimensions = new JComboBox<String>();
		comboDimensions.setName("comboSimpleDimensions");
		comboDimensions.setModel(new DefaultComboBoxModel<String>(new String[] {
				language.getProperty("simpleEncoderKeepOriginal"),
				"3840x2160", "1920x1080", "1280x720", "854x480", "640x360" }));
		comboDimensions.setSelectedIndex(0);
		comboDimensions.setEditable(true);
		comboDimensions.setFont(new Font(mainFont, Font.PLAIN, 11));
		comboDimensions.setBounds(controlX, y - 3, controlWidth, 22);
		panel.add(comboDimensions);

		y += rowHeight;

		// --- Include audio ---
		chkIncludeAudio = new JCheckBox(language.getProperty("simpleEncoderIncludeAudio"));
		chkIncludeAudio.setName("chkSimpleIncludeAudio");
		chkIncludeAudio.setSelected(true);
		chkIncludeAudio.setFont(new Font(mainFont, Font.PLAIN, 12));
		chkIncludeAudio.setBounds(labelX - 4, y, 200, 23);
		panel.add(chkIncludeAudio);

		y += rowHeight;

		// --- Suffix ---
		JLabel lblSuffix = new JLabel(language.getProperty("simpleEncoderSuffix"));
		lblSuffix.setFont(new Font(mainFont, Font.PLAIN, 12));
		lblSuffix.setBounds(labelX, y, 110, 16);
		panel.add(lblSuffix);

		txtSuffix = new JTextField("_converted");
		txtSuffix.setName("txtSimpleSuffix");
		txtSuffix.setFont(new Font(mainFont, Font.PLAIN, 11));
		txtSuffix.setBounds(controlX, y - 3, controlWidth, 22);
		panel.add(txtSuffix);

		y += rowHeight;

		// --- Open folder at end ---
		chkOpenFolder = new JCheckBox(language.getProperty("caseOpenFolderAtEnd"));
		chkOpenFolder.setName("chkSimpleOpenFolder");
		chkOpenFolder.setSelected(true);
		chkOpenFolder.setFont(new Font(mainFont, Font.PLAIN, 12));
		chkOpenFolder.setBounds(labelX - 4, y, 290, 23);
		panel.add(chkOpenFolder);

		// --- Listeners ---

		comboFormat.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED)
				{
					updateFormatDependentUI();
					updateEstimates();
				}
			}
		});

		sliderQuality.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int val = sliderQuality.getValue();
				lblQualityValue.setText(getQualityLabel(val));
				updateEstimates();
			}
		});

		chkIncludeAudio.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateEstimates();
			}
		});

		comboDimensions.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateEstimates();
			}
		});
	}

	/**
	 * Gets the internal format string (MP4, MOV, MP3, WAV) from the combo selection.
	 */
	public static String getSelectedFormat() {
		String selected = comboFormat.getSelectedItem().toString();
		if (selected.contains("H.264"))
			return "MP4";
		return selected;
	}

	/**
	 * Returns true if the current format is audio-only.
	 */
	public static boolean isAudioFormat() {
		String format = getSelectedFormat();
		return format.equals("MP3") || format.equals("WAV");
	}

	/**
	 * Shows/hides controls based on format selection.
	 */
	private static void updateFormatDependentUI() {

		boolean isAudio = isAudioFormat();
		boolean isWAV = getSelectedFormat().equals("WAV");

		comboDimensions.setEnabled(!isAudio);
		chkIncludeAudio.setEnabled(!isAudio);
		sliderQuality.setEnabled(!isWAV);

		if (isAudio) {
			chkIncludeAudio.setSelected(false);
			chkIncludeAudio.setEnabled(false);
			comboDimensions.setSelectedIndex(0);
		} else {
			chkIncludeAudio.setSelected(true);
			chkIncludeAudio.setEnabled(true);
		}
	}

	/**
	 * Updates the bitrate preview and file size estimate.
	 */
	public static void updateEstimates() {

		if (comboFormat == null)
			return;

		String format = getSelectedFormat();
		int quality = sliderQuality.getValue();

		// Bitrate preview
		lblBitratePreview.setText(formatBitrate(format, quality));

		// File size estimate
		long durationMs = 0;
		if (Shutter.list.getSize() > 0 && FFPROBE.totalLength > 0)
		{
			durationMs = FFPROBE.totalLength;
		}

		lblFileSizeEstimate.setText(
				functions.SimpleEncoder.estimateFileSize(format, quality, durationMs));
	}

	private static String getQualityLabel(int val) {
		if (val >= 75) return language.getProperty("simpleEncoderMaximum");
		if (val >= 50) return language.getProperty("simpleEncoderHigh");
		if (val >= 25) return language.getProperty("simpleEncoderMedium");
		return language.getProperty("simpleEncoderLow");
	}

	private static String formatBitrate(String format, int quality) {
		int bitrate = functions.SimpleEncoder.estimateBitrate(format, quality);
		if (bitrate >= 1000) {
			return String.format("~%.1f Mbps", bitrate / 1000.0);
		}
		return "~" + bitrate + " kbps";
	}
}

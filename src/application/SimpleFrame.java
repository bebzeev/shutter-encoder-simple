/*******************************************************************************************
* Shutter Simple — sage horizontal frame
*
* Standalone simplified UI that replaces the legacy Shutter window for non-technical
* users. Three blocks (drop+output / dimensions+toggles / files+destination) on a
* sage paper palette with hairline dividers.
********************************************************************************************/

package application;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.ui.FlatLineBorder;

import functions.SimpleEncoder;

@SuppressWarnings("serial")
public class SimpleFrame {

    // ── palette ──────────────────────────────────────────────────────────────
    static final Color PAPER       = new Color(0xDD, 0xE3, 0xCB);
    static final Color PAPER_2     = new Color(0xE6, 0xEC, 0xD5);
    static final Color INK         = new Color(0x11, 0x11, 0x11);
    static final Color MUTED       = new Color(0x6A, 0x6E, 0x5A);
    static final Color HAIR        = new Color(0x1A, 0x1A, 0x1A);
    static final Color SOFT        = new Color(0xC8, 0xCF, 0xB3);
    static final Color DOT_CLOSE   = new Color(0xC8, 0x62, 0x3C);
    static final Color DOT_MIN     = new Color(0xC9, 0xA8, 0x4B);
    static final Color DOT_MAX     = new Color(0x82, 0x90, 0x55);

    // ── fonts ────────────────────────────────────────────────────────────────
    static Font fontBody;
    static Font fontBodyBold;
    static Font fontMono;
    static Font fontMonoBold;

    // ── frame & components ──────────────────────────────────────────────────
    public static JFrame frame;
    static JPanel cardPanel;
    static JPanel headerPanel;
    static JPanel block1Panel;
    static JPanel block1Right;
    static JPanel block2Panel;
    static JComponent block2Hairline;
    static JPanel block3Panel;
    static JPanel alphaRow;
    static FormatChips formatChips;
    static ToggleSwitch alphaSwitch;
    static JLabel       alphaInlineLabel;
    static ToggleSwitch audioSwitch;
    static JLabel       audioInlineLabel;
    static ToggleSwitch sdrSwitch;
    static ToggleSwitch openFolderSwitch;
    static JComboBox<String> dimensionsCombo;
    static JComboBox<String> destinationCombo;
    static JTextField prefixField;
    static JTextField suffixField;
    static JSlider qualitySlider;
    static JLabel qualityNumLabel;
    static JLabel qualityTagLabel;
    static JLabel qualityLosslessLabel;
    static JPanel bitrateRow;
    static JPanel dimensionsRow;
    static JLabel formatReadoutLabel;
    static JLabel bitrateValueLabel;
    static JLabel summaryLabel;
    static JLabel filesCountLabel;
    static JButton convertButton;
    static DropZone dropZone;

    // ── state ──────────────────────────────────────────────────────────────
    public static String  selectedFormat        = "MP4";
    public static int     qualityValue          = 65;
    public static boolean alphaChannel          = false;
    public static boolean includeAudio          = true;
    public static boolean stripHDR              = false;
    public static boolean openFolderWhenDone    = true;
    public static String  prefixText            = "";
    public static String  suffixText            = "";
    public static String  dimensionsText        = "Keep original";
    public static String  destinationPath       = null;   // null → "Same as source"

    public static final String MODE_ENCODE    = "ENCODE";
    public static final String MODE_STRIP_HDR = "STRIP HDR";
    public static String  currentMode         = MODE_ENCODE;

    static boolean lastWasAudio = false;
    static boolean destinationComboGuard = false;        // suppresses re-entrant action events
    static ModeTabs modeTabs;

    // Height-based; aspect ratio is preserved automatically.
    // For portrait videos the value is treated as the SHORT edge so "720p" still
    // means roughly the same visual size for both landscape and portrait sources.
    static final String[] DIMENSION_OPTIONS = new String[] {
        "Keep original",
        "4K (2160p)",
        "1080p",
        "720p",
        "480p",
        "360p",
    };

    // block heights used to compute window size on format change
    static final int H_HEADER       = 56;
    static final int H_HAIRLINE     = 1;
    // Tight fit for the densest format (MOV: Format + Alpha + Quality + Bitrate/Audio + Dimensions = 5 rows)
    static final int H_BLOCK1_FULL  = 210;
    static final int H_BLOCK1_MOV   = 210;
    static final int H_BLOCK1_AUDIO = 210;
    static final int H_BLOCK2       = 0;     // block 2 is gone; constant kept for arithmetic safety
    static final int H_BLOCK3       = 130;
    static final int H_FOOTER       = 56;

    // ─────────────────────────────────────────────────────────────────────────

    public SimpleFrame() {
        loadFonts();
        installFlatLafOverrides();
        installDockIcon();
        buildUI();
    }

    /** Set the macOS dock icon (and the cross-platform taskbar icon) to our logo. */
    static void installDockIcon() {
        try {
            java.net.URL iconURL = SimpleFrame.class.getClassLoader().getResource("contents/icon.png");
            if (iconURL == null) return;
            java.awt.Image dockIcon = new javax.swing.ImageIcon(iconURL).getImage();
            try {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(dockIcon);
                }
            } catch (UnsupportedOperationException ignore) {}
        } catch (Throwable ignore) {}
    }

    static void loadFonts() {
        fontBody     = systemFontOrFallback("Space Grotesk", Font.SANS_SERIF, Font.PLAIN, 13f);
        fontBodyBold = systemFontOrFallback("Space Grotesk", Font.SANS_SERIF, Font.BOLD,  13f);
        fontMono     = loadTrueTypeOrFallback("fonts/SpaceMono-Regular.ttf", Font.MONOSPACED, Font.PLAIN, 12f);
        fontMonoBold = loadTrueTypeOrFallback("fonts/SpaceMono-Bold.ttf",    Font.MONOSPACED, Font.BOLD,  12f);
    }

    static Font systemFontOrFallback(String preferred, String fallbackName, int style, float size) {
        Font f = new Font(preferred, style, (int) size);
        if (f.getFamily().equalsIgnoreCase(preferred)) return f.deriveFont(style, size);
        return new Font(fallbackName, style, (int) size).deriveFont(size);
    }

    static Font loadTrueTypeOrFallback(String relativePath, String fallbackName, int style, float size) {
        try {
            File candidate = resolveBundled(relativePath);
            if (candidate != null && candidate.exists()) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, candidate);
                return f.deriveFont(style, size);
            }
        } catch (Exception ignore) {}
        return new Font(fallbackName, style, (int) size).deriveFont(size);
    }

    static File resolveBundled(String relativePath) {
        try {
            String base = SimpleFrame.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File baseFile = new File(base);
            File parent = baseFile.isFile() ? baseFile.getParentFile() : baseFile;
            File candidate = new File(parent, relativePath);
            if (candidate.exists()) return candidate;
            File cwd = new File(relativePath);
            if (cwd.exists()) return cwd;
        } catch (Exception ignore) {}
        return null;
    }

    static void installFlatLafOverrides() {
        // Match form-control colors to the sage palette
        UIManager.put("ComboBox.background",                PAPER);
        UIManager.put("ComboBox.foreground",                INK);
        UIManager.put("ComboBox.borderColor",               HAIR);
        UIManager.put("ComboBox.buttonArrowColor",          INK);
        UIManager.put("ComboBox.buttonHoverArrowColor",     INK);
        UIManager.put("ComboBox.buttonPressedArrowColor",   INK);
        UIManager.put("ComboBox.buttonDisabledArrowColor",  MUTED);
        UIManager.put("ComboBox.buttonBackground",          PAPER);
        UIManager.put("ComboBox.buttonHoverBackground",     SOFT);
        UIManager.put("ComboBox.editableBackground",        PAPER);
        UIManager.put("ComboBox.popupBackground",           PAPER);
        UIManager.put("ComboBox.selectionBackground",       INK);
        UIManager.put("ComboBox.selectionForeground",       PAPER);

        UIManager.put("TextField.background",               PAPER);
        UIManager.put("TextField.foreground",               INK);
        UIManager.put("TextField.borderColor",              HAIR);
        UIManager.put("TextField.focusedBorderColor",       INK);
        UIManager.put("TextField.caretForeground",          INK);
        UIManager.put("TextField.selectionBackground",      INK);
        UIManager.put("TextField.selectionForeground",      PAPER);

        UIManager.put("Slider.thumbColor",                  INK);
        UIManager.put("Slider.trackColor",                  HAIR);
        UIManager.put("Slider.trackValueColor",             INK);
    }

    // ── frame construction ──────────────────────────────────────────────────

    void buildUI() {
        frame = new JFrame("Shutter Encoder Simple");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        // Opaque PAPER background — no transparent compositing, no shape clipping.
        // Rounded corners + transparency cause flicker and pixelation during
        // resize/relayout on macOS, so we go with a clean rectangular frame.
        frame.setBackground(PAPER);
        frame.getRootPane().setBorder(null);
        frame.getContentPane().setBackground(PAPER);

        cardPanel = new JPanel();
        cardPanel.setBackground(PAPER);
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));

        headerPanel = buildHeader();
        cardPanel.add(headerPanel);
        // (no hairline between header and block 1 — title sits flush with the content)
        block1Panel = buildBlock1();
        cardPanel.add(block1Panel);
        cardPanel.add(hairline());
        // block 2 (dimensions/audio/SDR) is gone — those controls now live in block 1
        // right side (dimensions row + audio toggle inline with bitrate). Strip HDR
        // tab handles the SDR conversion mode separately.
        block2Panel    = new JPanel(); block2Panel.setVisible(false);
        block2Hairline = (JComponent) hairline(); block2Hairline.setVisible(false);
        block3Panel = buildBlock3();
        cardPanel.add(block3Panel);
        cardPanel.add(hairline());
        cardPanel.add(buildFooter());

        frame.setContentPane(cardPanel);

        // Default size — width matches the minimum (700) so the app opens compact;
        // user can drag the left/right edges to enlarge.
        int initialHeight = computeTargetHeight();
        int w = 700;
        frame.setResizable(true);
        frame.setSize(w, initialHeight);
        frame.setMinimumSize(new Dimension(700, 0));
        frame.setLocationRelativeTo(null);

        // Rounded corners. With OPAQUE PAPER background, setShape clips cleanly without
        // the transparent-shape flicker we saw before.
        frame.setShape(new RoundRectangle2D.Float(0, 0, w, initialHeight, 18, 18));
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                frame.setShape(new RoundRectangle2D.Float(0, 0, frame.getWidth(), frame.getHeight(), 18, 18));
            }
        });

        // Width-only resize handle on the right edge (height is content-driven)
        WidthResizer resizer = new WidthResizer();
        cardPanel.addMouseListener(resizer);
        cardPanel.addMouseMotionListener(resizer);

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { Settings.saveSettings(); }
        });

        refreshFormatDependentUI();

        frame.setVisible(true);
        frame.toFront();
    }

    static int computeTargetHeight() {
        // Block 2 is gone. Window is just header + block 1 + block 3 + footer + hairlines.
        return H_HEADER
             + H_BLOCK1_FULL + H_HAIRLINE
             + H_BLOCK3 + H_HAIRLINE
             + H_FOOTER;
    }

    /**
     * Apply the current mode (ENCODE / STRIP HDR) to the layout:
     * - STRIP HDR: collapse block 1 right column (drop zone spans full width),
     *   hide block 2 entirely.
     * - ENCODE: restore the standard two-column block 1 + block 2 visibility per format.
     */
    static void applyModeChange() {
        boolean isStripHDR = MODE_STRIP_HDR.equals(currentMode);

        if (block1Panel != null && block1Right != null && dropZone != null) {
            GridBagLayout layout = (GridBagLayout) block1Panel.getLayout();
            boolean rightAttached = (block1Right.getParent() == block1Panel);

            if (isStripHDR && rightAttached) {
                // Remove right side, make drop zone span both columns
                block1Panel.remove(block1Right);
                GridBagConstraints gc = layout.getConstraints(dropZone);
                gc.gridwidth = 2;
                gc.weightx = 1;
                gc.insets = new Insets(14, 22, 14, 22);
                layout.setConstraints(dropZone, gc);
            } else if (!isStripHDR && !rightAttached) {
                // Restore: drop zone single column + block1Right back in column 1
                GridBagConstraints gc = layout.getConstraints(dropZone);
                gc.gridwidth = 1;
                gc.insets = new Insets(14, 22, 14, 11);
                layout.setConstraints(dropZone, gc);

                GridBagConstraints rightGc = new GridBagConstraints();
                rightGc.gridx = 1; rightGc.gridy = 0;
                rightGc.weightx = 1.4; rightGc.weighty = 1;
                rightGc.fill = GridBagConstraints.BOTH;
                rightGc.insets = new Insets(8, 11, 8, 22);
                block1Panel.add(block1Right, rightGc);
            }
        }

        // STRIP HDR mode: also hide block 2 outright; ENCODE mode lets the
        // format-driven logic in refreshFormatDependentUI decide.
        if (isStripHDR) {
            if (block2Panel != null) block2Panel.setVisible(false);
            if (block2Hairline != null) block2Hairline.setVisible(false);
        }

        refreshFormatDependentUI();
    }

    // ── header (custom titlebar with traffic-light controls) ─────────────────

    JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PAPER);
        p.setBorder(new EmptyBorder(10, 16, 10, 22));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_HEADER));
        p.setPreferredSize(new Dimension(0, H_HEADER));
        p.setMinimumSize(new Dimension(0, H_HEADER));

        // West: window controls + title
        JPanel west = new JPanel();
        west.setOpaque(false);
        west.setLayout(new BoxLayout(west, BoxLayout.X_AXIS));

        WindowDot close = new WindowDot(DOT_CLOSE, "×", () -> System.exit(0));
        WindowDot min   = new WindowDot(DOT_MIN,   "−", () -> frame.setState(JFrame.ICONIFIED));
        WindowDot max   = new WindowDot(DOT_MAX,   "□", this::toggleMaximized);
        west.add(close); west.add(Box.createHorizontalStrut(8));
        west.add(min);   west.add(Box.createHorizontalStrut(8));
        west.add(max);
        west.add(Box.createHorizontalStrut(20));

        JLabel title = new JLabel("Shutter Encoder Simple");
        title.setFont(fontBody.deriveFont(Font.PLAIN, 17f));
        title.setForeground(INK);
        west.add(title);

        // Center: ENCODE / STRIP HDR mode tabs
        modeTabs = new ModeTabs();
        modeTabs.onChange = () -> {
            currentMode = modeTabs.selectedMode();
            applyModeChange();
        };
        JPanel center = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        center.setOpaque(false);
        center.add(modeTabs);

        // East: meta (no version)
        filesCountLabel = new JLabel("IDLE  ·  0 FILES");
        filesCountLabel.setFont(fontMonoBold.deriveFont(11f));
        filesCountLabel.setForeground(MUTED);

        p.add(west, BorderLayout.WEST);
        p.add(center, BorderLayout.CENTER);
        p.add(filesCountLabel, BorderLayout.EAST);

        // Window dragging (click-drag anywhere on header that isn't the dots)
        WindowDragger dragger = new WindowDragger();
        p.addMouseListener(dragger);
        p.addMouseMotionListener(dragger);
        title.addMouseListener(dragger);
        title.addMouseMotionListener(dragger);

        return p;
    }

    void toggleMaximized() {
        int state = frame.getExtendedState();
        if ((state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
            frame.setExtendedState(JFrame.NORMAL);
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    // ── block 1 ─────────────────────────────────────────────────────────────

    JPanel buildBlock1() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PAPER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_BLOCK1_FULL));
        p.setPreferredSize(new Dimension(0, H_BLOCK1_FULL));
        p.setMinimumSize(new Dimension(0, H_BLOCK1_AUDIO));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;

        gc.gridx = 0;
        gc.weightx = 1;
        gc.insets = new Insets(14, 22, 14, 11);
        dropZone = new DropZone();
        p.add(dropZone, gc);

        gc.gridx = 1;
        gc.weightx = 1.4;
        gc.insets = new Insets(8, 11, 8, 22);
        block1Right = buildBlock1Right();
        p.add(block1Right, gc);

        return p;
    }

    JPanel buildBlock1Right() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(PAPER);

        // Format row — readout has fixed width to prevent chip bouncing
        formatChips = new FormatChips();
        formatReadoutLabel = new JLabel("PRORES 422");
        formatReadoutLabel.setFont(fontMonoBold.deriveFont(11f));
        formatReadoutLabel.setForeground(MUTED);
        formatReadoutLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // Compact + shrinkable; truncates rather than overflows the row at narrow widths
        formatReadoutLabel.setPreferredSize(new Dimension(100, 18));
        formatReadoutLabel.setMinimumSize(new Dimension(60, 18));
        formatReadoutLabel.setMaximumSize(new Dimension(120, 18));
        right.add(buildSubRow("Format", formatChips, formatReadoutLabel, false));

        // Alpha row (separate row, visible only for MOV)
        alphaSwitch = new ToggleSwitch(false, () -> {
            alphaChannel = alphaSwitch.isOn();
            refreshFormatDependentUI();
        });
        alphaRow = buildSubRow("Alpha Ch.", wrapLeft(alphaSwitch), null, false);
        alphaRow.setVisible(false);
        right.add(alphaRow);

        // Quality row
        qualitySlider = new JSlider(0, 100, qualityValue);
        qualitySlider.setOpaque(false);
        qualitySlider.setBackground(PAPER);
        qualitySlider.setForeground(INK);
        qualitySlider.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(ChangeEvent e) {
                qualityValue = qualitySlider.getValue();
                refreshFormatDependentUI();
            }
        });
        qualityNumLabel = new JLabel("65");
        qualityNumLabel.setFont(fontMonoBold.deriveFont(13f));
        qualityNumLabel.setForeground(INK);
        qualityTagLabel = new JLabel("HIGH");
        qualityTagLabel.setFont(fontMonoBold.deriveFont(11f));
        qualityTagLabel.setForeground(MUTED);
        qualityLosslessLabel = new JLabel("LOSSLESS");
        qualityLosslessLabel.setFont(fontMonoBold.deriveFont(12f));
        qualityLosslessLabel.setForeground(INK);
        qualityLosslessLabel.setVisible(false);

        // Layout: slider expands → pushes num + tag flush right
        JPanel qualityControls = new JPanel();
        qualityControls.setLayout(new BoxLayout(qualityControls, BoxLayout.X_AXIS));
        qualityControls.setOpaque(false);
        qualityControls.add(qualitySlider);
        qualityControls.add(Box.createHorizontalStrut(10));
        qualityControls.add(qualityNumLabel);
        qualityControls.add(Box.createHorizontalStrut(8));
        qualityControls.add(qualityTagLabel);
        qualityControls.add(qualityLosslessLabel);
        right.add(buildSubRow("Quality", qualityControls, null, true));

        // Bitrate row: bitrate value on the left, AUDIO toggle inline on the right
        bitrateValueLabel = new JLabel("~9.3 Mbps");
        bitrateValueLabel.setFont(fontBody.deriveFont(Font.PLAIN, 16f));
        bitrateValueLabel.setForeground(INK);

        audioSwitch = new ToggleSwitch(true, () -> {
            includeAudio = audioSwitch.isOn();
            refreshFormatDependentUI();
        });
        // Allocated for legacy compatibility; never added to UI now.
        sdrSwitch = new ToggleSwitch(false, () -> { stripHDR = sdrSwitch.isOn(); });

        audioInlineLabel = new JLabel("AUDIO");
        audioInlineLabel.setFont(fontMonoBold.deriveFont(10f));
        audioInlineLabel.setForeground(MUTED);
        audioInlineLabel.setName("audioInlineLabel");

        JPanel bitrateAudioControls = new JPanel();
        bitrateAudioControls.setLayout(new BoxLayout(bitrateAudioControls, BoxLayout.X_AXIS));
        bitrateAudioControls.setOpaque(false);
        bitrateAudioControls.add(bitrateValueLabel);
        bitrateAudioControls.add(Box.createHorizontalGlue());
        bitrateAudioControls.add(audioInlineLabel);
        bitrateAudioControls.add(Box.createHorizontalStrut(12));
        bitrateAudioControls.add(audioSwitch);

        bitrateRow = buildSubRow("Bitrate", bitrateAudioControls, null, true);
        right.add(bitrateRow);

        // Dimensions row (last sub-row in block 1 right)
        dimensionsCombo = styledCombo(DIMENSION_OPTIONS);
        dimensionsCombo.addActionListener(e -> {
            dimensionsText = (String) dimensionsCombo.getSelectedItem();
            refreshFormatDependentUI();
        });
        dimensionsRow = buildSubRow("Dimensions", dimensionsCombo, null, true);
        right.add(dimensionsRow);

        return right;
    }

    JPanel wrapLeft(JComponent c) {
        JPanel w = new JPanel();
        w.setLayout(new BoxLayout(w, BoxLayout.X_AXIS));
        w.setOpaque(false);
        c.setAlignmentY(Component.CENTER_ALIGNMENT);
        w.add(c);
        w.add(Box.createHorizontalGlue());
        return w;
    }

    JPanel buildSubRow(String labelText, JComponent control, JLabel readout, boolean stretchControl) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setBorder(new EmptyBorder(6, 0, 6, 0));

        JLabel lbl = new JLabel(labelText.toUpperCase());
        lbl.setFont(fontMonoBold.deriveFont(10f));
        lbl.setForeground(MUTED);
        lbl.setPreferredSize(new Dimension(74, 18));
        lbl.setMinimumSize(new Dimension(74, 18));
        lbl.setMaximumSize(new Dimension(74, 18));

        row.add(lbl);
        row.add(Box.createHorizontalStrut(14));
        row.add(control);
        if (!stretchControl && readout == null) row.add(Box.createHorizontalGlue());
        if (readout != null) {
            row.add(Box.createHorizontalStrut(10));
            row.add(readout);
        }
        return row;
    }

    // ── block 2 ─────────────────────────────────────────────────────────────

    JPanel buildBlock2() {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.setBackground(PAPER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_BLOCK2));
        p.setPreferredSize(new Dimension(0, H_BLOCK2));

        // left
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setBackground(PAPER);
        left.setBorder(new EmptyBorder(18, 22, 18, 22));

        JLabel dimLabel = new JLabel("DIMENSIONS");
        dimLabel.setFont(fontMonoBold.deriveFont(10f));
        dimLabel.setForeground(MUTED);
        dimLabel.setPreferredSize(new Dimension(96, 22));
        dimLabel.setMaximumSize(new Dimension(96, 22));

        dimensionsCombo = styledCombo(DIMENSION_OPTIONS);
        dimensionsCombo.addActionListener(e -> {
            dimensionsText = (String) dimensionsCombo.getSelectedItem();
            refreshFormatDependentUI();
        });

        left.add(dimLabel);
        left.add(Box.createHorizontalStrut(14));
        left.add(dimensionsCombo);
        p.add(left);

        // right
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(PAPER);
        right.setBorder(new EmptyBorder(14, 22, 14, 22));

        audioSwitch = new ToggleSwitch(true, () -> {
            includeAudio = audioSwitch.isOn();
            refreshFormatDependentUI();
        });
        // SDR is now handled by the STRIP HDR mode tab — sdrSwitch is allocated
        // (so existing references don't NPE) but never added to the UI.
        sdrSwitch = new ToggleSwitch(false, () -> { stripHDR = sdrSwitch.isOn(); });

        right.add(buildSingleLabelToggleRow("INCLUDE AUDIO", audioSwitch));
        p.add(right);

        return p;
    }

    JPanel buildSingleLabelToggleRow(String text, ToggleSwitch sw) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel lbl = new JLabel(text);
        lbl.setFont(fontMonoBold.deriveFont(10f));
        lbl.setForeground(MUTED);

        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        row.add(sw);
        return row;
    }

    // ── block 3 ─────────────────────────────────────────────────────────────

    JPanel buildBlock3() {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.setBackground(PAPER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_BLOCK3));
        p.setPreferredSize(new Dimension(0, H_BLOCK3));

        // left: prefix + suffix
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(PAPER);
        left.setBorder(new EmptyBorder(22, 22, 22, 22));

        prefixField = styledTextField("");
        prefixField.getDocument().addDocumentListener(textListener(t -> prefixText = t.trim()));
        suffixField = styledTextField("");
        suffixField.getDocument().addDocumentListener(textListener(t -> suffixText = t.trim()));

        left.add(buildLabelControlRow("Prefix", prefixField));
        left.add(Box.createVerticalStrut(16));
        left.add(buildLabelControlRow("Suffix", suffixField));
        p.add(left);

        // right
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(PAPER);
        right.setBorder(new EmptyBorder(22, 22, 22, 22));

        destinationCombo = styledCombo(new String[] { "Same as source", "Choose folder…" });
        destinationCombo.addActionListener(e -> {
            if (destinationComboGuard) return;
            String s = (String) destinationCombo.getSelectedItem();
            if ("Choose folder…".equals(s)) {
                chooseDestinationFolder();
            } else if ("Same as source".equals(s)) {
                destinationPath = null;
                Shutter.caseChangeFolder1.setSelected(false);
                rebuildDestinationCombo();
            }
            // If a path option is selected, destinationPath is already set.
        });

        openFolderSwitch = new ToggleSwitch(true, () -> openFolderWhenDone = openFolderSwitch.isOn());

        right.add(buildLabelControlRow("Destination", destinationCombo));
        right.add(Box.createVerticalStrut(16));
        right.add(buildSingleLabelToggleRow("OPEN FOLDER WHEN DONE", openFolderSwitch));
        p.add(right);

        return p;
    }

    JPanel buildLabelControlRow(String labelText, JComponent control) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel lbl = new JLabel(labelText.toUpperCase());
        lbl.setFont(fontMonoBold.deriveFont(10f));
        lbl.setForeground(MUTED);
        lbl.setPreferredSize(new Dimension(96, 32));
        lbl.setMaximumSize(new Dimension(96, 32));

        row.add(lbl);
        row.add(Box.createHorizontalStrut(14));
        row.add(control);
        return row;
    }

    void chooseDestinationFolder() {
        // Use native macOS folder picker via java.awt.FileDialog
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        File dir = null;
        if (isMac) {
            String prev = System.getProperty("apple.awt.fileDialogForDirectories");
            try {
                System.setProperty("apple.awt.fileDialogForDirectories", "true");
                java.awt.FileDialog fd = new java.awt.FileDialog(frame, "Choose folder", java.awt.FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    dir = new File(fd.getDirectory(), fd.getFile());
                }
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories",
                    prev == null ? "false" : prev);
            }
        } else {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                dir = chooser.getSelectedFile();
            }
        }
        if (dir != null) {
            destinationPath = dir.getAbsolutePath();
            Shutter.lblDestination1.setText(destinationPath);
            Shutter.caseChangeFolder1.setSelected(true);
            rebuildDestinationCombo();
        } else {
            // user cancelled → revert combo to current state
            rebuildDestinationCombo();
        }
    }

    /** Rebuild the destination combo with the current path (if any) as the second option. */
    void rebuildDestinationCombo() {
        destinationComboGuard = true;
        try {
            destinationCombo.removeAllItems();
            destinationCombo.addItem("Same as source");
            if (destinationPath != null) {
                destinationCombo.addItem(abbreviatePath(destinationPath, 36));
            }
            destinationCombo.addItem("Choose folder…");
            destinationCombo.setSelectedIndex(destinationPath != null ? 1 : 0);
        } finally {
            destinationComboGuard = false;
        }
    }

    /** Shorten a path for display: keeps the last `maxChars` characters with a leading ellipsis. */
    static String abbreviatePath(String path, int maxChars) {
        if (path == null) return "";
        if (path.length() <= maxChars) return path;
        return "…" + path.substring(path.length() - (maxChars - 1));
    }

    DocumentListener textListener(java.util.function.Consumer<String> onChange) {
        return new DocumentListener() {
            void update(DocumentEvent e) {
                try { onChange.accept(e.getDocument().getText(0, e.getDocument().getLength())); }
                catch (Exception ex) {}
                refreshFormatDependentUI();
            }
            @Override public void insertUpdate(DocumentEvent e) { update(e); }
            @Override public void removeUpdate(DocumentEvent e) { update(e); }
            @Override public void changedUpdate(DocumentEvent e) { update(e); }
        };
    }

    // ── footer ──────────────────────────────────────────────────────────────

    JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PAPER);
        p.setBorder(new EmptyBorder(12, 22, 12, 22));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_FOOTER));
        p.setPreferredSize(new Dimension(0, H_FOOTER));

        summaryLabel = new JLabel("OUTPUT MP4 / H.264  ·  QUALITY 65  ·  ~9.3 MBPS  ·  AUDIO ON");
        summaryLabel.setFont(fontMonoBold.deriveFont(11f));
        summaryLabel.setForeground(MUTED);

        convertButton = new ConvertButton("CONVERT");
        convertButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onConvertClicked(); }
        });

        p.add(summaryLabel, BorderLayout.CENTER);
        p.add(convertButton, BorderLayout.EAST);
        return p;
    }

    void onConvertClicked() {
        if (Shutter.list.getSize() == 0) {
            showSageNotice("Drop or browse files first.");
            return;
        }
        if (convertButton instanceof ConvertButton) {
            ((ConvertButton) convertButton).flashGreen();
        }
        Shutter.btnStart.setText(Shutter.language.getProperty("simpleEncoderConvert"));
        SimpleEncoder.main();
    }

    /** Sage-themed modal notice — replaces the dark FlatLaf JOptionPane. */
    public static void showSageNotice(String message) {
        final javax.swing.JDialog dialog = new javax.swing.JDialog(frame, true);
        dialog.setUndecorated(true);
        dialog.setBackground(PAPER);

        JPanel content = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(HAIR);
                g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 14, 14));
                g2.dispose();
            }
        };
        content.setBackground(PAPER);
        content.setBorder(new EmptyBorder(22, 26, 18, 26));

        JLabel msg = new JLabel(message);
        msg.setFont(fontBody.deriveFont(Font.PLAIN, 14f));
        msg.setForeground(INK);
        msg.setHorizontalAlignment(SwingConstants.LEFT);

        JButton ok = new ConvertButton("OK");
        ok.setPreferredSize(new Dimension(80, 30));
        ok.addActionListener(e -> dialog.dispose());

        JPanel buttonRow = new JPanel();
        buttonRow.setOpaque(false);
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(Box.createHorizontalGlue());
        buttonRow.add(ok);

        content.add(msg, BorderLayout.CENTER);
        content.add(buttonRow, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 320), dialog.getHeight());
        dialog.setLocationRelativeTo(frame);
        dialog.setShape(new RoundRectangle2D.Float(0, 0, dialog.getWidth(), dialog.getHeight(), 14, 14));
        dialog.setVisible(true);
    }

    // ── refresh logic ──────────────────────────────────────────────────────

    static void refreshFormatDependentUI() {
        boolean isStripHDR = MODE_STRIP_HDR.equals(currentMode);
        boolean isMOV   = "MOV".equals(selectedFormat);
        boolean isAudio = "MP3".equals(selectedFormat) || "WAV".equals(selectedFormat);

        // Alpha row visible only for MOV (separate row above Quality).
        if (alphaRow != null) alphaRow.setVisible(isMOV && !isStripHDR);

        // Block 2 references kept for compatibility but never visible
        if (block2Panel != null) block2Panel.setVisible(false);
        if (block2Hairline != null) block2Hairline.setVisible(false);

        // Lossless formats: hide the slider/num/tag, show "LOSSLESS" text.
        boolean isWAV = "WAV".equals(selectedFormat);
        boolean isLossless = isMOV || isWAV;
        if (qualitySlider != null)      qualitySlider.setVisible(!isLossless);
        if (qualityNumLabel != null)    qualityNumLabel.setVisible(!isLossless);
        if (qualityTagLabel != null)    qualityTagLabel.setVisible(!isLossless);
        if (qualityLosslessLabel != null) qualityLosslessLabel.setVisible(isLossless);

        // Bitrate row: combined Bitrate value + AUDIO toggle inline.
        // Stay visible always; hide the bitrate label/value chunk for MOV (lossless),
        // hide the audio chunk for audio formats.
        if (bitrateRow != null) {
            bitrateRow.setVisible(true);
            for (Component c : bitrateRow.getComponents()) {
                if (c instanceof JLabel && "BITRATE".equals(((JLabel) c).getText())) {
                    c.setVisible(!isMOV);
                    break;
                }
            }
        }
        if (bitrateValueLabel != null) bitrateValueLabel.setVisible(!isMOV);
        if (audioInlineLabel != null) audioInlineLabel.setVisible(!isAudio);
        if (audioSwitch != null)      audioSwitch.setVisible(!isAudio);

        // Dimensions row: hide for audio formats
        if (dimensionsRow != null) dimensionsRow.setVisible(!isAudio);

        // MP3 still uses the slider — snap to standard bitrate buckets
        if (qualitySlider != null && qualitySlider.isVisible()) {
            if ("MP3".equals(selectedFormat)) {
                qualitySlider.setMajorTickSpacing(25);
                qualitySlider.setSnapToTicks(true);
            } else {
                qualitySlider.setSnapToTicks(false);
            }
            qualitySlider.setEnabled(true);
        }

        // Block 1 stays the same height for every format — even when fewer rows are visible
        // — so the layout doesn't shuffle when switching format chips.
        if (block1Panel != null) {
            block1Panel.setPreferredSize(new Dimension(0, H_BLOCK1_FULL));
            block1Panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, H_BLOCK1_FULL));
            block1Panel.setMinimumSize(new Dimension(0, H_BLOCK1_FULL));
        }

        // Recalculate the entire target height every refresh so we never drift.
        // Defer to the next EDT cycle so layout + resize happen in a single paint pass.
        if (cardPanel != null) {
            cardPanel.revalidate();
        }
        if (frame != null) {
            int targetH = computeTargetHeight();
            if (frame.getHeight() != targetH) {
                SwingUtilities.invokeLater(() -> {
                    frame.setSize(frame.getWidth(), targetH);
                    cardPanel.repaint();
                });
            } else if (cardPanel != null) {
                cardPanel.repaint();
            }
            lastWasAudio = isAudio;
        }

        // Codec readout
        String codec;
        if (isMOV) {
            codec = alphaChannel ? "PRORES 4444 + α" : "PRORES 422";
        } else if ("MP3".equals(selectedFormat)) {
            codec = "MP3";
        } else if ("WAV".equals(selectedFormat)) {
            codec = "PCM 16-BIT";
        } else {
            codec = "H.264";
        }
        if (formatReadoutLabel != null) formatReadoutLabel.setText(codec);

        // Live bitrate from SimpleEncoder (recomputes from quality + format + alpha)
        if (bitrateValueLabel != null) {
            int kbps = SimpleEncoder.estimateBitrate(selectedFormat, qualityValue);
            String bitrate;
            if (kbps >= 1000) bitrate = String.format("~%.1f Mbps", kbps / 1000.0);
            else              bitrate = "~" + kbps + " kbps";
            bitrateValueLabel.setText(bitrate);
        }

        // Quality tag
        String tag;
        int q = qualityValue;
        if (q >= 75) tag = "MAX";
        else if (q >= 50) tag = "HIGH";
        else if (q >= 25) tag = "MEDIUM";
        else tag = "LOW";
        if (qualityNumLabel != null) qualityNumLabel.setText(String.valueOf(q));
        if (qualityTagLabel != null) qualityTagLabel.setText(tag);

        // Summary
        if (summaryLabel != null) {
            StringBuilder s = new StringBuilder();
            if (isStripHDR) {
                s.append("STRIP HDR  ·  OUTPUT MOV / PRORES  ·  AVCONVERT");
            } else {
                s.append("OUTPUT ").append(selectedFormat).append(" / ").append(codec)
                 .append("  ·  QUALITY ").append(q);
                if (!isAudio) {
                    s.append("  ·  DIM ").append(dimensionsText.toUpperCase())
                     .append("  ·  AUDIO ").append(includeAudio ? "ON" : "OFF");
                }
            }
            summaryLabel.setText(s.toString());
        }
    }

    // ── form-control factories ──────────────────────────────────────────────

    JComboBox<String> styledCombo(String[] options) {
        JComboBox<String> c = new JComboBox<>(options);
        c.setFont(fontMonoBold.deriveFont(12f));
        c.setBackground(PAPER);
        c.setForeground(INK);
        c.setBorder(new FlatLineBorder(new Insets(0, 8, 0, 8), HAIR, 1.0f, 8));
        c.setMaximumRowCount(8);
        c.setOpaque(true);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        c.setPreferredSize(new Dimension(0, 32));
        c.setMinimumSize(new Dimension(120, 32));
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.putClientProperty("FlatLaf.style",
            "background: #DDE3CB;" +
            "borderColor: #1a1a1a;" +
            "focusedBorderColor: #1a1a1a;" +
            "buttonArrowColor: #111111;" +
            "buttonHoverArrowColor: #111111;" +
            "buttonPressedArrowColor: #111111;" +
            "buttonBackground: #DDE3CB;" +
            "buttonHoverBackground: #C8CFB3;" +
            "popupBackground: #DDE3CB;" +
            "selectionBackground: #111111;" +
            "selectionForeground: #DDE3CB;" +
            "arc: 8");
        return c;
    }

    JTextField styledTextField(String initial) {
        JTextField f = new JTextField(initial);
        f.setFont(fontMonoBold.deriveFont(12f));
        f.setBackground(PAPER);
        f.setForeground(INK);
        f.setCaretColor(INK);
        f.setBorder(new FlatLineBorder(new Insets(0, 10, 0, 10), HAIR, 1.0f, 8));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        f.setPreferredSize(new Dimension(0, 32));
        f.setMinimumSize(new Dimension(120, 32));
        f.putClientProperty("FlatLaf.style",
            "background: #DDE3CB;" +
            "borderColor: #1a1a1a;" +
            "focusedBorderColor: #1a1a1a;" +
            "arc: 8");
        return f;
    }

    JComponent hairline() {
        JPanel h = new JPanel();
        h.setBackground(HAIR);
        h.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        h.setPreferredSize(new Dimension(0, 1));
        h.setMinimumSize(new Dimension(0, 1));
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Custom components
    // ─────────────────────────────────────────────────────────────────────────

    /** Outer rounded hairline border applied to the whole card. */
    static class RoundedHairlineBorder implements javax.swing.border.Border {
        final int arc;
        RoundedHairlineBorder(int arc) { this.arc = arc; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(0, 0, 0, 0); }
        @Override public boolean isBorderOpaque() { return false; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(HAIR);
            g2.draw(new RoundRectangle2D.Float(x + 0.75f, y + 0.75f, w - 1.5f, h - 1.5f, arc, arc));
            g2.dispose();
        }
    }

    /** macOS-style traffic-light dot with glyph on hover. */
    static class WindowDot extends JComponent {
        Color color;
        String glyph;
        Runnable action;
        boolean hover;

        WindowDot(Color color, String glyph, Runnable action) {
            this.color = color;
            this.glyph = glyph;
            this.action = action;
            setPreferredSize(new Dimension(13, 13));
            setMinimumSize(new Dimension(13, 13));
            setMaximumSize(new Dimension(13, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { action.run(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(color);
            g2.fillOval(0, 0, w - 1, h - 1);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(color.darker());
            g2.drawOval(0, 0, w - 1, h - 1);
            if (hover) {
                g2.setColor(new Color(0x11, 0x11, 0x11, 200));
                g2.setFont(fontMonoBold.deriveFont(9f));
                FontMetrics fm = g2.getFontMetrics();
                int gx = (w - fm.stringWidth(glyph)) / 2 + 1;
                int gy = (h - fm.getHeight()) / 2 + fm.getAscent() - 1;
                g2.drawString(glyph, gx, gy);
            }
            g2.dispose();
        }
    }

    /** ENCODE / STRIP HDR pill tab switcher. Two segments, sliding active state. */
    static class ModeTabs extends JComponent {
        static final String[] MODES = { MODE_ENCODE, MODE_STRIP_HDR };
        int selected = 0;
        Runnable onChange;
        java.awt.Rectangle[] hitboxes = new java.awt.Rectangle[2];

        ModeTabs() {
            setPreferredSize(new Dimension(220, 28));
            setMinimumSize(new Dimension(220, 28));
            setMaximumSize(new Dimension(220, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(fontMonoBold.deriveFont(11f));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    for (int i = 0; i < hitboxes.length; i++) {
                        if (hitboxes[i] != null && hitboxes[i].contains(e.getPoint())) {
                            if (selected != i) {
                                selected = i;
                                repaint();
                                if (onChange != null) onChange.run();
                            }
                            return;
                        }
                    }
                }
            });
        }

        String selectedMode() { return MODES[selected]; }

        void setSelectedMode(String m) {
            for (int i = 0; i < MODES.length; i++) {
                if (MODES[i].equals(m)) { selected = i; repaint(); return; }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // Outer pill
            g2.setColor(PAPER);
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(HAIR);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));

            int segWidth = w / MODES.length;
            for (int i = 0; i < MODES.length; i++) {
                hitboxes[i] = new java.awt.Rectangle(i * segWidth, 0, segWidth, h);
            }

            // Active dark pill behind the selected segment
            int padding = 3;
            int activeX = selected * segWidth + padding;
            int activeW = segWidth - padding * 2;
            g2.setColor(INK);
            g2.fill(new RoundRectangle2D.Float(activeX, padding, activeW, h - padding * 2,
                h - padding * 2, h - padding * 2));

            // Text labels
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < MODES.length; i++) {
                String text = MODES[i];
                int textW = fm.stringWidth(text);
                int tx = i * segWidth + (segWidth - textW) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(i == selected ? PAPER : INK);
                g2.drawString(text, tx, ty);
            }
            g2.dispose();
        }
    }

    /** Mouse adapter that drags the frame around. */
    class WindowDragger extends MouseAdapter {
        Point start;
        @Override public void mousePressed(MouseEvent e) {
            start = e.getLocationOnScreen();
        }
        @Override public void mouseDragged(MouseEvent e) {
            if (start == null) return;
            Point now = e.getLocationOnScreen();
            Point loc = frame.getLocation();
            frame.setLocation(loc.x + (now.x - start.x), loc.y + (now.y - start.y));
            start = now;
        }
    }

    /** Width-only resize handle. Active in the leftmost or rightmost 6 px of the cardPanel.
        Height is content-driven so we don't expose vertical resize. */
    class WidthResizer extends MouseAdapter {
        static final int EDGE = 6;
        static final int MODE_NONE = 0, MODE_RIGHT = 1, MODE_LEFT = 2;

        int dragMode = MODE_NONE;
        int initialWidth;
        int initialFrameX;
        int initialClickX;

        int edgeAt(MouseEvent e) {
            int x = e.getX();
            int w = e.getComponent().getWidth();
            if (x <= EDGE) return MODE_LEFT;
            if (x >= w - EDGE) return MODE_RIGHT;
            return MODE_NONE;
        }

        @Override public void mouseMoved(MouseEvent e) {
            int edge = edgeAt(e);
            int cursor = (edge == MODE_LEFT) ? Cursor.W_RESIZE_CURSOR
                       : (edge == MODE_RIGHT) ? Cursor.E_RESIZE_CURSOR
                       : Cursor.DEFAULT_CURSOR;
            e.getComponent().setCursor(Cursor.getPredefinedCursor(cursor));
        }
        @Override public void mousePressed(MouseEvent e) {
            dragMode = edgeAt(e);
            if (dragMode != MODE_NONE) {
                initialWidth = frame.getWidth();
                initialFrameX = frame.getX();
                initialClickX = e.getLocationOnScreen().x;
            }
        }
        @Override public void mouseDragged(MouseEvent e) {
            if (dragMode == MODE_NONE) return;
            int dx = e.getLocationOnScreen().x - initialClickX;
            if (dragMode == MODE_RIGHT) {
                int newW = Math.max(700, Math.min(1600, initialWidth + dx));
                frame.setSize(newW, frame.getHeight());
            } else { // MODE_LEFT — grow leftward by moving the window's X
                int newW = Math.max(700, Math.min(1600, initialWidth - dx));
                int newX = initialFrameX + (initialWidth - newW);
                frame.setBounds(newX, frame.getY(), newW, frame.getHeight());
            }
        }
        @Override public void mouseReleased(MouseEvent e) {
            dragMode = MODE_NONE;
            e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
    }

    static class FormatChips extends JPanel {
        Chip mp4, mov, mp3, wav;
        FormatChips() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            mp4 = makeChip("MP4");
            mov = makeChip("MOV");
            mp3 = makeChip("MP3");
            wav = makeChip("WAV");
            mp4.setActive(true);
            mov.setActive(false);
            mp3.setActive(false);
            wav.setActive(false);
            add(mp4); add(Box.createHorizontalStrut(5));
            add(mov); add(Box.createHorizontalStrut(5));
            add(mp3); add(Box.createHorizontalStrut(5));
            add(wav);
            add(Box.createHorizontalGlue());
        }
        Chip makeChip(String text) {
            Chip c = new Chip(text);
            c.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { setActiveChip(text); }
            });
            return c;
        }
        void setActiveChip(String text) {
            mp4.setActive("MP4".equals(text));
            mov.setActive("MOV".equals(text));
            mp3.setActive("MP3".equals(text));
            wav.setActive("WAV".equals(text));
            selectedFormat = text;
            refreshFormatDependentUI();
        }
    }

    static class Chip extends JLabel {
        boolean active;
        Chip(String text) {
            super(text);
            setFont(fontMonoBold.deriveFont(10f));
            setBorder(new EmptyBorder(5, 8, 5, 8));
            setHorizontalAlignment(SwingConstants.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            setForeground(INK);     // ensure text shows on inactive chips at startup
        }
        void setActive(boolean a) {
            this.active = a;
            setForeground(a ? PAPER : INK);
            repaint();
        }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(36, d.width), 22);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(active ? INK : PAPER);
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(active ? INK : HAIR);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class ToggleSwitch extends JComponent {
        boolean on;
        Runnable onChange;
        ToggleSwitch(boolean on, Runnable onChange) {
            this.on = on;
            this.onChange = onChange;
            setPreferredSize(new Dimension(64, 26));
            setMinimumSize(new Dimension(64, 26));
            setMaximumSize(new Dimension(64, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(fontMonoBold.deriveFont(10f));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    ToggleSwitch.this.on = !ToggleSwitch.this.on;
                    repaint();
                    if (ToggleSwitch.this.onChange != null) ToggleSwitch.this.onChange.run();
                }
            });
        }
        boolean isOn() { return on; }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(on ? INK : PAPER);
            g2.fill(new RoundRectangle2D.Float(0.75f, 0.75f, w - 1.5f, h - 1.5f, h, h));
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(HAIR);
            g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, w - 1.5f, h - 1.5f, h, h));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String label = on ? "ON" : "OFF";
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(on ? PAPER : INK);
            int textX = on ? 9 : (w - fm.stringWidth(label) - 9);
            g2.drawString(label, textX, textY);
            int knob = h - 6;
            int kx = on ? (w - knob - 3) : 3;
            int ky = 3;
            g2.setColor(PAPER);
            g2.fill(new RoundRectangle2D.Float(kx, ky, knob, knob, knob, knob));
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(HAIR);
            g2.draw(new RoundRectangle2D.Float(kx + 0.5f, ky + 0.5f, knob - 1, knob - 1, knob, knob));
            g2.dispose();
        }
    }

    /** Custom pill-shaped Convert button. Supports a brief green flash + fade on click. */
    static class ConvertButton extends JButton {
        static final Color FLASH_GREEN = new Color(0x6E, 0xA8, 0x52);
        static final int   FLASH_MS    = 850;

        long flashStart = -1;
        javax.swing.Timer flashTimer;

        ConvertButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(fontMonoBold.deriveFont(12f));
            setForeground(PAPER);
            setPreferredSize(new Dimension(140, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Start a flash → fade animation. Background lerps green → INK over FLASH_MS. */
        void flashGreen() {
            flashStart = System.currentTimeMillis();
            if (flashTimer != null && flashTimer.isRunning()) flashTimer.stop();
            flashTimer = new javax.swing.Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - flashStart;
                if (elapsed >= FLASH_MS) {
                    flashStart = -1;
                    flashTimer.stop();
                }
                repaint();
            });
            flashTimer.setRepeats(true);
            flashTimer.start();
            repaint();
        }

        Color currentBackground() {
            if (flashStart > 0) {
                long elapsed = System.currentTimeMillis() - flashStart;
                float t = Math.min(1f, elapsed / (float) FLASH_MS);
                // ease-out cubic: t' = 1 - (1-t)^3
                float u = 1f - (1f - t) * (1f - t) * (1f - t);
                return lerp(FLASH_GREEN, INK, u);
            }
            return getModel().isPressed()  ? new Color(0x2a, 0x2a, 0x2a)
                 : getModel().isRollover() ? new Color(0x2a, 0x2a, 0x2a)
                 : INK;
        }

        static Color lerp(Color a, Color b, float t) {
            int r = (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t);
            int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl= (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
            return new Color(clamp(r), clamp(g), clamp(bl));
        }
        static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(currentBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(getText())) / 2;
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(PAPER);
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    /** Drop zone: dashed rounded border, hatched background, drag-drop, click-to-browse.
        Empty state shows icon + caption; filled state shows the file list. */
    public static class DropZone extends JPanel {
        boolean dragOver;
        IconCanvas iconCanvas;
        JLabel bigLabel;
        JLabel smallLabel;
        JPanel emptyPanel;
        JPanel filesListPanel;
        javax.swing.JScrollPane filesScroll;
        JLabel filesFooter;
        JButton clearButton;
        java.util.List<FileRow> fileRows = new java.util.ArrayList<>();

        DropZone() {
            setOpaque(false);            // ← important: don't fill via super.paintComponent
            setLayout(new java.awt.CardLayout());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // ── Empty state ────────────────────────────────────────────
            iconCanvas = new IconCanvas();
            iconCanvas.setAlignmentX(Component.CENTER_ALIGNMENT);

            bigLabel = new JLabel("Drop files here");
            bigLabel.setFont(fontBody.deriveFont(Font.PLAIN, 15f));
            bigLabel.setForeground(INK);
            bigLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            smallLabel = new JLabel("OR CLICK TO BROWSE");
            smallLabel.setFont(fontMonoBold.deriveFont(10f));
            smallLabel.setForeground(MUTED);
            smallLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            emptyPanel = new JPanel();
            emptyPanel.setOpaque(false);
            emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
            emptyPanel.add(Box.createVerticalGlue());
            emptyPanel.add(iconCanvas);
            emptyPanel.add(Box.createVerticalStrut(8));
            emptyPanel.add(bigLabel);
            emptyPanel.add(Box.createVerticalStrut(4));
            emptyPanel.add(smallLabel);
            emptyPanel.add(Box.createVerticalGlue());

            // ── Filled state ───────────────────────────────────────────
            filesListPanel = new JPanel();
            filesListPanel.setOpaque(false);
            filesListPanel.setLayout(new BoxLayout(filesListPanel, BoxLayout.Y_AXIS));

            filesScroll = new javax.swing.JScrollPane(filesListPanel,
                javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            filesScroll.setOpaque(false);
            filesScroll.getViewport().setOpaque(false);
            filesScroll.setBorder(null);
            filesScroll.getVerticalScrollBar().setUnitIncrement(16);

            filesFooter = new JLabel("CLICK TO ADD MORE");
            filesFooter.setFont(fontMonoBold.deriveFont(10f));
            filesFooter.setForeground(MUTED);
            filesFooter.setBorder(new EmptyBorder(6, 0, 4, 0));

            // Clear button — pill style, inline with footer
            clearButton = new JButton("CLEAR") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setStroke(new BasicStroke(1f));
                    boolean hover = getModel().isRollover() || getModel().isPressed();
                    g2.setColor(hover ? INK : PAPER);
                    g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
                    g2.setColor(HAIR);
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                    int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.setColor(hover ? PAPER : INK);
                    g2.drawString(getText(), tx, ty);
                    g2.dispose();
                }
            };
            clearButton.setContentAreaFilled(false);
            clearButton.setBorderPainted(false);
            clearButton.setFocusPainted(false);
            clearButton.setOpaque(false);
            clearButton.setFont(fontMonoBold.deriveFont(9f));
            clearButton.setForeground(INK);
            clearButton.setPreferredSize(new Dimension(60, 22));
            clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            clearButton.addActionListener(e -> {
                Shutter.list.clear();
                updateFilesCount();
            });

            JPanel footerBar = new JPanel(new BorderLayout(8, 0));
            footerBar.setOpaque(false);
            footerBar.setBorder(new EmptyBorder(4, 0, 0, 0));
            footerBar.add(filesFooter, BorderLayout.CENTER);
            footerBar.add(clearButton, BorderLayout.EAST);

            JPanel filledPanel = new JPanel(new BorderLayout());
            filledPanel.setOpaque(false);
            filledPanel.setBorder(new EmptyBorder(10, 14, 4, 14));
            filledPanel.add(filesScroll, BorderLayout.CENTER);
            filledPanel.add(footerBar, BorderLayout.SOUTH);

            add(emptyPanel,  "EMPTY");
            add(filledPanel, "FILES");
            ((java.awt.CardLayout) getLayout()).show(this, "EMPTY");

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { browseForFiles(); }
            });
            // Forward clicks on inner components too
            MouseAdapter forward = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { browseForFiles(); }
            };
            filesListPanel.addMouseListener(forward);
            filesFooter.addMouseListener(forward);
            filesScroll.getViewport().addMouseListener(forward);

            new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetListener() {
                @Override public void dragEnter(DropTargetDragEvent e) {
                    dragOver = true; repaint();
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                }
                @Override public void dragOver(DropTargetDragEvent e) { e.acceptDrag(DnDConstants.ACTION_COPY); }
                @Override public void dropActionChanged(DropTargetDragEvent e) {}
                @Override public void dragExit(DropTargetEvent e) { dragOver = false; repaint(); }
                @Override public void drop(DropTargetDropEvent e) {
                    dragOver = false; repaint();
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    try {
                        Object data = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (data instanceof List<?>) {
                            for (Object o : (List<?>) data) {
                                if (o instanceof File) addFile((File) o);
                            }
                            updateFilesCount();
                        }
                        e.dropComplete(true);
                    } catch (Exception ex) { e.dropComplete(false); }
                }
            }, true);
        }

        void browseForFiles() {
            // Use native macOS picker — JFileChooser inherits FlatLaf's dark theme
            // and isn't what users expect.
            boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
            if (isMac) {
                java.awt.FileDialog fd = new java.awt.FileDialog(SimpleFrame.frame, "Choose files", java.awt.FileDialog.LOAD);
                fd.setMultipleMode(true);
                fd.setVisible(true);
                File[] files = fd.getFiles();
                if (files != null) {
                    for (File f : files) addFile(f);
                    updateFilesCount();
                }
            } else {
                JFileChooser chooser = new JFileChooser();
                chooser.setMultiSelectionEnabled(true);
                if (chooser.showOpenDialog(SimpleFrame.frame) == JFileChooser.APPROVE_OPTION) {
                    for (File f : chooser.getSelectedFiles()) addFile(f);
                    updateFilesCount();
                }
            }
        }

        static void addFile(File f) {
            if (f == null) return;
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) for (File c : children) addFile(c);
                return;
            }
            String name = f.getName();
            if (name.startsWith(".") || !name.contains(".")) return;
            for (int i = 0; i < Shutter.list.getSize(); i++) {
                if (Shutter.list.getElementAt(i).equals(f.getAbsolutePath())) return;
            }
            Shutter.list.addElement(f.getAbsolutePath());
        }

        public static void updateFilesCount() {
            int n = Shutter.list.getSize();
            if (filesCountLabel != null) {
                filesCountLabel.setText("IDLE  ·  " + n + (n == 1 ? " FILE" : " FILES"));
            }
            if (dropZone != null) {
                java.awt.CardLayout cl = (java.awt.CardLayout) dropZone.getLayout();
                if (n == 0) {
                    dropZone.fileRows.clear();
                    dropZone.filesListPanel.removeAll();
                    cl.show(dropZone, "EMPTY");
                } else {
                    dropZone.filesListPanel.removeAll();
                    dropZone.fileRows.clear();
                    for (int i = 0; i < n; i++) {
                        File f = new File(Shutter.list.getElementAt(i));
                        FileRow row = new FileRow(f.getName());
                        dropZone.fileRows.add(row);
                        dropZone.filesListPanel.add(row);
                    }
                    dropZone.filesFooter.setText(
                        n + (n == 1 ? " FILE" : " FILES") + "  ·  CLICK TO ADD MORE");
                    cl.show(dropZone, "FILES");
                    dropZone.filesListPanel.revalidate();
                    dropZone.filesListPanel.repaint();
                }
                dropZone.repaint();
            }
        }

        /** Mark a file as actively encoding. */
        public static void setFileEncoding(int idx) {
            if (dropZone == null || idx < 0 || idx >= dropZone.fileRows.size()) return;
            javax.swing.SwingUtilities.invokeLater(() -> {
                dropZone.fileRows.get(idx).setStatus("ENCODING", INK);
                dropZone.fileRows.get(idx).setProgress(0);
            });
        }

        /** Update progress of the currently-encoding file. pct ∈ [0, 100]. */
        public static void setFileProgress(int idx, int pct) {
            if (dropZone == null || idx < 0 || idx >= dropZone.fileRows.size()) return;
            javax.swing.SwingUtilities.invokeLater(() -> dropZone.fileRows.get(idx).setProgress(pct));
        }

        /** Mark a file done — success=true → DONE in olive; success=false → FAILED in terracotta. */
        public static void setFileDone(int idx, boolean success) {
            if (dropZone == null || idx < 0 || idx >= dropZone.fileRows.size()) return;
            javax.swing.SwingUtilities.invokeLater(() -> {
                FileRow r = dropZone.fileRows.get(idx);
                r.setStatus(success ? "DONE" : "FAILED", success ? DOT_MAX : DOT_CLOSE);
                if (success) r.setProgress(100);
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Paint background, hatch, dashed border BEFORE letting children render
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // background fill
            g2.setColor(dragOver ? PAPER_2 : PAPER);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
            // diagonal hatch
            g2.setColor(new Color(17, 17, 17, 12));
            g2.setStroke(new BasicStroke(1f));
            for (int x = -h; x < w; x += 12) {
                g2.drawLine(x, 0, x + h, h);
            }
            // re-paint over the hatch with a subtle background
            // dashed (or solid on hover) rounded border
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, dragOver ? null : new float[]{4f, 4f}, 0f));
            g2.setColor(dragOver ? INK : HAIR);
            g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, w - 1.5f, h - 1.5f, 12, 12));
            g2.dispose();
            // (super.paintComponent does nothing useful since opaque=false; children paint after)
        }
    }

    /** A single file row in the queue: truncated filename + progress bar + status label.
        Uses GridBagLayout so the progress bar flexes with available width. */
    static class FileRow extends JPanel {
        JLabel nameLabel;
        ProgressBar progressBar;
        JLabel statusLabel;
        String fullName;

        FileRow(String filename) {
            this.fullName = filename;
            setOpaque(false);
            setLayout(new GridBagLayout());
            setBorder(new EmptyBorder(3, 4, 3, 4));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            nameLabel = new JLabel(truncate(filename, 18));
            nameLabel.setFont(fontMono.deriveFont(11f));
            nameLabel.setForeground(INK);
            nameLabel.setToolTipText(filename);
            nameLabel.setPreferredSize(new Dimension(110, 16));
            nameLabel.setMinimumSize(new Dimension(60, 16));

            progressBar = new ProgressBar();
            progressBar.setPreferredSize(new Dimension(60, 6));
            progressBar.setMinimumSize(new Dimension(30, 6));

            statusLabel = new JLabel("READY");
            statusLabel.setFont(fontMonoBold.deriveFont(9f));
            statusLabel.setForeground(MUTED);
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            statusLabel.setPreferredSize(new Dimension(45, 16));
            statusLabel.setMinimumSize(new Dimension(35, 16));

            GridBagConstraints gc = new GridBagConstraints();
            gc.gridy = 0;
            gc.fill = GridBagConstraints.HORIZONTAL;

            // name: weight 0 (preferred width, allowed to shrink to min)
            gc.gridx = 0;
            gc.weightx = 0;
            gc.insets = new Insets(0, 0, 0, 8);
            add(nameLabel, gc);

            // progress: weight 1 (fills remaining width, flexes with window)
            gc.gridx = 1;
            gc.weightx = 1.0;
            gc.insets = new Insets(0, 0, 0, 8);
            add(progressBar, gc);

            // status: weight 0 (fixed)
            gc.gridx = 2;
            gc.weightx = 0;
            gc.insets = new Insets(0, 0, 0, 0);
            add(statusLabel, gc);

            // Re-truncate the filename based on actual rendered width
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    refitName();
                }
            });
        }

        void refitName() {
            int w = nameLabel.getWidth();
            if (w < 30) return;
            FontMetrics fm = nameLabel.getFontMetrics(nameLabel.getFont());
            // Estimate how many chars fit in current width
            int avg = Math.max(6, fm.stringWidth("M"));
            int fit = Math.max(8, w / avg);
            nameLabel.setText(truncate(fullName, fit));
        }

        void setProgress(int pct) { progressBar.setProgress(pct); }
        void setStatus(String text, Color color) {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        }

        static String truncate(String s, int max) {
            if (s == null || s.length() <= max) return s;
            int dot = s.lastIndexOf('.');
            String ext = (dot > 0 && s.length() - dot <= 6) ? s.substring(dot) : "";
            int keep = max - 1 - ext.length();
            if (keep < 4) keep = 4;
            return s.substring(0, keep) + "…" + ext;
        }
    }

    /** Tiny rounded progress bar. */
    static class ProgressBar extends JComponent {
        int pct = 0;
        void setProgress(int p) { pct = Math.max(0, Math.min(100, p)); repaint(); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // track
            g2.setColor(SOFT);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(HAIR);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));
            // fill
            int fillW = (int) ((w - 2) * (pct / 100.0));
            if (fillW > 0) {
                g2.setColor(INK);
                g2.fill(new RoundRectangle2D.Float(1, 1, fillW, h - 2, h, h));
            }
            g2.dispose();
        }
    }

    /** Custom-painted icon: tray with downward arrow into it (matches HTML SVG). */
    static class IconCanvas extends JComponent {
        IconCanvas() {
            setPreferredSize(new Dimension(40, 38));
            setMinimumSize(new Dimension(40, 38));
            setMaximumSize(new Dimension(40, 38));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(INK);
            int w = getWidth(), h = getHeight();
            int cx = w / 2;
            int top = 4;
            int arrowTipY = h / 2 + 2;
            int trayTop = h / 2 + 4;
            int trayBottom = h - 6;
            // vertical arrow shaft
            g2.drawLine(cx, top, cx, arrowTipY);
            // arrowhead
            Path2D.Float head = new Path2D.Float();
            head.moveTo(cx - 5, arrowTipY - 5);
            head.lineTo(cx, arrowTipY);
            head.lineTo(cx + 5, arrowTipY - 5);
            g2.draw(head);
            // tray (open box at bottom)
            g2.drawLine(cx - 10, trayTop + 2, cx - 10, trayBottom);
            g2.drawLine(cx - 10, trayBottom, cx + 10, trayBottom);
            g2.drawLine(cx + 10, trayTop + 2, cx + 10, trayBottom);
            g2.dispose();
        }
    }
}

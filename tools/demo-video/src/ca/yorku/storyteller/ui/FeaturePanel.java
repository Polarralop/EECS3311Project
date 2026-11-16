package ca.yorku.storyteller.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * The narrow panel on the left of each demo video. It names the feature being shown (ID, name, and
 * whether it is AI, Hybrid or Deterministic) and says in a line or two what is happening, while the app
 * runs on the right. It is a recording aid only: it is not part of the app and reads nothing from it.
 *
 * <p>Every feature change is written to a log as "mm:ss  F04 Story Turn (Hybrid)", measured from the
 * moment the recording started (RECORD_START_MS), so the report can link each feature's timestamp.
 *
 * <p>Run on its own ({@link #main}) it reads commands from standard input, one per line, so the CLI
 * video's driver can use it: {@code video|V4 The CLI}, {@code feature|F04|Story Turn|Hybrid|text},
 * {@code note|text}, {@code step|text}.
 */
public final class FeaturePanel {

    static final int WIDTH = 400;
    private static final Color BG = new Color(0x0F0B24);
    private static final Color TEXT = new Color(0xECE9FB);
    private static final Color MUTED = new Color(0xA9A2D6);
    private static final Color LINE = new Color(0x463C8F);

    private final JFrame frame = new JFrame("Feature");
    private final JLabel video = label("", 13f, Font.PLAIN, MUTED);
    private final JLabel id = label(" ", 52f, Font.BOLD, TEXT);
    private final JTextArea name = area(24f, Font.BOLD, TEXT);
    private final JLabel kind = label(" ", 18f, Font.BOLD, Color.BLACK);
    private final JTextArea about = area(17f, Font.PLAIN, TEXT);
    private final JLabel stepHeading = label("Now", 14f, Font.BOLD, MUTED);
    private final JTextArea step = area(17f, Font.PLAIN, new Color(0xFFE08A));
    private final long startMs;
    private final PrintWriter log;

    FeaturePanel(int height, long startMs, String logPath) throws IOException {
        this.startMs = startMs;
        this.log = new PrintWriter(new FileWriter(logPath, StandardCharsets.UTF_8, true), true);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 3, LINE),
                BorderFactory.createEmptyBorder(30, 26, 26, 22)));
        kind.setOpaque(true);
        kind.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        stepHeading.setVisible(false);
        for (JComponent c : new JComponent[] {video, gap(30), id, gap(4), name, gap(18), kind, gap(28), about,
                gap(34), stepHeading, gap(6), step}) {
            c.setAlignmentX(0f);
            p.add(c);
        }
        p.add(Box.createVerticalGlue());
        JLabel foot = label("<html>AI Interactive Storyteller<br>EECS 3311 Stage 2 &middot; Claude Sonnet 5.5</html>",
                15f, Font.PLAIN, MUTED);
        foot.setAlignmentX(0f);
        p.add(foot);
        frame.setUndecorated(true);
        frame.setFocusableWindowState(false);
        frame.setContentPane(new JPanel(new BorderLayout()));
        frame.getContentPane().add(p);
        frame.setBounds(0, 0, WIDTH, height);
        frame.setVisible(true);
    }

    /** Seconds since the recording started, as m:ss. */
    String clock() {
        long s = Math.max(0, (System.currentTimeMillis() - startMs) / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    void video(String title) {
        later(() -> video.setText(title));
        log.println(clock() + "\tVIDEO\t" + title);
    }

    /** A new feature segment starts: the panel changes and the log records its start time. */
    void feature(String featureId, String featureName, String type, String text) {
        later(() -> {
            id.setText(featureId);
            name.setText(featureName);
            kind.setText(type);
            kind.setBackground(switch (type) {
                case "AI" -> new Color(0xC9B8FF);
                case "Hybrid" -> new Color(0xFFC86B);
                case "Deterministic" -> new Color(0x7FE0C0);
                default -> MUTED;
            });
            kind.setVisible(!type.isEmpty());
            about.setText(text);
            stepHeading.setVisible(false);
            step.setText("");
        });
        log.println(clock() + "\t" + featureId + "\t" + featureName + (type.isEmpty() ? "" : " (" + type + ")"));
    }

    /** What is happening right now, under the feature's description. */
    void step(String text) {
        later(() -> {
            stepHeading.setVisible(!text.isEmpty());
            step.setText(text);
        });
        log.println(clock() + "\tstep\t" + text);
    }

    /** Replaces the feature's description (for the title card). */
    void about(String text) {
        later(() -> about.setText(text));
    }

    private static void later(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    private static JLabel label(String text, float size, int style, Color colour) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("DejaVu Sans", style, Math.round(size)));
        l.setForeground(colour);
        return l;
    }

    private static JTextArea area(float size, int style, Color colour) {
        JTextArea a = new JTextArea();
        a.setFont(new Font("DejaVu Sans", style, Math.round(size)));
        a.setForeground(colour);
        a.setOpaque(false);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(null);
        a.setMaximumSize(new java.awt.Dimension(WIDTH - 60, 400));
        return a;
    }

    private static JComponent gap(int h) {
        return (JComponent) Box.createVerticalStrut(h);
    }

    /** Standalone panel for the CLI video: reads commands from standard input until it closes. */
    public static void main(String[] args) throws Exception {
        int height = Integer.parseInt(args[0]);
        long start = Long.parseLong(System.getenv().getOrDefault("RECORD_START_MS", "" + System.currentTimeMillis()));
        FeaturePanel panel = new FeaturePanel(height, start, args[1]);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "video" -> panel.video(f[1]);
                case "feature" -> panel.feature(f[1], f[2], f[3], f[4]);
                case "about" -> panel.about(f[1]);
                case "step" -> panel.step(f[1]);
                default -> { }
            }
        }
        System.exit(0);
    }
}

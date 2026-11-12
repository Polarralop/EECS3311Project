package ca.yorku.storyteller.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * The look of {@link StoryGUI}: one deep blue-purple and its shades. The shades stack from the darkest
 * (the window) to the lightest (cards and buttons), so a panel that sits on another one reads as a
 * layer above it. {@link #install()} applies the palette to every Swing component through a Metal
 * theme; the helpers below style the few components that stand out (headings, cards, the main button).
 *
 * <p>Display only: nothing here reads or changes the story.
 *
 * <p>Features: F01-F12 (GUI look).
 * <p>Patterns: MVC (View).
 */
final class StoryTheme {

    // Layers, darkest (furthest back) to lightest (closest to the user).
    /** The window behind everything, the menu bar and the status bar. */
    static final Color BASE = new Color(0x151030);
    /** Text boxes and lists: set into the layer they sit on. */
    static final Color INSET = new Color(0x191338);
    /** The story column and the dialogs. */
    static final Color SURFACE = new Color(0x1F1845);
    /** The sidebar, one layer above the story. */
    static final Color SIDEBAR = new Color(0x271F56);
    /** Cards (the recap, the sections of the sidebar) and buttons. */
    static final Color RAISED = new Color(0x332A6E);
    /** Borders and dividers. */
    static final Color LINE = new Color(0x463C8F);
    /** Selected rows and selected text. */
    static final Color SELECTION = new Color(0x5546C8);
    /** The main action of a screen (Send, Open, Start story). */
    static final Color ACCENT = new Color(0x6C5CE7);
    /** Small headings and the stripe of the recap card. */
    static final Color ACCENT_SOFT = new Color(0xA99BFF);
    static final Color TEXT = new Color(0xECE9FB);
    static final Color MUTED = new Color(0xA9A2D6);
    static final Color DISABLED = new Color(0x6F68A0);

    private StoryTheme() {
    }

    /** Applies the palette to the whole application. Call once, before the first component is built. */
    static void install() {
        MetalLookAndFeel.setCurrentTheme(new Indigo());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            return; // Metal ships with every JDK; without it the default look is kept
        }
        Border field = new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        UIManager.put("TextField.border", field);
        UIManager.put("TextArea.margin", new InsetsUIResource(4, 6, 4, 6));
        Border cell = new BorderUIResource(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        UIManager.put("List.cellNoFocusBorder", cell);
        UIManager.put("List.focusCellHighlightBorder", cell);
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createLineBorder(LINE)));
        UIManager.put("Button.background", new ColorUIResource(RAISED));
        UIManager.put("Button.select", new ColorUIResource(SELECTION));
        UIManager.put("Button.focus", new ColorUIResource(RAISED));
        UIManager.put("Button.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(5, 14, 5, 14))));
        UIManager.put("ComboBox.background", new ColorUIResource(RAISED));
        UIManager.put("MenuBar.background", new ColorUIResource(BASE));
        UIManager.put("MenuBar.border", new BorderUIResource(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE)));
        UIManager.put("Menu.background", new ColorUIResource(BASE));
        UIManager.put("PopupMenu.border", new BorderUIResource(BorderFactory.createLineBorder(LINE)));
        UIManager.put("TabbedPane.selected", new ColorUIResource(RAISED));
        UIManager.put("TabbedPane.background", new ColorUIResource(SIDEBAR));
        UIManager.put("TabbedPane.tabAreaBackground", new ColorUIResource(SIDEBAR));
        UIManager.put("TabbedPane.contentBorderInsets", new InsetsUIResource(1, 0, 0, 0));
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(4, 14, 4, 14));
        UIManager.put("TabbedPane.focus", new ColorUIResource(RAISED));
        UIManager.put("ScrollBar.background", new ColorUIResource(INSET));
        UIManager.put("ScrollBar.track", new ColorUIResource(INSET));
        UIManager.put("ToolTip.background", new ColorUIResource(RAISED));
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createLineBorder(LINE)));
        UIManager.put("TitledBorder.titleColor", new ColorUIResource(MUTED));
        UIManager.put("TitledBorder.border", new BorderUIResource(BorderFactory.createLineBorder(LINE)));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXT));
    }

    /** Marks the main action of a screen with the accent colour (while it can be pressed). */
    static void primary(JButton button) {
        if (button.getClientProperty(PRIMARY) == null) {
            button.addPropertyChangeListener("enabled", e -> paintPrimary(button));
        }
        button.putClientProperty(PRIMARY, Boolean.TRUE);
        paintPrimary(button);
    }

    /** Takes the accent colour off a button again. */
    static void plain(JButton button) {
        if (button.getClientProperty(PRIMARY) != null) {
            button.putClientProperty(PRIMARY, Boolean.FALSE);
            paintPrimary(button);
        }
    }

    private static final String PRIMARY = "storyteller.primary";

    private static void paintPrimary(JButton button) {
        boolean accent = Boolean.TRUE.equals(button.getClientProperty(PRIMARY)) && button.isEnabled();
        button.setBackground(accent ? ACCENT : UIManager.getColor("Button.background"));
        button.setForeground(accent ? Color.WHITE : UIManager.getColor("Button.foreground"));
    }

    /** A button that reads as a text link: no fill, no border, quiet colour. */
    static void quiet(JButton button) {
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        button.setFocusPainted(false);
        button.setForeground(MUTED);
        button.setFont(button.getFont().deriveFont(11f));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    /** A small, spaced-out heading for a section, in the soft accent colour. */
    static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.ENGLISH));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(ACCENT_SOFT);
        return label;
    }

    /** Gives a panel, and the plain panels and tabs inside it, the colour of a layer. Cards keep their own. */
    static <T extends JComponent> T layer(T component, Color background) {
        component.setBackground(background);
        for (java.awt.Component child : component.getComponents()) {
            if (child instanceof Card) {
                continue;
            }
            if (child instanceof JPanel || child instanceof javax.swing.JTabbedPane) {
                layer((JComponent) child, background);
            } else if (child instanceof javax.swing.JCheckBox) {
                child.setBackground(background);
            }
        }
        return component;
    }

    /**
     * A rounded panel one layer above whatever it sits on, with a soft shadow under it and, optionally,
     * an accent stripe down its left edge.
     */
    static final class Card extends JPanel {
        private static final int ARC = 12;
        private static final int SHADOW = 3;
        private final Color stripe;

        Card(LayoutManager layout, Color fill, Color stripe) {
            super(layout);
            this.stripe = stripe;
            setOpaque(false);
            setBackground(fill);
            setBorder(BorderFactory.createEmptyBorder(10, stripe == null ? 12 : 18, 10 + SHADOW, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight() - SHADOW;
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(0, SHADOW, w, h, ARC, ARC);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            if (stripe != null) {
                g2.setClip(0, 0, 5, h);
                g2.setColor(stripe);
                g2.fillRoundRect(0, 0, ARC * 2, h, ARC, ARC);
                g2.setClip(null);
            }
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Metal's colours and fonts replaced by the palette, so every standard component follows it. */
    private static final class Indigo extends DefaultMetalTheme {
        private static final FontUIResource PLAIN = new FontUIResource(Font.DIALOG, Font.PLAIN, 13);
        private static final FontUIResource SMALL = new FontUIResource(Font.DIALOG, Font.PLAIN, 11);

        @Override
        public String getName() {
            return "Indigo";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return new ColorUIResource(ACCENT_SOFT);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return new ColorUIResource(LINE);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return new ColorUIResource(RAISED);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return new ColorUIResource(SURFACE);
        }

        @Override
        protected ColorUIResource getWhite() {
            return new ColorUIResource(INSET);
        }

        @Override
        protected ColorUIResource getBlack() {
            return new ColorUIResource(TEXT);
        }

        @Override
        public ColorUIResource getInactiveControlTextColor() {
            return new ColorUIResource(DISABLED);
        }

        @Override
        public ColorUIResource getInactiveSystemTextColor() {
            return new ColorUIResource(DISABLED);
        }

        @Override
        public ColorUIResource getMenuDisabledForeground() {
            return new ColorUIResource(DISABLED);
        }

        @Override
        public ColorUIResource getControlHighlight() {
            return new ColorUIResource(LINE);
        }

        @Override
        public ColorUIResource getPrimaryControlHighlight() {
            return new ColorUIResource(ACCENT_SOFT);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return PLAIN;
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return PLAIN;
        }

        @Override
        public FontUIResource getUserTextFont() {
            return PLAIN;
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return PLAIN;
        }

        @Override
        public FontUIResource getSubTextFont() {
            return SMALL;
        }
    }
}

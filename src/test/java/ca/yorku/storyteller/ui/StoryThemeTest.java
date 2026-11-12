package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** Checks the palette of {@link StoryTheme} and its styling helpers (no window needed). */
class StoryThemeTest {

    private static float[] hsb(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    }

    /** WCAG contrast ratio between two colours, 1 (none) to 21 (black on white). */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    @Test
    void theLayersAreShadesOfOneBluePurpleFromDarkToLight() {
        List<Color> layers = List.of(StoryTheme.BASE, StoryTheme.SURFACE, StoryTheme.SIDEBAR, StoryTheme.RAISED,
                StoryTheme.LINE, StoryTheme.SELECTION, StoryTheme.ACCENT);
        float previous = 0;
        for (Color layer : layers) {
            float[] hsb = hsb(layer);
            assertTrue(hsb[0] >= 0.66f && hsb[0] <= 0.72f, "blue-purple hue, got " + hsb[0] * 360 + " for " + layer);
            assertTrue(hsb[2] > previous, "each layer is lighter than the one under it: " + layer);
            previous = hsb[2];
        }
        assertTrue(hsb(StoryTheme.BASE)[2] < 0.25f, "the main colour is deep");
    }

    @Test
    void textIsReadableOnEveryLayer() {
        for (Color layer : List.of(StoryTheme.BASE, StoryTheme.INSET, StoryTheme.SURFACE, StoryTheme.SIDEBAR,
                StoryTheme.RAISED)) {
            assertTrue(contrast(StoryTheme.TEXT, layer) >= 7, "text on " + layer);
            assertTrue(contrast(StoryTheme.MUTED, layer) >= 4.5, "muted text on " + layer);
        }
        assertTrue(contrast(Color.WHITE, StoryTheme.ACCENT) >= 4.5, "the label of the main button");
        assertTrue(contrast(StoryTheme.TEXT, StoryTheme.SELECTION) >= 4.5, "a selected row");
    }

    @Test
    void aPrimaryButtonCarriesTheAccentOnlyWhileItCanBePressed() {
        JButton send = new JButton("Send");
        Color plain = send.getBackground();
        StoryTheme.primary(send);
        assertEquals(StoryTheme.ACCENT, send.getBackground());
        send.setEnabled(false);
        assertNotEquals(StoryTheme.ACCENT, send.getBackground(), "a disabled button does not look pressable");
        send.setEnabled(true);
        assertEquals(StoryTheme.ACCENT, send.getBackground());
        StoryTheme.plain(send);
        assertEquals(plain, send.getBackground());
    }

    @Test
    void layerColoursNestedPanelsButLeavesCardsTheirOwnShade() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel inner = new JPanel();
        JCheckBox box = new JCheckBox("Pinned");
        inner.add(box);
        StoryTheme.Card card = new StoryTheme.Card(new BorderLayout(), StoryTheme.RAISED, StoryTheme.ACCENT_SOFT);
        outer.add(inner, BorderLayout.CENTER);
        outer.add(card, BorderLayout.NORTH);
        StoryTheme.layer(outer, StoryTheme.SIDEBAR);
        assertEquals(StoryTheme.SIDEBAR, outer.getBackground());
        assertEquals(StoryTheme.SIDEBAR, inner.getBackground());
        assertEquals(StoryTheme.SIDEBAR, box.getBackground());
        assertEquals(StoryTheme.RAISED, card.getBackground(), "a card sits one layer above");
    }
}

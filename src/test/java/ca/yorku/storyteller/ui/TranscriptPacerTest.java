package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Checks how {@link TranscriptPacer} reveals text. Each test runs in one go on the event thread, so the
 * pacer's timer cannot fire in between and the steps are driven by calling {@code tick()}.
 */
class TranscriptPacerTest {

    private final JTextArea area = new JTextArea();

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    void addedTextIsRevealedAFewCharactersAtATime() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 300); // 6 characters per 20 ms tick
            pacer.show("Opening.", false);
            assertEquals("Opening.", area.getText(), "the first text appears at once");

            pacer.show("Opening. The gate creaks open.", true);
            assertEquals("Opening.", area.getText(), "nothing new is shown before the first tick");
            assertTrue(pacer.isRevealing());
            pacer.tick();
            assertEquals("Opening. The g", area.getText());
            pacer.tick();
            assertEquals("Opening. The gate cr", area.getText());
            while (pacer.isRevealing()) {
                pacer.tick();
            }
            assertEquals("Opening. The gate creaks open.", area.getText());
            assertEquals(area.getText().length(), area.getCaretPosition(), "the view follows the new text");
        });
    }

    @Test
    void skipShowsTheRestAtOnce() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 300);
            pacer.show("A.", false);
            pacer.show("A. Snow falls on the road.", true);
            pacer.tick();
            pacer.skip();
            assertFalse(pacer.isRevealing());
            assertEquals("A. Snow falls on the road.", area.getText());
        });
    }

    @Test
    void textThatDoesNotExtendTheTranscriptReplacesItAtOnce() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 300);
            pacer.show("A. First turn.", false);
            pacer.show("A. Edited turn.", true);
            assertEquals("A. Edited turn.", area.getText(), "an edit or undo is not replayed");
            pacer.show("Another story.", false);
            assertEquals("Another story.", area.getText());
            assertFalse(pacer.isRevealing());
        });
    }

    @Test
    void moreTextMidRevealContinuesFromWhatIsShown() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 300);
            pacer.show("", false);
            pacer.show("One two three.", true);
            pacer.tick();
            assertEquals("One tw", area.getText());
            pacer.show("One two three. Four.", true);
            assertEquals("One tw", area.getText(), "what is on screen stays");
            pacer.skip();
            assertEquals("One two three. Four.", area.getText());
        });
    }

    @Test
    void aLongRevealIsSpedUpToStayUnderTheLimit() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 300);
            pacer.show("", false);
            pacer.show("x".repeat(30_000), true);
            int ticks = 0;
            while (pacer.isRevealing()) {
                pacer.tick();
                ticks++;
            }
            assertTrue(ticks * TranscriptPacer.TICK_MS <= TranscriptPacer.MAX_REVEAL_MS, ticks + " ticks");
        });
    }

    @Test
    void aSpeedOfZeroShowsEverythingAtOnce() throws Exception {
        onEdt(() -> {
            TranscriptPacer pacer = new TranscriptPacer(area, 0);
            pacer.show("A.", false);
            pacer.show("A. B.", true);
            assertEquals("A. B.", area.getText());
            assertFalse(pacer.isRevealing());
        });
    }
}

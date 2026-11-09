package ca.yorku.storyteller.ui;

import javax.swing.JTextArea;
import javax.swing.Timer;

/**
 * Shows the story transcript in a text area, revealing newly added text a few characters at a time
 * so a new turn reads as if it is being written, instead of appearing all at once. Only the display
 * is paced: the turn is already complete and saved in the session when it is shown.
 *
 * <p>Text that does not simply extend what is shown (a load, an undo, an edit) appears at once. A
 * long reveal is sped up so it never takes much longer than {@link #MAX_REVEAL_MS}, and
 * {@link #skip()} shows the rest immediately.
 *
 * <p>Features: F04, F10, F12 (display only).
 */
final class TranscriptPacer {

    /** Milliseconds between two reveal steps. */
    static final int TICK_MS = 20;
    /** The longest a reveal should take, however much text arrived. */
    static final int MAX_REVEAL_MS = 10_000;

    private final JTextArea area;
    private final Timer timer;
    private int charsPerSecond;
    /** The full text that should end up in the area. */
    private String target = "";
    /** How many characters of {@link #target} the area shows. */
    private int shown;
    /** Characters revealed per tick for the current reveal. */
    private int step = 1;

    /**
     * @param charsPerSecond reveal speed; 0 or less shows every change at once
     */
    TranscriptPacer(JTextArea area, int charsPerSecond) {
        this.area = area;
        this.charsPerSecond = charsPerSecond;
        this.timer = new Timer(TICK_MS, e -> tick());
    }

    void setCharsPerSecond(int charsPerSecond) {
        this.charsPerSecond = charsPerSecond;
        if (charsPerSecond <= 0) {
            skip();
        }
    }

    /**
     * Shows {@code text}. When {@code pace} is true and the text extends what is already there, only
     * the new part is revealed gradually; otherwise the area is replaced at once.
     */
    void show(String text, boolean pace) {
        if (text.equals(target)) {
            return;
        }
        if (pace && charsPerSecond > 0 && text.startsWith(target)) {
            // Keep what is on screen (even mid-reveal) and reveal the rest from there.
            target = text;
            int remaining = target.length() - shown;
            int base = Math.max(1, charsPerSecond * TICK_MS / 1000);
            int forMax = (int) Math.ceil(remaining * (double) TICK_MS / MAX_REVEAL_MS);
            step = Math.max(base, forMax);
            timer.start();
            return;
        }
        timer.stop();
        target = text;
        shown = text.length();
        area.setText(text);
        moveCaretToEnd();
    }

    /** Reveals the next few characters; called by the timer. */
    void tick() {
        if (shown >= target.length()) {
            timer.stop();
            return;
        }
        int next = Math.min(target.length(), shown + step);
        if (next < target.length() && Character.isHighSurrogate(target.charAt(next - 1))) {
            next++;
        }
        area.append(target.substring(shown, next));
        shown = next;
        moveCaretToEnd();
        if (shown >= target.length()) {
            timer.stop();
        }
    }

    /** Shows the rest of the text now. */
    void skip() {
        if (shown < target.length()) {
            area.append(target.substring(shown));
            shown = target.length();
            moveCaretToEnd();
        }
        timer.stop();
    }

    /** True while part of the text is still hidden. */
    boolean isRevealing() {
        return shown < target.length();
    }

    private void moveCaretToEnd() {
        area.setCaretPosition(area.getDocument().getLength());
    }
}

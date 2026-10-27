package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The three input modes differ only in how they frame the input (Strategy). */
class InputModeStrategyTest {

    @Test
    void namesMatchWhatTheUserPicks() {
        assertEquals("Do", new DoModeStrategy().getName());
        assertEquals("Say", new SayModeStrategy().getName());
        assertEquals("Story", new StoryModeStrategy().getName());
    }

    @Test
    void doFramesAnAttemptedAction() {
        String f = new DoModeStrategy().frame("  climb the wall ");
        assertTrue(f.startsWith("The user's character attempts this action: climb the wall\n"));
    }

    @Test
    void sayQuotesTheLineOnce() {
        assertTrue(new SayModeStrategy().frame("Hello").contains("says, aloud: \"Hello\""));
        assertTrue(new SayModeStrategy().frame("\"Hello\"").contains("says, aloud: \"Hello\"\n"));
    }

    @Test
    void storyContinuesFromTheUsersPassage() {
        String f = new StoryModeStrategy().frame("The bells rang.");
        assertTrue(f.endsWith(":\nThe bells rang."));
        assertTrue(f.contains("continue directly"));
    }
}

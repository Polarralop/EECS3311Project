package ca.yorku.storyteller.controller;

import java.time.Instant;

/**
 * A saved story as the front ends list it: its id (what {@link StoryController#loadSession} takes),
 * its title and when it was last saved.
 *
 * <p>Features: F11.
 */
public record SavedStory(String id, String title, Instant savedAt) {
}

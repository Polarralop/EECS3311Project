package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One lore fact with tags and a pinned flag.
 *
 * <p>Features: F02 F07 F08.
 * <p>Patterns: MVC (Model).
 */
public class LoreEntry {

    /** A tag is one lower-case word: letters, digits and hyphens. */
    private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private String id;
    private String title;
    private String content;
    private List<String> tags = new ArrayList<>();
    private boolean pinned;

    /** For JSON loading only. */
    private LoreEntry() {
    }

    /**
     * Creates an entry. The id may be null; {@link LoreBook#add} then assigns one.
     *
     * @throws IllegalArgumentException if the title or content is empty or a tag is not valid
     */
    public LoreEntry(String id, String title, String content, List<String> tags, boolean pinned) {
        this.id = id;
        setTitle(title);
        setContent(content);
        setTags(tags);
        this.pinned = pinned;
    }

    /** Returns a copy of this entry, so callers can edit it without touching the stored one. */
    public LoreEntry copy() {
        return new LoreEntry(id, title, content, tags, pinned);
    }

    /**
     * True when every word of the query appears in the title, the content or a tag, ignoring case.
     * A blank query matches every entry.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = (title + "\n" + content + "\n" + String.join(" ", tags)).toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    public String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A lore entry needs a title.");
        }
        this.title = title.strip();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A lore entry needs some content.");
        }
        this.content = content.strip();
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    /**
     * Replaces the tags. Tags are trimmed and lower-cased; duplicates are dropped.
     *
     * @throws IllegalArgumentException if a tag is not one word of letters, digits or hyphens
     */
    public void setTags(List<String> newTags) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (newTags != null) {
            for (String tag : newTags) {
                String t = tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
                if (!TAG.matcher(t).matches()) {
                    throw new IllegalArgumentException("\"" + tag + "\" is not a valid tag. Use one word of letters, digits or hyphens.");
                }
                cleaned.add(t);
            }
        }
        this.tags = new ArrayList<>(cleaned);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return (pinned ? "[pinned] " : "") + title + " (" + id + ")";
    }
}

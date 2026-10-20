package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stores lore entries and searches them; pinned entries are canon.
 *
 * <p>Features: F02 F04 F07 F08.
 * <p>Patterns: MVC (Model).
 */
public class LoreBook {

    private final List<LoreEntry> entries = new ArrayList<>();
    private int nextNumber = 1;

    /**
     * Adds a new entry. An entry without an id gets the next free one ("L1", "L2", ...).
     *
     * @throws IllegalArgumentException if another entry already has the same id or title
     */
    public void add(LoreEntry entry) {
        if (entry.getId() == null || entry.getId().isBlank()) {
            entry.setId(nextFreeId());
        } else if (find(entry.getId()).isPresent()) {
            throw new IllegalArgumentException("A lore entry with id " + entry.getId() + " already exists.");
        }
        requireUniqueTitle(entry);
        entries.add(entry);
    }

    /**
     * Replaces the stored entry that has the same id.
     *
     * @throws IllegalArgumentException if no entry has that id, or another entry has the same title
     */
    public void update(LoreEntry entry) {
        int index = indexOf(entry.getId());
        if (index < 0) {
            throw new IllegalArgumentException("There is no lore entry with id " + entry.getId() + ".");
        }
        requireUniqueTitle(entry);
        entries.set(index, entry);
    }

    /**
     * Removes the entry with this id.
     *
     * @throws IllegalArgumentException if no entry has that id
     */
    public void remove(String id) {
        int index = indexOf(id);
        if (index < 0) {
            throw new IllegalArgumentException("There is no lore entry with id " + id + ".");
        }
        entries.remove(index);
    }

    /** Entries matching the query (see {@link LoreEntry#matches}), pinned first, then by title. */
    public List<LoreEntry> search(String query) {
        return entries.stream()
                .filter(e -> e.matches(query))
                .sorted(Comparator.comparing(LoreEntry::isPinned).reversed()
                        .thenComparing(LoreEntry::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The pinned entries, which the consistency check treats as canon. */
    public List<LoreEntry> getPinned() {
        return entries.stream().filter(LoreEntry::isPinned).toList();
    }

    public List<LoreEntry> getAll() {
        return List.copyOf(entries);
    }

    public Optional<LoreEntry> find(String id) {
        int index = indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(entries.get(index));
    }

    public int size() {
        return entries.size();
    }

    private int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equalsIgnoreCase(id == null ? "" : id.strip())) {
                return i;
            }
        }
        return -1;
    }

    private void requireUniqueTitle(LoreEntry entry) {
        for (LoreEntry other : entries) {
            if (!other.getId().equals(entry.getId()) && other.getTitle().equalsIgnoreCase(entry.getTitle())) {
                throw new IllegalArgumentException("A lore entry titled \"" + entry.getTitle() + "\" already exists.");
            }
        }
    }

    private String nextFreeId() {
        String id;
        do {
            id = "L" + nextNumber++;
        } while (find(id).isPresent());
        return id;
    }
}

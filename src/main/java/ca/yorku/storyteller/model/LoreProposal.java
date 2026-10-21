package ca.yorku.storyteller.model;

import java.util.List;

/**
 * A lore entry suggested by the agent, waiting for the user's decision.
 *
 * <p>Features: F07.
 * <p>Patterns: MVC (Model).
 */
public class LoreProposal {

    private String id;
    private String title;
    private String content;
    private ProposalStatus status = ProposalStatus.PENDING;

    /** For JSON loading only. */
    private LoreProposal() {
    }

    /**
     * The id may be null; {@link StorySession#addProposal} then assigns one.
     *
     * @throws IllegalArgumentException if the title or content is empty
     */
    public LoreProposal(String id, String title, String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("A lore proposal needs a title and content.");
        }
        this.id = id;
        this.title = title.strip();
        this.content = content.strip();
    }

    /**
     * Marks the proposal approved and creates the lore entry for it (without an id; the lore book assigns one).
     *
     * @throws IllegalStateException if the proposal was already resolved
     */
    public LoreEntry approve() {
        requirePending();
        status = ProposalStatus.APPROVED;
        return new LoreEntry(null, title, content, List.of(), false);
    }

    /**
     * @throws IllegalStateException if the proposal was already resolved
     */
    public void reject() {
        requirePending();
        status = ProposalStatus.REJECTED;
    }

    private void requirePending() {
        if (status != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + id + " was already " + status.name().toLowerCase() + ".");
        }
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

    public String getContent() {
        return content;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    /** Puts an approval back when the lore book refused the entry. */
    void reopen() {
        status = ProposalStatus.PENDING;
    }
}

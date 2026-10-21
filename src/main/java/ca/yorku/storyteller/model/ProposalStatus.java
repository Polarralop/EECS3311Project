package ca.yorku.storyteller.model;

/**
 * Review state of a lore proposal.
 *
 * <p>Features: F07.
 */
public enum ProposalStatus {
    /** Waiting for the user's decision. */
    PENDING,
    /** Approved and added to the lore book. */
    APPROVED,
    /** Rejected; it never reaches the lore book. */
    REJECTED
}

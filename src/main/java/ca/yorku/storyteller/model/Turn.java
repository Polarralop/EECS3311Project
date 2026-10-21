package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One user input and the agent's narration, with the lore used and any consistency warnings.
 *
 * <p>Features: F04 F08 F09 F10.
 * <p>Patterns: MVC (Model).
 */
public class Turn {

    private int number;
    private String modeName;
    private String userInput;
    private String narration;
    private List<String> usedLoreIds = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    /** For JSON loading only. */
    private Turn() {
    }

    public Turn(int number, String modeName, String userInput, String narration,
                List<String> usedLoreIds, List<String> warnings) {
        this.number = number;
        this.modeName = modeName;
        this.userInput = userInput == null ? "" : userInput;
        setNarration(narration);
        this.usedLoreIds = new ArrayList<>(usedLoreIds == null ? List.of() : usedLoreIds);
        this.warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
    }

    public int getNumber() {
        return number;
    }

    public String getModeName() {
        return modeName;
    }

    public String getUserInput() {
        return userInput;
    }

    public String getNarration() {
        return narration;
    }

    /**
     * Replaces the narration (edit turn, F10).
     *
     * @throws IllegalArgumentException if the text is empty
     */
    public void setNarration(String narration) {
        if (narration == null || narration.isBlank()) {
            throw new IllegalArgumentException("A turn's narration cannot be empty.");
        }
        this.narration = narration;
    }

    public List<String> getUsedLoreIds() {
        return List.copyOf(usedLoreIds);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }
}

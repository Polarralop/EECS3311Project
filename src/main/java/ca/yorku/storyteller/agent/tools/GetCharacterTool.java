package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.StorySession;
import java.util.Map;

/**
 * Lets the model look up a character during a turn.
 *
 * <p>Features: F04.
 */
public class GetCharacterTool implements AgentTool {

    @Override
    public String getName() {
        return "get_character";
    }

    @Override
    public String getDescription() {
        return "Look up a character's description, traits and relationships by name. Use it before "
                + "a known character speaks or acts, so they stay in character.";
    }

    @Override
    public String getSchema() {
        return """
                {"type": "object",
                 "properties": {"name": {"type": "string", "description": "The character's name."}},
                 "required": ["name"]}""";
    }

    @Override
    public ToolResult execute(Map<String, String> args, StorySession session) {
        String name = args.getOrDefault("name", "").strip();
        return session.findCharacter(name)
                .map(c -> ToolResult.ok(c.describe()))
                .orElseGet(() -> ToolResult.error("There is no character named " + name + ". Known characters: "
                        + (session.getCharacters().isEmpty() ? "none"
                        : String.join(", ", session.getCharacters().stream().map(Character::getName).toList())) + "."));
    }
}

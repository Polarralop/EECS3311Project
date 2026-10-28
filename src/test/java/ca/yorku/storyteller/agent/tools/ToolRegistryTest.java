package ca.yorku.storyteller.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.llm.LLMToolSpec;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.StorySession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Validation in {@link ToolRegistry} and the two lookup tools of F04. */
class ToolRegistryTest {

    private ToolRegistry registry;
    private StorySession session;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new SearchLoreTool());
        registry.register(new GetCharacterTool());
        session = new StorySession("s1");
        session.getLoreBook().add(new LoreEntry(null, "Ashfall", "A city under grey snow.", List.of("city"), true));
        session.getLoreBook().add(new LoreEntry(null, "The Ember Guild", "Smiths of Ashfall.", List.of("faction"), false));
        session.addCharacter(new Character("Mira", "A courier.", List.of("quick")));
        session.addCharacter(new Character("Orrin", "Guildmaster.", List.of()));
        session.addRelationship("Mira", new Relationship("Orrin", "debtor", ""));
    }

    private ToolResult run(String tool, Map<String, String> args) {
        return registry.execute(new ToolCall("t1", tool, args), session);
    }

    @Test
    void schemasListEveryToolInOrder() {
        List<LLMToolSpec> specs = registry.schemas();
        assertEquals(List.of("search_lore", "get_character"), specs.stream().map(LLMToolSpec::name).toList());
        assertTrue(specs.get(0).inputSchemaJson().contains("\"required\": [\"query\"]"));
    }

    @Test
    void registeringTheSameNameTwiceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(new SearchLoreTool()));
    }

    @Test
    void unknownToolComesBackAsAFailedResult() {
        ToolResult r = run("delete_everything", Map.of());
        assertFalse(r.success());
        assertTrue(r.content().contains("search_lore, get_character"));
    }

    @Test
    void missingOrBlankRequiredArgumentIsRejectedBeforeTheToolRuns() {
        assertFalse(run("search_lore", Map.of()).success());
        ToolResult blank = run("get_character", Map.of("name", "  "));
        assertFalse(blank.success());
        assertEquals("get_character needs a value for: name.", blank.content());
    }

    @Test
    void aToolThatThrowsBecomesAFailedResult() {
        registry.register(new AgentTool() {
            public String getName() {
                return "broken";
            }

            public String getDescription() {
                return "";
            }

            public String getSchema() {
                return "{\"type\": \"object\", \"properties\": {}}";
            }

            public ToolResult execute(Map<String, String> args, StorySession s) {
                throw new IllegalArgumentException("No such event.");
            }
        });
        ToolResult r = run("broken", Map.of());
        assertFalse(r.success());
        assertEquals("No such event.", r.content());
    }

    @Test
    void invalidSchemaIsRejectedAtRegistration() {
        AgentTool bad = new GetCharacterTool() {
            @Override
            public String getName() {
                return "bad";
            }

            @Override
            public String getSchema() {
                return "{not json";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> registry.register(bad));
    }

    @Test
    void searchLoreReturnsEntriesWithIdsAndMarksCanon() {
        ToolResult r = run("search_lore", Map.of("query", "ashfall"));
        assertTrue(r.success());
        assertEquals(List.of("L1", "L2"), r.loreIds());
        assertTrue(r.content().startsWith("L1 [canon] Ashfall (city): A city under grey snow."));
    }

    @Test
    void searchLoreFallsBackToAnyWordWhenNoEntryHasEveryWord() {
        ToolResult r = run("search_lore", Map.of("query", "guild dragons"));
        assertEquals(List.of("L2"), r.loreIds());
    }

    @Test
    void searchLoreWithNoMatchSucceedsWithNoLore() {
        ToolResult r = run("search_lore", Map.of("query", "dragons"));
        assertTrue(r.success());
        assertTrue(r.loreIds().isEmpty());
        assertTrue(r.content().startsWith("No lore entries match"));
    }

    @Test
    void searchLoreShowsAtMostFiveEntries() {
        for (int i = 0; i < 7; i++) {
            session.getLoreBook().add(new LoreEntry(null, "Street " + i, "A street in the city.", List.of(), false));
        }
        ToolResult r = run("search_lore", Map.of("query", "street"));
        assertEquals(SearchLoreTool.MAX_RESULTS, r.loreIds().size());
        assertTrue(r.content().endsWith("(2 more; search with more specific words.)"));
    }

    @Test
    void getCharacterDescribesTheCharacterIgnoringCase() {
        ToolResult r = run("get_character", Map.of("name", "mira"));
        assertTrue(r.success());
        assertTrue(r.content().contains("Mira: A courier."));
        assertTrue(r.content().contains("debtor of Orrin"));
    }

    @Test
    void getCharacterForAnUnknownNameListsTheKnownOnes() {
        ToolResult r = run("get_character", Map.of("name", "Vex"));
        assertFalse(r.success());
        assertEquals("There is no character named Vex. Known characters: Mira, Orrin.", r.content());
    }
}

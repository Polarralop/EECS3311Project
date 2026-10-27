package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@link StoryCLI} with scripted input, through the real controller and repository. */
class StoryCLITest {

    /** Scripted model; tests never make live calls. */
    private final FakeLLMClient llm = new FakeLLMClient();

    @TempDir
    Path dir;

    private record Run(StoryController controller, String output) {
    }

    private Run run(String script) {
        StoryController controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        StoryCLI cli = new StoryCLI(controller, new BufferedReader(new StringReader(script)), out);
        controller.addObserver(cli);
        cli.run();
        return new Run(controller, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void loreCommandsAddSearchPinAndRemove() {
        Run r = run("""
                new
                lore add
                Ashfall
                A city under grey snow.
                place, city
                n
                lore add
                The Ember Guild
                Smiths of Ashfall.
                faction
                y
                lore search city
                lore pin L1
                lore list
                lore remove L2
                lore show L2
                quit
                """);
        assertTrue(r.output().contains("Added L1."));
        assertTrue(r.output().contains("L1 Ashfall  #place #city"));
        assertTrue(r.output().contains("L1 [pinned] Ashfall"));
        assertTrue(r.output().contains("Error: There is no lore entry with id L2."));
        assertEquals(1, r.controller().searchLore("").size());
    }

    @Test
    void startAsksForTheScenarioAndNeverPrintsThePlan() {
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"The letter is stolen.\"]}"));
        Run r = run("""
                new
                start


                A courier carries a forbidden letter.
                grim
                A city under grey snow
                Mira waits at the gate.
                a betrayal
                """);
        assertTrue(r.output().contains("Preparing the story..."));
        assertTrue(r.output().contains("The story \"A courier carries a forbidden letter\" has begun."));
        assertFalse(r.output().contains("The letter is stolen"));
        assertFalse(r.output().contains("a betrayal"));
        assertEquals(1, r.controller().getSession().getPlot().getEvents().size());
    }

    @Test
    void startCanDraftTheScenarioAndLetTheUserEditIt() {
        llm.enqueue(LLMResponse.ofText("{\"title\": \"Ashfall\", \"premise\": \"A courier carries a letter.\", "
                        + "\"tone\": \"grim\", \"setting\": \"A city under snow.\", \"openingSituation\": \"\"}"))
                .enqueue(LLMResponse.ofText("{\"events\": [\"E\"]}"));
        Run r = run("""
                start
                a courier in a snowy city
                y
                Ember Road





                """);
        assertTrue(r.output().contains("Premise: A courier carries a letter."));
        assertTrue(r.output().contains("Premise [A courier carries a letter.]: "));
        assertEquals("Ember Road", r.controller().getSession().getScenario().getTitle());
        assertEquals("A city under snow.", r.controller().getSession().getScenario().getSetting());
    }

    @Test
    void aRejectedDraftStartsNothing() {
        llm.enqueue(LLMResponse.ofText("{\"title\": \"T\", \"premise\": \"P\", \"setting\": \"S\"}"));
        Run r = run("start\nan idea\nn\n");
        assertTrue(r.output().contains("Draft discarded."));
        assertEquals(1, llm.getRequests().size());
        assertEquals(null, r.controller().getSession());
    }

    @Test
    void aFailedStartIsShownAsAnError() {
        llm.enqueueFailure(new LLMException("Could not reach Claude. Check your internet connection."));
        Run r = run("start\n\n\npremise\n\nsetting\n\n\n");
        assertTrue(r.output().contains("Error: Could not reach Claude. Check your internet connection."));
    }

    /** Queues a one-event plan for the start command; call it before queueing the turns. */
    private FakeLLMClient withPlan() {
        return llm.enqueue(LLMResponse.ofText("{\"events\": [\"The letter is stolen.\"]}"));
    }

    /** Starts a story (the plan must be queued with {@link #withPlan()}), then runs the rest of the script. */
    private Run started(String script) {
        return run("start\n\nAshfall\nA courier carries a letter.\n\nA city under snow\n\n\n" + script);
    }

    @Test
    void turnsUndoAndRetryFromTheCli() {
        withPlan().enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("a", "search_lore", Map.of("query", "ashfall")))))
                .enqueue(LLMResponse.ofText("Snow settles on the gate."))
                .enqueue(LLMResponse.ofText("The guard nods."))
                .enqueue(LLMResponse.ofText("The guard laughs instead."));
        Run r = started("""
                lore add
                Ashfall
                A city under grey snow.

                y
                do open the gate
                say Good evening, friend
                retry
                turns
                undo
                undo
                undo
                do
                """);
        String out = r.output();
        assertTrue(out.contains("Snow settles on the gate.\n(Lore used: L1 Ashfall)"));
        assertTrue(out.contains("The guard laughs instead."));
        assertTrue(out.contains("> Say: Good evening, friend"));
        assertTrue(out.contains("Error: There is nothing to undo."));
        assertTrue(out.contains("Error: Usage: do <text>"));
        assertFalse(out.contains("The letter is stolen"), "the plan is never printed");
        assertTrue(r.controller().getSession().getTurns().isEmpty());
    }

    @Test
    void aFailedRetryGivesTheInputBack() {
        withPlan().enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofRefusal());
        Run r = started("do climb the wall\nretry\n");
        assertTrue(r.output().contains("The turn stays undone; your input was: climb the wall"));
    }

    @Test
    void turnsBeforeStartingAreRefused() {
        Run r = run("new\ndo something\n");
        assertTrue(r.output().contains("Error: Start the story first."));
    }

    @Test
    void loreEditKeepsValuesOnEnter() {
        Run r = run("""
                new
                lore add
                Ashfall
                A city.

                n
                lore edit L1

                A city buried in ash.


                """);
        assertEquals("A city buried in ash.", r.controller().getLoreEntry("L1").getContent());
        assertEquals("Ashfall", r.controller().getLoreEntry("L1").getTitle());
    }

    @Test
    void invalidInputPrintsAnErrorAndCarriesOn() {
        Run r = run("""
                lore list
                new
                lore add

                content

                n
                frobnicate
                help
                """);
        assertTrue(r.output().contains("Error: Start a new story or load one first."));
        assertTrue(r.output().contains("Error: A lore entry needs a title."));
        assertTrue(r.output().contains("Error: Unknown command \"frobnicate\""));
        assertTrue(r.output().contains("Commands:"));
    }

    @Test
    void characterAndRelationshipCommands() {
        Run r = run("""
                new
                char add
                Mira
                Apprentice smith.
                stubborn, curious
                char add
                Guildmaster Orrin
                Head of the guild.

                rel add
                Mira
                guildmaster orrin
                apprentice
                does not trust him
                rel add
                Mira
                Ghost
                friend

                char show Mira
                char edit Guildmaster Orrin
                Orrin

                patient
                char list
                """);
        assertTrue(r.output().contains("Mira is now apprentice of Guildmaster Orrin (does not trust him)."));
        assertTrue(r.output().contains("Error: There is no character named Ghost to relate to."));
        assertTrue(r.output().contains("Traits: stubborn, curious"));
        assertTrue(r.output().contains("Orrin: Head of the guild."));
        assertEquals("Orrin", r.controller().getCharacter("Mira").getRelationships().get(0).getTargetName());
    }

    @Test
    void saveListAndLoad() {
        Run first = run("""
                new
                char add
                Mira


                save
                """);
        String id = first.controller().getSession().getId();
        Run second = run("sessions\nload " + id + "\nload nope\nchar list\n");
        assertTrue(second.output().contains(id));
        assertTrue(second.output().contains("Loaded \"" + id + "\": 0 lore entries, 1 characters, 0 turns."));
        assertTrue(second.output().contains("Error: There is no saved story called nope."));
        assertTrue(second.output().contains("Mira"));
        assertTrue(second.output().contains("[" + id + ": 0 lore, 1 characters, 0 turns]"));
    }
}

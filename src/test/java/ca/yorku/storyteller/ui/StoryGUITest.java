package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.controller.SavedStory;
import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link StoryGUI}'s panels without a window (works headless): fills the forms, clicks the
 * buttons and checks what reaches the controller and what the observer refresh shows.
 */
class StoryGUITest {

    /** Scripted model; tests never make live calls. */
    private final FakeLLMClient llm = new FakeLLMClient();

    @TempDir
    Path dir;

    private StoryController controller;
    private TestGUI gui;

    /** Records dialogs instead of opening them. */
    private static class TestGUI extends StoryGUI {
        final List<String> errors = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        String sessionToPick;
        List<SavedStory> offeredStories;
        int startFormOpened;
        /** Answers to yes/no questions, in order; the questions are recorded. */
        final java.util.ArrayDeque<Boolean> answers = new java.util.ArrayDeque<>();
        final List<String> questions = new ArrayList<>();

        @Override
        protected boolean confirm(String question) {
            questions.add(question);
            return answers.isEmpty() ? false : answers.poll();
        }

        TestGUI(StoryController c) {
            super(c);
            // Most tests read the transcript right after a turn, so show text at once.
            pacer.setCharsPerSecond(0);
        }

        @Override
        public void showError(String message) {
            errors.add(message);
        }

        @Override
        protected void showMessage(String message) {
            messages.add(message);
        }

        @Override
        protected String chooseSession(List<SavedStory> stories) {
            offeredStories = stories;
            return sessionToPick;
        }

        String narrationToType;

        @Override
        protected String askNarration(String current) {
            return narrationToType;
        }

        @Override
        protected void openStartForm() {
            startFormOpened++;
        }

        /** Model calls run straight away on the test's thread instead of a SwingWorker. */
        @Override
        protected <T> void runInBackground(String busyText, Supplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
            completeNow(work, onSuccess, onFailure);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        onEdt(() -> {
            gui = new TestGUI(controller);
            controller.addObserver(gui);
        });
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void savingLoreWithoutAStoryShowsAnError() throws Exception {
        onEdt(() -> {
            gui.loreTitle.setText("Ashfall");
            gui.loreContent.setText("A city.");
            gui.loreSave.doClick();
        });
        assertEquals(List.of("Start a new story or load one first."), gui.errors);
    }

    @Test
    void loreFormAddsEditsSearchesAndDeletes() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.loreTitle.setText("Ashfall");
            gui.loreContent.setText("A city under grey snow.");
            gui.loreTags.setText("place, city");
            gui.loreSave.doClick();
            gui.loreNew.doClick();
            gui.loreTitle.setText("Ember Guild");
            gui.loreContent.setText("Smiths.");
            gui.lorePinned.setSelected(true);
            gui.loreSave.doClick();
        });
        assertEquals(2, gui.loreItems.size());
        assertEquals("Ember Guild", gui.loreItems.get(0).getTitle(), "pinned entries are listed first");
        assertTrue(gui.status.getText().contains("2 lore entries"));

        onEdt(() -> {
            gui.loreList.setSelectedIndex(1);
            gui.loreContent.setText("A city buried in ash.");
            gui.loreSave.doClick();
            gui.loreSearch.setText("ash");
        });
        assertEquals("A city buried in ash.", controller.getLoreEntry("L1").getContent());
        assertEquals(1, gui.loreItems.size());

        onEdt(() -> {
            gui.loreSearch.setText("");
            gui.loreList.setSelectedIndex(0);
            gui.loreDelete.doClick();
        });
        assertEquals(1, controller.searchLore("").size());
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void invalidLoreShowsTheModelsMessage() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.loreContent.setText("No title.");
            gui.loreSave.doClick();
            gui.loreTitle.setText("Bad tags");
            gui.loreTags.setText("two words");
            gui.loreSave.doClick();
        });
        assertEquals("A lore entry needs a title.", gui.errors.get(0));
        assertTrue(gui.errors.get(1).contains("not a valid tag"));
    }

    @Test
    void characterFormAddsRenamesAndRelates() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.characterName.setText("Mira");
            gui.characterTraits.setText("stubborn, curious");
            gui.characterSave.doClick();
            gui.characterNew.doClick();
            gui.characterName.setText("Tomas");
            gui.characterSave.doClick();
            gui.characterList.setSelectedIndex(0);
            gui.relationshipTarget.setSelectedItem("Tomas");
            gui.relationshipType.setText("sister");
            gui.relationshipAdd.doClick();
        });
        assertEquals(1, gui.relationshipItems.size());
        assertEquals("sister", controller.getCharacter("Mira").relationshipTo("Tomas").orElseThrow().getType());

        onEdt(() -> {
            gui.characterList.setSelectedIndex(1);
            gui.characterName.setText("Tomas Vell");
            gui.characterSave.doClick();
        });
        assertEquals("Tomas Vell", controller.getCharacter("Mira").getRelationships().get(0).getTargetName());
        assertEquals(List.of("stubborn", "curious"), controller.getCharacter("Mira").getTraits());

        onEdt(() -> {
            gui.characterList.setSelectedIndex(0);
            gui.relationshipList.setSelectedIndex(0);
            gui.relationshipRemove.doClick();
            gui.characterNew.doClick();
            gui.characterName.setText("mira");
            gui.characterSave.doClick();
        });
        assertTrue(controller.getCharacter("Mira").getRelationships().isEmpty());
        assertEquals(List.of("A character named mira already exists."), gui.errors);
    }

    @Test
    void saveAndLoadThroughTheMenuActions() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.characterName.setText("Mira");
            gui.characterSave.doClick();
            gui.act(gui::save);
        });
        String id = controller.getSession().getId();
        onEdt(() -> {
            controller.newSession();
            gui.sessionToPick = id;
            gui.act(gui::load);
        });
        assertEquals(1, gui.characterItems.size());
        assertTrue(gui.recap.getText().startsWith("Loaded"));
        assertTrue(gui.recapPanel.isVisible());
        assertTrue(gui.status.getText().contains(id));
        onEdt(() -> controller.newSession());
        assertFalse(gui.recapPanel.isVisible(), "the recap belongs to the loaded story");
    }

    // ---- start screen and story tab title (F01, F11) ----

    @Test
    void withNoSavesTheAppOpensToNoStoriesDetectedAndCreateStory() throws Exception {
        assertTrue(gui.isShowingHome(), "no story tabs before a story is open");
        assertEquals("No stories detected", gui.homeHeading.getText());
        assertEquals(null, gui.openStory.getParent(), "only Create story... is offered");
        assertTrue(gui.createStory.getParent() != null);
        onEdt(() -> gui.createStory.doClick());
        assertEquals(1, gui.startFormOpened);
        startFromTheForm();
        assertFalse(gui.isShowingHome(), "starting a story opens its tabs");
    }

    @Test
    void withSavesTheAppOpensToSelectStoryAndOpensThePickedOne() throws Exception {
        startFromTheForm();
        onEdt(() -> gui.act(gui::save));
        String id = controller.getSession().getId();
        StoryController fresh = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        TestGUI[] second = new TestGUI[1];
        onEdt(() -> {
            second[0] = new TestGUI(fresh);
            fresh.addObserver(second[0]);
        });
        TestGUI g = second[0];
        assertTrue(g.isShowingHome());
        assertEquals("Select story", g.homeHeading.getText());
        SavedStory listed = g.savedStoryItems.get(0);
        assertEquals(1, g.savedStoryItems.size());
        assertEquals(id, listed.id());
        assertEquals("A courier carries a forbidden letter", listed.title());
        java.awt.Component cell = g.savedStoryList.getCellRenderer()
                .getListCellRendererComponent(g.savedStoryList, listed, 0, false, false);
        String html = ((javax.swing.JLabel) cell).getText();
        assertFalse(html.contains(id), "the id is not shown");
        assertTrue(html.contains("<font color='" + StoryGUI.savedColour(false) + "'>Saved "
                + StoryGUI.savedLabel(listed.savedAt(), java.time.ZoneId.systemDefault()) + "</font>"), html);
        assertEquals(0, g.savedStoryList.getSelectedIndex(), "the newest story is selected");

        onEdt(() -> g.openStory.doClick());
        assertFalse(g.isShowingHome());
        assertEquals(id, fresh.getSession().getId());
        assertEquals("A courier carries a forbidden letter", g.storyHeading.getText());
        assertTrue(g.transcript.getText().startsWith("Mira waits at the gate."));
        assertTrue(g.errors.isEmpty(), g.errors.toString());
    }

    @Test
    void theHeadingNamesTheStoryAndALongTitleIsWholeInItsTooltip() throws Exception {
        onEdt(() -> controller.newSession());
        assertEquals("Untitled story", gui.storyHeading.getText(), "a story prepared before its scenario");
        assertTrue(gui.storyTitle.isVisible() && gui.storyTitle.getText().startsWith("Story not started yet"));
        llm.enqueue(LLMResponse.ofText(PLAN));
        String longTitle = "The Very Long and Winding Road to the Ember Gate of Ashfall";
        onEdt(() -> {
            gui.scenarioTitle.setText(longTitle);
            gui.scenarioPremise.setText("A courier.");
            gui.scenarioSetting.setText("Snow.");
            gui.startButton.doClick();
        });
        assertEquals(longTitle, gui.storyHeading.getText());
        assertEquals(longTitle, gui.storyHeading.getToolTipText());
    }

    @Test
    void theLoadDialogOffersStoriesByTitle() throws Exception {
        startFromTheForm();
        onEdt(() -> gui.act(gui::save));
        String id = controller.getSession().getId();
        onEdt(() -> {
            gui.sessionToPick = null;
            gui.act(gui::load);
        });
        assertEquals(id, gui.offeredStories.get(0).id());
        assertEquals("A courier carries a forbidden letter  (saved "
                + StoryGUI.savedLabel(gui.offeredStories.get(0).savedAt(), java.time.ZoneId.systemDefault()) + ")",
                StoryGUI.choiceLabel(gui.offeredStories.get(0)));
    }

    @Test
    void theSaveTimeReadsAsADateAndTime() {
        java.time.Instant t = java.time.ZonedDateTime.of(2026, 11, 9, 16, 27, 0, 0, java.time.ZoneId.of("America/Toronto")).toInstant();
        assertEquals("Nov 9, 2026, 4:27 PM", StoryGUI.savedLabel(t, java.time.ZoneId.of("America/Toronto")));
    }

    @Test
    void loadingAStoryWithTurnsShowsTheRecapAtTheTopOfTheStoryTab() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("The gate opens."));
        onEdt(() -> {
            gui.input.setText("knock");
            gui.send.doClick();
            gui.act(gui::save);
        });
        String id = controller.getSession().getId();
        llm.enqueue(LLMResponse.ofText("You knocked, and the gate opened."));
        onEdt(() -> {
            controller.newSession();
            gui.sessionToPick = id;
            gui.act(gui::load);
        });
        assertTrue(gui.recap.getText().endsWith("The story so far: You knocked, and the gate opened."));
        assertTrue(gui.recapPanel.isVisible());
        assertTrue(gui.transcript.getText().endsWith("The gate opens."));
    }

    // ---- story tab, start form and proposals (F01, F04, F05, F07, F10) ----

    private static final String PLAN = "{\"events\": [\"The letter is stolen by the guild.\"]}";

    /** Fills the start form and clicks Start story. */
    private void startFromTheForm() throws Exception {
        llm.enqueue(LLMResponse.ofText(PLAN));
        onEdt(() -> {
            gui.scenarioPremise.setText("A courier carries a forbidden letter.");
            gui.scenarioSetting.setText("A city under grey snow");
            gui.scenarioOpening.setText("Mira waits at the gate.");
            gui.plotBasis.setText("a betrayal");
            gui.startButton.doClick();
        });
    }

    @Test
    void turnControlsAreUnavailableUntilTheStoryStarts() throws Exception {
        onEdt(() -> controller.newSession());
        assertFalse(gui.send.isEnabled());
        assertFalse(gui.input.isEnabled());
        assertFalse(gui.undo.isEnabled());
        assertTrue(gui.storyTitle.getText().startsWith("Story not started yet"));
    }

    @Test
    void theFormDraftsAScenarioAndStartsTheStoryWithoutShowingThePlan() throws Exception {
        llm.enqueue(LLMResponse.ofText("{\"title\": \"Ashfall\", \"premise\": \"A courier.\", \"tone\": \"grim\", "
                + "\"setting\": \"A snowy city.\", \"openingSituation\": \"At the gate.\"}"));
        onEdt(() -> {
            gui.scenarioIdea.setText("a courier in the snow");
            gui.generate.doClick();
        });
        assertEquals("Ashfall", gui.scenarioTitle.getText());
        assertEquals("A snowy city.", gui.scenarioSetting.getText());

        llm.enqueue(LLMResponse.ofText(PLAN));
        onEdt(() -> {
            gui.scenarioTitle.setText("Ember Road");
            gui.startButton.doClick();
        });
        assertTrue(controller.isStarted());
        assertEquals("Ember Road", gui.storyHeading.getText(), "the heading names the story");
        assertFalse(gui.storyTitle.isVisible());
        assertEquals("At the gate.", gui.transcript.getText());
        assertTrue(gui.send.isEnabled());
        assertEquals("", gui.scenarioTitle.getText(), "the form is cleared after a start");
        assertFalse(gui.transcript.getText().contains("stolen"));
        assertFalse(gui.status.getText().contains("stolen"));
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void aStartWithoutASettingShowsAnErrorAndStartsNothing() throws Exception {
        onEdt(() -> {
            gui.scenarioPremise.setText("A premise.");
            gui.startButton.doClick();
        });
        assertEquals(List.of("A story needs a setting."), gui.errors);
        assertFalse(controller.isStarted());
        assertEquals("A premise.", gui.scenarioPremise.getText(), "the form keeps what was typed");
    }

    @Test
    void sendShowsTheNarrationAndTheLoreUsed() throws Exception {
        onEdt(() -> {
            controller.newSession();
            controller.saveLoreEntry(new ca.yorku.storyteller.model.LoreEntry(null, "Ashfall", "A city.", List.of(), true));
        });
        startFromTheForm();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("a", "search_lore", Map.of("query", "ashfall")))))
                .enqueue(LLMResponse.ofText("Snow settles on the gate."))
                .enqueue(LLMResponse.ofText("{\"contradictions\": []}"));
        onEdt(() -> {
            gui.modeChoice.setSelectedItem("Say");
            gui.input.setText("Open up!");
            gui.send.doClick();
        });
        assertTrue(gui.transcript.getText().endsWith("> Say: Open up!\n\nSnow settles on the gate."));
        assertEquals("Ashfall", gui.usedLoreItems.get(0).getTitle());
        assertFalse(gui.usedLoreEmpty.isVisible(), "the empty note gives way to the list");
        assertEquals("", gui.input.getText());
        assertTrue(gui.undo.isEnabled() && gui.retry.isEnabled() && gui.edit.isEnabled());
    }

    @Test
    void aNewTurnIsRevealedGraduallyAndAClickShowsTheRest() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("Snow settles on the gate."))
                .enqueue(LLMResponse.ofText("{\"contradictions\": []}"));
        onEdt(() -> {
            gui.pacer.setCharsPerSecond(StoryGUI.CHARS_PER_SECOND);
            gui.input.setText("knock");
            gui.send.doClick();
            // Same event-thread task: the turn is saved but its text is not on screen yet.
            assertEquals(1, controller.getSession().getTurns().size());
            assertEquals("Mira waits at the gate.", gui.transcript.getText());
            assertTrue(gui.pacer.isRevealing());
            gui.pacer.skip();
            assertTrue(gui.transcript.getText().endsWith("> Do: knock\n\nSnow settles on the gate."));
        });
    }

    @Test
    void warningsAppearUnderTheTurnsNarration() throws Exception {
        onEdt(() -> {
            controller.newSession();
            controller.saveLoreEntry(new ca.yorku.storyteller.model.LoreEntry(null, "Orrin", "Orrin is dead.", List.of(), true));
        });
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("Orrin waves."))
                .enqueue(LLMResponse.ofText("{\"contradictions\": [{\"entry\": \"L1\", \"problem\": \"Orrin is dead.\"}]}"));
        onEdt(() -> {
            gui.input.setText("look");
            gui.send.doClick();
        });
        assertTrue(gui.transcript.getText().endsWith("Orrin waves.\n\n[Warning: Contradicts L1 \"Orrin\" (canon): Orrin is dead.]"));
    }

    @Test
    void acceptingTheSuggestedEndingShowsItAndClosesTheStory() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("u", "update_plot",
                        Map.of("action", "reached", "event_id", "E1")))))
                .enqueue(LLMResponse.ofText("A thief takes the letter."))
                .enqueue(LLMResponse.ofText("The thief vanishes into the snow."));
        gui.answers.add(true);
        onEdt(() -> {
            gui.input.setText("guard the letter");
            gui.send.doClick();
        });
        assertEquals(List.of("The story has reached its final turning point. End it now?"), gui.questions);
        assertTrue(gui.transcript.getText().endsWith("* * *\n\nThe thief vanishes into the snow.\n\nTHE END"));
        assertFalse(gui.send.isEnabled() || gui.input.isEnabled() || gui.undo.isEnabled() || gui.edit.isEnabled());
        assertEquals("Rewrite ending", gui.endStory.getText());
        assertTrue(gui.status.getText().contains("ended"));
    }

    @Test
    void endStoryEarlyAsksFirstAndDoesNothingIfDeclined() throws Exception {
        startFromTheForm();
        assertFalse(gui.endStory.isEnabled(), "no turn yet");
        llm.enqueue(LLMResponse.ofText("The gate opens."));
        onEdt(() -> {
            gui.input.setText("knock");
            gui.send.doClick();
        });
        assertTrue(gui.questions.isEmpty(), "the plan is not complete, so no suggestion");
        assertTrue(gui.endStory.isEnabled());
        onEdt(() -> gui.endStory.doClick());
        assertEquals("The story has not reached all of its turning points yet. End it here anyway?", gui.questions.get(0));
        assertFalse(controller.isEnded());

        llm.enqueue(LLMResponse.ofText("You go home."));
        gui.answers.add(true);
        onEdt(() -> gui.endStory.doClick());
        assertEquals("You go home.", controller.getSession().getEnding());
    }

    @Test
    void aFailedTurnKeepsTheInputInTheBox() throws Exception {
        startFromTheForm();
        llm.enqueueFailure(new LLMException("Could not reach Claude. Check your internet connection."));
        onEdt(() -> {
            gui.input.setText("climb the wall");
            gui.send.doClick();
        });
        assertEquals(List.of("Could not reach Claude. Check your internet connection."), gui.errors);
        assertEquals("climb the wall", gui.input.getText());
        assertTrue(controller.getSession().getTurns().isEmpty());
    }

    @Test
    void undoRetryAndEditButtons() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofText("Second."));
        onEdt(() -> {
            gui.input.setText("knock");
            gui.send.doClick();
            gui.retry.doClick();
        });
        assertTrue(gui.transcript.getText().endsWith("Second."));

        gui.narrationToType = "My own ending to the turn.";
        onEdt(() -> gui.edit.doClick());
        assertTrue(gui.transcript.getText().endsWith("My own ending to the turn."));

        gui.narrationToType = null;
        onEdt(() -> gui.edit.doClick());
        assertTrue(gui.transcript.getText().endsWith("My own ending to the turn."), "cancel changes nothing");

        onEdt(() -> gui.undo.doClick());
        assertTrue(gui.transcript.getText().endsWith("Second."));
        onEdt(() -> gui.undo.doClick());
        assertEquals("Mira waits at the gate.", gui.transcript.getText());
        assertFalse(gui.undo.isEnabled());
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void aFailedRetryPutsTheInputBack() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofRefusal());
        onEdt(() -> {
            gui.modeChoice.setSelectedItem("Story");
            gui.input.setText("The bells rang.");
            gui.send.doClick();
            gui.modeChoice.setSelectedItem("Do");
            gui.retry.doClick();
        });
        assertTrue(gui.errors.get(0).contains("declined"));
        assertEquals("The bells rang.", gui.input.getText());
        assertEquals("Story", gui.modeChoice.getSelectedItem());
        assertTrue(controller.getSession().getTurns().isEmpty());
    }

    @Test
    void proposalsAreListedWithACountAndCanBeApprovedOrRejected() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(
                        new LLMToolCall("a", "propose_lore", Map.of("title", "The Ember Guild", "content", "Smiths.")),
                        new LLMToolCall("b", "propose_lore", Map.of("title", "Grey Snow", "content", "It never melts.")))))
                .enqueue(LLMResponse.ofText("The forges glow."));
        onEdt(() -> {
            gui.input.setText("visit the forges");
            gui.send.doClick();
        });
        assertEquals(2, gui.proposalItems.size());
        assertTrue(gui.status.getText().contains("2 lore proposals waiting"));
        assertEquals("Proposals (2)", gui.sidebarTabs.getTitleAt(StoryGUI.PROPOSALS_TAB));
        assertTrue(gui.approve.isEnabled());

        onEdt(() -> {
            gui.proposalList.setSelectedIndex(0);
            gui.approve.doClick();
        });
        assertEquals("Smiths.", controller.getLoreEntry("L1").getContent());
        assertEquals(1, gui.proposalItems.size());

        onEdt(() -> {
            gui.reject.doClick();
            gui.proposalList.setSelectedIndex(0);
            gui.reject.doClick();
        });
        assertEquals(List.of("Select a proposal first."), gui.errors);
        assertEquals(0, gui.proposalItems.size());
        assertEquals(1, controller.searchLore("").size());
        assertEquals("Proposals", gui.sidebarTabs.getTitleAt(StoryGUI.PROPOSALS_TAB));
        assertFalse(gui.approve.isEnabled(), "nothing left to approve");
    }

    @Test
    void theSidebarHoldsLoreCharactersAndTheLoreUsedThisTurn() throws Exception {
        startFromTheForm();
        assertTrue(gui.isSidebarOpen(), "the sidebar starts open");
        assertEquals(List.of("Lore", "Characters", "Proposals"), List.of(gui.sidebarTabs.getTitleAt(0),
                gui.sidebarTabs.getTitleAt(1), gui.sidebarTabs.getTitleAt(2)));
        assertTrue(SwingUtilities.isDescendingFrom(gui.loreList, gui.sidebar));
        assertTrue(SwingUtilities.isDescendingFrom(gui.characterList, gui.sidebar));
        assertTrue(SwingUtilities.isDescendingFrom(gui.proposalList, gui.sidebar));
        assertTrue(SwingUtilities.isDescendingFrom(gui.usedLoreList, gui.sidebar), "lore used this turn lives in the sidebar");
        assertFalse(SwingUtilities.isDescendingFrom(gui.transcript, gui.sidebar));
        assertTrue(gui.usedLoreEmpty.isVisible(), "no turn yet, so a note instead of an empty list");
    }

    @Test
    void newSitsApartFromSaveAndDeleteInBothEditors() {
        assertEquals(gui.loreSave.getParent(), gui.loreDelete.getParent());
        assertFalse(gui.loreNew.getParent() == gui.loreSave.getParent(), "New is on its own, on the left");
        assertEquals(java.awt.BorderLayout.WEST, ((java.awt.BorderLayout) gui.loreNew.getParent().getParent().getLayout())
                .getConstraints(gui.loreNew.getParent()));
        assertEquals(gui.characterSave.getParent(), gui.characterDelete.getParent());
        assertFalse(gui.characterNew.getParent() == gui.characterSave.getParent());
        assertEquals("STORY RECAP", gui.recapHeading.getText());
        assertEquals("Relationships (one-way)", StoryGUI.RELATIONSHIPS_TITLE);
    }

    @Test
    void theSidebarCollapsesAndCountsWaitingProposalsWhileCollapsed() throws Exception {
        startFromTheForm();
        onEdt(() -> gui.sidebarToggle.doClick());
        assertFalse(gui.isSidebarOpen());
        assertEquals("\u00ab Lore & characters", gui.sidebarToggle.getText());
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(
                        new LLMToolCall("a", "propose_lore", Map.of("title", "Grey Snow", "content", "It never melts.")))))
                .enqueue(LLMResponse.ofText("Snow falls."));
        onEdt(() -> {
            gui.input.setText("look up");
            gui.send.doClick();
        });
        assertFalse(gui.isSidebarOpen(), "a turn does not reopen the sidebar");
        assertEquals("\u00ab Lore & characters (1)", gui.sidebarToggle.getText());
        onEdt(() -> gui.sidebarToggle.doClick());
        assertTrue(gui.isSidebarOpen());
        assertEquals("Hide lore & characters \u00bb", gui.sidebarToggle.getText());
    }

    @Test
    void openingALoreEntryBringsBackACollapsedSidebarOnTheLoreTab() throws Exception {
        onEdt(() -> {
            controller.newSession();
            controller.saveLoreEntry(new ca.yorku.storyteller.model.LoreEntry(null, "Ashfall", "A city.", List.of(), true));
            gui.sidebarTabs.setSelectedIndex(StoryGUI.CHARACTERS_TAB);
            gui.setSidebarOpen(false);
            gui.openLoreEntry("L1");
        });
        assertTrue(gui.isSidebarOpen());
        assertEquals(StoryGUI.LORE_TAB, gui.sidebarTabs.getSelectedIndex());
        assertEquals("Ashfall", gui.loreTitle.getText());
        assertEquals("L1", gui.loreList.getSelectedValue().getId());
    }
}

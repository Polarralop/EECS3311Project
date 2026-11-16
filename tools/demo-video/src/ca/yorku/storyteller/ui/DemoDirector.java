package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Drives the real Swing app for the Stage 2 demo videos V1 to V3, the way a person would: it moves the
 * mouse and clicks the app's own buttons, menus, tabs and list rows, and types into its fields. The app
 * runs unchanged, with the real ClaudeAdapter and Claude Sonnet 5.5; nothing here answers for the model.
 * The script reacts to what the model actually does (whether a proposal or a warning appeared, which
 * event is pending) and says so in the feature panel on the left.
 *
 * <p>Recording aid only, kept out of the Maven build. Usage: {@code DemoDirector V1|V2|V3|prep3 <log>}.
 * The save folder comes from STORYTELLER_SAVE_DIR, as for the app.
 */
public final class DemoDirector {

    /** The virtual screen (1536x864, scaled up to 1080p when recorded). */
    static final int SCREEN_W = java.awt.Toolkit.getDefaultToolkit().getScreenSize().width;
    static final int SCREEN_H = java.awt.Toolkit.getDefaultToolkit().getScreenSize().height;

    private final StoryController controller;
    private final StoryGUI gui;
    private final FeaturePanel panel;
    private final Robot robot;
    private final Path saveDir;
    private JFrame frame;
    private JFrame viewer;

    private DemoDirector(StoryController controller, StoryGUI gui, FeaturePanel panel, Path saveDir) throws AWTException {
        this.controller = controller;
        this.gui = gui;
        this.panel = panel;
        this.saveDir = saveDir;
        this.robot = new Robot();
        robot.setAutoDelay(0);
    }

    public static void main(String[] args) throws Exception {
        String video = args[0];
        Path saveDir = Path.of(System.getenv().getOrDefault("STORYTELLER_SAVE_DIR", "saves"));
        if (video.equals("prep3")) {
            prepareVideo3(StoryCLI.createController(), args[1]);
            System.exit(0);
        }
        long start = Long.parseLong(System.getenv().getOrDefault("RECORD_START_MS", "" + System.currentTimeMillis()));
        AtomicReference<DemoDirector> ref = new AtomicReference<>();
        StoryController controller = StoryCLI.createController();
        SwingUtilities.invokeAndWait(() -> {
            try {
                StoryTheme.install();
                FeaturePanel panel = new FeaturePanel(SCREEN_H, start, args[1]);
                StoryGUI gui = new StoryGUI(controller);
                controller.addObserver(gui);
                gui.open();
                ref.set(new DemoDirector(controller, gui, panel, saveDir));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        DemoDirector d = ref.get();
        d.placeWindow();
        try {
            switch (video) {
                case "V1" -> d.video1();
                case "V2" -> d.video2();
                case "V3" -> d.video3();
                default -> throw new IllegalArgumentException("Unknown video " + video);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            d.pause(1000);
            System.exit(2);
        }
        d.pause(1500);
        System.exit(0);
    }

    // ------------------------------------------------------------------ Video 1

    private void video1() {
        titleCard("V1 Setting up a story (GUI)",
                "The Swing GUI over the same StoryController the CLI uses. Claude Sonnet 5.5 is the storyteller. "
                        + "The API key comes from the environment and never appears on screen.");
        step("The start screen lists the saved stories, newest first, with when each was saved.");
        pause(4000);
        click(gui.openStory);
        awaitIdle();
        pause(1500);

        panel.feature("F02", "Lore Entry Management", "Deterministic",
                "Add, edit, search, pin and delete lore entries. Plain validation in the model, no AI. "
                        + "Pinned entries are canon that the storyteller must not contradict.");
        clickTab(StoryGUI.LORE_TAB);
        step("The Lore tab: L1 Ashfall and L2 The Ash Bell are pinned (canon).");
        pause(3500);
        step("Search \"bell\": only The Ash Bell is left.");
        type(gui.loreSearch, "bell");
        pause(2500);
        clear(gui.loreSearch);
        pause(1000);
        step("New entry: The red night, pinned. A later video tests the consistency check against it.");
        click(gui.loreNew);
        type(gui.loreTitle, "The red night");
        type(gui.loreContent, "Ashfall has no moon. At night the only light in the sky is the red glow of the volcano.");
        type(gui.loreTags, "sky, night");
        if (!onEdt(gui.lorePinned::isSelected)) {
            click(gui.lorePinned);
        }
        click(gui.loreSave);
        pause(2500);
        step("Edit L3 The Ember Guild: add the tag \"council\".");
        clickListItem(gui.loreList, e -> e.getTitle().equals("The Ember Guild"));
        pause(1200);
        appendText(gui.loreTags, ", council");
        click(gui.loreSave);
        pause(2000);
        step("Error path: a second entry titled Ashfall is rejected.");
        click(gui.loreNew);
        type(gui.loreTitle, "Ashfall");
        type(gui.loreContent, "A duplicate entry.");
        click(gui.loreSave);
        expectDialog("Cannot do that", "OK");
        step("Error path: a tag must be a single word.");
        replaceText(gui.loreTitle, "Test");
        replaceText(gui.loreTags, "two words");
        click(gui.loreSave);
        expectDialog("Cannot do that", "OK");
        step("Fix the tag, save, then delete the test entry.");
        replaceText(gui.loreTags, "test");
        click(gui.loreSave);
        pause(1500);
        click(gui.loreDelete);
        pause(2500);

        panel.feature("F03", "Character Management", "Deterministic",
                "Add and edit characters with traits, and one-way relationships between them. "
                        + "Validated in the model, no AI.");
        clickTab(StoryGUI.CHARACTERS_TAB);
        step("Mira's relationships to Tomas and Guildmaster Orrin.");
        clickListItem(gui.characterList, c -> c.getName().equals("Mira"));
        pause(3500);
        step("New character: Sela, a smuggler.");
        click(gui.characterNew);
        type(gui.characterName, "Sela");
        type(gui.characterDescription, "A smuggler who trades in stolen glowstone.");
        type(gui.characterTraits, "sly, generous");
        click(gui.characterSave);
        pause(2000);
        step("Relationship from Mira to Sela: friend.");
        clickListItem(gui.characterList, c -> c.getName().equals("Mira"));
        pause(1000);
        chooseCombo(gui.relationshipTarget, "Sela");
        type(gui.relationshipType, "friend");
        type(gui.relationshipNotes, "buys tools from her");
        click(gui.relationshipAdd);
        pause(3000);
        step("Error path: a second character named Mira is rejected.");
        click(gui.characterNew);
        type(gui.characterName, "Mira");
        click(gui.characterSave);
        expectDialog("Cannot do that", "OK");
        step("Edit Sela's description and save.");
        clickListItem(gui.characterList, c -> c.getName().equals("Sela"));
        pause(800);
        replaceText(gui.characterDescription, "A smuggler who trades in stolen glowstone and owes the guild money.");
        click(gui.characterSave);
        pause(2500);

        panel.feature("F01", "Scenario Setup", "Hybrid",
                "Claude drafts a scenario from a one-line idea (AI); the user can change every field, and "
                        + "validation is deterministic. The lore and characters prepared above are kept.");
        menu("Start story...");
        pause(1500);
        step("Idea, then Generate draft: Claude fills in the five fields.");
        type(gui.scenarioIdea, "Mira finds out the Ember Guild has been hiding tremors from the city.");
        click(gui.generate);
        awaitIdle();
        pause(4000);
        step("Change the title to The Silent Bell.");
        replaceText(gui.scenarioTitle, "The Silent Bell");
        pause(1000);
        step("Error path: a story needs a setting.");
        String setting = onEdt(gui.scenarioSetting::getText);
        clear(gui.scenarioSetting);
        click(gui.startButton);
        expectDialogAfterIdle("Cannot do that", "OK");
        type(gui.scenarioSetting, setting.isBlank() ? "Ashfall, a city built on the slopes of a volcano." : setting);
        step("Plot basis: kept hidden; it feeds the plan of pivotal events.");
        type(gui.plotBasis, "The guild's secret should come out by the end.");
        pause(1000);

        panel.feature("F05", "Pivotal Events", "Hybrid",
                "Starting the story makes Claude plan up to eight hidden turning points from the scenario and "
                        + "the plot basis (AI). The app stores them and tracks their status (deterministic). "
                        + "Neither front end ever shows them.");
        step("Start story: Claude plans the pivotal events, then the story view opens.");
        click(gui.startButton);
        awaitIdle();
        pause(3000);
        step("No plan anywhere in the story view or the sidebar.");
        pause(3500);
        step("Story > Save, then the save file is opened only to prove the plan exists.");
        menu("Save");
        expectDialog("Storyteller", "OK");
        showSaveFile("plot", "Save file, shown only to prove the hidden plan exists. The app never displays it.");
        step("plot.events: every event is PENDING.");
        pause(9000);
        closeViewer();
        step("The failure case (planning fails, so no story starts) is in the CLI video.");
        pause(4000);
        endCard();
    }

    // ------------------------------------------------------------------ Video 2

    private void video2() {
        titleCard("V2 Playing the story (GUI)",
                "The story set up in V1, opened from the start screen. Every turn below is a live call to "
                        + "Claude Sonnet 5.5.");
        step("Open The Silent Bell.");
        pause(3000);
        click(gui.openStory);
        awaitIdle();
        pause(2500);
        dismissRecap();

        panel.feature("F04", "Story Turn", "Hybrid",
                "The agent runs a tool-use loop: it looks lore and characters up through tools before it writes "
                        + "(AI). The three input modes, Do, Say and Story, are the Strategy pattern.");
        turn("Do", "I slip out of the forge before dawn and go to the old square to look at the Ash Bell.",
                "Do mode: an action for Mira.");
        step("\"Lore used this turn\" lists the entries the agent looked up. Double-click one to open it.");
        pause(2000);
        if (onEdt(() -> gui.usedLoreItems.size()) > 0) {
            doubleClickListRow(gui.usedLoreList, 0);
            pause(3000);
        }
        turn("Say", "Tomas, did you hear the mountain last night?", "Say mode: Mira speaks.");
        turn("Story", "Mira pressed her palm to the bell and felt it humming faintly, though no one had touched it.",
                "Story mode: the user writes a passage and the storyteller continues it.");
        step("Error path: an empty turn is rejected.");
        clear(gui.input);
        click(gui.send);
        expectDialog("Cannot do that", "OK");

        panel.feature("F07", "Lore Proposals", "Hybrid",
                "When the narration creates a lasting new fact, the agent proposes it through the propose_lore "
                        + "tool (AI). Only the user's approval adds it to the lore book (deterministic).");
        clickTab(StoryGUI.PROPOSALS_TAB);
        pause(1500);
        if (onEdt(() -> gui.proposalItems.size()) == 0) {
            step("No proposal yet, so the next turns name something new.");
            turn("Story", "Behind the bell tower Mira found a narrow stair down into the tunnels the old miners "
                    + "called the Hollows.", "Story mode: a new place.");
            if (onEdt(() -> gui.proposalItems.size()) == 0) {
                turn("Do", "I follow the tunnel until I find whoever left the chalk marks.", "Do mode.");
            }
            clickTab(StoryGUI.PROPOSALS_TAB);
        }
        int proposals = onEdt(() -> gui.proposalItems.size());
        if (proposals > 0) {
            step("The Proposals tab counts what is waiting. Select one and read it.");
            clickListRow(gui.proposalList, 0);
            pause(4000);
            String approved = onEdt(() -> gui.proposalList.getSelectedValue().getTitle());
            step("Approve: \"" + approved + "\" becomes a lore entry with a new id.");
            click(gui.approve);
            pause(1500);
            clickTab(StoryGUI.LORE_TAB);
            clickListItem(gui.loreList, e -> e.getTitle().equals(approved));
            pause(4000);
            clickTab(StoryGUI.PROPOSALS_TAB);
            if (onEdt(() -> gui.proposalItems.size()) > 0) {
                clickListRow(gui.proposalList, 0);
                step("Reject another: it leaves the list and never reaches the lore.");
                pause(3000);
                click(gui.reject);
                pause(3000);
            }
        } else {
            step("The agent proposed nothing in these turns; proposals only appear for lasting new facts.");
            pause(5000);
        }

        panel.feature("F08", "Consistency Check", "Hybrid",
                "After each turn a separate Claude call checks the narration against pinned lore and the entries "
                        + "the turn used (AI). A contradiction adds a warning; it never blocks the turn.");
        clickTab(StoryGUI.LORE_TAB);
        clickListItem(gui.loreList, e -> e.getTitle().equals("The red night"));
        step("Pinned canon from V1: Ashfall has no moon.");
        pause(4000);
        turn("Do", "I climb onto the forge roof at midnight and look out over the city.", "A night scene.");
        if (!lastTurnWarned()) {
            step("No warning: the narrator kept to the canon. Now the user writes a contradiction.");
            pause(3500);
            turn("Story", "Mira watched the full moon rise over the volcano.", "Story mode: a full moon.");
        }
        step(lastTurnWarned()
                ? "The [Warning: ...] line names the pinned entry. The turn still went through."
                : "The check ran and found no contradiction, so no warning was added.");
        pause(6000);

        panel.feature("F06", "Plot Steering", "AI",
                "Each turn the planner gives the agent hidden guidance toward the next pending event. The agent "
                        + "marks events reached, or revises the plan, through the update_plot tool.");
        menu("Save");
        expectDialog("Storyteller", "OK");
        PivotalEvent next = nextPending();
        showSaveFile("plot", "Save file, shown only to prove the feature. The app never displays the plan.");
        step(next == null ? "plot.events in the save file." : "The next pending event is " + next.getId() + ".");
        pause(8000);
        closeViewer();
        if (next != null) {
            String id = next.getId();
            step("Story mode: a passage that makes " + id + " happen.");
            turn("Story", passageFor(next), "Story mode: the user makes the event happen.");
            menu("Save");
            expectDialog("Storyteller", "OK");
            showSaveFile("plot", "Save file after the turn.");
            EventStatus now = statusOf(id);
            step(id + " is now " + now + (now == EventStatus.REACHED ? ": the agent marked it reached." : "."));
            pause(8000);
            closeViewer();
        }
        step("When the story leaves the plan behind, the agent revises it: old events become DROPPED. "
                + "Undo puts the plan back.");
        pause(5000);

        panel.feature("F10", "Undo, Retry, Edit Turn", "Hybrid",
                "Built with the Command pattern. Undo and Edit are deterministic; Retry calls Claude again, "
                        + "which makes the feature hybrid.");
        step("Undo: the last turn disappears from the transcript.");
        click(gui.undo);
        pause(3500);
        step("Retry: the latest turn is written again from the same input and mode.");
        click(gui.retry);
        awaitIdle();
        finishReveal();
        pause(3000);
        step("Edit: replace the narration with a sentence of the user's own.");
        click(gui.edit);
        JDialog edit = awaitDialog("Edit the last turn", 10_000);
        JTextArea area = find(edit, JTextArea.class);
        replaceText(area, "Mira let go of the bell. Whatever it had been trying to tell her, it would have to wait.");
        pause(1000);
        clickButton(edit, "OK");
        pause(3500);
        step("Undo the edit: the original narration comes back.");
        click(gui.undo);
        pause(3500);
        step("Error path: an empty narration is rejected.");
        click(gui.edit);
        edit = awaitDialog("Edit the last turn", 10_000);
        clear(find(edit, JTextArea.class));
        pause(800);
        clickButton(edit, "OK");
        expectDialog("Cannot do that", "OK");
        menu("Save");
        expectDialog("Storyteller", "OK");
        endCard();
    }

    // ------------------------------------------------------------------ Video 3

    private void video3() {
        titleCard("V3 Memory and the ending (GUI)",
                "The same story, played on off camera to 10+ turns with one turning point left.");
        panel.feature("F11", "Save and Load Session", "Deterministic",
                "The story is written to a JSON file and read back. Undo history is deliberately not saved, "
                        + "so it starts empty after a load.");
        step("The start screen lists The Silent Bell with when it was saved. Open it.");
        pause(4000);
        click(gui.openStory);
        awaitIdle();
        pause(1500);
        step("Undo is greyed out: the history starts empty after a load.");
        pause(4500);
        step("Story > Load... shows the same list as a dialog. Cancel it.");
        menu("Load...");
        expectDialog("Load a story", "Cancel");

        panel.feature("F09", "Story Memory Summarization", "AI",
                "Once a story is longer than eight turns, Claude folds older turns into a running summary so each "
                        + "turn's context stays short. On load, Claude writes the recap shown above the transcript.");
        step("The Story recap card was written by Claude when the story opened.");
        pause(9000);
        showSaveFile("summary", "Save file: the running summary and the turn it covers up to.");
        step("summary and summarizedUpTo in the save file.");
        pause(9000);
        closeViewer();
        click(gui.hideRecap);
        pause(1500);
        step("The recap failure case (the story still loads, with a note) is in the CLI video.");
        pause(3500);

        panel.feature("F12", "Ending Generation", "AI",
                "The storyteller suggests an ending once every pivotal event has happened, or the user can ask "
                        + "for one at any time. Claude writes it; it can be rewritten.");
        step("End story early: an event is still pending, so it asks first. Answer No.");
        click(gui.endStory);
        expectDialog("Storyteller", "No");
        PivotalEvent last = nextPending();
        if (last != null) {
            step("Story mode: a passage that makes the last turning point happen.");
            turn("Story", passageFor(last), "Story mode.");
        }
        JDialog offer = awaitDialog("Storyteller", 5_000);
        if (offer != null) {
            step("The storyteller suggests ending the story. Yes.");
            pause(3500);
            clickButton(offer, "Yes");
        } else {
            step("No suggestion this time, so End story, Yes.");
            click(gui.endStory);
            expectDialog("Storyteller", "Yes");
        }
        awaitIdle();
        finishReveal();
        step("The ending closes with THE END. Turn controls are disabled; the button reads Rewrite ending.");
        pause(7000);
        step("Rewrite ending: confirm, and a new ending replaces the old one.");
        click(gui.endStory);
        expectDialog("Storyteller", "Yes");
        awaitIdle();
        finishReveal();
        pause(5000);
        menu("Save");
        expectDialog("Storyteller", "OK");
        endCard();
    }

    /** Off camera: plays the V2 story on to 10+ turns with exactly one pending event left. */
    private static void prepareVideo3(StoryController c, String logPath) throws Exception {
        List<String> ids = c.listSessions();
        c.loadSession(ids.get(0));
        StorySession s = c.getSession();
        int guard = 0;
        while (guard++ < 20) {
            List<PivotalEvent> pending = s.getPlot().getEvents().stream()
                    .filter(e -> e.getStatus() == EventStatus.PENDING).toList();
            if (s.getTurns().size() >= 10 && pending.size() == 1) {
                break;
            }
            if (pending.size() > 1) {
                c.submitTurn(passageFor(pending.get(0)), "Story");
            } else {
                c.submitTurn("Mira walks the city at dusk, listening to what people say about the mountain.", "Do");
            }
            Files.writeString(Path.of(logPath), "turn " + s.getTurns().size() + ", pending "
                    + s.getPlot().getEvents().stream().filter(e -> e.getStatus() == EventStatus.PENDING).count() + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        c.saveSession();
    }

    private static String passageFor(PivotalEvent e) {
        String d = e.getDescription().strip();
        return d.endsWith(".") ? d : d + ".";
    }

    // ------------------------------------------------------------------ story helpers

    private void turn(String mode, String text, String note) {
        step(note + "  Send.");
        chooseCombo(gui.modeChoice, mode);
        type(gui.input, text);
        pause(700);
        click(gui.send);
        if (!awaitIdle()) {
            return;
        }
        pause(1500);
        clickAt(center(gui.transcript));
        finishReveal();
        pause(3500);
    }

    private boolean lastTurnWarned() {
        return onEdt(() -> controller.getSession().lastTurn().map(t -> !t.getWarnings().isEmpty()).orElse(false));
    }

    private PivotalEvent nextPending() {
        return onEdt(() -> controller.getSession().getPlot().nextPending().orElse(null));
    }

    private EventStatus statusOf(String id) {
        return onEdt(() -> controller.getSession().getPlot().getEvents().stream()
                .filter(e -> e.getId().equals(id)).map(PivotalEvent::getStatus).findFirst().orElse(null));
    }

    private void dismissRecap() {
        if (onEdt(gui.recapPanel::isVisible)) {
            pause(3000);
            click(gui.hideRecap);
            pause(800);
        }
    }

    private void finishReveal() {
        waitUntil(() -> !gui.pacer.isRevealing(), 60_000);
    }

    // ------------------------------------------------------------------ panel and cards

    private void titleCard(String title, String about) {
        panel.video(title);
        panel.feature("", title.substring(3), "", about);
        pause(1500);
    }

    private void endCard() {
        panel.feature("", "End of video", "", "");
        pause(2500);
    }

    private void step(String text) {
        panel.step(text);
    }

    /** A plain window over the app showing part of the save file; not part of the app. */
    private void showSaveFile(String key, String caption) {
        String id = onEdt(() -> controller.getSession().getId());
        Path file = saveDir.resolve(id + ".json");
        String text;
        try {
            ObjectMapper m = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            JsonNode root = m.readTree(file.toFile());
            StringBuilder sb = new StringBuilder();
            if (key.equals("summary")) {
                sb.append("\"summarizedUpTo\": ").append(root.path("summarizedUpTo")).append("\n\n\"summary\":\n");
                sb.append(root.path("summary").asText());
            } else {
                sb.append("\"plot\": ").append(m.writeValueAsString(root.path("plot")));
            }
            text = sb.toString();
        } catch (Exception e) {
            text = "Could not read " + file + ": " + e.getMessage();
        }
        String shown = text;
        SwingUtilities.invokeLater(() -> {
            viewer = new JFrame();
            viewer.setUndecorated(true);
            viewer.setFocusableWindowState(false);
            JTextArea a = new JTextArea(shown);
            a.setFont(new Font("DejaVu Sans Mono", Font.PLAIN, 14));
            a.setBackground(new Color(0x1B1B1F));
            a.setForeground(new Color(0xE8E8E8));
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            a.setEditable(false);
            a.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 18, 14, 18));
            JLabel head = new JLabel("  " + saveDir.resolve(id + ".json") + "   |   " + caption);
            head.setOpaque(true);
            head.setBackground(new Color(0x2D2D33));
            head.setForeground(new Color(0xFFE08A));
            head.setFont(new Font("DejaVu Sans", Font.BOLD, 13));
            head.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 6, 10, 6));
            viewer.getContentPane().add(head, java.awt.BorderLayout.NORTH);
            viewer.getContentPane().add(new JScrollPane(a));
            viewer.setBounds(FeaturePanel.WIDTH + 70, 90, SCREEN_W - FeaturePanel.WIDTH - 140, SCREEN_H - 180);
            viewer.setVisible(true);
        });
        pause(800);
    }

    private void closeViewer() {
        SwingUtilities.invokeLater(() -> {
            if (viewer != null) {
                viewer.dispose();
            }
        });
        pause(800);
    }

    // ------------------------------------------------------------------ dialogs

    /** Waits for a dialog with this title, lets the viewer read it, then presses the named button. */
    private void expectDialog(String title, String button) {
        JDialog d = awaitDialog(title, 15_000);
        if (d == null) {
            step("(No \"" + title + "\" dialog appeared.)");
            return;
        }
        pause(3200);
        clickButton(d, button);
        pause(1000);
    }

    private void expectDialogAfterIdle(String title, String button) {
        expectDialog(title, button);
        awaitIdle();
    }

    private final List<Window> handled = new ArrayList<>();

    private JDialog awaitDialog(String title, long timeoutMs) {
        AtomicReference<JDialog> found = new AtomicReference<>();
        waitUntil(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())
                        && !"Start a story".equals(d.getTitle())) {
                    found.set(d);
                    return true;
                }
            }
            return false;
        }, timeoutMs);
        if (found.get() != null) {
            pause(400);
        }
        return found.get();
    }

    private void clickButton(Container root, String text) {
        AbstractButton b = onEdt(() -> findButton(root, text));
        if (b == null) {
            throw new IllegalStateException("No button " + text);
        }
        click(b);
    }

    private static AbstractButton findButton(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing()) {
                return b;
            }
            if (child instanceof Container inner) {
                AbstractButton b = findButton(inner, text);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    private <T extends Component> T find(Container c, Class<T> type) {
        return onEdt(() -> findIn(c, type));
    }

    private static <T extends Component> T findIn(Container c, Class<T> type) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container inner) {
                T t = findIn(inner, type);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ waiting

    /** Waits until no model call is running. False if an error dialog came up instead (it is dismissed). */
    private boolean awaitIdle() {
        pause(400);
        waitUntil(() -> !isBusy(), 300_000);
        pause(300);
        JDialog error = awaitDialog("Cannot do that", 600);
        if (error != null) {
            step("Error from the app: " + dialogText(error));
            pause(3500);
            clickButton(error, "OK");
            pause(800);
            return false;
        }
        return true;
    }

    private String dialogText(JDialog d) {
        return onEdt(() -> {
            StringBuilder sb = new StringBuilder();
            collectLabels(d.getContentPane(), sb);
            return sb.toString().strip();
        });
    }

    private static void collectLabels(Container c, StringBuilder sb) {
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel l && l.getText() != null) {
                sb.append(l.getText()).append(' ');
            }
            if (child instanceof Container inner) {
                collectLabels(inner, sb);
            }
        }
    }

    private boolean isBusy() {
        try {
            Field f = StoryGUI.class.getDeclaredField("busy");
            f.setAccessible(true);
            return f.getBoolean(gui);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            pause(150);
        }
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T onEdt(Callable<T> work) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return work.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(work.call());
                } catch (Exception e) {
                    error.set(e);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (error.get() != null) {
            throw new IllegalStateException(error.get());
        }
        return result.get();
    }

    // ------------------------------------------------------------------ mouse and keyboard

    private void placeWindow() {
        waitUntil(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame f && "AI Interactive Storyteller".equals(f.getTitle()) && f.isShowing()) {
                    frame = f;
                    return true;
                }
            }
            return false;
        }, 10_000);
        SwingUtilities.invokeLater(() -> {
            frame.setBounds(FeaturePanel.WIDTH, 0, SCREEN_W - FeaturePanel.WIDTH, SCREEN_H);
            frame.validate();
        });
        pause(1200);
        robot.mouseMove(FeaturePanel.WIDTH + 700, 500);
    }

    private Point center(Component c) {
        return onEdt(() -> {
            Point p = c.getLocationOnScreen();
            Rectangle visible = c instanceof javax.swing.JComponent j ? j.getVisibleRect() : new Rectangle(c.getSize());
            return new Point(p.x + visible.x + visible.width / 2, p.y + visible.y + Math.min(visible.height / 2, 30));
        });
    }

    private void click(Component c) {
        clickAt(center(c));
    }

    private void clickAt(Point target) {
        glide(target);
        pause(250);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        pause(60);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        pause(350);
    }

    private void glide(Point target) {
        Point from = java.awt.MouseInfo.getPointerInfo().getLocation();
        int steps = 18;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            robot.mouseMove((int) (from.x + (target.x - from.x) * e), (int) (from.y + (target.y - from.y) * e));
            pause(16);
        }
    }

    private void doubleClickListRow(JList<?> list, int index) {
        Point p = rowPoint(list, index);
        glide(p);
        pause(250);
        for (int i = 0; i < 2; i++) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            pause(80);
        }
        pause(400);
    }

    private void clickListRow(JList<?> list, int index) {
        onEdt(() -> {
            list.ensureIndexIsVisible(index);
            return null;
        });
        clickAt(rowPoint(list, index));
    }

    private <T> void clickListItem(JList<T> list, java.util.function.Predicate<T> match) {
        int index = onEdt(() -> {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (match.test(list.getModel().getElementAt(i))) {
                    return i;
                }
            }
            return -1;
        });
        if (index < 0) {
            step("(Item not found in the list.)");
            return;
        }
        clickListRow(list, index);
    }

    private Point rowPoint(JList<?> list, int index) {
        return onEdt(() -> {
            Rectangle r = list.getCellBounds(index, index);
            Point p = list.getLocationOnScreen();
            return new Point(p.x + Math.min(r.x + 120, r.x + r.width / 2), p.y + r.y + r.height / 2);
        });
    }

    private void clickTab(int index) {
        Point p = onEdt(() -> {
            Rectangle r = gui.sidebarTabs.getBoundsAt(index);
            Point o = gui.sidebarTabs.getLocationOnScreen();
            return new Point(o.x + r.x + r.width / 2, o.y + r.y + r.height / 2);
        });
        clickAt(p);
        pause(600);
    }

    private void chooseCombo(JComboBox<String> combo, String item) {
        if (item.equals(onEdt(combo::getSelectedItem))) {
            return;
        }
        click(combo);
        pause(700);
        onEdt(() -> {
            combo.setSelectedItem(item);
            combo.hidePopup();
            return null;
        });
        pause(500);
    }

    /** Opens the Story menu and clicks one of its items. */
    private void menu(String item) {
        JMenu story = onEdt(() -> frame.getJMenuBar().getMenu(0));
        click(story);
        pause(600);
        JMenuItem target = onEdt(() -> {
            for (Component c : story.getMenuComponents()) {
                if (c instanceof JMenuItem m && item.equals(m.getText())) {
                    return m;
                }
            }
            return null;
        });
        if (target == null) {
            throw new IllegalStateException("No menu item " + item);
        }
        click(target);
        pause(500);
    }

    /** Clicks into a field and types the text a character at a time. */
    private void type(JTextComponent field, String text) {
        click(field);
        onEdt(() -> {
            field.setText("");
            return null;
        });
        typeChars(field, text);
    }

    private void appendText(JTextComponent field, String text) {
        click(field);
        onEdt(() -> {
            field.setCaretPosition(field.getDocument().getLength());
            return null;
        });
        typeChars(field, text);
    }

    private void typeChars(JTextComponent field, String text) {
        long perChar = text.length() > 60 ? 18 : 40;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            onEdt(() -> {
                field.replaceSelection(ch);
                return null;
            });
            pause(perChar);
        }
        pause(300);
    }

    private void replaceText(JTextComponent field, String text) {
        click(field);
        onEdt(() -> {
            field.selectAll();
            return null;
        });
        pause(400);
        onEdt(() -> {
            field.setText("");
            return null;
        });
        typeChars(field, text);
    }

    private void clear(JTextComponent field) {
        click(field);
        onEdt(() -> {
            field.selectAll();
            return null;
        });
        pause(400);
        onEdt(() -> {
            field.setText("");
            return null;
        });
        pause(400);
    }
}

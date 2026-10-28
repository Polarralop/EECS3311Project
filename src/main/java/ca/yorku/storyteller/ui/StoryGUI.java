package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.LoreProposal;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Swing front end. Displays session state and forwards user actions to the controller; it holds no
 * story logic. Every list is re-read from the session in {@link #onStoryChanged}.
 *
 * <p>The panels are built in the constructor and the window only in {@link #open()}, so the panels
 * can be exercised in tests without a display. Model calls (starting a story, a turn, a retry) run on a
 * background thread through {@link #runInBackground}, so the window stays responsive while Claude writes.
 * The hidden pivotal events are never read here.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (View), Observer (Concrete observer).
 */
public class StoryGUI implements StoryObserver {

    private final StoryController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JMenuBar menuBar = new JMenuBar();
    final JLabel status = new JLabel("No story open. Use Story > New or Story > Load.");

    private final JTabbedPane tabs = new JTabbedPane();

    // Story tab (F04, F10)
    final JLabel storyTitle = new JLabel();
    final JTextArea transcript = new JTextArea();
    final DefaultListModel<LoreEntry> usedLoreItems = new DefaultListModel<>();
    final JList<LoreEntry> usedLoreList = new JList<>(usedLoreItems);
    final JComboBox<String> modeChoice;
    final JTextArea input = new JTextArea(3, 40);
    final JButton send = new JButton("Send");
    final JButton undo = new JButton("Undo");
    final JButton retry = new JButton("Retry");
    final JButton edit = new JButton("Edit");

    // Start-a-story form (F01, F05)
    final JTextField scenarioIdea = new JTextField();
    final JButton generate = new JButton("Generate draft");
    final JTextField scenarioTitle = new JTextField();
    final JTextArea scenarioPremise = new JTextArea(2, 30);
    final JTextField scenarioTone = new JTextField();
    final JTextArea scenarioSetting = new JTextArea(2, 30);
    final JTextArea scenarioOpening = new JTextArea(2, 30);
    final JTextArea plotBasis = new JTextArea(2, 30);
    final JButton startButton = new JButton("Start story");
    final JPanel startForm;
    private JDialog startDialog;

    // Lore proposals (F07), in the Lore tab
    final DefaultListModel<LoreProposal> proposalItems = new DefaultListModel<>();
    final JList<LoreProposal> proposalList = new JList<>(proposalItems);
    final JTextArea proposalContent = new JTextArea(3, 30);
    final JButton approve = new JButton("Approve");
    final JButton reject = new JButton("Reject");
    private final JPanel proposalPanel = new JPanel(new BorderLayout(4, 4));

    /** True while a model call runs; the controls that would start another one are disabled. */
    private boolean busy;

    // Lore tab (F02)
    final JTextField loreSearch = new JTextField();
    final DefaultListModel<LoreEntry> loreItems = new DefaultListModel<>();
    final JList<LoreEntry> loreList = new JList<>(loreItems);
    final JTextField loreTitle = new JTextField();
    final JTextArea loreContent = new JTextArea(6, 30);
    final JTextField loreTags = new JTextField();
    final JCheckBox lorePinned = new JCheckBox("Pinned (canon)");
    final JButton loreNew = new JButton("New entry");
    final JButton loreSave = new JButton("Save entry");
    final JButton loreDelete = new JButton("Delete entry");
    private String editingLoreId;

    // Characters tab (F03)
    final DefaultListModel<Character> characterItems = new DefaultListModel<>();
    final JList<Character> characterList = new JList<>(characterItems);
    final JTextField characterName = new JTextField();
    final JTextArea characterDescription = new JTextArea(4, 30);
    final JTextField characterTraits = new JTextField();
    final DefaultListModel<Relationship> relationshipItems = new DefaultListModel<>();
    final JList<Relationship> relationshipList = new JList<>(relationshipItems);
    final JComboBox<String> relationshipTarget = new JComboBox<>();
    final JTextField relationshipType = new JTextField(10);
    final JTextField relationshipNotes = new JTextField(14);
    final JButton characterNew = new JButton("New character");
    final JButton characterSave = new JButton("Save character");
    final JButton characterDelete = new JButton("Delete character");
    final JButton relationshipAdd = new JButton("Add");
    final JButton relationshipRemove = new JButton("Remove selected");
    private String editingCharacterName;

    /** True while lists are being refilled, so selection events from the refill are ignored. */
    private boolean refreshing;

    public StoryGUI(StoryController controller) {
        this.controller = controller;
        modeChoice = new JComboBox<>(controller.modeNames().toArray(new String[0]));
        startForm = buildStartForm();
        buildMenu();
        tabs.addTab("Story", buildStoryTab());
        tabs.addTab("Lore", buildLoreTab());
        tabs.addTab("Characters", buildCharacterTab());
        root.add(tabs, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        root.add(status, BorderLayout.SOUTH);
        refreshStoryControls();
    }

    /** Shows the window. */
    public void open() {
        JFrame frame = new JFrame("AI Interactive Storyteller");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar);
        frame.setContentPane(root);
        frame.setSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(root, message, "Cannot do that", JOptionPane.WARNING_MESSAGE);
    }

    protected void showMessage(String message) {
        JOptionPane.showMessageDialog(root, message, "Storyteller", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Asks for the replacement narration in an editable box; null if the user cancels. */
    protected String askNarration(String current) {
        JTextArea area = new JTextArea(current, 12, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        int answer = JOptionPane.showConfirmDialog(root, new JScrollPane(area), "Edit the last turn",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? area.getText() : null;
    }

    /** Shows the start-a-story form in its own window. */
    protected void openStartForm() {
        if (startDialog == null) {
            startDialog = new JDialog(SwingUtilities.getWindowAncestor(root), "Start a story");
            startDialog.setContentPane(startForm);
            startDialog.pack();
            startDialog.setLocationRelativeTo(root);
        }
        startDialog.setVisible(true);
    }

    protected void closeStartForm() {
        if (startDialog != null) {
            startDialog.setVisible(false);
        }
    }

    /**
     * Runs a model call off the event thread, with the controls disabled and a busy message in the
     * status bar, then hands the result (or the error) back on the event thread.
     */
    protected <T> void runInBackground(String busyText, Supplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
        setBusy(true, busyText);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                setBusy(false, null);
                try {
                    T result = get();
                    act(() -> onSuccess.accept(result));
                } catch (ExecutionException e) {
                    failed(e.getCause(), onFailure);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /** What {@link #runInBackground} does once the work is over, run on the calling thread (used by tests). */
    final <T> void completeNow(Supplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
        T result;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            failed(e, onFailure);
            return;
        }
        act(() -> onSuccess.accept(result));
    }

    private void failed(Throwable error, Runnable onFailure) {
        onFailure.run();
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                || error instanceof UncheckedIOException || error instanceof LLMException) {
            showError(error.getMessage());
        } else {
            showError("Something went wrong: " + error);
        }
    }

    private void setBusy(boolean nowBusy, String busyText) {
        busy = nowBusy;
        if (busyText != null) {
            status.setText(busyText);
        }
        generate.setEnabled(!nowBusy);
        startButton.setEnabled(!nowBusy);
        refreshStoryControls();
        if (!nowBusy && controller.getSession() != null) {
            onStoryChanged(controller.getSession());
        }
    }

    /** Asks the user to pick a saved story; null if cancelled. */
    protected String chooseSession(List<String> ids) {
        Object choice = JOptionPane.showInputDialog(root, "Saved stories (newest first):", "Load a story",
                JOptionPane.PLAIN_MESSAGE, null, ids.toArray(), ids.get(0));
        return (String) choice;
    }

    /** Observer: re-reads the session into every list and the status bar. */
    @Override
    public void onStoryChanged(StorySession session) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onStoryChanged(session));
            return;
        }
        refreshing = true;
        try {
            int waiting = session.getPendingProposals().size();
            if (!busy) {
                status.setText("Story " + session.getId() + ": " + session.getLoreBook().size() + " lore entries, "
                        + session.getCharacters().size() + " characters, " + session.getTurns().size() + " turns"
                        + (waiting == 0 ? "" : ", " + waiting + " lore proposal" + (waiting == 1 ? "" : "s") + " waiting"));
            }
            refreshStory(session);
            refreshLore();
            refreshProposals(session);
            refreshCharacters(session);
        } finally {
            refreshing = false;
        }
    }

    // ---- menu: new, save, load (F11) ----

    private void buildMenu() {
        JMenu story = new JMenu("Story");
        story.add(menuItem("New (prepare lore and characters)", () -> controller.newSession()));
        story.add(menuItem("Start story...", this::openStartForm));
        story.add(menuItem("Save", this::save));
        story.add(menuItem("Load...", this::load));
        story.addSeparator();
        story.add(menuItem("Quit", () -> System.exit(0)));
        menuBar.add(story);
    }

    void save() {
        controller.saveSession();
        showMessage("Saved as " + controller.getSession().getId() + ".");
    }

    void load() {
        List<String> ids = controller.listSessions();
        if (ids.isEmpty()) {
            showError("There are no saved stories yet.");
            return;
        }
        String id = chooseSession(ids);
        if (id != null) {
            showMessage(controller.loadSession(id));
        }
    }

    // ---- story tab: turns, undo, retry, edit (F04, F10) ----

    private Component buildStoryTab() {
        storyTitle.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        usedLoreList.setVisibleRowCount(3);
        usedLoreList.setToolTipText("Double-click an entry to open it in the Lore tab.");
        usedLoreList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && usedLoreList.getSelectedValue() != null) {
                    openLoreEntry(usedLoreList.getSelectedValue().getId());
                }
            }
        });
        JPanel used = new JPanel(new BorderLayout());
        used.setBorder(BorderFactory.createTitledBorder("Lore used in the last turn"));
        used.add(new JScrollPane(usedLoreList), BorderLayout.CENTER);

        JPanel lastTurn = new JPanel(new BorderLayout());
        lastTurn.add(used, BorderLayout.CENTER);
        lastTurn.add(buttons(undo, retry, edit), BorderLayout.EAST);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        JPanel inputRow = new JPanel(new BorderLayout(4, 4));
        inputRow.add(modeChoice, BorderLayout.WEST);
        inputRow.add(new JScrollPane(input), BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(lastTurn, BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.SOUTH);

        send.addActionListener(e -> act(this::submitInput));
        undo.addActionListener(e -> act(this::undoTurn));
        retry.addActionListener(e -> act(this::retryTurn));
        edit.addActionListener(e -> act(this::editTurn));

        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(storyTitle, BorderLayout.NORTH);
        p.add(new JScrollPane(transcript), BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void refreshStory(StorySession session) {
        Scenario sc = session.getScenario();
        storyTitle.setText(sc == null ? "Story not started yet. Use Story > Start story..." : sc.getTitle());
        StringBuilder sb = new StringBuilder();
        if (sc != null && !sc.getOpeningSituation().isEmpty()) {
            sb.append(sc.getOpeningSituation()).append("\n\n");
        }
        for (Turn t : session.getTurns()) {
            sb.append("> ").append(t.getModeName()).append(": ").append(t.getUserInput()).append("\n\n")
                    .append(t.getNarration()).append("\n\n");
        }
        transcript.setText(sb.toString().strip());
        transcript.setCaretPosition(transcript.getDocument().getLength());
        usedLoreItems.clear();
        session.lastTurn().ifPresent(t -> {
            for (String id : t.getUsedLoreIds()) {
                controller.searchLore("").stream().filter(e -> e.getId().equals(id)).findFirst()
                        .ifPresent(usedLoreItems::addElement);
            }
        });
        refreshStoryControls();
    }

    /** Turn controls are usable only for a started story; undo, retry and edit only when they apply. */
    private void refreshStoryControls() {
        boolean started = controller.isStarted();
        boolean hasTurn = started && controller.getSession().lastTurn().isPresent();
        input.setEnabled(started && !busy);
        modeChoice.setEnabled(started && !busy);
        send.setEnabled(started && !busy);
        undo.setEnabled(controller.canUndo() && !busy);
        retry.setEnabled(controller.canUndo() && !busy);
        edit.setEnabled(hasTurn && !busy);
    }

    void submitInput() {
        String text = input.getText();
        String mode = (String) modeChoice.getSelectedItem();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Type something for the turn first.");
        }
        runInBackground("The storyteller is writing...", () -> controller.submitTurn(text, mode),
                turn -> input.setText(""), () -> { });
    }

    void undoTurn() {
        controller.undoTurn();
    }

    void retryTurn() {
        Turn last = controller.getSession().lastTurn().orElseThrow(() -> new IllegalStateException("There is no turn to retry."));
        runInBackground("The storyteller is rewriting the last turn...", controller::retryTurn, turn -> { }, () -> {
            // The turn stays undone: give the user their input back so it can be resent.
            input.setText(last.getUserInput());
            modeChoice.setSelectedItem(last.getModeName());
        });
    }

    void editTurn() {
        Turn last = controller.getSession().lastTurn().orElseThrow(() -> new IllegalStateException("There is no turn to edit."));
        String text = askNarration(last.getNarration());
        if (text != null) {
            controller.editTurn(text);
        }
    }

    private void openLoreEntry(String id) {
        tabs.setSelectedIndex(1);
        loreSearch.setText("");
        editingLoreId = id;
        refreshLore();
        controller.searchLore("").stream().filter(e -> e.getId().equals(id)).findFirst().ifPresent(this::showLoreEntry);
    }

    // ---- start-a-story form (F01, F05) ----

    private JPanel buildStartForm() {
        for (JTextArea a : List.of(scenarioPremise, scenarioSetting, scenarioOpening, plotBasis)) {
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
        }
        JPanel idea = new JPanel(new BorderLayout(4, 4));
        idea.setBorder(BorderFactory.createTitledBorder("Have a scenario drafted from an idea (optional)"));
        idea.add(scenarioIdea, BorderLayout.CENTER);
        idea.add(generate, BorderLayout.EAST);
        JPanel fields = form(
                "Title (made from the premise if empty)", scenarioTitle,
                "Premise *", new JScrollPane(scenarioPremise),
                "Tone", scenarioTone,
                "Setting *", new JScrollPane(scenarioSetting),
                "Opening situation", new JScrollPane(scenarioOpening),
                "Plot basis (optional, kept hidden)", new JScrollPane(plotBasis));
        generate.addActionListener(e -> act(this::generateDraft));
        startButton.addActionListener(e -> act(this::startStory));
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(idea, BorderLayout.NORTH);
        p.add(fields, BorderLayout.CENTER);
        p.add(buttons(startButton), BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(620, 520));
        return p;
    }

    void generateDraft() {
        String idea = scenarioIdea.getText();
        runInBackground("Drafting a scenario...", () -> controller.generateScenario(idea), draft -> {
            scenarioTitle.setText(draft.getTitle());
            scenarioPremise.setText(draft.getPremise());
            scenarioTone.setText(draft.getTone());
            scenarioSetting.setText(draft.getSetting());
            scenarioOpening.setText(draft.getOpeningSituation());
        }, () -> { });
    }

    void startStory() {
        Scenario scenario = new Scenario(scenarioTitle.getText(), scenarioPremise.getText(), scenarioTone.getText(),
                scenarioSetting.getText(), scenarioOpening.getText());
        String basis = plotBasis.getText();
        runInBackground("Preparing the story...", () -> {
            controller.startStory(scenario, basis);
            return controller.getSession();
        }, started -> {
            closeStartForm();
            for (JTextField f : List.of(scenarioIdea, scenarioTitle, scenarioTone)) {
                f.setText("");
            }
            for (JTextArea a : List.of(scenarioPremise, scenarioSetting, scenarioOpening, plotBasis)) {
                a.setText("");
            }
            tabs.setSelectedIndex(0);
        }, () -> { });
    }

    // ---- lore proposals (F07) ----

    private Component buildProposalPanel() {
        proposalList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        proposalList.setVisibleRowCount(4);
        proposalList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                LoreProposal p = (LoreProposal) value;
                return super.getListCellRendererComponent(list, p.getId() + "  " + p.getTitle(), index, selected, focus);
            }
        });
        proposalList.addListSelectionListener(e -> {
            LoreProposal p = proposalList.getSelectedValue();
            proposalContent.setText(p == null ? "" : p.getContent());
        });
        proposalContent.setEditable(false);
        proposalContent.setLineWrap(true);
        proposalContent.setWrapStyleWord(true);
        approve.addActionListener(e -> act(() -> resolveSelected(true)));
        reject.addActionListener(e -> act(() -> resolveSelected(false)));
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JScrollPane(proposalList), BorderLayout.WEST);
        center.add(new JScrollPane(proposalContent), BorderLayout.CENTER);
        proposalPanel.add(center, BorderLayout.CENTER);
        proposalPanel.add(buttons(approve, reject), BorderLayout.SOUTH);
        proposalPanel.setBorder(BorderFactory.createTitledBorder("Proposals from the storyteller (0 waiting)"));
        return proposalPanel;
    }

    private void refreshProposals(StorySession session) {
        LoreProposal selected = proposalList.getSelectedValue();
        proposalItems.clear();
        session.getPendingProposals().forEach(proposalItems::addElement);
        if (selected != null && proposalItems.contains(selected)) {
            proposalList.setSelectedValue(selected, true);
        } else {
            proposalContent.setText("");
        }
        int waiting = proposalItems.size();
        proposalPanel.setBorder(BorderFactory.createTitledBorder("Proposals from the storyteller (" + waiting + " waiting)"));
        tabs.setTitleAt(1, waiting == 0 ? "Lore" : "Lore (" + waiting + ")");
    }

    void resolveSelected(boolean approved) {
        LoreProposal p = proposalList.getSelectedValue();
        if (p == null) {
            throw new IllegalArgumentException("Select a proposal first.");
        }
        controller.resolveProposal(p.getId(), approved);
    }

    // ---- lore tab (F02) ----

    private Component buildLoreTab() {
        loreList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loreList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                LoreEntry e = (LoreEntry) value;
                String text = e.getId() + "  " + (e.isPinned() ? "★ " : "") + e.getTitle();
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        loreList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && loreList.getSelectedValue() != null) {
                showLoreEntry(loreList.getSelectedValue());
            }
        });
        loreSearch.getDocument().addDocumentListener(onChange(() -> act(this::refreshLore)));

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(labelled("Search", loreSearch), BorderLayout.NORTH);
        left.add(new JScrollPane(loreList), BorderLayout.CENTER);

        loreContent.setLineWrap(true);
        loreContent.setWrapStyleWord(true);
        JPanel form = form(
                "Title", loreTitle,
                "Content", new JScrollPane(loreContent),
                "Tags (comma separated)", loreTags,
                "", lorePinned);
        loreNew.addActionListener(e -> clearLoreForm());
        loreSave.addActionListener(e -> act(this::saveLoreEntry));
        loreDelete.addActionListener(e -> act(this::deleteLoreEntry));
        JPanel right = new JPanel(new BorderLayout());
        right.add(form, BorderLayout.NORTH);
        right.add(buttons(loreNew, loreSave, loreDelete), BorderLayout.SOUTH);

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(split(left, right), BorderLayout.CENTER);
        tab.add(buildProposalPanel(), BorderLayout.SOUTH);
        return tab;
    }

    private void refreshLore() {
        if (controller.getSession() == null) {
            return;
        }
        boolean wasRefreshing = refreshing;
        refreshing = true;
        try {
            loreItems.clear();
            controller.searchLore(loreSearch.getText()).forEach(loreItems::addElement);
            selectById(editingLoreId);
        } finally {
            refreshing = wasRefreshing;
        }
    }

    private void selectById(String id) {
        for (int i = 0; i < loreItems.size(); i++) {
            if (loreItems.get(i).getId().equals(id)) {
                loreList.setSelectedIndex(i);
                return;
            }
        }
        loreList.clearSelection();
    }

    private void showLoreEntry(LoreEntry e) {
        editingLoreId = e.getId();
        loreTitle.setText(e.getTitle());
        loreContent.setText(e.getContent());
        loreTags.setText(String.join(", ", e.getTags()));
        lorePinned.setSelected(e.isPinned());
    }

    void clearLoreForm() {
        editingLoreId = null;
        loreList.clearSelection();
        loreTitle.setText("");
        loreContent.setText("");
        loreTags.setText("");
        lorePinned.setSelected(false);
    }

    void saveLoreEntry() {
        LoreEntry entry = new LoreEntry(editingLoreId, loreTitle.getText(), loreContent.getText(),
                splitList(loreTags.getText()), lorePinned.isSelected());
        controller.saveLoreEntry(entry);
        editingLoreId = entry.getId();
        refreshLore();
    }

    void deleteLoreEntry() {
        if (editingLoreId == null) {
            throw new IllegalArgumentException("Select a lore entry to delete.");
        }
        String id = editingLoreId;
        clearLoreForm();
        controller.removeLoreEntry(id);
    }

    // ---- characters tab (F03) ----

    private Component buildCharacterTab() {
        characterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        characterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && characterList.getSelectedValue() != null) {
                showCharacter(characterList.getSelectedValue());
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JScrollPane(characterList), BorderLayout.CENTER);

        characterDescription.setLineWrap(true);
        characterDescription.setWrapStyleWord(true);
        JPanel form = form(
                "Name", characterName,
                "Description", new JScrollPane(characterDescription),
                "Traits (comma separated)", characterTraits);

        JPanel relationships = new JPanel(new BorderLayout(4, 4));
        relationships.setBorder(BorderFactory.createTitledBorder("Relationships (one way, from this character)"));
        relationshipList.setVisibleRowCount(4);
        relationships.add(new JScrollPane(relationshipList), BorderLayout.CENTER);
        JPanel removeRow = new JPanel(new BorderLayout());
        removeRow.add(relationshipRemove, BorderLayout.NORTH);
        relationships.add(removeRow, BorderLayout.EAST);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRow.add(new JLabel("To"));
        addRow.add(relationshipTarget);
        addRow.add(new JLabel("Type"));
        addRow.add(relationshipType);
        addRow.add(new JLabel("Notes"));
        addRow.add(relationshipNotes);
        addRow.add(relationshipAdd);
        relationships.add(addRow, BorderLayout.SOUTH);

        characterNew.addActionListener(e -> clearCharacterForm());
        characterSave.addActionListener(e -> act(this::saveCharacter));
        characterDelete.addActionListener(e -> act(this::deleteCharacter));
        relationshipAdd.addActionListener(e -> act(this::addRelationship));
        relationshipRemove.addActionListener(e -> act(this::removeRelationship));

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(form, BorderLayout.NORTH);
        right.add(relationships, BorderLayout.CENTER);
        right.add(buttons(characterNew, characterSave, characterDelete), BorderLayout.SOUTH);
        return split(left, right);
    }

    private void refreshCharacters(StorySession session) {
        characterItems.clear();
        relationshipTarget.removeAllItems();
        Character selected = null;
        for (Character c : session.getCharacters()) {
            characterItems.addElement(c);
            if (editingCharacterName != null && c.getName().equalsIgnoreCase(editingCharacterName)) {
                selected = c;
            }
        }
        if (selected == null) {
            editingCharacterName = null;
            characterList.clearSelection();
            relationshipItems.clear();
        } else {
            characterList.setSelectedValue(selected, true);
            showCharacter(selected);
        }
    }

    private void showCharacter(Character c) {
        editingCharacterName = c.getName();
        characterName.setText(c.getName());
        characterDescription.setText(c.getDescription());
        characterTraits.setText(String.join(", ", c.getTraits()));
        relationshipItems.clear();
        c.getRelationships().forEach(relationshipItems::addElement);
        relationshipTarget.removeAllItems();
        for (int i = 0; i < characterItems.size(); i++) {
            if (!Objects.equals(characterItems.get(i).getName(), c.getName())) {
                relationshipTarget.addItem(characterItems.get(i).getName());
            }
        }
    }

    void clearCharacterForm() {
        editingCharacterName = null;
        characterList.clearSelection();
        characterName.setText("");
        characterDescription.setText("");
        characterTraits.setText("");
        relationshipItems.clear();
        relationshipTarget.removeAllItems();
    }

    void saveCharacter() {
        Character c = editingCharacterName == null
                ? new Character(characterName.getText(), "", List.of())
                : controller.getCharacter(editingCharacterName);
        c.setName(characterName.getText());
        c.setDescription(characterDescription.getText());
        c.setTraits(splitList(characterTraits.getText()));
        String original = editingCharacterName;
        editingCharacterName = c.getName();
        try {
            controller.saveCharacter(original, c);
        } catch (RuntimeException e) {
            editingCharacterName = original;
            throw e;
        }
    }

    void deleteCharacter() {
        if (editingCharacterName == null) {
            throw new IllegalArgumentException("Select a character to delete.");
        }
        String name = editingCharacterName;
        clearCharacterForm();
        controller.removeCharacter(name);
    }

    void addRelationship() {
        if (editingCharacterName == null) {
            throw new IllegalArgumentException("Save or select a character first.");
        }
        String target = (String) relationshipTarget.getSelectedItem();
        if (target == null) {
            throw new IllegalArgumentException("Add another character to relate to first.");
        }
        controller.addRelationship(editingCharacterName, target, relationshipType.getText(), relationshipNotes.getText());
        relationshipType.setText("");
        relationshipNotes.setText("");
    }

    void removeRelationship() {
        Relationship r = relationshipList.getSelectedValue();
        if (editingCharacterName == null || r == null) {
            throw new IllegalArgumentException("Select a relationship to remove.");
        }
        controller.removeRelationship(editingCharacterName, r.getTargetName());
    }

    // ---- helpers ----

    /** Runs a user action and shows any error instead of letting it escape. */
    void act(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException | LLMException e) {
            showError(e.getMessage());
        }
    }

    private JMenuItem menuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> act(action));
        return item;
    }

    private static List<String> splitList(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static JPanel form(Object... labelAndField) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < labelAndField.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            p.add(new JLabel((String) labelAndField[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add((Component) labelAndField[i + 1], c);
        }
        return p;
    }

    private static JPanel labelled(String label, Component field) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel(label), BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static JPanel buttons(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        for (JButton b : buttons) {
            p.add(b);
        }
        return p;
    }

    private static JSplitPane split(Component left, Component right) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(260);
        split.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return split;
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    public static void main(String[] args) {
        StoryController controller = StoryCLI.createController();
        SwingUtilities.invokeLater(() -> {
            StoryGUI gui = new StoryGUI(controller);
            controller.addObserver(gui);
            gui.open();
        });
    }
}

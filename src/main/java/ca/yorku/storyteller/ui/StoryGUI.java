package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.controller.SavedStory;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * <p>An open story is one screen: the story (recap card, transcript, turn controls) with a sidebar on
 * its right that holds the lore used this turn and the Lore, Characters and Proposals tabs. The
 * sidebar collapses to leave the story the whole window. Colours come from {@link StoryTheme}.
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
    final JLabel status = new JLabel("No story open.");

    /** The start screen while no story is open, the story's tabs once one is. */
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    static final String HOME_CARD = "home";
    static final String STORY_CARD = "story";
    private String shownCard;

    /** The open story: its name on top, the story in the middle, the sidebar on the right. */
    private final JPanel storyCard = new JPanel(new BorderLayout());
    /** The name of the open story, above the transcript; a title too long to fit is whole in its tooltip. */
    final JLabel storyHeading = new JLabel(StorySession.UNTITLED);

    // Sidebar (F02, F03, F04, F07): the lore used this turn, then the Lore, Characters and Proposals tabs
    final JPanel sidebar = new JPanel(new BorderLayout(0, 10));
    final JTabbedPane sidebarTabs = new JTabbedPane();
    /** Collapses the sidebar and brings it back; while collapsed it also counts the waiting proposals. */
    final JButton sidebarToggle = new JButton();
    static final int SIDEBAR_WIDTH = 400;
    static final int LORE_TAB = 0;
    static final int CHARACTERS_TAB = 1;
    static final int PROPOSALS_TAB = 2;
    private int waitingProposals;

    // Start screen (F01, F11): pick a saved story, or create one
    private final JPanel home = new JPanel(new GridBagLayout());
    final JLabel homeHeading = new JLabel();
    final DefaultListModel<SavedStory> savedStoryItems = new DefaultListModel<>();
    final JList<SavedStory> savedStoryList = new JList<>(savedStoryItems);
    final JButton openStory = new JButton("Open");
    final JButton createStory = new JButton("Create story...");

    private static final DateTimeFormatter SAVED_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH);

    /** "Nov 9, 2026, 4:27 AM": when a story was saved, in the given time zone. */
    static String savedLabel(Instant savedAt, ZoneId zone) {
        return SAVED_FORMAT.format(savedAt.atZone(zone));
    }

    /** How a saved story reads in the Load dialog: "Ember Road (saved Nov 9, 2026, 4:27 AM)". */
    static String choiceLabel(SavedStory s) {
        return s.title() + "  (saved " + savedLabel(s.savedAt(), ZoneId.systemDefault()) + ")";
    }

    /** The colour of the "Saved ..." note next to a story's title, as "#rrggbb". */
    static String savedColour(boolean selected) {
        Color c = selected ? StoryTheme.TEXT : StoryTheme.MUTED;
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    /** Draws a saved story as its title, then when it was saved in a quieter colour. */
    static final class SavedStoryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            SavedStory s = (SavedStory) value;
            String grey = savedColour(selected);
            String html = "<html>" + escape(s.title()) + "&nbsp;&nbsp;<font color='" + grey + "'>Saved "
                    + savedLabel(s.savedAt(), ZoneId.systemDefault()) + "</font></html>";
            return super.getListCellRendererComponent(list, html, index, selected, focus);
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // Story tab (F04, F10)
    final JLabel storyTitle = new JLabel();
    // Recap after loading a story (F09), at the top of the Story tab
    final JTextArea recap = new JTextArea();
    final JPanel recapPanel = new JPanel(new BorderLayout());
    final JLabel recapHeading = StoryTheme.sectionLabel("Story recap");
    final JButton hideRecap = new JButton("Dismiss");
    /** The recap card grows with its text up to this many lines, then scrolls. */
    static final int RECAP_ROWS = 5;
    /** The story the recap belongs to; the recap is hidden once another story is open. */
    private String recapSessionId;
    final JTextArea transcript = new JTextArea();
    /** How fast new story text is revealed in the transcript, in characters per second. */
    static final int CHARS_PER_SECOND = 300;
    /** Reveals new narration gradually instead of all at once; click the transcript to show it all. */
    final TranscriptPacer pacer = new TranscriptPacer(transcript, CHARS_PER_SECOND);
    /** The story the transcript shows; text is only paced while the same story stays open. */
    private String shownSessionId;
    final DefaultListModel<LoreEntry> usedLoreItems = new DefaultListModel<>();
    final JList<LoreEntry> usedLoreList = new JList<>(usedLoreItems);
    final JLabel usedLoreEmpty = new JLabel("No lore was looked up in the last turn.");
    private JScrollPane usedLoreScroll;
    final JComboBox<String> modeChoice;
    final JTextArea input = new JTextArea(3, 40);
    final JButton send = new JButton("Send");
    final JButton undo = new JButton("Undo");
    final JButton retry = new JButton("Retry");
    final JButton edit = new JButton("Edit");
    final JButton endStory = new JButton("End story");

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

    // Lore proposals (F07), in the sidebar's Proposals tab
    final DefaultListModel<LoreProposal> proposalItems = new DefaultListModel<>();
    final JList<LoreProposal> proposalList = new JList<>(proposalItems);
    final JTextArea proposalContent = new JTextArea(6, 20);
    final JLabel proposalHint = new JLabel();
    final JButton approve = new JButton("Approve");
    final JButton reject = new JButton("Reject");
    private final JPanel proposalPanel = new JPanel(new BorderLayout(0, 8));

    /** True while a model call runs; the controls that would start another one are disabled. */
    private boolean busy;

    // Lore tab of the sidebar (F02)
    final JTextField loreSearch = new JTextField();
    final DefaultListModel<LoreEntry> loreItems = new DefaultListModel<>();
    final JList<LoreEntry> loreList = new JList<>(loreItems);
    final JTextField loreTitle = new JTextField();
    final JTextArea loreContent = new JTextArea(5, 20);
    final JTextField loreTags = new JTextField();
    final JCheckBox lorePinned = new JCheckBox("Pinned (canon)");
    final JButton loreNew = new JButton("New");
    final JButton loreSave = new JButton("Save");
    final JButton loreDelete = new JButton("Delete");
    private String editingLoreId;

    // Characters tab of the sidebar (F03)
    /** A relationship belongs to the character it is added from; the other character gets none back. */
    static final String RELATIONSHIPS_TITLE = "Relationships (one-way)";
    final DefaultListModel<Character> characterItems = new DefaultListModel<>();
    final JList<Character> characterList = new JList<>(characterItems);
    final JTextField characterName = new JTextField();
    final JTextArea characterDescription = new JTextArea(3, 20);
    final JTextField characterTraits = new JTextField();
    final DefaultListModel<Relationship> relationshipItems = new DefaultListModel<>();
    final JList<Relationship> relationshipList = new JList<>(relationshipItems);
    final JComboBox<String> relationshipTarget = new JComboBox<>();
    final JTextField relationshipType = new JTextField(10);
    final JTextField relationshipNotes = new JTextField(14);
    final JButton characterNew = new JButton("New");
    final JButton characterSave = new JButton("Save");
    final JButton characterDelete = new JButton("Delete");
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
        buildHome();
        buildStoryCard();
        cards.add(home, HOME_CARD);
        cards.add(storyCard, STORY_CARD);
        root.add(cards, BorderLayout.CENTER);
        status.setOpaque(true);
        status.setBackground(StoryTheme.BASE);
        status.setForeground(StoryTheme.MUTED);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, StoryTheme.LINE),
                BorderFactory.createEmptyBorder(5, 16, 5, 16)));
        root.add(status, BorderLayout.SOUTH);
        refreshStoryControls();
        if (controller.getSession() == null) {
            refreshHome();
            showCard(HOME_CARD);
        } else {
            showCard(STORY_CARD);
        }
    }

    private void showCard(String name) {
        cardLayout.show(cards, name);
        shownCard = name;
    }

    /** True while the start screen is showing, that is, before any story is open. */
    boolean isShowingHome() {
        return HOME_CARD.equals(shownCard);
    }

    /** Shows the window. */
    public void open() {
        JFrame frame = new JFrame("AI Interactive Storyteller");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar);
        frame.setContentPane(root);
        frame.setSize(new Dimension(1180, 740));
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(root, message, "Cannot do that", JOptionPane.WARNING_MESSAGE);
    }

    protected void showMessage(String message) {
        JOptionPane.showMessageDialog(root, message, "Storyteller", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Asks a yes/no question; true for yes. */
    protected boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(root, question, "Storyteller", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
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

    /** Asks the user to pick a saved story; its id, or null if cancelled. */
    protected String chooseSession(List<SavedStory> stories) {
        String[] labels = stories.stream().map(StoryGUI::choiceLabel).toArray(String[]::new);
        Object choice = JOptionPane.showInputDialog(root, "Saved stories (newest first):", "Load a story",
                JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == choice) {
                return stories.get(i).id();
            }
        }
        return null;
    }

    private List<SavedStory> savedStories() {
        return controller.listSavedStories();
    }

    /** Observer: re-reads the session into every list and the status bar. */
    @Override
    public void onStoryChanged(StorySession session) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onStoryChanged(session));
            return;
        }
        showCard(STORY_CARD);
        refreshing = true;
        try {
            int waiting = session.getPendingProposals().size();
            if (!busy) {
                status.setText("Story " + session.getId() + ": " + session.getLoreBook().size() + " lore entries, "
                        + session.getCharacters().size() + " characters, " + session.getTurns().size() + " turns"
                        + (session.isEnded() ? ", ended" : "")
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
        List<SavedStory> stories = savedStories();
        if (stories.isEmpty()) {
            showError("There are no saved stories yet.");
            return;
        }
        String id = chooseSession(stories);
        if (id != null) {
            loadStory(id);
        }
    }

    private void loadStory(String id) {
        runInBackground("Loading the story and writing a recap...", () -> controller.loadSession(id),
                this::showRecap, () -> { });
    }

    // ---- start screen (F01, F11) ----

    private void buildHome() {
        homeHeading.setFont(homeHeading.getFont().deriveFont(Font.BOLD, 18f));
        savedStoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedStoryList.setCellRenderer(new SavedStoryRenderer());
        savedStoryList.setVisibleRowCount(8);
        savedStoryList.setToolTipText("Double-click a story to open it.");
        savedStoryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && savedStoryList.getSelectedValue() != null) {
                    act(StoryGUI.this::openSelectedStory);
                }
            }
        });
        savedStoryList.addListSelectionListener(e -> openStory.setEnabled(savedStoryList.getSelectedValue() != null));
        openStory.addActionListener(e -> act(this::openSelectedStory));
        createStory.addActionListener(e -> act(this::openStartForm));
        home.setBackground(StoryTheme.BASE);
        StoryTheme.primary(openStory);
    }

    /**
     * Fills the start screen from the save folder: "Select story" and the saved stories, newest first,
     * or "No stories detected" with only the Create story... button.
     */
    void refreshHome() {
        List<SavedStory> stories;
        try {
            stories = savedStories();
        } catch (UncheckedIOException e) {
            stories = List.of();
            status.setText(e.getMessage());
        }
        savedStoryItems.clear();
        stories.forEach(savedStoryItems::addElement);
        home.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(6, 6, 6, 6);
        if (stories.isEmpty()) {
            homeHeading.setText("No stories detected");
            home.add(homeHeading, c);
            StoryTheme.primary(createStory);
            home.add(createStory, c);
        } else {
            homeHeading.setText("Select story");
            home.add(homeHeading, c);
            JScrollPane list = new JScrollPane(savedStoryList);
            list.setPreferredSize(new Dimension(460, 220));
            home.add(list, c);
            savedStoryList.setSelectedIndex(0);
            StoryTheme.plain(createStory);
            home.add(StoryTheme.layer(buttons(openStory, createStory), StoryTheme.BASE), c);
        }
        home.revalidate();
        home.repaint();
    }

    void openSelectedStory() {
        SavedStory choice = savedStoryList.getSelectedValue();
        if (choice == null) {
            throw new IllegalArgumentException("Select a story first.");
        }
        loadStory(choice.id());
    }

    /** Shows what was loaded and the agent's recap in a card above the transcript (F09). */
    void showRecap(String text) {
        recapSessionId = controller.getSession().getId();
        recap.setText(text);
        recap.setCaretPosition(0);
        recapPanel.setVisible(true);
    }

    // ---- story tab: turns, undo, retry, edit (F04, F10) ----

    private void buildStoryCard() {
        storyHeading.setFont(storyHeading.getFont().deriveFont(Font.BOLD, 18f));
        sidebarToggle.addActionListener(e -> setSidebarOpen(!isSidebarOpen()));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(StoryTheme.BASE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StoryTheme.LINE),
                BorderFactory.createEmptyBorder(8, 16, 8, 12)));
        header.add(storyHeading, BorderLayout.CENTER);
        header.add(sidebarToggle, BorderLayout.EAST);
        storyCard.add(header, BorderLayout.NORTH);
        storyCard.add(buildStoryColumn(), BorderLayout.CENTER);
        storyCard.add(buildSidebar(), BorderLayout.EAST);
        refreshSidebarToggle();
    }

    // ---- story column: recap, transcript, turns, undo, retry, edit (F04, F09, F10) ----

    private Component buildStoryColumn() {
        storyTitle.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(storyTitle, BorderLayout.NORTH);
        north.add(buildRecapCard(), BorderLayout.CENTER);
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setMargin(new Insets(14, 18, 14, 18));
        transcript.setFont(transcript.getFont().deriveFont(15f));
        transcript.setToolTipText("Click to show the rest of the text at once.");
        transcript.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                pacer.skip();
            }
        });

        JPanel turnButtons = new JPanel(new GridLayout(1, 3, 6, 0));
        for (JButton b : List.of(undo, retry, edit)) {
            turnButtons.add(b);
        }
        JPanel actions = new JPanel(new BorderLayout());
        actions.add(turnButtons, BorderLayout.WEST);
        actions.add(endStory, BorderLayout.EAST);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        StoryTheme.primary(send);
        JPanel inputRow = new JPanel(new BorderLayout(6, 6));
        inputRow.add(modeChoice, BorderLayout.WEST);
        inputRow.add(new JScrollPane(input), BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(6, 8));
        south.add(actions, BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.SOUTH);

        send.addActionListener(e -> act(this::submitInput));
        undo.addActionListener(e -> act(this::undoTurn));
        retry.addActionListener(e -> act(this::retryTurn));
        edit.addActionListener(e -> act(this::editTurn));
        endStory.addActionListener(e -> act(this::endStory));

        JPanel p = new JPanel(new BorderLayout(8, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(transcript), BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return StoryTheme.layer(p, StoryTheme.SURFACE);
    }

    /**
     * The recap as a card that sits on the story column: a raised shade with an accent stripe, a small
     * heading and the text itself, no box around it. It is as tall as its text, up to RECAP_ROWS lines.
     */
    private Component buildRecapCard() {
        recap.setEditable(false);
        recap.setLineWrap(true);
        recap.setWrapStyleWord(true);
        recap.setOpaque(false);
        recap.setMargin(new Insets(0, 0, 0, 0));
        JScrollPane scroll = new JScrollPane(recap) {
            @Override
            public Dimension getPreferredSize() {
                int line = recap.getFontMetrics(recap.getFont()).getHeight();
                Dimension d = super.getPreferredSize();
                d.height = Math.min(recap.getPreferredSize().height, line * RECAP_ROWS);
                return d;
            }
        };
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        StoryTheme.quiet(hideRecap);
        hideRecap.setToolTipText("Hide the recap. The story itself is not changed.");
        hideRecap.addActionListener(e -> recapPanel.setVisible(false));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(recapHeading, BorderLayout.WEST);
        heading.add(hideRecap, BorderLayout.EAST);
        StoryTheme.Card card = new StoryTheme.Card(new BorderLayout(0, 6), StoryTheme.RAISED, StoryTheme.ACCENT_SOFT);
        card.add(heading, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        recapPanel.setOpaque(false);
        recapPanel.add(card, BorderLayout.CENTER);
        recapPanel.setVisible(false);
        return recapPanel;
    }

    // ---- sidebar: lore used this turn, Lore, Characters, Proposals (F02, F03, F04, F07) ----

    private Component buildSidebar() {
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
        usedLoreList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, loreLabel((LoreEntry) value), index, selected, focus);
            }
        });
        usedLoreEmpty.setForeground(StoryTheme.MUTED);
        usedLoreScroll = new JScrollPane(usedLoreList);
        usedLoreScroll.setVisible(false);
        JPanel usedBody = new JPanel(new BorderLayout());
        usedBody.setOpaque(false);
        usedBody.add(usedLoreEmpty, BorderLayout.NORTH);
        usedBody.add(usedLoreScroll, BorderLayout.CENTER);
        StoryTheme.Card used = new StoryTheme.Card(new BorderLayout(0, 6), StoryTheme.RAISED, null);
        used.add(StoryTheme.sectionLabel("Lore used this turn"), BorderLayout.NORTH);
        used.add(usedBody, BorderLayout.CENTER);

        sidebarTabs.addTab("Lore", buildLoreTab());
        sidebarTabs.addTab("Characters", buildCharacterTab());
        sidebarTabs.addTab("Proposals", buildProposalPanel());

        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, StoryTheme.LINE),
                BorderFactory.createEmptyBorder(12, 12, 10, 12)));
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.add(used, BorderLayout.NORTH);
        sidebar.add(sidebarTabs, BorderLayout.CENTER);
        return StoryTheme.layer(sidebar, StoryTheme.SIDEBAR);
    }

    /** True while the sidebar is showing; collapsed, the story takes the whole window. */
    boolean isSidebarOpen() {
        return sidebar.isVisible();
    }

    void setSidebarOpen(boolean open) {
        sidebar.setVisible(open);
        refreshSidebarToggle();
        storyCard.revalidate();
    }

    private void refreshSidebarToggle() {
        String waiting = waitingProposals == 0 ? "" : " (" + waitingProposals + ")";
        sidebarToggle.setText(isSidebarOpen() ? "Hide lore & characters »" : "« Lore & characters" + waiting);
        sidebarToggle.setToolTipText(isSidebarOpen() ? "Collapse the sidebar to give the story the whole window."
                : waitingProposals == 0 ? "Show the lore, the characters and the lore used this turn."
                : "Show the sidebar: " + waitingProposals + " lore proposal" + (waitingProposals == 1 ? "" : "s") + " waiting.");
    }

    /** How a lore entry reads in a list: its id, a star when pinned, its title. */
    private static String loreLabel(LoreEntry e) {
        return e.getId() + "  " + (e.isPinned() ? "★ " : "") + e.getTitle();
    }

    private void refreshStory(StorySession session) {
        Scenario sc = session.getScenario();
        storyHeading.setText(session.getTitle());
        storyHeading.setToolTipText(session.getTitle());
        // The heading already names the story; the line above the transcript only explains how to start one.
        storyTitle.setText(sc == null ? "Story not started yet. Use Story > Start story..." : "");
        storyTitle.setVisible(sc == null);
        if (!session.getId().equals(recapSessionId)) {
            recapPanel.setVisible(false);
        }
        StringBuilder sb = new StringBuilder();
        if (sc != null && !sc.getOpeningSituation().isEmpty()) {
            sb.append(sc.getOpeningSituation()).append("\n\n");
        }
        for (Turn t : session.getTurns()) {
            sb.append("> ").append(t.getModeName()).append(": ").append(t.getUserInput()).append("\n\n")
                    .append(t.getNarration()).append("\n\n");
            for (String w : t.getWarnings()) {
                sb.append("[Warning: ").append(w).append("]\n");
            }
            if (!t.getWarnings().isEmpty()) {
                sb.append('\n');
            }
        }
        if (session.isEnded()) {
            sb.append("* * *\n\n").append(session.getEnding()).append("\n\nTHE END");
        }
        // A new turn or ending extends the transcript and is revealed gradually; anything else
        // (another story, an undo, an edit) replaces it at once.
        pacer.show(sb.toString().strip(), session.getId().equals(shownSessionId));
        shownSessionId = session.getId();
        usedLoreItems.clear();
        session.lastTurn().ifPresent(t -> {
            for (String id : t.getUsedLoreIds()) {
                controller.searchLore("").stream().filter(e -> e.getId().equals(id)).findFirst()
                        .ifPresent(usedLoreItems::addElement);
            }
        });
        usedLoreEmpty.setVisible(usedLoreItems.isEmpty());
        usedLoreScroll.setVisible(!usedLoreItems.isEmpty());
        refreshStoryControls();
    }

    /** Turn controls are usable only for a started story; undo, retry and edit only when they apply. */
    private void refreshStoryControls() {
        boolean ongoing = controller.isStarted() && !controller.isEnded() && !busy;
        input.setEnabled(ongoing);
        modeChoice.setEnabled(ongoing);
        send.setEnabled(ongoing);
        undo.setEnabled(ongoing && controller.canUndo());
        retry.setEnabled(ongoing && controller.canUndo());
        edit.setEnabled(ongoing && controller.canEnd());
        endStory.setEnabled(controller.canEnd() && !busy);
        endStory.setText(controller.isEnded() ? "Rewrite ending" : "End story");
    }

    void submitInput() {
        String text = input.getText();
        String mode = (String) modeChoice.getSelectedItem();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Type something for the turn first.");
        }
        pacer.skip();
        runInBackground("The storyteller is writing...", () -> controller.submitTurn(text, mode), turn -> {
            input.setText("");
            offerEnding();
        }, () -> { });
    }

    void undoTurn() {
        controller.undoTurn();
    }

    void retryTurn() {
        Turn last = controller.getSession().lastTurn().orElseThrow(() -> new IllegalStateException("There is no turn to retry."));
        runInBackground("The storyteller is rewriting the last turn...", controller::retryTurn, turn -> offerEnding(), () -> {
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

    // ---- ending (F12) ----

    /** After a turn that reached the last pivotal event, the storyteller suggests ending the story. */
    private void offerEnding() {
        if (controller.isEndingSuggested()
                && confirm("The story has reached its final turning point. End it now?")) {
            writeEnding();
        }
    }

    /** End story / Rewrite ending: asks first when events are still pending or an ending exists. */
    void endStory() {
        if (controller.isEnded()) {
            if (!confirm("Write a new ending in place of the current one?")) {
                return;
            }
        } else if (!controller.isReadyToEnd()
                && !confirm("The story has not reached all of its turning points yet. End it here anyway?")) {
            return;
        }
        writeEnding();
    }

    private void writeEnding() {
        runInBackground("The storyteller is writing the ending...", controller::requestEnding, ending -> { }, () -> { });
    }

    /** Opens a lore entry in the sidebar's Lore tab, bringing the sidebar back if it is collapsed. */
    void openLoreEntry(String id) {
        setSidebarOpen(true);
        sidebarTabs.setSelectedIndex(LORE_TAB);
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
        StoryTheme.primary(startButton);
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
        }, () -> { });
    }

    // ---- lore proposals (F07) ----

    private Component buildProposalPanel() {
        proposalList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        proposalList.setVisibleRowCount(5);
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
            proposalContent.setCaretPosition(0);
        });
        proposalContent.setEditable(false);
        proposalContent.setLineWrap(true);
        proposalContent.setWrapStyleWord(true);
        proposalHint.setForeground(StoryTheme.MUTED);
        approve.addActionListener(e -> act(() -> resolveSelected(true)));
        reject.addActionListener(e -> act(() -> resolveSelected(false)));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(proposalHint, BorderLayout.NORTH);
        top.add(new JScrollPane(proposalList), BorderLayout.CENTER);
        proposalPanel.setBorder(BorderFactory.createEmptyBorder(10, 2, 0, 2));
        proposalPanel.add(top, BorderLayout.NORTH);
        proposalPanel.add(new JScrollPane(proposalContent), BorderLayout.CENTER);
        proposalPanel.add(buttons(approve, reject), BorderLayout.SOUTH);
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
        waitingProposals = waiting;
        proposalHint.setText(waiting == 0
                ? "<html>No proposals waiting. When the storyteller suggests new lore, it appears here for you"
                        + " to approve or reject.</html>"
                : "<html>The storyteller suggests " + (waiting == 1 ? "this entry" : "these entries")
                        + ". Nothing joins the lore until you approve it.</html>");
        approve.setEnabled(waiting > 0);
        reject.setEnabled(waiting > 0);
        sidebarTabs.setTitleAt(PROPOSALS_TAB, waiting == 0 ? "Proposals" : "Proposals (" + waiting + ")");
        refreshSidebarToggle();
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
        loreList.setVisibleRowCount(5);
        loreList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, loreLabel((LoreEntry) value), index, selected, focus);
            }
        });
        loreList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && loreList.getSelectedValue() != null) {
                showLoreEntry(loreList.getSelectedValue());
            }
        });
        loreSearch.getDocument().addDocumentListener(onChange(() -> act(this::refreshLore)));

        JPanel entries = new JPanel(new BorderLayout(4, 8));
        entries.add(labelled("Search", loreSearch), BorderLayout.NORTH);
        entries.add(new JScrollPane(loreList), BorderLayout.CENTER);

        loreContent.setLineWrap(true);
        loreContent.setWrapStyleWord(true);
        loreTags.setToolTipText("Separate tags with commas.");
        JPanel form = form(
                "Title", loreTitle,
                "Content", new JScrollPane(loreContent),
                "Tags", loreTags,
                "", lorePinned);
        loreNew.addActionListener(e -> clearLoreForm());
        loreSave.addActionListener(e -> act(this::saveLoreEntry));
        loreDelete.addActionListener(e -> act(this::deleteLoreEntry));
        JPanel editor = new JPanel(new BorderLayout());
        editor.add(form, BorderLayout.CENTER);
        editor.add(editorButtons(loreNew, loreSave, loreDelete), BorderLayout.SOUTH);

        // The sidebar is narrow, so the entries sit above the editor instead of beside it.
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(10, 2, 0, 2));
        tab.add(entries, BorderLayout.CENTER);
        tab.add(editor, BorderLayout.SOUTH);
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
        characterList.setVisibleRowCount(4);
        characterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && characterList.getSelectedValue() != null) {
                showCharacter(characterList.getSelectedValue());
            }
        });

        characterDescription.setLineWrap(true);
        characterDescription.setWrapStyleWord(true);
        characterTraits.setToolTipText("Separate traits with commas.");
        JPanel form = form(
                "Name", characterName,
                "Description", new JScrollPane(characterDescription),
                "Traits", characterTraits);

        JPanel relationships = new JPanel(new BorderLayout(4, 4));
        relationships.setBorder(BorderFactory.createTitledBorder(RELATIONSHIPS_TITLE));
        relationshipList.setVisibleRowCount(2);
        relationships.add(new JScrollPane(relationshipList), BorderLayout.CENTER);
        relationshipNotes.setToolTipText("Optional notes about the relationship.");
        // To and Type share a row so the character list above keeps a few rows in the narrow sidebar.
        JPanel type = new JPanel(new BorderLayout(6, 0));
        type.add(new JLabel("Type"), BorderLayout.WEST);
        type.add(relationshipType, BorderLayout.CENTER);
        JPanel toAndType = new JPanel(new GridLayout(1, 2, 8, 0));
        toAndType.add(relationshipTarget);
        toAndType.add(type);
        JPanel addRow = form(
                "To", toAndType,
                "Notes", relationshipNotes);
        JPanel addPanel = new JPanel(new BorderLayout());
        addPanel.add(addRow, BorderLayout.CENTER);
        addPanel.add(buttons(relationshipAdd, relationshipRemove), BorderLayout.SOUTH);
        relationships.add(addPanel, BorderLayout.SOUTH);

        characterNew.addActionListener(e -> clearCharacterForm());
        characterSave.addActionListener(e -> act(this::saveCharacter));
        characterDelete.addActionListener(e -> act(this::deleteCharacter));
        relationshipAdd.addActionListener(e -> act(this::addRelationship));
        relationshipRemove.addActionListener(e -> act(this::removeRelationship));

        JPanel editor = new JPanel(new BorderLayout());
        editor.add(form, BorderLayout.NORTH);
        editor.add(editorButtons(characterNew, characterSave, characterDelete), BorderLayout.CENTER);
        editor.add(relationships, BorderLayout.SOUTH);

        // The sidebar is narrow, so the characters sit above the editor instead of beside it.
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(10, 2, 0, 2));
        tab.add(new JScrollPane(characterList), BorderLayout.CENTER);
        tab.add(editor, BorderLayout.SOUTH);
        return tab;
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
            c.insets = new Insets(8, 4, 4, 4); // level with the first line of text in the field
            p.add(new JLabel((String) labelAndField[i]), c);
            c.insets = new Insets(4, 4, 4, 4);
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

    /** New on the left, apart from the buttons that act on the entry in the form, on the right. */
    private static JPanel editorButtons(JButton create, JButton... onEntry) {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(create);
        JPanel p = new JPanel(new BorderLayout());
        p.add(left, BorderLayout.WEST);
        p.add(buttons(onEntry), BorderLayout.EAST);
        return p;
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
            StoryTheme.install();
            StoryGUI gui = new StoryGUI(controller);
            controller.addObserver(gui);
            gui.open();
        });
    }
}

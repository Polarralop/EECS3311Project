package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.StorySession;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Swing front end. Displays session state and forwards user actions to the controller; it holds no
 * story logic. Every list is re-read from the session in {@link #onStoryChanged}.
 *
 * <p>The panels are built in the constructor and the window only in {@link #open()}, so the panels
 * can be exercised in tests without a display.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (View), Observer (Concrete observer).
 */
public class StoryGUI implements StoryObserver {

    private final StoryController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JMenuBar menuBar = new JMenuBar();
    final JLabel status = new JLabel("No story open. Use Story > New or Story > Load.");

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
        buildMenu();
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Lore", buildLoreTab());
        tabs.addTab("Characters", buildCharacterTab());
        root.add(tabs, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        root.add(status, BorderLayout.SOUTH);
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
            status.setText("Story " + session.getId() + ": " + session.getLoreBook().size() + " lore entries, "
                    + session.getCharacters().size() + " characters, " + session.getTurns().size() + " turns");
            refreshLore();
            refreshCharacters(session);
        } finally {
            refreshing = false;
        }
    }

    // ---- menu: new, save, load (F11) ----

    private void buildMenu() {
        JMenu story = new JMenu("Story");
        story.add(menuItem("New", () -> controller.newSession()));
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

        return split(left, right);
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
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
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

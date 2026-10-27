package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.llm.ClaudeAdapter;
import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Command-line front end with the same functionality as the GUI. It only reads commands, forwards
 * them to {@link StoryController} and prints what it is given; it holds no story logic.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (View), Observer (Concrete observer).
 */
public class StoryCLI implements StoryObserver {

    private static final String HELP = """
            Commands:
              new                     open an empty story (prepare lore and characters before starting)
              start                   write the scenario and start the story (you will be asked for each field)
              save                    save the current story
              load <id>               load a saved story
              sessions                list saved stories
              lore [list]             list lore entries (pinned first)
              lore search <words>     search lore by title, content and tags
              lore show <id>          show one entry
              lore add                add an entry (you will be asked for each field)
              lore edit <id>          edit an entry (press Enter to keep a value)
              lore pin|unpin <id>     mark an entry as canon, or not
              lore remove <id>        delete an entry
              char [list]             list characters
              char show <name>        show a character and its relationships
              char add                add a character
              char edit <name>        edit a character
              char remove <name>      delete a character (and relationships to it)
              rel add                 add a relationship between two characters
              rel remove              remove a relationship
              help                    show this list
              quit                    leave""";

    private final StoryController controller;
    private final BufferedReader in;
    private final PrintStream out;
    private boolean running = true;

    public StoryCLI(StoryController controller, BufferedReader in, PrintStream out) {
        this.controller = controller;
        this.in = in;
        this.out = out;
    }

    /** Reads and handles commands until "quit" or the end of input. */
    public void run() {
        out.println("AI Interactive Storyteller. Type \"help\" for commands, \"new\" to start or \"load <id>\".");
        while (running) {
            out.print("> ");
            out.flush();
            String line = readLine();
            if (line == null) {
                break;
            }
            handleCommand(line);
        }
    }

    /** Handles one command line. Errors are printed and the loop carries on. */
    public void handleCommand(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String cmd = words[0].toLowerCase(Locale.ROOT);
        String sub = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "";
        String rest = argAfterSub(line);
        try {
            switch (cmd) {
                case "" -> { }
                case "help" -> out.println(HELP);
                case "quit", "exit" -> running = false;
                case "new" -> out.println("Opened " + controller.newSession().getId() + ". Add lore and characters, then type \"start\".");
                case "start" -> start();
                case "save" -> {
                    controller.saveSession();
                    out.println("Saved as " + controller.getSession().getId() + ".");
                }
                case "load" -> out.println(controller.loadSession(requireArg(words.length > 1 ? words[1] : "", "load <id>")));
                case "sessions" -> listSessions();
                case "lore" -> lore(sub, rest);
                case "char" -> character(sub, rest);
                case "rel" -> relationship(sub);
                default -> printError("Unknown command \"" + words[0] + "\". Type \"help\".");
            }
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException | LLMException e) {
            printError(e.getMessage());
        }
    }

    public void printError(String message) {
        out.println("Error: " + message);
    }

    /** Observer: one status line after every change, so the user sees what the story now holds. */
    @Override
    public void onStoryChanged(StorySession session) {
        out.println("[" + session.getId() + ": " + session.getLoreBook().size() + " lore, "
                + session.getCharacters().size() + " characters, " + session.getTurns().size() + " turns]");
    }

    // ---- scenario and pivotal events (F01, F05) ----

    private void start() {
        String title = ask("Title (optional)");
        String premise = ask("Premise");
        String tone = ask("Tone (optional)");
        String setting = ask("Setting");
        String opening = ask("Opening situation (optional)");
        String basis = ask("Plot basis (optional, kept hidden)");
        Scenario scenario = new Scenario(title, premise, tone, setting, opening);
        out.println("Preparing the story...");
        controller.startStory(scenario, basis);
        out.println("The story \"" + controller.getSession().getScenario().getTitle() + "\" has begun.");
    }

    // ---- lore (F02) ----

    private void lore(String sub, String rest) {
        switch (sub) {
            case "", "list" -> printLore(controller.searchLore(""));
            case "search" -> printLore(controller.searchLore(rest));
            case "show" -> printLoreEntry(controller.getLoreEntry(requireArg(rest, "lore show <id>")));
            case "add" -> {
                String title = ask("Title");
                String content = ask("Content");
                List<String> tags = splitList(ask("Tags (comma separated, optional)"));
                boolean pinned = yes(ask("Pinned? (y/n)"));
                LoreEntry entry = new LoreEntry(null, title, content, tags, pinned);
                controller.saveLoreEntry(entry);
                out.println("Added " + entry.getId() + ".");
            }
            case "edit" -> {
                LoreEntry entry = controller.getLoreEntry(requireArg(rest, "lore edit <id>"));
                entry.setTitle(askKeep("Title", entry.getTitle()));
                entry.setContent(askKeep("Content", entry.getContent()));
                entry.setTags(splitList(askKeep("Tags", String.join(", ", entry.getTags()))));
                entry.setPinned(yes(askKeep("Pinned? (y/n)", entry.isPinned() ? "y" : "n")));
                controller.saveLoreEntry(entry);
                out.println("Updated " + entry.getId() + ".");
            }
            case "pin", "unpin" -> {
                LoreEntry entry = controller.getLoreEntry(requireArg(rest, "lore " + sub + " <id>"));
                entry.setPinned(sub.equals("pin"));
                controller.saveLoreEntry(entry);
                out.println((sub.equals("pin") ? "Pinned " : "Unpinned ") + entry.getId() + ".");
            }
            case "remove" -> {
                controller.removeLoreEntry(requireArg(rest, "lore remove <id>"));
                out.println("Removed " + rest.strip() + ".");
            }
            default -> printError("Unknown lore command \"" + sub + "\". Type \"help\".");
        }
    }

    private void printLore(List<LoreEntry> entries) {
        if (entries.isEmpty()) {
            out.println("No lore entries.");
        }
        for (LoreEntry e : entries) {
            out.println(e.getId() + (e.isPinned() ? " [pinned] " : " ") + e.getTitle()
                    + (e.getTags().isEmpty() ? "" : "  #" + String.join(" #", e.getTags())));
        }
    }

    private void printLoreEntry(LoreEntry e) {
        out.println(e.getId() + (e.isPinned() ? " [pinned] " : " ") + e.getTitle());
        out.println(e.getContent());
        if (!e.getTags().isEmpty()) {
            out.println("Tags: " + String.join(", ", e.getTags()));
        }
    }

    // ---- characters (F03) ----

    private void character(String sub, String name) {
        switch (sub) {
            case "", "list" -> {
                List<Character> all = controller.listCharacters();
                if (all.isEmpty()) {
                    out.println("No characters.");
                }
                for (Character c : all) {
                    out.println(c.getName() + (c.getDescription().isEmpty() ? "" : ": " + c.getDescription()));
                }
            }
            case "show" -> out.println(controller.getCharacter(requireArg(name, "char show <name>")).describe());
            case "add" -> {
                Character c = new Character(ask("Name"), ask("Description"), splitList(ask("Traits (comma separated)")));
                controller.saveCharacter(null, c);
                out.println("Added " + c.getName() + ".");
            }
            case "edit" -> {
                Character c = controller.getCharacter(requireArg(name, "char edit <name>"));
                String original = c.getName();
                c.setName(askKeep("Name", c.getName()));
                c.setDescription(askKeep("Description", c.getDescription()));
                c.setTraits(splitList(askKeep("Traits", String.join(", ", c.getTraits()))));
                controller.saveCharacter(original, c);
                out.println("Updated " + c.getName() + ".");
            }
            case "remove" -> {
                controller.removeCharacter(requireArg(name, "char remove <name>"));
                out.println("Removed " + name + ".");
            }
            default -> printError("Unknown char command \"" + sub + "\". Type \"help\".");
        }
    }

    private void relationship(String sub) {
        switch (sub) {
            case "add" -> {
                String from = ask("From character");
                String to = ask("To character");
                String type = ask("Type (for example sister, rival)");
                String notes = ask("Notes (optional)");
                controller.addRelationship(from, to, type, notes);
                Relationship r = controller.getCharacter(from).relationshipTo(to).orElseThrow();
                out.println(from + " is now " + r + ".");
            }
            case "remove" -> {
                String from = ask("From character");
                String to = ask("To character");
                controller.removeRelationship(from, to);
                out.println("Removed the relationship from " + from + " to " + to + ".");
            }
            default -> printError("Use \"rel add\" or \"rel remove\".");
        }
    }

    // ---- save and load (F11) ----

    private void listSessions() {
        List<String> ids = controller.listSessions();
        if (ids.isEmpty()) {
            out.println("No saved stories.");
        }
        ids.forEach(out::println);
    }

    // ---- input helpers ----

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String ask(String label) {
        out.print(label + ": ");
        out.flush();
        String line = readLine();
        if (line == null) {
            throw new IllegalStateException("Input ended.");
        }
        return line.strip();
    }

    /** Asks for a value, showing the current one; an empty answer keeps it. */
    private String askKeep(String label, String current) {
        String answer = ask(label + " [" + current + "]");
        return answer.isEmpty() ? current : answer;
    }

    private static boolean yes(String answer) {
        return answer.toLowerCase(Locale.ROOT).startsWith("y");
    }

    private static List<String> splitList(String text) {
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** Everything after the first two words, keeping spaces, for names like "Guildmaster Orrin". */
    private static String argAfterSub(String line) {
        String[] parts = line.strip().split("\\s+", 3);
        return parts.length > 2 ? parts[2].strip() : "";
    }

    private static String requireArg(String arg, String usage) {
        if (arg == null || arg.isBlank()) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        return arg.strip();
    }

    /**
     * Builds the controller both front ends use, from .env (or the environment): the save folder, the
     * model and the API key. Without a key the app still opens; every model call then reports the
     * missing key as an error, and the deterministic features keep working.
     */
    static StoryController createController() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        Path saveDir = Path.of(env.get("STORYTELLER_SAVE_DIR", "saves"));
        LLMClient llm;
        try {
            llm = ClaudeAdapter.create(env.get("ANTHROPIC_API_KEY", ""), env.get("STORYTELLER_MODEL", ""));
        } catch (LLMException missingKey) {
            llm = request -> {
                throw missingKey;
            };
        }
        return new StoryController(new JsonSessionRepository(saveDir), StoryAgent.create(llm));
    }

    public static void main(String[] args) {
        StoryController controller = createController();
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StoryCLI cli = new StoryCLI(controller, stdin, System.out);
        controller.addObserver(cli);
        cli.run();
    }
}

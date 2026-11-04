package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lets the model mark the next pivotal event reached, or replace the pending events when the user has
 * taken the story somewhere the plan no longer fits (plot steering, F06). The plan stays hidden: the
 * result goes only to the model, never to a front end. A plot changed during a turn is restored if the
 * turn is undone or fails (SubmitTurnCommand keeps a copy).
 *
 * <p>Features: F06.
 */
public class UpdatePlotTool implements AgentTool {

    /** A revision longer than this is refused, so the story cannot be stretched without end. */
    public static final int MAX_NEW_EVENTS = 6;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String getName() {
        return "update_plot";
    }

    @Override
    public String getDescription() {
        return "Update the hidden plot plan. Use action \"reached\" with event_id once your narration has made "
                + "that pivotal event happen. Use action \"revise\" with new_events only when the user's choices "
                + "have made the pending events impossible or pointless: every pending event is replaced by the "
                + "new ones, which must fit what has already happened. The user never sees the plan.";
    }

    @Override
    public String getSchema() {
        return """
                {"type": "object",
                 "properties": {
                   "action": {"type": "string", "enum": ["reached", "revise"],
                              "description": "\\"reached\\" to mark an event as happened, \\"revise\\" to replace the pending events."},
                   "event_id": {"type": "string", "description": "For \\"reached\\": the id of the pending event, for example \\"E2\\"."},
                   "new_events": {"type": "array", "items": {"type": "string"},
                                  "description": "For \\"revise\\": the new pivotal events in story order, one or two sentences each."}},
                 "required": ["action"]}""";
    }

    /**
     * Marks an event reached or replaces the pending events. Refused (with the plot unchanged) when the
     * story has no plan, the action is unknown, the event is not pending, or a revision is empty, too
     * long, or comes after every event was reached.
     */
    @Override
    public ToolResult execute(Map<String, String> args, StorySession session) {
        Plot plot = session.getPlot();
        if (plot == null) {
            return ToolResult.error("This story has no plot plan to update.");
        }
        String action = args.getOrDefault("action", "").strip().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "reached" -> markReached(plot, args.getOrDefault("event_id", "").strip());
            case "revise" -> revise(plot, args.getOrDefault("new_events", ""));
            default -> ToolResult.error("Unknown action \"" + action + "\". Use \"reached\" or \"revise\".");
        };
    }

    private static ToolResult markReached(Plot plot, String eventId) {
        if (eventId.isEmpty()) {
            return ToolResult.error("Action \"reached\" needs the event_id of a pending event.");
        }
        Optional<PivotalEvent> expected = plot.nextPending();
        plot.markReached(eventId.toUpperCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder("Marked " + eventId.toUpperCase(Locale.ROOT) + " as reached.");
        if (expected.isPresent() && !expected.get().getId().equalsIgnoreCase(eventId)) {
            sb.append(" It was reached ahead of ").append(expected.get().getId()).append(", which is still pending.");
        }
        sb.append(nextLine(plot));
        return ToolResult.ok(sb.toString());
    }

    private static ToolResult revise(Plot plot, String rawEvents) {
        if (plot.nextPending().isEmpty()) {
            return ToolResult.error("Every pivotal event has been reached, so there is nothing to revise. "
                    + "Bring the story towards an ending instead.");
        }
        List<String> descriptions = parseEvents(rawEvents);
        if (descriptions.isEmpty()) {
            return ToolResult.error("Action \"revise\" needs new_events: at least one new pivotal event.");
        }
        if (descriptions.size() > MAX_NEW_EVENTS) {
            return ToolResult.error("A revision can have at most " + MAX_NEW_EVENTS + " events; you sent "
                    + descriptions.size() + ".");
        }
        int next = nextNumber(plot);
        List<PivotalEvent> events = new ArrayList<>();
        for (String d : descriptions) {
            events.add(new PivotalEvent("E" + next++, d));
        }
        plot.replacePending(events);
        return ToolResult.ok("The pending events were replaced by " + events.get(0).getId() + " to "
                + events.get(events.size() - 1).getId() + "." + nextLine(plot));
    }

    private static String nextLine(Plot plot) {
        return plot.nextPending()
                .map(e -> " Next pivotal event (" + e.getId() + "): " + e.getDescription())
                .orElse(" Every pivotal event has been reached; bring the threads together towards an ending.");
    }

    /**
     * The new events from the tool argument: a JSON array of strings (how the adapter passes an array),
     * or plain text with one event per line.
     */
    static List<String> parseEvents(String raw) {
        List<String> found = new ArrayList<>();
        String text = raw == null ? "" : raw.strip();
        if (text.startsWith("[")) {
            try {
                for (JsonNode n : JSON.readTree(text)) {
                    String d = n.isTextual() ? n.asText() : n.path("description").asText("");
                    if (!d.isBlank()) {
                        found.add(d.strip());
                    }
                }
                return found;
            } catch (Exception notJson) {
                found.clear();
            }
        }
        for (String line : text.split("\\R")) {
            String d = line.strip().replaceFirst("^(?:[-*]|\\d+[.)])\\s+", "").strip();
            if (!d.isEmpty()) {
                found.add(d);
            }
        }
        return found;
    }

    /** The next unused event number, after the highest "E" number in the plan (dropped events included). */
    private static int nextNumber(Plot plot) {
        int max = 0;
        for (PivotalEvent e : plot.getEvents()) {
            String digits = e.getId().replaceAll("\\D", "");
            if (!digits.isEmpty() && digits.length() < 9) {
                max = Math.max(max, Integer.parseInt(digits));
            }
        }
        return Math.max(max, plot.getEvents().size()) + 1;
    }
}

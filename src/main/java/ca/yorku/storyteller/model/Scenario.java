package ca.yorku.storyteller.model;

/**
 * Title, premise, tone, setting and opening situation of a story.
 *
 * <p>Features: F01 F05.
 * <p>Patterns: MVC (Model).
 */
public class Scenario {

    private String title;
    private String premise;
    private String tone;
    private String setting;
    private String openingSituation;

    /** For JSON loading only. */
    private Scenario() {
    }

    public Scenario(String title, String premise, String tone, String setting, String openingSituation) {
        this.title = clean(title);
        this.premise = clean(premise);
        this.tone = clean(tone);
        this.setting = clean(setting);
        this.openingSituation = clean(openingSituation);
    }

    /** The scenario as text for the model's context. Empty fields are left out. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        line(sb, "Title", title);
        line(sb, "Premise", premise);
        line(sb, "Tone", tone);
        line(sb, "Setting", setting);
        line(sb, "Opening situation", openingSituation);
        return sb.toString().strip();
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (!value.isEmpty()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    public String getTitle() {
        return title;
    }

    public String getPremise() {
        return premise;
    }

    public String getTone() {
        return tone;
    }

    public String getSetting() {
        return setting;
    }

    public String getOpeningSituation() {
        return openingSituation;
    }
}

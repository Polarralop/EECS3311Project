package ca.yorku.storyteller.llm;

/**
 * A tool the model may call: its name, what it does, and its input as a JSON Schema object.
 *
 * @param name            the tool name the model uses
 * @param description     when and why the model should use it
 * @param inputSchemaJson a JSON Schema object with "properties" and optionally "required"
 */
public record LLMToolSpec(String name, String description, String inputSchemaJson) {
}

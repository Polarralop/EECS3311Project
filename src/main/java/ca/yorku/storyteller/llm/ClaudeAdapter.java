package ca.yorku.storyteller.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates LLMRequest/LLMResponse to and from Anthropic's Messages API (the adaptee, via the
 * anthropic-java SDK). This is the only class in the app that knows about Anthropic.
 *
 * <p>Features: F01 F04-F09 F12.
 * <p>Patterns: Adapter (Adapter).
 */
public class ClaudeAdapter implements LLMClient {

    /** The app's model (Stage 1 design): Claude Sonnet 5.5. */
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnthropicClient client;
    private final String modelName;

    public ClaudeAdapter(AnthropicClient client, String modelName) {
        this.client = client;
        this.modelName = modelName == null || modelName.isBlank() ? DEFAULT_MODEL : modelName;
    }

    /**
     * Builds an adapter with its own HTTP client.
     *
     * @throws LLMException if the key is missing
     */
    public static ClaudeAdapter create(String apiKey, String modelName) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            throw new LLMException("No Anthropic API key is set. Copy .env.example to .env and put your key in ANTHROPIC_API_KEY.");
        }
        return new ClaudeAdapter(AnthropicOkHttpClient.builder().apiKey(apiKey).build(), modelName);
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public LLMResponse generate(LLMRequest request) {
        MessageCreateParams params = toApiBody(request);
        Message message;
        try {
            message = client.messages().create(params);
        } catch (UnauthorizedException e) {
            throw new LLMException("Claude rejected the API key. Check ANTHROPIC_API_KEY in .env.", e);
        } catch (RateLimitException e) {
            throw new LLMException("Claude is rate limiting requests. Wait a moment and try again.", e);
        } catch (AnthropicServiceException e) {
            throw new LLMException("Claude returned an error (HTTP " + e.statusCode() + "). Try again.", e);
        } catch (AnthropicIoException e) {
            throw new LLMException("Could not reach Claude. Check your internet connection.", e);
        } catch (AnthropicException e) {
            throw new LLMException("The call to Claude failed: " + e.getMessage(), e);
        }
        return fromApiBody(message);
    }

    /** Converts the provider-neutral request into the SDK's request type. */
    MessageCreateParams toApiBody(LLMRequest request) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(modelName)
                .maxTokens(request.getMaxTokens());
        if (!request.getSystemPrompt().isBlank()) {
            b.system(request.getSystemPrompt());
        }
        for (LLMToolSpec spec : request.getTools()) {
            b.addTool(toTool(spec));
        }
        for (LLMMessage m : request.getMessages()) {
            b.addMessage(toParam(m));
        }
        return b.build();
    }

    /** Converts the SDK's reply into the provider-neutral response. */
    LLMResponse fromApiBody(Message message) {
        StringBuilder text = new StringBuilder();
        List<LLMToolCall> calls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> {
                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append(t.text());
            });
            block.toolUse().ifPresent(t -> calls.add(toToolCall(t)));
        }
        String stop = message.stopReason().map(StopReason::asString).orElse("");
        boolean refused = message.stopReason().map(StopReason.REFUSAL::equals).orElse(false);
        // Keep the reply exactly as sent, so thinking blocks go back unchanged during a tool-use loop.
        LLMMessage assistant = LLMMessage.assistant(text.toString(), calls, message.toParam());
        return new LLMResponse(text.toString(), refused, calls, stop,
                message.usage().inputTokens(), message.usage().outputTokens(), assistant);
    }

    private MessageParam toParam(LLMMessage m) {
        if (m.getRole() == LLMMessage.Role.ASSISTANT) {
            if (m.getProviderContent() instanceof MessageParam original) {
                return original;
            }
            List<ContentBlockParam> blocks = new ArrayList<>();
            if (!m.getText().isBlank()) {
                blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.getText()).build()));
            }
            for (LLMToolCall call : m.getToolCalls()) {
                ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
                call.arguments().forEach((k, v) -> input.putAdditionalProperty(k, JsonValue.from(v)));
                blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                        .id(call.id()).name(call.name()).input(input.build()).build()));
            }
            return MessageParam.builder().role(MessageParam.Role.ASSISTANT).contentOfBlockParams(blocks).build();
        }
        if (!m.getToolResults().isEmpty()) {
            List<ContentBlockParam> results = new ArrayList<>();
            for (LLMToolResult r : m.getToolResults()) {
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.toolCallId()).content(r.content()).isError(r.error()).build()));
            }
            return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build();
        }
        return MessageParam.builder().role(MessageParam.Role.USER).content(m.getText()).build();
    }

    private static Tool toTool(LLMToolSpec spec) {
        JsonNode schema;
        try {
            schema = JSON.readTree(spec.inputSchemaJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("The schema of tool " + spec.name() + " is not valid JSON.", e);
        }
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        schema.path("properties").properties()
                .forEach(p -> props.putAdditionalProperty(p.getKey(), JsonValue.fromJsonNode(p.getValue())));
        Tool.InputSchema.Builder input = Tool.InputSchema.builder().properties(props.build());
        if (schema.has("required")) {
            input.required(JSON.convertValue(schema.get("required"), new TypeReference<List<String>>() { }));
        }
        return Tool.builder().name(spec.name()).description(spec.description()).inputSchema(input.build()).build();
    }

    /** Tool arguments arrive as JSON; each value becomes text (strings as-is, anything else as JSON). */
    private static LLMToolCall toToolCall(ToolUseBlock block) {
        JsonNode input = JSON.valueToTree(block._input().convert(Object.class));
        Map<String, String> args = new LinkedHashMap<>();
        if (input != null) {
            input.properties().forEach(e -> args.put(e.getKey(),
                    e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
        }
        return new LLMToolCall(block.id(), block.name(), args);
    }
}

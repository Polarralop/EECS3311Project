package ca.yorku.storyteller.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ClaudeAdapter}. No live calls: the conversions are tested directly, and
 * {@code generate} runs against a local HTTP server that plays the Messages API.
 */
class ClaudeAdapterTest {

    private static final String TOOL_SCHEMA = """
            {"type":"object","properties":{"query":{"type":"string","description":"Words to search for"}},
             "required":["query"]}""";

    private static final String TOOL_USE_REPLY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[
               {"type":"thinking","thinking":"","signature":"sig"},
               {"type":"text","text":"Let me check the lore."},
               {"type":"tool_use","id":"toolu_1","name":"search_lore","input":{"query":"ash bell","limit":3}}],
             "stop_reason":"tool_use","stop_sequence":null,
             "usage":{"input_tokens":120,"output_tokens":30}}""";

    private static final String TEXT_REPLY = """
            {"id":"msg_2","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"text","text":"The bell rings once."}],
             "stop_reason":"end_turn","stop_sequence":null,
             "usage":{"input_tokens":200,"output_tokens":12}}""";

    private static final String REFUSAL_REPLY = """
            {"id":"msg_3","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[],"stop_reason":"refusal","stop_sequence":null,
             "usage":{"input_tokens":50,"output_tokens":0}}""";

    private HttpServer server;
    private final List<String> receivedBodies = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static Message parse(String body) throws Exception {
        return ObjectMappers.jsonMapper().readValue(body, Message.class);
    }

    /** Starts a server that answers every POST with the given replies in order. */
    private ClaudeAdapter adapterAgainst(int status, String... replies) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] next = {0};
        server.createContext("/", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = replies[Math.min(next[0]++, replies.length - 1)].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return new ClaudeAdapter(AnthropicOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-key")
                .maxRetries(0)
                .build(), null);
    }

    private static LLMRequest searchRequest() {
        return new LLMRequest("You are a storyteller.",
                List.of(LLMMessage.user("I ring the bell.")),
                List.of(new LLMToolSpec("search_lore", "Search the lore book.", TOOL_SCHEMA)));
    }

    @Test
    void toApiBodyCarriesModelSystemToolsAndMessages() throws Exception {
        ClaudeAdapter adapter = new ClaudeAdapter(null, null);
        MessageCreateParams params = adapter.toApiBody(searchRequest());
        JsonNode body = json.readTree(ObjectMappers.jsonMapper().writeValueAsString(params._body()));

        assertEquals("claude-sonnet-5-5", body.get("model").asText());
        assertEquals(16000, body.get("max_tokens").asInt());
        assertEquals("You are a storyteller.", body.get("system").asText());
        JsonNode tool = body.get("tools").get(0);
        assertEquals("search_lore", tool.get("name").asText());
        assertEquals("string", tool.at("/input_schema/properties/query/type").asText());
        assertEquals("query", tool.at("/input_schema/required/0").asText());
        assertEquals("user", body.at("/messages/0/role").asText());
        assertEquals("I ring the bell.", body.at("/messages/0/content").asText());
    }

    @Test
    void fromApiBodyReadsTextToolCallsAndUsage() throws Exception {
        LLMResponse r = new ClaudeAdapter(null, null).fromApiBody(parse(TOOL_USE_REPLY));

        assertEquals("Let me check the lore.", r.getText());
        assertTrue(r.hasToolCalls());
        LLMToolCall call = r.getToolCalls().get(0);
        assertEquals("toolu_1", call.id());
        assertEquals("search_lore", call.name());
        assertEquals(Map.of("query", "ash bell", "limit", "3"), call.arguments());
        assertEquals("tool_use", r.getStopReason());
        assertEquals(120, r.getInputTokens());
        assertEquals(30, r.getOutputTokens());
        assertFalse(r.isRefused());
        assertTrue(r.getAssistantMessage().getProviderContent() instanceof MessageParam);
    }

    @Test
    void refusalIsFlagged() throws Exception {
        LLMResponse r = new ClaudeAdapter(null, null).fromApiBody(parse(REFUSAL_REPLY));
        assertTrue(r.isRefused());
        assertEquals("", r.getText());
    }

    @Test
    void toolLoopSendsAssistantReplyUnchangedAndToolResultsBack() throws Exception {
        ClaudeAdapter adapter = adapterAgainst(200, TOOL_USE_REPLY, TEXT_REPLY);
        LLMRequest first = searchRequest();
        LLMResponse r1 = adapter.generate(first);

        LLMRequest second = first.withMessages(r1.getAssistantMessage(), LLMMessage.toolResults(
                List.of(new LLMToolResult("toolu_1", "L2 The Ash Bell: rung when the mountain stirs.", false))));
        LLMResponse r2 = adapter.generate(second);

        assertEquals("The bell rings once.", r2.getText());
        assertFalse(r2.hasToolCalls());
        JsonNode sent = json.readTree(receivedBodies.get(1));
        JsonNode assistant = sent.at("/messages/1");
        assertEquals("assistant", assistant.get("role").asText());
        assertEquals("thinking", assistant.at("/content/0/type").asText());
        assertEquals("sig", assistant.at("/content/0/signature").asText());
        assertEquals("tool_use", assistant.at("/content/2/type").asText());
        JsonNode result = sent.at("/messages/2/content/0");
        assertEquals("tool_result", result.get("type").asText());
        assertEquals("toolu_1", result.get("tool_use_id").asText());
        assertFalse(result.get("is_error").asBoolean());
    }

    @Test
    void assistantMessageWithoutProviderContentIsRebuilt() throws Exception {
        ClaudeAdapter adapter = new ClaudeAdapter(null, null);
        LLMToolCall call = new LLMToolCall("toolu_9", "search_lore", Map.of("query", "bell"));
        LLMRequest req = searchRequest().withMessages(LLMMessage.assistant("Checking.", List.of(call), null),
                LLMMessage.toolResults(List.of(new LLMToolResult("toolu_9", "Nothing found.", true))));
        JsonNode body = json.readTree(ObjectMappers.jsonMapper().writeValueAsString(adapter.toApiBody(req)._body()));
        assertEquals("Checking.", body.at("/messages/1/content/0/text").asText());
        assertEquals("bell", body.at("/messages/1/content/1/input/query").asText());
        assertTrue(body.at("/messages/2/content/0/is_error").asBoolean());
    }

    @Test
    void httpErrorsBecomeLLMExceptionsWithReadableMessages() throws Exception {
        String error = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        ClaudeAdapter adapter = adapterAgainst(401, error);
        LLMException e = assertThrows(LLMException.class, () -> adapter.generate(searchRequest()));
        assertTrue(e.getMessage().contains("API key"));
    }

    @Test
    void serverErrorIsReported() throws Exception {
        String error = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}";
        ClaudeAdapter adapter = adapterAgainst(500, error);
        LLMException e = assertThrows(LLMException.class, () -> adapter.generate(searchRequest()));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void missingKeyIsReportedBeforeAnyCall() {
        assertThrows(LLMException.class, () -> ClaudeAdapter.create("", null));
        assertThrows(LLMException.class, () -> ClaudeAdapter.create("your-api-key-here", null));
    }

    @Test
    void requestValidatesItsMessages() {
        assertThrows(IllegalArgumentException.class, () -> new LLMRequest("s", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LLMRequest("s", List.of(LLMMessage.assistant("hi", List.of(), null)), List.of()));
    }

    @Test
    void fakeClientReturnsScriptedResponsesAndRecordsRequests() {
        FakeLLMClient fake = new FakeLLMClient()
                .enqueue(LLMResponse.ofText("Once upon a time."))
                .enqueueFailure(new LLMException("offline"));
        LLMRequest req = searchRequest();
        assertEquals("Once upon a time.", fake.generate(req).getText());
        assertThrows(LLMException.class, () -> fake.generate(req));
        assertThrows(IllegalStateException.class, () -> fake.generate(req));
        assertEquals(3, fake.getRequests().size());
        assertSame(req, fake.getRequests().get(0));
    }
}

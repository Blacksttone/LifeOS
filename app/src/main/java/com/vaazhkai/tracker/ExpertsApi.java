package com.vaazhkai.tracker;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.WebSearchResultBlock;
import com.anthropic.models.messages.WebSearchTool20260209;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asks Claude, as one of the app's experts, with live web search so answers come with sources.
 * Plain Java (no Android types) so it can be compiled and checked on its own.
 */
final class ExpertsApi {
    static final String MODEL = "claude-opus-5-5";

    private ExpertsApi() {}

    /**
     * @param history JSON array of {role: "user"|"assistant", text}
     * @return JSON {ok, text, sources:[{title,url}], error}
     */
    static String ask(String apiKey, String system, String history) {
        JSONObject out = new JSONObject();
        try {
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(Duration.ofMinutes(4))
                    .maxRetries(2)
                    .build();

            MessageCreateParams.Builder b = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(16000L)
                    .system(system)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                    .addTool(WebSearchTool20260209.builder().maxUses(5L).build())
                    // If a safety classifier declines, let the API retry on a fallback model.
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));

            JSONArray msgs = new JSONArray(history);
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.getJSONObject(i);
                String text = m.optString("text", "");
                if (text.isEmpty()) continue;
                if ("assistant".equals(m.optString("role"))) b.addAssistantMessage(text);
                else b.addUserMessage(text);
            }

            StringBuilder text = new StringBuilder();
            Map<String, String> sources = new LinkedHashMap<>();
            Message r = null;
            // Server-side web search can pause a long turn; resume it a few times.
            for (int turn = 0; turn < 4; turn++) {
                r = client.messages().create(b.build());
                for (ContentBlock block : r.content()) {
                    block.text().ifPresent(t -> {
                        text.append(t.text());
                        t.citations().ifPresent(list -> {
                            for (TextCitation c : list) {
                                c.webSearchResultLocation().ifPresent(w -> sources.putIfAbsent(w.url(), w.title().orElse(w.url())));
                            }
                        });
                    });
                    block.webSearchToolResult().ifPresent(res -> res.content().resultBlocks().ifPresent(list -> {
                        for (WebSearchResultBlock w : list) sources.putIfAbsent(w.url(), w.title());
                    }));
                }
                if (r.stopReason().map(s -> s.equals(StopReason.PAUSE_TURN)).orElse(false)) {
                    b.addMessage(r);
                    continue;
                }
                break;
            }

            if (r != null && r.stopReason().map(s -> s.equals(StopReason.REFUSAL)).orElse(false) && text.length() == 0) {
                out.put("ok", false).put("error", "This question couldn't be answered. Try asking it differently.");
                return out.toString();
            }

            JSONArray src = new JSONArray();
            for (Map.Entry<String, String> e : sources.entrySet()) {
                if (src.length() >= 8) break;
                src.put(new JSONObject().put("url", e.getKey()).put("title", e.getValue()));
            }
            out.put("ok", true).put("text", text.toString().trim()).put("sources", src);
        } catch (UnauthorizedException e) {
            put(out, "The API key isn't valid. Check it in Experts → Settings.");
        } catch (RateLimitException e) {
            put(out, "Too many requests right now. Wait a minute and try again.");
        } catch (AnthropicServiceException e) {
            put(out, e.statusCode() == 402 || e.statusCode() == 403
                    ? "Your Anthropic account can't make this request. Check billing and credits in the Anthropic Console."
                    : "The service had a problem (" + e.statusCode() + "). Try again.");
        } catch (Exception e) {
            put(out, "Couldn't reach the expert. Check your internet connection.");
        }
        return out.toString();
    }

    private static void put(JSONObject o, String msg) {
        try { o.put("ok", false).put("error", msg); } catch (Exception ignored) {}
    }
}

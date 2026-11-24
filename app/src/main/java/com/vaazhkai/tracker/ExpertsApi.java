package com.vaazhkai.tracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asks Claude, as one of the app's experts, with live web search so answers come with sources.
 * Plain HTTPS + org.json (no SDK) to keep the APK small.
 */
final class ExpertsApi {
    static final String MODEL = "claude-opus-5-5";
    private static final String URL_MESSAGES = "https://api.anthropic.com/v1/messages";

    private ExpertsApi() {}

    /**
     * @param history JSON array of {role: "user"|"assistant", text}
     * @return JSON {ok, text, sources:[{title,url}], error}
     */
    static String ask(String apiKey, String system, String history) {
        JSONObject out = new JSONObject();
        try {
            JSONArray messages = new JSONArray();
            JSONArray msgs = new JSONArray(history);
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.getJSONObject(i);
                String text = m.optString("text", "");
                if (text.isEmpty()) continue;
                messages.put(new JSONObject().put("role", "assistant".equals(m.optString("role")) ? "assistant" : "user").put("content", text));
            }

            JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 16000)
                    .put("system", system)
                    .put("output_config", new JSONObject().put("effort", "medium"))
                    .put("tools", new JSONArray().put(new JSONObject()
                            .put("type", "web_search_20260209").put("name", "web_search").put("max_uses", 5)))
                    // If a safety classifier declines, let the API retry on a fallback model.
                    .put("fallbacks", "default")
                    .put("messages", messages);

            StringBuilder text = new StringBuilder();
            Map<String, String> sources = new LinkedHashMap<>();
            String stop = "";
            // Server-side web search can pause a long turn; resume it a few times.
            for (int turn = 0; turn < 4; turn++) {
                int[] code = new int[1];
                String resp = post(apiKey, body.toString(), code);
                if (code[0] != 200) { put(out, errorFor(code[0])); return out.toString(); }
                JSONObject r = new JSONObject(resp);
                JSONArray content = r.optJSONArray("content");
                if (content == null) content = new JSONArray();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject block = content.getJSONObject(i);
                    String type = block.optString("type");
                    if ("text".equals(type)) {
                        text.append(block.optString("text"));
                        JSONArray cites = block.optJSONArray("citations");
                        if (cites != null) for (int c = 0; c < cites.length(); c++) {
                            JSONObject ci = cites.getJSONObject(c);
                            String u = ci.optString("url", "");
                            if (!u.isEmpty()) sources.putIfAbsent(u, ci.optString("title", u));
                        }
                    } else if ("web_search_tool_result".equals(type)) {
                        JSONArray res = block.optJSONArray("content");
                        if (res != null) for (int c = 0; c < res.length(); c++) {
                            JSONObject w = res.getJSONObject(c);
                            String u = w.optString("url", "");
                            if (!u.isEmpty()) sources.putIfAbsent(u, w.optString("title", u));
                        }
                    }
                }
                stop = r.optString("stop_reason", "");
                if ("pause_turn".equals(stop)) {
                    messages.put(new JSONObject().put("role", "assistant").put("content", content));
                    continue;
                }
                break;
            }

            if ("refusal".equals(stop) && text.length() == 0) {
                put(out, "This question couldn't be answered. Try asking it differently.");
                return out.toString();
            }

            JSONArray src = new JSONArray();
            for (Map.Entry<String, String> e : sources.entrySet()) {
                if (src.length() >= 8) break;
                src.put(new JSONObject().put("url", e.getKey()).put("title", e.getValue()));
            }
            out.put("ok", true).put("text", text.toString().trim()).put("sources", src);
        } catch (Exception e) {
            put(out, "Couldn't reach the expert. Check your internet connection.");
        }
        return out.toString();
    }

    private static String errorFor(int code) {
        if (code == 401) return "The API key isn't valid. Check it in Experts.";
        if (code == 429) return "Too many requests right now. Wait a minute and try again.";
        if (code == 402 || code == 403) return "Your Anthropic account can't make this request. Check billing and credits in the Anthropic Console.";
        return "The service had a problem (" + code + "). Try again.";
    }

    private static String post(String apiKey, String json, int[] code) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_MESSAGES).openConnection();
            try {
                c.setRequestMethod("POST");
                c.setConnectTimeout(20000);
                c.setReadTimeout(240000);
                c.setDoOutput(true);
                c.setRequestProperty("content-type", "application/json");
                c.setRequestProperty("x-api-key", apiKey);
                c.setRequestProperty("anthropic-version", "2023-06-01");
                c.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");
                try (OutputStream o = c.getOutputStream()) { o.write(json.getBytes(StandardCharsets.UTF_8)); }
                int status = c.getResponseCode();
                InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                String resp = read(in);
                if ((status == 429 || status == 529 || status >= 500) && attempt < 2) {
                    Thread.sleep(2000L * (attempt + 1));
                    continue;
                }
                code[0] = status;
                return resp;
            } catch (java.io.IOException e) {
                last = e;
                Thread.sleep(2000L * (attempt + 1));
            } finally {
                c.disconnect();
            }
        }
        throw last != null ? last : new Exception("failed");
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private static void put(JSONObject o, String msg) {
        try { o.put("ok", false).put("error", msg); } catch (Exception ignored) {}
    }
}

package com.inkbook.reader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** Word definitions via the Wiktionary REST API (online, graceful offline error). */
public final class Dict {

    public interface Callback {
        void onResult(ArrayList<String> lines);
        void onError(String msg);
    }

    private Dict() { }

    public static void lookup(final String rawWord, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String w = rawWord == null ? "" : rawWord.trim();
                    w = w.replaceAll("^[^A-Za-z']+|[^A-Za-z']+$", "").toLowerCase();
                    if (w.length() == 0) {
                        cb.onError("Nothing selected.");
                        return;
                    }
                    URL u = new URL("https://en.wiktionary.org/api/rest_v1/page/definition/"
                            + URLEncoder.encode(w, "UTF-8"));
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(7000);
                    conn.setReadTimeout(9000);
                    conn.setRequestProperty("Api-User-Agent", "InkBook/1.0 (e-book reader)");
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        cb.onError("No definition found for \u201C" + rawWord.trim() + "\u201D.");
                        return;
                    }
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();

                    JSONObject root = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
                    JSONArray lang = root.optJSONArray("en");
                    if (lang == null) {
                        JSONArray names = root.names();
                        if (names != null && names.length() > 0) {
                            lang = root.optJSONArray(names.optString(0));
                        }
                    }
                    if (lang == null) {
                        cb.onError("No definition found for \u201C" + rawWord.trim() + "\u201D.");
                        return;
                    }
                    ArrayList<String> out = new ArrayList<String>();
                    int added = 0;
                    for (int i = 0; i < lang.length() && added < 6; i++) {
                        JSONObject entry = lang.optJSONObject(i);
                        if (entry == null) continue;
                        String pos = entry.optString("partOfSpeech", "");
                        JSONArray defs = entry.optJSONArray("definitions");
                        if (defs == null) continue;
                        int perPos = 0;
                        for (int j = 0; j < defs.length() && added < 6 && perPos < 2; j++) {
                            JSONObject d = defs.optJSONObject(j);
                            if (d == null) continue;
                            String def = d.optString("definition", "");
                            if (def == null || def.length() == 0) continue;
                            String text = stripHtml(def);
                            if (text.length() == 0) continue;
                            String line = (pos.length() > 0 && perPos == 0 ? "[" + pos + "] " : "") + text;
                            out.add(line);
                            added++;
                            perPos++;
                        }
                    }
                    if (out.isEmpty()) {
                        cb.onError("No definition found for \u201C" + rawWord.trim() + "\u201D.");
                    } else {
                        cb.onResult(out);
                    }
                } catch (Exception e) {
                    cb.onError("Dictionary unreachable - check your connection.");
                } finally {
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception ignored) { }
                    }
                }
            }
        }).start();
    }

    private static String stripHtml(String html) {
        String s = html.replaceAll("(?is)<[^>]+>", " ");
        s = Util.htmlToText(s);
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 280) s = s.substring(0, 277) + "...";
        return s;
    }
}

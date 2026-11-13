package com.inkbook.reader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Settings, library and reading positions persisted in SharedPreferences. */
public final class Prefs {

    public static class BookEntry {
        public String id = "";
        public String title = "";
        public String fileName = "";
        public String format = "txt";
        public long addedAt;
        public long lastReadAt;
        public int lastPara = -1;
        public int lastPage = 0;
        public int totalPages = 0;
    }

    public static class Settings {
        public int fontSize = 18;
        public float lineSpacing = 1.35f;
        public int typeface = 0;   // 0 sans, 1 serif, 2 mono
        public int theme = 0;      // 0 gray, 1 white, 2 night
        public boolean justify = true;
        public boolean flash = true;
        public boolean haptic = true;
    }

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("inkbook", Context.MODE_PRIVATE);
    }

    public Settings settings() {
        Settings s = new Settings();
        try {
            String raw = sp.getString("settings", "{}");
            JSONObject o = new JSONObject(raw);
            s.fontSize = o.optInt("fs", 18);
            s.lineSpacing = (float) o.optDouble("ls", 1.35);
            s.typeface = o.optInt("tf", 0);
            s.theme = o.optInt("th", 0);
            s.justify = o.optBoolean("ju", true);
            s.flash = o.optBoolean("fl", true);
            s.haptic = o.optBoolean("ha", true);
        } catch (Exception ignored) { }
        return s;
    }

    public void saveSettings(Settings s) {
        try {
            JSONObject o = new JSONObject();
            o.put("fs", s.fontSize);
            o.put("ls", (double) s.lineSpacing);
            o.put("tf", s.typeface);
            o.put("th", s.theme);
            o.put("ju", s.justify);
            o.put("fl", s.flash);
            o.put("ha", s.haptic);
            sp.edit().putString("settings", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    public List<BookEntry> library() {
        List<BookEntry> out = new ArrayList<BookEntry>();
        try {
            String raw = sp.getString("library", "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                BookEntry e = new BookEntry();
                e.id = o.optString("id");
                e.title = o.optString("title");
                e.fileName = o.optString("file");
                e.format = o.optString("fmt", "txt");
                e.addedAt = o.optLong("added");
                e.lastReadAt = o.optLong("read");
                e.lastPara = o.optInt("para", -1);
                e.lastPage = o.optInt("page", 0);
                e.totalPages = o.optInt("total", 0);
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveLibrary(List<BookEntry> list) {
        try {
            JSONArray a = new JSONArray();
            for (BookEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("title", e.title);
                o.put("file", e.fileName);
                o.put("fmt", e.format);
                o.put("added", e.addedAt);
                o.put("read", e.lastReadAt);
                o.put("para", e.lastPara);
                o.put("page", e.lastPage);
                o.put("total", e.totalPages);
                a.put(o);
            }
            sp.edit().putString("library", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** @return {lastPara, lastPage, totalPages} */
    public int[] position(String bookId) {
        try {
            String raw = sp.getString("pos_" + bookId, "");
            if (raw == null || raw.length() < 2) return new int[]{-1, 0, 0};
            JSONObject o = new JSONObject(raw);
            return new int[]{o.optInt("p", -1), o.optInt("pg", 0), o.optInt("t", 0)};
        } catch (Exception e) {
            return new int[]{-1, 0, 0};
        }
    }

    public void savePosition(String bookId, int para, int page, int total) {
        try {
            JSONObject o = new JSONObject();
            o.put("p", para);
            o.put("pg", page);
            o.put("t", total);
            sp.edit().putString("pos_" + bookId, o.toString()).apply();
        } catch (Exception ignored) { }
    }
}

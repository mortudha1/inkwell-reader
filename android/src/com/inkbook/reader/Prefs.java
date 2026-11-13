package com.inkbook.reader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Settings, library, reading positions, per-book typography and marks. */
public final class Prefs {

    public static class BookEntry {
        public String id = "";
        public String title = "";
        public String author = "";
        public String fileName = "";
        public String format = "txt";
        public String shelf = "";      // "" = not on a bookshelf
        public boolean favorite = false;
        public long addedAt;
        public long lastReadAt;
        public int lastPara = -1;
        public int lastPage = 0;
        public int totalPages = 0;
    }

    /** Global display preferences (shared with the library screen). */
    public static class Settings {
        public int fontSize = 20;       // legacy, typography is per-book now
        public float lineSpacing = 1.5f;
        public int typeface = 1;
        public int theme = 0;           // 0 warm paper, 1 white, 2 dark
        public boolean justify = true;
        public boolean flash = false;   // full-screen refresh on page turn
        public boolean haptic = true;
        public float brightness = 0f;   // 0 = follow system
    }

    /** Typography remembered individually for each book. */
    public static class BookSettings {
        public int fontSize = 20;         // sp, comfortable tablet size
        public float lineSpacing = 1.5f;  // 1.2 .. 2.0
        public float paraSpacing = 0.35f; // 0 .. 1 paragraph gap amount
        public int margin = 1;            // 0 narrow .. 3 very wide
        public int typeface = 1;          // 0 sans, 1 serif, 2 mono
        public boolean justify = true;
    }

    /** Bookmark / highlight / note anchored to a paragraph. */
    public static class Mark {
        public long id;
        public int kind;      // 0 bookmark, 1 highlight, 2 note
        public int para;
        public int page;      // page number at creation time (display only)
        public long at;
        public String text = "";

        public Mark() { }

        public Mark(long id, int kind, int para, int page, long at, String text) {
            this.id = id;
            this.kind = kind;
            this.para = para;
            this.page = page;
            this.at = at;
            this.text = text == null ? "" : text;
        }
    }

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("inkbook", Context.MODE_PRIVATE);
    }

    // ----------------------------------------------------------- settings

    public Settings settings() {
        Settings s = new Settings();
        try {
            String raw = sp.getString("settings", "{}");
            JSONObject o = new JSONObject(raw);
            s.fontSize = o.optInt("fs", 20);
            s.lineSpacing = (float) o.optDouble("ls", 1.5);
            s.typeface = o.optInt("tf", 1);
            s.theme = o.optInt("th", 0);
            s.justify = o.optBoolean("ju", true);
            s.flash = o.optBoolean("fl", false);
            s.haptic = o.optBoolean("ha", true);
            s.brightness = (float) o.optDouble("br", 0.0);
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
            o.put("br", (double) s.brightness);
            sp.edit().putString("settings", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Per-book typography (falls back to defaults for a new book). */
    public BookSettings bookSettings(String bookId) {
        BookSettings b = new BookSettings();
        if (bookId == null) return b;
        try {
            String raw = sp.getString("bset_" + bookId, null);
            if (raw == null) {
                // migrate: first read of a legacy book inherits global typography
                JSONObject g = new JSONObject(sp.getString("settings", "{}"));
                if (g.has("fs") || g.has("ls") || g.has("tf") || g.has("ju")) {
                    b.fontSize = g.optInt("fs", 20);
                    b.lineSpacing = (float) g.optDouble("ls", 1.5);
                    b.typeface = g.optInt("tf", 1);
                    b.justify = g.optBoolean("ju", true);
                }
                return b;
            }
            JSONObject o = new JSONObject(raw);
            b.fontSize = o.optInt("fs", 20);
            b.lineSpacing = (float) o.optDouble("ls", 1.5);
            b.paraSpacing = (float) o.optDouble("ps", 0.35);
            b.margin = o.optInt("mg", 1);
            b.typeface = o.optInt("tf", 1);
            b.justify = o.optBoolean("ju", true);
        } catch (Exception ignored) { }
        return b;
    }

    public void saveBookSettings(String bookId, BookSettings b) {
        if (bookId == null) return;
        try {
            JSONObject o = new JSONObject();
            o.put("fs", b.fontSize);
            o.put("ls", (double) b.lineSpacing);
            o.put("ps", (double) b.paraSpacing);
            o.put("mg", b.margin);
            o.put("tf", b.typeface);
            o.put("ju", b.justify);
            sp.edit().putString("bset_" + bookId, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------- library

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
                e.author = o.optString("author");
                e.fileName = o.optString("file");
                e.format = o.optString("fmt", "txt");
                e.shelf = o.optString("shelf");
                e.favorite = o.optBoolean("fav", false);
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
                o.put("author", e.author);
                o.put("file", e.fileName);
                o.put("fmt", e.format);
                o.put("shelf", e.shelf);
                o.put("fav", e.favorite);
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

    /** Named bookshelves (collections). */
    public List<String> shelves() {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(sp.getString("shelves", "[]"));
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "");
                if (s.length() > 0) out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveShelves(List<String> shelves) {
        try {
            JSONArray a = new JSONArray();
            for (String s : shelves) {
                if (s != null && s.trim().length() > 0) a.put(s.trim());
            }
            sp.edit().putString("shelves", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ position

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

    // ---------------------------------------------------------------- marks

    /** Bookmarks, highlights and notes for one book, in paragraph order. */
    public List<Mark> marks(String bookId) {
        List<Mark> out = new ArrayList<Mark>();
        if (bookId == null) return out;
        try {
            JSONArray a = new JSONArray(sp.getString("marks_" + bookId, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Mark m = new Mark();
                m.id = o.optLong("id");
                m.kind = o.optInt("k", 0);
                m.para = o.optInt("p", 0);
                m.page = o.optInt("pg", 0);
                m.at = o.optLong("at", 0);
                m.text = o.optString("t", "");
                out.add(m);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveMarks(String bookId, List<Mark> list) {
        if (bookId == null) return;
        try {
            JSONArray a = new JSONArray();
            for (Mark m : list) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("k", m.kind);
                o.put("p", m.para);
                o.put("pg", m.page);
                o.put("at", m.at);
                o.put("t", m.text);
                a.put(o);
            }
            sp.edit().putString("marks_" + bookId, a.toString()).apply();
        } catch (Exception ignored) { }
    }
}

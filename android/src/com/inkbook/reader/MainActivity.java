package com.inkbook.reader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class MainActivity extends Activity {

    private static final int REQ_OPEN = 41;

    private Prefs prefs;
    private LibraryView libraryView;
    private ReaderView readerView;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        ensureSampleBook();
        libraryView = new LibraryView(this, new LibraryView.Host() {
            public void onOpenBook(Prefs.BookEntry e) {
                openBook(e);
            }

            public void onImport() {
                pickFile();
            }

            public void onDeleteBook(Prefs.BookEntry e) {
                deleteBook(e);
            }
        });
        showLibrary();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        if (readerView != null) applyImmersive();
        else applyNormalChrome();
    }

    /** Full immersive (hidden status/navigation bars) - reading screen only. */
    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Normal app chrome for the library: visible light status/navigation bars. */
    private void applyNormalChrome() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try {
            getWindow().setStatusBarColor(0xFFF7F6F3);
            getWindow().setNavigationBarColor(0xFFF7F6F3);
        } catch (Exception ignored) { }
    }

    private void showLibrary() {
        readerView = null;
        setContentView(libraryView);
        libraryView.refresh();
        applyNormalChrome();
    }

    // ------------------------------------------------------------ sample

    private void ensureSampleBook() {
        if (!prefs.library().isEmpty()) return;
        try {
            File dir = new File(getFilesDir(), "books");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "sample-althe-villa-rose.txt");
            if (!f.exists()) {
                InputStream in = getAssets().open("sample.txt");
                OutputStream os = new FileOutputStream(f);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                in.close();
                os.close();
            }
            Prefs.BookEntry e = new Prefs.BookEntry();
            e.id = "sample-villa-rose";
            e.title = "Althe Villa Rose (Sample)";
            e.fileName = f.getName();
            e.format = "txt";
            e.addedAt = System.currentTimeMillis();
            List<Prefs.BookEntry> list = prefs.library();
            list.add(e);
            prefs.saveLibrary(list);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ books

    private void openBook(final Prefs.BookEntry entry) {
        final File f = new File(getFilesDir(), "books/" + entry.fileName);
        if (!f.exists()) {
            Toast.makeText(this, "Book file is missing", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Opening \u201C" + entry.title + "\u201D", Toast.LENGTH_SHORT).show();
        final int[] pos = prefs.position(entry.id);
        new Thread(new Runnable() {
            public void run() {
                final Book book = new Book();
                boolean ok = book.load(f);
                final boolean ready = ok && book.paragraphs.size() > 0;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!ready) {
                            Toast.makeText(MainActivity.this,
                                    "Could not read this book", Toast.LENGTH_LONG).show();
                            return;
                        }
                        readerView = new ReaderView(MainActivity.this, book, entry, prefs,
                                pos[0], new ReaderView.CloseListener() {
                            public void onClose() {
                                persistEntry(entry);
                                showLibrary();
                            }
                        });
                        setContentView(readerView);
                        applyImmersive();
                    }
                });
            }
        }).start();
    }

    private void persistEntry(Prefs.BookEntry entry) {
        List<Prefs.BookEntry> list = prefs.library();
        for (Prefs.BookEntry e : list) {
            if (e.id != null && e.id.equals(entry.id)) {
                e.lastPara = entry.lastPara;
                e.lastPage = entry.lastPage;
                e.totalPages = entry.totalPages;
                e.lastReadAt = entry.lastReadAt;
                break;
            }
        }
        prefs.saveLibrary(list);
    }

    private void deleteBook(Prefs.BookEntry entry) {
        File f = new File(getFilesDir(), "books/" + entry.fileName);
        if (f.exists()) f.delete();
        List<Prefs.BookEntry> list = prefs.library();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id != null && list.get(i).id.equals(entry.id)) {
                list.remove(i);
                break;
            }
        }
        prefs.saveLibrary(list);
        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
        libraryView.refresh();
    }

    // ------------------------------------------------------------ import

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"text/plain", "application/epub+zip", "application/octet-stream"});
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            Toast.makeText(this, "File picker unavailable", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        importBook(data.getData());
    }

    private void importBook(Uri uri) {
        String name = queryName(uri);
        if (name == null || name.length() == 0) name = "book";
        String lower = name.toLowerCase();
        final String fmt;
        if (lower.endsWith(".epub")) fmt = "epub";
        else if (lower.endsWith(".txt")) fmt = "txt";
        else {
            Toast.makeText(this, "Only .txt and .epub files are supported", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File dir = new File(getFilesDir(), "books");
            if (!dir.exists()) dir.mkdirs();
            String fname = "bk_" + Long.toHexString(System.currentTimeMillis())
                    + (fmt.equals("epub") ? ".epub" : ".txt");
            File out = new File(dir, fname);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IllegalStateException("no stream");
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.close();

            Prefs.BookEntry e = new Prefs.BookEntry();
            e.id = "bk" + Long.toHexString(System.nanoTime());
            e.title = name.replaceAll("\\.(txt|epub)$", "");
            e.fileName = fname;
            e.format = fmt;
            e.addedAt = System.currentTimeMillis();

            List<Prefs.BookEntry> list = prefs.library();
            list.add(0, e);
            prefs.saveLibrary(list);
            Toast.makeText(this, "Imported: " + e.title, Toast.LENGTH_SHORT).show();
            libraryView.refresh();
        } catch (Exception ex) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_LONG).show();
        }
    }

    private String queryName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) { }
        finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (readerView != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (readerView.onBack()) return true;
                persistEntry(readerView.entry());
                showLibrary();
                return true;
            }
            if (readerView.handleKey(keyCode)) return true;
            return super.onKeyDown(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long now = System.currentTimeMillis();
            if (now - lastBack < 2200) {
                finish();
                return true;
            }
            lastBack = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

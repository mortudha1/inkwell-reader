package com.inkbook.reader;

import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * Static pagination engine: wraps every paragraph once (background thread),
 * records page-start anchors, then re-lays out single pages on demand for
 * rendering and hit-testing. Memory stays proportional to the book text.
 */
public final class Paginator {

    public static class Word {
        public String text;
        public float x0, x1;
    }

    public static class Line {
        public float y;      // baseline, relative to text column top
        public boolean heading;
        public int para;
        public final ArrayList<Word> words = new ArrayList<Word>();
    }

    public static class Page {
        public int index, total;
        public int startPara, endPara;
        public final ArrayList<Line> lines = new ArrayList<Line>();
    }

    public interface Progress {
        void onProgress(int percent);
        void onDone();
    }

    public static class LineToks {
        public final ArrayList<String> words = new ArrayList<String>();
        public float w;
    }

    private final Book book;
    private Paint paint;
    private Paint headPaint;
    private float bodyAscent, headAscent;
    private float maxW, maxH, indent, lineH, headH, paraGap;
    private boolean justify;
    private final ArrayList<LineToks> scratch = new ArrayList<LineToks>();
    private final List<int[]> pageStarts = new ArrayList<int[]>();
    private volatile boolean ready = false;

    public Paginator(Book book) {
        this.book = book;
    }

    public void configure(int width, int height, float fontPx, float spacingMult,
                          int typeface, boolean justifyText) {
        this.justify = justifyText;
        Typeface tf = Typeface.SANS_SERIF;
        if (typeface == 1) tf = Typeface.SERIF;
        else if (typeface == 2) tf = Typeface.MONOSPACE;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(tf);
        paint.setTextSize(fontPx);
        headPaint = new Paint(paint);
        headPaint.setFakeBoldText(true);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float fh = fm.descent - fm.ascent;
        bodyAscent = fm.ascent;
        Paint.FontMetrics hfm = headPaint.getFontMetrics();
        headAscent = hfm.ascent;
        lineH = fh * spacingMult;
        headH = (hfm.descent - hfm.ascent) * spacingMult;
        paraGap = lineH * 0.34f;
        indent = fontPx * 1.4f;
        maxW = Math.max(40f, width);
        maxH = Math.max(lineH, height);
        ready = false;
    }

    public Paint bodyPaint() { return paint; }
    public Paint headingPaint() { return headPaint; }
    public float bodyAscent() { return bodyAscent; }
    public float headAscent() { return headAscent; }
    public float lineHeight() { return lineH; }
    public boolean isReady() { return ready; }

    public int pageCount() {
        synchronized (pageStarts) {
            return ready ? pageStarts.size() - 1 : 0;
        }
    }

    public int pageOfPara(int para) {
        synchronized (pageStarts) {
            if (!ready) return 0;
            for (int i = pageStarts.size() - 2; i >= 0; i--) {
                if (pageStarts.get(i)[0] <= para) return i;
            }
            return 0;
        }
    }

    /** Runs on a worker thread. Anchors are {paraIndex, lineInPara} per page start. */
    public void paginate(Progress cb) {
        ArrayList<int[]> starts = new ArrayList<int[]>();
        int n = book.paragraphs.size();
        starts.add(new int[]{0, 0});
        float y = 0;
        boolean freshPage = true;
        for (int para = 0; para < n; para++) {
            boolean heading = book.headings.contains(para);
            if (heading && !freshPage && para > 0) {
                starts.add(new int[]{para, 0});
                y = 0;
                freshPage = true;
            }
            wrap(book.paragraphs.get(para), heading, scratch);
            int lc = scratch.isEmpty() ? 1 : scratch.size();
            float h = heading ? headH : lineH;
            for (int ln = 0; ln < lc; ln++) {
                if (y + h > maxH) {
                    starts.add(new int[]{para, ln});
                    y = 0;
                    freshPage = true;
                }
                y += h;
                if (heading && ln == 0) y += paraGap * 0.5f;
            }
            y += paraGap;
            freshPage = false;
            if (cb != null && para % 500 == 499) {
                cb.onProgress((para + 1) * 100 / Math.max(1, n));
            }
        }
        starts.add(new int[]{n, 0});
        synchronized (pageStarts) {
            pageStarts.clear();
            pageStarts.addAll(starts);
        }
        ready = true;
        if (cb != null) cb.onDone();
    }

    /** Renders one page on the UI thread using stored anchors. */
    public Page render(int idx) {
        if (!ready) return null;
        int pc;
        synchronized (pageStarts) { pc = pageStarts.size() - 1; }
        if (idx < 0 || idx >= pc) return null;

        int sp, sl, ep, el;
        synchronized (pageStarts) {
            sp = pageStarts.get(idx)[0];
            sl = pageStarts.get(idx)[1];
            ep = pageStarts.get(idx + 1)[0];
            el = pageStarts.get(idx + 1)[1];
        }

        Page pg = new Page();
        pg.index = idx;
        pg.total = pc;
        pg.startPara = sp;

        float y = 0;
        int para = sp;
        int ln = sl;
        int guard = 0;
        while (para < book.paragraphs.size() && guard++ < 4000) {
            boolean pastEnd = para > ep || (para == ep && ln >= el);
            if (pastEnd) break;

            boolean heading = book.headings.contains(para);
            wrap(book.paragraphs.get(para), heading, scratch);
            int lc = scratch.isEmpty() ? 1 : scratch.size();
            float h = heading ? headH : lineH;
            if (y + h > maxH + 0.5f) break;

            if (ln < scratch.size()) {
                addLine(pg, para, ln, heading, y, scratch.get(ln), lc);
            } else {
                Line blank = new Line();
                blank.heading = heading;
                blank.para = para;
                blank.y = y - (heading ? headAscent : bodyAscent);
                pg.lines.add(blank);
            }
            y += h;
            if (heading && ln == 0) y += paraGap * 0.5f;
            ln++;
            if (ln >= lc) {
                y += paraGap;
                para++;
                ln = 0;
            }
        }
        pg.endPara = para;
        return pg;
    }

    private void addLine(Page pg, int para, int lineIdx, boolean heading, float yTop,
                         LineToks lt, int lineCount) {
        Line line = new Line();
        line.heading = heading;
        line.para = para;

        Paint p = heading ? headPaint : paint;
        float ascent = heading ? headAscent : bodyAscent;
        line.y = yTop - ascent;

        int n = lt.words.size();
        if (n == 0) {
            pg.lines.add(line);
            return;
        }
        boolean first = (lineIdx == 0);
        float xStart = (first && !heading) ? indent : 0f;
        float avail = maxW - xStart;

        float spaceW = p.measureText(" ");
        float total = 0;
        for (int i = 0; i < n; i++) total += p.measureText(lt.words.get(i));
        if (n > 1) total += spaceW * (n - 1);

        float gap = 0;
        if (justify && !heading && first && n > 1 && lineCount > 1 && lt.w > 0) {
            gap = 0; // first line of paragraph is never justified extra (indent absorbs it)
        } else if (justify && !heading && !first && n > 1 && lineIdx < lineCount - 1) {
            gap = (avail - total) / (n - 1);
            if (gap < 0) gap = 0;
            if (gap > spaceW * 2.5f) gap = spaceW * 2.5f;
        }

        float cursor = xStart;
        for (int i = 0; i < n; i++) {
            Word wd = new Word();
            wd.text = lt.words.get(i);
            wd.x0 = cursor;
            float w = p.measureText(wd.text);
            cursor += w;
            wd.x1 = cursor;
            if (i < n - 1) cursor += spaceW + gap;
            line.words.add(wd);
        }
        pg.lines.add(line);
    }

    // ---------------------------------------------------------- wrapping

    private void wrap(String text, boolean heading, ArrayList<LineToks> out) {
        out.clear();
        Paint p = heading ? headPaint : paint;
        float spaceW = p.measureText(" ");
        float availFirst = maxW - indent;
        out.add(new LineToks());
        int len = text.length();
        int i = 0;
        while (i < len) {
            while (i < len && text.charAt(i) == ' ') i++;
            if (i >= len) break;
            int j = i;
            while (j < len && text.charAt(j) != ' ') j++;
            String word = text.substring(i, j);
            i = j;
            addWord(p, out, word, spaceW, availFirst);
        }
        // finalize widths
        for (LineToks lt : out) {
            if (lt.words.isEmpty()) { lt.w = 0; continue; }
            StringBuilder sb = new StringBuilder();
            for (String w : lt.words) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w);
            }
            lt.w = p.measureText(sb.toString());
        }
    }

    private void addWord(Paint p, ArrayList<LineToks> out, String word, float spaceW, float availFirst) {
        LineToks cur = out.get(out.size() - 1);
        float w = p.measureText(word);

        if (w > maxW) {
            // Hard-split words wider than the column (URLs, CJK runs, long tokens)
            if (!cur.words.isEmpty()) {
                cur = new LineToks();
                out.add(cur);
            }
            int start = 0;
            for (int k = 1; k <= word.length(); k++) {
                float cw = p.measureText(word, start, k);
                if (cw > maxW && k - 1 > start) {
                    LineToks nl = new LineToks();
                    nl.words.add(word.substring(start, k - 1));
                    out.add(nl);
                    start = k - 1;
                }
            }
            if (start < word.length()) {
                LineToks nl = new LineToks();
                nl.words.add(word.substring(start));
                out.add(nl);
            }
            return;
        }

        boolean firstLineOfPara = (out.size() == 1);
        float avail = firstLineOfPara ? availFirst : maxW;
        float need = cur.words.isEmpty() ? w : (cur.w + spaceW + w);
        if (need > avail && !cur.words.isEmpty()) {
            LineToks nl = new LineToks();
            nl.words.add(word);
            out.add(nl);
        } else {
            cur.words.add(word);
        }
    }
}

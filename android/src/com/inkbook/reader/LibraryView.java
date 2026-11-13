package com.inkbook.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Modern Android library screen: light app bar, white book cards with cover
 * thumbnails, progress bars and a floating action button. Deliberately looks
 * like a regular app - the e-ink styling lives only inside the reader screen.
 */
public final class LibraryView extends View {

    public interface Host {
        void onOpenBook(Prefs.BookEntry e);
        void onImport();
        void onDeleteBook(Prefs.BookEntry e);
    }

    // ------------------------------------------------------------ palette
    private static final int C_BG = 0xFFF7F6F3;          // warm light background
    private static final int C_SURFACE = 0xFFFFFFFF;     // cards / app bar
    private static final int C_HAIRLINE = 0xFFECEAE6;    // separators
    private static final int C_TEXT = 0xFF1D1C1A;        // primary text
    private static final int C_TEXT2 = 0xFF8B877E;       // secondary text
    private static final int C_ACCENT = 0xFF1E5F4E;      // deep ink green
    private static final int C_ACCENT_DARK = 0xFF174A3D; // FAB pressed
    private static final int C_ACCENT_SOFT = 0xFFE3EFEA; // tonal button bg
    private static final int C_TRACK = 0xFFE8E5DE;       // progress track
    private static final int C_DANGER = 0xFFB3261E;      // delete action
    private static final int C_SCRIM = 0x52000000;       // dialog scrim
    private static final int C_PRESSED = 0xFFF1EFEA;     // card pressed

    /** Muted book-cover colors, picked by title hash. */
    private static final int[] COVER_COLORS = {
            0xFFB3573F, 0xFF8A6D3B, 0xFF4E6E58, 0xFF58687A,
            0xFF96545C, 0xFFA87B2F, 0xFF44646B, 0xFF6E5A46
    };

    private static final int[] COVER_DARKS = {
            0xFF9A4832, 0xFF735A2F, 0xFF3F5A48, 0xFF48576A,
            0xFF7E454C, 0xFF8C6726, 0xFF385259, 0xFF59493B
    };

    // ------------------------------------------------------------ fields
    private final Host host;
    private final Prefs prefs;
    private final Prefs.Settings settings;
    private final List<Prefs.BookEntry> books = new ArrayList<Prefs.BookEntry>();

    private final float dpf;
    private final float touchSlop;

    private int W, H;
    private float scroll = 0;
    private float maxScroll = 0;

    private float downX, downY, lastMoveY;
    private boolean moved;
    private int pressedRow = -1;
    private Prefs.BookEntry pendingDelete = null;

    private final RectF importBtn = new RectF();
    private final RectF emptyBtn = new RectF();
    private final RectF dlgCancel = new RectF();
    private final RectF dlgDelete = new RectF();
    private final ArrayList<RectF> rowRects = new ArrayList<RectF>();

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCard = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFab = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint pTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMeta = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pCount = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pAppTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pCover = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pEmptyTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pEmptyBody = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBtnText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDlgTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDlgBody = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDlgBtn = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable longPressTask = new Runnable() {
        public void run() {
            if (pendingDelete != null) return;
            if (pressedRow >= 0 && pressedRow < books.size()) {
                pendingDelete = books.get(pressedRow);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        }
    };

    public LibraryView(Context c, Host host) {
        super(c);
        this.host = host;
        this.prefs = new Prefs(c);
        this.settings = prefs.settings();
        dpf = Util.dp(c, 1f);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        // software layer so Paint shadow layers render for cards / FAB
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        pCard.setShadowLayer(dpf * 5, 0, dpf * 2, 0x1A000000);
        pFab.setShadowLayer(dpf * 12, 0, dpf * 4, 0x33000000);
        pLine.setStyle(Paint.Style.FILL);

        pAppTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pAppTitle.setTextSize(Util.sp(c, 22));
        pCount.setTextSize(Util.sp(c, 13));

        pTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTitle.setTextSize(Util.sp(c, 16));

        pMeta.setTextSize(Util.sp(c, 12.5f));
        pCount.setColor(C_TEXT2);
        pMeta.setColor(C_TEXT2);

        pCover.setTypeface(Typeface.create("serif", Typeface.BOLD));
        pCover.setTextSize(Util.sp(c, 22));
        pCover.setTextAlign(Paint.Align.CENTER);

        pEmptyTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pEmptyTitle.setTextSize(Util.sp(c, 17));
        pEmptyTitle.setTextAlign(Paint.Align.CENTER);
        pEmptyBody.setTextSize(Util.sp(c, 13.5f));
        pEmptyBody.setTextAlign(Paint.Align.CENTER);

        pBtnText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pBtnText.setTextSize(Util.sp(c, 14));
        pBtnText.setTextAlign(Paint.Align.CENTER);

        pDlgTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pDlgTitle.setTextSize(Util.sp(c, 19));
        pDlgBody.setTextSize(Util.sp(c, 14));

        pDlgBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pDlgBtn.setTextSize(Util.sp(c, 14));
        pDlgBtn.setTextAlign(Paint.Align.CENTER);
    }

    public void refresh() {
        List<Prefs.BookEntry> list = prefs.library();
        Collections.sort(list, new Comparator<Prefs.BookEntry>() {
            public int compare(Prefs.BookEntry a, Prefs.BookEntry b) {
                if (a.lastReadAt != b.lastReadAt) return a.lastReadAt > b.lastReadAt ? -1 : 1;
                return a.addedAt > b.addedAt ? -1 : 1;
            }
        });
        books.clear();
        books.addAll(list);
        scroll = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
    }

    private float dp(float v) {
        return v * dpf;
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(C_BG);

        drawAppBar(cv, W);

        float listTop = dp(76);
        float listBot = H - dp(96);

        if (books.isEmpty()) {
            drawEmptyState(cv, listTop, listBot);
        } else {
            drawBookList(cv, listTop, listBot);
        }

        drawFab(cv);

        if (pendingDelete != null) drawDeleteDialog(cv);
    }

    private void drawAppBar(Canvas cv, int w) {
        pFill.setColor(C_SURFACE);
        cv.drawRect(0, 0, w, dp(64), pFill);
        pLine.setColor(C_HAIRLINE);
        cv.drawRect(0, dp(64), w, dp(64) + dpf, pLine);

        pAppTitle.setColor(C_TEXT);
        cv.drawText("InkBook", dp(20), dp(40), pAppTitle);

        String count = books.size() + (books.size() == 1 ? " book" : " books");
        pCount.setColor(C_TEXT2);
        float cw = pCount.measureText(count);
        cv.drawText(count, w - dp(20) - cw, dp(40), pCount);
    }

    private void drawBookList(Canvas cv, float listTop, float listBot) {
        rowRects.clear();
        float rowH = dp(96);
        float gap = dp(12);
        float contentH = books.size() * (rowH + gap);
        maxScroll = Math.max(0, contentH - (listBot - listTop));
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        cv.save();
        cv.clipRect(0, listTop, W, listBot);
        float y = listTop - scroll;
        for (int i = 0; i < books.size(); i++) {
            if (y + rowH < listTop) { y += rowH + gap; continue; }
            if (y > listBot) break;
            RectF r = new RectF(dp(16), y, W - dp(16), y + rowH);
            rowRects.add(r);
            drawBookCard(cv, r, books.get(i), i == pressedRow);
            y += rowH + gap;
        }
        cv.restore();

        if (maxScroll > 0) {
            float trackH = listBot - listTop;
            float thumbH = Math.max(dp(48), trackH * (trackH / contentH));
            float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
            pFill.setColor(0x3A000000);
            cv.drawRoundRect(new RectF(W - dpf * 6, thumbY, W - dpf * 3, thumbY + thumbH),
                    dpf * 2, dpf * 2, pFill);
        }
    }

    private void drawBookCard(Canvas cv, RectF r, Prefs.BookEntry e, boolean pressed) {
        pCard.setColor(pressed ? C_PRESSED : C_SURFACE);
        cv.drawRoundRect(r, dp(14), dp(14), pCard);

        // cover thumbnail
        int idx = Math.abs(e.title.hashCode()) % COVER_COLORS.length;
        RectF cover = new RectF(r.left + dp(16), r.top + dp(16),
                r.left + dp(16) + dp(48), r.top + dp(16) + dp(64));
        pFill.setColor(pressed ? COVER_DARKS[idx] : COVER_COLORS[idx]);
        cv.drawRoundRect(cover, dp(8), dp(8), pFill);

        // spine accents
        pFill.setColor(0x33FFFFFF);
        cv.drawRoundRect(new RectF(cover.left + dp(4), cover.top + dp(4),
                cover.left + dp(6), cover.bottom - dp(4)), dpf, dpf, pFill);

        String letter = (e.title == null || e.title.length() == 0)
                ? "?" : e.title.substring(0, 1).toUpperCase();
        pCover.setColor(0xE6FFFFFF);
        cv.drawText(letter, cover.centerX(), cover.centerY()
                - (pCover.ascent() + pCover.descent()) / 2f, pCover);

        // text column
        float tx = cover.right + dp(16);
        float tw = r.right - dp(44) - tx;

        CharSequence title = TextUtils.ellipsize(e.title, pTitle, tw, TextUtils.TruncateAt.END);
        pTitle.setColor(C_TEXT);
        cv.drawText(title.toString(), tx, r.top + dp(36), pTitle);

        String fmt = e.format == null ? "" : e.format.toUpperCase();
        String meta;
        if (e.totalPages > 0 && e.lastPara >= 0) {
            meta = fmt + "  \u00B7  Page " + (e.lastPage + 1) + " of " + e.totalPages;
        } else {
            meta = fmt + "  \u00B7  Not started yet";
        }
        CharSequence metaEl = TextUtils.ellipsize(meta, pMeta, tw, TextUtils.TruncateAt.END);
        pMeta.setColor(C_TEXT2);
        cv.drawText(metaEl.toString(), tx, r.top + dp(58), pMeta);

        // progress bar
        if (e.totalPages > 0 && e.lastPara >= 0) {
            int pct = Math.max(0, Math.min(100, (e.lastPage + 1) * 100 / Math.max(1, e.totalPages)));
            float barY = r.top + dp(72);
            float barW = r.right - dp(64) - tx;
            pFill.setColor(C_TRACK);
            cv.drawRoundRect(new RectF(tx, barY, tx + barW, barY + dp(4)), dp(2), dp(2), pFill);
            pFill.setColor(C_ACCENT);
            cv.drawRoundRect(new RectF(tx, barY, tx + Math.max(dp(6), barW * pct / 100f),
                    barY + dp(4)), dp(2), dp(2), pFill);
            pCount.setColor(C_TEXT2);
            String pctS = pct + "%";
            cv.drawText(pctS, r.right - dp(16) - pCount.measureText(pctS), barY + dp(5), pCount);
        }

        // chevron
        pMeta.setColor(0xFFB9B4AA);
        cv.drawText("\u203A", r.right - dp(24), r.centerY()
                - (pMeta.ascent() + pMeta.descent()) / 2f, pMeta);
    }

    private void drawFab(Canvas cv) {
        float fab = dp(56);
        importBtn.set(W - dp(20) - fab, H - dp(28) - fab, W - dp(20), H - dp(28));
        pFab.setColor(pressedRow == -2 ? C_ACCENT_DARK : C_ACCENT);
        cv.drawCircle(importBtn.centerX(), importBtn.centerY(), fab / 2f, pFab);

        // plus glyph
        pFill.setColor(0xFFFFFFFF);
        float cx = importBtn.centerX();
        float cy = importBtn.centerY();
        float arm = dp(9);
        float th = dp(2.6f);
        RectF hbar = new RectF(cx - arm, cy - th / 2f, cx + arm, cy + th / 2f);
        RectF vbar = new RectF(cx - th / 2f, cy - arm, cx + th / 2f, cy + arm);
        cv.drawRoundRect(hbar, th, th, pFill);
        cv.drawRoundRect(vbar, th, th, pFill);
    }

    private void drawEmptyState(Canvas cv, float top, float bottom) {
        float cx = W / 2f;
        float cy = (top + bottom) / 2f - dp(70);

        // illustration: soft circle with an open-book glyph
        float rad = dp(64);
        pFill.setColor(0xFFEFEDE8);
        cv.drawCircle(cx, cy, rad, pFill);

        RectF book = new RectF(cx - dp(30), cy - dp(22), cx + dp(30), cy + dp(22));
        pFill.setColor(C_SURFACE);
        cv.drawRoundRect(book, dp(6), dp(6), pFill);
        pLine.setColor(0xFFD9D5CC);
        cv.drawRect(book.centerX() - dpf, book.top + dp(5),
                book.centerX() + dpf, book.bottom - dp(5), pLine);
        pLine.setColor(0xFFC9C4B9);
        float ly = book.top + dp(9);
        while (ly < book.bottom - dp(9)) {
            cv.drawRect(book.left + dp(7), ly, book.left + dp(24), ly + dpf * 1.6f, pLine);
            cv.drawRect(book.right - dp(24), ly, book.right - dp(7), ly + dpf * 1.6f, pLine);
            ly += dp(7);
        }

        pEmptyTitle.setColor(C_TEXT);
        cv.drawText("No books yet", cx, cy + rad + dp(40), pEmptyTitle);
        pEmptyBody.setColor(C_TEXT2);
        cv.drawText("Import a TXT or EPUB file to start reading.", cx, cy + rad + dp(64), pEmptyBody);

        // tonal import button
        float bw = dp(184);
        float bh = dp(44);
        emptyBtn.set(cx - bw / 2f, cy + rad + dp(84), cx + bw / 2f, cy + rad + dp(84) + bh);
        pFill.setColor(pressedRow == -3 ? 0xFFD3E4DD : C_ACCENT_SOFT);
        cv.drawRoundRect(emptyBtn, dp(22), dp(22), pFill);
        pBtnText.setColor(C_ACCENT);
        cv.drawText("Import book", emptyBtn.centerX(),
                emptyBtn.centerY() - (pBtnText.ascent() + pBtnText.descent()) / 2f, pBtnText);
    }

    private void drawDeleteDialog(Canvas cv) {
        // scrim
        pFill.setColor(C_SCRIM);
        cv.drawRect(0, 0, W, H, pFill);

        float pw = Math.min(dp(320), W - dp(32));
        float ph = dp(196);
        RectF r = new RectF(W / 2f - pw / 2f, H / 2f - ph / 2f, W / 2f + pw / 2f, H / 2f + ph / 2f);
        pCard.setColor(C_SURFACE);
        cv.drawRoundRect(r, dp(18), dp(18), pCard);

        pDlgTitle.setColor(C_TEXT);
        cv.drawText("Delete book?", r.left + dp(24), r.top + dp(38), pDlgTitle);

        CharSequence t = TextUtils.ellipsize(pendingDelete.title, pDlgBody, pw - dp(48),
                TextUtils.TruncateAt.MIDDLE);
        pDlgBody.setColor(C_TEXT2);
        cv.drawText(t.toString(), r.left + dp(24), r.top + dp(66), pDlgBody);

        List<String> wrapped = wrap("The file and your reading progress will be removed.",
                pDlgBody, pw - dp(48));
        float wy = r.top + dp(92);
        for (String ln : wrapped) {
            cv.drawText(ln, r.left + dp(24), wy, pDlgBody);
            wy += dp(19);
        }

        // buttons, bottom-right
        float bH = dp(40);
        float by = r.bottom - dp(16) - bH;
        float cancelW = pDlgBtn.measureText("CANCEL") + dp(28);
        float deleteW = pDlgBtn.measureText("DELETE") + dp(28);
        dlgDelete.set(r.right - dp(24) - deleteW, by, r.right - dp(24), by + bH);
        dlgCancel.set(dlgDelete.left - dp(12) - cancelW, by, dlgDelete.left - dp(12), by + bH);

        pDlgBtn.setColor(C_ACCENT);
        cv.drawText("CANCEL", dlgCancel.centerX(),
                dlgCancel.centerY() - (pDlgBtn.ascent() + pDlgBtn.descent()) / 2f, pDlgBtn);
        pDlgBtn.setColor(C_DANGER);
        cv.drawText("DELETE", dlgDelete.centerX(),
                dlgDelete.centerY() - (pDlgBtn.ascent() + pDlgBtn.descent()) / 2f, pDlgBtn);
    }

    private List<String> wrap(String text, TextPaint p, float maxW) {
        List<String> out = new ArrayList<String>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String cand = cur.length() == 0 ? w : cur.toString() + " " + w;
            if (p.measureText(cand) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        int action = e.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastMoveY = y;
                moved = false;
                if (pendingDelete != null) {
                    handleDeleteTouch(x, y);
                    return true;
                }
                pressedRow = rowAt(x, y);
                if (pressedRow < 0 && importBtn.contains(x, y)) {
                    pressedRow = -2; // FAB pressed
                } else if (books.isEmpty() && emptyBtn.contains(x, y)) {
                    pressedRow = -3; // empty-state button pressed
                }
                invalidate();
                postDelayed(longPressTask, 550);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pendingDelete != null) return true;
                if (!moved && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                    moved = true;
                    removeCallbacks(longPressTask);
                    pressedRow = -1;
                    invalidate();
                }
                if (moved) {
                    float dy = y - lastMoveY;
                    scroll -= dy;
                    lastMoveY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressTask);
                pressedRow = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressTask);
                if (pendingDelete != null) {
                    handleDeleteTouch(x, y);
                    return true;
                }
                if (moved) {
                    pressedRow = -1;
                    invalidate();
                    return true;
                }
                if (pressedRow == -2 || importBtn.contains(x, y)) {
                    pressedRow = -1;
                    invalidate();
                    host.onImport();
                    return true;
                }
                if (pressedRow == -3 || (books.isEmpty() && emptyBtn.contains(x, y))) {
                    pressedRow = -1;
                    invalidate();
                    host.onImport();
                    return true;
                }
                int row = rowAt(x, y);
                pressedRow = -1;
                if (row >= 0 && row < books.size()) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    host.onOpenBook(books.get(row));
                } else {
                    invalidate();
                }
                return true;
            default:
                return true;
        }
    }

    private void handleDeleteTouch(float x, float y) {
        if (dlgCancel.contains(x, y)) {
            pendingDelete = null;
            invalidate();
        } else if (dlgDelete.contains(x, y)) {
            Prefs.BookEntry target = pendingDelete;
            pendingDelete = null;
            if (target != null) host.onDeleteBook(target);
        } else {
            // tap on scrim dismisses
            pendingDelete = null;
            invalidate();
        }
    }

    private int rowAt(float x, float y) {
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(x, y)) return i;
        }
        return -1;
    }
}

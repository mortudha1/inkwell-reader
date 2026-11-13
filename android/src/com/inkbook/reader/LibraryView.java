package com.inkbook.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

/** E-ink styled book shelf: rows with progress, import button, delete on long-press. */
public final class LibraryView extends View {

    public interface Host {
        void onOpenBook(Prefs.BookEntry e);
        void onImport();
        void onDeleteBook(Prefs.BookEntry e);
    }

    private final Host host;
    private final Prefs prefs;
    private final Prefs.Settings settings;
    private final List<Prefs.BookEntry> books = new ArrayList<Prefs.BookEntry>();

    private final float dpf;
    private final float spf;
    private final float touchSlop;

    private int W, H;
    private float scroll = 0;
    private float maxScroll = 0;

    private float downX, downY, lastMoveY;
    private boolean moved;
    private int pressedRow = -1;
    private Prefs.BookEntry pendingDelete = null;

    private final RectF importBtn = new RectF();
    private final ArrayList<RectF> rowRects = new ArrayList<RectF>();

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pHeader = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pKicker = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMeta = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBtn = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenuTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenu = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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
        spf = Util.sp(c, 1f);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        pStroke.setStyle(Paint.Style.STROKE);
        pLine.setStyle(Paint.Style.FILL);

        pHeader.setTypeface(TypefaceBold());
        pHeader.setTextSize(spf * 26);
        pKicker.setTextSize(spf * 10);
        if (android.os.Build.VERSION.SDK_INT >= 21) pKicker.setLetterSpacing(0.22f);
        pTitle.setTypeface(TypefaceBold());
        pTitle.setTextSize(spf * 16);
        pMeta.setTextSize(spf * 12);
        pBtn.setTypeface(TypefaceBold());
        pBtn.setTextSize(spf * 12);
        pBtn.setTextAlign(Paint.Align.CENTER);
        pSmall.setTextSize(spf * 11);
        pMenuTitle.setTypeface(TypefaceBold());
        pMenuTitle.setTextSize(spf * 13);
        pMenuTitle.setTextAlign(Paint.Align.CENTER);
        if (android.os.Build.VERSION.SDK_INT >= 21) pMenuTitle.setLetterSpacing(0.16f);
        pMenu.setTextSize(spf * 14);
    }

    private static android.graphics.Typeface TypefaceBold() {
        return android.graphics.Typeface.DEFAULT_BOLD;
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

    @Override
    protected void onDraw(Canvas cv) {
        EinkTheme th = EinkTheme.theme(settings.theme);
        cv.drawColor(th.bg);
        cv.drawRect(0, 0, W, H, Util.noisePaint());

        // header
        pHeader.setColor(th.text);
        cv.drawText("InkBook", dpf * 20, dpf * 46, pHeader);
        pKicker.setColor(th.textSoft);
        cv.drawText("E - I N K   R E A D E R", dpf * 20, dpf * 66, pKicker);
        pSmall.setColor(th.textSoft);
        String count = books.size() + (books.size() == 1 ? " BOOK" : " BOOKS");
        float cw = pSmall.measureText(count);
        cv.drawText(count, W - dpf * 20 - cw, dpf * 46, pSmall);
        pLine.setColor(th.line);
        cv.drawRect(dp(20), dp(80), W - dp(20), dp(80) + dpf, pLine);

        float listTop = dp(96);
        float listBot = H - dp(96);

        if (books.isEmpty()) {
            drawEmptyState(cv, th);
        } else {
            rowRects.clear();
            float rowH = dp(66);
            float gap = dp(10);
            float contentH = books.size() * (rowH + gap);
            maxScroll = Math.max(0, contentH - (listBot - listTop));
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0) scroll = 0;

            cv.save();
            cv.clipRect(dp(16), listTop, W - dp(16), listBot);
            float y = listTop - scroll;
            for (int i = 0; i < books.size(); i++) {
                if (y + rowH < listTop) { y += rowH + gap; continue; }
                if (y > listBot) break;
                RectF r = new RectF(dp(16), y, W - dp(16), y + rowH);
                rowRects.add(r);
                drawBookRow(cv, th, r, books.get(i), i == pressedRow);
                y += rowH + gap;
            }
            cv.restore();

            if (maxScroll > 0) {
                float trackH = listBot - listTop;
                float thumbH = Math.max(dp(40), trackH * (trackH / contentH));
                float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
                pFill.setColor(th.textSoft);
                cv.drawRoundRect(new RectF(W - dpf * 7, thumbY, W - dpf * 4, thumbY + thumbH),
                        dpf * 2, dpf * 2, pFill);
            }
        }

        // import button
        float bw = dp(220);
        float bh = dp(46);
        importBtn.set(W / 2f - bw / 2f, H - dp(78), W / 2f + bw / 2f, H - dp(32));
        pFill.setColor(th.text);
        cv.drawRoundRect(importBtn, dpf * 4, dpf * 4, pFill);
        pBtn.setColor(th.bg);
        cv.drawText("+  IMPORT BOOK", importBtn.centerX(),
                importBtn.centerY() - (pBtn.ascent() + pBtn.descent()) / 2f, pBtn);

        if (pendingDelete != null) drawDeleteConfirm(cv, th);
    }

    private float dp(float v) {
        return v * dpf;
    }

    private void drawBookRow(Canvas cv, EinkTheme th, RectF r, Prefs.BookEntry e, boolean pressed) {
        pFill.setColor(pressed ? th.tabFill : th.menuBg);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
        pStroke.setColor(th.btnBorder);
        pStroke.setStrokeWidth(dpf * 1.2f);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);

        float x0 = r.left + dp(14);
        float x1 = r.right - dp(38);

        CharSequence title = TextUtils.ellipsize(e.title, pTitle, x1 - x0, TextUtils.TruncateAt.END);
        pTitle.setColor(th.text);
        cv.drawText(title.toString(), x0, r.top + dp(28), pTitle);

        String meta;
        if (e.totalPages > 0 && e.lastPara >= 0) {
            int pct = Math.max(0, Math.min(100, (e.lastPage + 1) * 100 / Math.max(1, e.totalPages)));
            meta = e.format.toUpperCase() + "  \u00B7  p. " + (e.lastPage + 1) + "/" + e.totalPages
                    + "  \u00B7  " + pct + "%";
        } else {
            meta = e.format.toUpperCase() + "  \u00B7  not started";
        }
        pMeta.setColor(th.textSoft);
        cv.drawText(meta, x0, r.top + dp(50), pMeta);

        pTitle.setColor(th.textSoft);
        cv.drawText("\u203A", x1 + dp(8), r.centerY() - (pTitle.ascent() + pTitle.descent()) / 2f, pTitle);
    }

    private void drawEmptyState(Canvas cv, EinkTheme th) {
        float cx = W / 2f;
        float top = dp(150);

        // little device illustration
        float dw = dp(150);
        float dh = dp(196);
        RectF dev = new RectF(cx - dw / 2, top, cx + dw / 2, top + dh);
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 2.5f);
        cv.drawRoundRect(dev, dp(8), dp(8), pStroke);

        RectF screen = new RectF(dev.left + dp(12), dev.top + dp(14),
                dev.right - dp(12), dev.bottom - dp(52));
        pFill.setColor(th.tabFill);
        cv.drawRoundRect(screen, dp(2), dp(2), pFill);

        RectF bar = new RectF(screen.left + dp(6), screen.top + dp(6),
                screen.left + dp(80), screen.top + dp(16));
        pFill.setColor(th.textSoft);
        cv.drawRoundRect(bar, dp(2), dp(2), pFill);

        pLine.setColor(th.textSoft);
        float ly = screen.top + dp(30);
        while (ly < screen.bottom - dp(10)) {
            float w2 = (ly % (dp(30)) < dp(12)) ? dp(84) : dp(72);
            cv.drawRect(screen.left + dp(6), ly, screen.left + dp(6) + w2, ly + dp(2.5f), pLine);
            ly += dp(14);
        }

        RectF nav = new RectF(cx - dp(14), dev.bottom - dp(40), cx + dp(14), dev.bottom - dp(12));
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 2.5f);
        cv.drawRoundRect(nav, dp(5), dp(5), pStroke);

        pSmall.setColor(th.textSoft);
        pSmall.setTextAlign(Paint.Align.CENTER);
        cv.drawText("No books yet", cx, top + dh + dp(36), pSmall);
        pSmall.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDeleteConfirm(Canvas cv, EinkTheme th) {
        pFill.setColor(th.menuBg);
        float pw = Math.min(dp(300), W - dp(24));
        float ph = dp(170);
        RectF r = new RectF(W / 2f - pw / 2f, H / 2f - ph / 2f, W / 2f + pw / 2f, H / 2f + ph / 2f);
        cv.drawRoundRect(r, dpf * 4, dpf * 4, pFill);
        pStroke.setColor(th.menuBorder);
        pStroke.setStrokeWidth(dpf * 2f);
        cv.drawRoundRect(r, dpf * 4, dpf * 4, pStroke);

        pMenuTitle.setColor(th.menuText);
        cv.drawText("DELETE BOOK", r.centerX(), r.top + dp(30), pMenuTitle);
        pMenu.setColor(th.textSoft);
        CharSequence t = TextUtils.ellipsize(pendingDelete.title, pMenu, pw - dp(32),
                TextUtils.TruncateAt.MIDDLE);
        pMenu.setTextAlign(Paint.Align.CENTER);
        cv.drawText(t.toString(), r.centerX(), r.top + dp(58), pMenu);
        cv.drawText("Remove it from your library?", r.centerX(), r.top + dp(80), pMenu);
        pMenu.setTextAlign(Paint.Align.LEFT);

        float bw = dp(110);
        float by = r.bottom - dp(52);
        RectF cancel = new RectF(r.left + dp(20), by, r.left + dp(20) + bw, by + dp(36));
        RectF del = new RectF(r.right - dp(20) - bw, by, r.right - dp(20), by + dp(36));
        pFill.setColor(th.btnFill);
        cv.drawRoundRect(cancel, dpf * 3, dpf * 3, pFill);
        pStroke.setColor(th.btnBorder);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawRoundRect(cancel, dpf * 3, dpf * 3, pStroke);
        pFill.setColor(th.text);
        cv.drawRoundRect(del, dpf * 3, dpf * 3, pFill);
        pBtn.setTextAlign(Paint.Align.CENTER);
        pBtn.setColor(th.text);
        cv.drawText("CANCEL", cancel.centerX(), cancel.centerY() - (pBtn.ascent() + pBtn.descent()) / 2f, pBtn);
        pBtn.setColor(th.bg);
        cv.drawText("DELETE", del.centerX(), del.centerY() - (pBtn.ascent() + pBtn.descent()) / 2f, pBtn);
        pBtn.setTextAlign(Paint.Align.LEFT);
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
                    pressedRow = -2; // import pressed
                    invalidate();
                }
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
                if (pressedRow == -2) {
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
        float pw = Math.min(dp(300), W - dp(24));
        float ph = dp(170);
        float bw = dp(110);
        float by = H / 2f - ph / 2f + ph - dp(52);
        RectF cancel = new RectF(W / 2f - pw / 2f + dp(20), by, W / 2f - pw / 2f + dp(20) + bw, by + dp(36));
        RectF del = new RectF(W / 2f + pw / 2f - dp(20) - bw, by, W / 2f + pw / 2f - dp(20), by + dp(36));
        Prefs.BookEntry target = pendingDelete;
        if (cancel.contains(x, y)) {
            pendingDelete = null;
            invalidate();
        } else if (del != null && del.contains(x, y) && target != null) {
            pendingDelete = null;
            host.onDeleteBook(target);
        }
        // taps outside keep the dialog open
    }

    private int rowAt(float x, float y) {
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(x, y)) return i;
        }
        return -1;
    }
}

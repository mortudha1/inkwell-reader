package com.inkbook.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The reading screen. Everything is canvas-drawn to mimic a classic
 * e-ink device: gray paper, status bar with title tab, page counters,
 * side page buttons, bottom hardware-style buttons and e-ink dialogs.
 */
public final class ReaderView extends View {

    public interface CloseListener {
        void onClose();
    }

    private final Book book;
    private final Prefs prefs;
    private final Prefs.Settings settings;
    private final Prefs.BookEntry entry;
    private final CloseListener closeListener;
    private final Paginator paginator;
    private final int anchorPara;

    private final float dpf;   // 1dp in px
    private final float spf;   // 1sp in px
    private final float touchSlop;
    private final float swipeMin;

    private int W, H;
    private float statusH, bottomH, gutter, padX;
    private final RectF textCol = new RectF();

    private boolean paginating = false;
    private int progressPct = 0;
    private int paginateAnchor;
    private int pageIndex = 0;
    private Paginator.Page page;
    private int pageCacheIdx = -1;
    private int flashFrames = 0;
    private String hint = null;
    private int hintFrames = 0;

    private Overlay active = null;

    private Paginator.Word selWordRef = null;
    private String selWord = null;

    private final RectF hitQ = new RectF();
    private final RectF hitMenuB = new RectF();
    private final RectF hitHome = new RectF();
    private final RectF hitTheme = new RectF();
    private final RectF hitFont = new RectF();

    private float downX, downY;
    private boolean moved, longFired;

    // ---------------------------------------------------------------- paints

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint pStatus = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pStatusTab = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pStatusR = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBtn = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenu = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenuR = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenuBold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenuTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pHint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDict = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDictW = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDictTag = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pFmt = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pFmtSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSelTxt = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void initPaints(Context c) {
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dpf * 1.5f);
        pLine.setStyle(Paint.Style.FILL);

        pStatus.setTypeface(TypefaceBOLD());
        pStatus.setTextSize(spf * 12);
        pStatusTab.set(pStatus);
        pStatusR.set(pStatus);

        pBtn.setTypeface(TypefaceBOLD());
        pBtn.setTextSize(spf * 11);
        pBtn.setTextAlign(Paint.Align.CENTER);

        pMenu.setTextSize(spf * 15);
        pMenuR.set(pMenu);
        pMenuR.setTextAlign(Paint.Align.RIGHT);
        pMenuBold.set(pMenu);
        pMenuBold.setTypeface(TypefaceBOLD());
        pMenuBold.setTextAlign(Paint.Align.CENTER);

        pMenuTitle.set(pMenuBold);
        pMenuTitle.setTextSize(spf * 12);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            pMenuTitle.setLetterSpacing(0.18f);
        }

        pHint.set(pMenuBold);
        pHint.setTextSize(spf * 13);
        pHint.setLetterSpacing(0.12f);

        pDict.setTextSize(spf * 14);
        pDictW.set(pDict);
        pDictW.setTypeface(TypefaceBOLD());
        pDictW.setTextSize(spf * 18);
        pDictTag.set(pDict);
        pDictTag.setTextSize(spf * 9);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            pDictTag.setLetterSpacing(0.15f);
        }

        pFmt.set(pMenuBold);
        pFmt.setTextSize(spf * 14);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            pFmt.setLetterSpacing(0.2f);
        }
        pFmtSmall.set(pMenu);
        pFmtSmall.setTextAlign(Paint.Align.CENTER);
        pFmtSmall.setTextSize(spf * 12);

        pSelTxt.setTextSize(spf * 18);
    }

    private static android.graphics.Typeface TypefaceBOLD() {
        return android.graphics.Typeface.DEFAULT_BOLD;
    }

    // ------------------------------------------------------------- overlays

    abstract class Overlay {
        boolean visible = false;
        final ArrayList<float[]> hits = new ArrayList<float[]>();

        void clearHits() { hits.clear(); }

        void hit(float x0, float y0, float x1, float y1, int action) {
            hits.add(new float[]{x0, y0, x1, y1, action});
        }

        int hitAt(float x, float y) {
            for (float[] h : hits) {
                if (x >= h[0] && x <= h[2] && y >= h[1] && y <= h[3]) return (int) h[4];
            }
            return -1;
        }

        abstract void draw(Canvas cv, EinkTheme th);

        abstract boolean onTouch(float x, float y);

        void onDown(float x, float y) { }
        void onMove(float x, float y) { }
        void onCancel() { }
    }

    private final MenuOverlay menu = new MenuOverlay();
    private final SettingsOverlay settingsOv = new SettingsOverlay();
    private final GoToOverlay gotoOv = new GoToOverlay();
    private final ChaptersOverlay chaptersOv = new ChaptersOverlay();
    private final DictOverlay dictOv = new DictOverlay();

    // ------------------------------------------------------------ lifecycle

    public ReaderView(Context c, Book book, Prefs.BookEntry entry, Prefs prefs,
                      int anchorPara, CloseListener listener) {
        super(c);
        this.book = book;
        this.entry = entry;
        this.prefs = prefs;
        this.settings = prefs.settings();
        this.closeListener = listener;
        this.anchorPara = anchorPara;
        this.paginateAnchor = anchorPara;

        dpf = Util.dp(c, 1f);
        spf = Util.sp(c, 1f);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        swipeMin = dpf * 70;

        paginator = new Paginator(book);
        initPaints(c);
        setKeepScreenOn(true);
    }

    public Prefs.BookEntry entry() {
        return entry;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        statusH = dpf * 40;
        bottomH = dpf * 58;
        gutter = dpf * 30;
        padX = dpf * 10;
        textCol.set(gutter + dpf * 8, statusH + dpf * 8,
                w - gutter - dpf * 8, h - bottomH - dpf * 10);
        configurePaginator();
        startPagination();
    }

    private void configurePaginator() {
        float fontPx = settings.fontSize * spf;
        paginator.configure((int) textCol.width(), (int) textCol.height(),
                fontPx, settings.lineSpacing, settings.typeface, settings.justify);
        page = null;
        pageCacheIdx = -1;
    }

    private void startPagination() {
        paginating = true;
        progressPct = 0;
        invalidate();
        final int anchor = paginateAnchor;
        Thread t = new Thread(new Runnable() {
            public void run() {
                paginator.paginate(new Paginator.Progress() {
                    public void onProgress(final int pct) {
                        post(new Runnable() {
                            public void run() {
                                progressPct = pct;
                                invalidate();
                            }
                        });
                    }

                    public void onDone() {
                        post(new Runnable() {
                            public void run() {
                                paginating = false;
                                int total = paginator.pageCount();
                                int target = (anchor >= 0) ? paginator.pageOfPara(anchor) : 0;
                                if (target > total - 1) target = Math.max(0, total - 1);
                                pageIndex = target;
                                page = null;
                                pageCacheIdx = -1;
                                flashFrames = settings.flash ? 1 : 0;
                                invalidate();
                            }
                        });
                    }
                });
            }
        });
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    void repaginate() {
        if (paginating) return;
        Paginator.Page pg = pageAt(pageIndex);
        paginateAnchor = (pg != null) ? pg.startPara : Math.max(0, anchorPara);
        configurePaginator();
        hide();
        startPagination();
    }

    private Paginator.Page pageAt(int i) {
        if (pageCacheIdx == i && page != null) return page;
        page = paginator.render(i);
        pageCacheIdx = i;
        return page;
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas cv) {
        EinkTheme th = EinkTheme.theme(settings.theme);
        cv.drawColor(th.bg);
        cv.drawRect(0, 0, W, H, Util.noisePaint());

        if (flashFrames > 0) {
            flashFrames--;
            cv.drawColor(th.flash);
            postInvalidateDelayed(55);
            return;
        }

        if (paginating) {
            drawStatus(cv, th, 0);
            drawFormatting(cv, th);
            postInvalidateDelayed(60000);
            return;
        }

        int total = paginator.pageCount();
        if (total <= 0) {
            drawStatus(cv, th, 0);
            postInvalidateDelayed(60000);
            return;
        }
        if (pageIndex > total - 1) pageIndex = total - 1;
        if (pageIndex < 0) pageIndex = 0;

        drawStatus(cv, th, total);
        Paginator.Page pg = pageAt(pageIndex);
        if (pg != null) drawPageText(cv, th, pg);
        drawSideButtons(cv, th);
        drawBottomBar(cv, th);

        if (active != null) active.draw(cv, th);

        if (hint != null && hintFrames > 0) {
            hintFrames--;
            pHint.setColor(th.textSoft);
            cv.drawText(hint, W / 2f, H * 0.5f, pHint);
            if (hintFrames <= 0) hint = null;
            postInvalidateDelayed(900);
        }
        postInvalidateDelayed(60000);
    }

    private float baseCentered(Paint p, float top, float bottom) {
        return ((top + bottom) / 2f) - (p.ascent() + p.descent()) / 2f;
    }

    private void drawStatus(Canvas cv, EinkTheme th, int total) {
        // strip
        pFill.setColor(th.strip);
        cv.drawRect(0, 0, W, statusH + dpf * 4, pFill);
        pLine.setColor(th.line);
        cv.drawRect(0, statusH + dpf * 4, W, statusH + dpf * 5, pLine);

        float y0 = dpf * 6;
        float h0 = statusH - dpf * 12;

        // title tab
        String title = book.title == null ? "" : book.title;
        float maxTabW = W * 0.5f;
        CharSequence shown = TextUtils.ellipsize(title, pStatusTab, maxTabW - dpf * 24,
                TextUtils.TruncateAt.END);
        float tw = pStatusTab.measureText(shown.toString());
        RectF tab = new RectF(dpf * 2, y0, dpf * 12 + tw + dpf * 10, y0 + h0);
        pFill.setColor(th.tabFill);
        cv.drawRoundRect(tab, dpf * 3, dpf * 3, pFill);
        pStatusTab.setColor(th.tabText);
        float base = baseCentered(pStatusTab, y0, y0 + h0);
        cv.drawText(shown.toString(), dpf * 12, base, pStatusTab);

        // dot + chevrons
        float dotX = tab.right + dpf * 9;
        pFill.setColor(th.textSoft);
        cv.drawCircle(dotX, y0 + h0 / 2f, dpf * 2.2f, pFill);
        pStatus.setColor(th.textSoft);
        cv.drawText("\u00BB", dotX + dpf * 6, base, pStatus);

        // right side: page / time / battery
        pStatusR.setColor(th.text);
        float xRight = W - padX;

        // battery
        float bw = dpf * 22, bh = dpf * 11;
        float bx1 = xRight;
        float bx0 = bx1 - bw;
        float by0 = (statusH - bh) / 2f;
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawRect(bx0, by0, bx0 + bh * 0.86f, by0 + bh, pStroke);
        cv.drawRect(bx0 + bh * 0.86f + dpf, by0 + bh * 0.28f, bx1, by0 + bh * 0.72f, pStroke);
        int pct = Util.batteryPct(getContext());
        pFill.setColor(th.text);
        float iw = (bh * 0.86f - dpf * 4) * pct / 100f;
        cv.drawRect(bx0 + dpf * 2, by0 + dpf * 2, bx0 + dpf * 2 + iw, by0 + bh - dpf * 2, pFill);

        // time
        String now = Util.clock();
        float timeW = pStatusR.measureText(now);
        float timeX = bx0 - dpf * 12;
        cv.drawText(now, timeX, base, pStatusR);

        // page counter
        String pg = total > 0 ? (pageIndex + 1) + "/" + total : "-/-";
        cv.drawText(pg, timeX - timeW - dpf * 16, base, pStatusR);
    }

    private void drawFormatting(Canvas cv, EinkTheme th) {
        float cy = H * 0.44f;
        pFmt.setColor(th.text);
        cv.drawText("FORMATTING", W / 2f, cy, pFmt);
        pFmtSmall.setColor(th.textSoft);
        cv.drawText(progressPct + "%", W / 2f, cy + dpf * 28, pFmtSmall);

        float bw = W * 0.42f;
        float x0 = W / 2f - bw / 2f;
        float y = cy + dpf * 44;
        pStroke.setColor(th.textSoft);
        cv.drawRect(x0, y, x0 + bw, y + dpf * 9, pStroke);
        pFill.setColor(th.text);
        cv.drawRect(x0 + dpf, y + dpf, x0 + dpf + (bw - dpf * 2) * progressPct / 100f,
                y + dpf * 8, pFill);

        pFmtSmall.setColor(th.textSoft);
        cv.drawText(book.title, W / 2f, y + dpf * 34, pFmtSmall);
    }

    private void drawPageText(Canvas cv, EinkTheme th, Paginator.Page pg) {
        float left = textCol.left;
        float top = textCol.top;
        Paint body = paginator.bodyPaint();
        Paint head = paginator.headingPaint();
        for (Paginator.Line ln : pg.lines) {
            Paint p = ln.heading ? head : body;
            p.setColor(th.text);
            float by = top + ln.y;
            for (Paginator.Word wd : ln.words) {
                if (wd == selWordRef) {
                    float a = ln.heading ? paginator.headAscent() : paginator.bodyAscent();
                    float d = (ln.heading ? paginator.headingPaint() : paginator.bodyPaint()).descent();
                    pSel.setColor(th.text);
                    cv.drawRect(left + wd.x0 - dpf * 2, by + a - dpf * 2,
                            left + wd.x1 + dpf * 2, by + d + dpf * 2, pSel);
                    pSelTxt.setColor(th.selText);
                    pSelTxt.setTextSize(p.getTextSize());
                    cv.drawText(wd.text, left + wd.x0, by, pSelTxt);
                } else {
                    cv.drawText(wd.text, left + wd.x0, by, p);
                }
            }
        }
    }

    private void drawSideButtons(Canvas cv, EinkTheme th) {
        float bw = gutter - dpf * 6;
        float bh = dpf * 40;
        float[] xs = {dpf * 3, W - dpf * 3 - bw};
        float[] ys = {H * 0.36f, H * 0.60f};
        for (float x : xs) {
            for (int k = 0; k < ys.length; k++) {
                RectF r = new RectF(x, ys[k], x + bw, ys[k] + bh);
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);
                float cx = r.centerX();
                float cy = r.centerY();
                pStroke.setColor(th.text);
                pStroke.setStrokeWidth(dpf * 2f);
                float dx = dpf * 5, dy2 = dpf * 3.2f;
                if (k == 0) {
                    cv.drawLine(cx - dx, cy + dy2, cx, cy - dy2, pStroke);
                    cv.drawLine(cx, cy - dy2, cx + dx, cy + dy2, pStroke);
                } else {
                    cv.drawLine(cx - dx, cy - dy2, cx, cy + dy2, pStroke);
                    cv.drawLine(cx, cy + dy2, cx + dx, cy - dy2, pStroke);
                }
            }
        }
    }

    private void drawBottomBar(Canvas cv, EinkTheme th) {
        float top = H - bottomH;
        pLine.setColor(th.line);
        cv.drawRect(0, top, W, top + dpf, pLine);

        float bH = dpf * 34;
        float bTop = top + (bottomH - bH) / 2f;

        // Q|Q
        hitQ.set(dpf * 12, bTop, dpf * 12 + dpf * 52, bTop + bH);
        drawBtnRect(cv, th, hitQ);
        pBtn.setColor(th.text);
        cv.drawText("Q|Q", hitQ.centerX(), baseCentered(pBtn, bTop, bTop + bH), pBtn);

        // return-arrow + M
        hitMenuB.set(hitQ.right + dpf * 8, bTop, hitQ.right + dpf * 8 + dpf * 58, bTop + bH);
        drawBtnRect(cv, th, hitMenuB);
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.6f);
        float ax = hitMenuB.left + dpf * 10;
        float ay = hitMenuB.centerY();
        cv.drawLine(ax, ay, ax + dpf * 12, ay, pStroke);
        cv.drawLine(ax + dpf * 12, ay, ax + dpf * 12, ay + dpf * 5, pStroke);
        cv.drawLine(ax + dpf * 8, ay + dpf * 2, ax + dpf * 12, ay + dpf * 5, pStroke);
        cv.drawText("/M", hitMenuB.centerX() + dpf * 12, baseCentered(pBtn, bTop, bTop + bH), pBtn);

        // center nav button
        float cx = W / 2f;
        hitHome.set(cx - dpf * 26, bTop - dpf * 2, cx + dpf * 26, bTop + dpf * 42);
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 3f);
        cv.drawRoundRect(hitHome, dpf * 11, dpf * 11, pStroke);
        pFill.setColor(th.text);
        RectF inner = new RectF(cx - dpf * 15, bTop + dpf * 6, cx + dpf * 15, bTop + dpf * 28);
        cv.drawRoundRect(inner, dpf * 5, dpf * 5, pFill);

        // right buttons: theme (moon+sun), font (keyboard)
        hitFont.set(W - dpf * 12 - dpf * 52, bTop, W - dpf * 12, bTop + bH);
        hitTheme.set(hitFont.left - dpf * 8 - dpf * 52, bTop, hitFont.left - dpf * 8, bTop + bH);
        drawBtnRect(cv, th, hitTheme);
        drawBtnRect(cv, th, hitFont);

        // moon + sun
        float mx = hitTheme.centerX() - dpf * 8;
        float my = hitTheme.centerY();
        pFill.setColor(th.text);
        cv.drawCircle(mx, my, dpf * 5.5f, pFill);
        pFill.setColor(th.btnFill);
        cv.drawCircle(mx + dpf * 3, my - dpf * 2.5f, dpf * 4.6f, pFill);
        float sx = hitTheme.centerX() + dpf * 8;
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawCircle(sx, my, dpf * 3.4f, pStroke);
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            float x1 = sx + (float) Math.cos(ang) * dpf * 5;
            float y1 = my + (float) Math.sin(ang) * dpf * 5;
            float x2 = sx + (float) Math.cos(ang) * dpf * 7.4f;
            float y2 = my + (float) Math.sin(ang) * dpf * 7.4f;
            cv.drawLine(x1, y1, x2, y2, pStroke);
        }

        // keyboard glyph
        float kx0 = hitFont.centerX() - dpf * 14;
        float kx1 = hitFont.centerX() + dpf * 14;
        float ky0 = hitFont.centerY() - dpf * 9;
        float ky1 = hitFont.centerY() + dpf * 9;
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawRoundRect(new RectF(kx0, ky0, kx1, ky1), dpf * 2, dpf * 2, pStroke);
        pFill.setColor(th.text);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                float dx0 = kx0 + dpf * 4 + col * dpf * 5.4f;
                float dy0 = ky0 + dpf * 3.5f + row * dpf * 4.4f;
                cv.drawCircle(dx0, dy0, dpf * 1.0f, pFill);
            }
        }
        cv.drawLine(kx0 + dpf * 4, ky1 - dpf * 3.5f, kx1 - dpf * 4, ky1 - dpf * 3.5f, pStroke);
    }

    private void drawBtnRect(Canvas cv, EinkTheme th, RectF r) {
        pFill.setColor(th.btnFill);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
        pStroke.setColor(th.btnBorder);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);
    }

    private RectF drawPanelPx(Canvas cv, EinkTheme th, float pw, float ph) {
        pw = Math.min(pw, W - dpf * 24);
        ph = Math.min(ph, H - dpf * 40);
        RectF r = new RectF((W - pw) / 2f, (H - ph) / 2f, (W + pw) / 2f, (H + ph) / 2f);
        pFill.setColor(th.menuBg);
        cv.drawRoundRect(r, dpf * 4, dpf * 4, pFill);
        pStroke.setColor(th.menuBorder);
        pStroke.setStrokeWidth(dpf * 2f);
        cv.drawRoundRect(r, dpf * 4, dpf * 4, pStroke);
        return r;
    }

    // ---------------------------------------------------------------- touch

    private final Runnable longPressTask = new Runnable() {
        public void run() {
            if (paginating || active != null) return;
            longFired = true;
            selectWordAt(downX, downY);
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (paginating) return true;
        int action = e.getActionMasked();
        float x = e.getX();
        float y = e.getY();

        if (active != null) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    active.onDown(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    active.onMove(x, y);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    active.onCancel();
                    break;
                case MotionEvent.ACTION_UP:
                    return active.onTouch(x, y);
                default:
                    break;
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                moved = false;
                longFired = false;
                postDelayed(longPressTask, 550);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                    moved = true;
                    removeCallbacks(longPressTask);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressTask);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressTask);
                if (longFired) return true;
                float dx = x - downX;
                float dy = y - downY;
                if (Math.abs(dx) > swipeMin && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    if (dx < 0) next();
                    else prev();
                    return true;
                }
                if (!moved) handleTap(x, y);
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float x, float y) {
        if (y > H - bottomH) {
            if (hitQ.contains(x, y)) {
                show(gotoOv);
            } else if (hitMenuB.contains(x, y)) {
                show(menu);
            } else if (hitHome.contains(x, y)) {
                if (settings.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                closeListener.onClose();
            } else if (hitTheme.contains(x, y)) {
                settings.theme = (settings.theme + 1) % 3;
                prefs.saveSettings(settings);
                invalidate();
            } else if (hitFont.contains(x, y)) {
                show(settingsOv);
            }
            return;
        }
        if (x < W * 0.33f) {
            prev();
        } else if (x > W * 0.67f) {
            next();
        } else {
            show(menu);
        }
    }

    private void selectWordAt(float x, float y) {
        if (paginating || active != null) return;
        Paginator.Page pg = pageAt(pageIndex);
        if (pg == null) return;
        float left = textCol.left;
        float top = textCol.top;
        for (Paginator.Line ln : pg.lines) {
            float baseline = top + ln.y;
            float a = ln.heading ? paginator.headAscent() : paginator.bodyAscent();
            float d = (ln.heading ? paginator.headingPaint() : paginator.bodyPaint()).descent();
            float topY = baseline + a - dpf * 4;
            float botY = baseline + d + dpf * 4;
            if (y < topY || y > botY) continue;
            for (Paginator.Word wd : ln.words) {
                if (x >= left + wd.x0 - dpf * 6 && x <= left + wd.x1 + dpf * 6) {
                    selWordRef = wd;
                    selWord = wd.text;
                    if (settings.haptic) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                    showDict(wd.text);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------ navigation

    private void flashTurn() {
        flashFrames = settings.flash ? 2 : 0;
        if (settings.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    void next() {
        int total = paginator.pageCount();
        if (total <= 0) return;
        if (pageIndex >= total - 1) {
            hint = "END OF BOOK";
            hintFrames = 2;
            invalidate();
            return;
        }
        pageIndex++;
        page = null;
        pageCacheIdx = -1;
        saveProgress();
        flashTurn();
        invalidate();
    }

    void prev() {
        if (pageIndex <= 0) {
            hint = "START OF BOOK";
            hintFrames = 2;
            invalidate();
            return;
        }
        pageIndex--;
        page = null;
        pageCacheIdx = -1;
        saveProgress();
        flashTurn();
        invalidate();
    }

    void gotoPage(int p) {
        int total = paginator.pageCount();
        if (total <= 0) return;
        if (p < 0) p = 0;
        if (p > total - 1) p = total - 1;
        pageIndex = p;
        page = null;
        pageCacheIdx = -1;
        saveProgress();
        flashTurn();
        invalidate();
    }

    private void saveProgress() {
        Paginator.Page pg = pageAt(pageIndex);
        if (pg == null) return;
        entry.lastPara = pg.startPara;
        entry.lastPage = pageIndex;
        entry.totalPages = paginator.pageCount();
        entry.lastReadAt = System.currentTimeMillis();
        prefs.savePosition(entry.id, pg.startPara, pageIndex, entry.totalPages);
    }

    private void hint(String s) {
        hint = s;
        hintFrames = 2;
        invalidate();
    }

    public boolean handleKey(int keyCode) {
        if (paginating) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                prev();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                next();
                return true;
            default:
                return false;
        }
    }

    public boolean onBack() {
        if (active != null) {
            hide();
            return true;
        }
        saveProgress();
        return false;
    }

    // -------------------------------------------------------------- overlays

    void show(Overlay o) {
        selWordRef = null;
        selWord = null;
        active = o;
        o.visible = true;
        invalidate();
    }

    void showDict(String word) {
        selWord = word;
        active = dictOv;
        dictOv.visible = true;
        dictOv.startLookup(word);
        invalidate();
    }

    void hide() {
        selWordRef = null;
        selWord = null;
        if (active != null) active.visible = false;
        active = null;
        invalidate();
    }

    private ArrayList<String> wrapPlain(String text, Paint p, float maxW) {
        ArrayList<String> out = new ArrayList<String>();
        if (text == null || text.length() == 0) return out;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (w.length() == 0) continue;
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

    // ---------------------------------------------------------------- menu

    class MenuOverlay extends Overlay {

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = drawPanelPx(cv, th, dpf * 262, dpf * 262);
            pMenuTitle.setColor(th.menuText);
            cv.drawText("MENU", r.centerX(), r.top + dpf * 30, pMenuTitle);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 14, r.top + dpf * 42, r.right - dpf * 14,
                    r.top + dpf * 42 + dpf, pLine);

            String[] items = {"Chapters", "Go to Page", "Settings", "Library", "Close"};
            float rowH = dpf * 37;
            float y = r.top + dpf * 50;
            for (int i = 0; i < items.length; i++) {
                hit(r.left, y, r.right, y + rowH, i);
                pMenu.setColor(th.menuText);
                cv.drawText(items[i], r.left + dpf * 18, baseCentered(pMenu, y, y + rowH), pMenu);
                pMenu.setColor(th.textSoft);
                cv.drawText("\u203A", r.right - dpf * 20, baseCentered(pMenu, y, y + rowH), pMenu);
                if (i < items.length - 1) {
                    pLine.setColor(th.line);
                    cv.drawRect(r.left + dpf * 14, y + rowH, r.right - dpf * 14, y + rowH + dpf, pLine);
                }
                y += rowH;
            }
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            active = null;
            menu.visible = false;
            if (a == 0) show(chaptersOv);
            else if (a == 1) show(gotoOv);
            else if (a == 2) show(settingsOv);
            else if (a == 3) {
                saveProgress();
                closeListener.onClose();
            } else {
                invalidate();
            }
            return true;
        }
    }

    // ------------------------------------------------------------- settings

    class SettingsOverlay extends Overlay {

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = drawPanelPx(cv, th, dpf * 302, dpf * 330);
            pMenuTitle.setColor(th.menuText);
            cv.drawText("SETTINGS", r.centerX(), r.top + dpf * 28, pMenuTitle);
            hit(r.right - dpf * 36, r.top + dpf * 8, r.right - dpf * 8, r.top + dpf * 36, 98);
            pMenuBold.setColor(th.menuText);
            cv.drawText("X", r.right - dpf * 22, r.top + dpf * 28, pMenuBold);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 14, r.top + dpf * 40, r.right - dpf * 14, r.top + dpf * 41, pLine);

            float rowH = dpf * 36;
            float y = r.top + dpf * 48;
            float ctlW = dpf * 150;
            float cx1 = r.right - dpf * 14;
            float cx0 = cx1 - ctlW;

            // row 0: font size
            rowLabel(cv, th, y, rowH, "Font size");
            segBtn(cv, th, cx0, y + dpf * 4, dpf * 40, rowH - dpf * 8, "-", 0 * 8, false);
            pMenuBold.setColor(th.menuText);
            cv.drawText(String.valueOf(settings.fontSize), cx0 + dpf * 58,
                    baseCentered(pMenuBold, y, y + rowH), pMenuBold);
            segBtn(cv, th, cx0 + ctlW - dpf * 40, y + dpf * 4, dpf * 40, rowH - dpf * 8, "+", 0 * 8 + 1, false);
            y += rowH;

            // row 1: line spacing
            rowLabel(cv, th, y, rowH, "Line spacing");
            segBtn(cv, th, cx0, y + dpf * 4, dpf * 40, rowH - dpf * 8, "-", 1 * 8, false);
            pMenuBold.setColor(th.menuText);
            cv.drawText(String.format(Locale.US, "%.2f", settings.lineSpacing), cx0 + dpf * 58,
                    baseCentered(pMenuBold, y, y + rowH), pMenuBold);
            segBtn(cv, th, cx0 + ctlW - dpf * 40, y + dpf * 4, dpf * 40, rowH - dpf * 8, "+", 1 * 8 + 1, false);
            y += rowH;

            // row 2: typeface
            rowLabel(cv, th, y, rowH, "Typeface");
            String[] fonts = {"Sans", "Serif", "Mono"};
            float segW = (ctlW - dpf * 8) / 3f;
            for (int i = 0; i < 3; i++) {
                segBtn(cv, th, cx0 + i * (segW + dpf * 4), y + dpf * 4, segW, rowH - dpf * 8,
                        fonts[i], 2 * 8 + i, settings.typeface == i);
            }
            y += rowH;

            // row 3: theme
            rowLabel(cv, th, y, rowH, "Theme");
            String[] themes = {"Gray", "White", "Night"};
            for (int i = 0; i < 3; i++) {
                segBtn(cv, th, cx0 + i * (segW + dpf * 4), y + dpf * 4, segW, rowH - dpf * 8,
                        themes[i], 3 * 8 + i, settings.theme == i);
            }
            y += rowH;

            // row 4: justify
            rowLabel(cv, th, y, rowH, "Justify text");
            float togW = (ctlW - dpf * 4) / 2f;
            segBtn(cv, th, cx0, y + dpf * 4, togW, rowH - dpf * 8, "On", 4 * 8, settings.justify);
            segBtn(cv, th, cx0 + togW + dpf * 4, y + dpf * 4, togW, rowH - dpf * 8, "Off", 4 * 8 + 1, !settings.justify);
            y += rowH;

            // row 5: page flash
            rowLabel(cv, th, y, rowH, "Page flash");
            segBtn(cv, th, cx0, y + dpf * 4, togW, rowH - dpf * 8, "On", 5 * 8, settings.flash);
            segBtn(cv, th, cx0 + togW + dpf * 4, y + dpf * 4, togW, rowH - dpf * 8, "Off", 5 * 8 + 1, !settings.flash);
            y += rowH;

            // done
            y += dpf * 6;
            hit(r.left + dpf * 14, y, r.right - dpf * 14, y + dpf * 36, 99);
            pFill.setColor(th.menuText);
            cv.drawRoundRect(new RectF(r.left + dpf * 14, y, r.right - dpf * 14, y + dpf * 36),
                    dpf * 3, dpf * 3, pFill);
            pMenuBold.setColor(th.menuBg);
            cv.drawText("DONE", r.centerX(), y + dpf * 24, pMenuBold);
        }

        private void rowLabel(Canvas cv, EinkTheme th, float y, float rowH, String label) {
            pMenu.setColor(th.menuText);
            cv.drawText(label, rLeft() + dpf * 16, baseCentered(pMenu, y, y + rowH), pMenu);
            pLine.setColor(th.line);
            cv.drawRect(rLeft() + dpf * 14, y + rowH, rRight() - dpf * 14, y + rowH + dpf, pLine);
        }

        private float rLeft() {
            return (W - Math.min(dpf * 302, W - dpf * 24)) / 2f;
        }

        private float rRight() {
            return (W + Math.min(dpf * 302, W - dpf * 24)) / 2f;
        }

        private void segBtn(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                            String label, int action, boolean activeSeg) {
            RectF r = new RectF(x, y, x + w, y + h);
            hit(x, y, x + w, y + h, action);
            if (activeSeg) {
                pFill.setColor(th.menuText);
                cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
                pMenuBold.setColor(th.menuBg);
            } else {
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                pStroke.setStrokeWidth(dpf * 1.2f);
                cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);
                pMenuBold.setColor(th.menuText);
            }
            float ts = pMenuBold.getTextSize();
            pMenuBold.setTextSize(label.length() > 2 ? spf * 11 : spf * 13);
            cv.drawText(label, r.centerX(), baseCentered(pMenuBold, y, y + h), pMenuBold);
            pMenuBold.setTextSize(ts);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a == 98 || a == 99) {
                prefs.saveSettings(settings);
                hide();
                return true;
            }
            int row = a / 8;
            int ctrl = a % 8;
            if (row == 0) {
                settings.fontSize += (ctrl == 0) ? -1 : 1;
                if (settings.fontSize < 12) settings.fontSize = 12;
                if (settings.fontSize > 34) settings.fontSize = 34;
                prefs.saveSettings(settings);
                repaginate();
            } else if (row == 1) {
                settings.lineSpacing += (ctrl == 0) ? -0.15f : 0.15f;
                if (settings.lineSpacing < 1.0f) settings.lineSpacing = 1.0f;
                if (settings.lineSpacing > 2.0f) settings.lineSpacing = 2.0f;
                prefs.saveSettings(settings);
                repaginate();
            } else if (row == 2) {
                settings.typeface = ctrl;
                prefs.saveSettings(settings);
                repaginate();
            } else if (row == 3) {
                settings.theme = ctrl;
                prefs.saveSettings(settings);
                invalidate();
            } else if (row == 4) {
                settings.justify = (ctrl == 0);
                prefs.saveSettings(settings);
                repaginate();
            } else if (row == 5) {
                settings.flash = (ctrl == 0);
                prefs.saveSettings(settings);
                invalidate();
            }
            return true;
        }
    }

    // --------------------------------------------------------------- go to

    class GoToOverlay extends Overlay {

        final StringBuilder num = new StringBuilder();

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = drawPanelPx(cv, th, dpf * 252, dpf * 336);
            pMenuTitle.setColor(th.menuText);
            cv.drawText("GO TO PAGE", r.centerX(), r.top + dpf * 28, pMenuTitle);
            hit(r.right - dpf * 36, r.top + dpf * 8, r.right - dpf * 8, r.top + dpf * 36, 90);
            pMenuBold.setColor(th.menuText);
            cv.drawText("X", r.right - dpf * 22, r.top + dpf * 28, pMenuBold);

            // display
            RectF disp = new RectF(r.left + dpf * 14, r.top + dpf * 40,
                    r.right - dpf * 14, r.top + dpf * 78);
            pFill.setColor(th.btnFill);
            cv.drawRoundRect(disp, dpf * 3, dpf * 3, pFill);
            pStroke.setColor(th.btnBorder);
            pStroke.setStrokeWidth(dpf * 1.4f);
            cv.drawRoundRect(disp, dpf * 3, dpf * 3, pStroke);
            pMenuBold.setColor(th.menuText);
            String cur = num.length() > 0 ? num.toString() : "page";
            cv.drawText(cur, disp.centerX(), baseCentered(pMenuBold, disp.top, disp.bottom), pMenuBold);

            // keypad
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "GO"};
            float gap = dpf * 6;
            float kw = (r.width() - dpf * 28 - gap * 2) / 3f;
            float kh = dpf * 40;
            float ky = r.top + dpf * 90;
            for (int i = 0; i < keys.length; i++) {
                int row = i / 3;
                int col = i % 3;
                float kx = r.left + dpf * 14 + col * (kw + gap);
                float kyy = ky + row * (kh + gap);
                hit(kx, kyy, kx + kw, kyy + kh, i);
                boolean filled = (i == 11);
                if (filled) {
                    pFill.setColor(th.menuText);
                    cv.drawRoundRect(new RectF(kx, kyy, kx + kw, kyy + kh), dpf * 3, dpf * 3, pFill);
                    pMenuBold.setColor(th.menuBg);
                } else {
                    pFill.setColor(th.btnFill);
                    cv.drawRoundRect(new RectF(kx, kyy, kx + kw, kyy + kh), dpf * 3, dpf * 3, pFill);
                    pStroke.setColor(th.btnBorder);
                    cv.drawRoundRect(new RectF(kx, kyy, kx + kw, kyy + kh), dpf * 3, dpf * 3, pStroke);
                    pMenuBold.setColor(th.menuText);
                }
                float ts = pMenuBold.getTextSize();
                if (keys[i].length() > 2) pMenuBold.setTextSize(spf * 11);
                cv.drawText(keys[i], kx + kw / 2f, kyy + kh / 2f - (pMenuBold.ascent() + pMenuBold.descent()) / 2f, pMenuBold);
                pMenuBold.setTextSize(ts);
            }
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a == 90) {
                hide();
                return true;
            }
            if (a <= 8) {
                if (num.length() < 5) num.append((char) ('1' + a));
            } else if (a == 9) {
                if (num.length() > 0) num.deleteCharAt(num.length() - 1);
            } else if (a == 10) {
                if (num.length() < 5) num.append('0');
            } else if (a == 11) {
                try {
                    int v = Integer.parseInt(num.toString());
                    int total = paginator.pageCount();
                    if (v >= 1 && v <= total) {
                        num.setLength(0);
                        hide();
                        gotoPage(v - 1);
                        return true;
                    }
                } catch (NumberFormatException ignored) { }
            }
            invalidate();
            return true;
        }
    }

    // ------------------------------------------------------------ chapters

    class ChaptersOverlay extends Overlay {

        float scroll = 0;
        float downY0 = 0;
        boolean dragging = false;

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = drawPanelPx(cv, th, W * 0.88f, H * 0.62f);
            pMenuTitle.setColor(th.menuText);
            cv.drawText("CHAPTERS", r.centerX(), r.top + dpf * 28, pMenuTitle);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 14, r.top + dpf * 40, r.right - dpf * 14,
                    r.top + dpf * 41, pLine);

            float listTop = r.top + dpf * 48;
            float listBot = r.bottom - dpf * 12;
            int n = book.chapterTitles.size();
            float rowH = dpf * 34;
            float contentH = Math.max(1, n * rowH);
            float visibleH = listBot - listTop;
            float maxScroll = Math.max(0, contentH - visibleH);
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0) scroll = 0;

            cv.save();
            cv.clipRect(r.left, listTop, r.right, listBot);
            for (int i = 0; i < n; i++) {
                float y = listTop + i * rowH - scroll;
                if (y + rowH < listTop || y > listBot) continue;
                hit(r.left, y, r.right, y + rowH, i);
                String label = (i + 1) + ".  " + book.chapterTitles.get(i);
                CharSequence shown = TextUtils.ellipsize(label, pMenu,
                        r.width() - dpf * 90, TextUtils.TruncateAt.END);
                pMenu.setColor(th.menuText);
                cv.drawText(shown.toString(), r.left + dpf * 16,
                        baseCentered(pMenu, y, y + rowH), pMenu);
                pMenuR.setColor(th.textSoft);
                int pnum = paginator.pageOfPara(book.chapterParas.get(i)) + 1;
                cv.drawText("p. " + pnum, r.right - dpf * 16,
                        baseCentered(pMenu, y, y + rowH), pMenuR);
                if (i < n - 1) {
                    pLine.setColor(th.line);
                    cv.drawRect(r.left + dpf * 14, y + rowH, r.right - dpf * 14, y + rowH + dpf, pLine);
                }
            }
            cv.restore();

            if (maxScroll > 0) {
                float trackH = listBot - listTop;
                float thumbH = Math.max(dpf * 30, trackH * visibleH / contentH);
                float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
                pFill.setColor(th.textSoft);
                cv.drawRoundRect(new RectF(r.right - dpf * 8, thumbY, r.right - dpf * 5,
                        thumbY + thumbH), dpf * 2, dpf * 2, pFill);
            }
        }

        void onDown(float x, float y) {
            downY0 = y;
            dragging = false;
        }

        void onMove(float x, float y) {
            float dy = y - downY0;
            if (!dragging && Math.abs(dy) > touchSlop) dragging = true;
            if (dragging) {
                scroll -= dy;
                downY0 = y;
                invalidate();
            }
        }

        void onCancel() {
            dragging = false;
        }

        boolean onTouch(float x, float y) {
            if (dragging) {
                dragging = false;
                return true;
            }
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a < book.chapterParas.size()) {
                int target = paginator.pageOfPara(book.chapterParas.get(a));
                hide();
                gotoPage(target);
            }
            return true;
        }
    }

    // ---------------------------------------------------------- dictionary

    class DictOverlay extends Overlay {

        int state = 0; // 0 loading, 1 result, 2 error
        ArrayList<String> resultLines = null;
        String error = null;

        void startLookup(final String word) {
            state = 0;
            resultLines = null;
            error = null;
            invalidate();
            Dict.lookup(word, new Dict.Callback() {
                public void onResult(final ArrayList<String> lines) {
                    post(new Runnable() {
                        public void run() {
                            if (active != DictOverlay.this) return;
                            resultLines = lines;
                            state = 1;
                            invalidate();
                        }
                    });
                }

                public void onError(final String msg) {
                    post(new Runnable() {
                        public void run() {
                            if (active != DictOverlay.this) return;
                            error = msg;
                            state = 2;
                            invalidate();
                        }
                    });
                }
            });
        }

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            float ph = H * 0.42f;
            RectF r = new RectF(padX, H - bottomH - dpf * 12 - ph,
                    W - padX, H - bottomH - dpf * 12);
            pFill.setColor(th.menuBg);
            cv.drawRoundRect(r, dpf * 4, dpf * 4, pFill);
            pStroke.setColor(th.menuBorder);
            pStroke.setStrokeWidth(dpf * 2f);
            cv.drawRoundRect(r, dpf * 4, dpf * 4, pStroke);

            String word = selWord == null ? "" : selWord;
            CharSequence shown = TextUtils.ellipsize(word, pDictW, r.width() - dpf * 70,
                    TextUtils.TruncateAt.END);
            pDictW.setColor(th.menuText);
            cv.drawText(shown.toString(), r.left + dpf * 14, r.top + dpf * 28, pDictW);
            pDictTag.setColor(th.textSoft);
            cv.drawText("WIKTIONARY", r.left + dpf * 14, r.top + dpf * 44, pDictTag);

            hit(r.right - dpf * 38, r.top + dpf * 8, r.right - dpf * 8, r.top + dpf * 38, 91);
            pMenuBold.setColor(th.menuText);
            cv.drawText("X", r.right - dpf * 23, r.top + dpf * 28, pMenuBold);

            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.top + dpf * 52, r.right - dpf * 12,
                    r.top + dpf * 53, pLine);

            float cy = r.top + dpf * 66;
            float maxCy = r.bottom - dpf * 48;
            float textW = r.width() - dpf * 28;

            if (state == 0) {
                pDict.setColor(th.menuText);
                cv.drawText("Looking up ...", r.left + dpf * 14, cy + dpf * 14, pDict);
            } else if (state == 2) {
                pDict.setColor(th.textSoft);
                ArrayList<String> lines = wrapPlain(error == null ? "" : error, pDict, textW);
                for (String ln : lines) {
                    if (cy > maxCy) break;
                    cv.drawText(ln, r.left + dpf * 14, cy + dpf * 14, pDict);
                    cy += dpf * 20;
                }
            } else {
                pDict.setColor(th.menuText);
                boolean truncated = false;
                for (String s : resultLines) {
                    if (truncated) break;
                    for (String ln : wrapPlain(s, pDict, textW)) {
                        if (cy > maxCy) {
                            truncated = true;
                            break;
                        }
                        cv.drawText(ln, r.left + dpf * 14, cy + dpf * 14, pDict);
                        cy += dpf * 20;
                    }
                    cy += dpf * 6;
                }
                if (truncated) {
                    pDict.setColor(th.textSoft);
                    cv.drawText("...", r.left + dpf * 14, Math.min(cy + dpf * 14, maxCy), pDict);
                }
            }

            if (state == 2) {
                hit(r.left + dpf * 12, r.bottom - dpf * 40, r.left + dpf * 86, r.bottom - dpf * 12, 92);
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(new RectF(r.left + dpf * 12, r.bottom - dpf * 40,
                        r.left + dpf * 86, r.bottom - dpf * 12), dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                cv.drawRoundRect(new RectF(r.left + dpf * 12, r.bottom - dpf * 40,
                        r.left + dpf * 86, r.bottom - dpf * 12), dpf * 3, dpf * 3, pStroke);
                pMenuBold.setColor(th.menuText);
                cv.drawText("RETRY", r.left + dpf * 49, r.bottom - dpf * 22, pMenuBold);
            }

            hit(r.right - dpf * 90, r.bottom - dpf * 40, r.right - dpf * 12, r.bottom - dpf * 12, 93);
            pFill.setColor(th.menuText);
            cv.drawRoundRect(new RectF(r.right - dpf * 90, r.bottom - dpf * 40,
                    r.right - dpf * 12, r.bottom - dpf * 12), dpf * 3, dpf * 3, pFill);
            pMenuBold.setColor(th.menuBg);
            cv.drawText("CLOSE", r.right - dpf * 51, r.bottom - dpf * 22, pMenuBold);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a == 91 || a == 93) {
                hide();
                return true;
            }
            if (a == 92 && selWord != null) {
                startLookup(selWord);
            }
            invalidate();
            return true;
        }
    }
}

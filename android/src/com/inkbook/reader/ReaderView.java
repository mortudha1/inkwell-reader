package com.inkbook.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

/**
 * The reading screen. A calm, premium e-ink reading experience:
 * a thin top bar (title + contents dropdown, page and progress), a centered
 * reading column sized like a printed book, a floating monochrome toolbar
 * (contents, bookmarks, highlights, notes, typography, display), a subtle
 * progress hairline, and a distraction-free mode that hides everything.
 *
 * Typography is remembered per book. Bookmarks, highlights and notes are
 * anchored to paragraphs and persisted.
 */
public final class ReaderView extends View {

    public interface CloseListener {
        void onClose();
    }

    // ------------------------------------------------------------ inputs

    private final Book book;
    private final Prefs prefs;
    private final Prefs.Settings display;   // global display prefs (theme, flash, haptic, brightness)
    private final Prefs.BookSettings style; // per-book typography
    private final Prefs.BookEntry entry;
    private final CloseListener closeListener;
    private final Paginator paginator;
    private final int anchorPara;

    private final float dpf;   // 1dp in px
    private final float spf;   // 1sp in px
    private final float touchSlop;
    private final float swipeMin;

    // ------------------------------------------------------------ layout

    private int W, H;
    private boolean landscape;
    private float statusH, toolH;
    private final RectF textCol = new RectF();

    // ------------------------------------------------------------- state

    private boolean paginating = false;
    private int progressPct = 0;
    private int paginateAnchor;
    private int pageIndex = 0;
    private Paginator.Page page;
    private int pageCacheIdx = -1;

    private int flashFrames = 0;   // dark e-ink blink frame
    private int ghostFrames = 0;   // faint ghosting residue frame
    private String hint = null;
    private int hintFrames = 0;
    private boolean chromeHintShown = false;

    private boolean chrome = true; // true = top bar + toolbar visible

    // ------------------------------------------------------------- marks

    private final ArrayList<Prefs.Mark> marks = new ArrayList<Prefs.Mark>();
    private final HashSet<Integer> highlightParas = new HashSet<Integer>();

    private Paginator.Word selWordRef = null;
    private String selWord = null;
    private int selPara = -1;

    // ----------------------------------------------------------- overlays

    private Overlay active = null;
    private final ChaptersOverlay chaptersOv = new ChaptersOverlay();
    private final MarksOverlay bookmarksOv = new MarksOverlay(0);
    private final MarksOverlay highlightsOv = new MarksOverlay(1);
    private final MarksOverlay notesOv = new MarksOverlay(2);
    private final SettingsOverlay settingsOv = new SettingsOverlay();
    private final DisplayOverlay displayOv = new DisplayOverlay();
    private final GoToOverlay gotoOv = new GoToOverlay();
    private final DictOverlay dictOv = new DictOverlay();

    // ------------------------------------------------------------ hit areas

    private final RectF hitBack = new RectF();
    private final RectF hitToc = new RectF();
    private final RectF hitCounter = new RectF();
    private final RectF[] toolHits = new RectF[6];

    private float downX, downY;
    private boolean moved, longFired;

    // ---------------------------------------------------------------- paints

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint pTop = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pTopR = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pPanelTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pRow = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pRowR = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pRowVal = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBtn = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBtnSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pHint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pExcerpt = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDict = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDictW = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pDictTag = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pAa = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pPagT = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pPagSub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pSelTxt = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private void initPaints(Context c) {
        pStroke.setStyle(Paint.Style.STROKE);

        pTop.setTypeface(Typeface.DEFAULT_BOLD);
        pTop.setTextSize(spf * 12);
        pTop.setLetterSpacing(0.06f);

        pTopR.setTextSize(spf * 12);
        pTopR.setTextAlign(Paint.Align.RIGHT);

        pPanelTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pPanelTitle.setTextSize(spf * 11);
        pPanelTitle.setLetterSpacing(0.16f);

        pRow.setTextSize(spf * 15);
        pRowR.setTextSize(spf * 13);
        pRowR.setTextAlign(Paint.Align.RIGHT);

        pRowVal.setTypeface(Typeface.DEFAULT_BOLD);
        pRowVal.setTextSize(spf * 14);
        pRowVal.setTextAlign(Paint.Align.RIGHT);

        pBtn.setTypeface(Typeface.DEFAULT_BOLD);
        pBtn.setTextSize(spf * 12);
        pBtn.setTextAlign(Paint.Align.CENTER);

        pBtnSmall.setTypeface(Typeface.DEFAULT_BOLD);
        pBtnSmall.setTextSize(spf * 10.5f);
        pBtnSmall.setTextAlign(Paint.Align.CENTER);
        pBtnSmall.setLetterSpacing(0.1f);

        pHint.set(pBtnSmall);
        pHint.setTextSize(spf * 11);

        pExcerpt.setTextSize(spf * 14);

        pDict.setTextSize(spf * 14);
        pDictW.setTypeface(Typeface.SERIF);
        pDictW.setFakeBoldText(true);
        pDictW.setTextSize(spf * 19);
        pDictTag.setTextSize(spf * 9);
        pDictTag.setLetterSpacing(0.15f);

        pAa.setTypeface(Typeface.SERIF);
        pAa.setTextSize(spf * 17);
        pAa.setTextAlign(Paint.Align.CENTER);

        pPagT.setTypeface(Typeface.SERIF);
        pPagT.setTextSize(spf * 21);
        pPagT.setTextAlign(Paint.Align.CENTER);

        pPagSub.setTextSize(spf * 11);
        pPagSub.setLetterSpacing(0.14f);
        pPagSub.setTextAlign(Paint.Align.CENTER);

        pSelTxt.setTextSize(spf * 18);
    }

    // ------------------------------------------------------------- lifecycle

    public ReaderView(Context c, Book book, Prefs.BookEntry entry, Prefs prefs,
                      int anchorPara, CloseListener listener) {
        super(c);
        this.book = book;
        this.entry = entry;
        this.prefs = prefs;
        this.display = prefs.settings();
        this.style = prefs.bookSettings(entry.id);
        this.closeListener = listener;
        this.anchorPara = anchorPara;
        this.paginateAnchor = anchorPara;

        dpf = Util.dp(c, 1f);
        spf = Util.sp(c, 1f);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        swipeMin = dpf * 70;

        // marks
        marks.addAll(prefs.marks(entry.id));
        for (Prefs.Mark m : marks) {
            if (m.kind == 1) highlightParas.add(m.para);
        }

        paginator = new Paginator(book);
        initPaints(c);
        setKeepScreenOn(true);
        applyBrightness();
    }

    public Prefs.BookEntry entry() {
        return entry;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        // keep the current position when the device rotates
        if (page != null) paginateAnchor = page.startPara;
        landscape = w > h;
        statusH = dpf * 42;
        toolH = dpf * 54;
        computeColumn();
        configurePaginator();
        startPagination();
    }

    /** Centered reading column: 70-80% of the width, capped for line length. */
    private void computeColumn() {
        float[] fL = {0.82f, 0.75f, 0.67f, 0.58f};
        float[] fP = {0.92f, 0.86f, 0.79f, 0.70f};
        int mi = Math.max(0, Math.min(3, style.margin));
        float f = (landscape ? fL : fP)[mi];
        float fontPx = style.fontSize * spf;
        float cap = fontPx * 44f;              // keeps lines under ~88 characters
        float colW = Math.min(W * f, cap);
        colW = Math.min(colW, W - dpf * 32);
        float top = statusH + dpf * 30;
        float bot = H - toolH - dpf * 26;
        if (bot - top < fontPx * 4) bot = top + fontPx * 4;
        textCol.set((W - colW) / 2f, top, (W + colW) / 2f, bot);
    }

    private void configurePaginator() {
        float fontPx = style.fontSize * spf;
        paginator.configure((int) textCol.width(), (int) textCol.height(),
                fontPx, style.lineSpacing, style.typeface, style.justify, style.paraSpacing);
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
                                if (display.flash) {
                                    flashFrames = 1;
                                    ghostFrames = 1;
                                }
                                if (!chromeHintShown) {
                                    chromeHintShown = true;
                                    showHint("TAP THE CENTER TO HIDE THE CONTROLS");
                                } else {
                                    maybeChapterHint(pageAt(pageIndex));
                                }
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

    /** Re-lays out the book for changed typography, keeping the open overlay. */
    void repaginate() {
        if (paginating) return;
        Paginator.Page pg = pageAt(pageIndex);
        paginateAnchor = (pg != null) ? pg.startPara : Math.max(0, anchorPara);
        configurePaginator();
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
        EinkTheme th = EinkTheme.theme(display.theme);
        cv.drawColor(th.bg);
        cv.drawRect(0, 0, W, H, Util.noisePaint());

        if (flashFrames > 0) {
            flashFrames--;
            cv.drawColor(th.flash);
            postInvalidateDelayed(55);
            return;
        }

        if (paginating) {
            drawPaginating(cv, th);
            return;
        }

        int total = paginator.pageCount();
        if (total <= 0) {
            pPagSub.setColor(th.textSoft);
            cv.drawText("NOTHING TO SHOW", W / 2f, H / 2f, pPagSub);
            return;
        }
        if (pageIndex > total - 1) pageIndex = total - 1;
        if (pageIndex < 0) pageIndex = 0;

        if (chrome) drawTopBar(cv, th, total);

        Paginator.Page pg = pageAt(pageIndex);
        if (pg != null) drawPageText(cv, th, pg);
        if (pg != null && pageBookmarked()) {
            // tiny ribbon above the column's right edge marks a bookmarked page
            iconBookmark(cv, textCol.right - dpf * 14, textCol.top - dpf * 11, th, true);
        }

        if (chrome) drawToolbar(cv, th);
        drawProgress(cv, th, total);

        if (active != null) {
            pFill.setColor(th.scrim);
            cv.drawRect(0, 0, W, H, pFill);
            active.draw(cv, th);
        }

        if (hint != null && hintFrames > 0) {
            hintFrames--;
            pHint.setColor(th.textSoft);
            cv.drawText(hint, W / 2f, H - toolH - dpf * 22, pHint);
            if (hintFrames <= 0) hint = null;
            postInvalidateDelayed(1100);
        }
        if (ghostFrames > 0) {
            ghostFrames--;
            pFill.setColor(th.ghost);
            cv.drawRect(0, 0, W, H, pFill);
            postInvalidateDelayed(60);
            return;
        }
    }

    private float baseCentered(Paint p, float top, float bottom) {
        return ((top + bottom) / 2f) - (p.ascent() + p.descent()) / 2f;
    }

    // ----------------------------------------------------------- top bar

    private void drawTopBar(Canvas cv, EinkTheme th, int total) {
        pLine.setColor(th.line);
        cv.drawRect(0, statusH, W, statusH + dpf, pLine);
        float cy = statusH / 2f;

        // back to library
        hitBack.set(dpf * 2, 0, dpf * 46, statusH);
        iconChevronLeft(cv, dpf * 25, cy, th, th.text);

        // title
        String t = book.title == null ? "" : book.title.toUpperCase(Locale.getDefault());
        float tx = dpf * 48;
        CharSequence shown = TextUtils.ellipsize(t, pTop, W * 0.46f, TextUtils.TruncateAt.END);
        pTop.setColor(th.text);
        cv.drawText(shown.toString(), tx, baseCentered(pTop, 0, statusH), pTop);
        float tw = pTop.measureText(shown.toString());

        // contents dropdown affordance
        float gx = tx + tw + dpf * 16;
        hitToc.set(tx + tw, 0, Math.min(gx + dpf * 24, W * 0.55f), statusH);
        pStroke.setColor(th.textSoft);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawLine(gx, cy - dpf * 2.5f, gx + dpf * 4.5f, cy + dpf * 1.5f, pStroke);
        cv.drawLine(gx + dpf * 4.5f, cy + dpf * 1.5f, gx + dpf * 9, cy - dpf * 2.5f, pStroke);

        // right side: page / total + progress
        String right;
        if (total > 0) {
            int pct = Math.max(0, Math.min(100, (pageIndex + 1) * 100 / total));
            right = (pageIndex + 1) + " / " + total + "  \u00B7  " + pct + "%";
        } else {
            right = "\u2014";
        }
        float rw = pTopR.measureText(right);
        float rx = W - dpf * 14 - rw;
        pTopR.setColor(th.textSoft);
        cv.drawText(right, rx, baseCentered(pTopR, 0, statusH), pTopR);
        hitCounter.set(rx - dpf * 12, 0, W, statusH);
    }

    // ----------------------------------------------------------- toolbar

    private void drawToolbar(Canvas cv, EinkTheme th) {
        float top = H - toolH;
        pLine.setColor(th.line);
        cv.drawRect(0, top, W, top + dpf, pLine);

        float inset = Math.max(dpf * 18, W * 0.06f);
        float slotW = (W - inset * 2) / 6f;
        float bH = dpf * 40;
        float bTop = top + (toolH - bH) / 2f;
        for (int i = 0; i < 6; i++) {
            RectF r = new RectF(inset + i * slotW + dpf * 5, bTop,
                    inset + (i + 1) * slotW - dpf * 5, bTop + bH);
            toolHits[i] = r;
            pFill.setColor(th.btnFill);
            cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
            pStroke.setColor(th.btnBorder);
            pStroke.setStrokeWidth(dpf * 1.1f);
            cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);
            drawToolIcon(cv, th, i, r.centerX(), r.centerY());
        }
    }

    private void drawToolIcon(Canvas cv, EinkTheme th, int id, float cx, float cy) {
        switch (id) {
            case 0:
                iconList(cv, cx, cy, th);
                break;
            case 1:
                iconBookmark(cv, cx, cy, th, pageBookmarked());
                break;
            case 2:
                iconPen(cv, cx, cy, th);
                break;
            case 3:
                iconNote(cv, cx, cy, th);
                break;
            case 4:
                pAa.setColor(th.text);
                cv.drawText("Aa", cx, baseCentered(pAa, cy - dpf * 11, cy + dpf * 11), pAa);
                break;
            case 5:
                iconSun(cv, cx, cy, th);
                break;
        }
    }

    // ---------------------------------------------------------- progress

    private void drawProgress(Canvas cv, EinkTheme th, int total) {
        float h = dpf * 3;
        float y = H - h;
        pFill.setColor(th.line);
        cv.drawRect(0, y, W, H, pFill);
        if (total > 0) {
            float w = W * (float) (pageIndex + 1) / (float) total;
            pFill.setColor(th.textSoft);
            cv.drawRect(0, y, w, H, pFill);
        }
    }

    // --------------------------------------------------------- paginating

    private void drawPaginating(Canvas cv, EinkTheme th) {
        float cy = H * 0.40f;
        pPagT.setColor(th.text);
        CharSequence shown = TextUtils.ellipsize(
                book.title == null ? "" : book.title, pPagT, W * 0.72f, TextUtils.TruncateAt.END);
        cv.drawText(shown.toString(), W / 2f, cy, pPagT);
        if (book.author != null && book.author.length() > 0) {
            pRowR.setTextAlign(Paint.Align.CENTER);
            pRowR.setColor(th.textSoft);
            cv.drawText(book.author, W / 2f, cy + dpf * 26, pRowR);
            pRowR.setTextAlign(Paint.Align.RIGHT);
        }
        pLine.setColor(th.line);
        cv.drawRect(W / 2f - dpf * 22, cy + dpf * 46, W / 2f + dpf * 22, cy + dpf * 47, pLine);

        pPagSub.setColor(th.textSoft);
        cv.drawText("FORMATTING  \u00B7  " + progressPct + "%", W / 2f, cy + dpf * 76, pPagSub);

        float bw = Math.min(dpf * 260, W * 0.5f);
        float x0 = W / 2f - bw / 2f;
        float y = cy + dpf * 92;
        pLine.setColor(th.line);
        cv.drawRect(x0, y, x0 + bw, y + dpf * 5, pLine);
        pFill.setColor(th.textSoft);
        cv.drawRect(x0, y, x0 + bw * progressPct / 100f, y + dpf * 5, pFill);
    }

    // ----------------------------------------------------------- page text

    private void drawPageText(Canvas cv, EinkTheme th, Paginator.Page pg) {
        float left = textCol.left;
        float top = textCol.top;
        Paint body = paginator.bodyPaint();
        Paint head = paginator.headingPaint();

        // highlight bands behind marked paragraphs
        for (Paginator.Line ln : pg.lines) {
            if (highlightParas.contains(ln.para)) {
                Paint p = ln.heading ? head : body;
                float a = ln.heading ? paginator.headAscent() : paginator.bodyAscent();
                float by = top + ln.y;
                pFill.setColor(th.selBand);
                cv.drawRect(left - dpf * 8, by + a - dpf * 3,
                        left + textCol.width() + dpf * 8, by + p.descent() + dpf * 3, pFill);
            }
        }

        for (Paginator.Line ln : pg.lines) {
            Paint p = ln.heading ? head : body;
            p.setColor(th.text);
            float by = top + ln.y;
            for (Paginator.Word wd : ln.words) {
                if (wd == selWordRef) {
                    float a = ln.heading ? paginator.headAscent() : paginator.bodyAscent();
                    float d = (ln.heading ? paginator.headingPaint() : paginator.bodyPaint()).descent();
                    pFill.setColor(th.text);
                    cv.drawRect(left + wd.x0 - dpf * 2, by + a - dpf * 2,
                            left + wd.x1 + dpf * 2, by + d + dpf * 2, pFill);
                    pSelTxt.setColor(th.selText);
                    pSelTxt.setTextSize(p.getTextSize());
                    pSelTxt.setTypeface(p.getTypeface());
                    cv.drawText(wd.text, left + wd.x0, by, pSelTxt);
                } else {
                    cv.drawText(wd.text, left + wd.x0, by, p);
                }
            }
        }
    }

    // ---------------------------------------------------------------- icons

    private void iconChevronLeft(Canvas cv, float cx, float cy, EinkTheme th, int color) {
        pStroke.setColor(color);
        pStroke.setStrokeWidth(dpf * 1.8f);
        cv.drawLine(cx + dpf * 3, cy - dpf * 5.5f, cx - dpf * 3, cy, pStroke);
        cv.drawLine(cx - dpf * 3, cy, cx + dpf * 3, cy + dpf * 5.5f, pStroke);
    }

    private void iconList(Canvas cv, float cx, float cy, EinkTheme th) {
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.6f);
        float w = dpf * 17, gap = dpf * 5.5f;
        for (int i = -1; i <= 1; i++) {
            float y = cy + i * gap;
            pFill.setColor(th.text);
            cv.drawCircle(cx - w / 2f + dpf * 1.2f, y, dpf * 1.2f, pFill);
            cv.drawLine(cx - w / 2f + dpf * 5, y, cx + w / 2f, y, pStroke);
        }
    }

    private void iconBookmark(Canvas cv, float cx, float cy, EinkTheme th, boolean filled) {
        float w = dpf * 12, h = dpf * 17;
        float x0 = cx - w / 2f, x1 = cx + w / 2f, y0 = cy - h / 2f, y1 = cy + h / 2f;
        Path path = new Path();
        path.moveTo(x0, y0);
        path.lineTo(x1, y0);
        path.lineTo(x1, y1);
        path.lineTo(cx, y1 - dpf * 5);
        path.lineTo(x0, y1);
        path.close();
        if (filled) {
            pFill.setColor(th.text);
            cv.drawPath(path, pFill);
        } else {
            pStroke.setColor(th.text);
            pStroke.setStrokeWidth(dpf * 1.6f);
            cv.drawPath(path, pStroke);
        }
    }

    private void iconPen(Canvas cv, float cx, float cy, EinkTheme th) {
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.6f);
        cv.drawLine(cx - dpf * 7, cy + dpf * 6, cx + dpf * 3, cy - dpf * 4, pStroke);
        Path t = new Path();
        t.moveTo(cx + dpf * 1.5f, cy - dpf * 5.5f);
        t.lineTo(cx + dpf * 8, cy - dpf * 8);
        t.lineTo(cx + dpf * 4.5f, cy - dpf * 2);
        t.close();
        pFill.setColor(th.text);
        cv.drawPath(t, pFill);
        cv.drawLine(cx - dpf * 7, cy + dpf * 10, cx + dpf * 5, cy + dpf * 10, pStroke);
    }

    private void iconNote(Canvas cv, float cx, float cy, EinkTheme th) {
        float w = dpf * 13, h = dpf * 16;
        float x0 = cx - w / 2f, x1 = cx + w / 2f, y0 = cy - h / 2f, y1 = cy + h / 2f;
        float f = dpf * 4;
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.4f);
        Path pg = new Path();
        pg.moveTo(x0, y0);
        pg.lineTo(x1 - f, y0);
        pg.lineTo(x1, y0 + f);
        pg.lineTo(x1, y1);
        pg.lineTo(x0, y1);
        pg.close();
        cv.drawPath(pg, pStroke);
        cv.drawLine(x1 - f, y0, x1 - f, y0 + f, pStroke);
        cv.drawLine(x1 - f, y0 + f, x1, y0 + f, pStroke);
        cv.drawLine(x0 + dpf * 3, y0 + dpf * 7.5f, x1 - dpf * 3, y0 + dpf * 7.5f, pStroke);
        cv.drawLine(x0 + dpf * 3, y0 + dpf * 11, x1 - dpf * 5, y0 + dpf * 11, pStroke);
    }

    private void iconSun(Canvas cv, float cx, float cy, EinkTheme th) {
        pStroke.setColor(th.text);
        pStroke.setStrokeWidth(dpf * 1.5f);
        cv.drawCircle(cx, cy, dpf * 4, pStroke);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            float x1 = cx + (float) Math.cos(a) * dpf * 6;
            float y1 = cy + (float) Math.sin(a) * dpf * 6;
            float x2 = cx + (float) Math.cos(a) * dpf * 8.5f;
            float y2 = cy + (float) Math.sin(a) * dpf * 8.5f;
            cv.drawLine(x1, y1, x2, y2, pStroke);
        }
    }

    private void drawX(Canvas cv, EinkTheme th, float cx, float cy) {
        pStroke.setColor(th.menuText);
        pStroke.setStrokeWidth(dpf * 1.6f);
        float s = dpf * 5;
        cv.drawLine(cx - s, cy - s, cx + s, cy + s, pStroke);
        cv.drawLine(cx + s, cy - s, cx - s, cy + s, pStroke);
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
                postDelayed(longPressTask, 500);
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
        if (chrome) {
            if (y < statusH + dpf * 6) {
                if (hitBack.contains(x, y)) {
                    if (display.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    saveProgress();
                    releaseBrightness();
                    closeListener.onClose();
                } else if (hitToc.contains(x, y)) {
                    show(chaptersOv);
                } else if (hitCounter.contains(x, y)) {
                    show(gotoOv);
                }
                return;
            }
            if (y > H - toolH - dpf * 6) {
                int b = toolAt(x, y);
                if (b == 0) show(chaptersOv);
                else if (b == 1) toggleBookmark();
                else if (b == 2) show(highlightsOv);
                else if (b == 3) show(notesOv);
                else if (b == 4) show(settingsOv);
                else if (b == 5) show(displayOv);
                return;
            }
        }
        if (x < W * 0.32f) {
            prev();
        } else if (x > W * 0.68f) {
            next();
        } else {
            // distraction-free toggle
            chrome = !chrome;
            if (display.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            invalidate();
        }
    }

    private int toolAt(float x, float y) {
        for (int i = 0; i < 6; i++) {
            if (toolHits[i] != null && toolHits[i].contains(x, y)) return i;
        }
        return -1;
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
            Paint p = ln.heading ? paginator.headingPaint() : paginator.bodyPaint();
            float d = p.descent();
            float topY = baseline + a - dpf * 4;
            float botY = baseline + d + dpf * 4;
            if (y < topY || y > botY) continue;
            for (Paginator.Word wd : ln.words) {
                if (x >= left + wd.x0 - dpf * 6 && x <= left + wd.x1 + dpf * 6) {
                    selWordRef = wd;
                    selWord = wd.text;
                    selPara = ln.para;
                    if (display.haptic) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                    showDict(wd.text);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------ navigation

    /** Optional e-ink full-refresh blink; disabled by default for speed. */
    private void flashTurn() {
        if (display.flash) {
            flashFrames = 1;
            ghostFrames = 1;
        }
        if (display.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    void next() {
        int total = paginator.pageCount();
        if (total <= 0) return;
        if (pageIndex >= total - 1) {
            showHint("END OF BOOK");
            invalidate();
            return;
        }
        pageIndex++;
        page = null;
        pageCacheIdx = -1;
        saveProgress();
        flashTurn();
        maybeChapterHint(pageAt(pageIndex));
        invalidate();
    }

    void prev() {
        if (pageIndex <= 0) {
            showHint("START OF BOOK");
            invalidate();
            return;
        }
        pageIndex--;
        page = null;
        pageCacheIdx = -1;
        saveProgress();
        flashTurn();
        maybeChapterHint(pageAt(pageIndex));
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
        maybeChapterHint(pageAt(pageIndex));
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

    private void showHint(String s) {
        hint = s;
        hintFrames = 2;
    }

    /** Announce a chapter title briefly when a page turn lands on one. */
    private void maybeChapterHint(Paginator.Page pg) {
        if (pg == null) return;
        if (!book.headings.contains(pg.startPara)) return;
        int ci = book.chapterParas.indexOf(pg.startPara);
        if (ci >= 0 && ci < book.chapterTitles.size()) {
            String t = book.chapterTitles.get(ci);
            if (t != null && t.length() > 0) showHint(t.toUpperCase(Locale.getDefault()));
        }
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
            case KeyEvent.KEYCODE_SPACE:
                next();
                return true;
            case KeyEvent.KEYCODE_MENU:
                chrome = !chrome;
                invalidate();
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
        if (!chrome) {
            chrome = true;
            invalidate();
            return true;
        }
        saveProgress();
        releaseBrightness();
        return false;
    }

    // ---------------------------------------------------------------- marks

    private int currentPara() {
        Paginator.Page pg = pageAt(pageIndex);
        return pg != null ? pg.startPara : Math.max(0, anchorPara);
    }

    private boolean pageBookmarked() {
        return markAt(0, currentPara()) != null;
    }

    private Prefs.Mark markAt(int kind, int para) {
        for (Prefs.Mark m : marks) {
            if (m.kind == kind && m.para == para) return m;
        }
        return null;
    }

    private void saveMarksAll() {
        prefs.saveMarks(entry.id, marks);
    }

    private void toggleBookmark() {
        Paginator.Page pg = pageAt(pageIndex);
        if (pg == null) return;
        Prefs.Mark m = markAt(0, pg.startPara);
        if (m != null) {
            marks.remove(m);
            saveMarksAll();
            showHint("BOOKMARK REMOVED");
        } else {
            marks.add(new Prefs.Mark(System.nanoTime(), 0, pg.startPara, pageIndex + 1,
                    System.currentTimeMillis(), ""));
            saveMarksAll();
            showHint("BOOKMARKED");
        }
        invalidate();
    }

    private void toggleHighlight(int para) {
        Prefs.Mark m = markAt(1, para);
        if (m != null) {
            marks.remove(m);
            highlightParas.remove(para);
            saveMarksAll();
            showHint("HIGHLIGHT REMOVED");
        } else {
            marks.add(new Prefs.Mark(System.nanoTime(), 1, para, pageIndex + 1,
                    System.currentTimeMillis(), ""));
            highlightParas.add(para);
            saveMarksAll();
            showHint("HIGHLIGHTED");
        }
        invalidate();
    }

    private void addNote(int para, String text) {
        marks.add(new Prefs.Mark(System.nanoTime(), 2, para, pageIndex + 1,
                System.currentTimeMillis(), text));
        saveMarksAll();
        showHint("NOTE SAVED");
        invalidate();
    }

    private void deleteMark(long id) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            Prefs.Mark m = marks.get(i);
            if (m.id == id) {
                if (m.kind == 1) highlightParas.remove(m.para);
                marks.remove(i);
            }
        }
        saveMarksAll();
        invalidate();
    }

    private void jumpToPara(int para) {
        int target = paginator.pageOfPara(para);
        hide();
        gotoPage(target);
    }

    /** Simple native text entry for notes. */
    private void promptNote(final int para) {
        try {
            Context c = getContext();
            AlertDialog.Builder b = new AlertDialog.Builder(c);
            b.setTitle("Note");
            final EditText et = new EditText(c);
            et.setHint("Write a note for this passage");
            et.setMinLines(2);
            b.setView(et);
            b.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String t = et.getText().toString().trim();
                    if (t.length() > 0) addNote(para, t);
                }
            });
            b.setNegativeButton("Cancel", null);
            b.show();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ brightness

    private void applyBrightness() {
        try {
            Window w = ((Activity) getContext()).getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            float b = display.brightness;
            if (b <= 0.01f) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            } else {
                lp.screenBrightness = Math.max(0.02f, Math.min(1f, b));
            }
            w.setAttributes(lp);
        } catch (Exception ignored) { }
    }

    private void releaseBrightness() {
        try {
            Window w = ((Activity) getContext()).getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            w.setAttributes(lp);
        } catch (Exception ignored) { }
    }

    // -------------------------------------------------------------- overlays

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

        void onShow() { }

        abstract void draw(Canvas cv, EinkTheme th);

        abstract boolean onTouch(float x, float y);

        void onDown(float x, float y) { }
        void onMove(float x, float y) { }
        void onCancel() { }
    }

    void show(Overlay o) {
        selWordRef = null;
        selWord = null;
        active = o;
        o.visible = true;
        o.onShow();
        invalidate();
    }

    void showDict(String word) {
        selWord = word;
        active = dictOv;
        dictOv.visible = true;
        dictOv.onShow();
        dictOv.startLookup(word);
        invalidate();
    }

    void hide() {
        selWordRef = null;
        selWord = null;
        selPara = -1;
        if (active != null) active.visible = false;
        active = null;
        invalidate();
    }

    private void panel(Canvas cv, EinkTheme th, RectF r) {
        pFill.setColor(th.menuBg);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pFill);
        pStroke.setColor(th.menuBorder);
        pStroke.setStrokeWidth(dpf * 1.2f);
        cv.drawRoundRect(r, dpf * 3, dpf * 3, pStroke);
    }

    /** Side panel anchored to the right edge (typography / display settings). */
    private RectF sidePanel(Canvas cv, EinkTheme th, float pw) {
        pw = Math.min(pw, W - dpf * 20);
        float ph = H - dpf * 20;
        RectF r = new RectF(W - dpf * 10 - pw, dpf * 10, W - dpf * 10, dpf * 10 + ph);
        panel(cv, th, r);
        return r;
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
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

    // ------------------------------------------------------------- contents

    class ChaptersOverlay extends Overlay {

        float scroll = 0;
        float lastY = 0;
        boolean drag = false;
        boolean centered = false;
        final RectF pr = new RectF();

        @Override
        void onShow() {
            centered = false;
        }

        private int currentChapter() {
            int para = currentPara();
            int cur = 0;
            for (int i = 0; i < book.chapterParas.size(); i++) {
                if (book.chapterParas.get(i) <= para) cur = i;
            }
            return cur;
        }

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            int n = book.chapterTitles.size();
            float pw = Math.min(W - dpf * 20, dpf * 460);
            float rowH = dpf * 42;
            float headH = dpf * 46;
            float contentH = headH + Math.max(dpf * 46, n * rowH) + dpf * 12;
            float ph = Math.min(contentH, H * 0.72f);
            float x0 = dpf * 10;
            float y0 = statusH + dpf * 8;
            RectF r = new RectF(x0, y0, x0 + pw, y0 + ph);
            pr.set(r);
            panel(cv, th, r);

            pPanelTitle.setColor(th.menuText);
            cv.drawText("CONTENTS", r.left + dpf * 16, baseCentered(pPanelTitle, r.top, r.top + headH), pPanelTitle);
            hit(r.right - dpf * 42, r.top, r.right, r.top + headH, 98);
            drawX(cv, th, r.right - dpf * 21, r.top + headH / 2f);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.top + headH, r.right - dpf * 12, r.top + headH + dpf, pLine);

            float listTop = r.top + headH + dpf * 4;
            float listBot = r.bottom - dpf * 8;
            float maxScroll = Math.max(0, n * rowH - (listBot - listTop));
            if (!centered) {
                centered = true;
                float want = currentChapter() * rowH - (listBot - listTop) / 2f + rowH / 2f;
                scroll = clamp(want, 0, maxScroll);
            }
            scroll = clamp(scroll, 0, maxScroll);

            int cur = currentChapter();
            cv.save();
            cv.clipRect(r.left, listTop, r.right, listBot);
            for (int i = 0; i < n; i++) {
                float y = listTop + i * rowH - scroll;
                if (y + rowH < listTop || y > listBot) continue;
                hit(r.right - dpf * 40, y, r.right - dpf * 4, y + rowH, 1000 + i);
                hit(r.left, y, r.right, y + rowH, i);
                boolean on = (i == cur);
                if (on) {
                    pFill.setColor(th.menuText);
                    cv.drawRect(r.left + dpf * 6, y + dpf * 10, r.left + dpf * 9, y + rowH - dpf * 10, pFill);
                }
                pRow.setColor(on ? th.menuText : th.textSoft);
                String label = (i + 1) + ".  " + book.chapterTitles.get(i);
                CharSequence shown = TextUtils.ellipsize(label, pRow, r.width() - dpf * 92,
                        TextUtils.TruncateAt.END);
                cv.drawText(shown.toString(), r.left + dpf * 18,
                        baseCentered(pRow, y, y + rowH), pRow);
                pRowR.setColor(on ? th.menuText : th.textSoft);
                int pnum = paginator.pageOfPara(book.chapterParas.get(i)) + 1;
                cv.drawText("p. " + pnum, r.right - dpf * 44,
                        baseCentered(pRow, y, y + rowH), pRowR);
                pLine.setColor(th.line);
                cv.drawRect(r.left + dpf * 14, y + rowH, r.right - dpf * 14, y + rowH + dpf, pLine);
            }
            cv.restore();

            if (maxScroll > 0) {
                float trackH = listBot - listTop;
                float thumbH = Math.max(dpf * 30, trackH * (trackH / (n * rowH)));
                float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
                pFill.setColor(th.textSoft);
                cv.drawRoundRect(new RectF(r.right - dpf * 8, thumbY, r.right - dpf * 5,
                        thumbY + thumbH), dpf * 2, dpf * 2, pFill);
            }
        }

        void onDown(float x, float y) {
            lastY = y;
            drag = false;
        }

        void onMove(float x, float y) {
            if (Math.abs(y - lastY) > touchSlop) drag = true;
            if (drag) {
                scroll -= (y - lastY);
                lastY = y;
                invalidate();
            }
        }

        void onCancel() {
            drag = false;
        }

        boolean onTouch(float x, float y) {
            if (drag) {
                drag = false;
                return true;
            }
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) hide();
                return true;
            }
            if (a == 98) {
                hide();
                return true;
            }
            if (a >= 1000) {
                int idx = a - 1000;
                if (idx < book.chapterParas.size()) jumpToPara(book.chapterParas.get(idx));
                return true;
            }
            if (a < book.chapterParas.size()) {
                jumpToPara(book.chapterParas.get(a));
            }
            return true;
        }
    }

    // ------------------------------------------- bookmarks / highlights / notes

    class MarksOverlay extends Overlay {

        final int mode; // 0 bookmarks, 1 highlights, 2 notes
        float scroll = 0;
        float lastY = 0;
        boolean drag = false;
        boolean centered = false;
        final RectF pr = new RectF();

        MarksOverlay(int m) {
            mode = m;
        }

        @Override
        void onShow() {
            centered = false;
        }

        private String title() {
            if (mode == 0) return "BOOKMARKS";
            if (mode == 1) return "HIGHLIGHTS";
            return "NOTES";
        }

        private String emptyText() {
            if (mode == 0) return "No bookmarks yet";
            if (mode == 1) return "No highlights yet";
            return "No notes yet";
        }

        private ArrayList<Prefs.Mark> items() {
            ArrayList<Prefs.Mark> out = new ArrayList<Prefs.Mark>();
            for (Prefs.Mark m : marks) {
                if (m.kind == mode) out.add(m);
            }
            Collections.sort(out, new Comparator<Prefs.Mark>() {
                public int compare(Prefs.Mark a, Prefs.Mark b) {
                    return a.para - b.para;
                }
            });
            return out;
        }

        private String excerpt(Prefs.Mark m) {
            if (mode == 2) return m.text;
            if (m.para >= 0 && m.para < book.paragraphs.size()) {
                String s = book.paragraphs.get(m.para);
                return s.length() > 120 ? s.substring(0, 117) + "..." : s;
            }
            return "";
        }

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            ArrayList<Prefs.Mark> list = items();
            float pw = Math.min(dpf * 430, W - dpf * 24);
            float rowH = dpf * 56;
            float headH = dpf * 48;
            float footH = dpf * 58;
            float listH = Math.max(dpf * 72, list.size() * rowH + dpf * 4);
            float contentH = headH + listH + footH;
            float ph = Math.min(contentH, H - dpf * 32);
            RectF r = new RectF((W - pw) / 2f, (H - ph) / 2f, (W + pw) / 2f, (H + ph) / 2f);
            pr.set(r);
            panel(cv, th, r);

            pPanelTitle.setColor(th.menuText);
            cv.drawText(title(), r.left + dpf * 16, baseCentered(pPanelTitle, r.top, r.top + headH), pPanelTitle);
            hit(r.right - dpf * 42, r.top, r.right, r.top + headH, 98);
            drawX(cv, th, r.right - dpf * 21, r.top + headH / 2f);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.top + headH, r.right - dpf * 12, r.top + headH + dpf, pLine);

            float listTop = r.top + headH;
            float listBot = r.bottom - footH;
            float maxScroll = Math.max(0, list.size() * rowH - (listBot - listTop));
            if (!centered && list.size() > 0) {
                centered = true;
                int cp = currentPara();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).para >= cp) {
                        float want = i * rowH - (listBot - listTop) / 2f + rowH / 2f;
                        scroll = clamp(want, 0, maxScroll);
                        break;
                    }
                }
            }
            scroll = clamp(scroll, 0, maxScroll);

            if (list.isEmpty()) {
                pRow.setColor(th.textSoft);
                pRow.setTextAlign(Paint.Align.CENTER);
                cv.drawText(emptyText(), r.centerX(), (listTop + listBot) / 2f, pRow);
                pRow.setTextAlign(Paint.Align.LEFT);
            } else {
                cv.save();
                cv.clipRect(r.left, listTop, r.right, listBot);
                for (int i = 0; i < list.size(); i++) {
                    Prefs.Mark m = list.get(i);
                    float y = listTop + i * rowH - scroll;
                    if (y + rowH < listTop || y > listBot) continue;
                    hit(r.right - dpf * 42, y, r.right - dpf * 4, y + rowH, 1000 + i);
                    hit(r.left, y, r.right, y + rowH, i);

                    float ex0 = r.left + dpf * 16;
                    float exR = r.right - dpf * 44;

                    if (mode == 1) {
                        // soft band + marker bar for highlights
                        pFill.setColor(th.selBand);
                        cv.drawRect(ex0 - dpf * 4, y + dpf * 10, exR, y + rowH - dpf * 12, pFill);
                        pFill.setColor(th.menuText);
                        cv.drawRect(ex0 - dpf * 8, y + dpf * 12, ex0 - dpf * 5.5f, y + rowH - dpf * 14, pFill);
                    }

                    if (mode == 2) {
                        // note text, up to two lines
                        ArrayList<String> lines = wrapPlain(m.text, pExcerpt, exR - ex0 - dpf * 14);
                        pExcerpt.setColor(th.textSoft);
                        cv.drawText("\u201C", ex0, y + dpf * 26, pExcerpt);
                        float ty = y + dpf * 22;
                        int shown = 0;
                        for (String ln : lines) {
                            if (shown >= 2) break;
                            if (shown == 1 && lines.size() > 2) {
                                pExcerpt.setColor(th.textSoft);
                                cv.drawText(ln + " ...", ex0 + dpf * 12, ty, pExcerpt);
                                shown++;
                                break;
                            }
                            pExcerpt.setColor(th.menuText);
                            cv.drawText(ln, ex0 + dpf * 12, ty, pExcerpt);
                            ty += dpf * 18;
                            shown++;
                        }
                    } else {
                        CharSequence ex = TextUtils.ellipsize(excerpt(m), pExcerpt,
                                exR - ex0 - dpf * 14, TextUtils.TruncateAt.END);
                        pExcerpt.setColor(th.menuText);
                        cv.drawText(ex.toString(), ex0 + dpf * 12, y + dpf * 25, pExcerpt);
                        pRowR.setColor(th.textSoft);
                        pRowR.setTextAlign(Paint.Align.LEFT);
                        cv.drawText("\u00B6 p. " + m.page, ex0 + dpf * 12, y + dpf * 43, pRowR);
                        pRowR.setTextAlign(Paint.Align.RIGHT);
                    }

                    // delete
                    pStroke.setColor(th.textSoft);
                    pStroke.setStrokeWidth(dpf * 1.4f);
                    float dcy = y + rowH / 2f;
                    float ds = dpf * 4;
                    float dx1 = r.right - dpf * 20;
                    cv.drawLine(dx1 - ds, dcy - ds, dx1 + ds, dcy + ds, pStroke);
                    cv.drawLine(dx1 + ds, dcy - ds, dx1 - ds, dcy + ds, pStroke);

                    pLine.setColor(th.line);
                    cv.drawRect(r.left + dpf * 14, y + rowH, r.right - dpf * 14, y + rowH + dpf, pLine);
                }
                cv.restore();

                if (maxScroll > 0) {
                    float trackH = listBot - listTop;
                    float thumbH = Math.max(dpf * 30, trackH * trackH / (list.size() * rowH));
                    float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
                    pFill.setColor(th.textSoft);
                    cv.drawRoundRect(new RectF(r.right - dpf * 7, thumbY, r.right - dpf * 4,
                            thumbY + thumbH), dpf * 2, dpf * 2, pFill);
                }
            }

            // footer action
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.bottom - footH, r.right - dpf * 12, r.bottom - footH + dpf, pLine);
            float bw = r.width() - dpf * 60;
            float bx = r.left + dpf * 30;
            float by = r.bottom - footH + dpf * 12;
            hit(bx, by, bx + bw, by + dpf * 36, 99);
            pFill.setColor(th.menuText);
            cv.drawRoundRect(new RectF(bx, by, bx + bw, by + dpf * 36), dpf * 3, dpf * 3, pFill);
            String label;
            if (mode == 0) label = pageBookmarked() ? "REMOVE BOOKMARK" : "ADD BOOKMARK";
            else if (mode == 1) label = highlightParas.contains(currentPara())
                    ? "REMOVE HIGHLIGHT" : "HIGHLIGHT THIS PAGE";
            else label = "ADD NOTE";
            pBtn.setColor(th.menuBg);
            cv.drawText(label, r.centerX(), by + dpf * 24, pBtn);
        }

        void onDown(float x, float y) {
            lastY = y;
            drag = false;
        }

        void onMove(float x, float y) {
            if (Math.abs(y - lastY) > touchSlop) drag = true;
            if (drag) {
                scroll -= (y - lastY);
                lastY = y;
                invalidate();
            }
        }

        void onCancel() {
            drag = false;
        }

        boolean onTouch(float x, float y) {
            if (drag) {
                drag = false;
                return true;
            }
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) hide();
                return true;
            }
            if (a == 98) {
                hide();
                return true;
            }
            if (a == 99) {
                if (mode == 0) {
                    toggleBookmark();
                    if (pageBookmarked()) hide();
                } else if (mode == 1) {
                    toggleHighlight(currentPara());
                } else {
                    promptNote(currentPara());
                }
                return true;
            }
            if (a >= 1000) {
                ArrayList<Prefs.Mark> list = items();
                int idx = a - 1000;
                if (idx < list.size()) deleteMark(list.get(idx).id);
                return true;
            }
            ArrayList<Prefs.Mark> list = items();
            if (a < list.size()) jumpToPara(list.get(a).para);
            return true;
        }
    }

    // ------------------------------------------------------------- settings

    class SettingsOverlay extends Overlay {

        float scroll = 0;
        float lastY = 0;
        boolean scrollDrag = false;
        int dragSlider = -1;
        float contentH = 0;
        float listTop, listBot, lx, rx2;
        final RectF pr = new RectF();

        class Slider {
            float x0, x1, cy, vMin, vMax, step;

            void set(float a, float b, float c, float lo, float hi, float st) {
                x0 = a; x1 = b; cy = c; vMin = lo; vMax = hi; step = st;
            }

            float from(float x) {
                float f = clamp((x - x0) / Math.max(1f, x1 - x0), 0f, 1f);
                float v = vMin + f * (vMax - vMin);
                v = Math.round((v - vMin) / step) * step + vMin;
                return clamp(v, vMin, vMax);
            }

            boolean hit(float x, float y) {
                return y >= cy - dpf * 24 && y <= cy + dpf * 24
                        && x >= x0 - dpf * 14 && x <= x1 + dpf * 14;
            }

            void draw(Canvas cv, EinkTheme th, float v) {
                float f = clamp((v - vMin) / (vMax - vMin), 0f, 1f);
                pLine.setColor(th.line);
                cv.drawRect(x0, cy - dpf, x1, cy + dpf, pLine);
                pLine.setColor(th.textSoft);
                cv.drawRect(x0, cy - dpf, x0 + (x1 - x0) * f, cy + dpf, pLine);
                float tx = x0 + (x1 - x0) * f;
                pFill.setColor(th.menuBg);
                cv.drawRect(tx - dpf * 8, cy - dpf * 8, tx + dpf * 8, cy + dpf * 8, pFill);
                pStroke.setColor(th.text);
                pStroke.setStrokeWidth(dpf * 1.4f);
                cv.drawRect(tx - dpf * 8, cy - dpf * 8, tx + dpf * 8, cy + dpf * 8, pStroke);
                pFill.setColor(th.text);
                cv.drawRect(tx - dpf * 2.5f, cy - dpf * 2.5f, tx + dpf * 2.5f, cy + dpf * 2.5f, pFill);
            }
        }

        final Slider[] sliders = {new Slider(), new Slider(), new Slider()};

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = sidePanel(cv, th, dpf * 348);
            pr.set(r);
            float headH = dpf * 48;
            pPanelTitle.setColor(th.menuText);
            cv.drawText("TYPOGRAPHY", r.left + dpf * 16, baseCentered(pPanelTitle, r.top, r.top + headH), pPanelTitle);
            hit(r.right - dpf * 42, r.top, r.right, r.top + headH, 98);
            drawX(cv, th, r.right - dpf * 21, r.top + headH / 2f);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.top + headH, r.right - dpf * 12, r.top + headH + dpf, pLine);

            listTop = r.top + headH + dpf * 10;
            listBot = r.bottom - dpf * 10;
            lx = r.left + dpf * 16;
            rx2 = r.right - dpf * 16;
            float cw = rx2 - lx;

            scroll = clamp(scroll, 0, maxScroll());
            float y = listTop - scroll;

            // font family
            y = rowLabel(cv, th, y, "Font");
            float segW = (cw - dpf * 12) / 3f;
            String[] fonts = {"Serif", "Sans", "Mono"};
            for (int i = 0; i < 3; i++) {
                seg(cv, th, lx + i * (segW + dpf * 6), y, segW, dpf * 34, fonts[i],
                        i, style.typeface == i);
            }
            y += dpf * 42;

            // font size
            y = rowLabelVal(cv, th, y, "Size", String.valueOf(style.fontSize));
            sliders[0].set(lx + dpf * 4, rx2 - dpf * 4, y + dpf * 18, 14, 30, 1);
            sliders[0].draw(cv, th, style.fontSize);
            y += dpf * 42;

            // line height
            y = rowLabelVal(cv, th, y, "Line height",
                    String.format(Locale.US, "%.2f", style.lineSpacing));
            sliders[1].set(lx + dpf * 4, rx2 - dpf * 4, y + dpf * 18, 1.2f, 2.0f, 0.05f);
            sliders[1].draw(cv, th, style.lineSpacing);
            y += dpf * 42;

            // paragraph spacing
            y = rowLabelVal(cv, th, y, "Paragraph spacing",
                    Math.round(style.paraSpacing * 100) + "%");
            sliders[2].set(lx + dpf * 4, rx2 - dpf * 4, y + dpf * 18, 0f, 1f, 0.05f);
            sliders[2].draw(cv, th, style.paraSpacing);
            y += dpf * 42;

            // margins
            y = rowLabel(cv, th, y, "Margins");
            float mW = (cw - dpf * 18) / 4f;
            for (int i = 0; i < 4; i++) {
                marginSeg(cv, th, lx + i * (mW + dpf * 6), y, mW, dpf * 34, i,
                        3 + i, style.margin == i);
            }
            y += dpf * 42;

            // alignment
            y = rowLabel(cv, th, y, "Alignment");
            float aW = (cw - dpf * 6) / 2f;
            seg(cv, th, lx, y, aW, dpf * 34, "Justified", 7, style.justify);
            seg(cv, th, lx + aW + dpf * 6, y, aW, dpf * 34, "Left", 8, !style.justify);
            y += dpf * 42;

            // paper tone
            y = rowLabel(cv, th, y, "Paper tone");
            float tW = (cw - dpf * 16) / 3f;
            String[] names = {"Paper", "White", "Dark"};
            for (int i = 0; i < 3; i++) {
                themeSwatch(cv, th, lx + i * (tW + dpf * 8), y, tW, dpf * 30, i,
                        9 + i, display.theme == i, names[i]);
            }
            y += dpf * 54;

            contentH = y + scroll - listTop;
        }

        private float maxScroll() {
            return Math.max(0, contentH - (listBot - listTop));
        }

        private float rowLabel(Canvas cv, EinkTheme th, float y, String label) {
            pRow.setColor(th.menuText);
            cv.drawText(label, lx, y + dpf * 15, pRow);
            return y + dpf * 26;
        }

        private float rowLabelVal(Canvas cv, EinkTheme th, float y, String label, String val) {
            pRow.setColor(th.menuText);
            cv.drawText(label, lx, y + dpf * 15, pRow);
            pRowVal.setColor(th.menuText);
            cv.drawText(val, rx2, y + dpf * 15, pRowVal);
            return y + dpf * 26;
        }

        private void seg(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                         String label, int action, boolean activeSeg) {
            hit(x, y, x + w, y + h, action);
            RectF rr = new RectF(x, y, x + w, y + h);
            if (activeSeg) {
                pFill.setColor(th.menuText);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pBtn.setColor(th.menuBg);
            } else {
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                pStroke.setStrokeWidth(dpf * 1.1f);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pStroke);
                pBtn.setColor(th.menuText);
            }
            float ts = pBtn.getTextSize();
            pBtn.setTextSize(label.length() > 5 ? spf * 10.5f : spf * 12);
            cv.drawText(label, rr.centerX(), baseCentered(pBtn, y, y + h), pBtn);
            pBtn.setTextSize(ts);
        }

        private void marginSeg(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                               int level, int action, boolean activeSeg) {
            hit(x, y, x + w, y + h, action);
            RectF rr = new RectF(x, y, x + w, y + h);
            int ink = activeSeg ? th.menuBg : th.menuText;
            if (activeSeg) {
                pFill.setColor(th.menuText);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
            } else {
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
            }
            pStroke.setColor(ink);
            pStroke.setStrokeWidth(dpf * 1.2f);
            float px0 = x + w / 2f - dpf * 11, py0 = y + h / 2f - dpf * 7;
            float px1 = x + w / 2f + dpf * 11, py1 = y + h / 2f + dpf * 7;
            cv.drawRect(px0, py0, px1, py1, pStroke);
            float[] fr = {0.92f, 0.74f, 0.56f, 0.40f};
            float lw = (px1 - px0 - dpf * 5) * fr[level];
            float mid = (px0 + px1) / 2f;
            cv.drawLine(mid - lw / 2f, (py0 + py1) / 2f, mid + lw / 2f, (py0 + py1) / 2f, pStroke);
        }

        private void themeSwatch(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                                 int tIdx, int action, boolean activeSeg, String label) {
            hit(x, y - dpf * 4, x + w, y + h + dpf * 16, action);
            EinkTheme t2 = EinkTheme.theme(tIdx);
            pFill.setColor(t2.bg);
            cv.drawRect(x + dpf * 10, y, x + w - dpf * 10, y + h, pFill);
            pStroke.setColor(th.menuBorder);
            pStroke.setStrokeWidth(dpf * 1.1f);
            cv.drawRect(x + dpf * 10, y, x + w - dpf * 10, y + h, pStroke);
            pFill.setColor(t2.text);
            cv.drawRect(x + w / 2f - dpf * 9, y + h / 2f - dpf, x + w / 2f + dpf * 9,
                    y + h / 2f + dpf, pFill);
            if (activeSeg) {
                pStroke.setColor(th.menuText);
                pStroke.setStrokeWidth(dpf * 2f);
                cv.drawRect(x + dpf * 8.5f, y - dpf * 1.5f, x + w - dpf * 8.5f, y + h + dpf * 1.5f, pStroke);
            }
            pBtnSmall.setColor(th.menuText);
            cv.drawText(label, x + w / 2f, y + h + dpf * 13, pBtnSmall);
        }

        private void setSliderValue(int id, float v) {
            if (id == 0) style.fontSize = Math.round(v);
            else if (id == 1) style.lineSpacing = v;
            else style.paraSpacing = v;
        }

        private void applyTypography() {
            prefs.saveBookSettings(entry.id, style);
            repaginate();
        }

        private void applyMargins() {
            computeColumn();
            applyTypography();
        }

        void onDown(float x, float y) {
            dragSlider = -1;
            for (int i = 0; i < 3; i++) {
                if (sliders[i] != null && sliders[i].hit(x, y)) {
                    dragSlider = i;
                    break;
                }
            }
            if (dragSlider >= 0) {
                setSliderValue(dragSlider, sliders[dragSlider].from(x));
                invalidate();
                return;
            }
            lastY = y;
            scrollDrag = false;
        }

        void onMove(float x, float y) {
            if (dragSlider >= 0) {
                setSliderValue(dragSlider, sliders[dragSlider].from(x));
                invalidate();
                return;
            }
            if (!scrollDrag && Math.abs(y - lastY) > touchSlop) scrollDrag = true;
            if (scrollDrag) {
                scroll = clamp(scroll - (y - lastY), 0, maxScroll());
                lastY = y;
                invalidate();
            }
        }

        void onCancel() {
            dragSlider = -1;
            scrollDrag = false;
        }

        boolean onTouch(float x, float y) {
            if (dragSlider >= 0) {
                setSliderValue(dragSlider, sliders[dragSlider].from(x));
                dragSlider = -1;
                applyTypography();
                invalidate();
                return true;
            }
            if (scrollDrag) {
                scrollDrag = false;
                return true;
            }
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) {
                    prefs.saveBookSettings(entry.id, style);
                    hide();
                }
                return true;
            }
            if (a == 98) {
                prefs.saveBookSettings(entry.id, style);
                hide();
                return true;
            }
            if (a <= 2) {
                style.typeface = a;
                applyTypography();
                return true;
            }
            if (a <= 6) {
                style.margin = a - 3;
                applyMargins();
                return true;
            }
            if (a == 7) {
                style.justify = true;
                applyTypography();
                return true;
            }
            if (a == 8) {
                style.justify = false;
                applyTypography();
                return true;
            }
            if (a <= 11) {
                display.theme = a - 9;
                prefs.saveSettings(display);
                invalidate();
                return true;
            }
            return true;
        }
    }

    // -------------------------------------------------------------- display

    class DisplayOverlay extends Overlay {

        float lastY = 0;
        int dragSlider = -1;
        boolean sliderTap = false;
        float sCy, sX0, sX1;
        final RectF pr = new RectF();

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = sidePanel(cv, th, dpf * 348);
            pr.set(r);
            float headH = dpf * 48;
            pPanelTitle.setColor(th.menuText);
            cv.drawText("DISPLAY", r.left + dpf * 16, baseCentered(pPanelTitle, r.top, r.top + headH), pPanelTitle);
            hit(r.right - dpf * 42, r.top, r.right, r.top + headH, 98);
            drawX(cv, th, r.right - dpf * 21, r.top + headH / 2f);
            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 12, r.top + headH, r.right - dpf * 12, r.top + headH + dpf, pLine);

            float lx = r.left + dpf * 16;
            float rx2 = r.right - dpf * 16;
            float cw = rx2 - lx;
            float y = r.top + headH + dpf * 10;

            // brightness
            String val = display.brightness <= 0.01f ? "Auto"
                    : Math.round(display.brightness * 100) + "%";
            pRow.setColor(th.menuText);
            cv.drawText("Brightness", lx, y + dpf * 15, pRow);
            pRowVal.setColor(th.menuText);
            cv.drawText(val, rx2, y + dpf * 15, pRowVal);
            y += dpf * 26;
            sX0 = lx + dpf * 4;
            sX1 = rx2 - dpf * 4;
            sCy = y + dpf * 18;
            float sv = display.brightness <= 0.01f ? 0
                    : Math.round((display.brightness - 0.02f) / 0.98f * 100);
            drawSlider(cv, th, sX0, sX1, sCy, clamp(sv, 0, 100));
            y += dpf * 42;

            // fullscreen refresh
            pRow.setColor(th.menuText);
            cv.drawText("Full refresh on turn", lx, y + dpf * 15, pRow);
            y += dpf * 26;
            float aW = (cw - dpf * 6) / 2f;
            segBtn(cv, th, lx, y, aW, dpf * 34, "On", 12, display.flash);
            segBtn(cv, th, lx + aW + dpf * 6, y, aW, dpf * 34, "Off", 13, !display.flash);
            y += dpf * 42;

            // haptic
            pRow.setColor(th.menuText);
            cv.drawText("Haptic feedback", lx, y + dpf * 15, pRow);
            y += dpf * 26;
            segBtn(cv, th, lx, y, aW, dpf * 34, "On", 14, display.haptic);
            segBtn(cv, th, lx + aW + dpf * 6, y, aW, dpf * 34, "Off", 15, !display.haptic);
        }

        private void drawSlider(Canvas cv, EinkTheme th, float x0, float x1, float cy, float v) {
            pLine.setColor(th.line);
            cv.drawRect(x0, cy - dpf, x1, cy + dpf, pLine);
            pLine.setColor(th.textSoft);
            cv.drawRect(x0, cy - dpf, x0 + (x1 - x0) * v / 100f, cy + dpf, pLine);
            float tx = x0 + (x1 - x0) * v / 100f;
            pFill.setColor(th.menuBg);
            cv.drawRect(tx - dpf * 8, cy - dpf * 8, tx + dpf * 8, cy + dpf * 8, pFill);
            pStroke.setColor(th.text);
            pStroke.setStrokeWidth(dpf * 1.4f);
            cv.drawRect(tx - dpf * 8, cy - dpf * 8, tx + dpf * 8, cy + dpf * 8, pStroke);
            pFill.setColor(th.text);
            cv.drawRect(tx - dpf * 2.5f, cy - dpf * 2.5f, tx + dpf * 2.5f, cy + dpf * 2.5f, pFill);
        }

        private void segBtn(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                            String label, int action, boolean activeSeg) {
            hit(x, y, x + w, y + h, action);
            RectF rr = new RectF(x, y, x + w, y + h);
            if (activeSeg) {
                pFill.setColor(th.menuText);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pBtn.setColor(th.menuBg);
            } else {
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                pStroke.setStrokeWidth(dpf * 1.1f);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pStroke);
                pBtn.setColor(th.menuText);
            }
            cv.drawText(label, rr.centerX(), baseCentered(pBtn, y, y + h), pBtn);
        }

        private boolean sliderHit(float x, float y) {
            return y >= sCy - dpf * 24 && y <= sCy + dpf * 24
                    && x >= sX0 - dpf * 14 && x <= sX1 + dpf * 14;
        }

        private void setBrightnessFrom(float x) {
            float f = clamp((x - sX0) / Math.max(1f, sX1 - sX0), 0f, 1f);
            int v = Math.round(f * 100 / 5f) * 5;
            if (v <= 0) {
                display.brightness = 0f;
            } else {
                display.brightness = 0.02f + 0.98f * v / 100f;
            }
        }

        void onDown(float x, float y) {
            sliderTap = sliderHit(x, y);
            if (sliderTap) {
                dragSlider = 0;
                setBrightnessFrom(x);
                applyBrightness();
                invalidate();
                return;
            }
            lastY = y;
        }

        void onMove(float x, float y) {
            if (dragSlider == 0) {
                setBrightnessFrom(x);
                applyBrightness();
                invalidate();
            }
        }

        void onCancel() {
            dragSlider = -1;
            sliderTap = false;
        }

        boolean onTouch(float x, float y) {
            if (dragSlider == 0) {
                setBrightnessFrom(x);
                applyBrightness();
                prefs.saveSettings(display);
                dragSlider = -1;
                sliderTap = false;
                invalidate();
                return true;
            }
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) {
                    prefs.saveSettings(display);
                    hide();
                }
                return true;
            }
            if (a == 98) {
                prefs.saveSettings(display);
                hide();
                return true;
            }
            if (a == 12 || a == 13) {
                display.flash = (a == 12);
                prefs.saveSettings(display);
                invalidate();
                return true;
            }
            if (a == 14 || a == 15) {
                display.haptic = (a == 14);
                prefs.saveSettings(display);
                invalidate();
                return true;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- go to

    class GoToOverlay extends Overlay {

        final StringBuilder num = new StringBuilder();
        final RectF pr = new RectF();

        @Override
        void onShow() {
            num.setLength(0);
        }

        void draw(Canvas cv, EinkTheme th) {
            clearHits();
            RectF r = panelPx(cv, th, dpf * 252, dpf * 336);
            pPanelTitle.setColor(th.menuText);
            cv.drawText("GO TO PAGE", r.centerX(), r.top + dpf * 28, pPanelTitle);
            hit(r.right - dpf * 40, r.top, r.right, r.top + dpf * 40, 90);
            drawX(cv, th, r.right - dpf * 20, r.top + dpf * 20);

            RectF disp = new RectF(r.left + dpf * 14, r.top + dpf * 44,
                    r.right - dpf * 14, r.top + dpf * 84);
            pFill.setColor(th.btnFill);
            cv.drawRect(disp, pFill);
            pStroke.setColor(th.btnBorder);
            pStroke.setStrokeWidth(dpf * 1.2f);
            cv.drawRect(disp, pStroke);
            pRowVal.setTextAlign(Paint.Align.CENTER);
            pRowVal.setColor(th.menuText);
            String cur = num.length() > 0 ? num.toString() : "page";
            cv.drawText(cur, disp.centerX(), baseCentered(pRowVal, disp.top, disp.bottom), pRowVal);
            pRowVal.setTextAlign(Paint.Align.RIGHT);

            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "GO"};
            float gap = dpf * 6;
            float kw = (r.width() - dpf * 28 - gap * 2) / 3f;
            float kh = dpf * 40;
            float ky = r.top + dpf * 96;
            for (int i = 0; i < keys.length; i++) {
                int row = i / 3;
                int col = i % 3;
                float kx = r.left + dpf * 14 + col * (kw + gap);
                float kyy = ky + row * (kh + gap);
                hit(kx, kyy, kx + kw, kyy + kh, i);
                boolean filled = (i == 11);
                RectF kr = new RectF(kx, kyy, kx + kw, kyy + kh);
                if (filled) {
                    pFill.setColor(th.menuText);
                    cv.drawRoundRect(kr, dpf * 3, dpf * 3, pFill);
                    pBtn.setColor(th.menuBg);
                } else {
                    pFill.setColor(th.btnFill);
                    cv.drawRoundRect(kr, dpf * 3, dpf * 3, pFill);
                    pStroke.setColor(th.btnBorder);
                    pStroke.setStrokeWidth(dpf * 1.2f);
                    cv.drawRoundRect(kr, dpf * 3, dpf * 3, pStroke);
                    pBtn.setColor(th.menuText);
                }
                float ts = pBtn.getTextSize();
                if (keys[i].length() > 2) pBtn.setTextSize(spf * 11);
                cv.drawText(keys[i], kr.centerX(), baseCentered(pBtn, kyy, kyy + kh), pBtn);
                pBtn.setTextSize(ts);
            }
        }

        private RectF panelPx(Canvas cv, EinkTheme th, float pw, float ph) {
            pw = Math.min(pw, W - dpf * 24);
            ph = Math.min(ph, H - dpf * 40);
            RectF r = new RectF((W - pw) / 2f, (H - ph) / 2f, (W + pw) / 2f, (H + ph) / 2f);
            pr.set(r);
            panel(cv, th, r);
            return r;
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) hide();
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

    // ------------------------------------------------------------ dictionary

    class DictOverlay extends Overlay {

        int state = 0; // 0 loading, 1 result, 2 error
        ArrayList<String> resultLines = null;
        String error = null;
        final RectF pr = new RectF();

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
            float pw = Math.min(W - dpf * 24, dpf * 560);
            float ph = Math.min(H * 0.52f, dpf * 430);
            RectF r = new RectF((W - pw) / 2f, (H - ph) / 2f, (W + pw) / 2f, (H + ph) / 2f);
            pr.set(r);
            panel(cv, th, r);

            String word = selWord == null ? "" : selWord;
            CharSequence shown = TextUtils.ellipsize(word, pDictW, r.width() - dpf * 70,
                    TextUtils.TruncateAt.END);
            pDictW.setColor(th.menuText);
            cv.drawText(shown.toString(), r.left + dpf * 16, r.top + dpf * 34, pDictW);
            pDictTag.setColor(th.textSoft);
            cv.drawText("WIKTIONARY", r.left + dpf * 16, r.top + dpf * 52, pDictTag);

            hit(r.right - dpf * 40, r.top, r.right, r.top + dpf * 40, 91);
            drawX(cv, th, r.right - dpf * 20, r.top + dpf * 20);

            // actions: highlight this passage, write a note
            float ay = r.top + dpf * 66;
            float aw = (r.width() - dpf * 44 - dpf * 8) / 2f;
            String hLabel = (selPara >= 0 && highlightParas.contains(selPara))
                    ? "UNHIGHLIGHT" : "HIGHLIGHT";
            actionBtn(cv, th, r.left + dpf * 22, ay, aw, dpf * 32, hLabel, 92, false);
            actionBtn(cv, th, r.left + dpf * 22 + aw + dpf * 8, ay, aw, dpf * 32, "NOTE", 94, false);

            pLine.setColor(th.line);
            cv.drawRect(r.left + dpf * 14, ay + dpf * 40, r.right - dpf * 14, ay + dpf * 41, pLine);

            float cy = ay + dpf * 56;
            float maxCy = r.bottom - dpf * 16;
            float textW = r.width() - dpf * 44;

            if (state == 0) {
                pDict.setColor(th.menuText);
                cv.drawText("Looking up ...", r.left + dpf * 22, cy + dpf * 12, pDict);
            } else if (state == 2) {
                pDict.setColor(th.textSoft);
                ArrayList<String> lines = wrapPlain(error == null ? "" : error, pDict, textW);
                for (String ln : lines) {
                    if (cy > maxCy - dpf * 44) break;
                    cv.drawText(ln, r.left + dpf * 22, cy + dpf * 12, pDict);
                    cy += dpf * 20;
                }
                actionBtn(cv, th, r.left + dpf * 22, r.bottom - dpf * 46, dpf * 84, dpf * 32,
                        "RETRY", 93, false);
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
                        cv.drawText(ln, r.left + dpf * 22, cy + dpf * 12, pDict);
                        cy += dpf * 20;
                    }
                    cy += dpf * 6;
                }
                if (truncated) {
                    pDict.setColor(th.textSoft);
                    cv.drawText("...", r.left + dpf * 22, Math.min(cy + dpf * 12, maxCy), pDict);
                }
            }
        }

        private void actionBtn(Canvas cv, EinkTheme th, float x, float y, float w, float h,
                               String label, int action, boolean dark) {
            hit(x, y, x + w, y + h, action);
            RectF rr = new RectF(x, y, x + w, y + h);
            if (dark) {
                pFill.setColor(th.menuText);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pBtnSmall.setColor(th.menuBg);
            } else {
                pFill.setColor(th.btnFill);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pFill);
                pStroke.setColor(th.btnBorder);
                pStroke.setStrokeWidth(dpf * 1.2f);
                cv.drawRoundRect(rr, dpf * 3, dpf * 3, pStroke);
                pBtnSmall.setColor(th.menuText);
            }
            float ts = pBtnSmall.getTextSize();
            if (label.length() > 10) pBtnSmall.setTextSize(spf * 9.5f);
            cv.drawText(label, rr.centerX(), baseCentered(pBtnSmall, y, y + h), pBtnSmall);
            pBtnSmall.setTextSize(ts);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                if (!pr.contains(x, y)) hide();
                return true;
            }
            if (a == 91) {
                hide();
                return true;
            }
            if (a == 92) {
                toggleHighlight(selPara >= 0 ? selPara : currentPara());
                return true;
            }
            if (a == 94) {
                promptNote(selPara >= 0 ? selPara : currentPara());
                return true;
            }
            if (a == 93 && selWord != null) {
                startLookup(selWord);
            }
            invalidate();
            return true;
        }
    }
}

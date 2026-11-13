package com.inkbook.reader;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.StatFs;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BOOX e-reader style library: grayscale UI, fixed left navigation sidebar,
 * compact toolbar, 4-column document grid with thin-bordered covers, and a
 * bottom statistics / pagination bar. Sharp corners everywhere, no color.
 */
public final class LibraryView extends View {

    public interface Host {
        void onOpenBook(Prefs.BookEntry e);
        void onImport();
        void onDeleteBook(Prefs.BookEntry e);
    }

    // ------------------------------------------------------- palette (e-ink)
    private static final int BG = 0xFFE5E5E5;            // screen background
    private static final int SIDEBAR_BG = 0xFFD9D9D9;    // slightly darker grey
    private static final int INK = 0xFF171717;           // primary text / icons
    private static final int INK2 = 0xFF5E5E5E;          // secondary text
    private static final int HAIR = 0xFFADADAD;          // hairlines
    private static final int PANEL = 0xFFF4F4F3;         // dialog surface
    private static final int PANEL_HAIR = 0xFFC6C6C6;
    private static final int COVER_BG = 0xFFF1F1F0;
    private static final int COVER_BORDER = 0xFF3C3C3C;
    private static final int SCRIM = 0x2E000000;
    private static final int BLINK = 0xFF1B1B1B;
    private static final int SEL = 0xFF171717;           // selected tile black

    // ------------------------------------------------------------ sections
    static final int SEC_LIBRARY = 0;
    static final int SEC_STORE = 1;
    static final int SEC_NOTES = 2;
    static final int SEC_STORAGE = 3;
    static final int SEC_APPS = 4;
    static final int SEC_SETTINGS = 5;

    static final String[] SECTION_NAMES = {
            "Library", "Store", "Notes", "Storage", "Apps", "Settings"
    };

    private final Host host;
    private final Prefs prefs;
    private final Prefs.Settings settings;
    private final List<Prefs.BookEntry> books = new ArrayList<Prefs.BookEntry>();
    private final List<Prefs.BookEntry> filtered = new ArrayList<Prefs.BookEntry>();
    private List<String> shelves = new ArrayList<String>();

    // ---------------------------------------------------------- grid state
    private int cols = 4;
    private int page = 0;
    private int sortMode = 3;          // 0 title, 1 author, 2 added, 3 recently opened
    private String query = "";
    private String shelfFilter = null; // null = all books

    // ---------------------------------------------------------- geometry
    private int W, H;
    private float dpf, spf;
    private float touchSlop;
    private int statusH, sideW, toolH, bottomH;
    private float contentL, gridTop, gridBot, coverW, coverH, cellH;
    private int rows = 3, pageSize = 12, pageCount = 1;

    // ---------------------------------------------------------- interaction
    private float downX, downY;
    private boolean moved, longFired;
    private int blinkFrames = 0;
    private Overlay active = null;
    private Prefs.BookEntry actionTarget = null;
    private String pendingShelfFor = null; // entry id when creating shelf from add-to-shelf

    private final ArrayList<float[]> sideHits = new ArrayList<float[]>();
    private final ArrayList<float[]> toolHits = new ArrayList<float[]>();
    private final ArrayList<float[]> coverHits = new ArrayList<float[]>();
    private final ArrayList<float[]> bottomHits = new ArrayList<float[]>();

    // ------------------------------------------------------------- paints
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcon = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint pTime = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pAuthor = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pChip = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBottom = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pHead = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenu = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pMenuTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pCoverSerif = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pCoverSerifIt = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pCoverSans = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBody = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pBody2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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
        pIcon.setStyle(Paint.Style.STROKE);
        pIcon.setStrokeWidth(dpf * 1.5f);
        pIcon.setStrokeCap(Paint.Cap.ROUND);
        pIcon.setStrokeJoin(Paint.Join.MITER);

        pTime.setTextSize(spf * 11.5f);
        pLabel.setTextAlign(Paint.Align.CENTER);
        pLabel.setTextSize(spf * 10f);
        pTitle.setTextSize(spf * 13f);
        pAuthor.setTextSize(spf * 11f);
        pAuthor.setColor(INK2);
        pChip.setTypeface(Typeface.DEFAULT_BOLD);
        pChip.setTextSize(spf * 8f);
        pChip.setTextAlign(Paint.Align.CENTER);
        pBottom.setTextSize(spf * 13f);
        pHead.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pHead.setTextSize(spf * 17f);
        pMenu.setTextSize(spf * 14f);
        pMenuTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pMenuTitle.setTextSize(spf * 15f);
        pBody.setTextSize(spf * 14f);
        pBody2.setTextSize(spf * 12.5f);
        pBody2.setColor(INK2);

        pCoverSerif.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        pCoverSerif.setTextSize(spf * 13f);
        pCoverSerif.setTextAlign(Paint.Align.CENTER);
        pCoverSerifIt.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        pCoverSerifIt.setTextSize(spf * 14f);
        pCoverSerifIt.setTextAlign(Paint.Align.CENTER);
        pCoverSans.setTextAlign(Paint.Align.CENTER);
        pCoverSans.setTextSize(spf * 11f);
    }

    // ------------------------------------------------------------- data

    public void refresh() {
        books.clear();
        books.addAll(prefs.library());
        shelves = prefs.shelves();
        rebuild();
        invalidate();
    }

    private void saveBooks() {
        prefs.saveLibrary(books);
    }

    private void rebuild() {
        filtered.clear();
        String q = query.toLowerCase(Locale.US);
        for (Prefs.BookEntry e : books) {
            if (shelfFilter != null && !shelfFilter.equals(e.shelf)) continue;
            if (q.length() > 0
                    && !e.title.toLowerCase(Locale.US).contains(q)
                    && !e.author.toLowerCase(Locale.US).contains(q)) {
                continue;
            }
            filtered.add(e);
        }
        sortFiltered();
        clampPage();
    }

    private void sortFiltered() {
        final int m = sortMode;
        java.util.Collections.sort(filtered, new java.util.Comparator<Prefs.BookEntry>() {
            public int compare(Prefs.BookEntry a, Prefs.BookEntry b) {
                if (m == 0) return a.title.toLowerCase(Locale.US)
                        .compareTo(b.title.toLowerCase(Locale.US));
                if (m == 1) {
                    if (a.author.length() == 0 && b.author.length() > 0) return 1;
                    if (b.author.length() == 0 && a.author.length() > 0) return -1;
                    return a.author.toLowerCase(Locale.US)
                            .compareTo(b.author.toLowerCase(Locale.US));
                }
                if (m == 2) return a.addedAt > b.addedAt ? -1 : (a.addedAt < b.addedAt ? 1 : 0);
                long ra = a.lastReadAt == 0 ? -1 : a.lastReadAt;
                long rb = b.lastReadAt == 0 ? -1 : b.lastReadAt;
                return ra > rb ? -1 : (ra < rb ? 1 : 0);
            }
        });
    }

    private void clampPage() {
        pageSize = Math.max(1, rows * cols);
        pageCount = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        if (page > pageCount - 1) page = pageCount - 1;
        if (page < 0) page = 0;
    }

    private void setQuery(String q) {
        query = q == null ? "" : q;
        page = 0;
        computeGeometry();
        rebuild();
        invalidate();
    }

    // ---------------------------------------------------------- geometry

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        computeGeometry();
    }

    private void computeGeometry() {
        statusH = (int) dp(30);
        sideW = (int) (W < dp(480) ? dp(58) : dp(76));
        toolH = (int) dp(46);
        bottomH = (int) dp(44);
        contentL = sideW;

        float gridTopBase = statusH + toolH + dp(12);
        float extra = (query.length() > 0) ? dp(34) : 0;
        gridTop = gridTopBase + extra;
        gridBot = H - bottomH - dp(6);

        float contentW = W - contentL;
        float pad = dp(18);
        float gap = dp(20);
        coverW = (contentW - pad * 2 - gap * (cols - 1)) / cols;
        if (coverW < dp(56)) coverW = dp(56);
        coverH = coverW * 1.30f;
        float titleH = dp(17) * 2;
        float authorH = dp(14);
        cellH = coverH + dp(7) + titleH + authorH + dp(14);

        float avail = gridBot - gridTop;
        rows = (int) (avail / cellH);
        if (rows < 1) rows = 1;
        if (rows > 6) rows = 6;
        pageSize = rows * cols;
        clampPage();
    }

    private float dp(float v) {
        return v * dpf;
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas cv) {
        if (blinkFrames > 0) {
            blinkFrames--;
            cv.drawColor(BLINK);
            postInvalidateDelayed(50);
            return;
        }
        // reset shared stroke widths (some helpers change them)
        pIcon.setStrokeWidth(dpf * 1.5f);
        pStroke.setStrokeWidth(dpf);
        cv.drawColor(BG);

        sideHits.clear();
        toolHits.clear();
        coverHits.clear();
        bottomHits.clear();

        drawStatusBar(cv);
        drawSidebar(cv);

        if (section() == SEC_LIBRARY) {
            drawToolbar(cv);
            drawGrid(cv);
            drawBottomBar(cv);
        } else {
            drawSection(cv);
        }

        if (active != null) active.draw(cv);
    }

    private int section() {
        return currentSection;
    }

    private int currentSection = SEC_LIBRARY;

    // ---------------------------------------------------------- status bar

    private void drawStatusBar(Canvas cv) {
        pTime.setColor(INK);
        cv.drawText(Util.clock12(), dp(10), statusH / 2f + spf * 4, pTime);

        float x = W - dp(10);
        // "83%" + battery
        String pct = Util.batteryPct(getContext()) + "%";
        float pw = pTime.measureText(pct);
        cv.drawText(pct, x - pw, statusH / 2f + spf * 4, pTime);
        x -= pw + dp(5);
        float bs = dp(7);
        drawBattery(cv, x - bs, statusH / 2f, bs);
        x -= bs + dp(13);
        drawWifi(cv, x, statusH / 2f, dp(8));
        x -= dp(24);
        drawBluetooth(cv, x, statusH / 2f, dp(8));
        x -= dp(24);
        drawMute(cv, x, statusH / 2f, dp(9));
        x -= dp(24);
        drawRotate(cv, x, statusH / 2f, dp(9));

        pLine.setColor(HAIR);
        cv.drawRect(0, statusH, W, statusH + dpf, pLine);
    }

    private void drawBattery(Canvas cv, float x0, float cy, float s) {
        float h = s * 1.15f;
        float bodyW = s * 1.7f;
        float y0 = cy - h / 2f;
        pStroke.setColor(INK);
        pStroke.setStrokeWidth(dpf * 1.2f);
        cv.drawRect(x0, y0, x0 + bodyW, y0 + h, pStroke);
        cv.drawRect(x0 + bodyW + dpf, y0 + h * 0.3f, x0 + bodyW + dpf * 2.4f,
                y0 + h * 0.7f, pStroke);
        int pct = Util.batteryPct(getContext());
        pFill.setColor(INK);
        float iw = (bodyW - dpf * 3) * pct / 100f;
        cv.drawRect(x0 + dpf * 1.5f, y0 + dpf * 1.5f, x0 + dpf * 1.5f + iw,
                y0 + h - dpf * 1.5f, pFill);
    }

    private void drawWifi(Canvas cv, float cx, float cy, float s) {
        pIcon.setColor(INK);
        pFill.setColor(INK);
        cv.drawCircle(cx, cy + s * 0.45f, dpf * 1.3f, pFill);
        pIcon.setStrokeWidth(dpf * 1.4f);
        for (int k = 1; k <= 2; k++) {
            float r = s * 0.36f * k;
            cv.drawArc(new RectF(cx - r, cy + s * 0.45f - r * 1.6f,
                    cx + r, cy + s * 0.45f + r * 0.4f), 215f, 110f, false, pIcon);
        }
    }

    private void drawBluetooth(Canvas cv, float cx, float cy, float s) {
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.4f);
        float h = s * 0.9f;
        cv.drawLine(cx, cy - h / 2f, cx, cy + h / 2f, pIcon);
        cv.drawLine(cx, cy - h / 2f, cx + h * 0.38f, cy - h * 0.18f, pIcon);
        cv.drawLine(cx, cy + h / 2f, cx + h * 0.38f, cy + h * 0.18f, pIcon);
        cv.drawLine(cx + h * 0.38f, cy - h * 0.18f, cx - h * 0.38f, cy + h * 0.30f, pIcon);
        cv.drawLine(cx - h * 0.38f, cy - h * 0.30f, cx + h * 0.38f, cy + h * 0.18f, pIcon);
    }

    private void drawMute(Canvas cv, float cx, float cy, float s) {
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.3f);
        float h = s * 0.8f;
        // speaker
        pFill.setColor(INK);
        cv.drawRect(cx - s * 0.55f, cy - h * 0.28f, cx - s * 0.25f, cy + h * 0.28f, pFill);
        Path tri = new Path();
        tri.moveTo(cx - s * 0.25f, cy - h * 0.42f);
        tri.lineTo(cx + s * 0.05f, cy - h * 0.72f);
        tri.lineTo(cx + s * 0.05f, cy + h * 0.72f);
        tri.lineTo(cx - s * 0.25f, cy + h * 0.42f);
        tri.close();
        cv.drawPath(tri, pFill);
        // mute x
        pIcon.setStrokeWidth(dpf * 1.4f);
        cv.drawLine(cx + s * 0.22f, cy - h * 0.3f, cx + s * 0.6f, cy + h * 0.3f, pIcon);
        cv.drawLine(cx + s * 0.6f, cy - h * 0.3f, cx + s * 0.22f, cy + h * 0.3f, pIcon);
    }

    private void drawRotate(Canvas cv, float cx, float cy, float s) {
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.3f);
        RectF r = new RectF(cx - s * 0.5f, cy - s * 0.62f, cx + s * 0.5f, cy + s * 0.38f);
        cv.drawRect(r, pIcon);
        pFill.setColor(INK);
        cv.drawCircle(cx + s * 0.42f, cy + s * 0.52f, dpf * 1.7f, pFill);
    }

    // -------------------------------------------------------------- sidebar

    private void drawSidebar(Canvas cv) {
        pFill.setColor(SIDEBAR_BG);
        cv.drawRect(0, statusH + dpf, sideW, H, pFill);
        pLine.setColor(HAIR);
        cv.drawRect(sideW, statusH + dpf, sideW + dpf, H, pLine);

        float tileW = sideW - dp(12);
        float tileH = W < dp(480) ? dp(56) : dp(64);
        float x0 = (sideW - tileW) / 2f;
        float y = statusH + dp(10);
        float step = tileH + dp(12);

        int[] secs = {SEC_LIBRARY, SEC_STORE, SEC_NOTES, SEC_STORAGE, SEC_APPS};
        for (int i = 0; i < secs.length; i++) {
            drawSideTile(cv, x0, y, tileW, tileH, secs[i], SECTION_NAMES[secs[i]],
                    section() == secs[i]);
            sideHits.add(new float[]{x0, y, x0 + tileW, y + tileH, secs[i]});
            y += step;
        }
        float sy = H - tileH - dp(12);
        drawSideTile(cv, x0, sy, tileW, tileH, SEC_SETTINGS, "Settings",
                section() == SEC_SETTINGS);
        sideHits.add(new float[]{x0, sy, x0 + tileW, sy + tileH, SEC_SETTINGS});
    }

    private void drawSideTile(Canvas cv, float x, float y, float w, float h,
                              int sec, String label, boolean selected) {
        if (selected) {
            pFill.setColor(SEL);
            cv.drawRoundRect(new RectF(x, y, x + w, y + h), dp(2), dp(2), pFill);
        }
        float cx = x + w / 2f;
        float iconCy = y + h * 0.40f;
        float s = dp(22);
        pIcon.setColor(selected ? 0xFFF2F2F2 : INK);
        switch (sec) {
            case SEC_LIBRARY: iconBook(cv, cx, iconCy, s); break;
            case SEC_STORE: iconStore(cv, cx, iconCy, s); break;
            case SEC_NOTES: iconNotes(cv, cx, iconCy, s); break;
            case SEC_STORAGE: iconStorage(cv, cx, iconCy, s); break;
            case SEC_APPS: iconApps(cv, cx, iconCy, s); break;
            default: iconGear(cv, cx, iconCy, s); break;
        }
        pLabel.setColor(selected ? 0xFFF2F2F2 : INK);
        cv.drawText(label, cx, y + h * 0.40f + s / 2f + dp(13), pLabel);
    }

    // -------------------------------------------------------------- toolbar

    private void drawToolbar(Canvas cv) {
        float cy = statusH + toolH / 2f;
        float s = dp(21);
        pIcon.setColor(INK);

        float x = contentL + dp(20);
        for (int i = 0; i < 3; i++) {
            if (i == 0) iconClock(cv, x, cy, s);
            else if (i == 1) iconColumns(cv, x, cy, s);
            else iconCloud(cv, x, cy, s);
            toolHits.add(new float[]{x - dp(15), cy - dp(15), x + dp(15), cy + dp(15), i});
            x += dp(34);
        }

        float xr = W - dp(20);
        for (int i = 0; i < 3; i++) {
            int action = (i == 0) ? 10 : (i == 1 ? 11 : 12); // 10 search, 11 sort, 12 view
            if (i == 0) iconSearch(cv, xr, cy, s);
            else if (i == 1) iconSort(cv, xr, cy, s);
            else iconView(cv, xr, cy, s);
            toolHits.add(new float[]{xr - dp(15), cy - dp(15), xr + dp(15), cy + dp(15), action});
            xr -= dp(34);
        }
    }

    // ----------------------------------------------------------------- grid

    private void drawGrid(Canvas cv) {
        float pad = dp(18);
        float gap = dp(20);
        float contentW = W - contentL;

        // active search chip
        if (query.length() > 0) {
            drawSearchChip(cv, contentL + pad, statusH + toolH + dp(8));
        }

        if (filtered.isEmpty()) {
            drawEmptyGrid(cv);
            return;
        }

        int start = page * pageSize;
        for (int i = start; i < filtered.size() && i < start + pageSize; i++) {
            int k = i - start;
            int r = k / cols;
            int c = k % cols;
            float x = contentL + pad + c * (coverW + gap);
            float y = gridTop + r * cellH;
            Prefs.BookEntry e = filtered.get(i);
            drawCover(cv, e, x, y, coverW, coverH);
            coverHits.add(new float[]{x, y, x + coverW, y + coverH, i});
            drawCoverText(cv, e, x, y + coverH + dp(7));
        }
    }

    private void drawSearchChip(Canvas cv, float x, float y) {
        float h = dp(26);
        float tw = pBody2.measureText("Search: \"" + query + "\"");
        float w = tw + dp(40);
        if (w > W - contentL - dp(36)) w = W - contentL - dp(36);
        pFill.setColor(PANEL);
        cv.drawRect(x, y, x + w, y + h, pFill);
        pStroke.setColor(COVER_BORDER);
        pStroke.setStrokeWidth(dpf);
        cv.drawRect(x, y, x + w, y + h, pStroke);
        CharSequence t = TextUtils.ellipsize("Search: \"" + query + "\"", pBody2, w - dp(38),
                TextUtils.TruncateAt.END);
        pBody2.setColor(INK);
        cv.drawText(t.toString(), x + dp(10), y + h / 2f + spf * 4.4f, pBody2);
        // X
        float bx = x + w - dp(15);
        float by = y + h / 2f;
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.6f);
        cv.drawLine(bx - dp(4), by - dp(4), bx + dp(4), by + dp(4), pIcon);
        cv.drawLine(bx + dp(4), by - dp(4), bx - dp(4), by + dp(4), pIcon);
        bottomHits.add(new float[]{x + w - dp(24), y, x + w, y + h, 60});
    }

    private void drawEmptyGrid(Canvas cv) {
        float cx = (contentL + W) / 2f;
        float cy = (gridTop + gridBot) / 2f;
        pIcon.setColor(0xFF9A9A9A);
        pIcon.setStrokeWidth(dpf * 1.5f);
        iconBook(cv, cx, cy - dp(38), dp(40));
        pHead.setColor(INK);
        pHead.setTextAlign(Paint.Align.CENTER);
        cv.drawText("No books", cx, cy + dp(4), pHead);
        pHead.setTextAlign(Paint.Align.LEFT);
        String sub = (query.length() > 0 || shelfFilter != null)
                ? "Nothing matches the current filter."
                : "Import TXT, EPUB or PDF files to fill the bookshelf.";
        pBody2.setTextAlign(Paint.Align.CENTER);
        cv.drawText(sub, cx, cy + dp(26), pBody2);
        pBody2.setTextAlign(Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- cover

    private void drawCover(Canvas cv, Prefs.BookEntry e, float x, float y, float w, float h) {
        RectF r = new RectF(x, y, x + w, y + h);
        int hash = Math.abs(e.title.hashCode());
        int variant;
        String fmt = e.format == null ? "txt" : e.format.toLowerCase(Locale.US);
        if (fmt.equals("txt")) variant = 0;
        else if (fmt.equals("pdf")) variant = 2;
        else variant = 1 + (hash % 3); // epub / other: dark art, classic, dark minimal

        switch (variant) {
            case 0: drawCoverChevrons(cv, r, e); break;
            case 1: drawCoverDarkArt(cv, r, e); break;
            case 2: drawCoverClassic(cv, r, e); break;
            default: drawCoverDarkMin(cv, r, e); break;
        }

        // thin border
        pStroke.setColor(COVER_BORDER);
        pStroke.setStrokeWidth(dpf);
        cv.drawRect(r, pStroke);

        // footer: format chip (left), check + star (right)
        String chip = fmt.toUpperCase(Locale.US);
        float chipW = pChip.measureText(chip) + dp(8);
        float chipH = dp(11);
        float chipY = r.bottom - chipH - dp(3);
        pFill.setColor(0xFFFFFFFF);
        cv.drawRect(r.left + dp(3), chipY, r.left + dp(3) + chipW, chipY + chipH, pFill);
        pStroke.setColor(COVER_BORDER);
        pStroke.setStrokeWidth(dpf);
        cv.drawRect(r.left + dp(3), chipY, r.left + dp(3) + chipW, chipY + chipH, pStroke);
        pChip.setColor(INK);
        cv.drawText(chip, r.left + dp(3) + chipW / 2f, chipY + chipH / 2f + spf * 2.9f, pChip);

        float fy = r.bottom - dp(10.5f);
        float sx = r.right - dp(12);
        drawStar(cv, sx, fy, dp(4.6f), e.favorite);
        float chx = sx - dp(16);
        drawCheckCircle(cv, chx, fy, dp(4.6f));
    }

    /** Light placeholder with big chevrons, like reference PPTX/DOC/TXT docs. */
    private void drawCoverChevrons(Canvas cv, RectF r, Prefs.BookEntry e) {
        pFill.setColor(0xFFE9E9E8);
        cv.drawRect(r, pFill);
        // top text bars
        pFill.setColor(0xFFC6C6C6);
        cv.drawRect(r.left + dp(8), r.top + dp(8), r.left + dp(8) + r.width() * 0.52f,
                r.top + dp(12), pFill);
        cv.drawRect(r.left + dp(8), r.top + dp(15), r.left + dp(8) + r.width() * 0.34f,
                r.top + dp(19), pFill);
        // big left-pointing chevrons
        float mx = r.centerX() + r.width() * 0.10f;
        float my = r.centerY() + dp(2);
        float cw = r.width() * 0.34f;
        float chh = r.height() * 0.26f;
        Path big = chevronPath(mx + cw * 0.4f, my, cw, chh);
        pFill.setColor(0xFFBDBDBD);
        cv.drawPath(big, pFill);
        Path small = chevronPath(mx - cw * 0.42f, my, cw * 0.72f, chh * 0.86f);
        pFill.setColor(0xFFD2D2D1);
        cv.drawPath(small, pFill);
        // bottom-left solid block
        pFill.setColor(0xFFD8D8D7);
        cv.drawRect(r.left + dp(7), r.bottom - dp(22), r.left + dp(7) + r.width() * 0.42f,
                r.bottom - dp(16), pFill);
    }

    private Path chevronPath(float cx, float cy, float w, float h) {
        Path p = new Path();
        p.moveTo(cx - w / 2f, cy - h / 2f);
        p.lineTo(cx + w * 0.08f, cy - h / 2f);
        p.lineTo(cx + w / 2f, cy);
        p.lineTo(cx + w * 0.08f, cy + h / 2f);
        p.lineTo(cx - w / 2f, cy + h / 2f);
        p.lineTo(cx + w * 0.16f, cy);
        p.close();
        return p;
    }

    /** Dark art cover: italic serif title on near-black, thin inner frame. */
    private void drawCoverDarkArt(Canvas cv, RectF r, Prefs.BookEntry e) {
        pFill.setColor(0xFF3F3F3F);
        cv.drawRect(r, pFill);
        pStroke.setColor(0xFF6B6B6B);
        pStroke.setStrokeWidth(dpf);
        RectF inner = new RectF(r.left + dp(5), r.top + dp(5), r.right - dp(5), r.bottom - dp(17));
        cv.drawRect(inner, pStroke);

        float cy = r.top + r.height() * 0.36f;
        pCoverSerifIt.setColor(0xFFDCDCDC);
        drawWrappedCentered(cv, pCoverSerifIt, e.title, r.centerX(), cy,
                r.width() - dp(18), r.height() * 0.5f);
        if (e.author.length() > 0) {
            pCoverSans.setColor(0xFFABABAB);
            CharSequence a = TextUtils.ellipsize(e.author, pCoverSans,
                    r.width() - dp(16), TextUtils.TruncateAt.END);
            cv.drawText(a.toString(), r.centerX(), r.bottom - dp(24), pCoverSans);
        }
    }

    /** Classic light cover: double border, serif title + author, like old print. */
    private void drawCoverClassic(Canvas cv, RectF r, Prefs.BookEntry e) {
        pFill.setColor(0xFFF0F0EF);
        cv.drawRect(r, pFill);
        pStroke.setColor(0xFF2E2E2E);
        pStroke.setStrokeWidth(dpf);
        cv.drawRect(new RectF(r.left + dp(5), r.top + dp(5), r.right - dp(5), r.bottom - dp(17)), pStroke);
        pStroke.setStrokeWidth(dpf * 0.7f);
        cv.drawRect(new RectF(r.left + dp(8), r.top + dp(8), r.right - dp(8), r.bottom - dp(20)), pStroke);

        float cy = r.top + r.height() * 0.30f;
        pCoverSerif.setColor(0xFF1E1E1E);
        drawWrappedCentered(cv, pCoverSerif, e.title, r.centerX(), cy,
                r.width() - dp(24), r.height() * 0.44f);
        // ornament line
        pLine.setColor(0xFF8A8A8A);
        float ly = cy + r.height() * 0.28f;
        cv.drawRect(r.centerX() - dp(16), ly, r.centerX() + dp(16), ly + dpf, pLine);
        if (e.author.length() > 0) {
            pCoverSans.setColor(0xFF4A4A4A);
            CharSequence a = TextUtils.ellipsize(e.author, pCoverSans,
                    r.width() - dp(20), TextUtils.TruncateAt.END);
            cv.drawText(a.toString(), r.centerX(), ly + dp(13), pCoverSans);
        }
    }

    /** Dark minimal cover: small sans title, lots of black. */
    private void drawCoverDarkMin(Canvas cv, RectF r, Prefs.BookEntry e) {
        pFill.setColor(0xFF2B2B2B);
        cv.drawRect(r, pFill);
        pFill.setColor(0xFF4A4A4A);
        cv.drawRect(r.left + dp(8), r.top + dp(8), r.right - dp(8), r.top + dp(9), pFill);
        float cy = r.top + r.height() * 0.30f;
        pCoverSans.setColor(0xFFCFCFCF);
        drawWrappedCentered(cv, pCoverSans, e.title, r.centerX(), cy,
                r.width() - dp(20), r.height() * 0.5f);
    }

    private void drawWrappedCentered(Canvas cv, TextPaint p, String text,
                                     float cx, float cy, float maxW, float maxH) {
        if (text == null || text.length() == 0) return;
        String[] words = text.split("\\s+");
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String cand = cur.length() == 0 ? w : cur.toString() + " " + w;
            if (p.measureText(cand) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        float lineH = -p.ascent() + p.descent() + dp(2);
        int maxLines = (int) Math.max(1, Math.min(4, maxH / lineH));
        float y = cy - (Math.min(lines.size(), maxLines) - 1) * lineH / 2f
                - (p.ascent() + p.descent()) / 2f;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String ln = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                ln = TextUtils.ellipsize(ln, p, maxW, TextUtils.TruncateAt.END).toString();
            }
            cv.drawText(ln, cx, y, p);
            y += lineH;
        }
    }

    private void drawCoverText(Canvas cv, Prefs.BookEntry e, float x, float y) {
        float w = coverW;
        pTitle.setColor(INK);
        String[] lines = wrapTwo(e.title, pTitle, w);
        cv.drawText(lines[0], x, y + dp(12), pTitle);
        if (lines[1] != null) cv.drawText(lines[1], x, y + dp(12) + dp(17), pTitle);
        if (e.author.length() > 0) {
            CharSequence a = TextUtils.ellipsize(e.author, pAuthor, w, TextUtils.TruncateAt.END);
            pAuthor.setColor(INK2);
            cv.drawText(a.toString(), x, y + dp(12) + dp(17) + dp(15), pAuthor);
        }
    }

    private String[] wrapTwo(String text, TextPaint p, float maxW) {
        String[] out = new String[]{"", null};
        if (text == null || text.length() == 0) return out;
        if (p.measureText(text) <= maxW) {
            out[0] = text;
            return out;
        }
        String[] words = text.split("\\s+");
        StringBuilder l1 = new StringBuilder();
        int i = 0;
        for (; i < words.length; i++) {
            String cand = l1.length() == 0 ? words[i] : l1 + " " + words[i];
            if (p.measureText(cand) > maxW && l1.length() > 0) break;
            l1.setLength(0);
            l1.append(cand);
        }
        StringBuilder l2 = new StringBuilder();
        for (; i < words.length; i++) {
            String cand = l2.length() == 0 ? words[i] : l2 + " " + words[i];
            if (p.measureText(cand) > maxW && l2.length() > 0) break;
            l2.setLength(0);
            l2.append(cand);
        }
        out[0] = TextUtils.ellipsize(l1.length() > 0 ? l1.toString() : text, p, maxW,
                TextUtils.TruncateAt.END).toString();
        out[1] = TextUtils.ellipsize(l2.toString(), p, maxW, TextUtils.TruncateAt.END).toString();
        if (out[1].length() == 0) out[1] = null;
        return out;
    }

    // ----------------------------------------------------------- footer icons

    private void drawStar(Canvas cv, float cx, float cy, float r, boolean filled) {
        Path p = new Path();
        for (int k = 0; k < 10; k++) {
            double ang = -Math.PI / 2 + k * Math.PI / 5;
            float rr = (k % 2 == 0) ? r : r * 0.45f;
            float px = cx + (float) Math.cos(ang) * rr;
            float py = cy + (float) Math.sin(ang) * rr;
            if (k == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
        if (filled) {
            pFill.setColor(INK);
            cv.drawPath(p, pFill);
        } else {
            pStroke.setColor(INK);
            pStroke.setStrokeWidth(dpf);
            cv.drawPath(p, pStroke);
        }
    }

    private void drawCheckCircle(Canvas cv, float cx, float cy, float r) {
        pStroke.setColor(INK);
        pStroke.setStrokeWidth(dpf);
        cv.drawCircle(cx, cy, r, pStroke);
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.1f);
        cv.drawLine(cx - r * 0.45f, cy, cx - r * 0.1f, cy + r * 0.38f, pIcon);
        cv.drawLine(cx - r * 0.1f, cy + r * 0.38f, cx + r * 0.5f, cy - r * 0.38f, pIcon);
    }

    // ------------------------------------------------------------- bottom bar

    private void drawBottomBar(Canvas cv) {
        float y = H - bottomH;
        pBottom.setColor(INK);
        String total = "Total: Bookshelves(" + shelves.size() + ") / Books(" + books.size() + ")";
        cv.drawText(total, contentL + dp(18), y + bottomH / 2f + spf * 4.5f, pBottom);

        float xr = W - dp(18);
        // next page chevron (rightmost)
        float chevS = dp(9);
        drawChevron(cv, xr - chevS, y + bottomH / 2f, chevS, true);
        bottomHits.add(new float[]{xr - dp(26), y, xr, y + bottomH, 62});
        xr -= dp(30);
        // page text
        String pt = (page + 1) + "/" + pageCount;
        float tw = pBottom.measureText(pt);
        pBottom.setColor(INK);
        cv.drawText(pt, xr - tw, y + bottomH / 2f + spf * 4.5f, pBottom);
        xr -= tw + dp(12);
        // prev chevron
        drawChevron(cv, xr - chevS, y + bottomH / 2f, chevS, false);
        bottomHits.add(new float[]{xr - dp(26), y, xr, y + bottomH, 61});
        xr -= dp(34);
        // refresh
        drawRefresh(cv, xr, y + bottomH / 2f, dp(11));
        bottomHits.add(new float[]{xr - dp(14), y, xr + dp(14), y + bottomH, 63});
    }

    private void drawChevron(Canvas cv, float cx, float cy, float s, boolean right) {
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.8f);
        float d = right ? s : -s;
        cv.drawLine(cx - d * 0.5f, cy - s * 0.8f, cx + d * 0.5f, cy, pIcon);
        cv.drawLine(cx + d * 0.5f, cy, cx - d * 0.5f, cy + s * 0.8f, pIcon);
    }

    private void drawRefresh(Canvas cv, float cx, float cy, float r) {
        pIcon.setColor(INK);
        pIcon.setStrokeWidth(dpf * 1.6f);
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        cv.drawArc(oval, 40f, 280f, false, pIcon);
        // arrowhead
        float ang = (float) Math.toRadians(40f);
        float ax = cx + (float) Math.cos(ang) * r;
        float ay = cy + (float) Math.sin(ang) * r;
        pFill.setColor(INK);
        Path a = new Path();
        a.moveTo(ax + dpf * 2, ay - dpf * 4);
        a.lineTo(ax - dpf * 5, ay - dpf * 2);
        a.lineTo(ax + dpf * 2.5f, ay + dpf * 4);
        a.close();
        cv.drawPath(a, pFill);
    }

    // ------------------------------------------------------------- sections

    private void drawSection(Canvas cv) {
        int sec = section();
        pHead.setColor(INK);
        cv.drawText(SECTION_NAMES[sec], contentL + dp(18), statusH + dp(30), pHead);
        pLine.setColor(HAIR);
        cv.drawRect(contentL, statusH + dp(44), W, statusH + dp(44) + dpf, pLine);

        float top = statusH + dp(58);
        if (sec == SEC_STORAGE) drawStorageContent(cv, top);
        else if (sec == SEC_SETTINGS) drawSettingsContent(cv, top);
        else if (sec == SEC_APPS) drawAppsContent(cv, top);
        else if (sec == SEC_STORE) drawSimpleEmpty(cv, top, "store",
                "Store is not available on this device.",
                "Books are managed locally on this build.");
        else if (sec == SEC_NOTES) drawSimpleEmpty(cv, top, "notes",
                "No notes yet",
                "Highlights you make while reading will appear here.");
    }

    private void drawSimpleEmpty(Canvas cv, float top, String kind, String title, String sub) {
        float cx = (contentL + W) / 2f;
        float cy = top + (H - top - bottomH) / 2f - dp(30);
        pIcon.setColor(0xFF9A9A9A);
        pIcon.setStrokeWidth(dpf * 1.5f);
        float s = dp(44);
        if (kind.equals("store")) iconStore(cv, cx, cy, s);
        else iconNotes(cv, cx, cy, s);
        pHead.setColor(INK);
        pHead.setTextAlign(Paint.Align.CENTER);
        cv.drawText(title, cx, cy + s / 2f + dp(32), pHead);
        pHead.setTextAlign(Paint.Align.LEFT);
        pBody2.setTextAlign(Paint.Align.CENTER);
        cv.drawText(sub, cx, cy + s / 2f + dp(54), pBody2);
        pBody2.setTextAlign(Paint.Align.LEFT);
    }

    private void drawStorageContent(Canvas cv, float top) {
        float x = contentL + dp(18);
        float w = W - x - dp(18);
        float y = top + dp(10);

        StatFs stat = new StatFs(getContext().getFilesDir().getPath());
        long totalB = stat.getTotalBytes();
        long freeB = stat.getAvailableBytes();
        long usedB = totalB - freeB;
        double gb = 1073741824.0;
        int usedPct = totalB > 0 ? (int) (usedB * 100 / totalB) : 0;

        pBody.setColor(INK);
        cv.drawText("Internal storage", x, y, pBody);
        y += dp(18);
        pFill.setColor(0xFFC8C8C8);
        cv.drawRect(x, y, x + w, y + dp(10), pFill);
        pFill.setColor(INK);
        cv.drawRect(x, y, x + w * usedPct / 100f, y + dp(10), pFill);
        pStroke.setColor(COVER_BORDER);
        pStroke.setStrokeWidth(dpf);
        cv.drawRect(x, y, x + w, y + dp(10), pStroke);
        y += dp(26);

        pBody2.setColor(INK2);
        cv.drawText(String.format(Locale.US, "Used %.1f GB of %.1f GB  (%d%%)",
                usedB / gb, totalB / gb, usedPct), x, y, pBody2);
        y += dp(22);
        cv.drawText(String.format(Locale.US, "Free space: %.1f GB", freeB / gb), x, y, pBody2);
        y += dp(30);

        pBody.setColor(INK);
        cv.drawText("Documents on this device", x, y, pBody);
        y += dp(22);
        int epub = 0, txt = 0, pdf = 0, other = 0;
        for (Prefs.BookEntry e : books) {
            String f = e.format.toLowerCase(Locale.US);
            if (f.equals("epub")) epub++;
            else if (f.equals("txt")) txt++;
            else if (f.equals("pdf")) pdf++;
            else other++;
        }
        pBody2.setColor(INK2);
        cv.drawText("Books: " + books.size() + "    EPUB: " + epub + "    TXT: " + txt
                + "    PDF: " + pdf + (other > 0 ? "    Other: " + other : ""), x, y, pBody2);
        y += dp(22);
        cv.drawText("Bookshelves: " + shelves.size(), x, y, pBody2);
    }

    private void drawSettingsContent(Canvas cv, float top) {
        float x = contentL + dp(18);
        float rowH = dp(44);
        float w = W - x - dp(18);
        float y = top + dp(8);

        pBody.setColor(INK);
        cv.drawText("Reading experience", x, y, pBody);
        y += dp(16);

        y = settingsRow(cv, x, y, w, rowH, "Page turn flash", settings.flash, 70);
        y = settingsRow(cv, x, y, w, rowH, "Haptic feedback", settings.haptic, 71);

        y += dp(16);
        pBody.setColor(INK);
        cv.drawText("Reading theme", x, y, pBody);
        y += dp(16);
        String[] themes = {"Warm paper", "White", "Dark"};
        for (int i = 0; i < themes.length; i++) {
            boolean on = settings.theme == i;
            // radio
            pStroke.setColor(INK);
            pStroke.setStrokeWidth(dpf * 1.3f);
            float ry = y + rowH / 2f;
            cv.drawCircle(x + dp(10), ry, dp(6), pStroke);
            if (on) {
                pFill.setColor(INK);
                cv.drawCircle(x + dp(10), ry, dp(3), pFill);
            }
            pBody.setColor(INK);
            cv.drawText(themes[i], x + dp(28), ry + spf * 5, pBody);
            pLine.setColor(PANEL_HAIR);
            cv.drawRect(x, y + rowH - dpf, x + w, y + rowH, pLine);
            bottomHits.add(new float[]{x, y, x + w, y + rowH, 72 + i});
            y += rowH;
        }

        y += dp(18);
        pBody.setColor(INK);
        cv.drawText("About", x, y, pBody);
        y += dp(22);
        pBody2.setColor(INK2);
        cv.drawText("InkBook 1.0.0 - e-ink reader for Android", x, y, pBody2);
    }

    private float settingsRow(Canvas cv, float x, float y, float w, float rowH,
                              String label, boolean on, int action) {
        float ry = y + rowH / 2f;
        pStroke.setColor(INK);
        pStroke.setStrokeWidth(dpf * 1.4f);
        cv.drawRect(new RectF(x, ry - dp(7), x + dp(14), ry + dp(7)), pStroke);
        if (on) {
            pFill.setColor(INK);
            cv.drawRect(new RectF(x + dp(3), ry - dp(4), x + dp(11), ry + dp(4)), pFill);
        }
        pBody.setColor(INK);
        cv.drawText(label, x + dp(28), ry + spf * 5, pBody);
        pLine.setColor(PANEL_HAIR);
        cv.drawRect(x, y + rowH - dpf, x + w, y + rowH, pLine);
        bottomHits.add(new float[]{x, y, x + w, y + rowH, action});
        return y + rowH;
    }

    private void drawAppsContent(Canvas cv, float top) {
        float x = contentL + dp(18);
        float y = top + dp(12);
        float tw = dp(120);
        float th = dp(108);
        float gap = dp(16);
        String[] names = {"Reader", "Import book", "Refresh screen"};
        int[] actions = {80, 81, 82};
        for (int i = 0; i < names.length; i++) {
            float tx = x + i * (tw + gap);
            if (tx + tw > W) break;
            pFill.setColor(PANEL);
            cv.drawRect(new RectF(tx, y, tx + tw, y + th), pFill);
            pStroke.setColor(COVER_BORDER);
            pStroke.setStrokeWidth(dpf);
            cv.drawRect(new RectF(tx, y, tx + tw, y + th), pStroke);
            float cx = tx + tw / 2f;
            float icy = y + th * 0.38f;
            pIcon.setColor(INK);
            if (i == 0) iconBook(cv, cx, icy, dp(26));
            else if (i == 1) iconStorage(cv, cx, icy, dp(26));
            else drawRefresh(cv, cx, icy, dp(13));
            pLabel.setColor(INK);
            pLabel.setTextSize(spf * 11.5f);
            cv.drawText(names[i], cx, y + th - dp(18), pLabel);
            pLabel.setTextSize(spf * 10f);
            bottomHits.add(new float[]{tx, y, tx + tw, y + th, actions[i]});
        }
    }

    // ---------------------------------------------------------- panel base

    private RectF drawPanel(Canvas cv, float pw, float ph) {
        pw = Math.min(pw, W - dp(24));
        ph = Math.min(ph, H - dp(40));
        pFill.setColor(SCRIM);
        cv.drawRect(0, 0, W, H, pFill);
        RectF r = new RectF((W - pw) / 2f, (H - ph) / 2f, (W + pw) / 2f, (H + ph) / 2f);
        pFill.setColor(PANEL);
        cv.drawRoundRect(r, dp(2), dp(2), pFill);
        pStroke.setColor(INK);
        pStroke.setStrokeWidth(dpf);
        cv.drawRoundRect(r, dp(2), dp(2), pStroke);
        return r;
    }

    private void panelTitle(Canvas cv, RectF r, String title) {
        pMenuTitle.setColor(INK);
        cv.drawText(title, r.left + dp(16), r.top + dp(26), pMenuTitle);
        pLine.setColor(PANEL_HAIR);
        cv.drawRect(r.left, r.top + dp(38), r.right, r.top + dp(38) + dpf, pLine);
    }

    private void panelRow(Canvas cv, RectF r, float y, float rowH, String label,
                          int action, boolean selected, boolean radio, ArrayList<float[]> hits) {
        if (selected) {
            pFill.setColor(0xFFE4E4E3);
            cv.drawRect(r.left + dpf, y, r.right - dpf, y + rowH, pFill);
        }
        if (radio) {
            pStroke.setColor(INK);
            pStroke.setStrokeWidth(dpf * 1.3f);
            float cy = y + rowH / 2f;
            cv.drawCircle(r.left + dp(22), cy, dp(6), pStroke);
            if (selected) {
                pFill.setColor(INK);
                cv.drawCircle(r.left + dp(22), cy, dp(3), pFill);
            }
        }
        pMenu.setColor(INK);
        cv.drawText(label, r.left + dp(radio ? 40 : 16), y + rowH / 2f + spf * 5, pMenu);
        pLine.setColor(PANEL_HAIR);
        cv.drawRect(r.left + dpf, y + rowH - dpf, r.right - dpf, y + rowH, pLine);
        if (hits != null) hits.add(new float[]{r.left, y, r.right, y + rowH, action});
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

        abstract void draw(Canvas cv);

        abstract boolean onTouch(float x, float y);

        void onDown(float x, float y) { }
        void onMove(float x, float y) { }
        void onCancel() { }
    }

    private void show(Overlay o) {
        active = o;
        o.visible = true;
        invalidate();
    }

    void hide() {
        if (active != null) active.visible = false;
        active = null;
        invalidate();
    }

    class SortOverlay extends Overlay {
        void draw(Canvas cv) {
            clearHits();
            String[] opts = {"Title", "Author", "Date added", "Recently opened"};
            float rowH = dp(42);
            RectF r = drawPanel(cv, dp(224), dp(38) + opts.length * rowH + dp(8));
            panelTitle(cv, r, "Sort by");
            float y = r.top + dp(46);
            for (int i = 0; i < opts.length; i++) {
                panelRow(cv, r, y, rowH, opts[i], i, sortMode == i, true, hits);
                y += rowH;
            }
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a < 4) {
                sortMode = a;
                page = 0;
                rebuild();
                hide();
                invalidate();
            }
            return true;
        }
    }

    class ShelfOverlay extends Overlay {
        void draw(Canvas cv) {
            clearHits();
            float rowH = dp(42);
            int n = 1 + shelves.size() + 1;
            RectF r = drawPanel(cv, dp(252), dp(38) + n * rowH + dp(8));
            panelTitle(cv, r, "Bookshelves");
            float y = r.top + dp(46);
            panelRow(cv, r, y, rowH, "All books (" + books.size() + ")",
                    0, shelfFilter == null, true, hits);
            y += rowH;
            for (int i = 0; i < shelves.size(); i++) {
                int count = 0;
                for (Prefs.BookEntry e : books) {
                    if (shelves.get(i).equals(e.shelf)) count++;
                }
                panelRow(cv, r, y, rowH, shelves.get(i) + " (" + count + ")",
                        100 + i, shelfFilter != null && shelfFilter.equals(shelves.get(i)),
                        true, hits);
                y += rowH;
            }
            panelRow(cv, r, y, rowH, "+ New bookshelf", 9, false, false, hits);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a == 0) {
                shelfFilter = null;
                page = 0;
                rebuild();
                hide();
                invalidate();
            } else if (a == 9) {
                pendingShelfFor = null;
                showNewShelfInput();
            } else if (a >= 100) {
                shelfFilter = shelves.get(a - 100);
                page = 0;
                rebuild();
                hide();
                invalidate();
            }
            return true;
        }
    }

    class BookActionsOverlay extends Overlay {
        void draw(Canvas cv) {
            clearHits();
            String[] opts;
            if (actionTarget.favorite) {
                opts = new String[]{"Open", "Add to bookshelf", "Remove favorite", "Delete"};
            } else {
                opts = new String[]{"Open", "Add to bookshelf", "Mark favorite", "Delete"};
            }
            float rowH = dp(42);
            RectF r = drawPanel(cv, dp(244), dp(38) + opts.length * rowH + dp(8));
            panelTitle(cv, r, actionTarget.title);
            float y = r.top + dp(46);
            for (int i = 0; i < opts.length; i++) {
                panelRow(cv, r, y, rowH, opts[i], i, false, false, hits);
                y += rowH;
            }
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            final Prefs.BookEntry e = actionTarget;
            if (a == 0) {
                hide();
                host.onOpenBook(e);
            } else if (a == 1) {
                show(new AddToShelfOverlay());
                invalidate();
            } else if (a == 2) {
                e.favorite = !e.favorite;
                saveBooks();
                rebuild();
                hide();
                invalidate();
            } else if (a == 3) {
                showConfirm("Delete book",
                        "Remove \"" + e.title + "\" and its reading progress?",
                        "DELETE", 0);
            }
            return true;
        }
    }

    class AddToShelfOverlay extends Overlay {
        void draw(Canvas cv) {
            clearHits();
            float rowH = dp(42);
            int extra = (actionTarget.shelf.length() > 0) ? 1 : 0;
            int n = extra + shelves.size() + 1;
            RectF r = drawPanel(cv, dp(252), dp(38) + n * rowH + dp(8));
            panelTitle(cv, r, "Add to bookshelf");
            float y = r.top + dp(46);
            if (extra == 1) {
                panelRow(cv, r, y, rowH, "Remove from shelf", 8,
                        actionTarget.shelf.length() == 0, false, hits);
                y += rowH;
            }
            for (int i = 0; i < shelves.size(); i++) {
                panelRow(cv, r, y, rowH, shelves.get(i),
                        100 + i, actionTarget.shelf.equals(shelves.get(i)), true, hits);
                y += rowH;
            }
            panelRow(cv, r, y, rowH, "+ New bookshelf", 9, false, false, hits);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            final Prefs.BookEntry e = actionTarget;
            if (a == 8) {
                e.shelf = "";
                saveBooks();
                rebuild();
                hide();
                invalidate();
            } else if (a == 9) {
                pendingShelfFor = e.id;
                showNewShelfInput();
            } else if (a >= 100) {
                e.shelf = shelves.get(a - 100);
                saveBooks();
                rebuild();
                hide();
                invalidate();
            }
            return true;
        }
    }

    class ConfirmOverlay extends Overlay {
        String title;
        String message;
        String confirmLabel;
        int code; // 0 delete book, 1 delete shelf

        ConfirmOverlay(String t, String m, String label, int c) {
            title = t;
            message = m;
            confirmLabel = label;
            code = c;
        }

        void draw(Canvas cv) {
            clearHits();
            float rowH = dp(46);
            RectF r = drawPanel(cv, dp(280), dp(38) + rowH * 2 + dp(20));
            panelTitle(cv, r, title);
            pBody2.setColor(INK);
            float y = r.top + dp(52);
            List<String> wrapped = wrapText(message, pBody2, r.width() - dp(32));
            for (String ln : wrapped) {
                cv.drawText(ln, r.left + dp(16), y, pBody2);
                y += dp(18);
            }
            float by = r.bottom - rowH;
            // two text buttons, right aligned
            float cw = pMenu.measureText("Cancel") + dp(24);
            float dw = pMenu.measureText(confirmLabel) + dp(24);
            float dx1 = r.right - dp(16);
            float dx0 = dx1 - dw;
            float cx1 = dx0 - dp(12);
            float cx0 = cx1 - cw;
            pMenu.setColor(INK);
            pMenu.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Cancel", (cx0 + cx1) / 2f, by + rowH / 2f + spf * 5, pMenu);
            cv.drawText(confirmLabel, (dx0 + dx1) / 2f, by + rowH / 2f + spf * 5, pMenu);
            pMenu.setTextAlign(Paint.Align.LEFT);
            pLine.setColor(PANEL_HAIR);
            cv.drawRect(r.left + dpf, by, r.right - dpf, by + dpf, pLine);
            hit(cx0, by, cx1, by + rowH, 0);
            hit(dx0, by, dx1, by + rowH, 1);
        }

        boolean onTouch(float x, float y) {
            int a = hitAt(x, y);
            if (a < 0) {
                hide();
                return true;
            }
            if (a == 1) {
                if (code == 0 && actionTarget != null) {
                    Prefs.BookEntry t = actionTarget;
                    hide();
                    host.onDeleteBook(t);
                    refresh();
                    return true;
                }
            }
            hide();
            invalidate();
            return true;
        }
    }

    class InfoOverlay extends Overlay {
        String title;
        String message;

        InfoOverlay(String t, String m) {
            title = t;
            message = m;
        }

        void draw(Canvas cv) {
            clearHits();
            float rowH = dp(46);
            RectF r = drawPanel(cv, dp(280), dp(38) + rowH * 2 + dp(20));
            panelTitle(cv, r, title);
            float y = r.top + dp(52);
            List<String> wrapped = wrapText(message, pBody2, r.width() - dp(32));
            for (String ln : wrapped) {
                cv.drawText(ln, r.left + dp(16), y, pBody2);
                y += dp(18);
            }
            float by = r.bottom - rowH;
            float dw = pMenu.measureText("OK") + dp(28);
            float dx0 = r.right - dp(16) - dw;
            float dx1 = r.right - dp(16);
            pMenu.setColor(INK);
            pMenu.setTextAlign(Paint.Align.CENTER);
            cv.drawText("OK", (dx0 + dx1) / 2f, by + rowH / 2f + spf * 5, pMenu);
            pMenu.setTextAlign(Paint.Align.LEFT);
            pLine.setColor(PANEL_HAIR);
            cv.drawRect(r.left + dpf, by, r.right - dpf, by + dpf, pLine);
            hit(dx0, by, dx1, by + rowH, 0);
        }

        boolean onTouch(float x, float y) {
            hide();
            invalidate();
            return true;
        }
    }

    private void showConfirm(String title, String msg, String label, int code) {
        show(new ConfirmOverlay(title, msg, label, code));
    }

    private List<String> wrapText(String text, TextPaint p, float maxW) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.length() == 0) return out;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String cand = cur.length() == 0 ? w : cur + " " + w;
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

    // -------------------------------------------------------------- dialogs

    interface TextReady {
        void onText(String s);
    }

    private void showNewShelfInput() {
        showTextInput("New bookshelf", "Shelf name", new TextReady() {
            public void onText(String s) {
                if (s.length() == 0) return;
                if (!shelves.contains(s)) {
                    shelves.add(s);
                    prefs.saveShelves(shelves);
                }
                if (pendingShelfFor != null) {
                    for (Prefs.BookEntry e : books) {
                        if (pendingShelfFor.equals(e.id)) {
                            e.shelf = s;
                            break;
                        }
                    }
                    pendingShelfFor = null;
                    saveBooks();
                    rebuild();
                    invalidate();
                } else {
                    shelfFilter = s;
                    page = 0;
                    rebuild();
                    invalidate();
                }
            }
        });
    }

    private void showTextInput(String title, String hint, final TextReady ready) {
        try {
            Context ctx = getContext();
            final EditText input = new EditText(ctx);
            input.setSingleLine(true);
            input.setHint(hint);
            input.setTextSize(15);
            AlertDialog dlg = new AlertDialog.Builder(ctx,
                    android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            ready.onText(input.getText().toString().trim());
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            dlg.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dlg.show();
        } catch (Exception ignored) { }
    }

    private void showSearchDialog() {
        try {
            Context ctx = getContext();
            final EditText input = new EditText(ctx);
            input.setSingleLine(true);
            input.setHint("Search title or author");
            input.setTextSize(15);
            if (query.length() > 0) input.setText(query);
            AlertDialog dlg = new AlertDialog.Builder(ctx,
                    android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Search books")
                    .setView(input)
                    .setPositiveButton("Apply", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            setQuery(input.getText().toString().trim());
                        }
                    })
                    .setNeutralButton("Clear", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            setQuery("");
                        }
                    })
                    .setNegativeButton("Close", null)
                    .create();
            dlg.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            // instant filtering while typing
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
                public void onTextChanged(CharSequence s, int a, int b, int c2) { }

                public void afterTextChanged(android.text.Editable s) {
                    String q = s.toString().trim();
                    if (!q.equals(query)) {
                        query = q;
                        page = 0;
                        computeGeometry();
                        rebuild();
                        invalidate();
                    }
                }
            });
            dlg.show();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ sidebar icons

    private void iconBook(Canvas cv, float cx, float cy, float s) {
        float w = s * 0.74f;
        float h = s;
        float x0 = cx - w / 2f;
        float y0 = cy - h / 2f;
        cv.drawRect(new RectF(x0, y0, x0 + w, y0 + h), pIcon);
        pLine.setStyle(Paint.Style.FILL);
        pFill.setColor(pIcon.getColor());
        cv.drawRect(new RectF(x0 + w * 0.28f, y0 + h * 0.18f, x0 + w * 0.74f, y0 + h * 0.26f), pFill);
        cv.drawRect(new RectF(x0 + w * 0.28f, y0 + h * 0.38f, x0 + w * 0.74f, y0 + h * 0.46f), pFill);
        cv.drawRect(new RectF(x0 + w * 0.28f, y0 + h * 0.58f, x0 + w * 0.60f, y0 + h * 0.66f), pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    private void iconStore(Canvas cv, float cx, float cy, float s) {
        float w = s * 0.86f;
        float x0 = cx - w / 2f;
        float y0 = cy - s * 0.42f;
        // awning zigzag
        Path aw = new Path();
        aw.moveTo(x0, y0 + s * 0.18f);
        aw.lineTo(x0 + w, y0 + s * 0.18f);
        aw.lineTo(x0 + w, y0 + s * 0.30f);
        aw.lineTo(x0 + w * 0.75f, y0 + s * 0.44f);
        aw.lineTo(x0 + w * 0.5f, y0 + s * 0.30f);
        aw.lineTo(x0 + w * 0.25f, y0 + s * 0.44f);
        aw.lineTo(x0, y0 + s * 0.30f);
        aw.close();
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        cv.drawPath(aw, pFill);
        pLine.setStyle(Paint.Style.STROKE);
        // roof line
        cv.drawLine(x0, y0 + s * 0.18f, x0 + w * 0.12f, y0 - s * 0.06f, pIcon);
        cv.drawLine(x0 + w * 0.12f, y0 - s * 0.06f, x0 + w * 0.88f, y0 - s * 0.06f, pIcon);
        cv.drawLine(x0 + w * 0.88f, y0 - s * 0.06f, x0 + w, y0 + s * 0.18f, pIcon);
        // base
        cv.drawRect(new RectF(x0 + w * 0.10f, y0 + s * 0.52f, x0 + w * 0.90f, y0 + s * 1.0f), pIcon);
        cv.drawLine(x0 + w * 0.32f, y0 + s * 0.66f, x0 + w * 0.32f, y0 + s * 1.0f, pIcon);
        cv.drawLine(x0 + w * 0.32f, y0 + s * 0.66f, x0 + w * 0.72f, y0 + s * 0.66f, pIcon);
    }

    private void iconNotes(Canvas cv, float cx, float cy, float s) {
        float w = s * 0.72f;
        float h = s * 0.92f;
        float x0 = cx - w / 2f;
        float y0 = cy - h / 2f;
        cv.drawRect(new RectF(x0, y0, x0 + w, y0 + h), pIcon);
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        cv.drawRect(new RectF(x0 + w * 0.18f, y0 + h * 0.18f, x0 + w * 0.82f, y0 + h * 0.25f), pFill);
        cv.drawRect(new RectF(x0 + w * 0.18f, y0 + h * 0.38f, x0 + w * 0.82f, y0 + h * 0.45f), pFill);
        cv.drawRect(new RectF(x0 + w * 0.18f, y0 + h * 0.58f, x0 + w * 0.60f, y0 + h * 0.65f), pFill);
        // clip on top
        cv.drawRect(new RectF(cx - w * 0.14f, y0 - s * 0.08f, cx + w * 0.14f, y0 + s * 0.02f), pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    private void iconStorage(Canvas cv, float cx, float cy, float s) {
        float w = s * 0.82f;
        float x0 = cx - w / 2f;
        float y0 = cy - s * 0.44f;
        // lid
        cv.drawRect(new RectF(x0 - dp(2), y0, x0 + w + dp(2), y0 + s * 0.16f), pIcon);
        // body
        cv.drawRect(new RectF(x0, y0 + s * 0.16f, x0 + w, y0 + s * 1.0f), pIcon);
        // slot
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        cv.drawRect(new RectF(x0 + w * 0.32f, y0 + s * 0.34f, x0 + w * 0.68f, y0 + s * 0.42f), pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    private void iconApps(Canvas cv, float cx, float cy, float s) {
        float r = s * 0.52f;
        Path hex = new Path();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 6 + k * Math.PI / 3;
            float px = cx + (float) Math.cos(ang) * r;
            float py = cy + (float) Math.sin(ang) * r;
            if (k == 0) hex.moveTo(px, py);
            else hex.lineTo(px, py);
        }
        hex.close();
        cv.drawPath(hex, pIcon);
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cy, dpf * 1.6f, pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    private void iconGear(Canvas cv, float cx, float cy, float s) {
        float r = s * 0.34f;
        cv.drawCircle(cx, cy, r, pIcon);
        pIcon.setStrokeWidth(dpf * 1.8f);
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            float x1 = cx + (float) Math.cos(ang) * r;
            float y1 = cy + (float) Math.sin(ang) * r;
            float x2 = cx + (float) Math.cos(ang) * (r + s * 0.14f);
            float y2 = cy + (float) Math.sin(ang) * (r + s * 0.14f);
            cv.drawLine(x1, y1, x2, y2, pIcon);
        }
        pIcon.setStrokeWidth(dpf * 1.5f);
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cy, dpf * 1.5f, pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    // ---------------------------------------------------------- toolbar icons

    private void iconClock(Canvas cv, float cx, float cy, float s) {
        float r = s * 0.5f;
        cv.drawCircle(cx, cy, r, pIcon);
        pIcon.setStrokeWidth(dpf * 1.4f);
        cv.drawLine(cx, cy, cx, cy - r * 0.62f, pIcon);
        cv.drawLine(cx, cy, cx + r * 0.5f, cy + r * 0.18f, pIcon);
    }

    private void iconColumns(Canvas cv, float cx, float cy, float s) {
        float h = s * 0.9f;
        pIcon.setStrokeWidth(dpf * 2.2f);
        cv.drawLine(cx - s * 0.34f, cy - h / 2f, cx - s * 0.34f, cy + h / 2f, pIcon);
        cv.drawLine(cx, cy - h / 2f, cx, cy + h * 0.10f, pIcon);
        cv.drawLine(cx + s * 0.34f, cy - h / 2f, cx + s * 0.34f, cy + h / 2f, pIcon);
    }

    private void iconCloud(Canvas cv, float cx, float cy, float s) {
        pIcon.setStrokeWidth(dpf * 1.5f);
        RectF base = new RectF(cx - s * 0.48f, cy, cx + s * 0.48f, cy + s * 0.30f);
        cv.drawRoundRect(base, s * 0.15f, s * 0.15f, pIcon);
        cv.drawArc(new RectF(cx - s * 0.48f, cy - s * 0.22f, cx - s * 0.02f, cy + s * 0.24f),
                95f, 175f, false, pIcon);
        cv.drawArc(new RectF(cx - s * 0.18f, cy - s * 0.52f, cx + s * 0.40f, cy + s * 0.10f),
                140f, 200f, false, pIcon);
    }

    private void iconSearch(Canvas cv, float cx, float cy, float s) {
        float r = s * 0.36f;
        cv.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r, pIcon);
        cv.drawLine(cx + r * 0.35f, cy + r * 0.35f, cx + s * 0.52f, cy + s * 0.52f, pIcon);
    }

    private void iconSort(Canvas cv, float cx, float cy, float s) {
        pIcon.setStrokeWidth(dpf * 1.8f);
        cv.drawLine(cx - s * 0.42f, cy - s * 0.30f, cx + s * 0.30f, cy - s * 0.30f, pIcon);
        cv.drawLine(cx - s * 0.42f, cy, cx + s * 0.10f, cy, pIcon);
        cv.drawLine(cx - s * 0.42f, cy + s * 0.30f, cx + s * 0.30f, cy + s * 0.30f, pIcon);
        pFill.setColor(pIcon.getColor());
        pLine.setStyle(Paint.Style.FILL);
        Path a = new Path();
        a.moveTo(cx + s * 0.30f, cy + s * 0.08f);
        a.lineTo(cx + s * 0.48f, cy + s * 0.30f);
        a.lineTo(cx + s * 0.30f, cy + s * 0.30f);
        a.close();
        cv.drawPath(a, pFill);
        pLine.setStyle(Paint.Style.STROKE);
    }

    private void iconView(Canvas cv, float cx, float cy, float s) {
        float r = s * 0.48f;
        cv.drawCircle(cx, cy, r, pIcon);
        pIcon.setStrokeWidth(dpf * 1.6f);
        cv.drawLine(cx - r * 0.55f, cy - r * 0.38f, cx + r * 0.55f, cy - r * 0.38f, pIcon);
        cv.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, pIcon);
        cv.drawLine(cx - r * 0.55f, cy + r * 0.38f, cx + r * 0.55f, cy + r * 0.38f, pIcon);
    }

    // ---------------------------------------------------------------- touch

    private final Runnable longPressTask = new Runnable() {
        public void run() {
            if (longFired || active != null) return;
            if (section() != SEC_LIBRARY) return;
            int idx = hitAction(coverHits, downX, downY);
            if (idx >= 0 && idx < filtered.size()) {
                longFired = true;
                actionTarget = filtered.get(idx);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                show(new BookActionsOverlay());
            }
        }
    };

    private int hitIndex(ArrayList<float[]> hits, float x, float y) {
        for (int i = 0; i < hits.size(); i++) {
            float[] h = hits.get(i);
            if (x >= h[0] && x <= h[2] && y >= h[1] && y <= h[3]) return i;
        }
        return -1;
    }

    private int hitAction(ArrayList<float[]> hits, float x, float y) {
        int i = hitIndex(hits, x, y);
        return i >= 0 ? (int) hits.get(i)[4] : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        int action = e.getActionMasked();

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
                if (!moved) handleTap(x, y);
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float x, float y) {
        int sec = hitAction(sideHits, x, y);
        if (sec >= 0) {
            if (sec != section()) {
                currentSection = sec;
                if (sec == SEC_STORAGE) refresh();
                else invalidate();
            }
            return;
        }

        if (section() != SEC_LIBRARY) {
            // section content taps (settings rows / app tiles)
            int sa = hitAction(bottomHits, x, y);
            handleSectionAction(sa);
            return;
        }

        int tool = hitAction(toolHits, x, y);
        if (tool >= 0) {
            handleToolTap(tool);
            return;
        }

        int bot = hitAction(bottomHits, x, y);
        if (bot == 60) { // clear search chip
            setQuery("");
            computeGeometry();
            return;
        } else if (bot == 61 && page > 0) {
            page--;
            invalidate();
            return;
        } else if (bot == 62 && page < pageCount - 1) {
            page++;
            invalidate();
            return;
        } else if (bot == 63) { // refresh
            blinkFrames = 1;
            refresh();
            return;
        } else if (bot >= 70) {
            handleSectionAction(bot);
            return;
        }

        int idx = hitAction(coverHits, x, y);
        if (idx >= 0 && idx < filtered.size()) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            host.onOpenBook(filtered.get(idx));
        }
    }

    private void handleToolTap(int tool) {
        switch (tool) {
            case 0: // recently opened
                sortMode = 3;
                page = 0;
                rebuild();
                invalidate();
                break;
            case 1: // bookshelves
                show(new ShelfOverlay());
                break;
            case 2: // cloud sync
                show(new InfoOverlay("Cloud sync",
                        "Sign-in is not available on this build. All books are stored locally on this device."));
                break;
            case 10: // search
                showSearchDialog();
                break;
            case 11: // sort
                show(new SortOverlay());
                break;
            case 12: // view settings: cycle columns
                cols = (cols == 4) ? 3 : (cols == 3 ? 5 : 4);
                page = 0;
                computeGeometry();
                rebuild();
                invalidate();
                break;
            default:
                break;
        }
    }

    private void handleSectionAction(int a) {
        if (a < 0) return;
        if (a == 70) {
            settings.flash = !settings.flash;
            prefs.saveSettings(settings);
            invalidate();
        } else if (a == 71) {
            settings.haptic = !settings.haptic;
            prefs.saveSettings(settings);
            invalidate();
        } else if (a >= 72 && a <= 74) {
            settings.theme = a - 72;
            prefs.saveSettings(settings);
            invalidate();
        } else if (a == 80) {
            currentSection = SEC_LIBRARY;
            invalidate();
        } else if (a == 81) {
            host.onImport();
        } else if (a == 82) {
            blinkFrames = 1;
            refresh();
        }
    }
}

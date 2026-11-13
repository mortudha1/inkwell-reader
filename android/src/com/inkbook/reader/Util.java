package com.inkbook.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.BatteryManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** Small shared helpers: densities, noise texture, battery, clock, html decoding. */
public final class Util {

    private Util() { }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static float sp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().scaledDensity;
    }

    private static Bitmap NOISE;
    private static Paint NOISE_PAINT;

    /** Subtle paper-grain tile to simulate e-ink gray. */
    public static Paint noisePaint() {
        if (NOISE_PAINT == null) {
            final int S = 64;
            Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            Random r = new Random(20240101);
            Paint p = new Paint();
            for (int y = 0; y < S; y += 2) {
                for (int x = 0; x < S; x += 2) {
                    int a = r.nextInt(14);
                    if (a > 0) {
                        p.setColor(a < 7 ? 0x08000000 : 0x08FFFFFF);
                        cv.drawRect(x, y, x + 2, y + 2, p);
                    }
                }
            }
            NOISE = b;
            NOISE_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
            NOISE_PAINT.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
        return NOISE_PAINT;
    }

    public static int batteryPct(Context c) {
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return 100;
            int v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (v == Integer.MIN_VALUE || v < 0) return 100;
            return v;
        } catch (Exception e) {
            return 100;
        }
    }

    public static String clock() {
        try {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        } catch (Exception e) {
            return "--:--";
        }
    }

    /** Decode HTML entities without android.text (works everywhere). */
    public static String htmlToText(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '&') {
                int sc = s.indexOf(';', i);
                if (sc > i && sc - i < 12) {
                    String e = s.substring(i + 1, sc);
                    boolean done = true;
                    if (e.equals("amp")) sb.append('&');
                    else if (e.equals("lt")) sb.append('<');
                    else if (e.equals("gt")) sb.append('>');
                    else if (e.equals("quot")) sb.append('"');
                    else if (e.equals("apos")) sb.append('\'');
                    else if (e.equals("nbsp")) sb.append(' ');
                    else if (e.equals("mdash")) sb.append('-');
                    else if (e.equals("ndash")) sb.append('-');
                    else if (e.startsWith("#x") || e.startsWith("#X")) {
                        try { sb.append((char) Integer.parseInt(e.substring(2), 16)); }
                        catch (NumberFormatException nfe) { done = false; }
                    } else if (e.startsWith("#")) {
                        try { sb.append((char) Integer.parseInt(e.substring(1))); }
                        catch (NumberFormatException nfe) { done = false; }
                    } else {
                        done = false;
                    }
                    if (done) { i = sc + 1; continue; }
                }
                sb.append(ch);
                i++;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString();
    }
}

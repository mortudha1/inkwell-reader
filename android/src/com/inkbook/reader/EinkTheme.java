package com.inkbook.reader;

/**
 * Flat monochrome e-ink palettes. Warm paper tones, charcoal ink, hairline
 * rules - no color, no gradients, no shadows. Index 0 warm paper, 1 white,
 * 2 dark.
 */
public final class EinkTheme {

    public final int bg;        // screen background (paper)
    public final int text;      // body text (charcoal ink)
    public final int textSoft;  // secondary text
    public final int strip;     // legacy bar strip (unused by the reader)
    public final int tabFill;   // legacy tab fill
    public final int tabText;   // legacy tab text
    public final int line;      // hairline separators
    public final int btnFill;   // button surface
    public final int btnBorder; // button border
    public final int menuBg;    // dialog / panel surface
    public final int menuBorder;
    public final int menuText;
    public final int flash;     // full-refresh blink frame (dark)
    public final int ghost;     // faint settling wash right after the blink
    public final int selText;   // text over an inverted selection
    public final int selBand;   // highlight band behind paragraph text
    public final int scrim;     // dim wash behind overlays

    private EinkTheme(int bg, int text, int textSoft, int strip, int tabFill, int tabText,
                      int line, int btnFill, int btnBorder, int menuBg, int menuBorder,
                      int menuText, int flash, int ghost, int selText, int selBand, int scrim) {
        this.bg = bg;
        this.text = text;
        this.textSoft = textSoft;
        this.strip = strip;
        this.tabFill = tabFill;
        this.tabText = tabText;
        this.line = line;
        this.btnFill = btnFill;
        this.btnBorder = btnBorder;
        this.menuBg = menuBg;
        this.menuBorder = menuBorder;
        this.menuText = menuText;
        this.flash = flash;
        this.ghost = ghost;
        this.selText = selText;
        this.selBand = selBand;
        this.scrim = scrim;
    }

    public static EinkTheme theme(int idx) {
        switch (((idx % 3) + 3) % 3) {
            case 1: // White paper
                return new EinkTheme(
                        0xFFF7F5F0, 0xFF1C1C1B, 0xFF6F6D67, 0xFFEDEBE5, 0xFFE3E1DA, 0xFF1C1C1B,
                        0xFFD6D3CB, 0xFFFDFCF9, 0xFFC2BFB6, 0xFFFBFAF6, 0xFFB3B0A7, 0xFF1C1C1B,
                        0xFF141413, 0x14000000, 0xFFF7F5F0, 0xFFEAE7DE, 0x30000000);
            case 2: // Dark
                return new EinkTheme(
                        0xFF151514, 0xFFBAB8B1, 0xFF7C7A74, 0xFF1B1B1A, 0xFF232322, 0xFFC7C5BE,
                        0xFF2B2A27, 0xFF1D1D1B, 0xFF43423E, 0xFF1F1F1D, 0xFF4A4944, 0xFFC7C5BE,
                        0xFF0A0A09, 0x18FFFFFF, 0xFF151514, 0xFF2C2B27, 0x46000000);
            default: // Warm paper (default - like a printed novel)
                return new EinkTheme(
                        0xFFE9E8E2, 0xFF222222, 0xFF6E6B63, 0xFFDFDDD6, 0xFFD4D2CA, 0xFF222222,
                        0xFFCFCDC5, 0xFFF2F1EB, 0xFFB7B4AB, 0xFFF4F3EE, 0xFFA5A299, 0xFF222222,
                        0xFF1A1A19, 0x16000000, 0xFFE9E8E2, 0xFFDBD9D0, 0x2E000000);
        }
    }
}

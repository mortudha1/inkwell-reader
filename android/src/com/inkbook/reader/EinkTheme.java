package com.inkbook.reader;

/** Flat e-ink palettes. No color, no gradients - just paper grays. */
public final class EinkTheme {

    public final int bg;        // screen background
    public final int text;      // body text
    public final int textSoft;  // secondary text
    public final int strip;     // status bar strip
    public final int tabFill;   // status bar title tab
    public final int tabText;   // status bar title text
    public final int line;      // hairline separators
    public final int btnFill;   // button surface
    public final int btnBorder; // button border
    public final int menuBg;    // dialog surface
    public final int menuBorder;
    public final int menuText;
    public final int flash;     // e-ink refresh blink (dark blink on turn)
    public final int ghost;     // faint "ghosting" residue right after the blink
    public final int selText;   // text over selection

    private EinkTheme(int bg, int text, int textSoft, int strip, int tabFill, int tabText,
                      int line, int btnFill, int btnBorder, int menuBg, int menuBorder,
                      int menuText, int flash, int ghost, int selText) {
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
    }

    public static EinkTheme theme(int idx) {
        switch (idx % 3) {
            case 1: // White paper
                return new EinkTheme(
                        0xFFF3F0E9, 0xFF191917, 0xFF6B6B66, 0xFFE3E0D8, 0xFFD9D5CB, 0xFF191917,
                        0xFFBCB8AE, 0xFFEAE7DF, 0xFF8F8B81, 0xFFF6F4EE, 0xFF2A2A27, 0xFF191917,
                        0xFF1B1B1A, 0x16000000, 0xFFF3F0E9);
            case 2: // Night
                return new EinkTheme(
                        0xFF151514, 0xFFA9A9A6, 0xFF666663, 0xFF1D1D1C, 0xFF292928, 0xFFC2C2C0,
                        0xFF30302F, 0xFF1E1E1D, 0xFF555553, 0xFF202020, 0xFF6A6A68, 0xFFADADAB,
                        0xFF2C2C2A, 0x24FFFFFF, 0xFF151514);
            default: // Classic gray (like the photo)
                return new EinkTheme(
                        0xFFD6D6D3, 0xFF1E1E1C, 0xFF63635F, 0xFFC4C4C1, 0xFFB6B6B3, 0xFF161616,
                        0xFFA5A5A2, 0xFFC8C8C5, 0xFF7F7F7B, 0xFFE3E3E0, 0xFF343431, 0xFF1C1C1C,
                        0xFF191917, 0x1C000000, 0xFFD6D6D3);
        }
    }
}

package edu.keiser.deadlock.ui;

import java.awt.*;

public final class Theme {

    private Theme() {
    }

    public static final Color THINKING = new Color(0x888780);
    public static final Color WORKING = new Color(0x639922);
    public static final Color WAITING = new Color(0xEF9F27);
    public static final Color STUCK = new Color(0xE24B4A);
    public static final Color BACKGROUND = Color.WHITE;
    public static final Color CARD = new Color(0xF7F6F2);
    public static final Color CARD_BORDER = new Color(0xE4E2DA);
    public static final Color TABLE = new Color(0xF1EFE8);
    public static final Color TABLE_EDGE = new Color(0xD3D1C7);
    public static final Color FORK = new Color(0x444441);
    public static final Color TEXT = new Color(0x2C2C2A);
    public static final Color TEXT_MUTED = new Color(0x6B6A65);
    public static final Color DANGER_BG = new Color(0xFCEBEB);
    public static final Color DANGER_TEXT = new Color(0xA32D2D);
    public static final Color SUCCESS_TEXT = new Color(0x3B6D11);
    public static final Color WARNING_TEXT = new Color(0x854F0B);

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Font font(int style, float size) {
        Font base = javax.swing.UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(style, size);
    }
}
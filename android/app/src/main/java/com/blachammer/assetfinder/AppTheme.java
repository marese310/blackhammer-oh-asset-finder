package com.blachammer.assetfinder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class AppTheme {
    static final int DEFAULT_BACKGROUND = Color.rgb(17, 16, 26);
    static final int DEFAULT_FOREGROUND = Color.rgb(245, 242, 255);
    static final int DEFAULT_ACCENT = Color.rgb(180, 110, 255);
    static final int DEFAULT_FONT_SIZE = 10;
    static final int MIN_FONT_SIZE = 8;
    static final int MAX_FONT_SIZE = 18;

    private final SharedPreferences preferences;

    int background;
    int foreground;
    int accent;
    int fontSize;

    AppTheme(Context context) {
        preferences = context.getSharedPreferences("theme", Context.MODE_PRIVATE);
        background = preferences.getInt("background", DEFAULT_BACKGROUND);
        foreground = preferences.getInt("foreground", DEFAULT_FOREGROUND);
        accent = preferences.getInt("accent", DEFAULT_ACCENT);
        fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE,
                preferences.getInt("fontSize", DEFAULT_FONT_SIZE)));
    }

    void save() {
        preferences.edit()
                .putInt("background", background)
                .putInt("foreground", foreground)
                .putInt("accent", accent)
                .putInt("fontSize", fontSize)
                .apply();
    }

    void reset() {
        background = DEFAULT_BACKGROUND;
        foreground = DEFAULT_FOREGROUND;
        accent = DEFAULT_ACCENT;
        fontSize = DEFAULT_FONT_SIZE;
    }

    float fontScale() {
        return fontSize / (float) DEFAULT_FONT_SIZE;
    }

    int surface() {
        return blend(background, foreground, 0.10f);
    }

    int divider() {
        return blend(background, foreground, 0.18f);
    }

    int muted() {
        return blend(foreground, background, 0.30f);
    }

    static int blend(int from, int to, float amount) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount)
        );
    }

    static Integer parseColor(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("#?[0-9A-Fa-f]{6}")) {
            return null;
        }
        return Color.parseColor(trimmed.startsWith("#") ? trimmed : "#" + trimmed);
    }

    static String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }
}

package com.blachammer.assetfinder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {
    final Context context;
    final AppTheme theme;

    Ui(Context context, AppTheme theme) {
        this.context = context;
        this.theme = theme;
    }

    int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    GradientDrawable box(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp * theme.fontScale());
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    TextView button(String label, Runnable action) {
        TextView view = text(label, 13, theme.accent, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setBackground(box(theme.surface(), 12));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    EditText input(String hint, String value) {
        EditText edit = new EditText(context);
        edit.setTextColor(theme.foreground);
        edit.setHintTextColor(theme.muted());
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(15 * theme.fontScale());
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        edit.setBackground(box(theme.surface(), 12));
        return edit;
    }

    CheckBox checkBox(String label, boolean checked) {
        CheckBox box = new CheckBox(context);
        box.setText(label);
        box.setTextSize(13 * theme.fontScale());
        box.setTextColor(theme.muted());
        box.setChecked(checked);
        box.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{theme.accent, theme.muted()}
        ));
        return box;
    }

    TextView label(String value) {
        TextView view = text(value, 12, theme.muted(), false);
        view.setPadding(dp(2), dp(10), 0, dp(3));
        return view;
    }

    LinearLayout.LayoutParams match(int heightDp) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightDp > 0 ? dp(heightDp)
                : ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    LinearLayout.LayoutParams weightedMargin(int startDp) {
        LinearLayout.LayoutParams params = weighted();
        params.setMarginStart(dp(startDp));
        return params;
    }

    LinearLayout form() {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(4));
        return form;
    }

    ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    AlertDialog.Builder dialog(String title, View content) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(scroll(content));
        return builder;
    }

    Dialog style(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(box(theme.background, 18));
        }
        return dialog;
    }

    AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        style(dialog);
        dialog.show();
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );
        for (int which : new int[]{
                AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
            TextView button = dialog.getButton(which);
            if (button != null) {
                button.setTextColor(theme.accent);
            }
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        View title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title instanceof TextView) {
            ((TextView) title).setTextColor(theme.foreground);
        }
        return dialog;
    }

    void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    void copy(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        toast(label + " copied");
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}

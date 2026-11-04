package com.blachammer.assetfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    interface ResultCallback {
        void onResult(int resultCode, Intent data);
    }

    private static final String DISCORD_INVITE = "https://discord.gg/qeGAX6VCUz";
    private static final int TAB_ASSETS = 0;
    private static final int TAB_MAPS = 1;

    final ExecutorService executor = Executors.newSingleThreadExecutor();
    AssetDatabase database;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<ResultCallback> pendingResults = new SparseArray<>();
    private int nextRequestCode = 1000;

    private AppTheme theme;
    private Ui ui;
    private AssetsScreen assetsScreen;
    private MapsScreen mapsScreen;
    private TextView assetsTab;
    private TextView mapsTab;
    private int currentTab = TAB_ASSETS;
    private boolean databaseReady;
    private String importStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = new AppTheme(this);
        database = new AssetDatabase(this, importedRows ->
                mainHandler.post(() -> {
                    importStatus = "Loading offline assets... " + importedRows;
                    if (assetsScreen != null && !databaseReady) {
                        assetsScreen.setStatus(importStatus);
                    }
                })
        );
        buildUi();

        executor.execute(() -> {
            try {
                database.getWritableDatabase();
                runOnUiThread(() -> {
                    databaseReady = true;
                    assetsScreen.onDatabaseReady();
                    mapsScreen.onDatabaseReady();
                });
            } catch (Exception error) {
                runOnUiThread(() -> showDatabaseError(error));
            }
        });
    }

    private void buildUi() {
        ui = new Ui(this, theme);
        int background = theme.background;
        android.view.Window window = getWindow();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        boolean lightBackground = Color.luminance(background) > 0.5f;
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(lightBackground ? (flags | lightFlags) : (flags & ~lightFlags));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        root.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(8));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.ime()
                );
                view.setPadding(
                        ui.dp(16) + bars.left,
                        ui.dp(12) + bars.top,
                        ui.dp(16) + bars.right,
                        ui.dp(8) + bars.bottom
                );
                return insets;
            });
        }

        buildHeader(root);
        buildTabs(root);

        assetsScreen = new AssetsScreen(this, ui);
        mapsScreen = new MapsScreen(this, ui);
        FrameLayout content = new FrameLayout(this);
        content.addView(assetsScreen.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        content.addView(mapsScreen.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
        ));

        selectTab(currentTab);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.requestApplyInsets();
        }
        if (!databaseReady && importStatus != null) {
            assetsScreen.setStatus(importStatus);
        }
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blachammer_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        LinearLayout branding = new LinearLayout(this);
        branding.setOrientation(LinearLayout.VERTICAL);
        branding.setGravity(Gravity.CENTER_VERTICAL);
        branding.setPadding(ui.dp(10), 0, ui.dp(4), 0);
        branding.addView(ui.text("BLACHAMMER", 17, theme.accent, true));
        branding.addView(ui.text("ONCE HUMAN ASSET FINDER", 11, theme.foreground, true));
        header.addView(branding, ui.weighted());

        TextView appearance = ui.button("Theme", this::showAppearance);
        appearance.setContentDescription("Change app appearance");
        header.addView(appearance);

        TextView discord = ui.button("Discord", this::openDiscord);
        discord.setContentDescription("Join the BlacHammer Discord community");
        LinearLayout.LayoutParams discordParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        discordParams.setMarginStart(ui.dp(6));
        header.addView(discord, discordParams);

        root.addView(header, ui.match(0));
    }

    private void buildTabs(LinearLayout root) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, ui.dp(10), 0, ui.dp(8));
        assetsTab = ui.button("Asset Search", () -> selectTab(TAB_ASSETS));
        mapsTab = ui.button("Map and Markers", () -> selectTab(TAB_MAPS));
        tabs.addView(assetsTab, ui.weighted());
        tabs.addView(mapsTab, ui.weightedMargin(8));
        root.addView(tabs, ui.match(0));
    }

    private void selectTab(int tab) {
        currentTab = tab;
        assetsScreen.view().setVisibility(tab == TAB_ASSETS ? View.VISIBLE : View.GONE);
        mapsScreen.view().setVisibility(tab == TAB_MAPS ? View.VISIBLE : View.GONE);
        styleTab(assetsTab, tab == TAB_ASSETS);
        styleTab(mapsTab, tab == TAB_MAPS);
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackground(ui.box(active ? theme.accent : theme.surface(), 12));
        tab.setTextColor(active ? theme.background : theme.muted());
    }

    private void showAppearance() {
        LinearLayout form = ui.form();
        EditText backgroundInput = ui.input("#RRGGBB", AppTheme.toHex(theme.background));
        EditText foregroundInput = ui.input("#RRGGBB", AppTheme.toHex(theme.foreground));
        EditText accentInput = ui.input("#RRGGBB", AppTheme.toHex(theme.accent));
        TextView sizeLabel = ui.label("Font size: " + theme.fontSize);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(AppTheme.MAX_FONT_SIZE - AppTheme.MIN_FONT_SIZE);
        sizeBar.setProgress(theme.fontSize - AppTheme.MIN_FONT_SIZE);
        sizeBar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        sizeBar.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sizeLabel.setText("Font size: " + (progress + AppTheme.MIN_FONT_SIZE));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        TextView error = ui.text("", 13, Color.rgb(255, 120, 120), false);

        form.addView(ui.label("Background"));
        form.addView(backgroundInput);
        form.addView(swatches(backgroundInput, "#11101A", "#1C1E22", "#000000", "#0B1B2B", "#FFFFFF"));
        form.addView(ui.label("Text"));
        form.addView(foregroundInput);
        form.addView(swatches(foregroundInput, "#F5F2FF", "#FFFFFF", "#E0E0E0", "#1C1E22", "#000000"));
        form.addView(ui.label("Accent"));
        form.addView(accentInput);
        form.addView(swatches(accentInput, "#B46EFF", "#50C8FF", "#FF6E6E", "#6EFF9E", "#FFC24D"));
        form.addView(sizeLabel);
        form.addView(sizeBar);
        form.addView(ui.button("Reset to defaults", () -> {
            backgroundInput.setText(AppTheme.toHex(AppTheme.DEFAULT_BACKGROUND));
            foregroundInput.setText(AppTheme.toHex(AppTheme.DEFAULT_FOREGROUND));
            accentInput.setText(AppTheme.toHex(AppTheme.DEFAULT_ACCENT));
            sizeBar.setProgress(AppTheme.DEFAULT_FONT_SIZE - AppTheme.MIN_FONT_SIZE);
        }));
        form.addView(error);

        AlertDialog dialog = ui.show(ui.dialog("Appearance", form)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Integer background = AppTheme.parseColor(backgroundInput.getText().toString());
            Integer foreground = AppTheme.parseColor(foregroundInput.getText().toString());
            Integer accent = AppTheme.parseColor(accentInput.getText().toString());
            if (background == null || foreground == null || accent == null) {
                error.setText("Enter colors as #RRGGBB.");
                return;
            }
            theme.background = background;
            theme.foreground = foreground;
            theme.accent = accent;
            theme.fontSize = sizeBar.getProgress() + AppTheme.MIN_FONT_SIZE;
            theme.save();
            dialog.dismiss();
            rebuildUi();
        });
    }

    private View swatches(EditText target, String... colors) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, ui.dp(6), 0, ui.dp(2));
        for (String hex : colors) {
            View swatch = new View(this);
            android.graphics.drawable.GradientDrawable shape =
                    new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            shape.setColor(Color.parseColor(hex));
            shape.setStroke(ui.dp(2), Color.GRAY);
            swatch.setBackground(shape);
            swatch.setOnClickListener(v -> target.setText(hex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32));
            params.setMarginEnd(ui.dp(10));
            row.addView(swatch, params);
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private void rebuildUi() {
        assetsScreen.destroy();
        mapsScreen.destroy();
        buildUi();
        if (databaseReady) {
            assetsScreen.onDatabaseReady();
            mapsScreen.onDatabaseReady();
        }
    }

    void launchForResult(Intent intent, ResultCallback callback) {
        int code = nextRequestCode++;
        pendingResults.put(code, callback);
        try {
            startActivityForResult(intent, code);
        } catch (Exception error) {
            pendingResults.remove(code);
            ui.toast("No app is available to handle this action.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ResultCallback callback = pendingResults.get(requestCode);
        if (callback != null) {
            pendingResults.remove(requestCode);
            callback.onResult(resultCode, data);
        }
    }

    private void openDiscord() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE)));
        } catch (Exception error) {
            ui.toast("Could not open the Discord invite.");
        }
    }

    void showDatabaseError(Exception error) {
        assetsScreen.setStatus("Could not load assets.");
        new AlertDialog.Builder(this)
                .setTitle("Asset database error")
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("Close", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (currentTab == TAB_MAPS && mapsScreen.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        assetsScreen.destroy();
        mapsScreen.destroy();
        executor.shutdownNow();
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }
}

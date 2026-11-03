package com.blachammer.assetfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(17, 16, 26);
    private static final int SURFACE = Color.rgb(32, 30, 45);
    private static final int ACCENT = Color.rgb(180, 110, 255);
    private static final int TEXT = Color.rgb(245, 242, 255);
    private static final int MUTED = Color.rgb(178, 173, 194);
    private static final String DISCORD_INVITE = "https://discord.gg/qeGAX6VCUz";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AssetRecord> records = new ArrayList<>();
    private final AssetAdapter assetAdapter = new AssetAdapter();
    private final Runnable debouncedSearch = this::refreshSearch;

    private AssetDatabase database;
    private EditText searchInput;
    private Spinner categorySpinner;
    private Spinner subcategorySpinner;
    private CheckBox exactNameCheckBox;
    private TextView statusLabel;
    private TextView emptyLabel;
    private TextView loadMoreButton;
    private ProgressBar progressBar;
    private boolean filtersReady;
    private boolean updatingFilters;
    private boolean searchLoading;
    private boolean hasMoreResults;
    private int queryGeneration;
    private int loadedOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.view.Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                view.setPadding(
                        dp(16) + bars.left,
                        dp(12) + bars.top,
                        dp(16) + bars.right,
                        dp(8) + bars.bottom
                );
                return insets;
            });
        }

        buildHeader(root);
        buildSearchControls(root);
        buildResults(root);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.requestApplyInsets();
        }

        statusLabel.setText("Preparing the offline asset database...");
        progressBar.setVisibility(View.VISIBLE);
        database = new AssetDatabase(this, importedRows ->
                mainHandler.post(() ->
                        statusLabel.setText("Loading offline assets... " + importedRows)
                )
        );
        executor.execute(() -> {
            try {
                database.getReadableDatabase();
                List<String> categories = database.getCategories();
                runOnUiThread(() -> initializeFilters(categories));
            } catch (Exception error) {
                runOnUiThread(() -> showDatabaseError(error));
            }
        });
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.blachammer_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout branding = new LinearLayout(this);
        branding.setGravity(Gravity.CENTER_VERTICAL);
        branding.setOrientation(LinearLayout.VERTICAL);
        branding.setPadding(dp(12), 0, dp(4), 0);

        TextView name = text("BLACHAMMER", 17, ACCENT, true);
        TextView subtitle = text("ONCE HUMAN ASSET FINDER", 11, TEXT, true);
        branding.addView(name);
        branding.addView(subtitle);
        header.addView(branding, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
        ));

        TextView discord = text("Join Discord", 13, Color.rgb(174, 184, 255), true);
        discord.setGravity(Gravity.CENTER);
        discord.setPadding(dp(12), dp(9), dp(12), dp(9));
        discord.setBackground(backgroundDrawable(SURFACE, dp(12)));
        discord.setContentDescription("Join the BlacHammer Discord community");
        discord.setOnClickListener(view -> openDiscord());
        header.addView(discord, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
        ));

        TextView tagline = text(
                "Search the bundled asset database offline",
                13, MUTED, false
        );
        tagline.setPadding(0, dp(5), 0, dp(10));
        root.addView(tagline);
    }

    private void buildSearchControls(LinearLayout root) {
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(TEXT);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("Search assets, names, categories...");
        searchInput.setTextSize(16);
        searchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchInput.setBackground(backgroundDrawable(SURFACE, dp(12)));
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                scheduleSearch();
            }

            @Override
            public void afterTextChanged(android.text.Editable value) {}
        });
        root.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(8), 0, dp(4));
        categorySpinner = createSpinner();
        subcategorySpinner = createSpinner();
        filters.addView(categorySpinner, new LinearLayout.LayoutParams(
                0, dp(50), 1
        ));
        LinearLayout.LayoutParams subcategoryParams = new LinearLayout.LayoutParams(
                0, dp(50), 1
        );
        subcategoryParams.setMarginStart(dp(8));
        filters.addView(subcategorySpinner, subcategoryParams);
        root.addView(filters);

        exactNameCheckBox = new CheckBox(this);
        exactNameCheckBox.setText("Exact internal asset name");
        exactNameCheckBox.setTextColor(MUTED);
        exactNameCheckBox.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{ACCENT, MUTED}
        ));
        exactNameCheckBox.setOnCheckedChangeListener((button, checked) -> scheduleSearch());
        root.addView(exactNameCheckBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel = text("Loading...", 13, MUTED, false);
        statusRow.addView(statusLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
        ));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progressBar.setVisibility(View.GONE);
        statusRow.addView(progressBar, new LinearLayout.LayoutParams(dp(28), dp(28)));
        root.addView(statusRow);
    }

    private void buildResults(LinearLayout root) {
        FrameLayout results = new FrameLayout(this);
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
        );
        resultsParams.topMargin = dp(6);
        root.addView(results, resultsParams);

        ListView list = new ListView(this);
        list.setAdapter(assetAdapter);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(48, 45, 63)));
        list.setDividerHeight(dp(1));
        list.setSelector(android.R.color.transparent);
        list.setOnItemClickListener((parent, view, position, id) ->
                showAssetDetails(records.get(position))
        );
        results.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        emptyLabel = text("No matching assets. Try a different search.", 15, MUTED, false);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setVisibility(View.GONE);
        results.addView(emptyLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        loadMoreButton = text("Load more assets", 14, ACCENT, true);
        loadMoreButton.setGravity(Gravity.CENTER);
        loadMoreButton.setPadding(0, dp(12), 0, dp(12));
        loadMoreButton.setVisibility(View.GONE);
        loadMoreButton.setOnClickListener(view -> loadNextPage());
        root.addView(loadMoreButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(this);
        spinner.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        spinner.setAdapter(new FilterAdapter(Collections.singletonList("Loading...")));
        spinner.setEnabled(false);
        return spinner;
    }

    private void initializeFilters(List<String> categories) {
        updatingFilters = true;
        List<String> categoryOptions = new ArrayList<>();
        categoryOptions.add("All categories");
        categoryOptions.addAll(categories);
        categorySpinner.setAdapter(new FilterAdapter(categoryOptions));

        List<String> subcategoryOptions = new ArrayList<>();
        subcategoryOptions.add("All subcategories");
        subcategorySpinner.setAdapter(new FilterAdapter(subcategoryOptions));
        categorySpinner.setEnabled(true);
        subcategorySpinner.setEnabled(true);
        filtersReady = true;
        updatingFilters = false;

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id
            ) {
                if (!updatingFilters && filtersReady) {
                    updateSubcategories();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        subcategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id
            ) {
                if (!updatingFilters && filtersReady) {
                    scheduleSearch();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        refreshSearch();
    }

    private void updateSubcategories() {
        String category = selectedCategory();
        updatingFilters = true;
        subcategorySpinner.setEnabled(false);
        executor.execute(() -> {
            try {
                List<String> subcategories = database.getSubcategories(category);
                runOnUiThread(() -> {
                    List<String> options = new ArrayList<>();
                    options.add("All subcategories");
                    options.addAll(subcategories);
                    subcategorySpinner.setAdapter(new FilterAdapter(options));
                    subcategorySpinner.setSelection(0);
                    subcategorySpinner.setEnabled(true);
                    updatingFilters = false;
                    refreshSearch();
                });
            } catch (Exception error) {
                runOnUiThread(() -> showDatabaseError(error));
            }
        });
    }

    private String selectedCategory() {
        if (categorySpinner == null || categorySpinner.getSelectedItem() == null) {
            return null;
        }
        String selected = categorySpinner.getSelectedItem().toString();
        return selected.equals("All categories") ? null : selected;
    }

    private String selectedSubcategory() {
        if (subcategorySpinner == null || subcategorySpinner.getSelectedItem() == null) {
            return null;
        }
        String selected = subcategorySpinner.getSelectedItem().toString();
        return selected.equals("All subcategories") ? null : selected;
    }

    private void scheduleSearch() {
        mainHandler.removeCallbacks(debouncedSearch);
        mainHandler.postDelayed(debouncedSearch, 250);
    }

    private void refreshSearch() {
        if (!filtersReady) {
            return;
        }
        queryGeneration++;
        int generation = queryGeneration;
        loadedOffset = 0;
        hasMoreResults = false;
        records.clear();
        assetAdapter.notifyDataSetChanged();
        emptyLabel.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
        runSearchPage(generation, 0, false);
    }

    private void loadNextPage() {
        if (!filtersReady || searchLoading || !hasMoreResults) {
            return;
        }
        runSearchPage(queryGeneration, loadedOffset, true);
    }

    private void runSearchPage(int generation, int offset, boolean append) {
        String query = searchInput.getText().toString();
        String category = selectedCategory();
        String subcategory = selectedSubcategory();
        boolean exact = exactNameCheckBox.isChecked();

        searchLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        loadMoreButton.setVisibility(View.GONE);
        if (!append) {
            statusLabel.setText("Searching...");
        } else {
            statusLabel.setText("Loading more assets...");
        }

        executor.execute(() -> {
            try {
                List<AssetRecord> page = database.search(
                        query, category, subcategory, exact, offset
                );
                runOnUiThread(() -> {
                    if (generation != queryGeneration) {
                        return;
                    }
                    searchLoading = false;
                    progressBar.setVisibility(View.GONE);
                    hasMoreResults = page.size() > AssetDatabase.PAGE_SIZE;
                    if (hasMoreResults) {
                        page.remove(page.size() - 1);
                    }
                    if (!append) {
                        records.clear();
                    }
                    records.addAll(page);
                    loadedOffset = records.size();
                    assetAdapter.notifyDataSetChanged();
                    emptyLabel.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
                    statusLabel.setText(
                            hasMoreResults
                                    ? "Showing " + records.size() + "+ matching assets"
                                    : records.size() + " matching assets"
                    );
                    loadMoreButton.setVisibility(
                            hasMoreResults ? View.VISIBLE : View.GONE
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation == queryGeneration) {
                        searchLoading = false;
                        progressBar.setVisibility(View.GONE);
                        statusLabel.setText("Search failed: " + error.getMessage());
                        loadMoreButton.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void showAssetDetails(AssetRecord record) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(detailLine("Asset name", record.assetName));
        content.addView(detailLine("Display name", record.displayName));
        content.addView(detailLine("Category", record.category));
        content.addView(detailLine("Subcategory", record.subcategory));
        content.addView(detailLine("Source", record.source));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(record.displayName)
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy asset name", null)
                .create();
        content.setOnLongClickListener(view -> {
            copyText("Asset name", record.assetName);
            return true;
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                copyText("Asset name", record.assetName)
        );
    }

    private TextView detailLine(String label, String value) {
        TextView line = text(label + ": " + value, 14, TEXT, false);
        line.setPadding(0, dp(7), 0, dp(7));
        return line;
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private void openDiscord() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE)));
        } catch (Exception error) {
            Toast.makeText(
                    this, "Could not open the Discord invite.", Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showDatabaseError(Exception error) {
        progressBar.setVisibility(View.GONE);
        statusLabel.setText("Could not load assets.");
        new AlertDialog.Builder(this)
                .setTitle("Asset database error")
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("Close", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private GradientDrawable backgroundDrawable(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(debouncedSearch);
        executor.shutdownNow();
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }

    private final class FilterAdapter extends ArrayAdapter<String> {
        FilterAdapter(List<String> options) {
            super(MainActivity.this, android.R.layout.simple_spinner_item, options);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return filterText(getItem(position));
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView item = filterText(getItem(position));
            item.setBackgroundColor(SURFACE);
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            return item;
        }

        private TextView filterText(String value) {
            TextView item = text(value == null ? "" : value, 14, TEXT, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), 0, dp(12), 0);
            return item;
        }
    }

    private final class AssetAdapter extends ArrayAdapter<AssetRecord> {
        AssetAdapter() {
            super(MainActivity.this, android.R.layout.simple_list_item_1, records);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout container;
            TextView displayName;
            TextView assetName;
            TextView category;
            if (convertView == null || !(convertView.getTag() instanceof View[])) {
                container = new LinearLayout(MainActivity.this);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(dp(14), dp(10), dp(14), dp(10));
                container.setBackgroundColor(BACKGROUND);
                displayName = text("", 15, TEXT, true);
                assetName = text("", 13, ACCENT, false);
                category = text("", 12, MUTED, false);
                container.addView(displayName);
                LinearLayout.LayoutParams assetParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                assetParams.topMargin = dp(3);
                container.addView(assetName, assetParams);
                LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                categoryParams.topMargin = dp(3);
                container.addView(category, categoryParams);
                container.setTag(new View[]{displayName, assetName, category});
            } else {
                container = (LinearLayout) convertView;
                View[] children = (View[]) container.getTag();
                displayName = (TextView) children[0];
                assetName = (TextView) children[1];
                category = (TextView) children[2];
            }

            AssetRecord record = records.get(position);
            displayName.setText(record.displayName);
            assetName.setText(record.assetName);
            category.setText(record.category + "  -  " + record.subcategory);
            return container;
        }
    }
}

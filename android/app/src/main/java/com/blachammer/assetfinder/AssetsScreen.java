package com.blachammer.assetfinder;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AssetsScreen {
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_SUBCATEGORIES = "All subcategories";

    private final MainActivity activity;
    private final Ui ui;
    private final AppTheme theme;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AssetRecord> records = new ArrayList<>();
    private final Runnable debouncedSearch = this::refreshSearch;

    private final LinearLayout root;
    private EditText searchInput;
    private Spinner categorySpinner;
    private Spinner subcategorySpinner;
    private CheckBox exactCheck;
    private CheckBox hideVisualCheck;
    private CheckBox hideWorldCheck;
    private CheckBox gameplayCheck;
    private CheckBox baseOnlyCheck;
    private TextView statusLabel;
    private TextView emptyLabel;
    private TextView loadMoreButton;
    private TextView undoButton;
    private TextView filtersToggle;
    private LinearLayout filtersPanel;
    private ProgressBar progressBar;
    private ArrayAdapter<AssetRecord> adapter;

    private boolean filtersReady;
    private boolean updatingFilters;
    private boolean searchLoading;
    private boolean hasMoreResults;
    private int queryGeneration;
    private int totalCount = -1;
    private String appliedCategory;
    private String appliedSubcategory;
    private AssetFilter lastFilter = new AssetFilter();
    private AssetRecord lastDeleted;

    AssetsScreen(MainActivity activity, Ui ui) {
        this.activity = activity;
        this.ui = ui;
        this.theme = ui.theme;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        buildControls();
        buildResults();
    }

    View view() {
        return root;
    }

    private void buildControls() {
        searchInput = ui.input("Search assets, names, categories...", "");
        searchInput.setSingleLine(true);
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
        root.addView(searchInput, ui.match(52));

        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, ui.dp(8), 0, ui.dp(2));
        boolean wide = activity.getResources().getConfiguration().screenWidthDp >= 600;
        filtersToggle = ui.button(wide ? "Filters \u25B4" : "Filters \u25BE", this::toggleFilters);
        statusRow.addView(filtersToggle);
        statusLabel = ui.text("Preparing the offline asset database...", 13, theme.muted(), false);
        statusLabel.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        statusRow.addView(statusLabel, ui.weighted());
        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        progressBar.setVisibility(View.VISIBLE);
        statusRow.addView(progressBar, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        root.addView(statusRow);

        filtersPanel = new LinearLayout(activity);
        filtersPanel.setOrientation(LinearLayout.VERTICAL);
        filtersPanel.setVisibility(wide ? View.VISIBLE : View.GONE);

        LinearLayout spinners = new LinearLayout(activity);
        spinners.setGravity(Gravity.CENTER_VERTICAL);
        spinners.setPadding(0, ui.dp(4), 0, ui.dp(4));
        categorySpinner = createSpinner();
        subcategorySpinner = createSpinner();
        spinners.addView(categorySpinner, new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(0, ui.dp(50), 1);
        subParams.setMarginStart(ui.dp(8));
        spinners.addView(subcategorySpinner, subParams);
        filtersPanel.addView(spinners);

        hideVisualCheck = ui.checkBox("Hide Visual", true);
        hideWorldCheck = ui.checkBox("Hide World", true);
        gameplayCheck = ui.checkBox("Gameplay Only", false);
        exactCheck = ui.checkBox("Exact Asset Name", false);
        baseOnlyCheck = ui.checkBox("Base Assets Only", false);
        for (CheckBox box : new CheckBox[]{
                hideVisualCheck, hideWorldCheck, gameplayCheck, exactCheck, baseOnlyCheck
        }) {
            box.setOnCheckedChangeListener((button, checked) -> scheduleSearch());
        }
        filtersPanel.addView(checkRow(hideVisualCheck, hideWorldCheck));
        filtersPanel.addView(checkRow(gameplayCheck, exactCheck));
        filtersPanel.addView(checkRow(baseOnlyCheck, null));

        LinearLayout actions = new LinearLayout(activity);
        actions.setPadding(0, ui.dp(6), 0, ui.dp(4));
        actions.addView(ui.button("Reset", this::resetFilters));
        actions.addView(spaced(ui.button("Add Asset", () -> showAssetEditor(null))));
        actions.addView(spaced(ui.button("Export", this::exportResults)));
        undoButton = spaced(ui.button("Undo Delete", this::undoDelete));
        undoButton.setVisibility(View.GONE);
        actions.addView(undoButton);
        HorizontalScrollView actionScroll = new HorizontalScrollView(activity);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.addView(actions);
        filtersPanel.addView(actionScroll);
        root.addView(filtersPanel);
    }

    private TextView spaced(TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginStart(ui.dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout checkRow(CheckBox first, CheckBox second) {
        LinearLayout row = new LinearLayout(activity);
        row.addView(first, new LinearLayout.LayoutParams(0, ui.dp(42), 1));
        if (second != null) {
            row.addView(second, new LinearLayout.LayoutParams(0, ui.dp(42), 1));
        } else {
            row.addView(new View(activity), new LinearLayout.LayoutParams(0, ui.dp(42), 1));
        }
        return row;
    }

    private void toggleFilters() {
        boolean show = filtersPanel.getVisibility() != View.VISIBLE;
        filtersPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        filtersToggle.setText(show ? "Filters \u25B4" : "Filters \u25BE");
    }

    private void buildResults() {
        FrameLayout results = new FrameLayout(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
        );
        params.topMargin = ui.dp(6);
        root.addView(results, params);

        adapter = new AssetAdapter();
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        list.setDivider(new android.graphics.drawable.ColorDrawable(theme.divider()));
        list.setDividerHeight(ui.dp(1));
        list.setSelector(android.R.color.transparent);
        list.setOnItemClickListener((parent, view, position, id) ->
                showAssetDetails(records.get(position))
        );
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            ui.copy("Asset name", records.get(position).assetName);
            return true;
        });
        results.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        emptyLabel = ui.text("No matching assets. Try a different search.", 15, theme.muted(), false);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setVisibility(View.GONE);
        results.addView(emptyLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        loadMoreButton = ui.text("Load more assets", 14, theme.accent, true);
        loadMoreButton.setGravity(Gravity.CENTER);
        loadMoreButton.setPadding(0, ui.dp(12), 0, ui.dp(12));
        loadMoreButton.setVisibility(View.GONE);
        loadMoreButton.setOnClickListener(view -> loadNextPage());
        root.addView(loadMoreButton, ui.match(0));
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(activity);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        spinner.setAdapter(new FilterAdapter(java.util.Collections.singletonList("Loading...")));
        spinner.setEnabled(false);
        return spinner;
    }

    void onDatabaseReady() {
        activity.executor.execute(() -> {
            try {
                List<String> categories = activity.database.getCategories();
                List<String> subcategories = activity.database.getSubcategories(null);
                activity.runOnUiThread(() -> initializeFilters(categories, subcategories));
            } catch (Exception error) {
                activity.runOnUiThread(() -> activity.showDatabaseError(error));
            }
        });
    }

    private void initializeFilters(List<String> categories, List<String> subcategories) {
        applyFilterOptions(categories, subcategories, null, null);
        categorySpinner.setEnabled(true);
        subcategorySpinner.setEnabled(true);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingFilters || !filtersReady) {
                    return;
                }
                String selected = selectedCategory();
                if (!Objects.equals(selected, appliedCategory)) {
                    appliedCategory = selected;
                    updateSubcategories();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        subcategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingFilters || !filtersReady) {
                    return;
                }
                String selected = selectedSubcategory();
                if (!Objects.equals(selected, appliedSubcategory)) {
                    appliedSubcategory = selected;
                    scheduleSearch();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        filtersReady = true;
        refreshSearch();
    }

    private void applyFilterOptions(List<String> categories, List<String> subcategories,
                                    String category, String subcategory) {
        updatingFilters = true;
        List<String> categoryOptions = new ArrayList<>();
        categoryOptions.add(ALL_CATEGORIES);
        categoryOptions.addAll(categories);
        categorySpinner.setAdapter(new FilterAdapter(categoryOptions));
        int categoryIndex = category == null ? 0 : Math.max(0, categoryOptions.indexOf(category));
        categorySpinner.setSelection(categoryIndex);

        List<String> subcategoryOptions = new ArrayList<>();
        subcategoryOptions.add(ALL_SUBCATEGORIES);
        subcategoryOptions.addAll(subcategories);
        subcategorySpinner.setAdapter(new FilterAdapter(subcategoryOptions));
        int subcategoryIndex = subcategory == null
                ? 0 : Math.max(0, subcategoryOptions.indexOf(subcategory));
        subcategorySpinner.setSelection(subcategoryIndex);

        appliedCategory = categoryIndex == 0 ? null : category;
        appliedSubcategory = subcategoryIndex == 0 ? null : subcategory;
        updatingFilters = false;
    }

    private void updateSubcategories() {
        String category = selectedCategory();
        updatingFilters = true;
        subcategorySpinner.setEnabled(false);
        activity.executor.execute(() -> {
            try {
                List<String> subcategories = activity.database.getSubcategories(category);
                activity.runOnUiThread(() -> {
                    List<String> options = new ArrayList<>();
                    options.add(ALL_SUBCATEGORIES);
                    options.addAll(subcategories);
                    subcategorySpinner.setAdapter(new FilterAdapter(options));
                    subcategorySpinner.setSelection(0);
                    subcategorySpinner.setEnabled(true);
                    appliedSubcategory = null;
                    updatingFilters = false;
                    refreshSearch();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> activity.showDatabaseError(error));
            }
        });
    }

    private void reloadFilterOptions() {
        String category = selectedCategory();
        String subcategory = selectedSubcategory();
        activity.executor.execute(() -> {
            try {
                List<String> categories = activity.database.getCategories();
                List<String> subcategories = activity.database.getSubcategories(category);
                activity.runOnUiThread(() -> {
                    applyFilterOptions(categories, subcategories, category, subcategory);
                    refreshSearch();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> activity.showDatabaseError(error));
            }
        });
    }

    private void resetFilters() {
        searchInput.setText("");
        hideVisualCheck.setChecked(true);
        hideWorldCheck.setChecked(true);
        gameplayCheck.setChecked(false);
        exactCheck.setChecked(false);
        baseOnlyCheck.setChecked(false);
        if (filtersReady) {
            updatingFilters = true;
            categorySpinner.setSelection(0);
            appliedCategory = null;
            updatingFilters = false;
            updateSubcategories();
        }
    }

    private String selectedCategory() {
        Object selected = categorySpinner.getSelectedItem();
        if (selected == null || ALL_CATEGORIES.equals(selected.toString())
                || "Loading...".equals(selected.toString())) {
            return null;
        }
        return selected.toString();
    }

    private String selectedSubcategory() {
        Object selected = subcategorySpinner.getSelectedItem();
        if (selected == null || ALL_SUBCATEGORIES.equals(selected.toString())
                || "Loading...".equals(selected.toString())) {
            return null;
        }
        return selected.toString();
    }

    private AssetFilter currentFilter() {
        AssetFilter filter = new AssetFilter();
        filter.text = searchInput.getText().toString();
        filter.category = selectedCategory();
        filter.subcategory = selectedSubcategory();
        filter.exactName = exactCheck.isChecked();
        filter.hideVisual = hideVisualCheck.isChecked();
        filter.hideWorld = hideWorldCheck.isChecked();
        filter.gameplayOnly = gameplayCheck.isChecked();
        filter.baseAssetsOnly = baseOnlyCheck.isChecked();
        return filter;
    }

    private void scheduleSearch() {
        mainHandler.removeCallbacks(debouncedSearch);
        mainHandler.postDelayed(debouncedSearch, 250);
    }

    private void refreshSearch() {
        if (!filtersReady) {
            return;
        }
        int generation = ++queryGeneration;
        AssetFilter filter = currentFilter();
        lastFilter = filter;
        totalCount = -1;
        hasMoreResults = false;
        records.clear();
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
        searchLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        statusLabel.setText("Searching...");

        activity.executor.execute(() -> {
            try {
                List<AssetRecord> page = activity.database.search(filter, 0);
                activity.runOnUiThread(() -> {
                    if (generation == queryGeneration) {
                        applyPage(page, false);
                    }
                });
                if (generation != queryGeneration) {
                    return;
                }
                int total = activity.database.count(filter);
                activity.runOnUiThread(() -> {
                    if (generation == queryGeneration) {
                        totalCount = total;
                        updateStatus();
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> showSearchError(generation, error));
            }
        });
    }

    private void loadNextPage() {
        if (!filtersReady || searchLoading || !hasMoreResults) {
            return;
        }
        int generation = queryGeneration;
        AssetFilter filter = lastFilter;
        int offset = records.size();
        searchLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        loadMoreButton.setVisibility(View.GONE);
        statusLabel.setText("Loading more assets...");
        activity.executor.execute(() -> {
            try {
                List<AssetRecord> page = activity.database.search(filter, offset);
                activity.runOnUiThread(() -> {
                    if (generation == queryGeneration) {
                        applyPage(page, true);
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> showSearchError(generation, error));
            }
        });
    }

    private void applyPage(List<AssetRecord> page, boolean append) {
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
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        loadMoreButton.setVisibility(hasMoreResults ? View.VISIBLE : View.GONE);
        updateStatus();
    }

    private void updateStatus() {
        if (totalCount >= 0) {
            statusLabel.setText(totalCount + " results");
        } else {
            statusLabel.setText(
                    hasMoreResults ? "Showing " + records.size() + "+ results"
                            : records.size() + " results"
            );
        }
    }

    private void showSearchError(int generation, Exception error) {
        if (generation == queryGeneration) {
            searchLoading = false;
            progressBar.setVisibility(View.GONE);
            statusLabel.setText("Search failed: " + error.getMessage());
            loadMoreButton.setVisibility(View.GONE);
        }
    }

    private static String websiteUrl(AssetRecord record) {
        String section;
        switch (record.category == null ? "" : record.category) {
            case "Weapon":
                section = "weapons";
                break;
            case "Equipment":
                section = "armor";
                break;
            case "Deviant":
                section = "deviations";
                break;
            case "World":
            case "Structure":
                section = "map";
                break;
            default:
                section = "items";
        }
        String value = record.displayName == null || record.displayName.trim().isEmpty()
                ? record.assetName : record.displayName;
        return "https://www.oncehumandb.com/" + section + "?search=" + Uri.encode(value);
    }

    private TextView detailLine(String label, String value) {
        TextView line = ui.text(label + ": " + (value == null || value.isEmpty() ? "\u2014" : value),
                14, theme.foreground, false);
        line.setTextIsSelectable(true);
        line.setPadding(0, ui.dp(6), 0, ui.dp(6));
        return line;
    }

    private void showAssetDetails(AssetRecord record) {
        LinearLayout content = ui.form();
        content.addView(detailLine("Display name", record.displayName));
        content.addView(detailLine("Asset name", record.assetName));
        content.addView(detailLine("Category", record.category));
        content.addView(detailLine("Subcategory", record.subcategory));
        content.addView(detailLine("Source", record.source));
        content.addView(detailLine("Search terms", record.searchTerms));
        content.addView(detailLine("Notes", record.notes));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, ui.dp(8), 0, 0);
        AlertDialog[] holder = new AlertDialog[1];
        buttons.addView(dialogButton(holder, "Copy Asset Name",
                () -> ui.copy("Asset name", record.assetName), false));
        buttons.addView(dialogButton(holder, "Copy Display Name",
                () -> ui.copy("Display name", record.displayName), false));
        buttons.addView(dialogButton(holder, "Open on OnceHumanDB", () -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl(record))));
            } catch (Exception error) {
                ui.toast("Could not open OnceHumanDB.");
            }
        }, true));
        if (record.isManual()) {
            buttons.addView(dialogButton(holder, "Edit Manual Asset",
                    () -> showAssetEditor(record), true));
            buttons.addView(dialogButton(holder, "Delete Manual Asset",
                    () -> confirmDelete(record), true));
        }
        content.addView(buttons);

        AlertDialog.Builder builder = ui.dialog(record.displayName, content)
                .setNegativeButton("Close", null);
        holder[0] = ui.show(builder);
    }

    private View dialogButton(AlertDialog[] holder, String label, Runnable action, boolean dismiss) {
        TextView button = ui.button(label, () -> {
            if (dismiss && holder[0] != null) {
                holder[0].dismiss();
            }
            action.run();
        });
        LinearLayout.LayoutParams params = ui.match(0);
        params.topMargin = ui.dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private void showAssetEditor(AssetRecord existing) {
        boolean editing = existing != null;
        LinearLayout form = ui.form();
        EditText assetInput = ui.input("Internal asset name (required)",
                editing ? existing.assetName : "");
        EditText displayInput = ui.input("Display name", editing ? existing.displayName : "");
        EditText categoryInput = ui.input("Category (default: Unknown)",
                editing ? existing.category : "");
        EditText subcategoryInput = ui.input("Subcategory (default: Manual)",
                editing ? existing.subcategory : "");
        EditText notesInput = ui.input("Notes", editing ? existing.notes : "");
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP);
        TextView error = ui.text("", 13, android.graphics.Color.rgb(255, 120, 120), false);
        form.addView(ui.label("Asset name"));
        form.addView(assetInput);
        form.addView(ui.label("Display name"));
        form.addView(displayInput);
        form.addView(ui.label("Category"));
        form.addView(categoryInput);
        form.addView(ui.label("Subcategory"));
        form.addView(subcategoryInput);
        form.addView(ui.label("Notes"));
        form.addView(notesInput);
        form.addView(error);

        AlertDialog dialog = ui.show(
                ui.dialog(editing ? "Edit Manual Asset" : "Add New Asset", form)
                        .setPositiveButton(editing ? "Save Changes" : "Save Asset", null)
                        .setNegativeButton("Cancel", null)
        );
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String assetName = assetInput.getText().toString().trim();
            String displayName = displayInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            String subcategory = subcategoryInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();
            if (assetName.isEmpty()) {
                error.setText("Asset Name is required.");
                return;
            }
            if (displayName.isEmpty()) {
                displayName = assetName.replace('_', ' ');
            }
            if (category.isEmpty()) {
                category = "Unknown";
            }
            if (subcategory.isEmpty()) {
                subcategory = "Manual";
            }
            String finalDisplay = displayName;
            String finalCategory = category;
            String finalSubcategory = subcategory;
            long excludeId = editing ? existing.id : -1;
            view.setEnabled(false);
            activity.executor.execute(() -> {
                try {
                    boolean exists = activity.database.assetNameExists(assetName, excludeId);
                    activity.runOnUiThread(() -> {
                        view.setEnabled(true);
                        if (exists && editing) {
                            error.setText("That Asset Name already exists.");
                        } else if (exists) {
                            confirmDuplicate(assetName, () -> {
                                dialog.dismiss();
                                saveAsset(null, finalCategory, finalSubcategory, assetName,
                                        finalDisplay, notes);
                            });
                        } else {
                            dialog.dismiss();
                            saveAsset(existing, finalCategory, finalSubcategory, assetName,
                                    finalDisplay, notes);
                        }
                    });
                } catch (Exception failure) {
                    activity.runOnUiThread(() -> {
                        view.setEnabled(true);
                        error.setText("Could not save: " + failure.getMessage());
                    });
                }
            });
        });
    }

    private void confirmDuplicate(String assetName, Runnable proceed) {
        ui.show(new AlertDialog.Builder(activity)
                .setTitle("Duplicate Asset")
                .setMessage("An asset with this internal name already exists:\n\n" + assetName
                        + "\n\nAdd it anyway?")
                .setPositiveButton("Add anyway", (d, w) -> proceed.run())
                .setNegativeButton("Cancel", null));
    }

    private void saveAsset(AssetRecord existing, String category, String subcategory,
                           String assetName, String displayName, String notes) {
        activity.executor.execute(() -> {
            try {
                if (existing == null) {
                    activity.database.addManualAsset(category, subcategory, assetName,
                            displayName, notes);
                } else {
                    activity.database.updateManualAsset(existing.id, category, subcategory,
                            assetName, displayName, notes);
                }
                activity.runOnUiThread(() -> {
                    ui.toast(existing == null ? "Asset added" : "Asset updated");
                    reloadFilterOptions();
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> ui.toast("Save failed: " + failure.getMessage()));
            }
        });
    }

    private void confirmDelete(AssetRecord record) {
        ui.show(new AlertDialog.Builder(activity)
                .setTitle("Delete Manual Asset")
                .setMessage("Delete this manual asset?\n\n" + record.displayName + "\n"
                        + record.assetName + "\n\nYou can undo this deletion until the app closes.")
                .setPositiveButton("Delete", (d, w) -> deleteAsset(record))
                .setNegativeButton("Cancel", null));
    }

    private void deleteAsset(AssetRecord record) {
        activity.executor.execute(() -> {
            try {
                activity.database.deleteManualAsset(record.id);
                activity.runOnUiThread(() -> {
                    lastDeleted = record;
                    undoButton.setVisibility(View.VISIBLE);
                    ui.toast("Asset deleted");
                    reloadFilterOptions();
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> ui.toast("Delete failed: " + failure.getMessage()));
            }
        });
    }

    private void undoDelete() {
        AssetRecord record = lastDeleted;
        if (record == null) {
            return;
        }
        activity.executor.execute(() -> {
            try {
                activity.database.addManualAsset(record.category, record.subcategory,
                        record.assetName, record.displayName, record.notes);
                activity.runOnUiThread(() -> {
                    lastDeleted = null;
                    undoButton.setVisibility(View.GONE);
                    ui.toast("Deletion undone");
                    reloadFilterOptions();
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> ui.toast("Undo failed: " + failure.getMessage()));
            }
        });
    }

    private void exportResults() {
        if (!filtersReady || (totalCount == 0) || (totalCount < 0 && records.isEmpty())) {
            ui.toast("There are no results to export.");
            return;
        }
        AssetFilter filter = lastFilter.copy();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, "OnceHuman_SearchResults.csv");
        activity.launchForResult(intent, (resultCode, data) -> {
            if (resultCode != android.app.Activity.RESULT_OK || data == null
                    || data.getData() == null) {
                return;
            }
            Uri uri = data.getData();
            statusLabel.setText("Exporting results...");
            progressBar.setVisibility(View.VISIBLE);
            activity.executor.execute(() -> {
                try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt");
                     BufferedWriter writer = new BufferedWriter(
                             new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                    writer.write('\uFEFF');
                    activity.database.exportCsv(filter, writer);
                    writer.flush();
                    activity.runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        updateStatus();
                        ui.toast("Export complete");
                    });
                } catch (Exception failure) {
                    activity.runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        updateStatus();
                        ui.toast("Export failed: " + failure.getMessage());
                    });
                }
            });
        });
    }

    void destroy() {
        mainHandler.removeCallbacks(debouncedSearch);
    }

    void setStatus(String message) {
        statusLabel.setText(message);
    }

    private final class FilterAdapter extends ArrayAdapter<String> {
        FilterAdapter(List<String> options) {
            super(activity, android.R.layout.simple_spinner_item, options);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return filterText(getItem(position), false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return filterText(getItem(position), true);
        }

        private TextView filterText(String value, boolean dropdown) {
            TextView item = ui.text(value == null ? "" : value, 14, theme.foreground, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setSingleLine(true);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (dropdown) {
                item.setBackgroundColor(theme.surface());
                item.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            } else {
                item.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            }
            return item;
        }
    }

    private final class AssetAdapter extends ArrayAdapter<AssetRecord> {
        AssetAdapter() {
            super(activity, android.R.layout.simple_list_item_1, records);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout container;
            TextView displayName;
            TextView assetName;
            TextView category;
            if (convertView == null || !(convertView.getTag() instanceof View[])) {
                container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
                container.setBackgroundColor(theme.background);
                displayName = ui.text("", 15, theme.foreground, true);
                assetName = ui.text("", 13, theme.accent, false);
                category = ui.text("", 12, theme.muted(), false);
                container.addView(displayName);
                LinearLayout.LayoutParams assetParams = ui.match(0);
                assetParams.topMargin = ui.dp(3);
                container.addView(assetName, assetParams);
                LinearLayout.LayoutParams categoryParams = ui.match(0);
                categoryParams.topMargin = ui.dp(3);
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
            category.setText(record.category + " \u203A " + record.subcategory
                    + (record.isManual() ? "  \u2022 Manual" : ""));
            return container;
        }
    }
}

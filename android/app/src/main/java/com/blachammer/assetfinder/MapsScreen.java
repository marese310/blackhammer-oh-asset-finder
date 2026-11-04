package com.blachammer.assetfinder;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class MapsScreen {
    private static final int MAX_BITMAP_SIDE = 4096;
    private static final String ONCE_HUMAN_DB_MAP = "https://www.oncehumandb.com/map";
    private static final String MAP_GENIE_MAP =
            "https://mapgenie.io/once-human/maps/nalcott?x=-0.47099754573227415"
                    + "&y=0.4641109038569198&zoom=11.000000000000004";
    private static final String TH_MAPS = "https://oncehuman.th.gl/maps";

    private final MainActivity activity;
    private final Ui ui;
    private final AppTheme theme;
    private final LinearLayout root;

    private Spinner mapSelector;
    private MapView mapView;
    private FrameLayout mapHost;
    private WebView webView;
    private LinearLayout onlineBar;
    private TextView onlineStatus;
    private TextView backButton;
    private TextView forwardButton;
    private TextView placeButton;
    private LinearLayout markerPanel;
    private EditText markerSearch;
    private TextView markerDetails;
    private TextView editMarkerButton;
    private TextView deleteMarkerButton;
    private ArrayAdapter<MapMarker> markerAdapter;
    private ListView markerList;

    private final List<MapInfo> maps = new ArrayList<>();
    private final List<MapMarker> allMarkers = new ArrayList<>();
    private final List<MapMarker> visibleMarkers = new ArrayList<>();
    private MapInfo activeMap;
    private MapMarker selectedMarker;
    private boolean updatingMaps;
    private boolean placing;
    private boolean online;
    private int loadGeneration;

    MapsScreen(MainActivity activity, Ui ui) {
        this.activity = activity;
        this.ui = ui;
        this.theme = ui.theme;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        buildToolbars();
        buildMapArea();
        buildMarkerPanel();
        updateMarkerSelection(null);
    }

    View view() {
        return root;
    }

    private void buildToolbars() {
        mapSelector = new Spinner(activity);
        mapSelector.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        mapSelector.setAdapter(new MapAdapter(new ArrayList<>()));
        mapSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingMaps || position < 0 || position >= maps.size()) {
                    return;
                }
                MapInfo selected = maps.get(position);
                if (activeMap == null || activeMap.id != selected.id) {
                    loadMap(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams selectorParams = ui.match(50);
        selectorParams.bottomMargin = ui.dp(4);
        root.addView(mapSelector, selectorParams);

        LinearLayout tools = new LinearLayout(activity);
        tools.addView(ui.button("Import Map", this::importMap));
        placeButton = ui.button("Place Marker", this::togglePlacing);
        tools.addView(spaced(placeButton));
        tools.addView(spaced(ui.button("Fit", () -> mapView.fit())));
        tools.addView(spaced(ui.button("+", () -> mapView.zoomBy(1.5f))));
        tools.addView(spaced(ui.button("\u2212", () -> mapView.zoomBy(1f / 1.5f))));
        tools.addView(spaced(ui.button("Delete Map", this::confirmDeleteMap)));
        root.addView(scrolling(tools));

        LinearLayout links = new LinearLayout(activity);
        links.addView(ui.button("Offline Map", this::showOffline));
        links.addView(spaced(ui.button("OnceHumanDB Map", () -> showOnline(ONCE_HUMAN_DB_MAP))));
        links.addView(spaced(ui.button("MapGenie", () -> showOnline(MAP_GENIE_MAP))));
        links.addView(spaced(ui.button("TH Maps", () -> showOnline(TH_MAPS))));
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        linkParams.topMargin = ui.dp(6);
        root.addView(scrolling(links), linkParams);

        onlineBar = new LinearLayout(activity);
        onlineBar.setGravity(Gravity.CENTER_VERTICAL);
        onlineBar.setVisibility(View.GONE);
        backButton = ui.button("\u2039 Back", () -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            }
        });
        forwardButton = ui.button("Forward \u203A", () -> {
            if (webView != null && webView.canGoForward()) {
                webView.goForward();
            }
        });
        onlineBar.addView(backButton);
        onlineBar.addView(spaced(forwardButton));
        onlineBar.addView(spaced(ui.button("Reload", () -> {
            if (webView != null) {
                webView.reload();
            }
        })));
        onlineStatus = ui.text("", 12, theme.muted(), false);
        onlineStatus.setPadding(ui.dp(10), 0, 0, 0);
        onlineBar.addView(onlineStatus, ui.weighted());
        LinearLayout.LayoutParams barParams = ui.match(0);
        barParams.topMargin = ui.dp(6);
        root.addView(onlineBar, barParams);
    }

    private HorizontalScrollView scrolling(View content) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }

    private View spaced(View button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginStart(ui.dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private void buildMapArea() {
        mapHost = new FrameLayout(activity);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
        );
        hostParams.topMargin = ui.dp(8);
        root.addView(mapHost, hostParams);

        mapView = new MapView(activity, theme);
        mapView.setListener(new MapView.Listener() {
            @Override
            public void onMapTapped(float x, float y) {
                setPlacing(false);
                showMarkerEditor(null, x, y);
            }

            @Override
            public void onMarkerSelected(MapMarker marker) {
                selectMarker(marker, true, false);
            }
        });
        mapHost.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void buildMarkerPanel() {
        markerPanel = new LinearLayout(activity);
        markerPanel.setOrientation(LinearLayout.VERTICAL);
        markerPanel.setPadding(0, ui.dp(8), 0, 0);

        markerSearch = ui.input("Search markers...", "");
        markerSearch.setSingleLine(true);
        markerSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                applyMarkerFilter();
            }

            @Override
            public void afterTextChanged(android.text.Editable value) {}
        });
        markerPanel.addView(markerSearch, ui.match(46));

        markerAdapter = new MarkerAdapter();
        markerList = new ListView(activity);
        markerList.setAdapter(markerAdapter);
        markerList.setDivider(new android.graphics.drawable.ColorDrawable(theme.divider()));
        markerList.setDividerHeight(ui.dp(1));
        markerList.setSelector(android.R.color.transparent);
        markerList.setOnItemClickListener((parent, view, position, id) ->
                selectMarker(visibleMarkers.get(position), false, true)
        );
        LinearLayout.LayoutParams listParams = ui.match(110);
        listParams.topMargin = ui.dp(4);
        markerPanel.addView(markerList, listParams);

        markerDetails = ui.text("", 13, theme.foreground, false);
        markerDetails.setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(4));
        markerDetails.setMaxLines(4);
        markerPanel.addView(markerDetails);

        LinearLayout actions = new LinearLayout(activity);
        editMarkerButton = ui.button("Edit Marker", () -> {
            if (selectedMarker != null) {
                showMarkerEditor(selectedMarker, selectedMarker.x, selectedMarker.y);
            }
        });
        deleteMarkerButton = ui.button("Delete Marker", this::confirmDeleteMarker);
        actions.addView(editMarkerButton);
        actions.addView(spaced(deleteMarkerButton));
        markerPanel.addView(actions);
        root.addView(markerPanel);
    }

    void onDatabaseReady() {
        reloadMaps(-1);
    }

    private void reloadMaps(long selectId) {
        activity.executor.execute(() -> {
            try {
                List<MapInfo> loaded = activity.database.getMaps();
                activity.runOnUiThread(() -> applyMaps(loaded, selectId));
            } catch (Exception error) {
                activity.runOnUiThread(() -> ui.toast("Could not load maps: " + error.getMessage()));
            }
        });
    }

    private void applyMaps(List<MapInfo> loaded, long selectId) {
        long keepId = selectId >= 0 ? selectId : (activeMap == null ? -1 : activeMap.id);
        maps.clear();
        maps.addAll(loaded);
        updatingMaps = true;
        mapSelector.setAdapter(new MapAdapter(maps));
        int index = 0;
        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).id == keepId) {
                index = i;
            }
        }
        if (!maps.isEmpty()) {
            mapSelector.setSelection(index);
        }
        updatingMaps = false;

        if (maps.isEmpty()) {
            activeMap = null;
            loadGeneration++;
            mapView.setBitmap(null);
            mapView.setEmptyMessage("Import a map image to get started.");
            allMarkers.clear();
            applyMarkerFilter();
        } else {
            loadMap(maps.get(index));
        }
    }

    private void loadMap(MapInfo map) {
        activeMap = map;
        int generation = ++loadGeneration;
        setPlacing(false);
        mapView.setBitmap(null);
        mapView.setEmptyMessage("Loading map...");
        allMarkers.clear();
        applyMarkerFilter();
        File file = new File(mapsDirectory(), map.imageFile);
        activity.executor.execute(() -> {
            Bitmap bitmap = decodeBitmap(file);
            List<MapMarker> markers;
            try {
                markers = activity.database.getMarkers(map.id);
            } catch (Exception error) {
                markers = new ArrayList<>();
            }
            List<MapMarker> loadedMarkers = markers;
            activity.runOnUiThread(() -> {
                if (generation != loadGeneration) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    return;
                }
                if (bitmap == null) {
                    mapView.setEmptyMessage("Could not load the selected map image.");
                } else {
                    mapView.setBitmap(bitmap);
                }
                allMarkers.clear();
                allMarkers.addAll(loadedMarkers);
                applyMarkerFilter();
            });
        });
    }

    private File mapsDirectory() {
        File directory = new File(activity.getFilesDir(), "maps");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private static Bitmap decodeBitmap(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / sample > MAX_BITMAP_SIDE
                    || bounds.outHeight / sample > MAX_BITMAP_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable error) {
            return null;
        }
    }

    private void importMap() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        activity.launchForResult(intent, (resultCode, data) -> {
            if (resultCode != android.app.Activity.RESULT_OK || data == null
                    || data.getData() == null) {
                return;
            }
            Uri uri = data.getData();
            activity.executor.execute(() -> {
                File stored = null;
                try {
                    String type = activity.getContentResolver().getType(uri);
                    stored = new File(mapsDirectory(),
                            UUID.randomUUID().toString().replace("-", "") + "." + extensionFor(type));
                    try (InputStream in = activity.getContentResolver().openInputStream(uri);
                         OutputStream out = new java.io.FileOutputStream(stored)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(stored.getAbsolutePath(), bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        stored.delete();
                        activity.runOnUiThread(() ->
                                ui.toast("The selected file is not a usable map image."));
                        return;
                    }
                    File finalStored = stored;
                    activity.runOnUiThread(() -> showMapImportDialog(finalStored));
                } catch (Exception error) {
                    if (stored != null) {
                        stored.delete();
                    }
                    activity.runOnUiThread(() ->
                            ui.toast("Could not import the map: " + error.getMessage()));
                }
            });
        });
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "img";
        }
        switch (mimeType) {
            case "image/png":
                return "png";
            case "image/jpeg":
                return "jpg";
            case "image/webp":
                return "webp";
            case "image/gif":
                return "gif";
            case "image/bmp":
                return "bmp";
            default:
                return "img";
        }
    }

    private void showMapImportDialog(File stored) {
        LinearLayout form = ui.form();
        EditText nameInput = ui.input("Map name or region", "");
        EditText versionInput = ui.input("Scenario or game version", "Nalcott");
        TextView error = ui.text("", 13, android.graphics.Color.rgb(255, 120, 120), false);
        form.addView(ui.label("Map name or region"));
        form.addView(nameInput);
        form.addView(ui.label("Scenario or game version"));
        form.addView(versionInput);
        form.addView(error);

        AlertDialog dialog = ui.show(ui.dialog("Add Map Image", form)
                .setCancelable(false)
                .setPositiveButton("Add Map", null)
                .setNegativeButton("Cancel", (d, which) -> stored.delete()));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            String version = versionInput.getText().toString().trim();
            if (name.isEmpty()) {
                error.setText("A map name is required.");
                return;
            }
            if (version.isEmpty()) {
                version = "Nalcott";
            }
            String finalVersion = version;
            view.setEnabled(false);
            activity.executor.execute(() -> {
                try {
                    long id = activity.database.addMap(name, finalVersion, stored.getName());
                    activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        showOffline();
                        reloadMaps(id);
                    });
                } catch (Exception failure) {
                    stored.delete();
                    activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        ui.toast("Could not add the map: " + failure.getMessage());
                    });
                }
            });
        });
    }

    private void confirmDeleteMap() {
        MapInfo map = activeMap;
        if (map == null) {
            ui.toast("There is no map to delete.");
            return;
        }
        ui.show(new AlertDialog.Builder(activity)
                .setTitle("Delete Map")
                .setMessage("Delete \"" + map + "\" and all of its markers?")
                .setPositiveButton("Delete", (d, w) -> activity.executor.execute(() -> {
                    try {
                        activity.database.deleteMap(map.id);
                        new File(mapsDirectory(), map.imageFile).delete();
                        activity.runOnUiThread(() -> {
                            activeMap = null;
                            reloadMaps(-1);
                        });
                    } catch (Exception error) {
                        activity.runOnUiThread(() ->
                                ui.toast("Could not delete the map: " + error.getMessage()));
                    }
                }))
                .setNegativeButton("Cancel", null));
    }

    private void togglePlacing() {
        if (online) {
            showOffline();
        }
        if (!mapView.hasMap() || activeMap == null) {
            ui.toast("Import or select a map first.");
            return;
        }
        setPlacing(!placing);
    }

    private void setPlacing(boolean value) {
        placing = value;
        mapView.setPlacing(value);
        placeButton.setText(value ? "Cancel Placing" : "Place Marker");
    }

    private void applyMarkerFilter() {
        String query = markerSearch == null ? "" : markerSearch.getText().toString();
        visibleMarkers.clear();
        for (MapMarker marker : allMarkers) {
            if (marker.matches(query)) {
                visibleMarkers.add(marker);
            }
        }
        if (selectedMarker != null) {
            boolean stillVisible = false;
            for (MapMarker marker : visibleMarkers) {
                if (marker.id == selectedMarker.id) {
                    selectedMarker = marker;
                    stillVisible = true;
                }
            }
            if (!stillVisible) {
                selectedMarker = null;
            }
        }
        markerAdapter.notifyDataSetChanged();
        mapView.setMarkers(visibleMarkers);
        updateMarkerSelection(selectedMarker);
    }

    private void selectMarker(MapMarker marker, boolean scrollList, boolean center) {
        updateMarkerSelection(marker);
        if (scrollList) {
            int position = visibleMarkers.indexOf(marker);
            if (position >= 0) {
                markerList.smoothScrollToPosition(position);
            }
        }
        if (center) {
            mapView.centerOn(marker);
        }
    }

    private void updateMarkerSelection(MapMarker marker) {
        selectedMarker = marker;
        mapView.setSelectedMarkerId(marker == null ? -1 : marker.id);
        markerAdapter.notifyDataSetChanged();
        boolean has = marker != null;
        editMarkerButton.setEnabled(has);
        deleteMarkerButton.setEnabled(has);
        editMarkerButton.setAlpha(has ? 1f : 0.4f);
        deleteMarkerButton.setAlpha(has ? 1f : 0.4f);
        if (!has) {
            markerDetails.setText(allMarkers.isEmpty()
                    ? "No markers yet. Use Place Marker, then tap the map."
                    : "Select a marker to see its details.");
        } else {
            String notes = marker.notes.isEmpty() ? "No notes." : marker.notes;
            markerDetails.setText(marker.name + " \u2014 " + marker.type + "\n" + notes);
        }
    }

    private void showMarkerEditor(MapMarker existing, float x, float y) {
        if (activeMap == null) {
            return;
        }
        boolean editing = existing != null;
        LinearLayout form = ui.form();
        EditText nameInput = ui.input("Marker name", editing ? existing.name : "");
        EditText typeInput = ui.input("Type or status", editing ? existing.type : "Custom");
        EditText notesInput = ui.input("Information / notes", editing ? existing.notes : "");
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP);
        TextView error = ui.text("", 13, android.graphics.Color.rgb(255, 120, 120), false);
        form.addView(ui.label("Marker name"));
        form.addView(nameInput);
        form.addView(ui.label("Type or status"));
        form.addView(typeInput);
        form.addView(ui.label("Information / notes"));
        form.addView(notesInput);
        form.addView(ui.label(String.format(Locale.ROOT, "Map position: %.1f%%, %.1f%%",
                x * 100f, y * 100f)));
        form.addView(error);

        AlertDialog dialog = ui.show(ui.dialog(editing ? "Edit Marker" : "Add Map Marker", form)
                .setPositiveButton("Save Marker", null)
                .setNegativeButton("Cancel", null));
        long mapId = activeMap.id;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            String type = typeInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();
            if (name.isEmpty()) {
                error.setText("A marker name is required.");
                return;
            }
            if (type.isEmpty()) {
                type = "Custom";
            }
            String finalType = type;
            view.setEnabled(false);
            activity.executor.execute(() -> {
                try {
                    long id;
                    if (editing) {
                        activity.database.updateMarker(existing.id, name, finalType, notes);
                        id = existing.id;
                    } else {
                        id = activity.database.addMarker(mapId, name, finalType, notes, x, y);
                    }
                    List<MapMarker> markers = activity.database.getMarkers(mapId);
                    activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        if (activeMap == null || activeMap.id != mapId) {
                            return;
                        }
                        allMarkers.clear();
                        allMarkers.addAll(markers);
                        selectedMarker = null;
                        for (MapMarker marker : markers) {
                            if (marker.id == id) {
                                selectedMarker = marker;
                            }
                        }
                        applyMarkerFilter();
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

    private void confirmDeleteMarker() {
        MapMarker marker = selectedMarker;
        if (marker == null) {
            return;
        }
        ui.show(new AlertDialog.Builder(activity)
                .setTitle("Delete Marker")
                .setMessage("Delete the marker \"" + marker.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> activity.executor.execute(() -> {
                    try {
                        activity.database.deleteMarker(marker.id);
                        activity.runOnUiThread(() -> {
                            allMarkers.removeIf(item -> item.id == marker.id);
                            selectedMarker = null;
                            applyMarkerFilter();
                        });
                    } catch (Exception error) {
                        activity.runOnUiThread(() ->
                                ui.toast("Could not delete: " + error.getMessage()));
                    }
                }))
                .setNegativeButton("Cancel", null));
    }

    private void showOffline() {
        online = false;
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
        mapView.setVisibility(View.VISIBLE);
        markerPanel.setVisibility(View.VISIBLE);
        onlineBar.setVisibility(View.GONE);
    }

    private void showOnline(String url) {
        setPlacing(false);
        online = true;
        ensureWebView();
        mapView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        markerPanel.setVisibility(View.GONE);
        onlineBar.setVisibility(View.VISIBLE);
        onlineStatus.setText("Loading online map...");
        webView.loadUrl(url);
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void ensureWebView() {
        if (webView != null) {
            return;
        }
        webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        webView.setBackgroundColor(theme.background);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                onlineStatus.setText("");
                backButton.setAlpha(view.canGoBack() ? 1f : 0.4f);
                forwardButton.setAlpha(view.canGoForward() ? 1f : 0.4f);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    onlineStatus.setText("The online map did not load. Check your connection or reload.");
                }
            }
        });
        mapHost.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    boolean handleBack() {
        if (online && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        if (online) {
            showOffline();
            return true;
        }
        if (placing) {
            setPlacing(false);
            return true;
        }
        return false;
    }

    void destroy() {
        if (webView != null) {
            mapHost.removeView(webView);
            webView.destroy();
            webView = null;
        }
    }

    private final class MapAdapter extends ArrayAdapter<MapInfo> {
        MapAdapter(List<MapInfo> items) {
            super(activity, android.R.layout.simple_spinner_item, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return row(position, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return row(position, true);
        }

        private View row(int position, boolean dropdown) {
            TextView item = ui.text(String.valueOf(getItem(position)), 14, theme.foreground, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            if (dropdown) {
                item.setBackgroundColor(theme.surface());
                item.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            } else {
                item.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            }
            return item;
        }
    }

    private final class MarkerAdapter extends ArrayAdapter<MapMarker> {
        MarkerAdapter() {
            super(activity, android.R.layout.simple_list_item_1, visibleMarkers);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MapMarker marker = visibleMarkers.get(position);
            boolean selected = selectedMarker != null && selectedMarker.id == marker.id;
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7));
            container.setBackgroundColor(selected ? theme.surface() : theme.background);
            container.addView(ui.text(marker.name, 14, selected ? theme.accent : theme.foreground, true));
            container.addView(ui.text(
                    String.format(Locale.ROOT, "%s \u2022 %.0f%%, %.0f%%",
                            marker.type, marker.x * 100f, marker.y * 100f),
                    12, theme.muted(), false));
            return container;
        }
    }
}

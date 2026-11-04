package com.blachammer.assetfinder;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AssetDatabase extends SQLiteOpenHelper {
    static final int PAGE_SIZE = 40;
    private static final String DATABASE_NAME = "blachammer-assets.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "assets";
    private final Context context;
    private final ImportProgressListener progressListener;

    interface ImportProgressListener {
        void onProgress(int importedRows);
    }

    AssetDatabase(Context context, ImportProgressListener progressListener) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        this.progressListener = progressListener;
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                        "asset_name TEXT NOT NULL, " +
                        "display_name TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "subcategory TEXT NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "search_terms TEXT NOT NULL, " +
                        "notes TEXT NOT NULL DEFAULT '')"
        );
        database.execSQL(
                "CREATE INDEX assets_name_idx ON " + TABLE + "(asset_name COLLATE NOCASE)"
        );
        database.execSQL(
                "CREATE INDEX assets_category_idx ON " + TABLE +
                        "(category COLLATE NOCASE, subcategory COLLATE NOCASE)"
        );
        createMapTables(database);
        importBundledDatabase(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            createMapTables(database);
        }
    }

    private static void createMapTables(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS maps (" +
                        "map_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "version TEXT NOT NULL, " +
                        "image_file TEXT NOT NULL)"
        );
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS map_markers (" +
                        "marker_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "map_id INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "notes TEXT NOT NULL, " +
                        "x REAL NOT NULL, " +
                        "y REAL NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS markers_map_idx ON map_markers(map_id)"
        );
    }

    private void importBundledDatabase(SQLiteDatabase database) {
        int imported = 0;
        database.beginTransaction();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        context.getAssets().open("OnceHuman_EnhancedDatabase.csv"),
                        StandardCharsets.UTF_8
                )
        )) {
            CsvReader csv = new CsvReader(reader);
            List<String> header = csv.readRow();
            if (header == null) {
                throw new IllegalStateException("The bundled asset database is empty.");
            }
            int categoryIndex = header.indexOf("Category");
            int subcategoryIndex = header.indexOf("SubCategory");
            int assetNameIndex = header.indexOf("AssetName");
            int displayNameIndex = header.indexOf("DisplayName");
            int sourceIndex = header.indexOf("Source");
            int searchTermsIndex = header.indexOf("SearchTerms");
            if (categoryIndex < 0 || subcategoryIndex < 0 || assetNameIndex < 0 ||
                    displayNameIndex < 0 || sourceIndex < 0 || searchTermsIndex < 0) {
                throw new IllegalStateException("The bundled asset database has an invalid header.");
            }

            String insertSql = "INSERT INTO " + TABLE +
                    "(asset_name, display_name, category, subcategory, source, search_terms) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (android.database.sqlite.SQLiteStatement insert =
                         database.compileStatement(insertSql)) {
                List<String> row;
                while ((row = csv.readRow()) != null) {
                    if (row.size() <= searchTermsIndex) {
                        throw new IllegalStateException(
                                "The bundled asset database contains an incomplete row at record " +
                                        (imported + 2) + "."
                        );
                    }
                    if (row.size() <= Math.max(
                            Math.max(categoryIndex, subcategoryIndex),
                            Math.max(Math.max(assetNameIndex, displayNameIndex), sourceIndex)
                    )) {
                        throw new IllegalStateException(
                                "The bundled asset database contains a malformed row at record " +
                                        (imported + 2) + "."
                        );
                    }
                    String assetName = row.get(assetNameIndex);
                    if (assetName.isEmpty()) {
                        continue;
                    }
                    insert.clearBindings();
                    insert.bindString(1, assetName);
                    insert.bindString(2, row.get(displayNameIndex));
                    insert.bindString(3, row.get(categoryIndex));
                    insert.bindString(4, row.get(subcategoryIndex));
                    insert.bindString(5, row.get(sourceIndex));
                    insert.bindString(6, row.get(searchTermsIndex));
                    insert.executeInsert();
                    imported++;
                    if (imported % 10000 == 0 && progressListener != null) {
                        progressListener.onProgress(imported);
                    }
                }
            }
            if (imported == 0) {
                throw new IllegalStateException("The bundled asset database contains no assets.");
            }
            database.setTransactionSuccessful();
        } catch (IOException error) {
            throw new IllegalStateException("Could not read the bundled asset database.", error);
        } finally {
            database.endTransaction();
        }
    }

    List<String> getCategories() {
        return getDistinctValues("category", null);
    }

    List<String> getSubcategories(String category) {
        return getDistinctValues("subcategory", category);
    }

    private List<String> getDistinctValues(String column, String category) {
        List<String> values = new ArrayList<>();
        String selection = category == null ? null : "category = ? COLLATE NOCASE";
        String[] selectionArgs = category == null ? null : new String[]{category};
        try (Cursor cursor = getReadableDatabase().query(
                true, TABLE, new String[]{column}, selection, selectionArgs,
                null, null, column + " COLLATE NOCASE ASC", null
        )) {
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value != null && !value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static final String[] RECORD_COLUMNS = {
            "rowid", "category", "subcategory", "asset_name", "display_name",
            "source", "search_terms", "notes"
    };
    private static final String RESULT_ORDER =
            "category COLLATE NOCASE ASC, subcategory COLLATE NOCASE ASC, " +
                    "display_name COLLATE NOCASE ASC, asset_name COLLATE NOCASE ASC";

    private static final class Where {
        String selection;
        String[] args;
    }

    private static Where buildWhere(AssetFilter filter) {
        List<String> selections = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (filter.hideVisual) {
            selections.add("category <> ? COLLATE NOCASE");
            args.add("Visual");
        }
        if (filter.hideWorld) {
            selections.add("category <> ? COLLATE NOCASE");
            args.add("World");
        }
        if (filter.gameplayOnly) {
            StringBuilder in = new StringBuilder("category COLLATE NOCASE IN (");
            for (int i = 0; i < AssetFilter.GAMEPLAY_CATEGORIES.size(); i++) {
                in.append(i == 0 ? "?" : ",?");
                args.add(AssetFilter.GAMEPLAY_CATEGORIES.get(i));
            }
            selections.add(in.append(")").toString());
        }
        if (filter.category != null && !filter.category.isEmpty()) {
            selections.add("category = ? COLLATE NOCASE");
            args.add(filter.category);
        }
        if (filter.subcategory != null && !filter.subcategory.isEmpty()) {
            selections.add("subcategory = ? COLLATE NOCASE");
            args.add(filter.subcategory);
        }
        if (filter.baseAssetsOnly) {
            selections.add(
                    "lower(asset_name) NOT GLOB '*_albedo*' AND " +
                            "lower(asset_name) NOT GLOB '*_normal*' AND " +
                            "lower(asset_name) NOT GLOB '*_control*' AND " +
                            "lower(asset_name) NOT GLOB '*_feature*' AND " +
                            "lower(asset_name) NOT GLOB '*_lod[0-9]*'"
            );
        }
        String text = filter.text == null ? "" : filter.text.trim();
        if (!text.isEmpty()) {
            if (filter.exactName) {
                selections.add("asset_name = ? COLLATE NOCASE");
                args.add(text);
            } else {
                selections.add("search_terms LIKE ? ESCAPE '\\'");
                args.add("%" + escapeLike(text) + "%");
            }
        }

        Where where = new Where();
        where.selection = selections.isEmpty() ? null : TextUtils.join(" AND ", selections);
        where.args = args.isEmpty() ? null : args.toArray(new String[0]);
        return where;
    }

    private static AssetRecord readRecord(Cursor cursor) {
        return new AssetRecord(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                cursor.getString(7)
        );
    }

    List<AssetRecord> search(AssetFilter filter, int offset) {
        Where where = buildWhere(filter);
        List<AssetRecord> results = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE, RECORD_COLUMNS, where.selection, where.args,
                null, null, RESULT_ORDER, offset + "," + (PAGE_SIZE + 1)
        )) {
            while (cursor.moveToNext()) {
                results.add(readRecord(cursor));
            }
        }
        return results;
    }

    int count(AssetFilter filter) {
        Where where = buildWhere(filter);
        String sql = "SELECT COUNT(*) FROM " + TABLE +
                (where.selection == null ? "" : " WHERE " + where.selection);
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, where.args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    void exportCsv(AssetFilter filter, java.io.Writer writer) throws IOException {
        Where where = buildWhere(filter);
        writer.write("Category,SubCategory,AssetName,DisplayName,Source,SearchTerms,Notes\r\n");
        try (Cursor cursor = getReadableDatabase().query(
                TABLE, RECORD_COLUMNS, where.selection, where.args,
                null, null, RESULT_ORDER
        )) {
            while (cursor.moveToNext()) {
                AssetRecord record = readRecord(cursor);
                writer.write(csvField(record.category));
                writer.write(',');
                writer.write(csvField(record.subcategory));
                writer.write(',');
                writer.write(csvField(record.assetName));
                writer.write(',');
                writer.write(csvField(record.displayName));
                writer.write(',');
                writer.write(csvField(record.source));
                writer.write(',');
                writer.write(csvField(record.searchTerms));
                writer.write(',');
                writer.write(csvField(record.notes));
                writer.write("\r\n");
            }
        }
    }

    static String csvField(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    boolean assetNameExists(String assetName, long excludeId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE asset_name = ? COLLATE NOCASE AND rowid <> ? LIMIT 1",
                new String[]{assetName, String.valueOf(excludeId)}
        )) {
            return cursor.moveToFirst();
        }
    }

    static String buildSearchTerms(String category, String subcategory, String assetName,
                                   String displayName, String notes) {
        return TextUtils.join(";", new String[]{category, subcategory, assetName, displayName, notes});
    }

    private static android.content.ContentValues manualValues(
            String category, String subcategory, String assetName,
            String displayName, String notes
    ) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("asset_name", assetName);
        values.put("display_name", displayName);
        values.put("category", category);
        values.put("subcategory", subcategory);
        values.put("source", AssetRecord.MANUAL_SOURCE);
        values.put("search_terms",
                buildSearchTerms(category, subcategory, assetName, displayName, notes));
        values.put("notes", notes);
        return values;
    }

    long addManualAsset(String category, String subcategory, String assetName,
                        String displayName, String notes) {
        return getWritableDatabase().insertOrThrow(
                TABLE, null, manualValues(category, subcategory, assetName, displayName, notes)
        );
    }

    void updateManualAsset(long id, String category, String subcategory, String assetName,
                           String displayName, String notes) {
        getWritableDatabase().update(
                TABLE, manualValues(category, subcategory, assetName, displayName, notes),
                "rowid = ? AND source = ? COLLATE NOCASE",
                new String[]{String.valueOf(id), AssetRecord.MANUAL_SOURCE}
        );
    }

    void deleteManualAsset(long id) {
        getWritableDatabase().delete(
                TABLE, "rowid = ? AND source = ? COLLATE NOCASE",
                new String[]{String.valueOf(id), AssetRecord.MANUAL_SOURCE}
        );
    }

    List<MapInfo> getMaps() {
        List<MapInfo> maps = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "maps", new String[]{"map_id", "name", "version", "image_file"},
                null, null, null, null, "map_id ASC"
        )) {
            while (cursor.moveToNext()) {
                maps.add(new MapInfo(
                        cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3)
                ));
            }
        }
        return maps;
    }

    long addMap(String name, String version, String imageFile) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("name", name);
        values.put("version", version);
        values.put("image_file", imageFile);
        return getWritableDatabase().insertOrThrow("maps", null, values);
    }

    void deleteMap(long mapId) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete("map_markers", "map_id = ?", new String[]{String.valueOf(mapId)});
            database.delete("maps", "map_id = ?", new String[]{String.valueOf(mapId)});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    List<MapMarker> getMarkers(long mapId) {
        List<MapMarker> markers = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "map_markers",
                new String[]{"marker_id", "map_id", "name", "type", "notes", "x", "y"},
                "map_id = ?", new String[]{String.valueOf(mapId)},
                null, null, "name COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                markers.add(new MapMarker(
                        cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4),
                        cursor.getFloat(5), cursor.getFloat(6)
                ));
            }
        }
        return markers;
    }

    long addMarker(long mapId, String name, String type, String notes, float x, float y) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("map_id", mapId);
        values.put("name", name);
        values.put("type", type);
        values.put("notes", notes);
        values.put("x", x);
        values.put("y", y);
        return getWritableDatabase().insertOrThrow("map_markers", null, values);
    }

    void updateMarker(long markerId, String name, String type, String notes) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("notes", notes);
        getWritableDatabase().update(
                "map_markers", values, "marker_id = ?", new String[]{String.valueOf(markerId)}
        );
    }

    void deleteMarker(long markerId) {
        getWritableDatabase().delete(
                "map_markers", "marker_id = ?", new String[]{String.valueOf(markerId)}
        );
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

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
    private static final int DATABASE_VERSION = 1;
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
                        "search_terms TEXT NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX assets_name_idx ON " + TABLE + "(asset_name COLLATE NOCASE)"
        );
        database.execSQL(
                "CREATE INDEX assets_category_idx ON " + TABLE +
                        "(category COLLATE NOCASE, subcategory COLLATE NOCASE)"
        );
        importBundledDatabase(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException(
                "Unsupported asset database upgrade from " + oldVersion + " to " + newVersion
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

    List<AssetRecord> search(
            String text,
            String category,
            String subcategory,
            boolean exactName,
            int offset
    ) {
        List<String> selections = new ArrayList<>();
        List<String> selectionArgs = new ArrayList<>();

        if (text != null && !text.trim().isEmpty()) {
            if (exactName) {
                selections.add("asset_name = ? COLLATE NOCASE");
                selectionArgs.add(text.trim());
            } else {
                selections.add("search_terms LIKE ? ESCAPE '\\'");
                selectionArgs.add("%" + escapeLike(text.trim()) + "%");
            }
        }
        if (category != null && !category.isEmpty()) {
            selections.add("category = ? COLLATE NOCASE");
            selectionArgs.add(category);
        }
        if (subcategory != null && !subcategory.isEmpty()) {
            selections.add("subcategory = ? COLLATE NOCASE");
            selectionArgs.add(subcategory);
        }

        String selection = selections.isEmpty()
                ? null
                : TextUtils.join(" AND ", selections);
        String[] args = selectionArgs.isEmpty()
                ? null
                : selectionArgs.toArray(new String[0]);
        List<AssetRecord> results = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"category", "subcategory", "asset_name", "display_name", "source"},
                selection,
                args,
                null,
                null,
                "display_name COLLATE NOCASE ASC, asset_name COLLATE NOCASE ASC",
                offset + "," + PAGE_SIZE
        )) {
            while (cursor.moveToNext()) {
                results.add(new AssetRecord(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)
                ));
            }
        }
        return results;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

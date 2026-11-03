package com.blachammer.assetfinder;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class CsvReaderTest {
    @Test
    public void readsQuotedCommasAndEscapedQuotes() throws Exception {
        CsvReader csv = new CsvReader(new StringReader(
                "name,notes\r\n\"deviant, bear\",\"said \"\"hello\"\"\"\r\n"
        ));

        assertEquals(Arrays.asList("name", "notes"), csv.readRow());
        assertEquals(Arrays.asList("deviant, bear", "said \"hello\""), csv.readRow());
        assertNull(csv.readRow());
    }

    @Test
    public void readsNewlinesInsideQuotedFields() throws Exception {
        CsvReader csv = new CsvReader(new StringReader("\"first line\r\nsecond line\",value\n"));

        assertEquals(Arrays.asList("first line\r\nsecond line", "value"), csv.readRow());
        assertNull(csv.readRow());
    }

    @Test
    public void readsEmptyFieldsAndCrLfRows() throws Exception {
        CsvReader csv = new CsvReader(new StringReader(",last\r\nmiddle,\r\n"));

        assertEquals(Arrays.asList("", "last"), csv.readRow());
        assertEquals(Arrays.asList("middle", ""), csv.readRow());
        assertNull(csv.readRow());
    }

    @Test
    public void returnsNoRowForEmptyInput() throws Exception {
        CsvReader csv = new CsvReader(new StringReader(""));

        assertNull(csv.readRow());
    }

    @Test
    public void readsTheBundledAssetDatabase() throws Exception {
        File databaseFile = new File(System.getProperty("asset.database.path"));
        assertTrue(databaseFile.isFile());

        int imported = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(databaseFile), StandardCharsets.UTF_8)
        )) {
            CsvReader csv = new CsvReader(reader);
            List<String> header = csv.readRow();
            assertEquals(
                    Arrays.asList(
                            "Category", "SubCategory", "AssetName",
                            "DisplayName", "Source", "SearchTerms"
                    ),
                    header
            );

            List<String> row;
            while ((row = csv.readRow()) != null) {
                if (row.size() != header.size()) {
                    throw new AssertionError(
                            "Record " + (imported + 2) + " has " + row.size() +
                                    " columns; expected " + header.size() + "."
                    );
                }
                if (row.get(2).isEmpty()) {
                    throw new AssertionError("Asset name is empty in record " + (imported + 2));
                }
                imported++;
            }
        }
        if (imported < 100000) {
            throw new AssertionError("Only " + imported + " bundled records were read.");
        }
    }
}

package com.blachammer.assetfinder;

final class AssetRecord {
    static final String MANUAL_SOURCE = "Manual";

    final long id;
    final String category;
    final String subcategory;
    final String assetName;
    final String displayName;
    final String source;
    final String searchTerms;
    final String notes;

    AssetRecord(long id, String category, String subcategory, String assetName,
                String displayName, String source, String searchTerms, String notes) {
        this.id = id;
        this.category = category;
        this.subcategory = subcategory;
        this.assetName = assetName;
        this.displayName = displayName;
        this.source = source;
        this.searchTerms = searchTerms;
        this.notes = notes;
    }

    boolean isManual() {
        return MANUAL_SOURCE.equalsIgnoreCase(source);
    }
}

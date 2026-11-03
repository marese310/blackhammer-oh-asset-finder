package com.blachammer.assetfinder;

final class AssetRecord {
    final String category;
    final String subcategory;
    final String assetName;
    final String displayName;
    final String source;

    AssetRecord(String category, String subcategory, String assetName,
                String displayName, String source) {
        this.category = category;
        this.subcategory = subcategory;
        this.assetName = assetName;
        this.displayName = displayName;
        this.source = source;
    }
}

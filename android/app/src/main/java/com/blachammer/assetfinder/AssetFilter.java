package com.blachammer.assetfinder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AssetFilter {
    static final List<String> GAMEPLAY_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "Animal", "Deviant", "Equipment", "Interactive", "KeyItem", "Loot", "Monster",
            "NPC", "Plant", "Resource", "Structure", "Vehicle", "Weapon"
    ));

    String text = "";
    String category;
    String subcategory;
    boolean exactName;
    boolean hideVisual = true;
    boolean hideWorld = true;
    boolean gameplayOnly;
    boolean baseAssetsOnly;

    AssetFilter copy() {
        AssetFilter copy = new AssetFilter();
        copy.text = text;
        copy.category = category;
        copy.subcategory = subcategory;
        copy.exactName = exactName;
        copy.hideVisual = hideVisual;
        copy.hideWorld = hideWorld;
        copy.gameplayOnly = gameplayOnly;
        copy.baseAssetsOnly = baseAssetsOnly;
        return copy;
    }
}

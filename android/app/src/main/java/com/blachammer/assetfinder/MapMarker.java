package com.blachammer.assetfinder;

final class MapMarker {
    final long id;
    final long mapId;
    final String name;
    final String type;
    final String notes;
    final float x;
    final float y;

    MapMarker(long id, long mapId, String name, String type, String notes, float x, float y) {
        this.id = id;
        this.mapId = mapId;
        this.name = name;
        this.type = type;
        this.notes = notes;
        this.x = x;
        this.y = y;
    }

    boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        return name.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || type.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || notes.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }
}

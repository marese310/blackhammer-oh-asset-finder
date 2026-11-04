package com.blachammer.assetfinder;

final class MapInfo {
    final long id;
    final String name;
    final String version;
    final String imageFile;

    MapInfo(long id, String name, String version, String imageFile) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.imageFile = imageFile;
    }

    @Override
    public String toString() {
        return version == null || version.isEmpty() ? name : name + " \u2014 " + version;
    }
}

package me.seaicedragon.lostdropedit.storage;

public enum DropMode {
    APPEND,
    REPLACE;

    public DropMode toggle() {
        return this == APPEND ? REPLACE : APPEND;
    }
}

package org.nemesis.jfs.result;

/**
 *
 * @author Tim Boudreau
 */
public enum UpToDateness {
    CURRENT,
    STALE,
    UNKNOWN;

    public boolean mayRequireRebuild() {
        return this != CURRENT;
    }

    public boolean isUpToDate() {
        return !mayRequireRebuild();
    }
}

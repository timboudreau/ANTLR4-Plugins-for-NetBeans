package org.nemesis.jfs;

/**
 * The type of backing storage for a "file" in a JFS - needed by clients
 * up-to-dateness checks when a JFS is retained and reused, to know which files
 * should be re-checked to determine if rerunning a compilation or generation
 * process is needed.
 *
 * @author Tim Boudreau
 */
public enum JFSStorageKind {

    HEAP_BYTES,
    MAPPED_BYTES,
    MASQUERADED_DOCUMENT,
    MASQUERADED_FILE,
    DISCARDED;

    public boolean isBytes() {
        switch (this) {
            case HEAP_BYTES:
            case MAPPED_BYTES:
                return true;
        }
        return false;
    }

    public boolean isMasqueraded() {
        switch (this) {
            case MASQUERADED_DOCUMENT:
            case MASQUERADED_FILE:
                return true;
        }
        return false;
    }
}

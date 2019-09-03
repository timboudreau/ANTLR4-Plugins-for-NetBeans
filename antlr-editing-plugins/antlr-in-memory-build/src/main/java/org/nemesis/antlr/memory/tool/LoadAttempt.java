package org.nemesis.antlr.memory.tool;

import java.nio.file.Path;
import java.util.Objects;
import javax.tools.JavaFileManager;

/**
 *
 * @author Tim Boudreau
 */
final class LoadAttempt {

    private final Path path;
    private final JavaFileManager.Location location;

    public LoadAttempt(Path path, JavaFileManager.Location location) {
        this.path = path;
        this.location = location;
    }

    @Override
    public String toString() {
        return location + ":" + path;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.path);
        hash = 41 * hash + Objects.hashCode(this.location);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof LoadAttempt)) {
            return false;
        }
        final LoadAttempt other = (LoadAttempt) obj;
        if (!Objects.equals(this.path, other.path)) {
            return false;
        }
        return Objects.equals(this.location, other.location);
    }

}

/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.jfs;

import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.Serializable;
import java.util.Collection;
import javax.tools.JavaFileManager.Location;

/**
 * Abstraction for a pointer to a JFS file object independent of the file
 * system.
 *
 * @author Tim Boudreau
 */
public interface JFSCoordinates extends Comparable<JFSCoordinates> {

    public static JFSCoordinates create(Location loc, UnixPath path) {
        return new JFSFileCoordinates(notNull("path", path),
                notNull("loc", loc));
    }

    /**
     * Resolve this JFSFileCoordinates against a JFS which may or may not be the
     * same one it was created against, returning an existing file object if one
     * exists in it that has the same path and location name as this one, and
     * creating a new file object otherwise.
     *
     * @param jfs A JFS
     * @return A file object
     */
    JFSFileObject resolve(JFS against);

    /**
     * Resolve this JFSFileCoordinates against a JFS which may or may not be the
     * same one it was created against, returning a file object if one exists in
     * it that has the same path and location name as this one.
     *
     * @param jfs A JFS
     * @return A file object or null
     */
    JFSFileObject resolveOrCreate(JFS against);

    /**
     * Get the file path.
     *
     * @return The file path
     */
    UnixPath path();

    /**
     * The location within the file system.
     *
     * @return The location
     */
    Location location();

    default boolean is(UnixPath path) {
        return notNull("path", path).equals(path());
    }

    default JFSCoordinates toCoordinates() {
        return this;
    }

    default boolean isSameCoordinates(JFSCoordinates other) {
        return path().equals(other.path()) && location().equals(other.location());
    }

    @Override
    default int compareTo(JFSCoordinates o) {
        Location myLoc = location();
        Location otherLoc = o.location();
        if (myLoc != otherLoc || !myLoc.equals(otherLoc)) {
            if (myLoc.isOutputLocation() != otherLoc.isOutputLocation()) {
                if (myLoc.isOutputLocation()) {
                    return 1;
                } else {
                    return -1;
                }
            }
            int result = myLoc.getName().compareTo(otherLoc.getName());
            if (result != 0) {
                return result;
            }
        }
        return path().compareTo(o.path());
    }

    /**
     * Subinterface which can directly resolve a JFS FileObject if the
     * originating JFS was created in this VM and is still alive.
     */
    public interface Resolvable extends JFSCoordinates, Serializable {

        /**
         * Resolve the original FileObject without reference to the originating
         * JFS, if it still exists and is open.
         *
         * @return A file object or null
         */
        JFSFileObject resolveOriginal();
    }

    /**
     * Find the (first) JFSCoordinates corresponding to a given path in a
     * collection thereof.
     *
     * @param <T> The subtype
     * @param path A path
     * @param coll A collection of coordinates
     * @return A coordinate from the collection, or null
     */
    static <T extends JFSCoordinates> T forPath(UnixPath path, Collection<? extends T> coll) {
        for (T item : coll) {
            if (path.equals(item.path())) {
                return item;
            }
        }
        return null;
    }
}

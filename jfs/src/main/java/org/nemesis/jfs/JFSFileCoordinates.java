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
import javax.tools.JavaFileManager.Location;

/**
 * Coordinates within a JFS filesystem.
 *
 * @author Tim Boudreau
 */
final class JFSFileCoordinates implements JFSCoordinates {

    private final UnixPath path;
    private final Location location;

    JFSFileCoordinates(UnixPath path, Location location) {
        this.path = path;
        this.location = location;
    }

    @Override
    public UnixPath path() {
        return path;
    }

    @Override
    public Location location() {
        return location;
    }

    /**
     * Resolve this JFSFileCoordinates against a JFS which may or may not be the
     * same one it was created against, returning a file object if one exists in
     * it that has the same path and location name as this one.
     *
     * @param jfs A JFS
     * @return A file object or null
     */
    @Override
    public JFSFileObject resolve(JFS jfs) {
        return jfs.get(location, path);
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
    @Override
    public JFSFileObject resolveOrCreate(JFS jfs) {
        return jfs.get(location, path, true);
    }

    @Override
    public boolean equals(Object o) {
        return o == null || !(o instanceof JFSFileCoordinates) ? false
                : o == this ? true : ((JFSFileCoordinates) o).path.equals(path)
                        && ((JFSFileCoordinates) o).location.equals(location);
    }

    @Override
    public int hashCode() {
        return 17 * path.hashCode() * (location.hashCode() + 1);
    }

    @Override
    public String toString() {
        return location + ":" + path;
    }

}

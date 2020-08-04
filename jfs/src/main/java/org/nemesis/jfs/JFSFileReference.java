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
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import static org.nemesis.jfs.JFSUrlStreamHandlerFactory.PROTOCOL;

/**
 * A reference to a JFS file which does not reference the originating JFS, can
 * be serialized and/or resolved against another JFS.
 *
 * @author Tim Boudreau
 */
final class JFSFileReference implements JFSCoordinates.Resolvable {

    private static final long serialVersionUID = -7252390009644279891L;
    private final String urlString;
    private transient String path;
    private transient String location;

    JFSFileReference(String urlString) {
        this.urlString = urlString;
    }

    String fsId() {
        Matcher m = JFSUrlStreamHandlerFactory.URL_PATTERN.matcher(urlString);
        if (!m.find()) {
            return "-unknown-";
        }
        return m.group(1);
    }

    private synchronized void decode() {
        if (path != null || location != null) { // race
            return;
        }
        Matcher m = JFSUrlStreamHandlerFactory.URL_PATTERN.matcher(urlString);
        if (m.find()) {
            String loc = m.group(2);
            String pth = m.group(3);
            if (loc != null) {
                location = loc;
            } else {
                location = "UNKNOWN";
            }
            if (pth != null) {
                path = pth;
            } else {
                path = "";
            }
        } else {
            path = "";
            location = "UNKNOWN";
        }
    }

    /**
     * Convert this to a JFSCoordinates which does not remember the id of the
     * originating JFS.
     *
     * @return A JFSCoordinates
     */
    public JFSCoordinates toCoordinates() {
        return new JFSFileCoordinates(path(), location());
    }

    public UnixPath path() {
        if (path == null) {
            decode();
        }
        return UnixPath.get(path);
    }

    private String locationString() {
        if (location == null) {
            decode();
        }
        return location;
    }

    public boolean isSameCoordinates(JFSCoordinates other) {
        if (other == this) {
            return true;
        }
        if (other instanceof JFSFileReference) {
            JFSFileReference ref = (JFSFileReference) other;
            return locationString().equals(ref.locationString())
                    && path.equals(ref.path);
        }
        return location().equals(other.location()) && path().equals(other.path());
    }

    public Location location() {
        if (location == null) {
            decode();
        }
        if (JFSStorage.MERGED_LOCATION.getName().equals(location)) {
            return JFSStorage.MERGED_LOCATION;
        }
        return StandardLocation.locationFor(location);
    }

    /**
     * Resolve this JFSFileReference against a JFS which may or may not be the
     * same one it was created against, returning a file object if one exists in
     * it that has the same path and location name as this one.
     *
     * @param jfs A JFS
     * @return A file object or null
     */
    public JFSFileObject resolve(JFS jfs) {
        String loc = locationString();
        JFSStorage stor = jfs.storageForLocation(loc);
        return stor.find(Name.forFileName(path), false);
    }

    /**
     * Resolve this JFSFileReference against a JFS which may or may not be the
     * same one it was created against, returning an existing file object if one
     * exists in it that has the same path and location name as this one, and
     * creating a new file object otherwise.
     *
     * @param jfs A JFS
     * @return A file object
     */
    public JFSFileObject resolveOrCreate(JFS jfs) {
        String loc = locationString();
        JFSStorage stor = jfs.storageForLocation(loc);
        return stor.find(Name.forFileName(path), true);
    }

    /**
     * Resolve the file object that originated this file reference, if the
     * originating JFS still exists, and is still open and registered, returning
     * null if not, or if the encoded information is unrecognizable.
     *
     * @return A file object or null
     */
    public JFSFileObject resolveOriginal() {
        JFSUrlStreamHandlerFactory factory = JFSUrlStreamHandlerFactory.getDefault();
        JFSUrlStreamHandler handler = factory.handler(PROTOCOL);
        try {
            return handler.resolve(urlString);
        } catch (FileNotFoundException fnfe) {
            Logger.getLogger(JFSFileReference.class.getName())
                    .log(Level.FINE, "Cannot resolve '" + urlString + "'", fnfe);
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o == null || !(o instanceof JFSFileReference) ? false
                : o == this ? true
                        : urlString.equals(o.toString());
    }

    @Override
    public int hashCode() {
        return urlString.hashCode() * 23;
    }

    @Override
    public String toString() {
        return urlString;
    }
}

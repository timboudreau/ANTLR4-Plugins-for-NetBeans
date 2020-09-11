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

import com.mastfrog.predicates.Predicates;
import com.mastfrog.util.path.UnixPath;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.nemesis.jfs.result.UpToDateness;

/**
 * Allows for tracking changes to files inside a JFS, including mapped files and
 * documents, using both timestamps and SHA-1 hashes for matching; a file is
 * only considered modified if its actual content has changed.
 *
 * @author Tim Boudreau
 */
public class JFSFileModifications {

    private static final Logger LOG = Logger.getLogger(JFSFileModifications.class.getName());
    private static final Predicate<UnixPath> ALL = Predicates.alwaysTrue();
    private final JFS jfs;
    private final Set<? extends Location> locations;
    private Predicate<UnixPath> filter;
    private FilesInfo info;

    JFSFileModifications() {
        // Empty instance, for use when an error was thrown and there
        // is nothing to return
        this.jfs = null;
        this.filter = Predicates.alwaysFalse();
        this.locations = Collections.emptySet();
        this.info = new FilesInfo(Collections.emptyMap(), new byte[0]);
    }

    JFSFileModifications(JFS jfs, Location location, Location... moreLocations) {
        this(jfs, ALL, location, moreLocations);
    }

    JFSFileModifications(JFS jfs, Predicate<UnixPath> filter, Location location, Location... moreLocations) {
        this.filter = notNull("filter", filter);
        this.jfs = jfs;
        locations = locationSet(location, moreLocations);
        info = currentInfo();
    }

    JFSFileModifications(JFS jfs, Set<? extends Location> locations) {
        this(jfs, ALL, locations);
    }

    JFSFileModifications(JFS jfs, Predicate<UnixPath> filter, Set<? extends Location> locations) {
        this.filter = notNull("filter", filter);
        this.locations = locationSet(notNull("locations", locations));
        this.jfs = notNull("jfs", jfs);
        info = currentInfo();
    }

    JFSFileModifications(JFS jfs, JFSFileModifications old) {
        this.jfs = jfs;
        this.filter = old.filter;
        this.locations = old.locations;
        this.info = old.info;
    }

    @SuppressWarnings("unchecked")
    JFSFileModifications(Collection<? extends JFSCoordinates> coords, JFS jfs) {
        this.jfs = notNull("jfs", jfs);
        Set<Location> locs = new HashSet<>();
        Set<UnixPath> paths = new HashSet<>();
        Map<? extends Location, Map<UnixPath, Long>> stamps = new EnumMap<>(StandardLocation.class);
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-1");
            Set<JFSFileObject> sorted = new TreeSet<>();
            for (JFSCoordinates coord : coords) {
                Location loc = coord.location();
                locs.add(loc);
                if (!(loc instanceof StandardLocation) && stamps instanceof EnumMap<?, ?>) {
                    stamps = new HashMap<>(stamps);
                }
                UnixPath path = coord.path();
                paths.add(path);
                Map<Location, Map<UnixPath, Long>> data = (Map<Location, Map<UnixPath, Long>>) stamps;
                Map<UnixPath, Long> curr = data.get(loc);
                if (curr == null) {
                    curr = new HashMap<>();
                    data.put(loc, curr);
                }
                JFSFileObject jfsFo = coord.resolve(jfs);
                if (jfsFo != null) {
                    sorted.add(jfsFo);
                    curr.put(path, jfsFo.getLastModified());
                } else {
                    curr.put(path, 0L);
                }
            }
            this.filter = paths::contains;
            this.locations = locationSet(locs);
            for (JFSFileObject fo : sorted) {
                try {
                    fo.hash(dig);
                } catch (IOException ioe) {
                    LOG.log(Level.INFO, fo.getName(), ioe);
                    dig.update((byte) -1);
                }
            }
            info = new FilesInfo(stamps, dig.digest());
        } catch (NoSuchAlgorithmException err) {
            throw new AssertionError("SHA-1 not supported in this JVM", err);
        }
    }

    JFSFileModifications(JFSFileModifications old, boolean copy) {
        notNull("old", old);
        this.jfs = old.jfs;
        locations = old.locations;
        info = copy ? old.info : currentInfo();
    }

    JFSFileModifications(JFSFileModifications old) {
        this(old, false);
    }

    /**
     * Make a copy of this JFSFileModifications (if necessary) over another
     * JFS instance - useful if unused JFS's are closed and collected but
     * a file modifications instance may persist.
     *
     * @param jfs A JFS
     * @return this if the passed JFS is the same one initially provided,
     * otherwise a new modification set
     */
    public JFSFileModifications overJFS(JFS jfs) {
        if (jfs == this.jfs) {
            return this;
        }
        return new JFSFileModifications(jfs, this);
    }

    /**
     * Create a modification set over a particular JFS, tracking only those
     * coordinates in the passed set.
     *
     * @param jfs A JFS
     * @param coords A set of coordinates
     * @return An empty modifications if the passed set is empty, otherwise
     * one which filters based on membership in the passed set
     */
    public static JFSFileModifications of(JFS jfs, Collection<? extends JFSCoordinates> coords) {
        if (coords.isEmpty() || jfs.isReallyClosed()) {
            return empty();
        }
        return new JFSFileModifications(coords, jfs);
    }

    /**
     * Get the set of JFS coordinates this modification set is tracking (that
     * matched its filter the last time it was updated).
     *
     * @return A collection of coordinates
     */
    public Iterable<? extends JFSCoordinates> allCoordinates() {
        FilesInfo info;
        synchronized (this) {
            info = this.info;
        }
        if (info == null) {
            return Collections.emptySet();
        }
        List<JFSCoordinates> result = new ArrayList<>();
        for (Map.Entry<? extends Location, Map<UnixPath, Long>> e : info.timestamps.entrySet()) {
            for (Map.Entry<UnixPath, Long> e1 : e.getValue().entrySet()) {
                UnixPath path = e1.getKey();
                if (filter == null || filter.test(path)) {
                    JFSCoordinates nue = new JFSFileCoordinates(path, e.getKey());
                    result.add(nue);
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "JFSFileModifications(" + (jfs == null ? "-none-" : jfs.id()) + " " + info + ")";
    }

    /**
     * Create a copy of this modifications which ignores a particular file.
     *
     * @param path A path
     * @return A new JFSFileModifications
     */
    public JFSFileModifications excluding(String path) {
        return excluding(UnixPath.get(path));
    }

    /**
     * Create a copy of this modifications which ignores a particular file.
     *
     * @param path A path
     * @return A new JFSFileModifications
     */
    public JFSFileModifications excluding(UnixPath path) {
        Predicate<UnixPath> newFilter = filter.and(pth -> !path.equals(pth));
        return new JFSFileModifications(jfs, newFilter, locations);
    }

    /**
     * Create a copy of this JFSFileModifications whose reset status is
     * independent of the original.
     *
     * @return A set of modifications
     */
    public JFSFileModifications snapshot() {
        return new JFSFileModifications(this, true);
    }

    /**
     * Create a new JFSFileModifications which uses the same filter as
     * this one, which will only show modifications if the filesystem
     * is modified subsequent to this call.
     *
     * @return A JFSFileModifications
     */
    public JFSFileModifications withUpdatedState() {
        return new JFSFileModifications(this, false);
    }

    /**
     * Create an empty null-instance.
     *
     * @return A JFSFileModifications
     */
    public static JFSFileModifications empty() {
        return new JFSFileModifications();
    }

    /**
     * Determine if this instance is not keeping track of any files (it was
     * created by empty(), or the backing JFS is empty).
     *
     * @return True if nothing is being tracked currently
     */
    public boolean isEmpty() {
        if (jfs == null) {
            return true; // empty instance
        }
        FilesInfo ifo = info;
        return ifo == null ? true : ifo.timestamps.isEmpty();
    }

    FilesInfo initialState() {
        return info;
    }

    public JFSFileModifications toNew() {
        if (jfs == null) {
            return this;
        }
        return new JFSFileModifications(this);
    }

    /**
     * Get the current set of changes, compared with the initial or last-reset
     * state.
     *
     * @return A set of file changes
     */
    public FileChanges changes() {
        if (jfs == null) {
            return FileChanges.UNKNOWN;
        }
        return FileChanges.create(this);
    }

    /**
     * Get the set of file changes based on what this JFSFileModifications is
     * tracking, potentially querying a different JFS than the one this instance
     * was created against.
     *
     * @param over Another (or the same) JFS
     * @return A set of changes
     */
    public FileChanges changes(JFS over) {
        if (jfs == null && over == null) {
            return FileChanges.UNKNOWN;
        }
        if (over == this.jfs) {
            return changes();
        }
        return overJFS(over).changes();
    }

    /**
     * Reset the state of changed-ness of the files, such that subsequent calls
     * to get the status will show no changes.
     *
     * @return this
     */
    public JFSFileModifications refresh() {
        if (jfs == null) {
            return this;
        }
        setInfo(currentInfo());
        return this;
    }

    void setInfo(FilesInfo m) {
        info = m;
    }

    /**
     * Get the current set of file changes, and reset the state to be
     * unmodified.
     *
     * @return A set of file changes
     */
    public synchronized FileChanges changesAndReset() {
        if (jfs == null) {
            return FileChanges.UNKNOWN;
        }
        FileChanges result = FileChanges.create(this);
        refresh();
        return result;
    }

    private static Set<? extends Location> locationSet(Set<? extends Location> locations) {
        EnumSet<StandardLocation> es = EnumSet.noneOf(StandardLocation.class);
        boolean usable = true;
        for (Location l : locations) {
            if (l instanceof StandardLocation) {
                es.add((StandardLocation) l);
            } else {
                usable = false;
                break;
            }
        }
        if (!usable) {
            return locations;
        }
        return es;
    }

    /**
     * A set of changes to files being tracked by a JFSFileModifications.
     */
    public static abstract class FileChanges {

        private static final FileChanges EMPTY = new EmptyFileChanges(false);
        private static final FileChanges UNKNOWN = new EmptyFileChanges(true);

        public static FileChanges forNoFiles() {
            return UNKNOWN;
        }

        static FileChanges create(JFSFileModifications status) {
            FilesInfo initial;
            FilesInfo current;
            synchronized (status) {
                initial = status.initialState();
                current = status.currentInfo();
            }
            if (initial.timestamps.equals(current.timestamps)) {
                return EMPTY;
            }
            if (Arrays.equals(initial.hash, current.hash)) {
                return EMPTY;
            }
            return new Modifications(initial, current, status.filter);
        }

        public abstract FileChanges filter(Predicate<UnixPath> filter);

        public abstract UpToDateness status();

        private static final class EmptyFileChanges extends FileChanges {

            private final boolean unknown;

            public EmptyFileChanges(boolean unknown) {
                this.unknown = unknown;
            }

            @Override
            public Set<? extends UnixPath> modified() {
                return Collections.emptySet();
            }

            @Override
            public Set<? extends UnixPath> deleted() {
                return Collections.emptySet();
            }

            @Override
            public Set<? extends UnixPath> added() {
                return Collections.emptySet();
            }

            @Override
            public UpToDateness status() {
                return unknown ? UpToDateness.UNKNOWN : UpToDateness.CURRENT;
            }

            @Override
            public String toString() {
                return "no-changes";
            }

            @Override
            public FileChanges filter(Predicate<UnixPath> filter) {
                return this;
            }

            @Override
            public boolean isCreatedOrModified(UnixPath path) {
                return false;
            }
        }

        public abstract boolean isCreatedOrModified(UnixPath path);

        public abstract Set<? extends UnixPath> modified();

        public abstract Set<? extends UnixPath> deleted();

        public abstract Set<? extends UnixPath> added();

        public boolean existingFilesChanged() {
            return modified().isEmpty() && deleted().isEmpty();
        }

        public boolean isUpToDate() {
            return modified().isEmpty() && deleted().isEmpty() && added().isEmpty();
        }

        static final class Modifications extends FileChanges {

            private final Set<UnixPath> modified;
            private final Set<UnixPath> deleted;
            private final Set<UnixPath> added;
            private final Predicate<UnixPath> filter;

            Modifications(Set<UnixPath> modified, Set<UnixPath> deleted, Set<UnixPath> added, Predicate<UnixPath> filter) {
                notNull("filter", filter);
                notNull("modified", modified);
                notNull("added", added);
                notNull("deleted", deleted);
                this.modified = modified;
                this.deleted = deleted;
                this.added = added;
                this.filter = filter;
            }

            Modifications(FilesInfo orig, FilesInfo nue, Predicate<UnixPath> filter) {
                this.filter = filter;
                modified = new HashSet<>(7);
                deleted = new HashSet<>(7);
                added = new HashSet<>(7);
                // Technically if the same file exists in two locations,
                // we could record it twice, but we will still have accurate
                // enough information to make a go/rebuild decision
                for (Map.Entry<? extends Location, Map<UnixPath, Long>> e : orig.timestamps.entrySet()) {
                    Location loc = e.getKey();
                    Map<UnixPath, Long> origs = orig.timestamps.get(loc);
                    Map<UnixPath, Long> updates = nue.timestamps.get(loc);
                    for (Map.Entry<UnixPath, Long> ee : origs.entrySet()) {
                        if (filter != null && !filter.test(ee.getKey())) {
                            continue;
                        }
                        Long newModified = updates.get(ee.getKey());
                        if (newModified == null) {
                            deleted.add(ee.getKey());
                        } else if ((long) newModified != (long) ee.getValue()) {
                            modified.add(ee.getKey());
                        }
                    }
                    added.addAll(updates.keySet());
                    added.removeAll(origs.keySet());
                }
            }

            @Override
            public boolean isCreatedOrModified(UnixPath path) {
                return modified.contains(path) || added.contains(path);
            }

            @Override
            public UpToDateness status() {
                if (modified.isEmpty() && deleted.isEmpty()) {
                    return UpToDateness.CURRENT;
                } else if (modified.isEmpty()) {
                    return UpToDateness.UNKNOWN;
                } else {
                    return UpToDateness.staleStatus().addAll(modified).build();
                }
            }

            @Override
            public Set<? extends UnixPath> modified() {
                return modified;
            }

            @Override
            public Set<? extends UnixPath> deleted() {
                return deleted;
            }

            @Override
            public Set<? extends UnixPath> added() {
                return added;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                if (!added.isEmpty()) {
                    sb.append("ADDED: ").append(Strings.join(',', added));
                }
                if (!modified.isEmpty()) {
                    sb.append("MODIFIED: ").append(Strings.join(',', modified));
                }
                if (!deleted.isEmpty()) {
                    sb.append("DELETED: ").append(Strings.join(',', deleted));
                }
                return sb.toString();
            }

            @Override
            public FileChanges filter(Predicate<UnixPath> filter) {
                Set<Path> newModified = new HashSet<>(7);
                Set<Path> newDeleted = new HashSet<>(7);
                Set<Path> newAdded = new HashSet<>(7);
                added.stream().filter(filter).forEach(newAdded::add);
                deleted.stream().filter(filter).forEach(newDeleted::add);
                modified.stream().filter(filter).forEach(newModified::add);
                if (newAdded.isEmpty() && newDeleted.isEmpty() && newModified.isEmpty()) {
                    return EMPTY;
                }
                return new Modifications(modified, deleted, added, filter.and(this.filter));
            }
        }
    }

    private FilesInfo currentInfo() {
        boolean closed = jfs.isReallyClosed();
        boolean empty = jfs.isEmpty();
        boolean jfsDead = closed || empty;

        Map<? extends Location, Map<UnixPath, Long>> timestamps = locationMap();
        List<JFSFileObject> files = new ArrayList<>();
        for (Location loc : locations) {
            Map<UnixPath, Long> itemsForLocation = timestamps.get(loc);
            if (!jfsDead) {
                jfs.list(loc, (location, fo) -> {
                    try {
                        UnixPath path = UnixPath.get(fo.getName());
                        if (filter == null || filter.test(path)) {
                            itemsForLocation.put(path, fo.getLastModified());
                            files.add(fo);
                        }
                    } catch (Exception ex) {
                        LOG.log(Level.SEVERE, "Exception indexing " + jfs + " on " + fo, ex);
                    }
                });
            }
        }
        files.sort((a, b) -> {
            // XXX if the same file path exists in more than one location,
            // this will have non-deterministic results
            return a.path().compareTo(b.path());
        });
        byte[] hash;
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-1");
            for (JFSFileObject fo : files) {
                try {
                    fo.hash(dig);
                } catch (MappedObjectDeletedException ex) {
                    LOG.log(Level.FINE, "Deleted: " + fo, ex);
                    fo.delete();
                }
            }
            hash = dig.digest();
        } catch (NoSuchAlgorithmException | IOException ex) {
            return Exceptions.chuck(ex);
        }
        return new FilesInfo(timestamps, hash);
    }

    /**
     * Get the last-modified date of a file at the time this modification set
     * was created or last-updated.
     *
     * @param coords A set of coordinates
     * @return a Long or null if unrecorded
     */
    public Long lastModifiedOf(JFSCoordinates coords) {
        FilesInfo info;
        synchronized(this) {
            info = this.info;
        }
        return info == null ? null : info.lastModifiedOf(
                notNull("coords", coords));
    }

    private <R> Map<? extends Location, Map<UnixPath, R>> locationMap() {
        // Comparing a hash lookup to a bitwise operation in an
        // EnumSet or EnumMap, this optimization is worth it
        if (locations instanceof EnumSet<?>) {
            Map<StandardLocation, Map<UnixPath, R>> result = new EnumMap<>(StandardLocation.class);
            for (Location l : locations) {
                Map<UnixPath, R> m = new HashMap<>(20);
                result.put((StandardLocation) l, m);
            }
            return result;
        }
        Map<Location, Map<UnixPath, R>> result = new HashMap<>();
        for (Location l : locations) {
            result.put(l, new HashMap<>(20));
        }
        return result;
    }

    private static Set<? extends Location> locationSet(Location first, Location... more) {
        if (first instanceof StandardLocation) {
            Set<StandardLocation> s = EnumSet.of((StandardLocation) first);
            boolean allEnums = true;
            for (Location l : more) {
                if (l instanceof StandardLocation) {
                    s.add((StandardLocation) l);
                } else {
                    allEnums = false;
                    break;
                }
            }
            if (allEnums) {
                return s;
            }
        }
        Set<Location> result = new HashSet<>();
        result.add(notNull("first", first));
        result.addAll(Arrays.asList(more));
        return result;
    }

    static final class FilesInfo {

        final Map<? extends Location, Map<UnixPath, Long>> timestamps;
        final byte[] hash;

        public FilesInfo(Map<? extends Location, Map<UnixPath, Long>> timestamps, byte[] hash) {
            this.timestamps = timestamps;
            this.hash = hash;
        }

        Long lastModifiedOf(JFSCoordinates coords) {
            Map<UnixPath, Long> stamps = timestamps.get(coords.location());
            if (stamps != null) {
                return stamps.get(coords.path());
            }
            return null;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("FilesInfo(").append(timestamps.keySet()).append(": ").append(Strings.toPaddedHex(hash)).append(": {");
            for (Map.Entry<? extends Location, Map<UnixPath, Long>> e : timestamps.entrySet()) {
                for (Map.Entry<UnixPath, Long> e1 : e.getValue().entrySet()) {
                    result.append(e1.getKey()).append(',');
                }
            }
            return result.append("})").toString();
        }
    }
}

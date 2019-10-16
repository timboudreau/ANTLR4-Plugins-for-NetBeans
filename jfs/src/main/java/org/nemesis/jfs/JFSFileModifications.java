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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import org.nemesis.jfs.result.UpToDateness;

/**
 *
 * @author Tim Boudreau
 */
public class JFSFileModifications {

    private final JFS jfs;
    private Predicate<UnixPath> filter;
    private final Set<? extends Location> locations;
    private static final Logger LOG = Logger.getLogger(JFSFileModifications.class.getName());
    private FilesInfo info;
    private static final Predicate<UnixPath> ALL = Predicates.alwaysTrue();

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

    JFSFileModifications(JFS jfs, Set<Location> locations) {
        this(jfs, ALL, locations);
    }

    JFSFileModifications(JFS jfs, Predicate<UnixPath> filter, Set<Location> locations) {
        this.filter = notNull("filter", filter);
        this.locations = locationSet(notNull("locations", locations));
        this.jfs = notNull("jfs", jfs);
        info = currentInfo();
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

    public JFSFileModifications snapshot() {
        return new JFSFileModifications(this, true);
    }

    public static JFSFileModifications empty() {
        return new JFSFileModifications();
    }

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

    public FileChanges changes() {
        if (jfs == null) {
            return FileChanges.UNKNOWN;
        }
        return FileChanges.create(this);
    }

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

    public synchronized FileChanges changesAndReset() {
        if (jfs == null) {
            return FileChanges.UNKNOWN;
        }
        FileChanges result = FileChanges.create(this);
        refresh();
        return result;
    }

    private static Set<? extends Location> locationSet(Set<Location> locations) {
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
        }

        public abstract Set<? extends UnixPath> modified();

        public abstract Set<? extends UnixPath> deleted();

        public abstract Set<? extends UnixPath> added();

        public boolean isUpToDate() {
            return modified().isEmpty() && deleted().isEmpty();
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
        Map<? extends Location, Map<UnixPath, Long>> timestamps = locationMap();
        List<JFSFileObject> files = new ArrayList<>();
        for (Location loc : locations) {
            Map<UnixPath, Long> itemsForLocation = timestamps.get(loc);
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
        files.sort((a, b) -> {
            return a.getName().compareTo(b.getName());
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
    }
}

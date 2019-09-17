package org.nemesis.jfs;

import com.mastfrog.predicates.Predicates;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
    private Predicate<Path> filter;
    private final Set<? extends Location> locations;
    private static final Logger LOG = Logger.getLogger(JFSFileModifications.class.getName());
    private Map<? extends Location, Map<Path, Long>> info;
    private static final Predicate<Path> ALL = Predicates.alwaysTrue();

    JFSFileModifications() {
        // Empty instance, for use when an error was thrown and there
        // is nothing to return
        this.jfs = null;
        this.filter = Predicates.alwaysFalse();
        this.locations = Collections.emptySet();
        this.info = Collections.emptyMap();
    }

    JFSFileModifications(JFS jfs, Location location, Location... moreLocations) {
        this(jfs, ALL, location, moreLocations);
    }

    JFSFileModifications(JFS jfs, Predicate<Path> filter, Location location, Location... moreLocations) {
        this.filter = notNull("filter", filter);
        this.jfs = jfs;
        locations = locationSet(location, moreLocations);
        info = currentInfo();
    }

    JFSFileModifications(JFS jfs, Set<Location> locations) {
        this(jfs, ALL, locations);
    }

    JFSFileModifications(JFS jfs, Predicate<Path> filter, Set<Location> locations) {
        this.filter = notNull("filter", filter);
        this.locations = locationSet(notNull("locations", locations));
        this.jfs = notNull("jfs", jfs);
        info = currentInfo();
    }

    JFSFileModifications(JFSFileModifications old) {
        notNull("old", old);
        this.jfs = old.jfs;
        locations = old.locations;
        info = currentInfo();
    }

    public static JFSFileModifications empty() {
        return new JFSFileModifications();
    }

    Map<? extends Location, Map<Path, Long>> initialState() {
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

    void setInfo(Map<? extends Location, Map<Path, Long>> m) {
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
            Map<? extends Location, Map<Path, Long>> initial;
            Map<? extends Location, Map<Path, Long>> current;
            synchronized (status) {
                initial = status.initialState();
                current = status.currentInfo();
            }
            if (initial.equals(current)) {
                return EMPTY;
            }
            return new Modifications(initial, current, status.filter);
        }

        public abstract FileChanges filter(Predicate<Path> filter);

        public abstract UpToDateness status();

        private static final class EmptyFileChanges extends FileChanges {
            private final boolean unknown;

            public EmptyFileChanges(boolean unknown) {
                this.unknown = unknown;
            }

            @Override
            public Set<? extends Path> modified() {
                return Collections.emptySet();
            }

            @Override
            public Set<? extends Path> deleted() {
                return Collections.emptySet();
            }

            @Override
            public Set<? extends Path> added() {
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
            public FileChanges filter(Predicate<Path> filter) {
                return this;
            }
        }

        public abstract Set<? extends Path> modified();

        public abstract Set<? extends Path> deleted();

        public abstract Set<? extends Path> added();

        public boolean isUpToDate() {
            return modified().isEmpty() && deleted().isEmpty();
        }

        static final class Modifications extends FileChanges {

            private final Set<Path> modified;
            private final Set<Path> deleted;
            private final Set<Path> added;
            private final Predicate<Path> filter;

            Modifications(Set<Path> modified, Set<Path> deleted, Set<Path> added, Predicate<Path> filter) {
                notNull("filter", filter);
                notNull("modified", modified);
                notNull("added", added);
                notNull("deleted", deleted);
                this.modified = modified;
                this.deleted = deleted;
                this.added = added;
                this.filter = filter;
            }

            Modifications(Map<? extends Location, Map<Path, Long>> orig, Map<? extends Location, Map<Path, Long>> nue, Predicate<Path> filter) {
                this.filter = filter;
                modified = new HashSet<>(7);
                deleted = new HashSet<>(7);
                added = new HashSet<>(7);
                // Technically if the same file exists in two locations,
                // we could record it twice, but we will still have accurate
                // enough information to make a go/rebuild decision
                for (Map.Entry<? extends Location, Map<Path, Long>> e : orig.entrySet()) {
                    Location loc = e.getKey();
                    Map<Path, Long> origs = orig.get(loc);
                    Map<Path, Long> updates = nue.get(loc);
                    for (Map.Entry<Path, Long> ee : origs.entrySet()) {
                        if (!filter.test(ee.getKey())) {
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
            public Set<? extends Path> modified() {
                return modified;
            }

            @Override
            public Set<? extends Path> deleted() {
                return deleted;
            }

            @Override
            public Set<? extends Path> added() {
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
            public FileChanges filter(Predicate<Path> filter) {
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

    private Map<? extends Location, Map<Path, Long>> currentInfo() {
        Map<? extends Location, Map<Path, Long>> items = locationMap();
        for (Location loc : locations) {
            Map<Path, Long> itemsForLocation = items.get(loc);
            jfs.list(loc, (location, fo) -> {
                try {
                    Path path = Paths.get(fo.getName());
                    if (filter.test(path)) {
                        itemsForLocation.put(path, fo.getLastModified());
                    }
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, "Exception indexing " + jfs + " on " + fo, ex);
                }
            });
        }
        return items;
    }

    private Map<? extends Location, Map<Path, Long>> locationMap() {
        // Comparing a hash lookup to a bitwise operation in an
        // EnumSet or EnumMap, this optimization is worth it
        if (locations instanceof EnumSet<?>) {
            Map<StandardLocation, Map<Path, Long>> result = new EnumMap<>(StandardLocation.class);
            for (Location l : locations) {
                Map<Path, Long> m = new HashMap<>(20);
                result.put((StandardLocation) l, m);
            }
            return result;
        }
        Map<Location, Map<Path, Long>> result = new HashMap<>();
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
}

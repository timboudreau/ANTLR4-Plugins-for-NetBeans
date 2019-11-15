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
package org.nemesis.antlr.project.extensions;

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.common.ShutdownHooks;
import org.nemesis.antlr.common.cachefile.CacheFileUtils;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.nemesis.antlr.projectupdatenotificaton.UpToDateness;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
class KnownAntlrProjectCache implements Runnable {

    private static volatile KnownAntlrProjectCache INSTANCE;
    private final Path file;
    private final Map<Path, TimestampStatus> values = new ConcurrentHashMap<>();
    private boolean needSave;
    private final RequestProcessor.Task saveTask = RequestProcessor.getDefault().create(this);
    private static final int DELAY = 20000;
    private static final String CACHE_FILE_NAME = "known-antlr-projects.cache";
    private static final Logger LOG = Logger.getLogger(KnownAntlrProjectCache.class.getName());

    private static int MAGIC = 7203;

    KnownAntlrProjectCache() {
        this(Places.getCacheSubfile(CACHE_FILE_NAME).toPath(), true);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    KnownAntlrProjectCache(Path file, boolean load) {
        this.file = file;
        if (load && Files.exists(file)) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                if (channel.size() > 0) {
                    ByteBuffer numBuf = ByteBuffer.allocate(Integer.BYTES);
                    int magic = CacheFileUtils.readInt(channel, numBuf);
                    if (magic != MAGIC) {
                        throw new IOException("Wrong magic number " + Integer.toHexString(magic) + " expected " + Integer.toHexString(MAGIC));
                    }
                    int recordSize = CacheFileUtils.readInt(channel, numBuf);
                    if (recordSize < 0 || recordSize > channel.size()) {
                        throw new IOException("Invalid record size " + recordSize
                                + " in file of " + channel.size()
                                + " at " + (channel.position() - Integer.BYTES));
                    }
                    ByteBuffer buf = ByteBuffer.allocate(recordSize);
                    int amtRead = channel.read(buf);
                    if (amtRead != recordSize) {
                        throw new IOException("Should have read " + recordSize
                                + " but got " + amtRead);
                    }
                    int count = buf.getInt();
                    if (count < 0 || count > 65536) {
                        throw new IOException("Impossible number of cache entries: "
                                + count + " in " + file);
                    }
                    for (int i = 0; i < count; i++) {
                        Path path = CacheFileUtils.readPath(buf);
                        TimestampStatus status = TimestampStatus.load(buf);
                        if (Files.exists(path)) {
                            System.out.println("  load known project " + path + " " + status);
                            values.put(path, status);
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.log(Level.INFO, "Exception loading cache file " + file + ". Deleting.", ex);
                try {
                    FileUtils.deleteIfExists(file);
                } catch (IOException ex1) {
                    LOG.log(Level.SEVERE, "Exception deleting cache file " + file + ".", ex1);
                }
            }
        }
        if (file != null && !Boolean.getBoolean("unit.test")) {
            ShutdownHooks.addRunnable(saveTask::cancel);
            ShutdownHooks.addWeakRunnable(this);
            if (needSave) {
                dirty();
            }
        }
    }

    private void dirty() {
        if (file != null) {
            saveTask.schedule(DELAY);
        }
    }

    static KnownAntlrProjectCache getInstance() {
        KnownAntlrProjectCache localInstance = KnownAntlrProjectCache.INSTANCE;
        if (localInstance == null) {
            synchronized (KnownAntlrProjectCache.class) {
                localInstance = KnownAntlrProjectCache.INSTANCE;
                if (localInstance == null) {
                    KnownAntlrProjectCache.INSTANCE = localInstance = new KnownAntlrProjectCache(
                            Places.getCacheSubfile(CACHE_FILE_NAME).toPath(), true);
                }
            }
        }
        return localInstance;
    }

    public boolean isAntlrProject(Path path) {
        return isAntlrProject(path, () -> {
            return AntlrConfiguration.forFile(path) != null;
        });
    }

    public boolean isAntlrProject(Path path, Callable<Boolean> supp) {
        assert Files.isDirectory(path) : "Not a dir: " + path;
        TimestampStatus ts = values.getOrDefault(path, DEFAULT_UNKNOWN);
        try {
            if (Files.exists(path)) {
                if (ts.isUnknown() || ts.upToDateness.isChanged()) {
                    Boolean resolvedValue = supp.call();
                    if (resolvedValue == null) {
                        return false;
                    }
                    TimestampStatus newStatus
                            = new TimestampStatus(Status.valueOf(resolvedValue),
                                    ts.upToDateness == null
                                            ? UpToDateness.forProject(path)
                                            : ts.upToDateness);

                    if (!newStatus.equals(ts)) {
                        values.put(path, newStatus);
                        dirty();
                    }
                    return newStatus.status == Status.YES;
                }
            } else {
                if (values.containsKey(path)) {
                    dirty();
                }
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return false;
    }

    @Override
    public void run() {
        try (FileChannel channel = FileChannel.open(file, CREATE,
                TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer numBuf = ByteBuffer.allocate(Integer.BYTES);
            CacheFileUtils.writeNumber(MAGIC, channel, numBuf);
            long sizepos = channel.position();
            CacheFileUtils.writeNumber(0, channel, numBuf);
            Map<Path, TimestampStatus> snapshot = new HashMap<>(this.values);
            Set<Path> paths = new TreeSet<>(snapshot.keySet());
            // Prune dead files and unknown statii
            for (Iterator<Path> it = paths.iterator(); it.hasNext();) {
                Path p = it.next();
                if (!Files.exists(p)) {
                    it.remove();
                    continue;
                }
                TimestampStatus ts = snapshot.get(p);
                if (ts.isUnknown()) {
                    it.remove();
                }
            }
            int written = CacheFileUtils.writeNumber(snapshot.size(), channel, numBuf);
            for (Path toSave : paths) {
                written += CacheFileUtils.writePath(toSave, channel);
                TimestampStatus status = snapshot.get(toSave);
                written += status.save(channel);
            }
            long currentPos = channel.position();
            try {
                channel.position(sizepos);
                CacheFileUtils.writeNumber(written, channel, numBuf);
            } finally {
                channel.position(currentPos);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    static TimestampStatus DEFAULT_UNKNOWN = new TimestampStatus(Status.UNKNOWN, null);

    static class TimestampStatus {

        private final Status status;
        private final UpToDateness upToDateness;

        public TimestampStatus(Status status, UpToDateness upToDateness) {
            this.status = status;
            this.upToDateness = upToDateness;
        }

        boolean isUnknown() {
            return status.isUnknown();
        }

        public static TimestampStatus load(ByteBuffer buf) throws IOException {
            Status status = Status.read(buf);
            UpToDateness dt = UpToDateness.load(buf);
            return new TimestampStatus(status, dt);
        }

        public int save(FileChannel channel) throws IOException {
            return status.write(channel)
                    + upToDateness.store(channel);
        }

        @Override
        public String toString() {
            return status + ":" + upToDateness;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + Objects.hashCode(this.status);
            hash = 59 * hash + Objects.hashCode(this.upToDateness);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TimestampStatus other = (TimestampStatus) obj;
            if (this.status != other.status) {
                return false;
            }
            return Objects.equals(this.upToDateness,
                    other.upToDateness);
        }

    }

    enum Status {
        YES,
        NO,
        UNKNOWN;

        int write(FileChannel channel) throws IOException {
            ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
            buf.putInt(ordinal());
            buf.flip();
            channel.write(buf);
            return buf.capacity();
        }

        static Status read(ByteBuffer buf) throws IOException {
            return Status.values()[buf.getInt()];
        }

        static Status valueOf(Boolean val) {
            if (val == null) {
                return UNKNOWN;
            } else if (val) {
                return YES;
            } else {
                return NO;
            }
        }

        public String toString() {
            switch (this) {
                case NO:
                    return "n";
                case YES:
                    return "y";
                default:
                    return "u";
            }
        }

        static Status fromString(String val) {
            switch (val) {
                case "y":
                    return YES;
                case "n":
                    return NO;
                case "u":
                default:
                    return UNKNOWN;
            }
        }

        boolean isUnknown() {
            return this == UNKNOWN;
        }
    }
}

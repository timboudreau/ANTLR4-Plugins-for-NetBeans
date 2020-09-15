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
package org.nemesis.antlr.projectupdatenotificaton;

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.nemesis.antlr.common.cachefile.CacheFileUtils.readString;
import static org.nemesis.antlr.common.cachefile.CacheFileUtils.writePath;
import static org.nemesis.antlr.common.cachefile.CacheFileUtils.writeNumber;
import static org.nemesis.antlr.common.cachefile.CacheFileUtils.readInt;

/**
 * Allows for querying if a pom file or one of its known dependencies has been
 * changed since the last time it was queried.
 *
 * @author Tim Boudreau
 */
final class ProjectUpdateStatus implements UpToDateness {

    private static final Logger LOG
            = Logger.getLogger(ProjectUpdateStatus.class.getName());
    private final Path projectDir;
    private long greatestTimestampSeenOnLastRefresh;
    private int lastPathCount;
    private static int MAGIC = 72031;

    @SuppressWarnings("LeakingThisInConstructor")
    private ProjectUpdateStatus(Path projectDir, long lastCheckTimestamp, int lastPathCount) {
        this.lastPathCount = lastPathCount;
        this.greatestTimestampSeenOnLastRefresh = lastCheckTimestamp;
        this.projectDir = projectDir;
    }

    ProjectUpdateStatus(Path projectDir) {
        this.projectDir = notNull("projectDir", projectDir);
    }

    public String toString() {
        return projectDir + ":" + lastPathCount + ":" + new Date(greatestTimestampSeenOnLastRefresh);
    }

    // test methods
    int lastPathCount() {
        return lastPathCount;
    }

    long lastCheckTimestamp() {
        return greatestTimestampSeenOnLastRefresh;
    }

    @Override
    public Path path() {
        return projectDir;
    }

    boolean isEqual(ProjectUpdateStatus status) {
        if (status == this) {
            return true;
        }
        return greatestTimestampSeenOnLastRefresh
                == status.greatestTimestampSeenOnLastRefresh
                && status.lastPathCount == lastPathCount
                && projectDir.equals(status.projectDir);
    }

    public void refresh() {
        Set<Path> toNotify;
        synchronized (this) {
            toNotify = _refresh();
        }
        for (Path p : toNotify) {
            ProjectUpdates.notifyPathChanged(p);
        }
    }

    private Set<Path> _refresh() {
        Set<Path> paths = ProjectUpdates.dependersOn(projectDir);
        Iterable<Path> allPaths = CollectionUtils.concatenate(paths,
                Collections.singleton(projectDir));
        return _refresh(allPaths);
    }

    private Set<Path> _refresh(Iterable<Path> paths) {
        long result = 0;
        int size = 0;
        Set<Path> test = new HashSet<>(3);
        Set<Path> toNotify = new HashSet<>(3);
        for (Path p : paths) {
            if (!Files.exists(p)) {
                ProjectUpdates.fileDeleted(p);
                continue;
            }
            if (Files.isDirectory(p)) {
                PotentialBuildFileFinder.findPossibleBuildFiles(p, test);
                if (!test.isEmpty()) {
                    for (Path buildFile : test) {
                        try {
                            long mod = Files.getLastModifiedTime(buildFile).toMillis();
                            if (mod > greatestTimestampSeenOnLastRefresh) {
                                toNotify.add(p);
                            }
                            result = Math.max(result, mod);
                            size++;
                        } catch (IOException ex) {
                            LOG.log(Level.INFO, "Error checking " + buildFile, ex);
                        }
                    }
                    test.clear();
                }
            }
        }
        greatestTimestampSeenOnLastRefresh = result;
        lastPathCount = size;
        return toNotify;
    }

    private long lastCheck;
    private static final int THROTTLE_MS = 250;

    @Override
    public boolean isChanged() {
        long oldTimestamp, newTimestamp;
        int oldSize, newSize;
        Set<Path> toNotify = null;
        synchronized (this) {
            oldTimestamp = greatestTimestampSeenOnLastRefresh;
            oldSize = lastPathCount;
            // A little throttling for very frequent tests, since
            // particularly on Windows, file date checks are expensive
            if (System.currentTimeMillis() - lastCheck > THROTTLE_MS) {
                toNotify = _refresh();
                newTimestamp = greatestTimestampSeenOnLastRefresh;
                newSize = lastPathCount;
            } else {
                newTimestamp = oldTimestamp;
                newSize = oldSize;
            }
        }
        boolean result = oldTimestamp != newTimestamp || oldSize != newSize;
        boolean isInitial = oldTimestamp == 0 || oldSize == 0;
        if (toNotify != null && !isInitial) {
            for (Path p : toNotify) {
                ProjectUpdates.notifyPathChanged(p);
            }
        }
        lastCheck = System.currentTimeMillis();
        return result;
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> ProjectUpdateStatus load(T channel) throws IOException {
        ByteBuffer numberBuf = ByteBuffer.allocate(Long.BYTES);
        int magic = readInt(channel, numberBuf);
        if (magic != MAGIC) {
            throw new IOException("Wrong magic number " + magic + " should be " + MAGIC);
        }
        int length = readInt(channel, numberBuf);
        if (length < 0 || length > 1536) {
            throw new IOException("Absurd path length " + length
                    + " at " + (channel.position() - Integer.BYTES) + " in " + channel);
        }
        ByteBuffer data = ByteBuffer.allocate(length);
        int readLength = channel.read(data);
        if (readLength != length) {
            throw new IOException("Read wrong number of bytes " + readLength + " expected "
                    + length);
        }
        data.flip();
        int pathCount = data.getInt();
        if (pathCount < 0 || pathCount > 512) {
            throw new IOException("Absurd path count " + pathCount);
        }
        long lastCheckTimestamp = data.getLong();
        Path base = Paths.get(readString(data));
        ProjectUpdateStatus result = new ProjectUpdateStatus(base, lastCheckTimestamp, pathCount);
        return result;
    }

    public static <T extends WritableByteChannel & SeekableByteChannel> ProjectUpdateStatus load(ByteBuffer data) throws IOException {
        int magic = data.getInt();
        if (magic != MAGIC) {
            throw new IOException("Wrong magic number " + magic + " should be " + MAGIC);
        }
        int length = data.getInt();
        if (length < 0 || length > 1536) {
            throw new IOException("Absurd path length " + length);
        }
        int pathCount = data.getInt();
        if (pathCount < 0 || pathCount > 512) {
            throw new IOException("Absurd path count " + pathCount);
        }
        long lastCheckTimestamp = data.getLong();
        Path base = Paths.get(readString(data));
        ProjectUpdateStatus result = new ProjectUpdateStatus(base, lastCheckTimestamp, pathCount);
        return result;
    }

    public <T extends WritableByteChannel & SeekableByteChannel> int store(T channel) throws IOException {
        long timestamp;
        int pathCount;
        synchronized (this) {
            timestamp = this.greatestTimestampSeenOnLastRefresh;
            pathCount = lastPathCount;
        }
        ByteBuffer numberBuf = ByteBuffer.allocate(Long.BYTES);

        writeNumber(MAGIC, channel, numberBuf);

        long lengthPosition = channel.position();

        // write a placeholder length
        writeNumber(0, channel, numberBuf);
        int written = writeNumber(pathCount, channel, numberBuf);
        written += writeNumber(timestamp, channel, numberBuf);
        written += writePath(projectDir, channel);
        long currentPos = channel.position();
        try {
            channel.position(lengthPosition);
            writeNumber(written, channel, numberBuf);
        } finally {
            channel.position(currentPos);
        }
        return written + (Integer.BYTES * 2);
    }

}

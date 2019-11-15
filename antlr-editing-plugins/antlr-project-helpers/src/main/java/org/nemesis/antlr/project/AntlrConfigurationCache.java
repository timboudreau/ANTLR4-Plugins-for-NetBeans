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
package org.nemesis.antlr.project;

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.common.ShutdownHooks;
import org.nemesis.antlr.common.cachefile.CacheFileUtils;
import org.nemesis.antlr.projectupdatenotificaton.UpToDateness;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrConfigurationCache {

    static volatile AntlrConfigurationCache INSTANCE; // pkg private for tests
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final int MAGIC = 8302718;
    private static final int DELAY = 16000;
    private final Path file;
    private final Saver saver = new Saver();
    private final RequestProcessor.Task task = RequestProcessor.getDefault().create(saver);
    private static final Logger LOG = Logger.getLogger(AntlrConfigurationCache.class.getName());

    AntlrConfigurationCache(Path file, boolean load) throws IOException {
        this.file = file;
        boolean dirty = false;
        if (load && file != null && Files.exists(file)) {
            try (FileChannel ch = FileChannel.open(file, READ)) {
                if (ch.size() > 0) {
                    ByteBuffer numBuf = ByteBuffer.allocate(Integer.BYTES);
                    int magic = CacheFileUtils.readInt(ch, numBuf);
                    if (magic != MAGIC) {
                        throw new IOException("Bad magic number");
                    }
                    int length = CacheFileUtils.readInt(ch, numBuf);
                    if (length < 0) {
                        throw new IOException("Insane length: " + length);
                    }

                    int count = CacheFileUtils.readInt(ch, numBuf);
                    if (count > 3276) {
                        throw new IOException("Absurd number of entries: " + count);
                    }

                    for (int i = 0; i < count; i++) {
                        Path path = CacheFileUtils.readPath(ch, numBuf);
                        CacheEntry entry = CacheEntry.load(ch);
                        if (Files.exists(path)) {
                            cache.put(path, entry);
                        } else {
                            dirty = true;
                        }
                    }
                }
            }
        }
        if (file != null && !Boolean.getBoolean("unit.test")) {
            ShutdownHooks.addRunnable(task::cancel);
            ShutdownHooks.addRunnable(saver);
        }
        if (dirty) {
            dirty();
        }
    }

    int size() {
        return cache.size();
    }

    static AntlrConfigurationCache instance() {
        if (INSTANCE == null) {
            synchronized (AntlrConfigurationCache.class) {
                if (INSTANCE == null) {
                    Path cacheFile = Places.getCacheSubfile("antlr-configs.cache").toPath();
                    try {
                        INSTANCE = new AntlrConfigurationCache(cacheFile, true);
                    } catch (IOException ex) {
                        LOG.log(Level.INFO, "Exception reading antlr config cache, will delete.", ex);
                        try {
                            FileUtils.deleteIfExists(cacheFile);
                            INSTANCE = new AntlrConfigurationCache(cacheFile, false);
                        } catch (IOException ex1) {
                            LOG.log(Level.INFO, "Exception NOT reading antlr config cache", ex1);
                        }
                    }

                }
            }
        }
        return INSTANCE;
    }

    public AntlrConfiguration get(Path projectDir, Supplier<AntlrConfiguration> supplier) {
        CacheEntry en = cache.get(projectDir);
        if (en != null) {
            return en.get(this::dirty, supplier);
        } else {
            AntlrConfiguration config = supplier.get();
            if (config != null) {
                UpToDateness utd = UpToDateness.forProject(projectDir);
                // will always return true the first time, so initialize it
                utd.isChanged();
                cache.put(projectDir, new CacheEntry(utd, config));
            }
            return config;
        }
    }

    void store() throws IOException {
        try (FileChannel ch = FileChannel.open(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
            ByteBuffer numBuf = ByteBuffer.allocate(Integer.BYTES);
            CacheFileUtils.writeNumber(MAGIC, ch, numBuf);
            long pos = ch.position();
            CacheFileUtils.writeNumber(0, ch, numBuf);

            TreeMap<Path, CacheEntry> snapshot = new TreeMap<>(cache);

            int written = CacheFileUtils.writeNumber(snapshot.size(), ch, numBuf);
            for (Map.Entry<Path, CacheEntry> e : snapshot.entrySet()) {
                written += CacheFileUtils.writePath(e.getKey(), ch);
                written += e.getValue().store(ch);
            }
            long currPos = ch.position();
            try {
                ch.position(pos);
                CacheFileUtils.writeNumber(written, ch, numBuf);
            } finally {
                ch.position(currPos);
            }
        }
    }

    private void dirty() {
        if (file != null) {
            task.schedule(DELAY);
        }
    }

    class Saver implements Runnable {

        @Override
        public void run() {
            try {
                store();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private static final class CacheEntry {

        private final UpToDateness upToDate;
        private AntlrConfiguration config;

        public CacheEntry(UpToDateness upToDate, AntlrConfiguration config) {
            this.upToDate = upToDate;
            this.config = config;
        }

        @Override
        public String toString() {
            return upToDate + " / " + config;
        }

        AntlrConfiguration get(Runnable callIfReplaced, Supplier<AntlrConfiguration> ifOutOfDate) {
            if (isChanged()) {
                AntlrConfiguration nue = ifOutOfDate.get();
                if (nue != null) {
                    synchronized (this) {
                        config = nue;
                    }
                }
                return nue;
            }
            synchronized (this) {
                return config;
            }
        }

        boolean isChanged() {
            return upToDate.isChanged();
        }

        public static <C extends ReadableByteChannel & SeekableByteChannel> CacheEntry load(C channel) throws IOException {
            UpToDateness up = UpToDateness.load(channel);
            AntlrConfiguration config = AntlrConfiguration.readFrom(channel);
            return new CacheEntry(up, config);
        }

        public <C extends WritableByteChannel & SeekableByteChannel> int store(C channel) throws IOException {
            return upToDate.store(channel) + config.writeTo(channel);
        }
    }
}

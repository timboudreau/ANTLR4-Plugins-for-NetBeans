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
package org.nemesis.antlr.output;

import com.mastfrog.util.path.UnixPath;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.ref.Reference;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_16;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.StandardLocation;
import org.nemesis.antlr.common.ShutdownHooks;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.memory.spi.AntlrOutput;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFS.JFSBuilder;
import org.nemesis.jfs.JFSFileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * Implementation of AntlrLoggers and AntlrOutput, backed by a JFS (which can be
 * off heap or memory mapped temp file). Basically it will round-robin between
 * virtual files, keeping the virtual file with the output alive either until a
 * timeout is reached without a Supplier for that output being requested, or
 * until a supplier that has been instantiated has been garbage collected. So
 * effectively, we reuse the same bytes over and over, and requesting a given
 * path and task name will always get the output of the most recently closed
 * writer or print stream (or the most recent one written that already has an
 * allocated supplier). Just set the max age to leave plenty of time for a job
 * and subsequent jobs to run, and for the user to decide they want to see the
 * output, and all will be well.
 *
 * @author Tim Boudreau
 */
@ServiceProviders({
    @ServiceProvider(service = AntlrLoggers.class, position = 10000),
    @ServiceProvider(service = AntlrOutput.class, position = 10000)
})
public class Output extends AntlrLoggers implements AntlrOutput, Runnable {

    static final int MAX_AGE_MILLIS = 240_000;
    static Output INSTANCE;
    private final JFS jfs;
    private final Map<UnixPath, FileIndices> indicesForPath = new HashMap<>();
    private final int maxAge;
    private final Map<UnixPath, Reference<TextSupplier>> suppliers
            = new HashMap<>();
    private volatile int vix;
    private final Set<Consumer<Path>> onUpdated
            = Collections.synchronizedSet(new WeakSet<>());
    private final RequestProcessor lazyChanges = new RequestProcessor("antlr-output-notifier", 1);
    private final RequestProcessor.Task publishChangesTask = lazyChanges.create(this);
    private final Set<Path> pendingChanges = new HashSet<>();
    private static final int PUBLISH_DELAY = 1000;

    private static final Logger LOG = Logger.getLogger(Output.class.getName());

    public Output() throws IOException {
        this(UTF_16, MAX_AGE_MILLIS);
    }

    Output(Charset charset, int maxAge) throws IOException {
        this(JFS.builder().withCharset(charset).useOffHeapStorage(),
                maxAge);
    }

    Output(JFSBuilder jfsBuilder, int maxAge) throws IOException {
        this(jfsBuilder.build(), maxAge);
    }

    Output(JFS jfs, int maxAge) {
        this.maxAge = maxAge;
        this.jfs = jfs;
        assert jfs != null : "No jfs";
        assert maxAge > 0 : "Invalid max age " + maxAge;
        INSTANCE = this;
        if (!Boolean.getBoolean("unit.test")) {
            // delete the temp file on JVM exit
            ShutdownHooks.add(jfs::close);
        }
    }

    JFS jfs() {
        return jfs;
    }

    public String toString() {
        return "Output(" + jfs + ")";
    }

    // Pending:  Could probably just return null streams if nothing is
    // listening
    @Override
    public void run() {
        Set<Path> paths;
        Set<Consumer<Path>> consumers = new HashSet<>(onUpdated);
        if (!consumers.isEmpty()) {
            synchronized (pendingChanges) {
                paths = new HashSet<>(pendingChanges);
                pendingChanges.clear();
            }
            paths.forEach(p -> {
                consumers.forEach(c -> {
                    try {
                        c.accept(p);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });
            });
        }
    }

    void onWriterClosed(Path change) {
        if (!onUpdated.isEmpty()) {
            synchronized (pendingChanges) {
                pendingChanges.add(change);
                publishChangesTask.schedule(PUBLISH_DELAY);
            }
        }
    }

    @Override
    public void onUpdated(Consumer<Path> c) {
        onUpdated.add(c);
    }

    private List<Consumer<Path>> toUpdate() {
        return onUpdated.isEmpty() ? Collections.emptyList() : new ArrayList<>(onUpdated);
    }

    private UnixPath key(Path path, String task) {
        Path targetPath = path;
        if (targetPath.isAbsolute()) {
            // Path considers the / to be part of the first name
            // for purposes of subpaths - subpath(1, nameCount)
            // of /foo/bar/baz is bar/baz.  So just convert to a
            // string, and to UnixPath here, and UnixPath.get()
            // below is a no-op
            targetPath = UnixPath.get(path.toString().substring(1));
        }
        UnixPath up = UnixPath.get(targetPath);
        return up.getParent().resolve(path.getFileName() + "-" + task);
    }

    private UnixPath indexedKey(Path path, String task, int round) {
        return UnixPath.get(Integer.toString(round)).resolve(key(path, task));
    }

    private FileIndices indices(UnixPath path) {
        synchronized (indicesForPath) {
            FileIndices result = indicesForPath.get(path);
            if (result == null) {
                result = new FileIndices(maxAge);
                indicesForPath.put(path, result);
            }
            return result;
        }
    }

    void gc() {
        Map<UnixPath, FileIndices> i4p;
        synchronized (indicesForPath) {
            i4p = new HashMap<>(indicesForPath);
        }
        for (Map.Entry<UnixPath, FileIndices> e : i4p.entrySet()) {
            synchronized (e.getValue()) {
                BitSet cleaned = e.getValue().gc(maxAge);
                for (int bit = cleaned.nextSetBit(0); bit >= 0; bit = cleaned.nextSetBit(bit + 1)) {
                    UnixPath pathForCleaned = UnixPath.get(Integer.toString(bit)).resolve(e.getKey());
                    JFSFileObject fo = jfs.get(StandardLocation.SOURCE_OUTPUT, pathForCleaned);
                    if (fo != null) {
                        fo.delete();
                    }
                }
            }
        }
    }

    @Override
    protected PrintStream streamForPathAndTask(Path path, String task) {
        try {
            return jfs.whileWriteLocked(() -> {
                if (vix++ % 20 > 0) {
                    gc();
                }
                UnixPath key = key(path, task);
                FileIndices in = indices(key);
                int ix = in.writerOpened();
                UnixPath outputPath = UnixPath.get(Integer.toString(ix)).resolve(key);
                try {
                    JFSFileObject fo = jfs.get(StandardLocation.SOURCE_OUTPUT, outputPath, true);
                    return new RecyclingPrintStream(outputPath, in, ix,
                            fo.openOutputStream(), true, jfs.encoding(), path);
                } catch (IOException ex) {
                    LOG.log(Level.INFO, "Exception opening JFS output " + outputPath + " with " + in, ex);
                    return nullPrintStream();
                }
            });
        } catch (IOException ex) {
            LOG.log(Level.INFO, "Exception opening JFS output", ex);
            return nullPrintStream();
        }
    }

    @Override
    protected Writer writerForPathAndTask(Path path, String task) {
        try {
            return jfs.whileWriteLocked(() -> {
                UnixPath key = key(path, task);
                FileIndices in = indices(key);
                int ix = in.writerOpened();
                UnixPath outputPath = UnixPath.get(Integer.toString(ix)).resolve(key);
                try {
                    JFSFileObject fo = jfs.get(StandardLocation.SOURCE_OUTPUT, outputPath, true);
                    return new RecyclingWriter(outputPath, in, ix, fo.openOutputStream(), jfs.encoding(), path);
                } catch (IOException ex) {
                    LOG.log(Level.INFO, "Exception opening JFS output " + outputPath + " with " + in, ex);
                    return nullWriter();
                }
            });
        } catch (IOException ex) {
            LOG.log(Level.INFO, "Exception opening JFS output", ex);
            return nullWriter();
        }
    }

    @Override
    public Supplier<? extends CharSequence> outputFor(Path path, String task) {
        synchronized (suppliers) {
            try {
                return jfs.whileReadLocked(() -> {
                    UnixPath taskFilePath = key(path, task);
                    FileIndices in = indices(taskFilePath);
                    int last = in.readerOpened();
                    if (last < 0) {
                        return null;
                    }
                    UnixPath lastKey = UnixPath.get(Integer.toString(last)).resolve(taskFilePath);

                    return getSupplierInternal(lastKey, in, last);
                });
            } catch (IOException ioe) {
                LOG.log(Level.INFO, "Exception opening JFS reader", ioe);
                return null;
            }
        }
    }

    public Supplier<? extends CharSequence> outputFor(int seq, Path path, String task) {
        synchronized (suppliers) {
            try {
                return jfs.whileReadLocked(() -> {
                    UnixPath taskFilePath = key(path, task);
                    FileIndices in = indices(taskFilePath);
                    int last = in.readerOpened(seq);
                    if (last < 0) {
                        return null;
                    }
                    UnixPath lastKey = UnixPath.get(Integer.toString(seq)).resolve(taskFilePath);
                    return getSupplierInternal(lastKey, in, last);
                });
            } catch (IOException ioe) {
                LOG.log(Level.INFO, "Exception opening JFS reader", ioe);
                return null;
            }
        }
    }

    private TextSupplier getSupplierInternal(UnixPath lastKey, FileIndices in, int last) {
        Reference<TextSupplier> ref = suppliers.get(lastKey);
        if (ref != null) {
            TextSupplier supp = ref.get();
            if (supp != null) {
                return supp;
            }
        }

        JFSFileObject fo = jfs.get(StandardLocation.SOURCE_OUTPUT, lastKey, false);
        if (fo == null) {
            return null;
        }
        TextSupplier result = new TextSupplier(fo, in, last);
        suppliers.put(lastKey, result.ref());
        return result;
    }
}

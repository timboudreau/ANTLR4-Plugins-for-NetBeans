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
package org.nemesis.antlr.spi.language;

import com.mastfrog.util.collections.CollectionUtils;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Utilities;

/**
 * Registry of ParseResultHooks which can be programmatically registered against
 * a mime type or fileobject. We need this to allow editor kits which are
 * dynamically created against Antlr grammars which are being edited to rebuild
 * themselves when the grammar they provide syntax highlighting for is edited
 * and reparsed, to allow them to rerun analysis of any open files, See the
 * antlr-live project.
 * <p>
 * ParserResultHooks registered thusly <i>must</i> remain strongly referenced or
 * they will be garbage collected and listening will stop.
 * </p>
 *
 * @author Tim Boudreau
 */
final class ProgrammaticParseResultHookRegistry {

    private static volatile ProgrammaticParseResultHookRegistry INSTANCE = new ProgrammaticParseResultHookRegistry();

    private final Map<String, Set<ResultHookReference<?>>> byMimeType
            = CollectionUtils.concurrentSupplierMap(
                    ProgrammaticParseResultHookRegistry::set);

    private final Map<FileObject, Set<ResultHookReference<?>>> byFile
            = CollectionUtils.concurrentSupplierMap(
                    ProgrammaticParseResultHookRegistry::set);

    private static final Logger LOG = Logger.getLogger(
            ProgrammaticParseResultHookRegistry.class.getName());

    static Set<ResultHookReference<?>> set() {
        // XXX should be WeakHashSet?
        return Collections.synchronizedSet(new HashSet<>(5));
    }

    static ProgrammaticParseResultHookRegistry instance() {
        ProgrammaticParseResultHookRegistry result = INSTANCE;
        if (result == null) {
            synchronized (ProgrammaticParseResultHookRegistry.class) {
                result = INSTANCE;
                if (result == null) {
                    result = INSTANCE = new ProgrammaticParseResultHookRegistry();
                }
            }
        }
        return result;
    }

    static boolean active() {
        return INSTANCE != null;
    }

    public synchronized static void shutdown() { // for tests
        if (INSTANCE != null) {
            INSTANCE._shutdown();
            INSTANCE = null;
        }
    }

    private void _shutdown() {
        LOG.log(Level.FINE, "ProgrammaticParseResultHookRegistry.shutdown "
                + " clearing {0} and {1}", new Object[]{
                    byMimeType.keySet(),
                    byFile.keySet()
                });
        try {
            byMimeType.entrySet().forEach((e) -> {
                new HashSet<>(e.getValue()).forEach((r) -> {
                    r.run();
                });
            });
        } finally {
            byMimeType.clear();
            try {
                new HashSet<>(byFile.entrySet()).forEach((e) -> {
                    e.getValue().forEach((r) -> {
                        r.run();
                    });
                });
            } finally {
                byFile.clear();
            }
        }
    }

    public static void deregisterAllOfType(String mimeType) {
        LOG.log(Level.FINER, "Deregister all hooks for {0}", mimeType);
        instance()._deregisterAllOfType(mimeType);
    }

    private void _deregisterAllOfType(String mimeType) {
        try {
            byMimeType.entrySet().stream().filter((e) -> (mimeType.equals(e.getKey()))).forEach((e) -> {
                new HashSet<>(e.getValue()).forEach((r) -> {
                    r.run();
                });
            });
            byMimeType.remove(mimeType);
        } finally {
            Set<FileObject> toRemove = new HashSet<>();
            byFile.entrySet().stream().filter((e) -> (mimeType.equals(e.getKey().getMIMEType()))).forEach((e) -> {
                new HashSet<>(e.getValue()).forEach((r) -> {
                    toRemove.add(e.getKey());
                    r.run();
                });
            });
            for (FileObject fo : toRemove) {
                byFile.remove(fo);
            }
        }
    }

    public static <T extends ParserRuleContext> void register(FileObject fo, ParseResultHook<T> hook) {
        instance()._register(fo, hook);
    }

    public static <T extends ParserRuleContext> void register(String mimeType, ParseResultHook<T> hook) {
        instance()._register(mimeType, hook);
    }

    private static <T extends ParserRuleContext> void deregister(String mimeType, ResultHookReference<T> hook) {
        instance()._deregister(mimeType, hook);
    }

    private static <T extends ParserRuleContext> void deregister(FileObject fo, ResultHookReference<T> hook) {
        instance()._deregister(fo, hook);
    }

    public static <T extends ParserRuleContext> boolean deregister(String mimeType, ParseResultHook<T> hook) {
        return instance()._deregister(mimeType, hook);
    }

    public static <T extends ParserRuleContext> boolean deregister(FileObject fo, ParseResultHook<T> hook) {
        return instance()._deregister(fo, hook);
    }

    private static boolean removeFromSet(Set<ResultHookReference<?>> s, ParseResultHook<?> hook) {
        boolean result = false;
        LOG.log(Level.FINEST, "Remove programmatic ParseResultHook {0}", hook);
        for (Iterator<ResultHookReference<?>> it = s.iterator(); it.hasNext();) {
            ResultHookReference<?> r = it.next();
            if (hook == r.get()) {
                it.remove(); // avoid CME by preemptively removing
                r.run();
                result = true;
            }
        }
        return result;
    }

    private <T extends ParserRuleContext> boolean _deregister(String mimeType, ParseResultHook<T> hook) {
        return removeFromSet(byMimeType.get(mimeType), hook);
    }

    private <T extends ParserRuleContext> boolean _deregister(FileObject fo, ParseResultHook<T> hook) {
        return removeFromSet(byFile.get(fo), hook);
    }

    <T extends ParserRuleContext> void _register(FileObject fo, ParseResultHook<T> hook) {
        byFile.get(fo).add(new FileResultHookReference<>(hook, fo));
    }

    <T extends ParserRuleContext> void _register(String mimeType, ParseResultHook<T> hook) {
        byMimeType.get(mimeType).add(new MimeTypeResultHookReference<>(hook, mimeType));
    }

    <T extends ParserRuleContext> void _deregister(String mimeType, ResultHookReference<T> hook) {
        byMimeType.get(mimeType).remove(hook);
    }

    <T extends ParserRuleContext> void _deregister(FileObject fo, ResultHookReference<T> hook) {
        byFile.get(fo).remove(hook);
    }

    static void onReparse(ParserRuleContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) {
        instance()._onReparse(tree, mimeType, extraction, populate, fixes);
    }

    final void _onReparse(ParserRuleContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) {
        Set<ResultHookReference<?>> allHooks = new HashSet<>(byMimeType.get(mimeType));
        extraction.source().lookup(FileObject.class, file -> {
            allHooks.addAll(byFile.get(file));
        });
        LOG.log(Level.FINER, "Run {0} programmatically registered hooks for reparse of {1}",
                new Object[]{allHooks.size(), extraction.source()});
        allHooks.forEach((hook) -> {
            try {
                LOG.log(Level.FINEST, "Run {0} for {1}", new Object[] {hook, extraction.source()});
                hook.onReparse(tree, mimeType, extraction, populate, fixes);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Exception in " + hook + " for "
                        + mimeType + " against " + extraction.source(), ex);
            }
        });
    }

    static abstract class ResultHookReference<T extends ParserRuleContext> extends WeakReference<ParseResultHook<T>> implements Runnable {

        volatile boolean destroyed;
        private final Set<String> warned = new HashSet<>(2);
        private final String stringValue;

        public ResultHookReference(ParseResultHook<T> referent) {
            super(referent, Utilities.activeReferenceQueue());
            stringValue = referent.toString();
        }

        protected abstract String targetString();

        @Override
        public synchronized final void run() {
            if (destroyed) {
                return;
            }
            LOG.log(Level.FINE, "Programmatic parse result hook {0} was gc'd "
                    + "or explicitly removed", stringValue);
            destroyed = true;
            onDestroyed();
        }

        protected abstract void onDestroyed();

        protected void beforeReparse(ParseResultHook<T> hook, String mimeType, Extraction ext) {

        }

        final void onBeforeReparse(ParseResultHook<T> hook, String mimeType, Extraction extraction) {
            assert hook != null;
            beforeReparse(hook, mimeType, extraction);
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Begin programmatic hook reparse of {0} by {1} ({2}) for {3} in {4} registered on {5}",
                        new Object[]{extraction.source(), hook, hook.getClass().getName(), mimeType, getClass().getName(), targetString()});
            }
        }

        final void onAfterReparse(ParseResultHook<T> hook, String mimeType, Extraction extraction, long elapsedMs) {
            assert hook != null;
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Programmatic hook reparse of {0} by {1} ({2}) for {3} in {4} registered on {5} took {6}",
                        new Object[]{extraction.source(), hook, hook.getClass().getName(), mimeType, getClass().getName(), targetString(), elapsedMs});
            }
        }

        protected final void onReparse(ParserRuleContext tree, String mimeType, Extraction extraction, ParseResultContents populate, Fixes fixes) throws Exception {
            ParseResultHook<T> hook = get();
            if (hook != null) {
                if (hook.type().isInstance(tree)) {
                    long then = System.currentTimeMillis();
                    onBeforeReparse(hook, mimeType, extraction);
                    hook.onReparse(hook.type().cast(tree), mimeType, extraction, populate, fixes);
                    onAfterReparse(hook, mimeType, extraction, System.currentTimeMillis() - then);
                } else {
                    String clName = tree.getClass().getName();
                    if (!warned.contains(clName)) {
                        warned.add(clName);
                        Logger.getLogger(ResultHookReference.class.getName()).log(Level.WARNING,
                                "Will not run hook {0}({1}) against  parse tree of "
                                + "type {2} because the hook is parameterized on {3}. "
                                + "Probably that is the wrong type",
                                new Object[]{hook, hook.getClass().getName(),
                                    tree.getClass().getName(), hook.type().getClass().getName()});
                    }
                }
            } else {
                LOG.log(Level.FINER, "Hook discovered to have been "
                        + "gc'd on attempt to run: {0}", stringValue);
                run();
            }
        }
    }

    private static final class FileResultHookReference<T extends ParserRuleContext> extends ResultHookReference<T> {

        private final FileObject fo;
        private final L l = new L();
        private final String stringVal;

        public FileResultHookReference(ParseResultHook<T> referent, FileObject fo) {
            super(referent);
            stringVal = referent.toString();
            this.fo = fo;
            fo.addFileChangeListener(FileUtil.weakFileChangeListener( l, fo ));
        }

        @Override
        protected void beforeReparse(ParseResultHook<T> hook, String mimeType, Extraction ext) {
            Optional<FileObject> optActual = ext.source().lookup(FileObject.class);
            if (optActual.isPresent()) {
                if (!fo.equals(optActual.get())) {
                    throw new IllegalStateException("Hook is being passed an extraction of "
                            + "the wrong file."
                            + "\nExpect:  " + fo.getPath()
                            + "\nBut got: " + optActual.get().getPath());
                }
            }
        }

        @Override
        protected String targetString() {
            return fo.getNameExt() + " with " + get() + " for " + stringVal;
        }

        @Override
        protected void onDestroyed() {
            LOG.log(Level.FINEST, "FileResultHookReference destroyed for {0} - {1}",
                    new Object[]{fo, stringVal});
            fo.removeFileChangeListener(l);
            deregister(fo, (ResultHookReference) this);
        }

        class L extends FileChangeAdapter {

            @Override
            public void fileDeleted(FileEvent fe) {
                LOG.log(Level.FINE, "Remove programmatic file hook for {0} "
                        + "due to deletion", fe.getFile());
                run();
            }
        }
    }

    private static final class MimeTypeResultHookReference<T extends ParserRuleContext> extends ResultHookReference<T> {

        private final String mimeType;
        private final String stringVal;

        public MimeTypeResultHookReference(ParseResultHook<T> referent, String mimeType) {
            super(referent);
            stringVal = referent.toString();
            this.mimeType = mimeType;
        }

        @Override
        protected String targetString() {
            return mimeType;
        }

        @Override
        protected void onDestroyed() {
            LOG.log(Level.FINEST, "FileResultHookReference destroyed for {0} - {1}",
                    new Object[]{mimeType, stringVal});
            deregister(mimeType, this);
        }
    }
}

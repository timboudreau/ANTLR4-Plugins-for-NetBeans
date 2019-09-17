/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.parsing;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.WeakSet;

/**
 * A parser which, if necessary, recompiles the grammar in-memory, generates
 * extraction code specific to that grammar, compiles that, and then extracts a
 * ParseTreeProxy by running the generated extractor code against the grammar in
 * an isolating classloader (so the version of Antlr used by the project can
 * potentially be used). The returned ParseTreeProxy is a copy of the syntax
 * tree, token stream and related information which will not leak objects from
 * the classloader used.
 *
 * @author Tim Boudreau
 */
public class EmbeddedAntlrParser {

    private final AtomicReference<GrammarRunResult<EmbeddedParser>> runner = new AtomicReference<>();
    private final AtomicInteger revCount = new AtomicInteger();
    private final Path path;
    private final String grammarName;
    static final Logger LOG = Logger.getLogger(EmbeddedAntlrParser.class.getName());
    Runnable unsubscriber;
    final BiConsumer<Extraction, GrammarRunResult<EmbeddedParser>> subscriber
            = new BC();
    private final Set<Runnable> changeListeners = new WeakSet<>();
    volatile boolean disposed;

    static {
        LOG.setLevel(Level.ALL);
    }

    public EmbeddedAntlrParser(Path path, String grammarName) {
        this.path = path;
        this.grammarName = grammarName;
    }

    public GrammarRunResult<?> lastResult() {
        return runner.get();
    }

    /**
     * Subscribe to notifications that the underlying Antlr grammar has been
     * re-parsed.
     *
     * @param listener A runnable, which will be weakly referenced
     */
    public void listen(Runnable listener) {
        changeListeners.add(notNull("listener", listener));
    }

    /**
     * Unsubscribe from notifications about changes in the underlying grammar.
     *
     * @param listener
     */
    public void unlisten(Runnable listener) {
        changeListeners.remove(notNull("listener", listener));
    }

    /**
     * Dispose of this instance, causing it to cease getting notifications of
     * grammar changes even if it is still strongly referenced. Any subsequent
     * calls to parse will return <code>AntlrProxies.forUnparsed</code>.
     */
    public void dispose() {
        // This disposes of the reference that is keeping our subscription
        // to reparses alive, so we won't be called for new reparses
        disposed = true;
        if (unsubscriber != null) {
            unsubscriber.run();
            unsubscriber = null;
        }
        GrammarRunResult<EmbeddedParser> g = runner.getAndSet(null);
        if (g != null && g.get() != null) {
            g.get().onDiscard();
        }
    }

    boolean isDisposed() {
        return disposed;
    }

    private final class BC implements BiConsumer<Extraction, GrammarRunResult<EmbeddedParser>> {

        @Override
        public void accept(Extraction t, GrammarRunResult<EmbeddedParser> runResult) {
            LOG.log(Level.FINER, "Got new run result for {0}: {2}, usable? "
                    + "{3} status {4}", new Object[]{
                        EmbeddedAntlrParser.this, t.source(),
                        runResult.isUsable(), runResult.currentStatus()
                    });
            if (runResult.isUsable()) {
                setRunner(runResult);
            }
        }
    }

    boolean isUpToDate() {
        GrammarRunResult<EmbeddedParser> res = runner.get();
        if (res == null) {
            return false;
        }
        return res.currentStatus().isUpToDate();
    }

    @Override
    public String toString() {
        return super.toString() + "(" + grammarName + " " + path + " " + rev()
                + ")";
    }

    int setRunner(GrammarRunResult<EmbeddedParser> runner) {
        GrammarRunResult<EmbeddedParser> old = this.runner.get();
        this.runner.set(runner);
        if (old != null && old.get() != null) {
            old.get().onDiscard();
        }
        int result = update();
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Exception updating " + r + " for "
                        + runner, ex);
            }
        }
        return result;
    }

    int update() {
        return revCount.incrementAndGet();
    }

    int rev() {
        return revCount.get();
    }

    static ThreadLocal<Integer> staleCheckEntryCount = ThreadLocal.withInitial(() -> 0);

    private boolean checkStale() {
        int reentries = staleCheckEntryCount.get();
        if (reentries > 0) {
            return false;
        }
        staleCheckEntryCount.set(reentries + 1);
        boolean result = false;
        try {
            if (Thread.interrupted()) {
                return false;
            }
            GrammarRunResult<EmbeddedParser> r = runner.get();
            if (r == null || r.currentStatus().mayRequireRebuild()) {
                LOG.log(Level.FINER, "Force reparse of {0} for stale embedded parser", path);
                // XXX should see if we have a document?
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
                // Invalidate the source - we cannot guarantee we have received a
                // file change notification yet (or at all), and if we haven't,
                // ParserManager et. al. will reuse an old copy of the source
                // (tests will fail if this line is removed)
                sourceInvalidator().accept(fo);
                if (Thread.interrupted()) {
                    return false;
                }
                if (fo != null) {
                    for (;;) {
                        // Force a reparse, which will trigger a call to setRunner()
                        Collection<Source> srcs = Collections.singleton(Source.create(fo));
                        try {
                            // Must not use a static instance of UT or we will get the
                            // same result again and again
                            ParserManager.parse(srcs, new UT());
                            result = true;
                            break;
                        } catch (ParseException ex) {
                            if (ex.getCause() instanceof ClosedByInterruptException) {
                                LOG.log(Level.WARNING, grammarName, ex);
                            } else {
                                LOG.log(Level.WARNING, grammarName, ex);
                                break;
                            }
                        }
                    }
                } else {
                    LOG.log(Level.WARNING, "File object for {0} gone", path);
                }
            }
        } finally {
            staleCheckEntryCount.set(reentries);
        }
        return result;
    }

    static Consumer<FileObject> SOURCE_INVALIDATOR;

    static Consumer<FileObject> sourceInvalidator() {
        if (SOURCE_INVALIDATOR == null) {
            SOURCE_INVALIDATOR = SourceInvalidator.create();
        }
        return SOURCE_INVALIDATOR;
    }

    private String lastText;
    private ParseTreeProxy lastResult;

    private synchronized ParseTreeProxy setTextAndResult(String text, ParseTreeProxy prox) {
        if (text != null) {
            lastText = text;
            lastResult = prox;
        }
        return prox;
    }

    private synchronized ParseTreeProxy lastResultIfMatches(boolean wasStale, String t) {
        if (!wasStale) {
            if (lastText != null && lastResult != null
                    && Objects.equals(t, lastText) && !lastResult.isUnparsed()) {
//                LOG.log(Level.INFO, "Use cached result from {0}", this);
//                return lastResult;
            }
        }
        return null;
    }

    public ParseTreeProxy parse(String t) throws Exception {
        boolean wasStale = checkStale();
        ParseTreeProxy result = lastResultIfMatches(wasStale, t);
        if (result != null) {
            return result;
        }
        GrammarRunResult<EmbeddedParser> r = runner.get();
        if (r == null || r.get() == null) {
            return AntlrProxies.forUnparsed(path, grammarName, t);
        }
        return setTextAndResult(t, r.get().parse(t));
    }

    public ParseTreeProxy parse(String t, int ruleNo) throws Exception {
        boolean wasStale = checkStale();
        ParseTreeProxy result = lastResultIfMatches(wasStale, t);
        if (result != null) {
            return result;
        }
        GrammarRunResult<EmbeddedParser> r = runner.get();
        if (r == null || r.get() == null) {
            return AntlrProxies.forUnparsed(path, grammarName, t);
        }
        return setTextAndResult(t, r.get().parse(t, ruleNo));
    }

    public ParseTreeProxy parse(String t, String ruleName) throws Exception {
        boolean wasStale = checkStale();
        ParseTreeProxy result = lastResultIfMatches(wasStale, t);
        if (result != null) {
            return result;
        }
        GrammarRunResult<EmbeddedParser> r = runner.get();
        if (r == null || r.get() == null) {
            return AntlrProxies.forUnparsed(path, grammarName, t);
        }
        return setTextAndResult(t, r.get().parse(t, ruleName));
    }

    static final class UT extends UserTask {

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            if (Thread.interrupted()) {
                return;
            }
            Parser.Result res = resultIterator.getParserResult();
            LOG.log(Level.FINEST, "Forced reparse of {0} gets {1}",
                    new Object[]{resultIterator.getSnapshot().getSource(),
                        res});
        }
    }
}

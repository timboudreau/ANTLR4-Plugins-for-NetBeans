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
package org.nemesis.antlr.live.parsing;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingFunction;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Segment;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.impl.DeadEmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.debug.api.Debug;
import org.nemesis.debug.api.Trackables;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.result.UpToDateness;
import org.nemesis.misc.utils.ActivityPriority;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;

/**
 * Main entry point to live running of generated Antlr grammars. This class does
 * a LOT:
 * <ul>
 * <li>It is passed a "runner" - really a holder for the JFSClassLoader which
 * can set it and invoke ParserExtractor that was generated and compiled with
 * the grammar in the classloader, returning a ParseTreeProxy which does not
 * leak instances from the classloader (everything is wrappered as proxy objects
 * and java primitives)</li>
 * <li>It keeps track of the last modified state of the grammar files, and if
 * out of date, the next call to parse() will first force ParserManager to
 * reparse the grammar file (which will indirectly trigger a reentrant call to
 * accept, setting a new environment).</li>
 * <li>It keeps the last parse result and text, and if they are the same and the
 * environment is not stale, will reuse them</li>
 * </ul>
 * <p>
 * Instanes should be manually disposed when no longer needed, so the
 * JFSClassloader and potentially the JFS it belongs to can also be closed
 * (right now this only happens if the owning Project is garbage collected, but
 * eventually it should be discarded after sufficient lack of use).
 * </p>
 *
 * @author Tim Boudreau
 */
final class EmbeddedAntlrParserImpl extends EmbeddedAntlrParser implements TriConsumer<Extraction, GrammarRunResult<EmbeddedParser>, EmbeddedParser> {

    private final Set<BiConsumer<? super Extraction, ? super GrammarRunResult<?>>> listeners
            = Collections.synchronizedSet(new WeakSet<>());
    private final AtomicInteger rev = new AtomicInteger();
    private final AtomicReference<EmbeddedParsingEnvironment> environment
            = new AtomicReference<>();
    private static final Consumer<FileObject> INVALIDATOR = SourceInvalidator.create();

    private static final Logger LOG = Logger.getLogger(EmbeddedAntlrParser.class.getName());

    private Runnable unsubscriber;
    private final String logName;
    private final Path path;
    private final String grammarName;
    private volatile boolean disposed;
    private AtomicReference<LastParseInfo> lastParseInfo;
    private final ThreadLocal<Boolean> reentry = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final String mimeType;

    EmbeddedAntlrParserImpl(String logName, Path path, String grammarName, String mimeType) {
        this.logName = logName;
        this.mimeType = mimeType;
        this.path = path;
        this.grammarName = grammarName;
        environment.set(new EmbeddedParsingEnvironment(path, grammarName));
        lastParseInfo = new AtomicReference<>(new LastParseInfo(path, grammarName));
        LOG.log(Level.FINER, "Create an EmbeddedAntlrParserImpl {0} for {1} grammar {2} type {3}",
                new Object[]{logName, path, grammarName, mimeType});
    }

    @Override
    public String toString() {
        return "EmbeddedAntlrParser(disposed=" + disposed
                + ", rev=" + rev.get()
                + ", env=" + environment.get()
                + ", last=" + lastParseInfo
                + ", listenerCount=" + listeners.size()
                + ", listeners=" + listeners
                + ")";
    }

    @Override
    public void clean() {
        EmbeddedParsingEnvironment env = environment.get();
        if (env != null) {
            env.parser.clean();
        }
    }

    @Override
    boolean isUpToDate() {
        return environment.get().isUpToDate();
    }

    void setUnsubscriber(Runnable unsubscriber) {
        this.unsubscriber = notNull("unsubscriber", unsubscriber);
    }

    private final Object staleCheckLock = new Object();

    private boolean checkStaleAndReparseGrammarIfNeeded(EmbeddedParsingEnvironment info) throws Exception {
        if (disposed) {
            LOG.log(Level.INFO, "Stale check in disposed EmbeddedAntlrParser",
                    new Exception("Stale check in disposed EmbeddedAntlrParser."
                            + " Nothing should be using this."));
            return false;
        }
        if (info.parser instanceof DeadEmbeddedParser) {
            LOG.log(Level.FINE, "Force reparse due to dead parser for {0} on {1}",
                    new Object[]{grammarName, Thread.currentThread()});
//            System.out.println("DEAD PARSER FORCE REPARSE for " + getClass().getSimpleName() + "@" + System.identityHashCode(this)
//                    + " on " + Thread.currentThread());
            forceGrammarFileReparse(info);
            return true;
        }
        if (!info.isUpToDate()) {
//            synchronized (staleCheckLock) {
            info = environment.get();
            if (!info.isUpToDate()) {
                LOG.log(Level.FINE, "{0} force reparse due to {1} up to date? ",
                        new Object[]{path.getFileName(), new Object[]{info, info.isUpToDate()}});
                int oldRev = rev.get();
                forceGrammarFileReparse(info);
                if (rev.get() != oldRev) {
                    EmbeddedParsingEnvironment newEnv = this.environment.get();
                }
                return true;
//                }
            }
        }
        return false;
    }

    private final ReentrantReadWriteLock parseLock = new ReentrantReadWriteLock(true);

    private static CharSequence escapeChar(char c) {
        CharSequence result = Escaper.CONTROL_CHARACTERS.escape(c);
        return result == null ? Character.toString(c) : result;
    }

    private static String charDiff(CharSequence a, CharSequence b) {
        StringBuilder sb = new StringBuilder();
        sb.append("Old length: ").append(a.length()).append(" new length ").append(b.length());
        int firstDifference = -1;
        char sda, sdb, eda, edb;
        sda = sdb = eda = edb = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            char ca = a.charAt(i);
            char cb = a.charAt(i);
            if (ca != cb) {
                sda = ca;
                sdb = cb;
                firstDifference = i;
                break;
            }
        }
        if (firstDifference == -1) {
            sb.append("\nAll characters present in both strings match.");
        }
        int lastDifferenceA = -1;
        int lastDifferenceB = -1;

        for (int aa = a.length() - 1, bb = b.length() - 1; aa >= 0 && bb >= 0; aa--, bb--) {
            char ca = a.charAt(aa);
            char cb = b.charAt(bb);
            if (ca != cb) {
                eda = ca;
                edb = cb;
                lastDifferenceA = aa;
                lastDifferenceB = bb;
                break;
            }
        }
        if (firstDifference != -1) {
            sb.append("\nFirst difference at ")
                    .append(firstDifference).append(" '")
                    .append(escapeChar(sda))
                    .append("' vs '").append(escapeChar(sdb)).append("'.");
        }

        sb.append("\nLast difference at ").append(lastDifferenceA)
                .append("/").append(lastDifferenceB).append(" '")
                .append(escapeChar(eda))
                .append("' vs '").append(escapeChar(edb))
                .append("'");
//        sb.append("\nText A: ").append(a);
//        sb.append("\nText B: ").append(b);
        return sb.toString();
    }

    private static CharSequence convert(CharSequence seq) {
        // If this is a org.netbeans.spi.lexer.LexerInput$ReadText
        // we need to copy it, or its internals will get ripped out while
        // we're using it
        if (seq instanceof StringBuilder || seq instanceof Segment || seq instanceof String) {
            return seq;
        }
        return seq.toString();
    }

    @Override
    public EmbeddedAntlrParserResult parse(CharSequence textToParse) throws Exception {
        // First, se if this is a reprise of the last parse and the grammar
        // files have not changed; if so, and the text matches, just return
        // the previous parser result without acquiring any locks.  I can't think
        // of a scenario where this fails.
        EmbeddedParsingEnvironment oldInfo = environment.get();
        LOG.log(Level.FINER, "Parse {0} chars for {1} with parser {2}",
                new Object[]{textToParse == null ? "null" : textToParse.length(),
                    grammarName, logName});

        if (oldInfo.parser instanceof DeadEmbeddedParser || oldInfo.isUpToDate()) {
            LastParseInfo last = lastParseInfo.get();
            if (last != null && last.canReuse(textToParse)) {
                if (last.parserResult.grammarTokensHash().equals(oldInfo.grammarTokensHash)) {
                    LOG.log(Level.FINEST, "Reuse last parser result for same text. My tokens hash {0}",
                            oldInfo.grammarTokensHash);
                    return last.parserResult;
                }
            } else if (last != null && textToParse != null && last.seq != null && LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "Text may be changed: {0}", charDiff(textToParse, last.seq));
            }
        }
        // XXX - this sucks, but lexer / snapshot char sequences explode on
        // contact if touched after exiting the UserTask
        CharSequence toParse = textToParse == null ? null : convert(textToParse);
        // Return the cached lastParseInfo where possible, ignoring cases where the
        // text is null (in which case, we are being invoked just for the lexer to get
        // the list of token types)
        return Debug.runObjectThrowing(this, logName + "-" + environment.get().grammarTokensHash, () -> {
            return getOrWaitForParallellResult(toParse, sq -> {
                Obj<EmbeddedAntlrParserResult> resHolder = Obj.create();
                // We need to grab the parser manager lock for our mime type
                // here, basically to keep everybody else out.
                if (!EventQueue.isDispatchThread() && ActivityPriority.get() != ActivityPriority.REALTIME) {
                    // Sigh - this locking is more correct, but it is also the
                    // source of many UI freezes.  So, try letting the EDT parse
                    // freely and see if that wreaks more havoc or less
//                    ParserManager.parse(mimeType, new UserTask() {
//                        // Seems we need to preemptively acquire the parser manager's
//                        // lock, so as to avoid a deadlock
//                        @Override
//                        public void run(ResultIterator ri) throws Exception {
                    doReparseText(resHolder, toParse);
//                        }
//
//                    });
                } else {
                    doReparseText(resHolder, toParse);
                }
                return resHolder.get();
            });
        });
    }

    boolean doReparseText(Obj<EmbeddedAntlrParserResult> resHolder, CharSequence toParse) throws Exception {
        EmbeddedParsingEnvironment info = environment.get();
//                    info.runResult.jfs().whileLockedWithWithLockDowngrade(() -> {
//
//                    }, () -> null);
// XXX way too many levels of locking in here.
// Should not need an internal lock.
// Should not need the parser lock.
// Should use the JFS read lock (?)

        ReentrantReadWriteLock.WriteLock writeLock = parseLock.writeLock();
        ReentrantReadWriteLock.ReadLock readLock = parseLock.readLock();
//        writeLock.lock();
        AntlrProxies.ParseTreeProxy res;
        boolean wasStale = false;
        try {
            try {
                wasStale = checkStaleAndReparseGrammarIfNeeded(info);
                if (wasStale) {
                    EmbeddedParsingEnvironment newInfo = environment.get();
                    LOG.log(Level.FINER, "Stale check {0} replaced parser env? {1}",
                            new Object[]{grammarName,
                                newInfo != info});
                    Debug.message("Replace env " + (info != newInfo), newInfo::toString);
                    info = newInfo;
                }
            } finally {
//                readLock.lock();
//                writeLock.unlock();
            }
            if (!wasStale) {
                LastParseInfo lpi = lastParseInfo.get();
                if (lpi.canReuse(toParse)) {
                    // XXX
                    LOG.log(Level.FINEST, "Reuse previous parser result {0} "
                            + "for same or null text", lpi);
                    Debug.message(logName + "-reuse-" + info.grammarTokensHash,
                            lpi::toString);
                    resHolder.set(lpi.parserResult);
                    return true;
                }
            }
            Debug.message("Will parse with", info.parser::toString);
            res = info.parser.parse(logName, toParse);
        } finally {
//            readLock.unlock();
        }
        LOG.log(Level.FINEST, "Parsed to {0} by {1}",
                new Object[]{res.loggingInfo(), info.parser});
        String tokensHash = info.grammarTokensHash;
        EmbeddedAntlrParserResult result = new EmbeddedAntlrParserResult(path, res,
                info.runResult, tokensHash, grammarName);
        if (toParse != null) {
            lastParseInfo.set(new LastParseInfo(result, toParse));
            Trackables.track(AntlrProxies.ParseTreeProxy.class, res, () -> {
                return res.loggingInfo() + "\t" + tokensHash + "\n" + logName;
            });
        }
        LOG.log(Level.FINE, "New parser result {0}", result);
        Debug.success(logName + "-created-" + res.loggingInfo(), result::toString);
        resHolder.set(result);
        return false;
    }

    private final RequestProcessor proc = new RequestProcessor("embedded-parser-grammar-reparse", 1, false);

    class Reparser implements Runnable {

        @Override
        public void run() {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            if (fo != null && fo.isValid()) {
                try {
                    INVALIDATOR.accept(fo);
                    LOG.log(Level.FINEST, "Invalidated Source for {0}", fo.getNameExt());
                    Source src = Source.create(fo);
                    ParserManager.parseWhenScanFinished(Collections.singleton(src), new UT());
                } catch (Exception ex) {
                    org.openide.util.Exceptions.printStackTrace(ex);
                }
            } else {
                EmbeddedAntlrParsers.onAtteptToParseNonExistentFile(EmbeddedAntlrParserImpl.this);
                LOG.log(Level.INFO, "File object for {0} disappeared when "
                        + "attempting to force a reparse.", path);
            }
        }
    }
    private final RequestProcessor.Task reparseTask = proc.create(new Reparser());

    private void reparseLater(EmbeddedParsingEnvironment info) {
        if (info != null) {
            if (info.parser != null && info.parser.getClass() != DeadEmbeddedParser.class) {
                if (info.runResult != null) {
                    AntlrGenerationResult toCheck = info.runResult.getWrapped(AntlrGenerationResult.class);
                    if (toCheck != null) {
                        if (!toCheck.isUsable() && RebuildSubscriptions.isThrottled(toCheck)) {
                            return;
                        }
                        if (toCheck.areOutputFilesUpToDate()) {
                            return;
                        }
                    }
                }
            }
        }
        reparseTask.schedule(500);
    }

    private boolean forceGrammarFileReparse(EmbeddedParsingEnvironment info) throws Exception {
        if (true) {
            reparseLater(info);
            return false;
        }
        if (info != null) {
            if (info.parser != null && info.parser.getClass() != DeadEmbeddedParser.class) {
                if (info.runResult != null) {
                    AntlrGenerationResult toCheck = info.runResult.getWrapped(AntlrGenerationResult.class);
                    if (toCheck != null) {
                        if (!toCheck.isUsable() && RebuildSubscriptions.isThrottled(toCheck)) {
                            return false;
                        }
                        if (toCheck.areOutputFilesUpToDate()) {
                            return false;
                        }
                    }
                }
            }
        }
        if (EventQueue.isDispatchThread()) {
            return false;
        }
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
        if (fo != null && fo.isValid()) {
            INVALIDATOR.accept(fo);
            LOG.log(Level.FINEST, "Invalidated Source for {0}", fo.getNameExt());
            Source src = Source.create(fo);
            if (info != environment.get()) {
                // Another thread got here first - we're not locked yet
                return true;
            }
            ActivityPriority.REALTIME.wrapThrowing(() -> {
                ParserManager.parse(Collections.singleton(src), new UT());
            });
            return true;
        } else {
            EmbeddedAntlrParsers.onAtteptToParseNonExistentFile(this);
            LOG.log(Level.INFO, "File object for {0} disappeared when "
                    + "attempting to force a reparse.", path);
            return false;
        }
    }

    @Override
    int setRunner(Extraction extraction, GrammarRunResult<EmbeddedParser> runner, EmbeddedParser parser) {
        if (disposed) {
            LOG.log(Level.FINE,
                    "Attempt to set new extraction on a disposed parser",
                    new Exception("Attempt to set new extraction on a "
                            + "disposed parser for " + extraction.source()));
            return rev.get();
        }
        int oldRev = rev.get();
//        System.out.println("SET RUNNER " + runner + " for " + (extraction == null ? null : extraction.source().name()));
        if (runner.isUsable()) {
            return Debug.runInt(this, "setRunner-" + extraction.tokensHash()
                    + " listeners " + listeners.size(), runner::toString, () -> {
                EmbeddedParsingEnvironment current = environment.get();
                if (current.shouldReplace(extraction, runner)) {
//                    lastParseInfo.set(placeholderInfo);
                    try {
                        environment.set(new EmbeddedParsingEnvironment(extraction.tokensHash(), runner, parser));
                    } finally {
                        current.dispose();
                    }
                    Set<BiConsumer<? super Extraction, ? super GrammarRunResult<?>>> ll = new HashSet<>(listeners);
                    Debug.message("Pass to " + listeners.size() + " listeners", listeners::toString);
                    for (BiConsumer<? super Extraction, ? super GrammarRunResult<?>> l : ll) {
                        try {
                            l.accept(extraction, runner);
                        } catch (Exception | Error ex) {
                            Exceptions.printStackTrace(ex);
                            if (ex instanceof Error) {
                                throw (Error) ex;
                            }
                        }
                    }
                    return rev.incrementAndGet();
                }
                return rev.get();
            });
        } else {
            Debug.failure("Handed an unusable GrammarRunResult", runner::toString);
            LOG.log(Level.WARNING, "Set an unusable runner on " + path, new Exception());
        }
        return rev.get();
    }

    @Override
    public void accept(Extraction t, GrammarRunResult<EmbeddedParser> runResult, EmbeddedParser parser) {
        Debug.run(this, logName + "-accept-" + t.tokensHash(), runResult::toString, () -> {
            if (reentry.get()) {
                LOG.log(Level.INFO, "Attempt to reenter accept for " + t.source(),
                        new IllegalStateException("Attempt to reenter accept for " + t.source()));
                Debug.failure("reentry", t.tokensHash());
                return;
            }
            LastParseInfo info = lastParseInfo.get();
            if (info != null && info.parserResult != null && info.parserResult.runResult() == runResult) {
                // multiply subscribed - shouldn't happen but did
                return;
            }
            reentry.set(true);
            try {
                LOG.log(Level.FINER, "Got new run result for {0}: {2}, usable? "
                        + "{3} status {4} on {5}", new Object[]{
                            EmbeddedAntlrParserImpl.this, t.source(),
                            runResult.isUsable(), runResult.currentStatus(),
                            logName
                        });
                if (runResult.isUsable()) {
                    setRunner(t, runResult, parser);
                } else {
                    Debug.failure("non-usable", runResult.genResult()::toString);
                    LOG.log(Level.FINE, "Non-usable generation result {0} for {1}"
                            + "; will not use", new Object[]{runResult, t.source()});
                }
            } finally {
                reentry.set(false);
            }
        });
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (unsubscriber != null) {
            unsubscriber.run();
            unsubscriber = null;
        }
        LOG.log(Level.WARNING, "Dispose embedded parser for " + path + " / " + grammarName, new Exception());
        environment.set(new EmbeddedParsingEnvironment(path, grammarName));
    }

    int rev() {
        return rev.get();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void listen(BiConsumer<Extraction, GrammarRunResult<?>> listener) {
        // XXX should pass the last good env in if we have it
        // Should paramterize on String, Grr instead of Extraction so
        // we just pass the tokens hash
        listeners.add(listener);
    }

    @Override
    public void unlisten(BiConsumer<Extraction, GrammarRunResult<?>> listener) {
        listeners.remove(listener);
    }

    static class EmbeddedParsingEnvironment {

        final String grammarTokensHash;
        EmbeddedParser parser;
        final GrammarRunResult<EmbeddedParser> runResult;

        public EmbeddedParsingEnvironment(Path path, String grammarName) {
            LOG.log(Level.FINEST, "Create an initial dummy environment for {0} grammar {1}",
                    new Object[]{path, grammarName});
            grammarTokensHash = "-";
            parser = new DeadEmbeddedParser(path, grammarName);
            runResult = null;
        }

        public EmbeddedParsingEnvironment(String grammarTokensHash, GrammarRunResult<EmbeddedParser> runner, EmbeddedParser parser) {
            LOG.log(Level.FINER, "Create a new EmbeddedParsingEnvironment for run result {0}",
                    runner);
            this.grammarTokensHash = grammarTokensHash;
            // XXX - this is only needed for error highlighting and output window
            // printing, after which it is useless.  Find a way to dispose of
            // it when really done, since it holds the whole Antlr grammar tree
            // in memory unnecessarily
            this.runResult = runner;
            this.parser = parser;
        }

        void dispose() {
            parser = null;
        }

        public UpToDateness status() {
            if (runResult == null) {
                return UpToDateness.STALE;
            }
            return runResult.currentStatus();
        }

        @Override
        public String toString() {
            return "EmbeddedParsingEnvironment(" + grammarTokensHash
                    + ", " + status()
                    + ", " + runResult
                    + ")";
        }

        public boolean isUpToDate() {
            return status() == UpToDateness.CURRENT;
        }

        public boolean shouldReplace(Extraction extraction, GrammarRunResult<EmbeddedParser> runner) {
            if (this.parser == null || this.parser instanceof DeadEmbeddedParser) {
                return true;
            }
            if (runResult != null) {
                AntlrGenerationResult gen = runResult.getWrapped(AntlrGenerationResult.class);
                if (gen != null) {
                    if (gen.grammarFileLastModified > extraction.sourceLastModifiedAtExtractionTime()) {
                        return false;
                    }
                }
            }
            return !isUpToDate();
        }
    }

    static final UT UT = new UT();

    static final class UT extends UserTask {

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            if (Thread.interrupted()) {
//                System.out.println("  UT INTERRUPT");
                LOG.log(Level.FINER, "Interrupted while awaiting parser result");
//                return;
            }
            Parser.Result res = resultIterator.getParserResult();
//            System.out.println("UT PARSED " + res);
            Debug.success("Parse success", () -> {
                return res.toString();
            });
            LOG.log(Level.FINEST, "Forced reparse of {0} gets {1}",
                    new Object[]{resultIterator.getSnapshot().getSource(),
                        res});
        }
    }

    static boolean charSequencesMatchModuloTrailingNewline(CharSequence a, CharSequence b) {
        // Okay, this gets a little tricky:  A CharSeq retrieved from a BaseDocument
        // will always contain a trailing newline - the contract or javax.swing.Document,
        // or at least NetBeans' interpretation of it; the text from a LexerInput will not.
        // So, when the user types a character, many TaskFactories may spring into
        // action and ask for a parse, all with the same document's text, but it will
        // alternate between text with and without a trailing newline.  So if we don't
        // handle that case, we will wind up thrashing new and expensive reparses 4-5
        // times for each keystroke, for the same text
        int aLen = a.length();
        int bLen = b.length();
        if (aLen == bLen) {
            return Strings.charSequencesEqual(a, b);
        }
        if (Math.abs(aLen - bLen) == 1) {
            CharSequence longer = aLen > bLen ? a : b;
            CharSequence shorter = longer == a ? b : a;
            int longerLength = longer == a ? aLen : bLen;
            if (longer.charAt(longerLength - 1) == '\n') {
                int shorterLength = longer == a ? bLen : aLen;
                switch (shorterLength) {
                    case 0:
                        return true;
                    case 1:
                        return longer.charAt(0) == shorter.charAt(0);
                    default:
                        int half = shorterLength / 2;
                        if (shorterLength > 2 && shorterLength % 2 != 0) {
                            half++;
                        }
                        int end = shorterLength - 1;
                        for (int i = 0; i < half; i++) {
                            if (shorter.charAt(i) != longer.charAt(i) || shorter.charAt(end - i) != longer.charAt(end - i)) {
                                return false;
                            }
                        }
                }
                return true;
            }
        }
        return false;
    }

    private final Object pendingLock = new Object();
    private PendingParseInfo ppi;

    EmbeddedAntlrParserResult getOrWaitForParallellResult(CharSequence seq, ThrowingFunction<CharSequence, EmbeddedAntlrParserResult> s) throws Exception {
        PendingParseInfo p;
        boolean needParse = false;
        synchronized (pendingLock) {
            p = ppi;
            needParse = p == null;
            if (needParse) {
                p = new PendingParseInfo(seq);
                ppi = p;
            }
        }
        if (needParse) {
            try {
                EmbeddedAntlrParserResult res = s.apply(seq);
                p.set(res);
                return res;
            } finally {
                synchronized (pendingLock) {
                    if (p == ppi) {
                        ppi = null;
                    }
                }
                p.release();
                p = null;
            }
        } else {
            if (p != null) {
                EmbeddedAntlrParserResult result = p.getIfPresent();
                if (result != null) {
                    return result;
                }
                // Try not to block the EDT
                if (!EventQueue.isDispatchThread()) {
                    result = p.get(seq);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return s.apply(seq);
    }

    static final class PendingParseInfo {

        private final CharSequence seq;
        private final AtomicReference<EmbeddedAntlrParserResult> result = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        PendingParseInfo(CharSequence seq) {
            this.seq = seq;
        }

        void release() {
            latch.countDown();
        }

        EmbeddedAntlrParserResult getIfPresent() {
            return result.get();
        }

        boolean canReuse(CharSequence textToParse) {
            if (textToParse == null) {
                return true;
            }
            if (seq != null && textToParse != null) {
                return charSequencesMatchModuloTrailingNewline(seq, textToParse);
            }
            return seq != null && Strings.charSequencesEqual(seq, textToParse);
        }

        void set(EmbeddedAntlrParserResult result) {
            this.result.set(result);
            release();
        }

        EmbeddedAntlrParserResult get(CharSequence seq) {
            if (canReuse(seq)) {
                EmbeddedAntlrParserResult res = result.get();
                if (res != null) {
                    return res;
                }
                try {
                    latch.await();
                } catch (InterruptedException ex) {
                    LOG.log(Level.INFO, "Interrupted waiting for parse", ex);
                }
                return result.get();
            }
            return null;
        }
    }

    static final class LastParseInfo {

        private final EmbeddedAntlrParserResult parserResult;
        private final CharSequence seq;

        LastParseInfo(EmbeddedAntlrParserResult parserResult, CharSequence seq) {
            this.parserResult = parserResult;
            this.seq = seq;
        }

        LastParseInfo(Path path, String grammarName) {
            this.parserResult = new EmbeddedAntlrParserResult(path, AntlrProxies.forUnparsed(path, grammarName, "-"),
                    null, "-", grammarName);
            this.seq = "-";
        }

        long tokenNamesChecksum() {
            return parserResult.proxy() == null || parserResult.proxy().isUnparsed()
                    ? 0 : parserResult.proxy().tokenNamesChecksum();
        }

        boolean canReuse(CharSequence textToParse) {
            if (textToParse == null && !parserResult.proxy().isUnparsed()) {
                return true;
            }
            if (seq != null && textToParse != null) {
                return charSequencesMatchModuloTrailingNewline(seq, textToParse);
            }
            if (textToParse == null) {
                return false;
            }
            return seq != null && parserResult.runResult() != null
                    && Strings.charSequencesEqual(seq, textToParse);
        }

        @Override
        public String toString() {
            int len = seq.length();
            StringBuilder sb = new StringBuilder("LastParseInfo(")
                    .append(parserResult)
                    .append(" of ").append(len)
                    .append(" chars: '");
            truncated(seq, sb, 20);
            return Strings.escape(sb.toString(), Escaper.NEWLINES_AND_OTHER_WHITESPACE);
        }
    }

    static String truncate(CharSequence seq, int length) {
        return truncated(seq, new StringBuilder(length + 3), length).toString();
    }

    static StringBuilder truncated(CharSequence seq, StringBuilder into, int length) {
        int seqLen = seq.length();
        int len = Math.min(length, seqLen);
        for (int i = 0; i < Math.min(20, len); i++) {
            try {
                into.append(seq.charAt(i));
            } catch (Exception ex) {
                into.append(ex);
            }
        }
        if (seqLen > len) {
            into.append("...'");
        }
        return into;
    }
}

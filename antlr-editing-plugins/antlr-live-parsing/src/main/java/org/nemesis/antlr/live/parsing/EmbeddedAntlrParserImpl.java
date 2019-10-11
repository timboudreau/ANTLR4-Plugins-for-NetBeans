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
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.compilation.AntlrGenerationAndCompilationResult;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.impl.DeadEmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.debug.api.Debug;
import org.nemesis.debug.api.Trackables;
import org.nemesis.extraction.Extraction;
import org.nemesis.jfs.JFSFileModifications;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
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
final class EmbeddedAntlrParserImpl extends EmbeddedAntlrParser implements BiConsumer<Extraction, GrammarRunResult<EmbeddedParser>> {

    private final Set<BiConsumer<? super Extraction, ? super GrammarRunResult<?>>> listeners
            = Collections.synchronizedSet(new WeakSet<>());
    private final AtomicInteger rev = new AtomicInteger();
    private final AtomicReference<EmbeddedParsingEnvironment> environment
            = new AtomicReference<>();
    private static final Consumer<FileObject> INVALIDATOR = SourceInvalidator.create();

    private static final Logger LOG = Logger.getLogger(EmbeddedAntlrParser.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    private Runnable unsubscriber;
    private final String logName;
    private final Path path;
    private final String grammarName;
    private volatile boolean disposed;
    private LastParseInfo lastParseInfo;
    private final LastParseInfo placeholderInfo;
    private final ThreadLocal<Boolean> reentry = ThreadLocal.withInitial(() -> Boolean.FALSE);

    EmbeddedAntlrParserImpl(String logName, Path path, String grammarName) {
        this.logName = logName;
        this.path = path;
        this.grammarName = grammarName;
        environment.set(new EmbeddedParsingEnvironment(path, grammarName));
        lastParseInfo = placeholderInfo = new LastParseInfo(path, grammarName);
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

    boolean isUpToDate() {
        return environment.get().isUpToDate();
    }

    void setUnsubscriber(Runnable unsubscriber) {
        this.unsubscriber = notNull("unsubscriber", unsubscriber);
    }

    private boolean checkStaleAndReparseGrammarIfNeeded(EmbeddedParsingEnvironment info) throws Exception {
        if (disposed) {
            LOG.log(Level.INFO, "Stale check in disposed EmbeddedAntlrParser",
                    new Exception("Stale check in disposed EmbeddedAntlrParser."
                            + " Nothing should be using this."));
            return false;
        }
        if (!info.isUpToDate()) {
            LOG.log(Level.FINE, "{0} force reparse due to {2}",
                    new Object[]{path.getFileName(), info});
            forceGrammarFileReparse(info);
            return true;
        }
        return false;
    }

    @Override
    public synchronized EmbeddedAntlrParserResult parse(CharSequence textToParse) throws Exception {
        // XXX - this sucks, but lexer / snapshot char sequences explode on
        // contact after a while
        String toParse = textToParse.toString();
        // Return the cached lastParseInfo where possible, ignoring cases where the
        // text is null (in which case, we are being invoked just for the lexer to get
        // the list of token types)
        return Debug.runObjectThrowing(this, logName + "-" + environment.get().grammarTokensHash, () -> {
            EmbeddedParsingEnvironment info = environment.get();
            boolean wasStale = checkStaleAndReparseGrammarIfNeeded(info);
            if (wasStale) {
                EmbeddedParsingEnvironment newInfo = environment.get();
                LOG.log(Level.FINER, "Stale check {0} replaced parser env? {1}",
                        new Object[]{grammarName,
                            newInfo != info});
                Debug.message("Replace env " + (info != newInfo), newInfo::toString);
                info = newInfo;
            }
            LastParseInfo lpi = this.lastParseInfo;
            if (lpi.canReuse(toParse)) {
                LOG.log(Level.FINEST, "Reuse previous parser result {0} "
                        + "for same or null text", lpi);
                Debug.message(logName + "-reuse-" + info.grammarTokensHash,
                        lpi::toString);
                return lpi.parserResult;
            }
            AntlrProxies.ParseTreeProxy res = info.parser.parse(logName, toParse);
            LOG.log(Level.FINEST, "Parsed to {0} by {1}",
                    new Object[]{res.loggingInfo(), info.parser});
            String gth = info.grammarTokensHash;
            EmbeddedAntlrParserResult result = new EmbeddedAntlrParserResult(res,
                    info.runResult, info.grammarTokensHash);
            if (toParse != null) {
                lastParseInfo = new LastParseInfo(result, toParse);
                Trackables.track(AntlrProxies.ParseTreeProxy.class, res, () -> {
                    return res.loggingInfo() + "\t" + gth + "\n" + logName;
                });
            }
            LOG.log(Level.FINE, "New parser result {0}", result);
            Debug.success(logName + "-created-" + res.loggingInfo(), result::toString);
            return result;
        });
    }

    private void forceGrammarFileReparse(EmbeddedParsingEnvironment info) throws Exception {
        Debug.runThrowing(this, logName + "-force-reparse-" + info.grammarTokensHash, () -> {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(path.toFile()));
            INVALIDATOR.accept(fo);
            LOG.log(Level.FINEST, "Invalidated Source for {0}", fo.getNameExt());
            Source src = Source.create(fo);
            ParserManager.parse(Collections.singleton(src), new UT());
        });
    }

    @Override
    int setRunner(Extraction extraction, GrammarRunResult<EmbeddedParser> runner) {
        if (disposed) {
            LOG.log(Level.FINE,
                    "Attempt to set new extraction on a disposed parser",
                    new Exception("Attempt to set new extraction on a "
                            + "disposed parser for " + extraction.source()));
            return rev.get();
        }
        if (runner.isUsable()) {
            return Debug.runInt(this, "setRunner-" + extraction.tokensHash()
                    + " listeners " + listeners.size(), runner::toString, () -> {
                EmbeddedParsingEnvironment current = environment.get();
                if (current.shouldReplace(extraction, runner)) {
                    synchronized (this) {
                        lastParseInfo = placeholderInfo;
                    }
                    environment.set(new EmbeddedParsingEnvironment(extraction.tokensHash(), runner));
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
        }
        return rev.get();
    }

    @Override
    public void accept(Extraction t, GrammarRunResult<EmbeddedParser> runResult) {
        Debug.run(this, logName + "-accept-" + t.tokensHash(), runResult::toString, () -> {
            System.out.println("ACCEPT " + t.tokensHash() + " w/ " + runResult);
            if (reentry.get()) {
                LOG.log(Level.INFO, "Attempt to reenter accept for " + t.source(),
                        new IllegalStateException("Attempt to reenter accept for " + t.source()));
                Debug.failure("reentry", t.tokensHash());
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
                AntlrGenerationAndCompilationResult g = runResult.genResult();
                AntlrGenerationResult gg = g.generationResult();
                Path p = t.source().lookup(Path.class).isPresent() ? t.source().lookup(Path.class).get() : null;

                Path actualGeneratedGrammar = g.jfs().originOf(gg.grammarFile, Path.class);
                if (!path.equals(actualGeneratedGrammar) || (p != null && !p.equals(path)) || !grammarName.equals(gg.grammarName)) {
                    String msg = "EmbeddedAntlrParser " + this + " was passed a GrammarRunResult whose "
                            + " Antlr generation result belongs to another file"
                            + "\nPath  : " + path
                            + "\nExtsrc: " + p
                            + "\nGensrc: " + gg.grammarFile
                            + "\nGPaths: " + runResult.sources()
                            + "\nMy Grammar Name: " + grammarName
                            + "\nRR Grammar Name: " + gg.grammarName;
                    LOG.log(Level.SEVERE, msg, new Exception(msg));
                    Debug.failure("wrong-path", msg);
                    return;
                }

                if (runResult.isUsable()) {
                    setRunner(t, runResult);
                } else {
                    Debug.failure("non-usable", g::toString);
                    LOG.log(Level.FINE, "Non-usable generation result {0} for {1}"
                            + "; will not use", new Object[]{runResult, t.source()});
                    System.out.println("non-usable run result, not setting");
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
        environment.set(new EmbeddedParsingEnvironment(path, grammarName));
    }

    int rev() {
        return rev.get();
    }

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
        final EmbeddedParser parser;
        final JFSFileModifications modifications;
        final GrammarRunResult<EmbeddedParser> runResult;

        public EmbeddedParsingEnvironment(Path path, String grammarName) {
            grammarTokensHash = "-";
            modifications = JFSFileModifications.empty();
            parser = new DeadEmbeddedParser(path, grammarName);
            runResult = null;
        }

        public EmbeddedParsingEnvironment(String grammarTokensHash, GrammarRunResult<EmbeddedParser> runner) {
            this.grammarTokensHash = grammarTokensHash;
            // XXX - this is only needed for error highlighting and output window
            // printing, after which it is useless.  Find a way to dispose of
            // it when really done, since it holds the whole Antlr grammar tree
            // in memory unnecessarily
            this.runResult = runner;
            JFSFileModifications mods = JFSFileModifications.empty();
            if (runner != null && runner.genResult() != null) {
                if (runner.genResult().generationResult() != null) {
                    if (runner.genResult().generationResult().filesStatus != null) {
                        mods = runner.genResult().generationResult().filesStatus.snapshot();
                    }
                }
            }
            this.modifications = mods;
            this.parser = runner.get();
        }

        @Override
        public String toString() {
            return "EmbeddedParsingEnvironment(" + grammarTokensHash
                    + ", " + modifications
                    + ", " + runResult
                    + ")";
        }

        public boolean isUpToDate() {
            if (modifications.isEmpty()) {
                return false;
            }
            JFSFileModifications.FileChanges changes = modifications.changes();
            boolean result = changes.isUpToDate();
            if (!result) {
                LOG.log(Level.FINEST, "Parse env not up to date {0}", changes);
            }
            return result;
        }

        public boolean shouldReplace(Extraction extraction, GrammarRunResult<EmbeddedParser> runner) {
            if (this.parser == null || this.parser instanceof DeadEmbeddedParser) {
                return true;
            }
            if (Objects.equals(extraction.tokensHash(), grammarTokensHash)) {
                return false;
            }
            if (!modifications.isEmpty()) {
                modifications.refresh();
            }
            return true;
        }
    }

    static final class UT extends UserTask {

        @Override
        public void run(ResultIterator resultIterator) throws Exception {
            if (Thread.interrupted()) {
                return;
            }
            Parser.Result res = resultIterator.getParserResult();
            Debug.success("Parse success", () -> {
                return res.toString();
            });
            LOG.log(Level.FINEST, "Forced reparse of {0} gets {1}",
                    new Object[]{resultIterator.getSnapshot().getSource(),
                        res});
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
            this.parserResult = new EmbeddedAntlrParserResult(AntlrProxies.forUnparsed(path, grammarName, "-"),
                    null, "-");
            this.seq = "-";
        }

        boolean canReuse(CharSequence textToParse) {
            if (textToParse == null && !parserResult.proxy().isUnparsed()) {
                return true;
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

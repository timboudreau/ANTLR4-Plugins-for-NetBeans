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

import java.util.function.BiConsumer;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.extraction.Extraction;

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
public abstract class EmbeddedAntlrParser {

    EmbeddedAntlrParser() {
    }

    /**
     * Dispose of this instance, causing it to cease getting notifications of
     * grammar changes even if it is still strongly referenced. Any subsequent
     * calls to parse will return <code>AntlrProxies.forUnparsed</code>.
     */
    public abstract void dispose();

    public abstract EmbeddedAntlrParserResult parse(CharSequence textToParse) throws Exception;

    abstract int setRunner(Extraction extraction, GrammarRunResult<EmbeddedParser> runner);

    /**
     * Subscribe to notifications that the underlying Antlr grammar has been
     * re-parsed.
     *
     * @param listener A runnable, which will be weakly referenced
     */
    public abstract void listen(BiConsumer<Extraction, GrammarRunResult<?>> listener);

    /**
     * Unsubscribe from notifications about changes in the underlying grammar.
     *
     * @param listener
     */
    public abstract void unlisten(BiConsumer<Extraction, GrammarRunResult<?>> listener);

    // Really at this point these two methods are only for tests:
    abstract boolean isUpToDate();

    public abstract boolean isDisposed();

    abstract int rev();

    /*
    public EmbeddedAntlrParserResult parse(String t, int ruleNo) throws Exception {
    CurrentParser cp = runner.get();
    boolean wasStale = checkStale(cp);
    EmbeddedAntlrParserResult result = lastResultIfMatches(wasStale, t);
    if (result != null) {
    return result;
    }
    if (wasStale) {
    cp = runner.get();
    }
    GrammarRunResult<EmbeddedParser> r = cp == null ? null : cp.runResult;
    if (r == null || r.get() == null) {
    return AntlrProxies.forUnparsed(path, grammarName, t);
    }
    return setTextAndResult(t, r.get().parse(logName, t, ruleNo));
    }
    public ParseTreeProxy parse(String t, String ruleName) throws Exception {
    CurrentParser cp = runner.get();
    boolean wasStale = checkStale(cp);
    ParseTreeProxy result = lastResultIfMatches(wasStale, t);
    if (result != null) {
    return result;
    }
    if (wasStale) {
    cp = runner.get();
    }
    GrammarRunResult<EmbeddedParser> r = cp == null ? null : cp.runResult;
    if (r == null || r.get() == null) {
    return AntlrProxies.forUnparsed(path, grammarName, t);
    }
    return setTextAndResult(t, r.get().parse(logName, t, ruleName));
    }
     */
}

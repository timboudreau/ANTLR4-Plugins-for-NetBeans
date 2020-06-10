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

import com.mastfrog.util.collections.CollectionUtils;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.antlr.live.execution.AntlrRunSubscriptions;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.impl.EmbeddedParser;
import org.nemesis.antlr.live.parsing.impl.ReparseListeners;
import org.nemesis.debug.api.Debug;
import org.nemesis.misc.utils.CachingSupplier;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public final class EmbeddedAntlrParsers {

    private static final Logger LOG = Logger.getLogger(EmbeddedAntlrParsers.class.getName());
    private final Map<FileObject, Set<EmbeddedAntlrParserImpl>> liveParsersForFile
            = CollectionUtils.concurrentSupplierMap(() -> {
                return Collections.synchronizedSet(CollectionUtils.weakSet());
            });

    private static final Supplier<EmbeddedAntlrParsers> INSTANCE_SUPPLIER
            = CachingSupplier.of(EmbeddedAntlrParsers::new);

    private static EmbeddedAntlrParsers instance() {
        return INSTANCE_SUPPLIER.get();
    }

    static void onAtteptToParseNonExistentFile(EmbeddedAntlrParserImpl impl) {
        instance().onDeadFile(impl);
    }

    void onDeadFile(EmbeddedAntlrParserImpl parser) {
        Debug.message("onDeadFile", parser::toString);
        // Called if the associated file no longer exists - try
        // to dispose class loaders, etc.
        Set<FileObject> dead = new HashSet<>();
        for (Map.Entry<FileObject, Set<EmbeddedAntlrParserImpl>> e : liveParsersForFile.entrySet()) {
            boolean removed = e.getValue().remove(parser);
            if (e.getValue().isEmpty()) {
                dead.add(e.getKey());
            }
            if (!e.getKey().isValid()) {
                for (EmbeddedAntlrParserImpl ee : e.getValue()) {
                    if (ee != parser) {
                        ee.dispose();
                    }
                }
                e.getValue().clear();
                dead.add(e.getKey());
            }
            if (removed) {
                parser.dispose();
            }
        }
        for (FileObject fo : dead) {
            liveParsersForFile.remove(fo);
        }
    }

    /**
     * Get an embedded antlr parser which can be passed some text which will be
     * processed using an in-memory-compiled version of the passed Antlr grammar
     * file, returning an analysis of the syntax tree, any errors encountered,
     * etc. In the case that an exception is thrown or compilation fails, an
     * unparsed representation with a single token type will be returned.
     *
     * @param grammar A grammar
     * @return A parser
     */
    public static EmbeddedAntlrParser forGrammar(String logName, FileObject grammar) {
        return instance()._subscribe(logName, grammar);
    }

    /**
     * Subscribe to analyses for a particular path; when any EmbeddedAntlrParser
     * parses something using the given grammar file path, the listener will get
     * a notification.
     *
     * @param path The path
     * @param listener A listener
     */
    public static void listen(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        ReparseListeners.listen(path, listener);
    }

    public static void unlisten(Path path, BiConsumer<? super GrammarRunResult<EmbeddedParser>, ? super AntlrProxies.ParseTreeProxy> listener) {
        ReparseListeners.unlisten(path, listener);
    }

    private EmbeddedAntlrParser find(Set<EmbeddedAntlrParserImpl> parsers) {
        for (EmbeddedAntlrParserImpl e : parsers) {
            if (e != null && !e.isDisposed()) {
                return e;
            }
        }
        return null;
    }

    private EmbeddedAntlrParser _subscribe(String logName, FileObject grammar) {
        Set<EmbeddedAntlrParserImpl> set = liveParsersForFile.get(grammar);
        EmbeddedAntlrParserImpl p;
//        EmbeddedAntlrParser p = find(set);
//        if (p != null) {
//            return p;
//        }

        Path path = FileUtil.toFile(grammar).toPath();
        p = new EmbeddedAntlrParserImpl(logName, path, grammar.getName());

        LOG.log(Level.FINE, "Create EmbeddedAntlrParser for {0} with "
                + " {1} subscribers",
                new Object[]{grammar.getPath(), set.size()});
        Runnable unsubscriber = lastUn = AntlrRunSubscriptions
                .forType(EmbeddedParser.class)
                .subscribe(grammar, p);
        p.setUnsubscriber(unsubscriber);
        set.add(p);
        return p;
    }

    Runnable lastUn;
}

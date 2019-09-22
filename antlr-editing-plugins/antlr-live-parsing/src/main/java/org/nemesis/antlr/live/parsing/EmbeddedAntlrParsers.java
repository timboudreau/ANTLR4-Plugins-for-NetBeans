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

import com.mastfrog.util.collections.CollectionUtils;
import java.nio.file.Path;
import java.util.Collections;
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
import org.nemesis.misc.utils.CachingSupplier;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class EmbeddedAntlrParsers {

    private static final Logger LOG = Logger.getLogger(EmbeddedAntlrParsers.class.getName());
    private final Map<FileObject, Set<EmbeddedAntlrParser>> liveParsersForFile
            = CollectionUtils.concurrentSupplierMap(() -> {
                return Collections.synchronizedSet(CollectionUtils.weakSet());
            });

    private static final Supplier<EmbeddedAntlrParsers> INSTANCE_SUPPLIER
            = CachingSupplier.of(EmbeddedAntlrParsers::new);

    private static EmbeddedAntlrParsers instance() {
        return INSTANCE_SUPPLIER.get();
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

    private EmbeddedAntlrParser find(Set<EmbeddedAntlrParser> parsers) {
        for (EmbeddedAntlrParser e : parsers) {
            if (e != null && !e.isDisposed()) {
                return e;
            }
        }
        return null;
    }

    private EmbeddedAntlrParser _subscribe(String logName, FileObject grammar) {
        Set<EmbeddedAntlrParser> set = liveParsersForFile.get(grammar);

        EmbeddedAntlrParser p;
//        EmbeddedAntlrParser p = find(set);
//        if (p != null) {
//            return p;
//        }

        Path path = FileUtil.toFile(grammar).toPath();
        p = new EmbeddedAntlrParser(logName, path, grammar.getName());

        LOG.log(Level.FINE, "Create EmbeddedAntlrParser for {0} with "
                + " {1} subscribers",
                new Object[]{grammar.getPath(), set.size()});
        Runnable unsubscriber = AntlrRunSubscriptions
                .forType(EmbeddedParser.class)
                .subscribe(grammar, p.subscriber);
        p.unsubscriber = unsubscriber;
        set.add(p);
        return p;
    }
}

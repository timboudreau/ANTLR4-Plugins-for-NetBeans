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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.function.BooleanSupplier;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.nemesis.antlr.spi.language.AntlrParseResult;
import org.nemesis.antlr.spi.language.NbParserHelper;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.SyntaxError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarSummary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.Collector;
import org.nemesis.extraction.Extraction;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.ParsingBag;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 *
 * @author Tim Boudreau
 */
public class ParseHelper extends NbParserHelper<ANTLRv4Parser, ANTLRv4Lexer, AntlrParseResult, ANTLRv4Parser.GrammarFileContext>{

    public static final AntlrParseResult.Key<GrammarSummary> GRAMMAR_SUMMARY_KEY =
                AntlrParseResult.key("summary", GrammarSummary.class);
    public static final AntlrParseResult.Key<ANTLRv4SemanticParser> SEMANTIC_CHECKER_KEY =
                AntlrParseResult.key("semantics", ANTLRv4SemanticParser.class);

    @Override
    protected boolean onErrorNode(ErrorNode nd, ParseResultContents populate) {
        return super.onErrorNode(nd, populate); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected ErrorDescription convertError(Snapshot snapshot, SyntaxError error) {
        return super.convertError(snapshot, error); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void onParseCompleted(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            ParseResultContents populate, Fixes fixes, BooleanSupplier cancelled) throws Exception {
        
        ParsingBag bag = ParsingBag.forGrammarSource(extraction.source());
        Collector coll = bag.get(Collector.class);
        ANTLRv4SemanticParser p = bag.get(ANTLRv4SemanticParser.class);
        populate.put(GRAMMAR_SUMMARY_KEY, coll.summary());
        populate.put(SEMANTIC_CHECKER_KEY, p);
//        p.enterGrammarFile(tree);
//        tree.accept(p);
        super.onParseCompleted(tree, extraction, populate, fixes, cancelled);
    }

    @Override
    protected void onCreateAntlrParser(ANTLRv4Lexer lexer, ANTLRv4Parser parser, Snapshot snapshot) throws Exception {
        GrammarSource<Snapshot> source = GrammarSource.find(snapshot, "text/x-g4");
        ParsingBag bag = ParsingBag.forGrammarSource(source);
        Collector collector = new Collector(bag);

        bag.put(Collector.class, collector);
        parser.addParseListener(collector);
        ANTLRv4SemanticParser p = new ANTLRv4SemanticParser(bag.source(), collector.summary());
        bag.put(ANTLRv4SemanticParser.class, p);
        parser.addParseListener(p);
    }
}

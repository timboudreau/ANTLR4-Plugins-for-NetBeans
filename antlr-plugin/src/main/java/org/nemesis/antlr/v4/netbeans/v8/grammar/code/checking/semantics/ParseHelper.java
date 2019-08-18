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

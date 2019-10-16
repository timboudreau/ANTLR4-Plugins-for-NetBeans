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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking;

import java.io.IOException;

import java.util.List;

import java.util.logging.Logger;

import org.antlr.v4.runtime.CommonTokenStream;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.syntax.ANTLRv4SyntacticErrorListener;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink.parser.HyperlinkParser;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.Collector;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarSummary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.ParsingBag;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4GrammarChecker {

    private static final Logger LOG = Logger.getLogger("ANTLR plugin:" + ANTLRv4GrammarChecker.class.getName());

    private ANTLRv4Parser parser;
    private String grammarName;
    private GrammarType grammarType;
    private String firstParserRule;
    private ANTLRv4SyntacticErrorListener syntacticErrorListener;
    private ANTLRv4SemanticParser semanticParser;
    private List<ParsingError> semanticErrors;
    private List<ParsingError> semanticWarnings;

    public ANTLRv4Parser getParser() {
        return parser;
    }

    public String getGrammarName() {
        return grammarName;
    }

    public GrammarType getGrammarType() {
        return this.grammarType;
    }

    public String getFirstParserRule() {
        return this.firstParserRule;
    }

    public ANTLRv4SyntacticErrorListener getSyntacticErrorListener() {
        return syntacticErrorListener;
    }

    public ANTLRv4SemanticParser getSemanticParser() {
        return semanticParser;
    }

    public boolean encounteredSyntacticError() {
        return syntacticErrorListener == null
                ? false
                : syntacticErrorListener.encounteredError();
    }

    public List<ParsingError> getSyntacticErrors() {
        return syntacticErrorListener == null
                ? null : syntacticErrorListener.getParsingError();
    }

    public List<ParsingError> getSemanticErrors() {
        return semanticErrors;
    }

    public int getSemanticErrorNumber() {
        return semanticErrors == null ? 0 : semanticErrors.size();
    }

    public boolean encounteredSemanticError() {
        return semanticErrors == null
                ? false
                : !semanticErrors.isEmpty();
    }

    public List<ParsingError> getSemanticWarnings() {
        return semanticWarnings;
    }

    public int getSemanticWarningNumber() {
        return semanticWarnings == null ? 0 : semanticWarnings.size();
    }

    public boolean encounteredSemanticWarning() {
        return semanticWarnings == null
                ? false
                : !semanticWarnings.isEmpty();
    }
    /**
     *
     * @param doc : may be null. If it is null no hyperlink parsing will occur
     */
    private final GrammarSource<?> src;
    private final ParsingBag  bag;

    public ANTLRv4GrammarChecker(ParsingBag bag) {
//        assert grammarFilePath != null;
        this.src = bag.source();
        this.bag = bag;
        this.parser = null;
        this.grammarName = null;
        this.grammarType = GrammarType.UNDEFINED;
        this.firstParserRule = null;
        this.syntacticErrorListener = null;
        this.semanticParser = null;
        this.semanticErrors = null;
        this.semanticWarnings = null;
    }

    public GrammarSummary check() throws IOException {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(src.stream());
        lexer.removeErrorListeners();

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        parser = new ANTLRv4Parser(tokens);
        parser.removeErrorListeners();

        syntacticErrorListener = new ANTLRv4SyntacticErrorListener(src);
        parser.removeErrorListeners(); // remove ConsoleErrorListener
        parser.addErrorListener(syntacticErrorListener); // add ours
        GrammarSummary summary = bag.get(GrammarSummary.class);

        // If we are in an undefined project type, we do nothing
//            if (projectType != ProjectType.UNDEFINED) {
//            }
        // First step : we parse our grammar
        ParseTree tree = parser.grammarFile();

        // Second step: we walk through parse tree in order to recover semantic
        // info and determine if there are semantic errors
        // If we are in an undefined project type, we do nothing
//            if (projectType != ProjectType.UNDEFINED) {
        ParseTreeWalker walker = new ParseTreeWalker();
        if (summary == null) {
            // We add a collector in charge of collecting a summary of grammar
            // (summary is attached to parsed document as a property with
            // GrammarSummary.class as a key)
            Collector collector = new Collector(bag);
            parser.addParseListener(collector);
            walker.walk(collector, tree);
            summary = collector.summary();
            assert summary != null : "Collector did not create a summary";
            bag.put(GrammarSummary.class, summary);
        }

        assert bag.get(GrammarSummary.class) != null : "Summary should be in bag but is not: " + bag;

        semanticParser = new ANTLRv4SemanticParser(src, summary);
        walker.walk(semanticParser, tree);
        semanticErrors = semanticParser.getSemanticErrors();
        semanticWarnings = semanticParser.getSemanticWarnings();

        // We recover info about our grammar and we run some post-process
        // checkings
        grammarName = semanticParser.getGrammarName();
        grammarType = semanticParser.getGrammarType();
        // We recover the first imported parser rule
        firstParserRule = semanticParser.getFirstParserRule();

        // We launch a post walk check
        semanticParser.check();

        // third step: we walk through parse tree again in order to prepare
        // hyperlinks
        HyperlinkParser hyperlinkParser = new HyperlinkParser(bag);
        walker.walk(hyperlinkParser, tree);
//            }
        return summary;
    }
}

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
package org.nemesis.antlr.file.impl;

import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.file.impl.OrganizeRules.RuleEntry;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.simple.SampleFile;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class OrganizeRulesTest {

    @Test
    public void testOrganizing() throws Exception {
        NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(RULE_BOUNDS);
        List<RuleEntry> rls = OrganizeRules.organizeRules(doc, ruleBounds, extraction.referenceGraph(RULE_NAME_REFERENCES));
        Set<List<String>> pairs = new HashSet<>();
        for (RuleEntry re : rls) {
            for (RuleEntry re2 : rls) {
                if (re2 == re || re2.kind() != re.kind()) {
                    continue;
                }
                if (re.hasInitialAtomMatch(re2)) {
                    List<String> p = Arrays.asList(re.name(), re2.name());
                    Collections.sort(p);
                    if (pairs.add(p)) {
                        System.out.println(re.name() + " shares atoms with  " + re2.name());
                        System.out.println("   atoms: " + re.initialAtoms() + " and " + re2.initialAtoms());
                    }
                }
            }
        }

        OrganizeRules.updateDocument(ruleBounds, doc, rls);
        System.out.println(doc.getText(0, doc.getLength()));

        Extraction re = reextract(doc);
        NamedSemanticRegions<RuleTypes> newRuleBounds = re.namedRegions(RULE_BOUNDS);
        assertNotNull(newRuleBounds);
        assertNotEquals(newRuleBounds, ruleBounds);

        Set<String> oldRuleNames = new TreeSet<>(ruleBounds.allNames());
        Set<String> newRuleNames = new TreeSet<>(newRuleBounds.allNames());
        assertEquals(oldRuleNames, newRuleNames, () -> {
            return "Organizing changed the set of names in the document.  Disjunction: "
                    + CollectionUtils.disjunction(oldRuleNames, newRuleNames)
                    + " old set:\n" + oldRuleNames + "\n new set:\n" + newRuleNames;
        });
        // XXX once the algorithm is stablized, add more tests here
    }

    static SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile;
    static ANTLRv4Parser.GrammarFileContext grammar;
    static Extraction extraction;
    static StyledDocument doc;

    @BeforeAll
    public static void setup() throws IOException, BadLocationException {
        extraction = nmExtraction();
        doc = new DefaultStyledDocument();
        doc.insertString(0, sampleFile.text(), null);
    }

    static Extraction reextract(StyledDocument doc) throws Exception {
        String text = doc.getText(0, doc.getLength());
        ErrCheck check = new ErrCheck(() -> text);
        ExtractorBuilder<ANTLRv4Parser.GrammarFileContext> eb = Extractor.builder(ANTLRv4Parser.GrammarFileContext.class, ANTLR_MIME_TYPE);
        AntlrExtractor.populateBuilder(eb);
        Extractor<ANTLRv4Parser.GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = new ANTLRv4Lexer(CharStreams.fromString(text));
        lex.removeErrorListeners();
        lex.addErrorListener(check);
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lex));
        parser.removeErrorListeners();
        parser.addErrorListener(check);

        Extraction result = ext.extract(parser.grammarFile(),
                GrammarSource.find(sampleFile.charStream(),
                        ANTLR_MIME_TYPE), tokens);
        parser.reset();
        parser.grammarFile().accept(check);
        return result;
    }

    static Extraction nmExtraction() throws IOException {
        sampleFile = sampleFile();
        ErrCheck check = new ErrCheck(() -> {
            try {
                return sampleFile.text();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return ex.getMessage();
            }
        });
        ExtractorBuilder<ANTLRv4Parser.GrammarFileContext> eb = Extractor.builder(ANTLRv4Parser.GrammarFileContext.class, ANTLR_MIME_TYPE);
        AntlrExtractor.populateBuilder(eb);
        Extractor<ANTLRv4Parser.GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = sampleFile.lexer();
        lex.removeErrorListeners();
        lex.addErrorListener(check);
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = sampleFile.parser();
        parser.removeErrorListeners();
        parser.addErrorListener(check);
        Extraction nmExtraction = ext.extract(parser.grammarFile(),
                GrammarSource.find(sampleFile.charStream(),
                        ANTLR_MIME_TYPE), tokens);
        grammar = parser.grammarFile();
        assertFalse(nmExtraction.isPlaceholder());
        return nmExtraction;
    }

    static SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile() {
        if (true) {
            return AntlrSampleFiles.RUST;
        }
        return AntlrSampleFiles.MARKDOWN_PARSER.withText(orig -> {
            try {
                String lexerText = AntlrSampleFiles.MARKDOWN_LEXER.text();
                int ix = lexerText.indexOf("OpenHeading");
                return orig.replace("parser grammar", "grammar") + "\n" + lexerText.substring(ix) //                        + "\n\n// trailing comment"
                        ;
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        });
    }

    static class ErrCheck extends ANTLRv4BaseVisitor<Void> implements ANTLRErrorListener {

        private final Supplier<String> text;

        public ErrCheck(Supplier<String> text) {
            this.text = text;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            if (offendingSymbol instanceof Token && ((Token) offendingSymbol).getType() == -1) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String[] lines = text.get().split("\n");
            for (int i = line - 2; i < lines.length; i++) {
                sb.append(lines[i]).append('\n');
            }
            throw new AssertionError("Organize rules generated a document with syntax errors: " + offendingSymbol + " at " + line + ":" + charPositionInLine + ": " + msg + "\n" + sb
                    + "\n\nFull text:\n" + text.get());
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        }

        @Override
        public Void visitErrorNode(ErrorNode node) {
            throw new AssertionError("Organize rules generated a document with parse errors: " + node + " @ " + node.getText());
        }
    }
}

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
package org.nemesis.antlr.file.antlrrefactoring;

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.createProblem;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.warningProblem;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.util.NbBundle.Messages;

/**
 * Extracts just the atoms and surrounding punctuation from one parser, lexer or
 * fragment rule, for use when inlining rules. Omits actions, alternative labels
 * and atom labels, but records that they were there so the user can be warned
 * and decide whether or not to proceed.
 *
 * @author Tim Boudreau
 */
final class RuleBodyExtractor extends ANTLRv4BaseVisitor<CharSequence> {

    private final StringBuilder sb = new StringBuilder();
    private final String ruleName;
    private boolean active;
    private int blockCount;
    private int labeledAlternativeCount;
    private int actionBlockCount;
    private int labeledAtomCount;
    private int atomCount;
    private final ANTLRv4Parser parser;
    private boolean ruleFound;
    private int whitespaceCharsUpToAndIncludingPrecedingNewline = -1;
    private int whitespaceCharsUpToAndIncludingSubsequentNewline = -1;

    private int alternativesInCurrentBlock;
    private boolean inAltList;
    private boolean hasParseErrors;
    private String fullRuleText;

    RuleBodyExtractor(String ruleName, ANTLRv4Parser parser) {
        this.ruleName = ruleName;
        this.parser = parser;
    }

    @Override
    public CharSequence visitErrorNode(ErrorNode node) {
        hasParseErrors = true;
        return sb;
    }

    public boolean hasParseErrors() {
        return hasParseErrors;
    }

    public int blockCount() {
        return blockCount;
    }

    public int actionBlockCount() {
        return actionBlockCount;
    }

    public int labeledAtomCount() {
        return labeledAtomCount;
    }

    public int labeledAlternativeCount() {
        return labeledAlternativeCount;
    }

    public int atomCount() {
        return atomCount;
    }

    public boolean wasRuleFound() {
        return ruleFound;
    }

    public String fullRuleText() {
        return fullRuleText;
    }

    @Override
    protected CharSequence defaultResult() {
        return sb;
    }

    @Override
    public CharSequence visitChildren(RuleNode node) {
        return super.visitChildren(node);
    }

    public int surroundingWhitespaceCharsUpToAndIncludingPrecedingNewline() {
        return whitespaceCharsUpToAndIncludingPrecedingNewline;
    }

    public int surroundingWhitespaceCharsUpToAndIncludingSubsequentNewline() {
        return whitespaceCharsUpToAndIncludingSubsequentNewline;
    }

    private void captureSurroundingWhitespaceInfo(ParserRuleContext targetRule) {
        Interval ival = targetRule.getSourceInterval();
        if (ival.a > 0) {
            Token prev = parser.getTokenStream().get(ival.a - 1);
            String txt = prev.getText();
            if (Strings.isBlank(txt)) {
                int lix = txt.lastIndexOf('\n');
                if (lix >= 0) {
                    whitespaceCharsUpToAndIncludingPrecedingNewline = txt.length() - lix;
                }
            }
        }
        if (ival.b < parser.getTokenStream().size()) {
            Token next = parser.getTokenStream().get(ival.b + 1);
            if (next.getType() == ANTLRv4Lexer.SEMI && ival.b + 1 < parser.getTokenStream().size()) {
                next = parser.getTokenStream().get(ival.b + 2);
            }
            if (next.getType() != TokenStream.EOF) {
                String txt = next.getText();
                if (Strings.isBlank(txt)) {
                    int ix = txt.indexOf('\n');
                    if (ix >= 0) {
                        whitespaceCharsUpToAndIncludingSubsequentNewline = ix + 1;
                    }
                }
            }
        }
    }

    @Override
    public CharSequence visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        active = ruleName.equals(ctx.parserRuleDeclaration().parserRuleIdentifier().getText());
        ruleFound |= active;
        if (!active) {
            return sb;
        } else {
            captureSurroundingWhitespaceInfo(ctx);
            fullRuleText = ctx.getText();
        }
        try {
            return super.visitParserRuleSpec(ctx);
        } finally {
            active = false;
        }
    }

    @Override
    public CharSequence visitTokenRuleSpec(ANTLRv4Parser.TokenRuleSpecContext ctx) {
        active = ruleName.equals(ctx.tokenRuleDeclaration().tokenRuleIdentifier().getText());
        ruleFound |= active;
        if (!active) {
            return sb;
        } else {
            captureSurroundingWhitespaceInfo(ctx);
            fullRuleText = ctx.getText();
        }
        try {
            return super.visitTokenRuleSpec(ctx);
        } finally {
            active = false;
        }
    }

    @Override
    public CharSequence visitFragmentRuleSpec(ANTLRv4Parser.FragmentRuleSpecContext ctx) {
        active = ruleName.equals(ctx.fragmentRuleDeclaration().fragmentRuleIdentifier().getText());
        ruleFound |= active;
        if (!active) {
            return sb;
        } else {
            captureSurroundingWhitespaceInfo(ctx);
            fullRuleText = ctx.getText();
        }
        try {
            return super.visitFragmentRuleSpec(ctx);
        } finally {
            active = false;
        }
    }

    @Override
    public CharSequence visitParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext ctx) {
        return sb;
    }

    @Override
    public CharSequence visitLabeledParserRuleElement(ANTLRv4Parser.LabeledParserRuleElementContext ctx) {
        if (ctx.ASSIGN() != null && ctx.parserRuleAtom() != null) {
            labeledAtomCount++;
            return visitParserRuleAtom(ctx.parserRuleAtom());
        }
        return extractText(ctx);
    }

    @Override
    public CharSequence visitParserRuleAtom(ANTLRv4Parser.ParserRuleAtomContext ctx) {
        atomCount++;
        return extractText(ctx);
    }

    @Override
    public CharSequence visitLexerRuleAtom(ANTLRv4Parser.LexerRuleAtomContext ctx) {
        atomCount++;
        return extractText(ctx);
    }

    @Override
    public CharSequence visitParserRuleLabeledAlternative(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx) {
        if (ctx.identifier() != null) {
            if (active) {
                labeledAlternativeCount++;
                return visitParserRuleAlternative(ctx.parserRuleAlternative());
            }
        }
        return super.visitParserRuleLabeledAlternative(ctx);
    }

    @Override
    public CharSequence visitEbnfSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        return extractText(ctx);
    }

    private <T> T enterAltList(Supplier<T> s) {
        boolean oldInAltList = inAltList;
        inAltList = true;
        int oldAlts = alternativesInCurrentBlock;
        alternativesInCurrentBlock = 0;
        try {
            return s.get();
        } finally {
            inAltList = oldInAltList;
            alternativesInCurrentBlock = oldAlts;
        }
    }

    @Override
    public CharSequence visitLexerRuleBlock(ANTLRv4Parser.LexerRuleBlockContext ctx) {
        return enterAltList(() -> {
            return super.visitLexerRuleBlock(ctx);
        });
    }

    @Override
    public CharSequence visitLexerRuleElementBlock(ANTLRv4Parser.LexerRuleElementBlockContext ctx) {
        return enterBlock(() -> {
            return super.visitLexerRuleElementBlock(ctx);
        });
    }

    @Override
    public CharSequence visitLexerRuleAlt(ANTLRv4Parser.LexerRuleAltContext ctx) {
        return enterAlternative(() -> {
            return super.visitLexerRuleAlt(ctx);
        });
    }

    @Override
    public CharSequence visitAltList(ANTLRv4Parser.AltListContext ctx) {
        return enterAltList(() -> {
            return super.visitAltList(ctx);
        });
    }

    @Override
    public CharSequence visitParserRuleAlternative(ANTLRv4Parser.ParserRuleAlternativeContext ctx) {
        return enterAlternative(() -> {
            return super.visitParserRuleAlternative(ctx);
        });
    }

    private <T> T enterAlternative(Supplier<T> supp) {
        if (alternativesInCurrentBlock > 0) {
            sb.append(" |");
            lastAppendedTokenType = ANTLRv4Lexer.OR;
        }
        try {
            return supp.get();
        } finally {
            alternativesInCurrentBlock++;
        }
    }

    private <T> T enterBlock(Supplier<T> r) {
        blockCount++;
        try {
            switch (lastAppendedTokenType) {
                case ANTLRv4Lexer.LPAREN:
                case -1:
                    break;
                default:
                    sb.append(' ');
            }
            sb.append('(');
            lastAppendedTokenType = ANTLRv4Lexer.LPAREN;
            return r.get();
        } finally {
            sb.append(')');
            lastAppendedTokenType = ANTLRv4Lexer.RPAREN;
        }

    }

    @Override
    public CharSequence visitBlock(ANTLRv4Parser.BlockContext ctx) {
        return enterBlock(() -> {
            return super.visitBlock(ctx);
        });
    }

    private StringBuilder extractText(ParserRuleContext ctx) {
        if (ctx == null) {
            return sb;
        }
        Interval ival = ctx.getSourceInterval();
        for (int i = ival.a; i <= ival.b; i++) {
            Token tok = parser.getTokenStream().get(i);
            appendText(tok);
        }
        return sb;
    }

    @Override
    public CharSequence visitTerminal(ANTLRv4Parser.TerminalContext ctx) {
        if (!active) {
            return sb;
        }
        return sb;
    }

    @Override
    public CharSequence visitActionBlock(ANTLRv4Parser.ActionBlockContext ctx) {
        if (active) {
            actionBlockCount++;
            return sb;
        }
        return super.visitActionBlock(ctx);
    }

    private int lastAppendedTokenType = -1;

    private StringBuilder appendText(Token tok) {
        String text = tok.getText();
        if (Strings.isBlank(text)) {
            return sb;
        }
        boolean prependSpace = true;
        switch (tok.getType()) {
            case ANTLRv4Lexer.QUESTION:
            case ANTLRv4Lexer.STAR:
            case ANTLRv4Lexer.PLUS:
            case ANTLRv4Lexer.RANGE:
                prependSpace = false;
                break;
            case ANTLRv4Lexer.LEXER_CHAR_SET:
            case ANTLRv4Lexer.FRAGDEC_LBRACE:
                switch (lastAppendedTokenType) {
                    case ANTLRv4Lexer.RPAREN:
                    case ANTLRv4Lexer.NOT:
                        prependSpace = false;
                        break;
                }
                break;
            case ANTLRv4Lexer.RPAREN:
                switch (lastAppendedTokenType) {
                    case ANTLRv4Lexer.QUESTION:
                    case ANTLRv4Lexer.STAR:
                    case ANTLRv4Lexer.PLUS:
                    case ANTLRv4Lexer.RPAREN:
                        prependSpace = false;
                }
                break;
            default:
                switch (lastAppendedTokenType) {
                    case ANTLRv4Lexer.RANGE:
                        prependSpace = false;
                        break;
                }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '(') {
            prependSpace = false;
        }
        if (prependSpace && sb.length() > 0 && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.append(' ');
        }
        lastAppendedTokenType = tok.getType();
        return sb.append(text.trim());
    }

    public static final class RuleBodyExtractionResult {

        public final int atomCount;
        public final CharSequence text;
        public final Problem problem;
        public final String fullRuleText;
        public final int whitespaceCharsUpToAndIncludingPrecedingNewline;
        public final int whitespaceCharsUpToAndIncludingSubsequentNewline;

        public RuleBodyExtractionResult(int atomCount, CharSequence text, Problem problem, String fullRuleText,
                int whitespaceCharsUpToAndIncludingPrecedingNewline,
                int whitespaceCharsUpToAndIncludingSubsequentNewline) {
            this.atomCount = atomCount;
            this.text = text;
            this.problem = problem;
            this.fullRuleText = fullRuleText;
            this.whitespaceCharsUpToAndIncludingPrecedingNewline
                    = whitespaceCharsUpToAndIncludingPrecedingNewline;
            this.whitespaceCharsUpToAndIncludingSubsequentNewline
                    = whitespaceCharsUpToAndIncludingSubsequentNewline;
        }

        @Override
        public String toString() {
            return "RuleBodyExtractionResult{" + "atomCount=" + atomCount + ", problem=" + problem
                    + ", whitespaceCharsUpToAndIncludingPrecedingNewline="
                    + whitespaceCharsUpToAndIncludingPrecedingNewline
                    + ", whitespaceCharsUpToAndIncludingSubsequentNewline="
                    + whitespaceCharsUpToAndIncludingSubsequentNewline
                    + ", text='" + Escaper.CONTROL_CHARACTERS.escape(text)
                    + "', fullRuleText='" + Escaper.CONTROL_CHARACTERS.escape(fullRuleText)
                    + "'}";
        }
    }

    @Messages({
        "# {0} - ruleName",
        "ruleNotFound=No rule found named {0}",
        "parseErrorsEncountered=Parse errors encountered",
        "syntaxErrorsEncountered=Syntax errors encountered",
        "labeledAtomsEncountered=Labeled parser or lexer atoms are assigned labels (e.g. someName=someRule). The "
        + "labels will not be present in the inlined rule body.",
        "actionsEncountered=Programmatic actions associated with this rule; they will not be "
        + "present in the inlined rule.",
        "labeledAlternativesEncountered=Labeled alternatives (e.g. #Foo) present in the original parser rule "
        + "will be omitted from the result.",
        "bodyIsEmpty=Extracted rule body is empty"
    })
    public static final RuleBodyExtractionResult extractRuleBody(String ruleName, Document doc) throws IOException {
        ErrorChecker checker = new ErrorChecker();
        Segment seg = new Segment();
        doc.render(() -> {
            try {
                doc.getText(0, doc.getLength(), seg);
            } catch (BadLocationException ex) {
                com.mastfrog.util.preconditions.Exceptions.chuck(ex);
            }
        });
        GrammarSource<?> gs = GrammarSource.find(doc, AntlrConstants.ANTLR_MIME_TYPE);
        CharStream charStream = gs.stream();
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(checker);
        ANTLRv4Parser parser = new ANTLRv4Parser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(checker);
        RuleBodyExtractor ext = new RuleBodyExtractor(ruleName, parser);
        CharSequence result = parser.grammarFile().accept(ext);
        Problem problem = null;
        if (!ext.wasRuleFound()) {
            problem = createProblem(true, Bundle.ruleNotFound(ruleName));
        }
        if (Strings.isBlank(result.toString())) {
            problem = chainProblems(problem, warningProblem(Bundle.bodyIsEmpty()));
        }
        if (checker.errorEncountered) {
            problem = chainProblems(problem, warningProblem(Bundle.parseErrorsEncountered()));
        }
        if (ext.labeledAlternativeCount() > 0) {
            problem = chainProblems(problem, warningProblem(Bundle.labeledAlternativesEncountered()));
        }
        if (ext.labeledAtomCount() > 0) {
            problem = chainProblems(problem, warningProblem(Bundle.labeledAtomsEncountered()));
        }
        if (ext.actionBlockCount() > 0) {
            problem = chainProblems(problem, warningProblem(Bundle.actionsEncountered()));
        }
        if (ext.atomCount() > 1) {
            ext.sb.insert(0, '(');
            ext.sb.append(')');
        }
        RuleBodyExtractionResult bres = new RuleBodyExtractionResult(ext.atomCount(), result,
                problem, ext.fullRuleText,
                ext.surroundingWhitespaceCharsUpToAndIncludingPrecedingNewline(),
                ext.surroundingWhitespaceCharsUpToAndIncludingSubsequentNewline());
        return bres;
    }

    private static Problem chainProblems(Problem a, Problem b) {
        if (a == null && b == null) {
            return null;
        } else if (a != null && b == null) {
            return a;
        } else if (b != null && a == null) {
            return b;
        } else {
            if (b.isFatal() && !a.isFatal()) {
                return attachTo(b, a);
            } else {
                return attachTo(a, b);
            }
        }
    }

    private static Problem attachTo(Problem a, Problem b) {
        while (a.getNext() != null) {
            a = a.getNext();
        }
        a.setNext(b);
        return a;
    }

}

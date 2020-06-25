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

import com.mastfrog.range.IntRange;
import java.io.IOException;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.refactoring.AbstractRefactoringContext;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.warningProblem;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.modules.refactoring.api.Problem;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * In the case that we could generate invalid or incorrect Antlr code - by
 * generating, say, <code>foo=(This? That)</code>, this class runs a parse and
 * detects whether the target for replacement is labeled and if so expands the
 * bounds of the passed range to include the label, so that it will be included
 * in the replacement process and not be present in the result.
 *
 * @author Tim Boudreau
 */
final class ReplacementBoundsFinder extends ANTLRv4BaseVisitor<IntRange<? extends IntRange<?>>> {

    private IntRange<? extends IntRange<?>> range;
    private final IntRange<? extends IntRange<?>> targetRange;
    private boolean found;
    private boolean rangeExpanded;
    private String oldLabel;
    private String ruleContainingUsage;
    private String grammarName;

    public ReplacementBoundsFinder(IntRange<? extends IntRange<?>> targetRange) {
        this.targetRange = targetRange;
        this.range = targetRange;
    }

    String containingRuleName() {
        return ruleContainingUsage;
    }

    boolean rangeWasExpanded() {
        return rangeExpanded;
    }

    String label() {
        return oldLabel;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitChildren(RuleNode node) {
        if (found) {
            // Shortcut visiting anything else
            return range;
        }
        return super.visitChildren(node);
    }

    private boolean isTarget(ParserRuleContext ctx) {
        Token start = ctx.start;
        Token stop = ctx.stop;
        int startPosition = start.getStartIndex();
        int stopPosition = stop.getStopIndex();
        boolean result = startPosition == targetRange.start() && stopPosition == targetRange.stop();
        if (result) {
            found = true;
        }
        return result;
    }

    @Override
    protected IntRange<? extends IntRange<?>> defaultResult() {
        return range;
    }

    String grammarName() {
        return grammarName;
    }

    private <T> T findAncestor(Class<T> type, ParserRuleContext ctx) {
        if (ctx instanceof ANTLRv4Parser.GrammarFileContext) {
            return null;
        }
        while (ctx != null && !(ctx instanceof GrammarFileContext) && !type.isInstance(ctx)) {
            ctx = ctx.getParent();
        }
        if (type.isInstance(ctx)) {
            return type.cast(ctx);
        }
        return null;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitGrammarFile(GrammarFileContext ctx) {
        grammarName = ctx.grammarSpec().identifier().getText();
        return super.visitGrammarFile(ctx);
    }

    @Override
    public IntRange<? extends IntRange<?>> visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
        if (isTarget(ctx)) {
            ANTLRv4Parser.LabeledParserRuleElementContext labeled = findAncestor(ANTLRv4Parser.LabeledParserRuleElementContext.class, ctx);
            if (labeled != null && labeled.identifier() != null) {
                int newStart = labeled.identifier().start.getStartIndex();
                range = targetRange.withStart(newStart);
                rangeExpanded = true;
                oldLabel = labeled.identifier().getText();
            }
            ANTLRv4Parser.ParserRuleSpecContext spec = findAncestor(ANTLRv4Parser.ParserRuleSpecContext.class, ctx);
            if (spec != null) {
                ruleContainingUsage = spec.parserRuleDeclaration().parserRuleIdentifier().getText();
            }
        }
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitTokenRuleIdentifier(ANTLRv4Parser.TokenRuleIdentifierContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitFragmentRuleIdentifier(ANTLRv4Parser.FragmentRuleIdentifierContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitIdentifier(ANTLRv4Parser.IdentifierContext ctx) {
        return range;
    }

    // Override stuff that's not useful to scan through for performance
    @Override
    public IntRange<? extends IntRange<?>> visitLanguageSpec(ANTLRv4Parser.LanguageSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitAnalyzerDirectiveSpec(ANTLRv4Parser.AnalyzerDirectiveSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitActionBlock(ANTLRv4Parser.ActionBlockContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitChannelsSpec(ANTLRv4Parser.ChannelsSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitHeaderAction(ANTLRv4Parser.HeaderActionContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitTokenVocabSpec(ANTLRv4Parser.TokenVocabSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitTokensSpec(ANTLRv4Parser.TokensSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitThrowsSpec(ANTLRv4Parser.ThrowsSpecContext ctx) {
        return range;
    }

    @Override
    public IntRange<? extends IntRange<?>> visitSingleTypeImportDeclaration(ANTLRv4Parser.SingleTypeImportDeclarationContext ctx) {
        return range;
    }

    static class ReplacementBoundsResult {

        public final IntRange<? extends IntRange<?>> range;
        public final boolean found;
        public final Problem problem;

        public ReplacementBoundsResult(IntRange<? extends IntRange<?>> range, boolean found, Problem problem) {
            this.range = range;
            this.found = found;
            this.problem = problem;
        }
    }

    public static ReplacementBoundsResult adjustReplacementBounds(FileObject file, IntRange<? extends IntRange<?>> reference) throws IOException {
        // Need to unsure we're working with the live, possibly edited document, not the
        // possibly stale file on disk
        DataObject dob = DataObject.find(file);
        EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
        return adjustReplacementBounds(ck.getDocument(), reference);
    }

    @Messages({
        "# {0} - start",
        "# {1} - end",
        "# {2} - fileName",
        "# {3} - ruleContainingTheUsage",
        "# {4} - label",
        "elideLabelWithRule=Usage at {0}:{1} in rule {2} of grammar {3} has a label '{4}' which will be removed"
    })
    public static ReplacementBoundsResult adjustReplacementBounds(Document doc, IntRange<? extends IntRange<?>> reference) throws IOException {
        ErrorChecker check = new ErrorChecker();
        GrammarSource<Document> gs = GrammarSource.find(doc, ANTLR_MIME_TYPE);
        CharStream stream = gs.stream();
        ANTLRv4Lexer lex = new ANTLRv4Lexer(stream);
        lex.removeErrorListeners();
        lex.addErrorListener(check);
        CommonTokenStream cts = new CommonTokenStream(lex);
        ANTLRv4Parser parser = new ANTLRv4Parser(cts);
        parser.removeErrorListeners();
        parser.addErrorListener(check);
        ReplacementBoundsFinder finder = new ReplacementBoundsFinder(reference);
        IntRange<? extends IntRange<?>> result = parser.grammarFile().accept(finder);
        if (result == reference) {
            return new ReplacementBoundsResult(result, true, null);
        } else {
            Problem p = null;
            if (finder.rangeWasExpanded()) {
                String lbl = finder.label();
                String containingRule = finder.containingRuleName();
                String gn = finder.grammarName;
                p = AbstractRefactoringContext.warningProblem(Bundle.elideLabelWithRule(
                        result.start(), result.end(), containingRule, gn, lbl));
            }
            if (check.errorEncountered) {
                p = AbstractRefactoringContext.chainProblems(p, warningProblem(Bundle.syntaxErrorsEncountered()));
            }
            return new ReplacementBoundsResult(result, finder.found, p);
        }
    }
}

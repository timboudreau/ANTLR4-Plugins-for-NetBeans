package org.nemesis.antlrformatting.spi;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingStateBuilder;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.modules.editor.indent.spi.Context;

/**
 * AntlrFormatterProvider implementation which is used by the generated
 * code from the annotation processor.
 *
 * @author Tim Boudreau
 */
final class StubFormatter<T extends Enum<T>, L extends Lexer> extends AntlrFormatterProvider<Preferences, T> {

    private final Vocabulary vocabulary;
    private final String[] modeNames;
    private final AntlrFormatterStub<T, L> stub;
    private final Function<CharStream, L> lexerFactory;
    private final String mimeType;
    private final int[] whitespaceTokens;
    private final Predicate<Token> debugTokens;
    private final String[] parserRuleNames;
    private final Function<Lexer,RuleNode> rootRuleFinder;

    StubFormatter(String mimeType, AntlrFormatterStub<T, L> stub, Class<T> enumType, Vocabulary vocab, String[] modeNames,
            Function<CharStream, L> lexerFactory, int[] whitespaceTokens, Predicate<Token> debugTokens,
            String[] parserRuleNames, Function<Lexer,RuleNode> rootRuleFinder) {
        super(enumType);
        this.vocabulary = vocab;
        this.modeNames = modeNames;
        this.stub = stub;
        this.lexerFactory = lexerFactory;
        this.mimeType = mimeType;
        this.whitespaceTokens = whitespaceTokens;
        this.debugTokens = debugTokens;
        this.parserRuleNames = parserRuleNames;
        this.rootRuleFinder = rootRuleFinder;
    }

    @Override
    protected String[] parserRuleNames() {
        return parserRuleNames != null && parserRuleNames.length == 0 ? null
                : parserRuleNames;
    }

    @Override
    protected RuleNode parseAndExtractRootRuleNode(Lexer lexer) {
        return rootRuleFinder.apply(lexer);
    }

    @Override
    protected Predicate<Token> debugLogPredicate() {
        return debugTokens;
    }

    @Override
    protected Preferences configuration(Context ctx) {
        return CodeStylePreferences.get(ctx.document(), mimeType).getPreferences();
    }

    @Override
    protected Criterion whitespace() {
        if (whitespaceTokens.length == 0) {
            return super.whitespace();
        }
        return Criterion.anyOf(vocabulary, whitespaceTokens);
    }

    @Override
    protected int indentSize(Preferences config) {
        if (config == null) {
            return super.indentSize(config);
        }
        return config.getInt(AntlrFormatterStub.PREFS_KEY_INDENT_BY, super.indentSize(config));
    }

    @Override
    protected L createLexer(CharStream stream) {
        return lexerFactory.apply(stream);
    }

    @Override
    protected Vocabulary vocabulary() {
        return vocabulary;
    }

    @Override
    protected String[] modeNames() {
        return modeNames;
    }

    @Override
    protected void configure(LexingStateBuilder<T, ?> stateBuilder, FormattingRules rules, Preferences config) {
        stub.configure(stateBuilder, rules, config);
    }
}

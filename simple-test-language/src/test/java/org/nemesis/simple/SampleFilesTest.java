package org.nemesis.simple;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.nemesis.simple.language.SimpleLanguageParser;

/**
 *
 * @author Tim Boudreau
 */
public class SampleFilesTest {

    @Test
    public void testSamplesLexAndParseWithoutErrors() throws IOException {
        for (SampleFiles f : SampleFiles.values()) {
            testOneFile(f);
        }
    }

    private void testOneFile(SampleFiles f) throws IOException {
        System.out.println("MIN " + f.text());
        ErrL errs = new ErrL(f);
        SimpleLanguageLexer lexer = f.lexer(errs);
        Token t;
        do {
            t = lexer.nextToken();
            if (log(f)) {
                if (t.getType() != SimpleLanguageLexer.S_WHITESPACE) {
                    String name = SimpleLanguageLexer.VOCABULARY.getSymbolicName(t.getType());
                    if (name == null) {
                        name = SimpleLanguageLexer.VOCABULARY.getDisplayName(t.getType());
                    }
                    System.out.println(f + "-" + t.getTokenIndex() + ": " + name + ": '" + t.getText().replace("\n", "\\n") + "'");
                }
            }
        } while (t.getType() != SimpleLanguageLexer.EOF);

        SimpleLanguageParser parser = f.parser();
        parser.addErrorListener(errs);
        PTV ptv = new PTV(f);
        parser.compilation_unit().accept(ptv);
    }

    static boolean log(SampleFiles f) {
        return false;
    }

    private static final class PTV extends AbstractParseTreeVisitor<Void> {

        int depth;
        private final SampleFiles file;

        PTV(SampleFiles f) {
            this.file = f;
        }

        @Override
        public Void visitChildren(RuleNode node) {
            depth++;
            try {
                char[] spaces = new char[depth * 2];
                Arrays.fill(spaces, ' ');
                String sp = new String(spaces);
                int ct = node.getChildCount();
                boolean hasRuleChildren = false;
                for (int i = 0; i < ct; i++) {
                    if (node.getChild(i) instanceof ParserRuleContext) {
                        hasRuleChildren = true;
                        break;
                    }
                }
                if (log(file)) {
                    if (hasRuleChildren) {
                        System.out.println(file + ": " + sp + node.getClass().getSimpleName());
                    } else {
                        System.out.println(file + ":" + sp + node.getClass().getSimpleName()
                                + ": " + node.getText().replace("\n", "\\n"));
                    }
                }
                super.visitChildren(node);
            } finally {
                depth--;
            }
            return null;
        }

        @Override
        public Void visitErrorNode(ErrorNode node) {
            throw new AssertionError(file + ": "
                    + "Error node encountered at "
                    + depth + ": " + node + " - '" + node.getText() + "' "
                    + node.toStringTree());
        }
    }

    private static final class ErrL implements ANTLRErrorListener {

        private final SampleFiles file;

        ErrL(SampleFiles f) {
            this.file = f;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> rcgnzr, Object o, int i, int i1, String string, RecognitionException re) {
            fail(file + ": Syntax error " + o + " @ " + i + ":" + i1 + " " + string + " " + re);
        }

        @Override
        public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs) {
            System.out.println(file + ": ambiguity @" + i + ":" + i1);
        }

        @Override
        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
            System.out.println(file + ": fullContext @" + i + ":" + i1);
        }

        @Override
        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
            System.out.println(file + ": contextSensitivity @" + i + ":" + i1);
        }

    }

}

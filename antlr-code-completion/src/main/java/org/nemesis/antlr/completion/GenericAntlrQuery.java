package org.nemesis.antlr.completion;

import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.nemesis.misc.utils.function.IOFunction;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;

/**
 *
 * @author Tim Boudreau
 */
public class GenericAntlrQuery extends AsyncCompletionQuery {

    private final IOFunction<Document, Parser> parserForDoc;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private static final Logger LOG = Logger.getLogger(GenericAntlrQuery.class.getName());
    private final Font font;

    GenericAntlrQuery(IOFunction<Document, Parser> parserForDoc, IntPredicate preferredRules, IntPredicate ignoredRules, Font font) {
        this.parserForDoc = parserForDoc;
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
        this.font = font;
    }

    @Override
    protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
        if (caretOffset < 0) {
            resultSet.finish();
            return;
        }
        try {
            doQuery(resultSet, doc, caretOffset);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception computing completion query", ex);
        }
    }

    private void doQuery(CompletionResultSet resultSet, Document doc, int caret) throws IOException {
        try {
            Parser p = parserForDoc.apply(doc);
            Lexer lexer = (Lexer) p.getInputStream().getTokenSource();
            lexer.reset();
            List<Token> stream = new ArrayList<>();
            // Prioritize token suggestions based on their frequency in the
            // document (the items will then de-prioritize those that are
            // punctuation by multiplying this number)
            int[] frequencies = new int[p.getVocabulary().getMaxTokenType()+1];
            int ix = 0;
            for (Token t = lexer.nextToken(); t.getType() != -1; t = lexer.nextToken()) {
                CommonToken ct = new CommonToken(t);
                ct.setTokenIndex(ix++);
                stream.add(ct);
                frequencies[t.getType()]++;
            }

            System.out.println("STREAM SIZE " + stream.size());

            int caretToken = findCaretToken(caret, stream, 0, stream.size() - 1);
            if (caretToken == -1) {
                System.out.println("DID NOT FIND TOKEN");
                return;
            }

//            CodeCompletionCore core = new CodeCompletionCore(parserForDoc.apply(doc),
//                    preferredRules, ignoredRules);

            CodeCompletionCore core = new CodeCompletionCore(parserForDoc.apply(doc), null, null);

            CodeCompletionCore.CandidatesCollection result = core.collectCandidates(caretToken, null);

            System.out.println("GOT RESULT " + result);

            Token caretTok = stream.get(caretToken);

            System.out.println("TOKENS: " + result.tokens);
            for (Map.Entry<Integer, List<Integer>> e : result.tokens.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                String symName = p.getVocabulary().getLiteralName(e.getKey());
                if (symName != null) {
                    resultSet.addItem(new AntlrCompletionItem(strip(symName), caretTok, frequencies[e.getKey()], font));
                }

                for (Integer i : e.getValue()) {
                    Token tok = stream.get(i);
                    resultSet.addItem(new AntlrCompletionItem(tok, caretTok, frequencies[e.getKey()], font));
                }
            }
        } finally {
            resultSet.finish();
        }
    }

    private static String strip(String s) {
        if (s.length() > 1 && s.charAt(0) == '\'' && s.charAt(s.length()-1) == '\'') {
            s = s.substring(1, s.length()-1);
        }
        return s;
    }

    private boolean contains(Token tok, int pos) {
        boolean result = pos >= tok.getStartIndex() && pos <= tok.getStopIndex();
        return result;
    }

    private int findCaretToken(int caret, List<Token> stream, int start, int stop) {
        System.out.println("Find caret " + caret + " from tokens " + start + " to " + stop);
        if (true) {
            for (int i = start; i <= stop; i++) {
                Token t = stream.get(i);
                if (contains(t, caret)) {
                    return t.getTokenIndex();
                }
            }
            return -1;
        }

        // binary search
        Token first = stream.get(start);
        if (contains(first, caret)) {
            System.out.println("matched first " + start);
            return first.getTokenIndex();
        }
        if (start >= stop) {
            System.out.println("bail 1");
            return -1;
        }
        Token last = stream.get(stop);
        if (contains(last, caret)) {
            System.out.println("matched last " + stop);
            return last.getTokenIndex();
        }
        int middle = start + ((stop - start) / 2);
        Token mid = stream.get(middle);
        if (contains(mid, caret)) {
            System.out.println("matched first " + middle);
            return mid.getTokenIndex();
        }
        start++;
        stop--;
        if (stop < start || start < 0) {
            System.out.println("bail 2");
            return -1;
        }
        if (caret < mid.getStartIndex()) {
            middle--;
            if (middle < 0) {
                System.out.println("bail 3");
                return -1;
            }
            return findCaretToken(caret, stream, start, middle);
        } else {
            middle++;
            if (middle > stream.size()) {
                System.out.println("bail 4");
                return -1;
            }
            return findCaretToken(caret, stream, middle, stop);
        }
    }

    @Override
    protected void filter(CompletionResultSet resultSet) {
        super.filter(resultSet); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected boolean canFilter(JTextComponent component) {
        return super.canFilter(component); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void preQueryUpdate(JTextComponent component) {
        super.preQueryUpdate(component); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void prepareQuery(JTextComponent component) {
        super.prepareQuery(component); //To change body of generated methods, choose Tools | Templates.
    }

}

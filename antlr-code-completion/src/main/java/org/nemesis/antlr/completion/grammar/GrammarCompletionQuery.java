package org.nemesis.antlr.completion.grammar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import static org.nemesis.antlr.completion.TokenUtils.findCaretToken;
import org.nemesis.misc.utils.IntMap;
import org.nemesis.misc.utils.function.IOFunction;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarCompletionQuery extends AsyncCompletionQuery {

    private static final Logger LOG = Logger.getLogger(GrammarCompletionQuery.class.getName());
    private final IOFunction<Document, Parser> parserForDoc;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private final Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache;

    GrammarCompletionQuery(IOFunction<Document, Parser> parserForDoc, IntPredicate preferredRules, IntPredicate ignoredRules, Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache) {
        this.parserForDoc = parserForDoc;
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
        this.cache = cache;
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
        } finally {
            resultSet.finish();
        }
    }

    private void doQuery(CompletionResultSet resultSet, Document doc, int caret) throws IOException {
        Parser p = parserForDoc.apply(doc);
        Lexer lexer = (Lexer) p.getInputStream().getTokenSource();
        lexer.reset();
        List<Token> stream = new ArrayList<>();
        // Prioritize token suggestions based on their frequency in the
        // document (the items will then de-prioritize those that are
        // punctuation by multiplying this number)
        int[] frequencies = new int[p.getVocabulary().getMaxTokenType() + 1];
        int ix = 0;
        for (Token t = lexer.nextToken(); t.getType() != -1; t = lexer.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            stream.add(ct);
            frequencies[t.getType()]++;
        }

        int caretToken = findCaretToken(caret, stream, 0, stream.size() - 1);
        if (caretToken == -1) {
            return;
        }

//            CodeCompletionCore core = new CodeCompletionCore(parserForDoc.apply(doc), null, null, cache);
        CodeCompletionCore core = new CodeCompletionCore(parserForDoc.apply(doc), preferredRules, ignoredRules, cache);

        CodeCompletionCore.CandidatesCollection result = core.collectCandidates(caretToken, null);

        System.out.println("\n\nCOMPLETION RESULT: " + result + "\n");

        Token caretTok = stream.get(caretToken);

        result.tokens.forEach((tokenId, list) -> {
            if (list != null) {
                String symName = p.getVocabulary().getLiteralName(tokenId);
                if (symName != null) {
                    resultSet.addItem(new GrammarCompletionItem(
                            strip(symName), caretTok, frequencies[tokenId]));
                }

                list.forEach((IntConsumer) i -> {
                    Token tok = stream.get(i);
                    resultSet.addItem(new GrammarCompletionItem(
                            tok, caretTok, frequencies[tokenId]));
                });
            }
        });
    }

    private static String strip(String s) {
        if (s.length() > 1 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            s = s.substring(1, s.length() - 1);
        }
        return s;
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

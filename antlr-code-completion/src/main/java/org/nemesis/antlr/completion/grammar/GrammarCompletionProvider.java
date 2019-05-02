package org.nemesis.antlr.completion.grammar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Parser;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.function.throwing.io.IOFunction;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 *
 * @author Tim Boudreau
 */
public class GrammarCompletionProvider implements CompletionProvider {

    private final IOFunction<Document, Parser> parserForDoc;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private final Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache = new HashMap<>(3);

    protected GrammarCompletionProvider(IOFunction<Document,Parser> parserForDoc, IntPredicate preferredRules, IntPredicate ignoredRules) {
        this.parserForDoc = parserForDoc;
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new GrammarCompletionQuery(parserForDoc, preferredRules,
                ignoredRules, cache), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0;
    }

}

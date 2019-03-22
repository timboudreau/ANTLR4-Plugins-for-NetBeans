package org.nemesis.antlr.completion;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Parser;
import org.nemesis.misc.utils.IntMap;
import org.nemesis.misc.utils.function.IOFunction;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 *
 * @author Tim Boudreau
 */
public class GenericAntlrCompletionProvider implements CompletionProvider {

    private final IOFunction<Document, Parser> parserForDoc;
    private final IntPredicate preferredRules;
    private final IntPredicate ignoredRules;
    private final Map<String, IntMap<CodeCompletionCore.FollowSetsHolder>> cache = new HashMap<>(3);

    protected GenericAntlrCompletionProvider(IOFunction<Document,Parser> parserForDoc, IntPredicate preferredRules, IntPredicate ignoredRules) {
        this.parserForDoc = parserForDoc;
        this.preferredRules = preferredRules;
        this.ignoredRules = ignoredRules;
        new Exception("Create a new completion provider").printStackTrace(System.out);
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new GenericAntlrQuery(parserForDoc, preferredRules,
                ignoredRules, cache), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0;
    }

}

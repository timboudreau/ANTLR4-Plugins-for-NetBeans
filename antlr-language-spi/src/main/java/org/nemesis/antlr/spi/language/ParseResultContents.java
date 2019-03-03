package org.nemesis.antlr.spi.language;

import java.util.List;
import java.util.Optional;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 * Object which can be populated to provide additional data into a parser
 * result.
 *
 * @see ParseResultHook
 * @author Tim Boudreau
 */
public abstract class ParseResultContents {

    ParseResultContents() {
    }

    /**
     * Get a value previously put into this parser result.
     *
     * @param <T> The value type
     * @param key The key
     * @return An optional containing the data if present
     */
    public final <T> Optional<T> get(AntlrParseResult.Key<T> key) {
        return _get(key);
    }

    /**
     * Add an error.
     *
     * @param err The error
     * @return this
     */
    public abstract ParseResultContents addErrorDescription(ErrorDescription err);

    abstract void setSyntaxErrors(List<? extends SyntaxError> errors, NbParserHelper helper);

    abstract void addSyntaxError(SyntaxError err);

    abstract <T> Optional<T> _get(AntlrParseResult.Key<T> key);

    /**
     * Put a value, using a key obtained from AntlrParseResult.key() which
     * should be available to other things which know about that key and may
     * want to use the data you are deriving from the parse.
     *
     * @param <T> The key's value type
     * @param key The key itself
     * @param obj An object to put
     * @return this
     */
    public final <T> ParseResultContents put(AntlrParseResult.Key<T> key, T obj) {
        if (obj != null) {
            if (!key.type().isInstance(obj)) {
                throw new ClassCastException("Value is not an instance of " + "key's type: " + key.type().getName() + " vs " + obj.getClass().getName() + " (" + obj + ")");
            }
            _put(key, obj);
        }
        return this;
    }

    abstract <T> void _put(AntlrParseResult.Key<T> key, T obj);

}

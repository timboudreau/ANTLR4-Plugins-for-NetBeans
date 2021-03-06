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
package org.nemesis.antlr.spi.language;

import java.io.IOException;
import java.util.Collection;
import org.nemesis.antlr.spi.language.fix.Fixes;
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

    abstract void addSyntaxErrors(Collection<? extends SyntaxError> errors, NbParserHelper helper);

    abstract void addSyntaxError(SyntaxError err);

    abstract <T> Optional<T> _get(AntlrParseResult.Key<T> key);

    public abstract void replaceErrors(ParseResultContents replacements);

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

    abstract Fixes fixes() throws IOException;

    /**
     * For use with ParserResultHooks that are run against an already
     * used AntlrParseResult, to provide a way for the hook to be
     * fooled into thinking it's running against a fresh parse.
     *
     * @return A ParseResultContents that throws away anything added to it
     */
    public static ParseResultContents empty() {
        return Empty.INSTANCE;
    }

    static class Empty extends ParseResultContents {

        static Empty INSTANCE = new Empty();

        @Override
        public void replaceErrors(
                ParseResultContents other ) {
        }

        @Override
        public ParseResultContents addErrorDescription(ErrorDescription err) {
            return this;
        }

        @Override
        void addSyntaxErrors(
                Collection<? extends SyntaxError> errors, NbParserHelper helper ) {
            // do nothing
        }

        @Override
        void setSyntaxErrors(List<? extends SyntaxError> errors, NbParserHelper helper) {
            // do nothing
        }

        @Override
        void addSyntaxError(SyntaxError err) {
            // do nothing
        }

        @Override
        <T> Optional<T> _get(AntlrParseResult.Key<T> key) {
            return Optional.empty();
        }

        @Override
        <T> void _put(AntlrParseResult.Key<T> key, T obj) {
            // do nothing
        }

        @Override
        Fixes fixes() {
            return Fixes.empty();
        }
    }
}

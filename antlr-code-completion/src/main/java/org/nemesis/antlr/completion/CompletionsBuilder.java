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
package org.nemesis.antlr.completion;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Document;
import org.antlr.v4.runtime.Lexer;
import com.mastfrog.function.throwing.io.IOFunction;
import org.netbeans.spi.editor.completion.CompletionProvider;

/**
 * Builder for code completion, which can handle setting up multiple different
 * kinds of completion for one mime type.
 *
 * @author Tim Boudreau
 */
public class CompletionsBuilder {

    final List<CompletionStub<?>> stubs = new ArrayList<>(5);
    final IOFunction<? super Document, ? extends Lexer> lexerFactory;

    CompletionsBuilder(IOFunction<? super Document, ? extends Lexer> lexerFactory) {
        this.lexerFactory = lexerFactory;
    }

    CompletionsBuilder(CompletionsBuilder orig) {
        this.stubs.addAll(orig.stubs);
        this.lexerFactory = orig.lexerFactory;
    }

    public <I> CompletionBuilder<I> add() {
        return new CompletionBuilder<>(this);
    }

    FinishableCompletionsBuilder add(CompletionStub<?> stub) {
        stubs.add(stub);
        return new FinishableCompletionsBuilder(this);
    }

    /**
     * A CompletionsBuilder which defines at least one completion and can be
     * built into a CompletionProvider.
     */
    public static final class FinishableCompletionsBuilder extends CompletionsBuilder {

        FinishableCompletionsBuilder(CompletionsBuilder orig) {
            super(orig);
        }

        /**
         * Instantiate a CompletionProvider based on the CompletionBuilders you
         * have run.  Typically you return this from a static method, and annotate
         * it with
         * <code>&#064;MimeRegistration(mimeType="YOUR_MIME_TYPE", service=CompletionBuilder.class).</code>
         *
         * @return A usable CompletionProvider
         */
        public CompletionProvider build() {
            return new AntlrCompletionProvider(lexerFactory, stubs);
        }

    }
}

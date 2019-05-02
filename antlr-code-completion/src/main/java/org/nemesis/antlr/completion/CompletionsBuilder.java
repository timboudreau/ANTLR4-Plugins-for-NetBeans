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

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

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Token;
import org.nemesis.antlr.completion.CompletionsBuilder.FinishableCompletionsBuilder;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import org.netbeans.spi.editor.completion.CompletionTask;

/**
 * Builder for all aspects of code completion, which allows you to selectively
 * supply or replace various aspects of the visual or behavioral characteristics
 * of completion. Get an instance from a {@link CompletionsBuilder}.
 *
 * @author Tim Boudreau
 */
public final class CompletionBuilder<I> {

    private IntUnaryOperator sortPriority = CompletionPriorities.DEFAULT;
    private ItemRenderer<? super I> renderer = DefaultItemRenderer.INSTANCE;
    private Function<? super I, ? extends CompletionTask> docTaskFactory;
    private Function<? super I, ? extends CompletionTask> tooltipTask;
    private ThrowingTriConsumer<? super I, ? super JTextComponent, ? super TokenMatch> performer = DefaultDocumentUpdater.INSTANCE;
    private BiPredicate<? super I, ? super JTextComponent> instantSubstitution;
    private ThrowingBiConsumer<? super I, ? super KeyEvent> keyEventHandler;
    private ToIntFunction<? super I> sorter;
    private BiFunction<? super List<Token>, ? super Token, ? extends TokenMatch> tokenPatternMatcher;
    private final CompletionsBuilder all;
    private BiFunction<? super StringKind, ? super I, ? extends String> stringifier;

    CompletionBuilder(CompletionsBuilder all) {
        this.all = all;
    }

    public FinishableCompletionsBuilder build(CompletionItemProvider<? extends I> itemsProvider) {
        CompletionStub<I> stub = new CompletionStub<>(
                sortPriority,
                renderer,
                docTaskFactory,
                tooltipTask,
                performer,
                instantSubstitution,
                keyEventHandler,
                sorter,
                stringifier,
                tokenPatternMatcher,
                itemsProvider
        );
        return all.add(stub);
    }

    /**
     * Set up one or more <i>token patterns</i> that determine when the
     * resulting {@link CompletionItemProvider} should be used.  A token
     * pattern consists of an optional list of token types that must <i>precede</i> the
     * token the caret is in, an optional list of token types that must
     * <i>follow</i> the caret the token is in, a predicate for what tokens
     * to ignore when computing preceding and following tokens (such as
     * comments or whitespace), and an optional predicate for matching the
     * token the caret is in.  Each pattern is given a <i>name</i> which is
     * passed to the CompletionItemProvider when computing items.
     *
     * @param patternName The name of this pattern
     * @param preceding The token types (static fields on your generated Antlr
     * lexer), if any, which should precede the caret in order for your
     * completion item provider to be invoked - in order from furthest to
     * nearest to the caret.
     *
     * @return A builder that lets you specify other aspects of the pattern,
     * or add additional patterns.
     */
    public TokenTriggersBuilder.TokenTriggerPatternBuilder<I> whenPrecedingTokensMatch(String patternName, int... preceding) {
        return new TokenTriggersBuilder<>(this).whenPrecedingTokensMatch(patternName, preceding);
    }

    /**
     * Set a function which will match token patterns (use {@link TokenTriggersBuilder
     * instead where possible - it is much simpler).
     *
     * @param tokenPatternMatcher A BiFunction which takes a list of all tokens in the
     * document, and the token the caret is in, and returns the name of a matching rule
     * if completion should run, and null if it should not.
     *
     * @return this
     */
    public CompletionBuilder<I> setTokenPatternMatcher(BiFunction<? super List<Token>, ? super Token, ? extends TokenMatch> tokenPatternMatcher) {
        if (this.tokenPatternMatcher != null) {
            BiFunction<? super List<Token>, ? super Token, ? extends TokenMatch> old = this.tokenPatternMatcher;
            this.tokenPatternMatcher = (toks, target) -> {
                TokenMatch result = old.apply(toks, target);
                if (result == null) {
                    result = tokenPatternMatcher.apply(toks, target);
                }
                return result;
            };
        } else {
            this.tokenPatternMatcher = tokenPatternMatcher;
        }
        return this;
    }

    /**
     * Set the overall sort priority for items in this completion,
     * relative to <i>items in other completions you may provide</i>.
     * This determines not the sort order of individual items, but how the
     * sort priority from individual items should be altered to sort items
     * from the {@link CompletionItemProvider} you are building right now,
     * relative to any others that may be present.
     *
     * @see org.nemesis.antlr.completion.CompletionPriorities
     * @param sortPriority A function that modifies sort priority - use
     * {@link CompletionPriorities} for standard ones.
     * @return this
     */
    public CompletionBuilder<I> setSortPriority(IntUnaryOperator sortPriority) {
        this.sortPriority = sortPriority;
        return this;
    }

    /**
     * Set a fixed priority for all items from the completion item
     * provider you are building, causing the sort priority of individual
     * items to be ignored.
     *
     * @param fixedPriority A fixed priority
     * @return this
     */
    public CompletionBuilder<I> setSortPriority(int fixedPriority) {
        this.sortPriority = i -> fixedPriority;
        return this;
    }

    /**
     * Set an object which will render the completion item onscreen. Frequently
     * the default behavior is fine and setStringifier() will allow you to
     * specify a name and (visually deemphasized) description.
     *
     * @param renderer A renderer
     * @return this
     */
    public CompletionBuilder<I> setRenderer(ItemRenderer<? super I> renderer) {
        this.renderer = renderer;
        return this;
    }

    /**
     * Set the stringifier which is used to compute the displayed text,
     * the insert string, any description, etc. See the <code>stringifyWith()</code>
     * method for a simple way to build one using regular expressions.  If none
     * is supplied, <code>toString()</code> on each item is used.
     *
     * @param stringifier A stringifier function
     * @return this
     */
    public CompletionBuilder<I> setStringifier(BiFunction<? super StringKind, ? super I, ? extends String> stringifier) {
        if (this.stringifier != null && stringifier != null) {
            BiFunction<? super StringKind, ? super I, ? extends String> old = this.stringifier;
            this.stringifier = new CombinedStringifier<>(old, stringifier);
        } else {
            this.stringifier = stringifier;
        }

        if (this.renderer == DefaultItemRenderer.INSTANCE) {
            this.renderer = new DefaultItemRenderer<>(stringifier);
        }
        if (this.performer == DefaultDocumentUpdater.INSTANCE) {
            this.performer = new DefaultDocumentUpdater<>(stringifier);
        }
        return this;
    }

    static final class CombinedStringifier<I> implements BiFunction<StringKind, I, String> {

        private final BiFunction<? super StringKind, ? super I, ? extends String> a;
        private final BiFunction<? super StringKind, ? super I, ? extends String> b;

        public CombinedStringifier(BiFunction<? super StringKind, ? super I, ? extends String> a, BiFunction<? super StringKind, ? super I, ? extends String> b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String apply(StringKind t, I u) {
            String result = a.apply(t, u);
            if (result == null) {
                result = b.apply(t, u);
            }
            return result;
        }

        @Override
        public String toString() {
            return "CombinedStringifier{" + a + "," + b + "}";
        }

    }

    /**
     * Create a pattern based stringifier.
     *
     * @param patterns A mapping of string kinds to patterns, which will be used on
     * <code>toString()</code> on each item to determine the text to use.
     *
     * @return this
     */
    public CompletionBuilder<I> setStringifier(EnumMap<StringKind, Pattern> patterns) {
        return setStringifier(new PatternStringifier(patterns));
    }

    /**
     * Create a pattern based stringifier.
     *
     * @param patterns A mapping of string kinds to patterns, which will be used on
     * <code>toString()</code> on each item to determine the text to use.
     * @param messageFormats A mapping of strings usable by
     * <a href="https://docs.oracle.com/javase/9/docs/api/java/text/MessageFormat.html#format-java.lang.String-java.lang.Object...-">
     * <code>MessageFormat.format</code></a> to alter the matched pattern.
     *
     * @return this
     */
    public CompletionBuilder<I> setStringifier(EnumMap<StringKind, Pattern> patterns, EnumMap<StringKind, String> messageFormats) {
        return setStringifier(new PatternStringifier(patterns, messageFormats));
    }

    /**
     * Use a builder to create a pattern based stringifier.
     *
     * @param pat A pattern, which must contain at least one group to be useful
     * @return A builder
     */
    public PatternStringifierBuilder.InterimStringifierBuilder<I> stringifyWith(Pattern pat) {
        return new PatternStringifierBuilder<>(this).withPattern(pat);
    }

    /**
     * Pass in a function which will create a separate task to look up
     * documentation for an item.
     *
     * @param docTaskFactory A function which can create a completion task
     * @return this
     */
    public CompletionBuilder<I> setDocTaskFactory(Function<? super I, ? extends CompletionTask> docTaskFactory) {
        this.docTaskFactory = docTaskFactory;
        return this;
    }

    /**
     * Pass in a function which will create a separate task to look up
     * the tooltip for an item.
     *
     * @param docTaskFactory A function which can create a completion task
     * @return this
     */
    public CompletionBuilder<I> setTooltipTask(Function<? super I, ? extends CompletionTask> tooltipTask) {
        this.tooltipTask = tooltipTask;
        return this;
    }

    /**
     * Set the code which will insert or replace text in the document.  The
     * default behavior is to simply insert or replace the selection with the result of
     * <code>stringifier.apply(TEXT_TO_INSERT, item)</code> or <code>toString()</code>
     * on the item if no stringifier is present, with a few minor caveats:
     * <ul>
     * <li>If a partially typed word matching the suggestion is has been typed, insert only the remainder</li>
     * <li>If the character preceding the caret is a letter or digit and no subsequence has been matched, prepend a space to the insertion text</li>
     * <li>If the character <i>following</i> the caret is a letter or a digit, append a space to the insertion text</li>
     * </ul>
     *
     * @see org.nemesis.antlr.completion.SimpleDocumentUpdater
     * @param performer The code that will update the document
     * @return this
     */
    public CompletionBuilder<I> setInsertAction(ThrowingTriConsumer<? super I, ? super JTextComponent, ? super TokenMatch> performer) {
        this.performer = performer;
        return this;
    }

    /**
     * Overload of <code>setInsertAction(ThrowingBiConsumer&lt;? super I, ? super JTextComponent&gt;) to
     * ease writing subclasses of {@link SimpleDocumentUpdater} as lambdas.
     *
     * @see org.nemesis.antlr.completion.SimpleDocumentUpdater
     * @param performer The code that will update the document
     * @return this
     */
    public CompletionBuilder<I> setInsertAction(SimpleDocumentUpdater<I> performer) {
        this.performer = performer;
        return this;
    }

    /**
     * See the contract of <code>
     * <a href="http://bits.netbeans.org/dev/javadoc/org-netbeans-modules-editor-completion/org/netbeans/spi/editor/completion/CompletionItem.html#instantSubstitution-javax.swing.text.JTextComponent-">
     * CompletionItem.instantSubstitution</a></code>
     *
     * @param instantSubstitution A predicate that performs instant substitution
     * @return this
     */
    public CompletionBuilder<I> setInstantSubstitution(BiPredicate<? super I, ? super JTextComponent> instantSubstitution) {
        this.instantSubstitution = instantSubstitution;
        return this;
    }

    /**
     * Handle key events over an item directly using this handler.
     *
     * @param keyEventHandler A function which will receive key events and the item they occurred over.
     * @return this
     */
    public CompletionBuilder<I> setKeyEventHandler(ThrowingBiConsumer<? super I, ? super KeyEvent> keyEventHandler) {
        this.keyEventHandler = keyEventHandler;
        return this;
    }

    /**
     * Set the sorter, which will provide sort priority values to items.
     *
     * @param sorter A function which can provide a sort priority for items
     * @return this
     */
    public CompletionBuilder<I> setSorter(ToIntFunction<? super I> sorter) {
        this.sorter = sorter;
        return this;
    }
}

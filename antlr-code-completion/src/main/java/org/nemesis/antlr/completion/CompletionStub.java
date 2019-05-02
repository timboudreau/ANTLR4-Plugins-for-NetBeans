package org.nemesis.antlr.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import javax.swing.text.JTextComponent;
import org.antlr.v4.runtime.Token;
import static org.nemesis.antlr.completion.TokenMatch.DEFAULT_TOKEN_MATCH;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class CompletionStub<I> {

    private final IntUnaryOperator sortPriority;
    private final ItemRenderer<? super I> renderer;
    private final Function<? super I, ? extends CompletionTask> docTaskFactory;
    private final Function<? super I, ? extends CompletionTask> tooltipTask;
    private final ThrowingTriConsumer<? super I, ? super JTextComponent, ? super TokenMatch> performer;
    private final BiPredicate<? super I, ? super JTextComponent> instantSubstitution;
    private final ThrowingBiConsumer<? super I, ? super KeyEvent> keyEventHandler;
    private final ToIntFunction<? super I> sorter;
    private final BiFunction<? super StringKind, ? super I, ? extends String> stringifier;
    private final BiFunction<? super List<Token>, ? super Token, ? extends TokenMatch> tokenPatternMatcher;
    private final CompletionItemProvider<? extends I> itemsProvider;

    public CompletionStub(
            IntUnaryOperator sortPriority,
            ItemRenderer<? super I> renderer,
            Function<? super I, ? extends CompletionTask> docTaskFactory,
            Function<? super I, ? extends CompletionTask> tooltipTask,
            ThrowingTriConsumer<? super I, ? super JTextComponent, ? super TokenMatch> performer,
            BiPredicate<? super I, ? super JTextComponent> instantSubstitution,
            ThrowingBiConsumer<? super I, ? super KeyEvent> keyEventHandler,
            ToIntFunction<? super I> sorter,
            BiFunction<? super StringKind, ? super I, ? extends String> stringifier,
            BiFunction<? super List<Token>, ? super Token, ? extends TokenMatch> tokenPatternMatcher,
            CompletionItemProvider<? extends I> itemsProvider) {
        this.sortPriority = sortPriority;
        this.renderer = renderer;
        this.docTaskFactory = docTaskFactory;
        this.tooltipTask = tooltipTask;
        this.performer = performer;
        this.instantSubstitution = instantSubstitution;
        this.keyEventHandler = keyEventHandler;
        this.sorter = sorter;
        this.stringifier = stringifier;
        this.tokenPatternMatcher = tokenPatternMatcher;
        this.itemsProvider = itemsProvider;
    }

    TokenMatch matches(List<Token> tokens, Token caretToken) {
        if (tokenPatternMatcher != null) {
            return tokenPatternMatcher.apply(tokens, caretToken);
        }
        return DEFAULT_TOKEN_MATCH;
    }

    List<CompletionItem> run(TokenMatch match, ThrowingFunction<CompletionItemProvider<? extends I>, Collection<? extends I>> cachedFetcher) throws Exception {
        List<CompletionItem> result = new ArrayList<>();
        Collection<? extends I> all = cachedFetcher.apply(itemsProvider);
        all.forEach((item) -> {
            result.add(new Item(item, match));
        });
        return result;
    }

    class Item implements CompletionItem {

        private final I item;
        private final TokenMatch match;

        public Item(I item, TokenMatch match) {
            this.item = item;
            this.match = match;
        }

        @Override
        public int getPreferredWidth(Graphics g, Font defaultFont) {
            return renderer.getPreferredWidth(item, g, defaultFont);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
            renderer.render(item, g, defaultFont, defaultColor, backgroundColor, width, height, selected);
        }

        @Override
        public CompletionTask createDocumentationTask() {
            return docTaskFactory == null ? null : docTaskFactory.apply(item);
        }

        @Override
        public CompletionTask createToolTipTask() {
            return tooltipTask == null ? null : tooltipTask.apply(item);
        }

        @Override
        public void defaultAction(JTextComponent component) {
            try {
                performer.apply(item, component, match);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        public void processKeyEvent(KeyEvent evt) {
            if (keyEventHandler == null) {
                return;
            }
            try {
                keyEventHandler.accept(item, evt);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        public boolean instantSubstitution(JTextComponent component) {
            return instantSubstitution == null ? false : instantSubstitution.test(item, component);
        }

        @Override
        public int getSortPriority() {
            if (sortPriority == null && sorter == null) {
                return 1;
            } else if (sortPriority != null && sorter == null) {
                return sortPriority.applyAsInt(1);
            } else if (sorter != null && sortPriority == null) {
                return sorter.applyAsInt(item);
            }
            return sortPriority.applyAsInt(sorter.applyAsInt(item));
        }

        @Override
        public CharSequence getSortText() {
            if (stringifier != null) {
                return stringifier.apply(StringKind.SORT_TEXT, item);
            }
            return null;
        }

        @Override
        public CharSequence getInsertPrefix() {
            String result = null;
            if (stringifier != null) {
                result = stringifier.apply(StringKind.INSERT_PREFIX, item);
            }
            return result;
        }

        @Override
        public String toString() {
            return CompletionStub.this.getClass().getSimpleName()
                    + "." + getClass().getSimpleName() + "{" + item
                    + " for " + match + "}";
        }
    }
}

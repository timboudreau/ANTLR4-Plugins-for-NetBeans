package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.RuleNode;
import org.nemesis.antlrformatting.impl.CaretFixer;
import org.nemesis.antlrformatting.impl.CaretInfo;
import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.openide.util.Exceptions;

/**
 * The LexingState, during a parse, captures numbers and booleans about the
 * current context of the parse, which can be used to determine when a
 * formatting rule is active. It can be used to, say, apply different rules to
 * the last token in a list, or distinguish multi-line statements from single-
 * line ones, or tokens within lists of elements. It is configured in your
 * {@link org.nemesis.antlrformatting.spi.AntlrFormatterProvider}'s
 * <code>populateState()</code> method with a builder which can be very flexibly
 * configured to capture a number or boolean (such as the number of tokens
 * distance to another token of a certain type) keyd to an enum constant. These
 * values can then be used during formatting to apply different rules, or decide
 * what sort of formatting to apply.
 *
 * @author Tim Boudreau
 */
public final class LexingState {

    private final boolean[] booleans;
    private final IntList[] stacks;
    private final int[] values;
    private final LexingStateBuilder.Kind[] kinds;
    private final List<BiConsumer<Token, LexerScanner>> befores;
    private final List<BiConsumer<Token, LexerScanner>> afters;
    private final Class<? extends Enum<?>> enumType;

    LexingState(boolean[] booleans, IntList[] stacks, int[] values, LexingStateBuilder.Kind[] kinds, List<BiConsumer<Token, LexerScanner>> consumers, List<BiConsumer<Token, LexerScanner>> afters, Class<? extends Enum<?>> enumType) {
        this.booleans = booleans;
        this.stacks = stacks;
        this.values = values;
        this.kinds = kinds;
        this.befores = consumers;
        this.afters = afters;
        this.enumType = enumType;
    }

    LexingState snapshot() {
        boolean[] booleansSnapshot = Arrays.copyOf(booleans, booleans.length);
        int[] valuesSnapshot = Arrays.copyOf(values, values.length);
        IntList[] stacksSnapshot = new IntList[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                stacksSnapshot[i] = new IntList(stacks[i]);
            }
        }
        return new LexingState(booleansSnapshot, stacksSnapshot, valuesSnapshot, kinds,
                Collections.emptyList(), Collections.emptyList(), enumType);
    }

    private Set<Class<?>> warned;

    private <T extends Enum<T>> void checkType(T enumElement) {
        if (!enumType.isInstance(enumElement)) {
            boolean alreadyWarned;
            if (warned == null) {
                warned = new HashSet<>();
                alreadyWarned = false;
            } else {
                alreadyWarned = warned.contains(enumElement.getClass());
            }
            if (!alreadyWarned) {
                new Exception("Querying with an enum " + enumElement + " of type " + enumElement.getClass().getName()
                        + " but LexingState was built with " + enumType.getName() + " - this is probably a bug.")
                        .printStackTrace(System.err);
                warned.add(enumElement.getClass());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public String toString() {
        return toString((Class) enumType);
    }

    private boolean isSet(Enum<?> e) {
        int ord = e.ordinal();
        if (ord > kinds.length || kinds[ord] == null) {
            return false;
        }
        switch (kinds[ord]) {
            case BOOLEAN:
                return booleans[ord];
            case COUNTER:
                return values[ord] != -1;
            case STACK:
                return stacks[ord] != null && !stacks[ord].isEmpty();
            default:
                return false;
        }
    }

    <T extends Enum<T>> String toString(Class<T> type) {
        StringBuilder sb = new StringBuilder("LexingState{");
//        sb.append("kinds=").append(Arrays.toString(kinds));
        int initialLength = sb.length();
        for (T t : type.getEnumConstants()) {
//            if (!isSet(t)) {
//                continue;
//            }
            if (kinds[t.ordinal()] != null) {
                if (sb.length() != initialLength) {
                    sb.append(", ");
                }
                switch (kinds[t.ordinal()]) {
                    case BOOLEAN:
                        sb.append(t.name()).append('=').append(booleans[t.ordinal()]);
                        break;
                    default:
                        sb.append(t.name()).append('=').append(get(t));
                        if (kinds[t.ordinal()] == LexingStateBuilder.Kind.STACK) {
                            if (stacks[t.ordinal()].size() > 0) {
                                sb.append(" (").append(stacks[t.ordinal()]).append(")");
                            }
                        }
                }
            }
        }
        return sb.append('}').toString();
    }

    void clear() {
        Arrays.fill(booleans, false);
        for (IntList i : stacks) {
            if (i != null) {
                i.clear();
            }
        }
        Arrays.fill(values, -1);
    }

    /**
     * Get the boolean associated with a particular enum key.
     *
     * @param <T>
     * @param key The key
     * @return A boolean
     */
    public <T extends Enum<T>> boolean getBoolean(T key) {
        checkType(key);
        return booleans[key.ordinal()];
    }

    /**
     * Get the first value (for stack-oriented values) assigned to one of the
     * passed keys.
     *
     * @param <T> The key type
     * @param first The first key
     * @param all Any other keys to check
     * @return A value or -1.
     */
    @SafeVarargs
    public final <T extends Enum<T>> int getFirst(T first, T... all) {
        checkType(first);
        int result = get(first);
        if (result == -1) {
            for (T other : all) {
                checkType(other);
                result = get(other);
                if (result != -1) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * For stack-oriented enum keys, get the <i>size</i> of the stack - for
     * example, if you are counting nested braces, this would give you the
     * nesting depth, which might reflect how many stops of indentation to
     * apply.
     *
     * @param <T> The key type
     * @param item The key
     * @return An int or -1
     * @throws IllegalArgumentException if the key was not configured in this
     * lexing state's builder to be stack-oriented
     */
    public <T extends Enum<T>> int stackSize(T item) {
        checkType(item);
        if (kinds[item.ordinal()] != LexingStateBuilder.Kind.STACK) {
            throw new IllegalArgumentException("Not a stack counter: " + item);
        }
        return stacks[item.ordinal()].size();
    }

    /**
     * Get a list of all currently pushed values for a stack-oriented key.
     *
     * @param <T>
     * @param key
     * @return
     * @throws IllegalArgumentException if the key was not configured in this
     * lexing state's builder to be stack-oriented
     */
    public <T extends Enum<T>> List<Integer> allValues(T key) {
        checkType(key);
        if (kinds[key.ordinal()] != LexingStateBuilder.Kind.STACK) {
            throw new IllegalArgumentException("Not a stack counter: " + key);
        }
        return new ArrayList<>(stacks[key.ordinal()]);
    }

    /**
     * Get a value, using the default value if none is present.
     *
     * @param <T> The type
     * @param key The key
     * @param defaultValue The value to use if unset
     * @return A value
     */
    public <T extends Enum<T>> int get(T key, int defaultValue) {
        checkType(key);
        int result = get(key);
        if (result == -1) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * Get the value for an item. If the item was specified as a boolean, true =
     * 1 and false = 0.
     *
     * @param <T> The type
     * @param key The key
     * @return A value or -1
     */
    public <T extends Enum<T>> int get(T key) {
        checkType(key);
        int ord = key.ordinal();
        if (kinds[ord] == null) {
            throw new IllegalArgumentException("No handler for " + key + " created");
        }
        switch (kinds[ord]) {
            case BOOLEAN:
                return booleans[ord] ? 1 : 0;
            case COUNTER:
                return values[ord];
            case STACK:
                IntList stack = stacks[ord];
                if (stack.isEmpty()) {
                    return -1;
                }
                return stack.peek();
            default:
                throw new AssertionError();
        }
    }

    void onBeforeProcessToken(Token t, LexerScanner scanner) {
        for (BiConsumer<Token, LexerScanner> c : befores) {
            c.accept(t, scanner);
        }
    }

    void onAfterProcessToken(Token t, LexerScanner scanner) {
        for (BiConsumer<Token, LexerScanner> c : afters) {
            c.accept(t, scanner);
        }
    }

    public static <T extends Enum<T>> LexingStateBuilder<T, LexingState> builder(Class<T> type) {
        return new LexingStateBuilder<>(type, (LexingState ls) -> {
            return ls;
        });
    }

    // Just reifies List<Integer> so we can put it in an array
    // without the compiler complaining
    static final class IntList extends LinkedList<Integer> {

        IntList(IntList stack) {
            super(stack);
        }

        IntList() {

        }
    }

    // Implementation of API/SPI accessor bridge
    static final class FA extends FormattingAccessor {

        @Override
        public FormattingResult reformat(int start, int end, int indentSize,
                FormattingRules rules, LexingState state, Criterion whitespace,
                Predicate<Token> debug, Lexer lexer, String[] modeNames,
                CaretInfo caretPos, CaretFixer updateWithCaretPosition,
                RuleNode parseTreeRoot) {
            lexer.reset();
            EverythingTokenStream tokens = new EverythingTokenStream(lexer, modeNames);
            IntFunction<Set<Integer>> ruleFetcher = null;
            if (parseTreeRoot != null) {
                ParserRuleCollector collector = new ParserRuleCollector(tokens);
                ruleFetcher = collector.visit(parseTreeRoot);
                lexer.reset();
            }

            TokenStreamRewriter rew = new TokenStreamRewriter(tokens);
            return new FormattingContextImpl(rew, start, end, indentSize,
                    rules, state, whitespace, debug, ruleFetcher)
                    .go(tokens, caretPos, updateWithCaretPosition);
        }

        @Override
        public FormattingRules createFormattingRules(Vocabulary vocabulary, String[] modeNames, String[] parserRuleNames) {
            return new FormattingRules(vocabulary, modeNames, parserRuleNames);
        }
    }

    static {
        FormattingAccessor.DEFAULT = new FA();
        try {
            // Ensure it's initialized
            Class.forName(AntlrFormatterProvider.class.getName(), true, LexingState.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}

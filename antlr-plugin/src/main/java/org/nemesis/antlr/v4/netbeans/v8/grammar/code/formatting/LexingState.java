package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;

/**
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

    public <T extends Enum<T>> String toString(Class<T> type) {
        StringBuilder sb = new StringBuilder("LexingState{");
        int initialLength = sb.length();
        for (T t : type.getEnumConstants()) {
            if (!isSet(t)) {
                continue;
            }
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

    public <T extends Enum<T>> boolean getBoolean(T item) {
        checkType(item);
        return booleans[item.ordinal()];
    }

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

    public <T extends Enum<T>> int stackSize(T item) {
        checkType(item);
        if (kinds[item.ordinal()] != LexingStateBuilder.Kind.STACK) {
            throw new IllegalArgumentException("Not a stack counter: " + item);
        }
        return stacks[item.ordinal()].size();
    }

    public <T extends Enum<T>> List<Integer> allValues(T item) {
        checkType(item);
        if (kinds[item.ordinal()] != LexingStateBuilder.Kind.STACK) {
            throw new IllegalArgumentException("Not a stack counter: " + item);
        }
        return new ArrayList<>(stacks[item.ordinal()]);
    }

    public <T extends Enum<T>> int get(T item, int defaultValue) {
        checkType(item);
        int result = get(item);
        if (result == -1) {
            result = defaultValue;
        }
        return result;
    }

    public <T extends Enum<T>> int get(T item) {
        checkType(item);
        int ord = item.ordinal();
        if (kinds[ord] == null) {
            throw new IllegalArgumentException("No handler for " + item + " created");
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

    interface LexerScanner {

        int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType);

        int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType);

        int tokenCountToNext(boolean ignoreWhitespace, IntPredicate targetType);

        int tokenCountToPreceding(boolean ignoreWhitespace, IntPredicate targetType);

        int currentCharPositionInLine();

        int origCharPositionInLine();
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
}

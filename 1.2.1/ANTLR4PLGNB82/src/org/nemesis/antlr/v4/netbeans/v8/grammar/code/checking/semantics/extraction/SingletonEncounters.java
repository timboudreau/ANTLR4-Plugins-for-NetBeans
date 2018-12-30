package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.SingletonEncounters.SingletonEncounter;

/**
 * The set of encounters with somethibg which is expected to be a singleton -
 * data that occurs once per parsed document, but which, in a malformed source,
 * may not be.
 *
 * @param <KeyType>
 */
public class SingletonEncounters<KeyType> implements Iterable<SingletonEncounter<KeyType>>, Serializable {

    private final List<SingletonEncounter<KeyType>> encounters = new ArrayList<>(3);

    public SingletonEncounter<KeyType> first() {
        return hasEncounter() ? encounters.get(0) : null;
    }

    void add(KeyType key, int start, int end, Class<? extends ParserRuleContext> in) {
        encounters.add(new SingletonEncounter<>(start, end, key, in));
    }

    /**
     * Returns true if there is only one encounter, and the value extracted for
     * it equals() the passed expected value.
     *
     * @param expectedValue The value to test equality against
     * @return true if they are equal
     */
    public boolean is(KeyType expectedValue) {
        return encounters.size() == 1 && Objects.equals(expectedValue, first().value());
    }

    @Override
    public Iterator<SingletonEncounter<KeyType>> iterator() {
        return Collections.unmodifiableList(encounters).iterator();
    }

    /**
     * Visit all encounters which are <i>not the first one</i> (useful for marking
     * these as errors).
     *
     * @param c A consumer
     * @return The number visited
     */
    public int visitOthers(Consumer<SingletonEncounter<KeyType>> c) {
        if (encounters.size() < 2) {
            return 0;
        }
        for (int i = 1; i < encounters.size(); i++) {
            c.accept(encounters.get(i));
            ;
        }
        return encounters.size() - 2;
    }

    public boolean hasEncounter() {
        return !encounters.isEmpty();
    }

    public boolean hasMultiple() {
        return encounters.size() > 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < encounters.size(); i++) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(i).append(": ").append(encounters.get(i));
        }
        return sb.toString();
    }

    public static final class SingletonEncounter<KeyType> implements Serializable {

        private final int start;
        private final int end;
        private final KeyType key;
        private final Class<? extends ParserRuleContext> in;

        public SingletonEncounter(int start, int end, KeyType key, Class<? extends ParserRuleContext> in) {
            this.start = start;
            this.end = end;
            this.key = key;
            this.in = in;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public KeyType value() {
            return key;
        }

        public Class<? extends ParserRuleContext> in() {
            return in;
        }

        public String toString() {
            return key + "@" + start + ":" + end + "`" + in.getSimpleName();
        }
    }
}

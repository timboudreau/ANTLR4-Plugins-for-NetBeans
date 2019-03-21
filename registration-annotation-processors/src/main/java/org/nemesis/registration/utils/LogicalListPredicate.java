package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import org.nemesis.misc.utils.ThreadLocalUtils;

/**
 * Keeps stack depth from going insane, and provides pretty formatting for
 * toString().
 *
 * @author Tim Boudreau
 */
final class LogicalListPredicate<T> extends AbstractNamed implements NamedPredicate<T>, ListPredicate<T> {

    private final List<Predicate<? super T>> l = new ArrayList<>(5);
    private final boolean and;

    LogicalListPredicate(boolean and, Predicate<? super T> first) {
        this(and);
        accept(first);
    }

    LogicalListPredicate(boolean and) {
        this.and = and;
    }

    @Override
    public Iterator<Predicate<? super T>> iterator() {
        return l.iterator();
    }

    void add(Predicate<T> pred) {
        l.add(pred);
    }

    @Override
    public NamedPredicate<T> and(Predicate<? super T> other) {
        if (and) {
            accept(other);
            return this;
        } else {
            return PredicateUtils.andPredicate(this).and(other);
        }
    }

    @Override
    public NamedPredicate<T> or(Predicate<? super T> other) {
        if (!and) {
            accept(other);
            return this;
        } else {
            return PredicateUtils.orPredicate(this).or(other);
        }
    }

    static final Consumer<IntConsumer> ENTRY_COUNT = ThreadLocalUtils.entryCounter();

    @Override
    public String name() {
        if (l.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        ENTRY_COUNT.accept(depth -> {
            char[] indent = new char[depth * 2];
            for (Iterator<?> it = l.iterator(); it.hasNext();) {
                Object next = it.next();
                String name = Named.findName(next);
                if (!(next instanceof LogicalListPredicate<?>)) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    sb.append(indent);
                }
                sb.append(name);
                if (it.hasNext()) {
                    sb.append(and ? " &&" : " ||");
                }
            }
        });
        return sb.toString();
    }

    @Override
    public boolean test(T t) {
        boolean result = true;
        List<Predicate<? super T>> copy = new ArrayList<>(l);
        for (Predicate<? super T> p : copy) {
            if (and) {
                result &= p.test(t);
            } else {
                result |= p.test(t);
            }
            if (and && !result) {
                break;
            }
        }
        return result;
    }

    @Override
    public void accept(Predicate<? super T> t) {
        l.add(t);
    }

}

package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class PredicateUtils {

    private PredicateUtils() {
        throw new AssertionError();
    }

    public static <T> NamedPredicate<T> alwaysTrue() {
        return new AllOrNothing<>(true);
    }

    public static <T> NamedPredicate<T> alwaysFalse() {
        return new AllOrNothing<>(false);
    }

    public static <T> NamedPredicate<T> namedPredicate(String name, Predicate<T> pred) {
        Named named = Wrapper.find(pred, Named.class);
        if (named != null && !name.equals(named.name())) {
            name = name + "<was:" + named.name() + ">";
        } else if (named != null && name.equals(named.name()) && pred instanceof NamedPredicate<?>) {
            return (NamedPredicate<T>) pred;
        }
        return new NamedWrapperPredicate<>(name, pred);
    }

    public static <T> NamedPredicate<T> namedPredicate(Supplier<String> name, Predicate<T> pred) {
        if (pred == null) {
            throw new IllegalArgumentException("Null predicate for "
                    + (name == null ? "null" : name.get()));
        }
        NamedPredicate<T> result;
        if (pred instanceof NamedPredicate<?>) {
            result = (NamedPredicate<T>) pred;
        } else {
            Named named = Wrapper.find(pred, Named.class);
            if (named != null && !name.equals(named.name())) {
                Supplier<String> old = name;
                name = () -> old.get() + "<was:" + named.name() + ">";
            }
        }
        return new NamedWrapperPredicate<>(name, pred);
    }

    public static <T> NamedPredicate<T> namedPredicate(Predicate<T> pred) {
        if (pred instanceof NamedPredicate<?>) {
            return ((NamedPredicate<T>) pred);
        }
        Named n = Wrapper.find(pred, Named.class);
        if (n != null) {
            return namedPredicate(n.name(), pred);
        }
        StackTraceElement[] els = new Exception().getStackTrace();
        if (els == null || els.length == 0) { // compiled w/o debug info
            return namedPredicate(pred.toString(), pred);
        }
        StackTraceElement theElement = null;
        String nm = PredicateUtils.class.getName();
        for (int i = 0; i < els.length; i++) {
            String cn = els[i].getClassName();
            if (cn == null) {
                continue;
            }
            if (nm.equals(cn) || (cn != null && (cn.startsWith(nm) || cn.startsWith("java.")))) {
                continue;
            }
            if (cn.contains("AbstractPredicateBuilder")) {
                continue;
            }
            theElement = els[i];
            break;
        }
        return namedPredicate(theElement.toString() + ":" + pred, pred);
    }

    public static final <V, T> Predicate<T> converted(Function<T, V> converter, Predicate<V> predicate, AbsenceAction onNull) {
        return new ConvertingPredicate<>(predicate, converter, onNull);
    }

    public static final <V, T> Predicate<T> converted(Function<T, V> converter, Predicate<V> predicate) {
        return new ConvertingPredicate<>(predicate, converter, AbsenceAction.TRUE);
    }

    public static final <V, T> NamedPredicate<T> converted(NamedPredicate<V> predicate, Function<T, V> converter, AbsenceAction onNull) {
        return new ConvertingNamedPredicate<>(predicate, converter, onNull);
    }

    public static final <V, T> NamedPredicate<T> converted(NamedPredicate<V> predicate, Function<T, V> converter) {
        return new ConvertingNamedPredicate<>(predicate, converter, AbsenceAction.TRUE);
    }

    public static <T> ListPredicate<T> andPredicate(Predicate<? super T> first) {
        return new LogicalListPredicate(true, first);
    }

    public static <T> ListPredicate<T> orPredicate(Predicate<? super T> first) {
        return new LogicalListPredicate(false, first);
    }

    public static <T> ListPredicate<T> andPredicate() {
        return new LogicalListPredicate(true);
    }

    public static <T> ListPredicate<T> orPredicate() {
        return new LogicalListPredicate(false);
    }

    static <T> NamedPredicate<Iterable<T>> listPredicate(boolean and, NamedPredicate<? super T> orig) {
        return new ListConvertedNamedPredicate<>(orig, and);
    }

    static <T> Predicate<Iterable<T>> listPredicate(Predicate<? super T> orig, boolean and) {
        if (orig instanceof NamedPredicate<?>) {
            return new ListConvertedNamedPredicate<>((NamedPredicate<? super T>) orig, and);
        } else {
            return new ListConvertedPredicate<>(orig, and);
        }
    }

    private static class ListConvertedPredicate<T> implements Predicate<Iterable<T>>, Wrapper<Predicate<? super T>> {

        final Predicate<? super T> orig;
        private final boolean and;

        public ListConvertedPredicate(Predicate<? super T> orig, boolean and) {
            this.orig = orig;
            this.and = and;
        }

        @Override
        public boolean test(Iterable<T> t) {
            boolean result = and;
            for (T obj : t) {
                if (and) {
                    result &= orig.test(obj);
                    if (!result) {
                        break;
                    }
                } else {
                    result |= orig.test(obj);
                    if (result) {
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return Named.findName(orig);
        }

        @Override
        public Predicate<? super T> wrapped() {
            return orig;
        }
    }

    private static final class ListConvertedNamedPredicate<T> extends ListConvertedPredicate<T> implements NamedPredicate<Iterable<T>> {

        public ListConvertedNamedPredicate(NamedPredicate<? super T> orig, boolean and) {
            super(orig, and);
        }

        @Override
        public String name() {
            return ((NamedPredicate<?>) orig).name();
        }

        @Override
        public String toString() {
            return name();
        }
    }

    private static final class LogicalListPredicate<T> extends AbstractNamed implements NamedPredicate<T>, ListPredicate<T> {

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

        @Override
        public String name() {
            if (l.isEmpty()) {
                return "empty";
            }
            StringBuilder sb = new StringBuilder();
            for (Iterator<?> it = l.iterator(); it.hasNext();) {
                String name = Named.findName(it.next());
                sb.append(name);
                if (it.hasNext()) {
                    sb.append(" && ");
                }
            }
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

    static final class ConvertingPredicate<T, V> implements Wrapper<Predicate<V>>, Predicate<T> {

        private final Predicate<V> delegate;
        private final Function<T, V> converter;
        private final BooleanSupplier nullConversionHandler;

        public ConvertingPredicate(Predicate<V> delegate, Function<T, V> converter, BooleanSupplier nullConversionHandler) {
            this.delegate = delegate;
            this.converter = converter;
            this.nullConversionHandler = nullConversionHandler;
        }

        @Override
        public Predicate<V> wrapped() {
            return delegate;
        }

        @Override
        public boolean test(T t) {
            V v = converter.apply(t);
            if (v == null && nullConversionHandler != null) {
                if (nullConversionHandler != AbsenceAction.PASS_THROUGH) {
                    return nullConversionHandler.getAsBoolean();
                }
            }
            return delegate.test(v);
        }

        public String toString() {
            return Named.findName(delegate);
        }

        @Override
        public Predicate<T> and(Predicate<? super T> other) {
            return PredicateUtils.andPredicate(this).and(other);
        }

        @Override
        public Predicate<T> or(Predicate<? super T> other) {
            return PredicateUtils.orPredicate(this).or(other);
        }
    }

    static final class ConvertingNamedPredicate<T, V> implements Wrapper<Predicate<V>>, NamedPredicate<T> {

        private final NamedPredicate<V> delegate;
        private final Function<T, V> converter;
        private final BooleanSupplier nullConversionHandler;

        public ConvertingNamedPredicate(NamedPredicate<V> delegate, Function<T, V> converter, BooleanSupplier nullConversionHandler) {
            this.delegate = delegate;
            this.converter = converter;
            this.nullConversionHandler = nullConversionHandler;
        }

        public String name() {
            return delegate.name();
        }

        @Override
        public Predicate<V> wrapped() {
            return delegate;
        }

        @Override
        public boolean test(T t) {
            V v = converter.apply(t);
            if (v == null && nullConversionHandler != null) {
                if (nullConversionHandler != AbsenceAction.PASS_THROUGH) {
                    return nullConversionHandler.getAsBoolean();
                }
            }
            return delegate.test(v);
        }

        public String toString() {
            return Named.findName(delegate);
        }

        @Override
        public NamedPredicate<T> and(Predicate<? super T> other) {
            return PredicateUtils.andPredicate(this).and(other);
        }

        @Override
        public NamedPredicate<T> or(Predicate<? super T> other) {
            return PredicateUtils.orPredicate(this).or(other);
        }

    }

    static final class AllOrNothing<T> extends AbstractNamed implements NamedPredicate<T> {

        private final boolean all;

        public AllOrNothing(boolean all) {
            this.all = all;
        }

        @Override
        public String name() {
            return all ? "always-true" : "always-false";
        }

        @Override
        public boolean test(T t) {
            return all;
        }

        @Override
        public NamedPredicate<T> and(Predicate<? super T> other) {
            return other instanceof NamedPredicate<?> ? (NamedPredicate<T>) other : namedPredicate((Predicate<T>) other);
        }
    }

    public static <T> Predicate<T> lazyPredicate(Supplier<Predicate<T>> supp) {
        return new Lazy<>(supp);
    }

    static final class Lazy<T> implements Predicate<T> {

        private final Supplier<Predicate<T>> factory;
        private Predicate<T> value;

        public Lazy(Supplier<Predicate<T>> factory) {
            this.factory = factory;
        }

        @Override
        public boolean test(T t) {
            return value().test(t);
        }

        @Override
        public String toString() {
            return value().toString();
        }

        private Predicate<T> value() {
            if (this.value == null) {
                this.value = factory.get();
            }
            return value;
        }

    }
}

package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class ListTransformPredicate<T, R> implements NamedPredicate<T>, Wrapper<Predicate<R>> {

    private final NamedPredicate<R> orig;
    private final Function<T, List<? extends R>> xform;
    private final boolean and;

    public ListTransformPredicate(NamedPredicate<R> orig, Function<T, List<? extends R>> xform) {
        this(orig, xform, true);
    }

    public ListTransformPredicate(NamedPredicate<R> orig, Function<T, List<? extends R>> xform, boolean and) {
        this.orig = orig;
        this.xform = xform;
        this.and = and;
    }

    @Override
    public String name() {
        StringBuilder sb = new StringBuilder();
        LogicalListPredicate.ENTRY_COUNT.accept(depth -> {
//            char[] c = new char[depth * 2];
//            sb.append(c).append("as-list:\n");
            sb.append(orig.name());
        });
        return sb.toString();
    }

    @Override
    public Predicate<R> wrapped() {
        return orig;
    }

    @Override
    public boolean test(T t) {
        List<R> l = new ArrayList<>(xform.apply(t));
        return orig.toListPredicate(and).test(l);
    }
}

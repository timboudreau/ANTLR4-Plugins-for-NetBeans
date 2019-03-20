package org.nemesis.registration.utils;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
class NamedWrapperPredicate<T> extends AbstractNamed implements NamedPredicate<T>, Wrapper<Predicate<T>> {

    private final Supplier<String> name;
    private final Predicate<T> delegate;

    NamedWrapperPredicate(Supplier<String> name, Predicate<T> delegate) {
        this.name = name;
        this.delegate = delegate;
        if (name == null) {
            throw new IllegalArgumentException("Name null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate null");
        }
    }

    NamedWrapperPredicate(String name, Predicate<T> delegate) {
        this.name = () -> name;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return name.get();
    }

    @Override
    public boolean test(T t) {
        return delegate.test(t);
    }

    @Override
    public Predicate<T> wrapped() {
        return delegate;
    }

}

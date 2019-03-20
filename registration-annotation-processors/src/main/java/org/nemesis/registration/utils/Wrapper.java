package org.nemesis.registration.utils;

/**
 *
 * @author Tim Boudreau
 */
interface Wrapper<P> extends Named {

    P wrapped();

    default P root() {
        P wrapped = wrapped();
        while (wrapped instanceof Wrapper<?>) {
            wrapped = ((Wrapper<P>) wrapped).wrapped();
        }
        return wrapped;
    }

    @Override
    default String name() {
        Named n = find(wrapped(), Named.class);
        if (n != null) {
            return n.name();
        }
        return wrapped().toString();
    }

    static <P1> P1 root(P1 o) {
        if (o instanceof Wrapper<?>) {
            return ((Wrapper<P1>) o).root();
        }
        return (P1) o;
    }

    static <F> F find(Object o, Class<? super F> what) {
        if (o instanceof Wrapper<?>) {
            return ((Wrapper<?>) o).find(what);
        }
        return null;
    }

    default <F> F find(Class<? super F> what) {
        if (what.isInstance(this)) {
            return (F) this;
        }
        P wrapped = wrapped();
        if (what.isInstance(wrapped)) {
            return (F) wrapped;
        }
        if (wrapped instanceof Wrapper<?>) {
            return ((Wrapper<?>) wrapped).find(what);
        }
        return null;
    }
}

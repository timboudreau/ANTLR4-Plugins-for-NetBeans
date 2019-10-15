/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.source.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class AdaptedRelativeResolverImplementation<F, T> extends RelativeResolverImplementation<T> {

    private final RelativeResolverImplementation<F> from;
    private final Function<? super F, ? extends T> toTo;
    private final Function<? super T, ? extends F> toFrom;

    AdaptedRelativeResolverImplementation(RelativeResolverImplementation<F> from, Class<T> type, Function<? super F, ? extends T> toTo, Function<? super T, ? extends F> toFrom) {
        super(type);
        this.from = from;
        this.toTo = toTo;
        this.toFrom = toFrom;
    }

    @Override
    public String toString() {
        return from + " -> " + type().getName()
                + " using " + toFrom + " and " + toTo;
    }

    private F toFileObject(T doc) {
        return toFrom.apply(doc);
    }

    private T toDocument(F fo) {
        return toTo.apply(fo);
    }

    @Override
    public Optional<T> resolve(T relativeTo, String name) {
        F fo = toFileObject(relativeTo);
        if (fo != null) {
            Optional<F> result = from.resolve(fo, name);
            if (result.isPresent()) {
                T doc = toDocument(result.get());
                return Optional.ofNullable(doc);
            }
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.from);
        hash = 29 * hash + Objects.hashCode(this.toTo);
        hash = 29 * hash + Objects.hashCode(this.toFrom);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AdaptedRelativeResolverImplementation<?, ?> other = (AdaptedRelativeResolverImplementation<?, ?>) obj;
        if (!Objects.equals(this.from, other.from)) {
            return false;
        }
        if (!Objects.equals(this.toTo, other.toTo)) {
            return false;
        }
        return Objects.equals(this.toFrom, other.toFrom);
    }
}

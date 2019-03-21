/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.registration.utils;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
interface NamedPredicate<T> extends Named, Predicate<T> {

    public default NamedPredicate<Iterable<T>> toListPredicate(boolean and) {
        return PredicateUtils.listPredicate(and, this);
    }

    default <R> NamedPredicate<R> listTransform(Function<R, List<? extends T>> func) {
        return new ListTransformPredicate<>(this, func);
    }

    default <R> NamedPredicate<R> convert(Function<R, T> converter, AbsenceAction onNullConversion) {
        return PredicateUtils.converted(this, converter, onNullConversion);
    }

    @Override
    public default NamedPredicate<T> and(Predicate<? super T> other) {
        Predicate<T> myRoot = Wrapper.root(this);
        Predicate<? super T> otherRoot = Wrapper.root(other);
        Predicate<T> toInvoke = myRoot.and(otherRoot);
        String myName = name();
        String otherName = Named.findName(other);
        return new NamedWrapperPredicate<>("(" + myName + " && " + otherName + ")", toInvoke);
    }

    @Override
    public default NamedPredicate<T> negate() {
        Predicate<T> myRoot = Wrapper.root(this);
        String myName = name();
        return new NamedWrapperPredicate<T>("!" + myName, myRoot) {
            @Override
            public NamedPredicate<T> negate() {
                return NamedPredicate.this;
            }
        };
    }

    @Override
    public default NamedPredicate<T> or(Predicate<? super T> other) {
        Predicate<T> myRoot = Wrapper.root(this);
        Predicate<? super T> otherRoot = Wrapper.root(other);
        Predicate<T> toInvoke = myRoot.or(otherRoot);
        String myName = name();
        String otherName = Named.findName(other);
        return new NamedWrapperPredicate<>("(" + myName + " || " + otherName + ")", toInvoke);
    }
}

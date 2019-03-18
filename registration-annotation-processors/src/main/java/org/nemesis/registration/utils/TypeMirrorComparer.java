package org.nemesis.registration.utils;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
abstract class TypeMirrorComparer implements BiPredicate<TypeMirror, TypeMirror> {

    private final AnnotationUtils utils;

    TypeMirrorComparer(AnnotationUtils utils) {
        this.utils = utils;
    }

    public TypeMirrorComparer erasure() {
        return new TypeMirrorComparer(utils) {
            @Override
            public boolean test(TypeMirror t, TypeMirror u) {
                return TypeMirrorComparer.this.test(utils.erasureOf(t), utils.erasureOf(u));
            }
        };
    }

    public TypeMirrorComparer reverse() {
        return new TypeMirrorComparer(utils) {
            @Override
            public boolean test(TypeMirror t, TypeMirror u) {
                return TypeMirrorComparer.this.test(u, t);
            }
        };
    }

    public Predicate<TypeMirror> toPredicate(TypeMirror t) {
        return new Predicate<TypeMirror>() {
            @Override
            public boolean test(TypeMirror o) {
                return TypeMirrorComparer.this.test(t, o);
            }

            @Override
            public String toString() {
                return TypeMirrorComparer.this.toString() + "(" + t + ")";
            }
        };
    }

    public Predicate<TypeMirror> toPredicate(Supplier<TypeMirror> t) {
        return new Predicate<TypeMirror>() {
            @Override
            public boolean test(TypeMirror o) {
                return TypeMirrorComparer.this.test(t.get(), o);
            }

            @Override
            public String toString() {
                return TypeMirrorComparer.this.toString() + "(" + t + ")";
            }
        };
    }

}

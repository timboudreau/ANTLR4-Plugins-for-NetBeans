package org.nemesis.registration.utils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 *
 * @author Tim Boudreau
 */
enum TypeMirrorComparison {
    EXACT_MATCH, SAME_TYPE, IS_SUBTYPE, IS_SUPERTYPE, IS_ASSIGNABLE,
    IS_SUBSIGNATURE, IS_SUPERSIGNATURE;

    static {
        AnnotationUtils.forceLogging();
    }

    private <V> Supplier<TypeMirror> lazyTypeMirrorSupplier(V v, Function<V, TypeMirror> convert, BiFunction<Boolean, String, Boolean> b) {
        return () -> {
            TypeMirror result = convert.apply(v);
            b.apply(result != null, "No type element found for " + v);
            return result;
        };
    }

    <V> Predicate<TypeMirror> predicate(V v, Function<V, TypeMirror> convert, AnnotationUtils utils, BiFunction<Boolean, String, Boolean> b) {
        // Do NOT convert ahead of time, or we can fail compilation on
        // creating a type test if we are simply on the classpath
        // of a project that does not have the type we're resolving
        // on the classpath
        return predicate(lazyTypeMirrorSupplier(v, convert, b), utils, b);
    }

    Predicate<TypeMirror> predicate(Supplier<TypeMirror> lazy, AnnotationUtils utils, BiFunction<Boolean, String, Boolean> b) {
        return comparer(utils, b).toPredicate(lazy);
    }

    Predicate<TypeMirror> predicate(TypeMirror tm, AnnotationUtils utils, BiFunction<Boolean, String, Boolean> b) {
        return comparer(utils, b).toPredicate(tm);
    }

    TypeMirrorComparer comparer(AnnotationUtils utils, BiFunction<Boolean, String, Boolean> b) {
        return new TypeMirrorComparer(utils) {
            @Override
            public boolean test(TypeMirror t, TypeMirror u) {
                if (u == null) {
                    return b.apply(false, "No first type provided with " + u);
                }
                if (t == null) {
                    return b.apply(false, "No second type provided with " + t);
                }
                Types types = utils.processingEnv().getTypeUtils();
                switch (TypeMirrorComparison.this) {
                    case EXACT_MATCH:
                        return b.apply(t.toString().equals(u.toString()),
                                t + " is does not match " + u);
                    case SAME_TYPE:
                        return b.apply(types.isSameType(t, u), t
                                + " is not the same type as " + u);
                    case IS_ASSIGNABLE:
                        return b.apply(types.isAssignable(t, u), t
                                + " is not assignable as " + u);
                    case IS_SUBTYPE:
                        return b.apply(types.isSubtype(t, u), t
                                + " is not a subtype of " + u);
                    case IS_SUPERTYPE:
                        return b.apply(types.isSubtype(u, t), t
                                + " is not a supertype of " + u);
                    case IS_SUBSIGNATURE:
                        if (!(u instanceof ExecutableType)) {
                            return b.apply(false, u
                                    + " is not an executable type");
                        }
                        if (!(t instanceof ExecutableType)) {
                            return b.apply(false, t
                                    + " is not an executable type");
                        }
                        ExecutableType ea = (ExecutableType) t;
                        ExecutableType eb = (ExecutableType) u;
                        return b.apply(types.isSubsignature(ea, eb), eb
                                + " is not a subsignature of " + ea);
                    case IS_SUPERSIGNATURE:
                        if (!(u instanceof ExecutableType)) {
                            return b.apply(false, u
                                    + " is not an executable type");
                        }
                        if (!(t instanceof ExecutableType)) {
                            return b.apply(false, t
                                    + " is not an executable type");
                        }
                        ExecutableType ea1 = (ExecutableType) t;
                        ExecutableType eb1 = (ExecutableType) u;
                        return b.apply(types.isSubsignature(eb1, ea1), eb1
                                + " is not a supersignature of " + ea1);
                }
                throw new AssertionError(TypeMirrorComparison.this);
            }

            @Override
            public String toString() {
                return TypeMirrorComparison.this.name();
            }
        };
    }

}

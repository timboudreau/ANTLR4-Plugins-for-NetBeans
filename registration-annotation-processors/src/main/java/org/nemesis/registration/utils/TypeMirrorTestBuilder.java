package org.nemesis.registration.utils;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class TypeMirrorTestBuilder<T> extends AbstractPredicateBuilder<TypeMirror, TypeMirrorTestBuilder<T>, T> {

    TypeMirrorTestBuilder(AnnotationUtils utils, Function<TypeMirrorTestBuilder<T>, T> converter) {
        super(utils, converter);
    }

    private Supplier<TypeMirror> toTypeMirror(String s) {
        return () -> {
            TypeElement el = utils.processingEnv().getElementUtils().getTypeElement(s);
            if (el == null) {
                fail("Could not load type " + s);
                return null;
            }
            return el.asType();
        };
    }

    public TypeMirrorTestBuilder<T> isAssignable(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.IS_ASSIGNABLE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> isAssignable(String type) {
        return addPredicate(TypeMirrorComparison.IS_ASSIGNABLE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isType(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.SAME_TYPE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> isType(String type) {
        return addPredicate(TypeMirrorComparison.SAME_TYPE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isSubtype(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.IS_SUBTYPE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> isSubtype(String type) {
        return addPredicate(TypeMirrorComparison.IS_SUBTYPE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isSupertype(String type) {
        return addPredicate(TypeMirrorComparison.IS_SUPERTYPE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isSupertype(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.IS_SUPERTYPE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> isSubsignature(String type) {
        return addPredicate(TypeMirrorComparison.IS_SUBSIGNATURE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isSubsignature(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.IS_SUBSIGNATURE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> isSupersignature(String type) {
        return addPredicate(TypeMirrorComparison.IS_SUPERSIGNATURE.predicate(toTypeMirror(type), utils, this::maybeFail));
    }

    public TypeMirrorTestBuilder<T> isSupersignature(Supplier<TypeMirror> other) {
        return addPredicate(TypeMirrorComparison.IS_SUPERSIGNATURE.comparer(utils, this::maybeFail).toPredicate(other));
    }

    public TypeMirrorTestBuilder<T> typeKindMustBe(TypeKind kind) {
        return addPredicate(m -> {
            return maybeFail(kind == m.getKind(), "Type kind of " + m + " must be " + kind + " but is " + m.getKind());
        });
    }

    public TypeMirrorTestBuilder<T> nestingKindMustBe(NestingKind kind) {
        return addPredicate(tm -> {
            TypeElement el = utils.processingEnv().getElementUtils().getTypeElement(tm.toString());
            return maybeFail(el != null, "Could not find a type element for " + tm, () -> {
                return kind == el.getNestingKind();
            });
        });
    }

    public TypeMirrorTestBuilder<T> nestingKindMustNotBe(NestingKind kind) {
        return addPredicate(tm -> {
            TypeElement el = utils.processingEnv().getElementUtils().getTypeElement(tm.toString());
            return maybeFail(el != null, "Could not find a type element for " + tm, () -> {
                return kind != el.getNestingKind();
            });
        });
    }

    public TypeMirrorTestBuilder<TypeMirrorTestBuilder<T>> testTypeParameterOfSupertypeOrInterface(String supertypeOrInterface, int typeParameter) {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            typeKindMustBe(TypeKind.DECLARED);
            return addPredicate(m -> {
                TypeMirror sup = findSupertypeOrInterfaceOfType(m, supertypeOrInterface);
                if (sup == null) {
                    return fail("No supertype or interface type matching " + supertypeOrInterface + " on " + m);
                }
                if (sup.getKind() != TypeKind.DECLARED) {
                    return fail("Not a declared type: " + sup);
                }
                DeclaredType t = (DeclaredType) sup;
                List<? extends TypeMirror> args = t.getTypeArguments();
                if (args == null || args.size() < typeParameter) {
                    return fail("No type argument for index " + typeParameter
                            + " on " + t);
                }
                return tmtb.predicate().test(args.get(typeParameter));
            });
        });
    }

    public TypeMirrorTestBuilder<TypeMirrorTestBuilder<T>> testSupertypeOrInterface(String supertypeOrInterface) {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            typeKindMustBe(TypeKind.DECLARED);
            return addPredicate(m -> {
                TypeMirror sup = findSupertypeOrInterfaceOfType(m, supertypeOrInterface);
                if (sup == null) {
                    return fail("No supertype or interface type matching "
                            + supertypeOrInterface + " on " + m);
                }
                if (sup.getKind() != TypeKind.DECLARED) {
                    return fail("Not a declared type: " + sup);
                }
                DeclaredType t = (DeclaredType) sup;
                return tmtb.predicate().test(t);
            });
        });
    }

    private TypeMirror findSupertypeOrInterfaceOfType(TypeMirror on, String type) {
        if (type.equals(on.toString()) || type.equals(utils.erasureOf(on))) {
            return on;
        }
        List<? extends TypeMirror> sups = utils.processingEnv().getTypeUtils().directSupertypes(on);
        for (TypeMirror tm : sups) {
            if (type.equals(tm.toString()) || type.equals(utils.erasureOf(tm))) {
                return tm;
            }
        }
        return null;
    }
}

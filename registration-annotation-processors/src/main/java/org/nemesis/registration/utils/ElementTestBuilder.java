package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class ElementTestBuilder<E extends Element, R, B extends ElementTestBuilder<E, R, B>>
        extends AbstractPredicateBuilder<E, B, R> {

    ElementTestBuilder(AnnotationUtils utils, Function<B, R> builder) {
        super(utils, builder);
    }

    public TypeMirrorTestBuilder<B> testElementAsType() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(e -> {
                return tmtb.predicate().test(e.asType());
            });
        });
    }

    static <E extends Element, B extends ElementTestBuilder<E, Predicate<? super E>, B>> ElementTestBuilder<E, Predicate<? super E>, B> create(AnnotationUtils utils) {
        return new ElementTestBuilder<>(utils, etb -> {
            return etb.predicate();
        });
    }

    private static <E extends Element, B extends ElementTestBuilder<E, Predicate<? super E>, B>> Function<B, Predicate<? super E>> defaultBuilder() {
        return (tb) -> {
            return tb.predicate();
        };
    }

    public TypeElementTestBuilder<B, ?> testContainingClass() {
        return new TypeElementTestBuilder<>(utils, tetb -> {
            return addPredicate(AnnotationUtils::enclosingType, (et, tp) -> {
                return tetb.predicate().test(tp);
            });
        });
    }

    public TypeMirrorTestBuilder<B> testContainingType() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(AnnotationUtils::enclosingTypeAsTypeMirror, (et, tp) -> {
                if (tp == null) {
                    return true;
                }
                return tmtb.predicate().test(tp);
            });
        });
    }

    public TypeElementTestBuilder<B, ?> testOutermostClass() {
        return new TypeElementTestBuilder<>(utils, tetb -> {
            return addPredicate(AnnotationUtils::topLevelType, (et, tp) -> {
                return tetb.predicate().test(tp);
            });
        });
    }

    public TypeMirrorTestBuilder<B> testOutermostType() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(AnnotationUtils::topLevelTypeAsTypeMirror, (et, tp) -> {
                if (tp == null) {
                    return true;
                }
                return tmtb.predicate().test(tp);
            });
        });
    }

    Predicate<? super E> getPredicate() {
        return predicate();
    }

    public B mustBeFullyReifiedType() {
        return addPredicate((E e) -> {
            return maybeFail(e.asType().getKind() == TypeKind.DECLARED,
                    "Not a fully reified type: " + e.asType());
        });
    }

    public B typeParameterExtends(int ix, String name) {
        return addPredicate((e) -> {
            TypeMirror expected = utils.getTypeParameter(ix, e, this::fail);
            TypeElement el = utils.processingEnv().getElementUtils().getTypeElement(name);
            if (el == null) {
                fail("Could not load " + name + " from the classpath");
                return false;
            }
            TypeMirror real = e.asType();
            boolean result = utils.processingEnv().getTypeUtils().isSameType(real, expected) || utils.processingEnv().getTypeUtils().isAssignable(expected, real);
            return maybeFail(result, real + " is not assignable as " + expected);
        });
    }

    public B typeParameterExtendsOneOf(int ix, String name, String... more) {
        return addPredicate((e) -> {
            TypeMirror expected = utils.getTypeParameter(ix, e);
            List<String> all = new ArrayList<>();
            all.add(name);
            all.addAll(Arrays.asList(more));
            TypeMirror real = e.asType();
            boolean result = false;
            for (String a : all) {
                TypeElement el = utils.processingEnv().getElementUtils().getTypeElement(name);
                if (el == null) {
                    fail("Could not load " + name + " from the classpath");
                    return false;
                }
                result |= utils.processingEnv().getTypeUtils().isSameType(real, expected) || utils.processingEnv().getTypeUtils().isAssignable(expected, real);
                if (result) {
                    break;
                }
            }
            return maybeFail(result, real + " is not assignable as " + expected);
        });
    }

    public B typeParameterMatches(int ix, BiPredicate<TypeMirror, AnnotationUtils> tester) {
        return addPredicate((e) -> {
            TypeMirror tm = utils.getTypeParameter(ix, e);
            if (tm == null) {
                fail("No type parameter " + ix);
                return false;
            }
            return tester.test(tm, utils);
        });
    }

    public B isKind(ElementKind kind) {
        return addPredicate((e) -> {
            if (kind != e.getKind()) {
                fail("Element type must be " + kind + " but is " + e.getKind());
                return false;
            }
            return true;
        });
    }

    public B hasModifier(Modifier m) {
        return addPredicate((e) -> {
            return maybeFail(e.getModifiers() != null && e.getModifiers().contains(m),
                    "Must be " + m);
        });
    }

    public B hasModifiers(Modifier a, Modifier... more) {
        B result = hasModifier(a);
        for (Modifier m : more) {
            hasModifier(m);
        }
        return result;
    }

    public B doesNotHaveModifier(Modifier m) {
        return addPredicate((e) -> {
            return maybeFail(e.getModifiers() == null || !e.getModifiers().contains(m),
                    "Modifier " + m + " must not be used here");
        });
    }

    public B doesNotHaveModifiers(Modifier a, Modifier... more) {
        doesNotHaveModifier(a);
        for (Modifier m : more) {
            doesNotHaveModifier(m);
        }
        return (B) this;
    }

    public B isSubTypeOf(String typeName, String... moreTypeNames) {
        return addPredicate((e) -> {
            if (e == null) {
                return true;
            }
            if (moreTypeNames.length == 0) {
                AnnotationUtils.TypeComparisonResult res = utils.isSubtypeOf(e, typeName);
                if (!res.isSubtype()) {
                    switch (res) {
                        case FALSE:
                            fail("Not a subtype of " + typeName + ": " + e.asType());
                            break;
                        case TYPE_NAME_NOT_RESOLVABLE:
                            fail("Could not resolve on classpath: " + typeName);
                            break;
                    }
                }
                return res.isSubtype();
            } else {
                List<String> all = new ArrayList<>(Arrays.asList(typeName));
                all.addAll(Arrays.asList(moreTypeNames));
                for (String test : all) {
                    AnnotationUtils.TypeComparisonResult res = utils.isSubtypeOf(e, test);
                    if (res.isSubtype()) {
                        return true;
                    }
                }
                fail("Not a subtype of any of " + AnnotationUtils.join(',', all.toArray(new String[0])) + ": " + e.asType());
                return false;
            }
        });
    }
}

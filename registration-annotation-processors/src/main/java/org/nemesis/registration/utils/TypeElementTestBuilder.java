package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.PUBLIC;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class TypeElementTestBuilder<R, B extends TypeElementTestBuilder<R, B>> extends ElementTestBuilder<TypeElement, R, B> {

    public TypeElementTestBuilder(AnnotationUtils utils, Function<B, R> builder) {
        super(utils, builder);
    }

    public TypeElementTestBuilder<R, B> mustHavePublicNoArgConstructor() {
        return addPredicate(te -> {
            boolean found = false;
            for (Element el : te.getEnclosedElements()) {
                if (el.getKind() == ElementKind.CONSTRUCTOR) {
                    ExecutableElement ex = (ExecutableElement) el;
                    found = ex.getParameters().isEmpty()
                            && el.getModifiers().contains(PUBLIC);
                    if (found) {
                        break;
                    }
                }
            }
            return maybeFail(found, te.getQualifiedName() + " does not have a public no-argument constructor");
        });
    }

    public MethodTestBuilder<TypeElementTestBuilder<R, B>, ?> testMethod(String name, String... argTypes) {
        return new MethodTestBuilder<>(utils, mtb -> {
            return addPredicate(te -> {
                ExecutableElement toTest = null;
                outer:
                for (Element el : te.getEnclosedElements()) {
                    if (el.getKind() == ElementKind.METHOD && el instanceof ExecutableElement) {
                        if (name.equals(el.getSimpleName().toString())) {
                            ExecutableElement ex = (ExecutableElement) el;
                            List<String> realArgTypes = new ArrayList<>();
                            List<? extends VariableElement> params = ex.getParameters();
                            if (params.size() == argTypes.length) {
                                if (argTypes.length == 0) {
                                    toTest = ex;
                                    break outer;
                                }
                                for (int i = 0; i < argTypes.length; i++) {
                                    VariableElement param = params.get(i);
                                    String argType = argTypes[i];
                                    TypeMirror actualType = param.asType();
                                    if (!argType.equals(actualType.toString()) && !argType.equals(utils.erasureOf(actualType).toString())) {
                                        continue outer;
                                    }
                                }
                                toTest = ex;
                            }
                        }
                    }
                }
                ExecutableElement testIt = toTest;
                return maybeFail(toTest != null, "Could not find a method " + name
                        + "(" + AnnotationUtils.join(',', argTypes) + " on " + te.asType(),
                        () -> {
                            return mtb.predicate().test(testIt);
                        });
            });
        });
    }

    public MethodTestBuilder<TypeElementTestBuilder<R, B>, ?> testMethod(String name, List<? extends TypeMirror> argTypes) {
        return new MethodTestBuilder<>(utils, mtb -> {
            return addPredicate(te -> {
                ExecutableElement toTest = null;
                outer:
                for (Element el : te.getEnclosedElements()) {
                    if (el.getKind() == ElementKind.METHOD && el instanceof ExecutableElement) {
                        if (name.equals(el.getSimpleName().toString())) {
                            ExecutableElement ex = (ExecutableElement) el;
                            List<String> realArgTypes = new ArrayList<>();
                            List<? extends VariableElement> params = ex.getParameters();
                            if (params.size() == argTypes.size()) {
                                if (argTypes.isEmpty()) {
                                    toTest = ex;
                                    break outer;
                                }
                                for (int i = 0; i < argTypes.size(); i++) {
                                    VariableElement param = params.get(i);
                                    TypeMirror argType = argTypes.get(i);
                                    TypeMirror actualType = param.asType();
                                    if (!argType.equals(actualType.toString()) && !argType.equals(utils.erasureOf(actualType).toString())) {
                                        continue outer;
                                    }
                                }
                                toTest = ex;
                            }
                        }
                    }
                }
                ExecutableElement testIt = toTest;
                return maybeFail(toTest != null, "Could not find a method " + name
                        + "(" + AnnotationUtils.join(',', argTypes) + " on " + te.asType(),
                        () -> {
                            return mtb.predicate().test(testIt);
                        });
            });
        });
    }

    public B implementsInterface(String iface) {
        return addPredicate(e -> {
            boolean result = _implementsInterface(iface, e);
            if (!result) {
                fail("Type does not implement " + iface + ": " + e.getQualifiedName());
            }
            return result;
        });
    }

    public B nestingKindMustBe(NestingKind kind) {
        return addPredicate(e -> {
            boolean result = e.getNestingKind() == kind;
            if (!result) {
                fail("Nesting kind for type must be " + kind);
            }
            return result;
        });
    }

    public B nestingKindMayNotBe(NestingKind kind) {
        return addPredicate(e -> {
            boolean result = e.getNestingKind() != kind;
            if (!result) {
                fail("Nesting kind for type may not be " + kind);
            }
            return result;
        });
    }

    public B testImplementedInterface() {
        return addPredicate(e -> {
            e.getInterfaces();
            return false;
        });
    }

//    public B implementsInterfaceWithTypeParameterOfType(String iface, TypeMirror paramType, int paramIndex) {
//        return addPredicate(e -> {
//            boolean result = _implementsInterface(iface, e);
//            if (result) {
//                TypeMirror arg = utils.findTypeArgumentOnInterfacesImplementedBy(e, iface, paramIndex);
//            }
//            return result;
//        });
//    }
    private boolean _implementsInterface(String iface, TypeElement te) {
        if (!"java.lang.Object".equals(te.asType())) {
            for (TypeMirror i : te.getInterfaces()) {
                if (utils.erasureOf(i).toString().equals(iface)) {
                    return true;
                }
            }
        }
        return false;
    }
}

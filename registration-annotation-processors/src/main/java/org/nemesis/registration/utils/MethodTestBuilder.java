package org.nemesis.registration.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class MethodTestBuilder<R, B extends MethodTestBuilder<R, B>> extends ElementTestBuilder<ExecutableElement, R, B> {

    public MethodTestBuilder(AnnotationUtils utils, Function<B, R> builder) {
        super(utils, builder);
    }

    static <R, B extends MethodTestBuilder<R, B>> MethodTestBuilder<R, B> newBuilder(AnnotationUtils utils, Function<B, R> builder) {
        return new MethodTestBuilder<>(utils, builder);
    }

    public TypeMirrorTestBuilder<MethodTestBuilder<R, B>> testThrownTypes() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(m -> {
                boolean result = true;
                for (TypeMirror thrown : m.getThrownTypes()) {
                    result &= tmtb.predicate().test(thrown);
                }
                return result;
            });
        });
    }

    public TypeMirrorTestBuilder<B> testTypeParameter(int param) {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(m -> {
                List<? extends TypeParameterElement> params = m.getTypeParameters();
                if (params == null || params.isEmpty()) {
                    fail("No type parameters on " + m + " but wanted to test parameter " + param);
                    return false;
                }
                TypeParameterElement el = params.get(param);
                return tmtb.predicate().test(el.asType());
            });
        });
    }

    public TypeMirrorTestBuilder<B> returnType() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(m -> {
                return tmtb.predicate().test(m.getReturnType());
            });
        });
    }

    public TypeMirrorTestBuilder<B> testArgumentType(int argument) {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(m -> {
                if (m.getParameters().size() < argument) {
                    fail("Method " + m + " does not have an argument at position " + argument);
                    return false;
                }
                return tmtb.predicate().test(m.getParameters().get(argument).asType());
            });
        });
    }

    public TypeMirrorTestBuilder<B> receiverType() {
        return new TypeMirrorTestBuilder<>(utils, tmtb -> {
            return addPredicate(m -> {
                return tmtb.predicate().test(m.getReceiverType());
            });
        });
    }

    public B returns(String type, String... moreTypes) {
        Set<String> all = new HashSet<>(Arrays.asList(moreTypes));
        all.add(type);
        System.out.println("check return type " + all);
        Predicate<ExecutableElement> pred = (el) -> {
            for (String oneType : all) {
                AnnotationUtils.TypeComparisonResult res = utils.isSubtypeOf(el, oneType);
                if (res.isSubtype()) {
                    return true;
                }
            }
            fail("Return type is not one of " + AnnotationUtils.join(',', all));
            return false;
        };
        return addPredicate(pred);
    }

    public B mustNotTakeArguments() {
        return addPredicate((e) -> {
            boolean result = e.getParameters().isEmpty();
            if (!result) {
                fail("Method must not take any arguments");
            }
            return result;
        });
    }

    static <B1 extends MethodTestBuilder<Predicate<? super ExecutableElement>, B1>> MethodTestBuilder<Predicate<? super ExecutableElement>, B1> createMethod(AnnotationUtils utils) {
        return new MethodTestBuilder<Predicate<? super ExecutableElement>, B1>(utils, defaultMethodBuilder());
    }

    private static <B extends MethodTestBuilder<Predicate<? super ExecutableElement>, B>> Function<B, Predicate<? super ExecutableElement>> defaultMethodBuilder() {
        return (tb) -> {
            return tb.getPredicate();
        };
    }

    public B argumentTypesMustBe(String... types) {
        addPredicate((e) -> {
            int count = e.getParameters().size();
            if (count != types.length) {
                fail("Wrong number of arguments - " + count + " - expecting " + types.length + " of types " + Arrays.toString(types));
                return false;
            }
            return true;
        });
        for (int i = 0; i < types.length; i++) {
            final int index = i;
            addPredicate((e) -> {
                List<? extends VariableElement> params = e.getParameters();
                VariableElement toTest = params.get(index);
                AnnotationUtils.TypeComparisonResult isSubtype = utils.isSubtypeOf(toTest, types[index]);
                boolean result = isSubtype.isSubtype();
                if (!result) {
                    switch (isSubtype) {
                        case FALSE:
                            fail("Type of argument " + index + " should be " + types[index] + " not " + toTest.asType());
                            break;
                        case TYPE_NAME_NOT_RESOLVABLE:
                            fail("Type of argument " + index + " should be " + types[index] + " but that type is not resolvable on " + "the compilation class path.");
                    }
                }
                return result;
            });
        }
        return (B) this;
    }

    private static <R, M2 extends MethodTestBuilder<R,M2>> M2  createNew(AnnotationUtils utils, Function<M2,R> func) {
        return (M2) new MethodTestBuilder<>(utils, func);
    }

//    private <M1 extends MethodTestBuilder<AbstractConcludingBranchBuilder<ExecutableElement, B, R, ExecutableElement, M2>, M2>, M2 extends MethodTestBuilder<B, M2>> void foo() {
//
//    }

    /*
    private <R, M2 extends MethodTestBuilder<R, M2>> void foo() {

    }

    protected <B1 extends AbstractPredicateBuilder<ExecutableElement, B1, AbstractConcludingBranchBuilder<ExecutableElement, B, R, ExecutableElement, B2>>, B2 extends AbstractPredicateBuilder<ExecutableElement, B2, B>>
                    AbstractBranchBuilder<ExecutableElement, B, R, ExecutableElement, B1, B2>
            doBranch(Predicate<? super ExecutableElement> test,
                    Function<Function<? super B1, AbstractConcludingBranchBuilder<ExecutableElement, B, R, ExecutableElement, B2>>, ? super B1> createFirst,
                    Function<Function<? super B2, ? extends B>, ? extends B2> createSecond,
                    Function<Predicate<? super ExecutableElement>, ? extends B> onDone) {
        return branch(test, createFirst, t -> t, createSecond, onDone);
    }


    public <M2 extends MethodTestBuilder<B, M2>, M1 extends AbstractPredicateBuilder<ExecutableElement, M1, AbstractConcludingBranchBuilder<ExecutableElement, B, R, ExecutableElement, M2>>> AbstractBranchBuilder<ExecutableElement, B,R, ExecutableElement, ?,?> ifReturnType(String type) {
        Predicate<ExecutableElement> test = m -> {
            return TypeMirrorComparison.SAME_TYPE.predicate(type, utils, this::maybeFail).test(m.getReturnType());
        };
        Function<Function<M2, B>, M2> createSecond = new Function<Function<M2, B>, M2>() {
            @Override
            public M2 apply(Function<M2, B> func) {
                return (M2) new MethodTestBuilder<>(utils, func);
            }
        };
        Function<Predicate<ExecutableElement>, B> onDone = this::addPredicate;
        
        return doBranch(test, 
                (Function<? super M1, AbstractConcludingBranchBuilder<ExecutableElement, B, R, ExecutableElement, M2>> ab) -> {
            return createNew(utils, m2 -> {
                return null;
            });
        }, createSecond, onDone);

//        Function createFirst = (Function<Function<B1, AbstractConcludingBranchBuilder<T, B, R, T, B2>>, B1> func) -> {
//            return createNew(utils, func);
//        };

        

    }
*/

}

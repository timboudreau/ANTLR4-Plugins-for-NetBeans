package org.nemesis.registration.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import static org.nemesis.registration.utils.AnnotationUtils.simpleName;

/**
 *
 * @author Tim Boudreau
 */
public class MultiAnnotationTestBuilder<R> extends AbstractPredicateBuilder<AnnotationMirror, MultiAnnotationTestBuilder<R>, R> {

    private final Map<String, Map<ElementKind, Predicate<Element>>> predicateForKindForAnnotationType = new HashMap<>();

    MultiAnnotationTestBuilder(AnnotationUtils utils, Function<MultiAnnotationTestBuilder<R>, R> converter) {
        super(utils, converter);
    }

    static MultiAnnotationTestBuilder<Predicate<? super AnnotationMirror>> createDefault(AnnotationUtils utils) {
        return new MultiAnnotationTestBuilder<>(utils, matb -> {
            return matb.predicate();
        });
    }

    public MultiAnnotationTestBuilder<R> whereAnnotationType(String annoType, Consumer<AnnotationMirrorTestBuilderWithAssociatedElementTests<?>> c) {
        // Use lazy predicate to avoid doing much simply because an annotation processor
        // was instantiated - it might never be used
        addPredicate("if-annotation-type:" + simpleName(annoType), PredicateUtils.lazyPredicate(() -> {
            boolean[] built = new boolean[1];
            AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl amtb = new AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl(annoType, utils, b -> {
                addPredicate("if-annotation-type: " + annoType, new TypeTestPredicate(annoType, b.predicate()));
                built[0] = true;
                return null;
            });
            c.accept(amtb);
            if (!built[0]) {
                amtb.build();
            }
            return amtb.predicate();
        }));
        return this;
    }

    public AnnotationMirrorTestBuilder<MultiAnnotationTestBuilder<R>> whereAnnotationType(String annoType) {
        return new AnnotationMirrorTestBuilder<>(utils, amtb -> {
            addPredicate("if-annotation-type: " + annoType, new TypeTestPredicate(annoType, amtb.predicate()));
            return this;
        });
    }

    static final class TypeTestPredicate extends AbstractNamed implements NamedPredicate<AnnotationMirror> {

        private final String type;
        private final Predicate<? super AnnotationMirror> other;

        public TypeTestPredicate(String type, Predicate<? super AnnotationMirror> other) {
            this.type = type;
            this.other = other;
        }

        @Override
        public boolean test(AnnotationMirror t) {
            if (type.equals(t.getAnnotationType().toString())) {
                return other.test(t);
            }
            return true;
        }

        @Override
        public String name() {
            return simpleName(type);
        }
    }

    public static abstract class AnnotationMirrorTestBuilderWithAssociatedElementTests<T> extends AnnotationMirrorTestBuilder<T> {

        AnnotationMirrorTestBuilderWithAssociatedElementTests(AnnotationUtils utils, Function<AnnotationMirrorTestBuilder<T>, T> converter) {
            super(utils, converter);
        }

        public abstract AnnotationMirrorTestBuilderWithAssociatedElementTests<T> whereMethodIsAnnotated(Consumer<MethodTestBuilder<?, ? extends MethodTestBuilder<?, ?>>> c);

        public abstract AnnotationMirrorTestBuilderWithAssociatedElementTests<T> whereFieldIsAnnotated(Consumer<ElementTestBuilder<VariableElement, ?, ? extends ElementTestBuilder<VariableElement, ?, ?>>> c);

        public abstract AnnotationMirrorTestBuilderWithAssociatedElementTests<T> whereClassIsAnnotated(Consumer<TypeElementTestBuilder<?, ? extends TypeElementTestBuilder<?, ?>>> c);
    }

    private class AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl extends AnnotationMirrorTestBuilderWithAssociatedElementTests<MultiAnnotationTestBuilder> {

        private final String annoType;

        AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl(String annoType, AnnotationUtils utils, Function<AnnotationMirrorTestBuilder<MultiAnnotationTestBuilder>, MultiAnnotationTestBuilder> converter) {
            super(utils, converter);
            this.annoType = annoType;
        }

        @SuppressWarnings("unchecked")
        public AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl add(Predicate<? extends Element> p, ElementKind... k) {
            for (ElementKind kind : k) {
                Map<ElementKind, Predicate<Element>> predicateForKind = predicateForKindForAnnotationType.get(annoType);
                if (predicateForKind == null) {
                    predicateForKind = new HashMap<>();
                    predicateForKindForAnnotationType.put(annoType, predicateForKind);
                }
                Predicate<Element> pred = predicateForKind.get(kind);
                if (pred != null) {
                    pred = pred.and((Predicate<Element>) p);
                }
                predicateForKind.put(kind, pred);
            }
            return this;
        }

        public AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl whereMethodIsAnnotated(Consumer<MethodTestBuilder<?, ? extends MethodTestBuilder<?, ?>>> c) {
            boolean[] built = new boolean[1];
            assert annoType != null;
            MethodTestBuilder<Void, ?> mtb = new MethodTestBuilder<>(utils, b -> {
                add(b._predicate(), ElementKind.METHOD);
                built[0] = true;
                return null;
            });
            c.accept(mtb);
            if (!built[0]) {
                mtb.build();
            }
            return this;
        }

        public AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl whereFieldIsAnnotated(Consumer<ElementTestBuilder<VariableElement, ?, ? extends ElementTestBuilder<VariableElement, ?, ?>>> c) {
            boolean[] built = new boolean[1];
            assert annoType != null;
            ElementTestBuilder<VariableElement, Void, ?> mtb = new ElementTestBuilder<>(utils, b -> {
                add(b._predicate(), ElementKind.FIELD);
                built[0] = true;
                return null;
            });
            c.accept(mtb);
            if (!built[0]) {
                mtb.build();
            }
            return this;
        }

        public AnnotationMirrorTestBuilderWithAssociatedElementTestsImpl whereClassIsAnnotated(Consumer<TypeElementTestBuilder<?, ? extends TypeElementTestBuilder<?, ?>>> c) {
            boolean[] built = new boolean[1];
            assert annoType != null;
            TypeElementTestBuilder<Void, ?> mtb = new TypeElementTestBuilder<>(utils, b -> {
                add(b._predicate(), ElementKind.CLASS);
                add(b._predicate(), ElementKind.INTERFACE);
                built[0] = true;
                return null;
            });
            c.accept(mtb);
            if (!built[0]) {
                mtb.build();
            }
            return this;
        }
    }

    public BiPredicate<? super AnnotationMirror, ? super Element> biPredicate() {
        return new Bi(this, predicateForKindForAnnotationType);
    }

    private static class Bi extends AbstractNamed implements BiPredicate<AnnotationMirror, Element>, Named {

        NamedPredicate<AnnotationMirror> pred;
        private final Map<String, Map<ElementKind, Predicate<Element>>> predicateForKindForAnnotationType;

        Bi(MultiAnnotationTestBuilder mtb, Map<String, Map<ElementKind, Predicate<Element>>> predicateForKindForAnnotationType) {
            this.predicateForKindForAnnotationType = Collections.unmodifiableMap(new HashMap<>(predicateForKindForAnnotationType));
            this.pred = mtb._predicate();
        }

        public boolean test(AnnotationMirror mirror, Element on) {
            boolean result = mainPred().test(mirror);
            String typeName = mirror.getAnnotationType().toString();
            Predicate<Element> pred = predicateForAnnotationType(typeName);
            return pred.test(on) && result;
        }

        private Predicate<AnnotationMirror> mainPred() {
            return pred;
        }

        @Override
        public String name() {
            StringBuilder sb = new StringBuilder(pred.toString());
            return sb.append(predicateForKindForAnnotationType).toString();
        }

        private Predicate<Element> predicateForAnnotationType(String type) {
            return new SwitchingPredicate(type);
        }

        class SwitchingPredicate implements NamedPredicate<Element> {

            private final String annotationType;

            public SwitchingPredicate(String annotationType) {
                this.annotationType = annotationType;
            }

            @Override
            public String name() {
                Map<ElementKind, Predicate<Element>> m = predicateForKindForAnnotationType.get(annotationType);
                if (m == null) {
                    return "{}";
                }
                StringBuilder sb = new StringBuilder().append('{');
                for (Iterator<Map.Entry<ElementKind, Predicate<Element>>> it = m.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<ElementKind, Predicate<Element>> e = it.next();
                    sb.append(e.getKey()).append(": ").append(e.getValue());
                    if (it.hasNext()) {
                        sb.append(",");
                    }
                }
                return sb.append('}').toString();
            }

            @Override
            public boolean test(Element t) {
                Map<ElementKind, Predicate<Element>> m = predicateForKindForAnnotationType.get(annotationType);
                if (m == null) {
                    return true;
                }
                Predicate<Element> elPred = m.get(t.getKind());
                if (elPred == null) {
                    return true;
                }
                return elPred.test(t);
            }
        }
    }
}

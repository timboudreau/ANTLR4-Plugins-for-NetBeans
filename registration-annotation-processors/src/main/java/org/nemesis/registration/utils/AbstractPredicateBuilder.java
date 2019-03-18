package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Base class for builders which test annotation members or types. Consists of
 * constructing a single Predicate which runs all the tests created in builders
 * and added. Such predicates are expected to add failure messages for javac to
 * output, and there is special handling for tracking the element or annotation
 * mirror being operated on through that. If calling such a predicate manually,
 * call the static method <code>enter</code> on this class to set up the initial
 * context. Each predicate added will update the type or annotation mirror it is
 * processing if needed (to handle annotation members which are other
 * annotations and ensure error messages get attached to the right place in the
 * sources).
 *
 * @author Tim Boudreau
 */
public class AbstractPredicateBuilder<T, B extends AbstractPredicateBuilder<T, B, R>, R> {

    private Predicate<T> predicate;
    protected final AnnotationUtils utils;
    private final Function<B, R> converter;

    AbstractPredicateBuilder(AnnotationUtils utils, Function<B, R> converter) {
        this.utils = utils;
        this.converter = converter;
    }

    public final R build() {
        return converter.apply((B) this);
    }

    final Predicate<? super T> predicate() {
        if (predicate == null) {
            return ignored -> true;
        }
        return predicate;
    }

    public final B addPredicate(BiPredicate<? super T, Consumer<? super String>> predWithErrorMessageDest) {
        return addPredicate(t -> {
            return predWithErrorMessageDest.test(t, this::fail);
        });
    }

    public final B addPredicate(Predicate<? super T> pred) {
        if (predicate == null) {
            predicate = wrapPredicate(pred);
        } else {
            predicate = predicate.and(wrapPredicate(pred));
        }
        return (B) this;
    }

    public final <V> B addPredicate(Function<T, V> converter, Predicate<V> predicate) {
        return addPredicate((e) -> {
            V v = converter.apply(e);
            return predicate.test(v);
        });
    }

    public final <V> B addPredicate(Function<T, V> converter, BiPredicate<T, V> predicate) {
        return addPredicate((e) -> {
            V v = converter.apply(e);
            return predicate.test(e, v);
        });
    }

    private Predicate<T> wrapPredicate(Predicate<? super T> orig) {
        return t -> {
            if (t instanceof Element) {
                enter((Element) t, () -> {
                    return orig.test(t);
                });
            } else if (t instanceof AnnotationMirror) {
                enter((AnnotationMirror) t, () -> {
                    return orig.test(t);
                });
            }
            boolean result = orig.test(t);
            return result;
        };
    }

    protected final boolean maybeFail(boolean val, String msg) {
        if (!val) {
            return fail(msg);
        }
        return val;
    }

    protected final boolean maybeFail(boolean val, String msg, BooleanSupplier nextTest) {
        if (!val) {
            return fail(msg);
        }
        return nextTest.getAsBoolean();
    }

    private final ThreadLocal<DeferredFailures> deferredFailures = new ThreadLocal<>();

    public B branch(Predicate<? super T> test, Predicate<? super T> ifTrue, Predicate<? super T> ifFalse) {
        return addPredicate(new OneOfPredicate(test, ifTrue, ifFalse));
    }

    public B or(Predicate<? super T> a, Predicate<? super T> b) {
        return addPredicate(new OrPredicate(a, b));
    }

    protected <N, B1 extends AbstractPredicateBuilder<N, B1, AbstractConcludingBranchBuilder<T, B, R, N, B2>>, B2 extends AbstractPredicateBuilder<N, B2, B>> AbstractBranchBuilder<T, B, R, N, B1, B2>
            branch(Predicate<? super T> test,
                    Function<Function<? super B1, ? extends AbstractConcludingBranchBuilder<T, B, R, N, B2>>, ? extends B1> createFirst,
                    Function<? super T, ? extends N> convert,
                    Function<Function<? super B2, ? extends B>, ? extends B2> createSecond,
                    Function<Predicate<? super T>, ? extends B> onDone) {

        return new AbstractBranchBuilder<>(test, createFirst, convert, createSecond, onDone);
    }

    protected <B1 extends AbstractPredicateBuilder<T, B1, AbstractConcludingBranchBuilder<T, B, R, T, B2>>, B2 extends AbstractPredicateBuilder<T, B2, B>>
                    AbstractBranchBuilder<T, B, R, T, B1, B2>
            branch(Predicate<? super T> test,
                    Function<Function<? super B1, ? extends AbstractConcludingBranchBuilder<T, B, R, T, B2>>, ? extends B1> createFirst,
                    Function<Function<? super B2, ? extends B>, ? extends B2> createSecond,
                    Function<Predicate<? super T>, ? extends B> onDone) {

        return new AbstractBranchBuilder<>(test, createFirst, t -> t, createSecond, onDone);
    }

    protected <N, B1 extends AbstractPredicateBuilder<N, B1, B>, O, B2 extends AbstractPredicateBuilder<O, B2, B>> HeteroBranchBuilder<N, B1, O, B2>
            branch(Predicate<? super T> test, Supplier<B1> supp, Supplier<B2> supp2, Function<T, N> convert, Function<T, O> convert2) {
        return new HeteroBranchBuilder(supp, test, supp2, convert, convert2);
    }

    public static class AbstractBranchBuilder<T,
            B extends AbstractPredicateBuilder<T, B, R>, R, N,
            B1 extends AbstractPredicateBuilder<N, B1, AbstractConcludingBranchBuilder<T, B, R, N, B2>>,
            B2 extends AbstractPredicateBuilder<N, B2, B>> {

        private final Predicate<? super T> test;

        private final Function<Function<? super B1, ? extends AbstractConcludingBranchBuilder<T, B, R, N, B2>>, ? extends B1> createFirst;
        private final Function<? super T, ? extends N> convert;
        private final Function<Function<? super B2, ? extends B>, ? extends B2> createSecond;
        private final Function<Predicate<? super T>, ? extends B> onDone;

        AbstractBranchBuilder(Predicate<? super T> test,
                Function<Function<? super B1, ? extends AbstractConcludingBranchBuilder<T, B, R, N, B2>>, ? extends B1> createFirst,
                Function<? super T, ? extends N> convert,
                Function<Function<? super B2, ? extends B>, ? extends B2> createSecond, Function<Predicate<? super T>, ? extends B> onDone) {
            this.test = test;
            this.createFirst = createFirst;
            this.convert = convert;
            this.createSecond = createSecond;
            this.onDone = onDone;
        }

        public B1 ifTrue() {
            return createFirst.apply((B1 b1a) -> {
                return new AbstractConcludingBranchBuilder<T, B, R, N, B2>(b1a.predicate(), convert, onDone, test, createSecond);
            });
        }

        public AbstractConcludingBranchBuilder<T, B, R, N, B2> ifTrue(Consumer<B1> c) {
            AtomicReference<AbstractConcludingBranchBuilder<T, B, R, N, B2>> ref = new AtomicReference<>();
            B1 b1 = createFirst.apply((B1 b1a) -> {
                AbstractConcludingBranchBuilder<T, B, R, N, B2> result
                        = new AbstractConcludingBranchBuilder<T, B, R, N, B2>(b1a.predicate(), convert, onDone, test, createSecond);
                ref.set(result);
                return result;
            });
            c.accept(b1);
            return ref.get();
        }

    }

    public static class AbstractConcludingBranchBuilder<T, B extends AbstractPredicateBuilder<T, B, R>, R, N, B2 extends AbstractPredicateBuilder<N, B2, B>> {

        private final Predicate<? super N> first;
        private final Function<? super T, ? extends N> convert;
        private final Function<Predicate<? super T>, ? extends B> onDone;
        private final Predicate<? super T> test;
        private final Function<Function<? super B2, ? extends B>, ? extends B2> factory;

        AbstractConcludingBranchBuilder(Predicate<? super N> first, Function<? super T, ? extends N> convert,
                Function<Predicate<? super T>, ? extends B> onDone, Predicate<? super T> test,
                Function<Function<? super B2, ? extends B>, ? extends B2> factory) {
            this.first = first;
            this.convert = convert;
            this.onDone = onDone;
            this.test = test;
            this.factory = factory;

        }

        public B2 ifFalse() {
            return factory.apply(b2a -> {
                return onDone.apply(done(b2a));
            });
        }

        public B ifFalse(Consumer<? super B2> c) {
            AtomicReference<B> res = new AtomicReference<>();
            B2 b2 = factory.apply(b2a -> {
                B result = onDone.apply(done(b2a));
                res.set(result);
                return result;
            });
            c.accept(b2);
            return res.get();
        }

        private Predicate<T> done(B2 b1) {
            Predicate<? super N> second = b1.predicate();
            Predicate<T> firstConverted = t -> {
                return first.test(convert.apply(t));
            };
            Predicate<T> secondConverted = t -> {
                return second.test(convert.apply(t));
            };
            return (t) -> {
                if (test.test(t)) {
                    return firstConverted.test(t);
                } else {
                    return secondConverted.test(t);
                }
            };
        }
    }

    /*
    class BranchBuilder<N, B1 extends AbstractPredicateBuilder<N, B1, BranchBuilder<N, B1, B2>>,
            B2 extends AbstractPredicateBuilder<N, B2, B>>
            extends AbstractBranchBuilder<T, B, R, N, B1, B2, ConcludingBranchBuilder> {

        private  Function<AbstractConcludingBranchBuilder<T, B, R, N, B2, ?>, B1> builderSupplier;
        private final Predicate<? super T> test;
        private Predicate<? super T> ifTruePredicate;
        private final Function<T, N> convert;

        BranchBuilder(Supplier<B1> builderSupplier, Predicate<? super T> test, Function<T, N> convert) {
//            this.builderSupplier = builderSupplier;
            this.test = test;
            this.convert = convert;
        }

        @Override
        public AbstractConcludingBranchBuilder<T, B, R, N, B2, ?> ifTrue(Consumer<B1> c) {
            B1 builder = null;
            c.accept(builder);
            Predicate<? super N> pred = builder.predicate();
            ifTruePredicate = (T t) -> {
                N n = convert.apply(t);
                return pred.test(n);
            };
            return new ConcludingBranchBuilder();
        }

        class ConcludingBranchBuilder extends AbstractConcludingBranchBuilder<T, B, R, N, B2, ConcludingBranchBuilder> {

            public B ifFalse(Consumer<B2> c) {
                B1 builder = builderSupplier.get();
                c.accept(builder);
                Predicate<? super N> pred = builder.predicate();
                Predicate<? super T> ifFalsePredicate = (T t) -> {
                    N n = convert.apply(t);
                    return pred.test(n);
                };
                return AbstractPredicateBuilder.this.addPredicate(
                        new OneOfPredicate(test, ifTruePredicate, ifFalsePredicate));
            }
        }
    }
     */
    public class HeteroBranchBuilder<N, B1 extends AbstractPredicateBuilder<N, B1, B>, O, B2 extends AbstractPredicateBuilder<O, B2, B>> {

        private final Supplier<B1> builderSupplier;
        private final Predicate<? super T> test;
        private Predicate<? super T> ifTruePredicate;
        private final Supplier<B2> builderSupplier2;
        private final Function<T, N> convert;
        private final Function<T, O> convert2;

        HeteroBranchBuilder(Supplier<B1> builderSupplier, Predicate<? super T> test, Supplier<B2> builderSupplier2, Function<T, N> convert, Function<T, O> convert2) {
            this.builderSupplier = builderSupplier;
            this.test = test;
            this.builderSupplier2 = builderSupplier2;
            this.convert = convert;
            this.convert2 = convert2;
        }

        public HeteroConcludingBranchBuilder ifTrue(Consumer<B1> c) {
            B1 builder = builderSupplier.get();
            c.accept(builder);
            Predicate<? super N> pred = builder.predicate();
            ifTruePredicate = (T t) -> {
                N n = convert.apply(t);
                return pred.test(n);
            };
            return new HeteroConcludingBranchBuilder();
        }

        public class HeteroConcludingBranchBuilder {

            public B ifFalse(Consumer<B2> c) {
                B2 builder = builderSupplier2.get();
                c.accept(builder);
                Predicate<? super O> pred = builder.predicate();
                Predicate<? super T> ifFalsePredicate = (T t) -> {
                    O n = convert2.apply(t);
                    return pred.test(n);
                };
                return addPredicate(
                        new OneOfPredicate(test, ifTruePredicate, ifFalsePredicate));
            }
        }
    }

    class OneOfPredicate implements Predicate<T> {

        private final Predicate<? super T> test;
        private final Predicate<? super T> ifTrue;
        private final Predicate<? super T> ifFalse;

        public OneOfPredicate(Predicate<? super T> test, Predicate<? super T> ifTrue, Predicate<? super T> ifFalse) {
            this.test = test;
            this.ifTrue = ifTrue;
            this.ifFalse = ifFalse;
        }

        @Override
        public boolean test(T val) {
            if (test.test(val)) {
                return ifTrue.test(val);
            } else {
                return ifFalse.test(val);
            }
        }
    }

    class OrPredicate implements Predicate<T> {

        private final Predicate<? super T> a;
        private final Predicate<? super T> b;

        public OrPredicate(Predicate<? super T> a, Predicate<? super T> b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean test(T val) {
            DeferredFailures aFails = runWithDeferredFailures(val, a);
            if (!aFails.wasOk()) {
                DeferredFailures bFails = runWithDeferredFailures(val, b);
                if (bFails.wasOk()) {
                    return true;
                }
                aFails.announce(utils);
                bFails.announce(utils);
                return false;
            } else {
                return true;
            }
        }
    }

    private <T> DeferredFailures runWithDeferredFailures(T val, Predicate<? super T> pred) {
        DeferredFailures old = deferredFailures.get();
        DeferredFailures fails = new DeferredFailures();
        deferredFailures.set(fails);
        try {
            fails.run(val, pred);
        } finally {
            deferredFailures.set(old);
        }
        return fails;
    }

    static Set<Object> collectedFailures = new HashSet<>();

    protected final boolean fail(String msg) {
        Element el = elementContext.get();
        AnnotationMirror mir = annotationContext.get();
        DeferredFailures failures = deferredFailures.get();
        if (failures != null) {
            failures.add(msg, el, mir);
        }
        String key = el + "-" + msg + "-" + mir;
        if (collectedFailures.contains(key)) {
            return false;
        }
        collectedFailures.add(key);

        if (el != null && mir != null) {
            utils.fail(msg, el, mir);
        } else if (el != null) {
            utils.fail(msg, el);
        } else if (mir != null) {
            utils.fail(msg, null, mir);
        } else {
            utils.fail(msg);
        }
        return false;
    }

    protected final boolean warn(String msg) {
        Element el = elementContext.get();
        AnnotationMirror mir = annotationContext.get();
        if (el != null && mir != null) {
            utils.warn(msg, el, mir);
        } else if (el != null) {
            utils.warn(msg, el);
        } else if (mir != null) {
            utils.warn(msg, null, mir);
        } else {
            utils.warn(msg);
        }
        return false;
    }

    private static final class DeferredFailures {

        private final List<Entry> all = new ArrayList<>();
        private boolean predicateResult;

        void add(String msg, Element el, AnnotationMirror mir) {
            all.add(new Entry(msg, el, mir));
        }

        <T> boolean run(T val, Predicate<? super T> pred) {
            predicateResult = pred.test(val);
            return predicateResult;
        }

        boolean predicateResult() {
            return predicateResult;
        }

        boolean wasOk() {
            return all.isEmpty() && predicateResult;
        }

        void announce(AnnotationUtils utils) {
            for (Entry e : all) {
                e.invoke(utils);
            }
        }

        private static final class Entry {

            private final String msg;
            private final Element el;
            private final AnnotationMirror mir;

            public Entry(String msg, Element el, AnnotationMirror mirror) {
                this.msg = msg;
                this.el = el;
                this.mir = mirror;
            }

            public void invoke(AnnotationUtils utils) {
                if (el != null && mir != null) {
                    utils.fail(msg, el, mir);
                } else if (el != null) {
                    utils.fail(msg, el);
                } else if (mir != null) {
                    utils.fail(msg, null, mir);
                } else {
                    utils.fail(msg);
                }
            }
        }
    }

    private static final ThreadLocal<Element> elementContext = new ThreadLocal<>();
    private static final ThreadLocal<AnnotationMirror> annotationContext = new ThreadLocal<>();

    public static <X> X enter(Element el, Supplier<X> r) {
        return enter(el, null, r);
    }

    public static <X> X enter(AnnotationMirror anno, Supplier<X> r) {
        return enter(null, anno, r);
    }

    public static <X> X enter(Element el, AnnotationMirror anno, Supplier<X> supp) {
        Element oldElement = elementContext.get();
        AnnotationMirror oldAnno = annotationContext.get();
        try {
            if (el != null) {
                elementContext.set(el);
            }
            if (anno != null) {
                annotationContext.set(anno);
            }
            return supp.get();
        } finally {
            if (anno != null) {
                annotationContext.set(oldAnno);
            }
            if (el != null) {
                elementContext.set(oldElement);
            }
        }
    }
}

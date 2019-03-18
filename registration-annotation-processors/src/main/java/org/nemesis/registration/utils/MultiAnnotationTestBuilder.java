/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.registration.utils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;

/**
 *
 * @author Tim Boudreau
 */
public class MultiAnnotationTestBuilder<R> extends AbstractPredicateBuilder<AnnotationMirror, MultiAnnotationTestBuilder<R>, R> {

    MultiAnnotationTestBuilder(AnnotationUtils utils, Function<MultiAnnotationTestBuilder<R>, R> converter) {
        super(utils, converter);
    }

    static MultiAnnotationTestBuilder<Predicate<? super AnnotationMirror>> createDefault(AnnotationUtils utils) {
        return new MultiAnnotationTestBuilder<>(utils, matb -> {
            return matb.predicate();
        });
    }

    public MultiAnnotationTestBuilder<R> whereAnnotationType(String annoType, Consumer<AnnotationMirrorTestBuilder<?>> c) {
        boolean[] built = new boolean[1];
        AnnotationMirrorTestBuilder<Void> amtb = new AnnotationMirrorTestBuilder<>(utils, b -> {
            addPredicate(new TypeTestPredicate(annoType, b.predicate()));
            built[0] = true;
            return null;
        });
        c.accept(amtb);
        if (!built[0]) {
            amtb.build();
        }
        return this;
    }

    public AnnotationMirrorTestBuilder<MultiAnnotationTestBuilder<R>> whereAnnotationType(String annoType) {
        return new AnnotationMirrorTestBuilder<>(utils, amtb -> {
            addPredicate(new TypeTestPredicate(annoType, amtb.predicate()));
            return this;
        });
    }

    static final class TypeTestPredicate implements Predicate<AnnotationMirror> {

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

    }

}

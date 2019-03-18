package org.nemesis.registration.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;

/**
 *
 * @author Tim Boudreau
 */
public class AnnotationMirrorTestBuilder<T> extends AbstractPredicateBuilder<AnnotationMirror, AnnotationMirrorTestBuilder<T>, T> {

    AnnotationMirrorTestBuilder(AnnotationUtils utils, Function<AnnotationMirrorTestBuilder<T>, T> converter) {
        super(utils, converter);
    }

    public AnnotationMirrorTestBuilder<T> atLeastOneMemberMayBeSet(String... memberNames) {
        return addPredicate((am) -> {
            List<Object> all = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (String name : memberNames) {
                Object o = utils.annotationValue(am, name, Object.class);
                if (o != null) {
                    names.add(name);
                    all.add(o);
                }
            }
            return maybeFail(!all.isEmpty(), "At least one of "
                    + AnnotationUtils.join(',', memberNames)
                    + " MUST be used but found " + names);
        });
    }

    public AnnotationMirrorTestBuilder<T> onlyOneMemberMayBeSet(String... memberNames) {
        return addPredicate((am) -> {
            List<Object> all = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (String name : memberNames) {
                Object o = utils.annotationValue(am, name, Object.class);
                if (o != null) {
                    names.add(name);
                    all.add(o);
                }
            }
            return maybeFail(all.size() <= 1, "Only one of "
                    + AnnotationUtils.join(',', memberNames)
                    + " may be used, but found "
                    + AnnotationUtils.join(',',
                            names.toArray(new String[names.size()])));
        });
    }

    public AnnotationMirrorMemberTestBuilder<AnnotationMirrorTestBuilder<T>>
            testMember(String memberName) {
        return new AnnotationMirrorMemberTestBuilder<>((ammtb) -> {
            addPredicate(ammtb.predicate());
            return this;
        }, memberName, utils);
    }

    public AnnotationMirrorTestBuilder<T>
            testMember(String memberName, Consumer<AnnotationMirrorMemberTestBuilder<?>> c) {
        boolean[] built = new boolean[1];
        AnnotationMirrorMemberTestBuilder<Void> m = new AnnotationMirrorMemberTestBuilder<>((ammtb) -> {
            addPredicate(ammtb.predicate());
            built[0] = true;
            return null;
        }, memberName, utils);
        c.accept(m);
        if (!built[0]) {
            m.build();
        }
        return this;
    }

    public AnnotationMirrorMemberTestBuilder<AnnotationMirrorTestBuilder<T>>
            testMemberIfPresent(String memberName) {
        return new AnnotationMirrorMemberTestBuilder<>((ammtb) -> {
            addPredicate((outer) -> {
                List<AnnotationMirror> values = utils.annotationValues(outer,
                        memberName, AnnotationMirror.class);
                if (values.isEmpty()) {
                    return true;
                }
                return ammtb.predicate().test(outer);
            });
            return this;
        }, memberName, utils);
    }

    public AnnotationMirrorTestBuilder<T>
            testMemberIfPresent(String memberName, Consumer<AnnotationMirrorMemberTestBuilder<?>> c) {
        boolean[] built = new boolean[1];
        AnnotationMirrorMemberTestBuilder<Void> m = new AnnotationMirrorMemberTestBuilder<>((ammtb) -> {
            addPredicate((outer) -> {
                List<AnnotationMirror> values = utils.annotationValues(outer,
                        memberName, AnnotationMirror.class);
                if (values.isEmpty()) {
                    return true;
                }
                return ammtb.predicate().test(outer);
            });
            built[0] = true;
            return null;
        }, memberName, utils);
        c.accept(m);
        if (!built[0]) {
            m.build();
        }
        return this;
    }

    public AnnotationMirrorTestBuilder<AnnotationMirrorTestBuilder<T>> testMemberAsAnnotation(String memberName) {
        return new AnnotationMirrorTestBuilder<>(utils, (amtb) -> {
            addPredicate((AnnotationMirror a) -> {
                List<AnnotationMirror> mir = utils.annotationValues(a, memberName, AnnotationMirror.class);
                Predicate<? super AnnotationMirror> p = amtb.predicate();
                boolean result = true;
                if (p != null) {
                    for (AnnotationMirror am : mir) {
                        result &= p.test(am);
                    }
                }
                return result;
            });
            return this;
        });
    }

    public AnnotationMirrorTestBuilder<T> testMemberAsAnnotation(String memberName, Consumer<AnnotationMirrorTestBuilder<?>> c) {
        boolean[] built = new boolean[1];
        AnnotationMirrorTestBuilder<Void> res = new AnnotationMirrorTestBuilder<>(utils, (amtb) -> {
            addPredicate((AnnotationMirror a) -> {
                List<AnnotationMirror> mir = utils.annotationValues(a, memberName, AnnotationMirror.class);
                Predicate<? super AnnotationMirror> p = amtb.predicate();
                boolean result = true;
                if (p != null) {
                    for (AnnotationMirror am : mir) {
                        result &= p.test(am);
                    }
                }
                return result;
            });
            built[0] = true;
            return null;
        });
        c.accept(res);
        if (!built[0]) {
            res.build();
        }
        return this;
    }
}

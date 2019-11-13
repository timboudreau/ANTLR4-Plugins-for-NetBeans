/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.extraction;

import static com.mastfrog.util.preconditions.Checks.nonNegative;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.tree.ParseTree;
import org.nemesis.data.Hashable;

/**
 * Factory for predicates which test parse trees / rule nodes / parser rule
 * context by type or other attributes.
 *
 * @author Tim Boudreau
 */
abstract class RuleParentPredicate<P extends ParseTree> implements Predicate<P>, Hashable {

    static <T> RuleHierarchyPredicateBuilder<T> builder(Function<Predicate<? super ParseTree>, T> converter) {
        return new RuleHierarchyPredicateBuilder<>(converter);
    }

    RuleParentPredicate() {

    }

    static class IsTop<P extends ParseTree> extends RuleParentPredicate<P> {

        @Override
        public boolean test(ParseTree t) {
            return t != null && t.getParent() == null;
        }

        @Override
        public String toString() {
            return "is-top";
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(1029012);
        }
    }

    static class ChildCount extends RuleParentPredicate<ParseTree> {

        private final int count;

        public ChildCount(int count) {
            this.count = nonNegative("count", count);
        }

        @Override
        public boolean test(ParseTree t) {
            return t != null && count == t.getChildCount();
        }

        @Override
        public String toString() {
            return "has-" + count + "-children";
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(2091090924);
            hasher.writeInt(count);
        }
    }

    static <P extends ParseTree> RuleParentPredicate<P> wrap(Predicate<? super P> pred) {
        return new Wrap(pred);
    }

    static class Wrap<P extends ParseTree> extends RuleParentPredicate<P> {

        private final Predicate<? super ParseTree> delegate;

        Wrap(Predicate<? super ParseTree> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean test(P t) {
            return delegate.test(t);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(delegate);
        }
    }

    static class OP extends RuleParentPredicate<ParseTree> {

        Predicate<? super ParseTree> additionalParentTests;
        Predicate<ParseTree> parentTest;
        final int n;

        OP(Predicate<ParseTree> parentTest, int n) {
            this.parentTest = parentTest;
            this.n = n;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.hashObject(parentTest);
            hasher.writeInt(n);
        }

        @Override
        public boolean test(ParseTree t) {
            return findPassingAncestor(t) != null;
        }

        @Override
        public String toString() {
            return parentTest + "-within-" + n + "-ancestors";
        }

        @Override
        public RuleParentPredicate<ParseTree> and(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).and(wrap(other));
            }
            return this;
        }

        @Override
        public RuleParentPredicate<ParseTree> or(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).or(wrap(other));
            }
            return this;
        }

        @Override
        public RuleParentPredicate negate() {
            parentTest = parentTest.negate();
            return this;
        }

        ParseTree findPassingAncestor(ParseTree pt) {
            ParseTree p = pt;
            boolean found = false;
            for (int i = 0; p != null && i < n + 1; i++) {
                if (parentTest.test(p)) {
                    if (additionalParentTests != null) {
                        if (!additionalParentTests.test(p == null ? null : p.getParent())) {
                            return null;
                        }
                    }
                    found = true;
                    break;
                }
                p = p.getParent();
            }
            return found ? p : null;
        }
    }

    static class Hierarchical extends RuleParentPredicate<ParseTree> {

        final Predicate<? super ParseTree> parentTest;
        final Predicate<? super ParseTree> test;
        Predicate<? super ParseTree> additionalParentTests;

        Hierarchical(Predicate<? super ParseTree> test, RuleParentPredicate parentTest) {
            this.test = test;
            this.parentTest = parentTest;
        }

        @Override
        public RuleParentPredicate<ParseTree> and(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).and(wrap(other));
            }
            return this;
        }

        @Override
        public RuleParentPredicate<ParseTree> or(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).or(wrap(other));
            }
            return this;
        }

        @Override
        public boolean test(ParseTree t) {
            if (t == null) {
                return false;
            }
            boolean result;
            if (test instanceof OP) {
                t = ((OP) test).findPassingAncestor(t);
                result = t != null;
            } else {
                result = test.test(t);
            }
            if (result && parentTest != null) {
                ParseTree par = t.getParent();
                result = parentTest.test(par);
                if (result && additionalParentTests != null) {
                    result &= additionalParentTests.test(t);
                }
            }
            return result;
        }

        @Override
        public void hashInto(Hasher hasher) {
            if (parentTest != null) {
                hasher.hashObject(parentTest);
            }
            if (additionalParentTests != null) {
                hasher.hashObject(additionalParentTests);
            }
            hasher.hashObject(test);
        }

        @Override
        public String toString() {
            if (additionalParentTests != null) {
                return test.toString() + (parentTest == null ? "<stop>" : " -> and(" + parentTest + "," + additionalParentTests + ")");
            } else {
                return test.toString() + (parentTest == null ? "<stop>" : " -> " + parentTest);
            }
        }
    }

    static class Any extends RuleParentPredicate<ParseTree> {

        @Override
        public boolean test(ParseTree t) {
            return t != null && t.getParent() != null;
        }

        @Override
        public String toString() {
            return "has-parent/any-type";
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(89323);
        }
    }

    static class ExactTypeRuleParent<P extends ParseTree> extends RuleParentPredicate<P> {

        private final Class<? extends P> type;
        Predicate<? super P> additionalParentTests;

        @Override
        public RuleParentPredicate<P> and(Predicate<? super P> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<P>) additionalParentTests).and(wrap(other));
            }
            return this;
        }

        @Override
        public RuleParentPredicate<P> or(Predicate<? super P> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<P>) additionalParentTests).or(wrap(other));
            }
            return this;
        }

        public ExactTypeRuleParent(Class<? extends P> type) {
            this.type = type;
        }

        @Override
        public boolean test(ParseTree t) {
            if (t == null) {
                return false;
            }
            ParseTree par = t.getParent();
            boolean result = par != null && type.isInstance(par);
            if (result && additionalParentTests != null) {
                result &= additionalParentTests.test(t == null ? null : ((P) t.getParent()));
            }
            return result;
        }

        @Override
        public String toString() {
            if (additionalParentTests != null) {
                return "and(has-parent-of-" + type.getSimpleName()
                        + "," + additionalParentTests + ")";
            } else {
                return "has-parent-of-" + type.getSimpleName();
            }
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(2039010);
            hasher.writeString(type.getName());
        }
    }

    static class MultiTypeRuleParent extends RuleParentPredicate<ParseTree> {

        private final Class<?>[] types;
        Predicate<? super ParseTree> additionalParentTests;

        public MultiTypeRuleParent(Class<?>[] types) {
            this.types = types;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(485929);
            for (int i = 0; i < types.length; i++) {
                hasher.writeString(types[i].getName());
            }
        }

        @Override
        public boolean test(ParseTree t) {
            if (t == null) {
                return false;
            }
            ParseTree par = t.getParent();
            for (Class<?> c : types) {
                if (c.isInstance(par)) {
                    if (additionalParentTests != null) {
                        return additionalParentTests.test(t == null ? null : (t.getParent()));
                    }
                    return true;
                }
            }
            return false;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Class<?> type : types) {
                if (sb.length() > 0) {
                    sb.append("/");
                }
                sb.append(type.getSimpleName());
            }
            return sb.append(']').insert(0, "parent-of-types-[").toString();
        }

        @Override
        public RuleParentPredicate<ParseTree> and(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).and(wrap(other));
            }
            return this;
        }

        @Override
        public RuleParentPredicate<ParseTree> or(Predicate<? super ParseTree> other) {
            if (additionalParentTests == null) {
                additionalParentTests = other;
            } else {
                additionalParentTests = ((Predicate<ParseTree>) additionalParentTests).or(wrap(other));
            }
            return this;
        }
    }

    @Override
    public RuleParentPredicate<P> or(Predicate<? super P> other) {
        return new LogicalRuleParentPredicate(true, this, other);
    }

    @Override
    public RuleParentPredicate<P> negate() {
        return new NegatedRuleParentPredicate<>(this);
    }

    @Override
    public RuleParentPredicate<P> and(Predicate<? super P> other) {
        return new LogicalRuleParentPredicate(false, this, other);
    }

    static class LogicalRuleParentPredicate<P extends ParseTree> extends RuleParentPredicate<P> {

        private final boolean or;
        private final List<Predicate<? super P>> all = new ArrayList<>(4);

        LogicalRuleParentPredicate(boolean or, Predicate<? super ParseTree> initial, Predicate<? super ParseTree> next) {
            this.or = or;
            all.add(initial);
            all.add(next);
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(or ? 3 : 1);
            for (Predicate<?> p : all) {
                hasher.hashObject(p);
            }
        }

        @Override
        public String toString() {
            return (or ? "or(" : "and(") + Strings.join(',', all) + ")";
        }

        @Override
        public boolean test(P t) {
            for (Predicate<? super P> p : all) {
                if (!p.test(t)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public RuleParentPredicate<P> or(Predicate<? super P> other) {
            if (or) {
                all.add(other);
                return this;
            }
            return new LogicalRuleParentPredicate(true, this, other);
        }

        @Override
        public RuleParentPredicate<P> and(Predicate<? super P> other) {
            if (!or) {
                all.add(other);
                return this;
            }
            return new LogicalRuleParentPredicate(false, this, other);
        }
    }

    static class NegatedRuleParentPredicate<P extends ParseTree> extends RuleParentPredicate<P> {

        private final RuleParentPredicate<P> orig;

        public NegatedRuleParentPredicate(RuleParentPredicate<P> orig) {
            this.orig = orig;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(-1);
            hasher.hashObject(orig);
        }

        @Override
        public boolean test(P t) {
            return !orig.test(t);
        }

        @Override
        public RuleParentPredicate negate() {
            return orig;
        }

        public String toString() {
            return "not(" + orig + ")";
        }
    }
}

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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.tree.ParseTree;
import org.nemesis.data.Hashable;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RuleParentPredicate implements Predicate<ParseTree>, Hashable {

    public static <T> Builder<T> builder(Function<Predicate<ParseTree>, ? extends T> converter) {
        return new Builder<>(converter);
    }

    RuleParentPredicate() {

    }

    public static class Builder<T> {

        private final Function<Predicate<ParseTree>, ? extends T> converter;
        private final List<RuleParentPredicate> hierarchy = new LinkedList<>();

        Builder(Function<Predicate<ParseTree>, ? extends T> converter) {
            this.converter = converter;
        }

        public Builder<T> withParentType(Class<? extends ParseTree> parentType) {
            hierarchy.add(new ExactTypeRuleParent(parentType));
            return this;
        }

        public Builder<T> withParentType(Class<? extends ParseTree> firstType, Class<? extends ParseTree> secondType, Class<?>... moreTypes) {
            Set<Class<?>> all = new LinkedHashSet<>();
            all.add(firstType);
            all.add(secondType);
            all.addAll(Arrays.asList(moreTypes));
            hierarchy.add(new MultiTypeRuleParent(all.toArray(new Class<?>[all.size()])));
            return this;
        }

        public Builder<T> skippingParent() {
            hierarchy.add(new Any());
            return this;
        }

        public Builder<T> thatMatches(Predicate<? super ParseTree> additionalTest) {
            if (hierarchy.isEmpty()) {
                hierarchy.add(new Any());
            }
            hierarchy.set(hierarchy.size() - 1, hierarchy.get(hierarchy.size() - 1).and(additionalTest));
            return this;
        }

        public Builder<T> thatHasChildren(int count) {
            return thatMatches(new ChildCount(count));
        }

        public Builder<T> thatIsTop() {
            return thatMatches(new IsTop());
        }

        public Builder<T> thatHasOnlyOneChild() {
            return thatMatches(new ChildCount(1));
        }

        public T build() {
            Hierarchical top = null;
            for (RuleParentPredicate pred : hierarchy) {
                if (top == null) {
                    top = new Hierarchical(pred, null);
                } else {
                    top = new Hierarchical(top, pred);
                }
            }
            return converter.apply(top);
        }
    }

    static class IsTop extends RuleParentPredicate {

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

    static class ChildCount extends RuleParentPredicate {

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

    static class Hierarchical extends RuleParentPredicate {

        private final Predicate<? super ParseTree> parentTest;
        private final Predicate<? super ParseTree> test;

        public Hierarchical(Predicate<? super ParseTree> test, RuleParentPredicate parentTest) {
            this.test = test;
            this.parentTest = parentTest;
        }

        @Override
        public boolean test(ParseTree t) {
            boolean result = test.test(t);
            if (result && parentTest != null) {
                return parentTest.test(t.getParent());
            }
            return result;
        }

        @Override
        public void hashInto(Hasher hasher) {
            if (parentTest != null) {
                hasher.hashObject(parentTest);
            }
            hasher.hashObject(test);
        }

        @Override
        public String toString() {
            if (true) {
                return test.toString() + (parentTest == null ? "<stop>" : " -> " + parentTest);
            }
            StringBuilder sb = new StringBuilder();
            LinkedList<Predicate<? super ParseTree>> pars = new LinkedList<>();
            Predicate<? super ParseTree> par = parentTest;
            while (par != null) {
                if (par instanceof Hierarchical) {
                    pars.add(0, ((Hierarchical) par).test);
                    if (((Hierarchical) par).parentTest instanceof Hierarchical) {
                        par = ((Hierarchical) par).parentTest;
                    } else {
                        if (((Hierarchical) par).parentTest != null) {
                            pars.add(((Hierarchical) par).parentTest);
                        }
                        break;
                    }
                } else {
                    pars.add(0, par);
                    break;
                }
            }
            String indent = "";
            for (Predicate<? super ParseTree> p : pars) {
                sb.append(indent).append(p);
                indent += "\n  ";
            }
            return sb.toString();
        }
    }

    static class Any extends RuleParentPredicate {

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

    static class ExactTypeRuleParent extends RuleParentPredicate {

        private final Class<? extends ParseTree> type;

        public ExactTypeRuleParent(Class<? extends ParseTree> type) {
            this.type = type;
        }

        @Override
        public boolean test(ParseTree t) {
            if (t == null) {
                return false;
            }
            ParseTree par = t.getParent();
            return par != null && type.isInstance(par);
        }

        @Override
        public String toString() {
            return "has-parent-of-" + type.getSimpleName();
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(2039010);
            hasher.writeString(type.getName());
        }
    }

    static class MultiTypeRuleParent extends RuleParentPredicate {

        private final Class<?>[] types;

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
    }

    @Override
    public RuleParentPredicate or(Predicate<? super ParseTree> other) {
        return new LogicalRuleParentPredicate(true, this, other);
    }

    @Override
    public RuleParentPredicate negate() {
        return new NegatedRuleParentPredicate(this);
    }

    @Override
    public RuleParentPredicate and(Predicate<? super ParseTree> other) {
        return new LogicalRuleParentPredicate(false, this, other);
    }

    static class LogicalRuleParentPredicate extends RuleParentPredicate {

        private final boolean or;
        private final List<Predicate<? super ParseTree>> all = new ArrayList<>(4);

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
        public boolean test(ParseTree t) {
            for (Predicate<? super ParseTree> p : all) {
                if (!p.test(t)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public RuleParentPredicate or(Predicate<? super ParseTree> other) {
            if (or) {
                all.add(other);
                return this;
            }
            return new LogicalRuleParentPredicate(true, this, other);
        }

        @Override
        public RuleParentPredicate and(Predicate<? super ParseTree> other) {
            if (!or) {
                all.add(other);
                return this;
            }
            return new LogicalRuleParentPredicate(false, this, other);
        }
    }

    static class NegatedRuleParentPredicate extends RuleParentPredicate {

        private final RuleParentPredicate orig;

        public NegatedRuleParentPredicate(RuleParentPredicate orig) {
            this.orig = orig;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeInt(-1);
            hasher.hashObject(orig);
        }

        @Override
        public boolean test(ParseTree t) {
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

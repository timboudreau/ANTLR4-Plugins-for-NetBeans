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

import com.mastfrog.util.preconditions.Checks;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Builder for complex predicates that test a rule node or its parent types
 * within an Antlr parse tree, hierarchically, starting with the current node,
 * then its parent, and so forth - so you can match, say, only nodes which are
 * FooRules that have a parent of BarRule and an ancestor of QuuxRule within 3
 * parent nodes of the initially matched one.
 * <p>
 * This can be used in ExtractionBuilders to match cases where some particular
 * pattern of parent hierarchy is interesting regardless of how deep in the
 * parse tree it occurs, and ignoring parent elements which may vary but have no
 * effect on interest in the pattern; for example, when parsing an Antlr
 * grammar, we want to detect EBNF items that can match the empty string (which
 * Antlr itself will flag with a near-useless error message that amounts to
 * "Somewhere in the closure of this rule is something that can match the empty
 * string, but I won't tell you where. Go figure it out for yourself.") at
 * <i>any</i> level of the parse tree (but they only match if they are the only
 * child of a parent block or ebnf), with several possible ancestor hierarchies
 * depending on whether the block in question is a labeled alternative, plain
 * alternative or the entire rule body is, e.g. "foo : bar*".
 * </p>
 *
 * @param <T> The return type
 */
public final class RuleHierarchyPredicateBuilder<T> {

    private final Function<Predicate<? super ParseTree>, ? extends T> converter;
    final List<RuleParentPredicate<?>> hierarchy = new LinkedList<>();

    RuleHierarchyPredicateBuilder(Function<Predicate<? super ParseTree>, ? extends T> converter) {
        this.converter = converter;
    }

    /**
     * Match a rule node whose <i>parent</i> node is of the passed type;
     * subsequent calls to <code>withParentType()</code> or
     * <code>withParentTypes()</code> will match on the parent of the node that
     * passed this test.
     *
     * @param parentType The parent type
     * @return this
     */
    public <P extends ParseTree> RuleHierarchyPredicateChildBuilder<T, P> withParentType(Class<? extends P> parentType) {
        hierarchy.add(new RuleParentPredicate.ExactTypeRuleParent(parentType));
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    /**
     * Match a rule node whose <i>parent</i> node is of any of the passed types;
     * subsequent calls to <code>withParentType()</code> or
     * <code>withParentTypes()</code> will match on the parent of the node that
     * passed this test.
     *
     * @param parentType The parent type
     * @return this
     */
    public RuleHierarchyPredicateChildBuilder<T, ParseTree> withParentType(Class<? extends ParseTree> firstType, Class<? extends ParseTree> secondType, Class<?>... moreTypes) {
        Set<Class<?>> all = new LinkedHashSet<>();
        all.add(firstType);
        all.add(secondType);
        all.addAll(Arrays.asList(moreTypes));
        hierarchy.add(new RuleParentPredicate.MultiTypeRuleParent(all.toArray(new Class<?>[all.size()])));
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    /**
     * Allow the parent of the current or last-matched node to be of any type -
     * skip it and perform the next test on <i>its</i> parent.
     *
     * @return this
     */
    public RuleHierarchyPredicateChildBuilder<T, ParseTree> skippingParent() {
        hierarchy.add(new RuleParentPredicate.Any());
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    /**
     * Find an ancestor node of the given type within <i>n</i> parents of the
     * tested node, and continue all subsequent parent tests against
     * <i>that</i> node.
     *
     * @param type The node type
     * @param n A (non-zero, non-negative) number of parent nodes to search for
     * a match
     * @return
     */
    public <P extends ParseTree> RuleHierarchyPredicateChildBuilder<T, P> withAncestor(Class<? extends P> type, int n) {
        hierarchy.add(new RuleParentPredicate.OP(new RuleParentPredicate.ExactTypeRuleParent(Checks.notNull("type", type)), Checks.nonZero("withinAncestorCount", Checks.nonNegative("withinAncestorCount", n))));
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    /**
     * Find an ancestor node matching the passed predicate within <i>n</i>
     * parents of the tested node, and continue all subsequent parent tests
     * against <i>that</i> node.
     *
     * @param type The node type
     * @param n A (non-zero, non-negative) number of parent nodes to search for
     * a match
     * @return
     */
    public RuleHierarchyPredicateChildBuilder<T, ParseTree> withAncestor(Predicate<? super ParseTree> pred, int n) {
        hierarchy.add(new RuleParentPredicate.OP(RuleParentPredicate.wrap(pred), Checks.nonZero("withinAncestorCount", Checks.nonNegative("withinAncestorCount", n))));
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    /**
     * Find an ancestor node of the given type within <i>n</i> parents of the
     * tested node, and continue all subsequent parent tests against
     * <i>that</i> node.
     *
     * @param firstType The node type
     * @param secondType The node type
     * @param moreTypes Additional types to match on
     * @param n A (non-zero, non-negative) number of parent nodes to search for
     * a match
     * @return this
     */
    public RuleHierarchyPredicateChildBuilder<T, ParseTree> withAncestor(int n, Class<? extends ParseTree> firstType, Class<? extends ParseTree> secondType, Class<?>... moreTypes) {
        Set<Class<?>> all = new LinkedHashSet<>();
        all.add(Checks.notNull("firstType", firstType));
        all.add(Checks.notNull("secondType", secondType));
        all.addAll(Arrays.asList(moreTypes));
        Class<?>[] types = Checks.noNullElements("moreTypes", all.toArray(new Class<?>[all.size()]));
        hierarchy.add(new RuleParentPredicate.OP(new RuleParentPredicate.MultiTypeRuleParent(types), Checks.nonZero("withinAncestorCount", Checks.nonNegative("withinAncestorCount", n))));
        return new RuleHierarchyPredicateChildBuilder<>(this);
    }

    public static final class RuleHierarchyPredicateChildBuilder<T, P extends ParseTree> {

        private final RuleHierarchyPredicateBuilder<T> builder;

        RuleHierarchyPredicateChildBuilder(RuleHierarchyPredicateBuilder<T> builder) {
            this.builder = builder;
        }

        /**
         * Match a rule node whose <i>parent</i> node is of the passed type;
         * subsequent calls to <code>withParentType()</code> or
         * <code>withParentTypes()</code> will match on the parent of the node
         * that passed this test.
         *
         * @param parentType The parent type
         * @return this
         */
        public <N extends ParseTree> RuleHierarchyPredicateChildBuilder<T, N> withParentType(Class<? extends N> parentType) {
            return builder.withParentType(parentType);
        }

        /**
         * Match a rule node whose <i>parent</i> node is of any of the passed
         * types; subsequent calls to <code>withParentType()</code> or
         * <code>withParentTypes()</code> will match on the parent of the node
         * that passed this test.
         *
         * @param parentType The parent type
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, ParseTree> withParentType(Class<? extends ParseTree> firstType, Class<? extends ParseTree> secondType, Class<?>... moreTypes) {
            return builder.withParentType(firstType, secondType, moreTypes);
        }

        /**
         * Allow the parent of the current or last-matched node to be of any
         * type - skip it and perform the next test on <i>its</i> parent.
         *
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, ParseTree> skippingParent() {
            return builder.skippingParent();
        }

        /**
         * Find an ancestor node of the given type within <i>n</i> parents of
         * the tested node, and continue all subsequent parent tests against
         * <i>that</i> node.
         *
         * @param type The node type
         * @param n A (non-zero, non-negative) number of parent nodes to search
         * for a match
         * @return
         */
        public <N extends ParseTree> RuleHierarchyPredicateChildBuilder<T, N> withAncestor(Class<? extends N> type, int n) {
            return builder.withAncestor(type, n);
        }

        /**
         * Find an ancestor node matching the passed predicate within <i>n</i>
         * parents of the tested node, and continue all subsequent parent tests
         * against <i>that</i> node.
         *
         * @param type The node type
         * @param n A (non-zero, non-negative) number of parent nodes to search
         * for a match
         * @return
         */
        public RuleHierarchyPredicateChildBuilder<T, ParseTree> withAncestor(Predicate<? super ParseTree> pred, int n) {
            return builder.withAncestor(pred, n);
        }

        /**
         * Find an ancestor node of the given type within <i>n</i> parents of
         * the tested node, and continue all subsequent parent tests against
         * <i>that</i> node.
         *
         * @param firstType The node type
         * @param secondType The node type
         * @param moreTypes Additional types to match on
         * @param n A (non-zero, non-negative) number of parent nodes to search
         * for a match
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, ParseTree> withAncestor(int n, Class<? extends ParseTree> firstType, Class<? extends ParseTree> secondType, Class<?>... moreTypes) {
            return builder.withAncestor(n, firstType, secondType, moreTypes);
        }

        public T build() {
            return builder.build();
        }

        /**
         * Apply an additional test to the last added predicate.
         *
         * @param additionalTest Another test
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, P> thatMatches(Predicate<? super P> additionalTest) {
            if (builder.hierarchy.isEmpty()) {
                builder.hierarchy.add(new RuleParentPredicate.Any());
            }
            int lastPosition = builder.hierarchy.size() - 1;
            RuleParentPredicate<P> last = (RuleParentPredicate<P>) builder.hierarchy.get(lastPosition);
            System.out.println("AND " + last + " WITH " + additionalTest);
            builder.hierarchy.set(lastPosition, last.and(additionalTest));
            return this;
        }

        /**
         * Apply an additional test to the node matched by the passed predicate,
         * that it must have exactly the passed number of children.
         *
         * @param count The number of children
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, P> thatHasChildren(int count) {
            return thatMatches(new RuleParentPredicate.ChildCount(count));
        }

        /**
         * Apply an additional test to the node matched by the passed predicate,
         * that it must be the top of the document.
         *
         * @param count The number of children
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, P> thatIsTop() {
            return thatMatches(new RuleParentPredicate.IsTop());
        }

        /**
         * Apply an additional test to the node matched by the passed predicate,
         * that it must have exactly one child.
         *
         * @param count The number of children
         * @return this
         */
        public RuleHierarchyPredicateChildBuilder<T, P> thatHasOnlyOneChild() {
            return thatMatches(new RuleParentPredicate.ChildCount(1));
        }
    }

    public T build() {
        RuleParentPredicate<? super ParseTree> top = null;
        int max = hierarchy.size() - 1;
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            RuleParentPredicate curr = hierarchy.get(i);
            if (top == null) {
                if (i == max) {
                    top = curr;
                } else {
                    top = new RuleParentPredicate.Hierarchical(curr, null);
                }
            } else {
                top = new RuleParentPredicate.Hierarchical(curr, top);
            }
        }
        if (top == null) {
            return converter.apply(new RuleParentPredicate.Any());
        }
        return converter.apply(top);
    }
}

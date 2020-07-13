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
package com.mastfrog.antlr.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Utilities for scanning the parents of an antlr parse tree node and
 * determining if they match some test.
 *
 * @author Tim Boudreau
 */
public final class TreeUtils {

    private TreeUtils() {
        throw new AssertionError();
    }

    /**
     * Determine if a parse tree is or is an ancestor of a given other parse
     * tree.
     *
     * @param possibleAncestor The possible ancestor
     * @param target The three which might be a descendat
     * @return true if they are related
     */
    public static boolean isAncestor(ParseTree possibleAncestor, ParseTree target) {
        if (target == null) {
            return false;
        }
        if (possibleAncestor == target) {
            return true;
        }
        return isAncestor(possibleAncestor, target.getParent());
    }

    /**
     * Returns true if the passed context is not the only child of its parent.
     *
     * @param ctx The rule context
     * @return
     */
    public static boolean hasSibling(ParserRuleContext ctx) {
        return sibling(ctx) != null;
    }

    /**
     * Return either the next element or previous element if there are any trees
     * adjacent to this one in its parent.
     *
     * @param orig A parse tree
     * @return the sibling or null
     */
    public static ParseTree sibling(ParserRuleContext orig) {
        ParseTree result = nextSibling(orig);
        return result == null ? TreeUtils.prevSibling(orig) : result;
    }

    /**
     * Determine if a rule has an ancestor of a given type.
     *
     * @param ctx The rule
     * @param type The type
     * @return true if one is found between this rule and the top of the tree
     */
    public static boolean hasAncestor(ParserRuleContext ctx, Class<? extends ParserRuleContext> type) {
        ParserRuleContext curr = ctx.getParent();
        while (curr != null) {
            if (type.isInstance(curr)) {
                return true;
            }
            curr = curr.getParent();
        }
        return false;
    }

    /**
     * Get the nearest ancestor to the passed rule of type T.
     *
     * @param <T> The type
     * @param ctx The rule
     * @param type The type
     * @return An instance or null
     */
    public static <T> T ancestor(ParserRuleContext ctx, Class<T> type) {
        ParserRuleContext curr = ctx.getParent();
        while (curr != null) {
            if (type.isInstance(curr)) {
                return type.cast(curr);
            }
            curr = curr.getParent();
        }
        return null;
    }

    /**
     * Determine if all ancestors of the passed rule up to (but not including)
     * the nearest one of the passed type have only one child.
     *
     * @param ctx The rule
     * @param stopAt The type
     * @return True if all ancestors tested have only one child
     */
    public static boolean isSingleChildAncestors(ParserRuleContext ctx, Class<? extends ParserRuleContext> stopAt) {
        ParserRuleContext curr = ctx.getParent();
        while (curr != null && !stopAt.isInstance(curr)) {
            if (curr.getChildCount() != 1) {
                return false;
            }
            curr = curr.getParent();
        }
        return true;
    }

    /**
     * Find the outermost ancestor of type T, of the passed rule context, before
     * encountering one of the passed stop types or the top of the parse tree.
     *
     * @param <T> The type
     * @param ctx A rule context
     * @param targetType The type sought
     * @param stopTypes Types at which the search should cease and any
     * encountered result be returned, if encountered
     * @return An instance of T or null
     */
    public static <T> T findOutermostAncestor(ParserRuleContext ctx, Class<T> targetType, Class<?>... stopTypes) {
        ParserRuleContext curr = ctx.getParent();
        Set<Class<?>> types = stopTypes.length == 0 ? Collections.emptySet() : new HashSet<>(Arrays.asList(stopTypes));
        T result = null;
        while (curr != null && !types.contains(curr.getClass())) {
            if (targetType.isInstance(curr)) {
                result = targetType.cast(curr);
            }
            curr = curr.getParent();
        }
        return result;
    }

    /**
     * Find the outermost ancestor of type T, of the passed rule context, before
     * reaching the top of the parse tree.
     *
     * @param <T> The type
     * @param ctx A rule context
     * @param targetType The type sought
     * @return An instance of T or null
     */
    public static <T> T findOutermostAncestor(ParserRuleContext ctx, Class<T> targetType) {
        ParserRuleContext curr = ctx;
        T result = null;
        while (curr != null) {
            if (targetType.isInstance(curr)) {
                result = targetType.cast(curr);
            }
            curr = curr.getParent();
        }
        return result;
    }

    /**
     * Find a sibling of the outermost ancestor of type T which is of type R and
     * is the next child of the parent of that ancestor.
     *
     * @param <T> The ancestor type
     * @param <R> The target type
     * @param ctx The rule to start searching the hierarchy upward from
     * @param ancestorType The ancestor type
     * @param siblingType The sibling type
     * @param stopTypes Types to stop searching for the ancestor at
     * @return An instance of R or null
     */
    public static <T extends ParserRuleContext, R> R findRightAdjacentSiblingOfOutermostAncestor(ParserRuleContext ctx, Class<T> ancestorType, Class<R> siblingType, Class<?>... stopTypes) {
        T ancestor = findOutermostAncestor(ctx, ancestorType, stopTypes);
        if (ancestor == null) {
            return null;
        }
        ParserRuleContext parent = ancestor.getParent();
        if (parent != null) {
            List<ParseTree> kids = parent.children;
            if (kids != null) {
                int ix = kids.indexOf(ancestor);
                if (ix < 0 || ix == kids.size() - 1) {
                    return null;
                }
                ParseTree next = kids.get(ix + 1);
                if (next != null && siblingType.isInstance(next)) {
                    return siblingType.cast(next);
                }
            }
        }
        return null;
    }

    /**
     * Find a sibling of the outermost ancestor of type T which is of type R and
     * is the next child of the parent of that ancestor.
     *
     * @param <T> The ancestor type
     * @param <R> The target type
     * @param ctx The rule to start searching the hierarchy upward from
     * @param ancestorType The ancestor type
     * @param siblingType The sibling type
     * @param stopTypes Types to stop searching for the ancestor at
     * @return An instance of R or null
     */
    public static <T extends ParserRuleContext, R> R findLeftAdjacentSiblingOfOutermostAncestor(ParserRuleContext ctx, Class<T> ancestorType, Class<R> siblingType, Class<?>... stopTypes) {
        T ancestor = findOutermostAncestor(ctx, ancestorType, stopTypes);
        if (ancestor == null) {
            return null;
        }
        ParserRuleContext parent = ancestor.getParent();
        if (parent != null) {
            List<ParseTree> kids = parent.children;
            if (kids != null) {
                int ix = kids.indexOf(ancestor);
                if (ix == 0) {
                    return null;
                }
                ParseTree prev = kids.get(ix - 1);
                if (prev != null && siblingType.isInstance(prev)) {
                    return siblingType.cast(prev);
                }
            }
        }
        return null;
    }

    public static <T extends ParserRuleContext, R> boolean hasRightAdjacentSiblingOfOutrmostAncestor(ParserRuleContext ctx, Class<T> ancestorType, Class<R> siblingType, Class<?>... stopTypes) {
        return findRightAdjacentSiblingOfOutermostAncestor(ctx, ancestorType, siblingType, stopTypes) != null;
    }

    /**
     * Flexibly search the ancestor nodes to the passed tree node, using the
     * passed function to determine whether to continue, succed or fail.
     *
     * @param ctx The rule
     * @param test The test function
     * @return The result of the function, or TreeNodeSearchResult.UNANSWERED if
     * the top of the tree was reached (the test function must never return
     * UNANSWERED).
     */
    public static TreeNodeSearchResult searchParentTree(ParserRuleContext ctx, Function<ParserRuleContext, TreeNodeSearchResult> test) {
        ParserRuleContext curr = ctx.getParent();
        TreeNodeSearchResult result;
        while (curr != null) {
            result = test.apply(curr);
            switch (result) {
                case OK:
                case FAIL:
                    return result;
                case CONTINUE:
                    curr = curr.getParent();
                    break;
                case UNANSWERED:
                default:
                    throw new AssertionError(result);
            }
        }
        return TreeNodeSearchResult.UNANSWERED;
    }

    public static <T extends ParseTree> T nextSibling(ParserRuleContext ctx, Class<T> type) {
        ParseTree next = nextSibling(ctx);
        if (next != null && type.isInstance(next)) {
            return type.cast(next);
        }
        return null;
    }

    public static <T extends ParseTree> T prevSibling(ParserRuleContext ctx, Class<T> type) {
        ParseTree next = prevSibling(ctx);
        if (next != null && type.isInstance(next)) {
            return type.cast(next);
        }
        return null;
    }

    public static ParseTree nextSibling(ParserRuleContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        if (parent == null) {
            return null;
        }
        int ix = parent.children.indexOf(ctx);
        int size = parent.children.size();
        if (ix < size - 1) {
            return parent.getChild(ix + 1);
        }
        return null;
    }

    public static ParseTree prevSibling(ParserRuleContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        if (parent == null) {
            return null;
        }
        int ix = parent.children.indexOf(ctx);
        if (ix > 0) {
            return parent.getChild(ix - 1);
        }
        return null;
    }

    public static boolean isSingleChildDescendants(ParseTree ctx) {
        if (ctx.getChildCount() == 0) {
            return true;
        }
        if (ctx.getChildCount() == 1) {
            return isSingleChildDescendants(ctx.getChild(0));
        }
        return false;
    }

    public static String stringify(ParserRuleContext ctx) {
        return stringify(ctx, new StringBuilder(), 0, ctx.depth()).toString();
    }

    public static StringBuilder stringify(ParserRuleContext ctx, StringBuilder into, int depth, int offset) {
        char[] c = new char[depth * 2];
        Arrays.fill(c, ' ');
        into.append(c);
        into.append(depth + offset).append(". ").append(ctx.getClass().getSimpleName())
                .append(": ").append(ctx.getText()).append("'\n");
        int ct = ctx.getChildCount();
        for (int i = 0; i < ct; i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) child;
                stringify(prc, into, depth + 1, offset);
            }
        }
        return into;
    }

    /**
     * Result of visiting one parent of a tree node.
     */
    public enum TreeNodeSearchResult {
        /**
         * Success result, stop searching parent nodes.
         */
        OK,
        /**
         * Failed result, stop searching parent nodes.
         */
        FAIL,
        /**
         * Continue searching parent nodes, no success or failure yet.
         */
        CONTINUE,
        /**
         * Fallthrough - the top of the tree was reached with neither success
         * nor failure.
         */
        UNANSWERED;

        public boolean isSuccess() {
            return this == OK;
        }
    }
}

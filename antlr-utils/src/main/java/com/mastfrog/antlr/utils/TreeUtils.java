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

import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;

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
        TreeNodeSearchResult result = TreeNodeSearchResult.UNANSWERED;
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

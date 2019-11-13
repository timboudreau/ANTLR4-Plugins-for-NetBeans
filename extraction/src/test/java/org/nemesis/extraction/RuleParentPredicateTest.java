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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.nemesis.extraction.RuleParentPredicateTest.Trees.a;
import static org.nemesis.extraction.RuleParentPredicateTest.Trees.b;
import static org.nemesis.extraction.RuleParentPredicateTest.Trees.c;
import static org.nemesis.extraction.RuleParentPredicateTest.Trees.d;

/**
 *
 * @author Tim Boudreau
 */
public class RuleParentPredicateTest {

    @Test
    public void testToStringIsMeaningful() {
        String msg = RuleParentPredicate.<String>builder(pred -> {
            return pred.toString();
        }).withParentType(RuleNode.class)
                .withParentType(ParserRuleContext.class)
                .thatHasOnlyOneChild()
                .skippingParent()
                .withParentType(RuleNode.class)
                .thatIsTop()
                .build();
        assertEquals("has-parent-of-RuleNode -> "
                + "and(has-parent-of-ParserRuleContext,has-1-children) -> "
                + "has-parent/any-type -> and(has-parent-of-RuleNode,is-top)",
                msg);
    }

    @Test
    public void testAncestorsWithAdditionalTests() {
        RuleContext shouldPass = Trees.tree(d, a, a, b, b, b, c, d);

        Predicate<? super ParseTree> p;
        boolean pass;
        p = RuleParentPredicate.builder(RuleParentPredicateTest::p2p)
                .withParentType(C.class)
                .withAncestor(A.class, 4)
                .thatMatches((A a) -> {
                    // If we get a CCE here, we're being handed the child
                    // node not the parent node
                    System.out.println("GOT " + a);
                    assertNotNull(a, "Was passed null");
                    assertTrue(a instanceof A, "Got wrong type " + a.getClass().getName() + ": " + a);
                    assertTrue(a.getParent() instanceof A, "Unexpected parent " + a.getParent());
                    return true;
                })
                .withParentType(A.class)
                .withParentType(D.class)
                .build();

        pass = p.test(shouldPass);
        assertTrue(pass, p + " did not match " + shouldPass);
    }

    @Test
    public void testSkippingToAncestors() {
        RuleContext shouldPass = Trees.tree(d, a, a, b, b, b, c, d);
        RuleContext shouldFail = Trees.tree(a, b, b, c, c, c, c, d);

        Predicate<? super ParseTree> p;
        boolean pass;
        boolean fail;

        p = RuleParentPredicate.builder(RuleParentPredicateTest::p2p)
                .withParentType(C.class)
                .withAncestor(A.class, 4)
                .withParentType(A.class)
                .withParentType(D.class)
                .build();

        pass = p.test(shouldPass);
        fail = p.test(shouldFail);

        assertTrue(pass, p + " did not match " + shouldPass);
        assertFalse(fail, p + " did match " + shouldFail);
    }

    @Test
    public void testMatching() {
        RuleContext shouldPass = Trees.tree(a, b, c, d);
        RuleContext shouldFail = Trees.tree(d, c, b, a);

        assertTrue(shouldPass instanceof D, "test is broken: " + shouldPass);
        assertTrue(shouldFail instanceof A, "test is broken: " + shouldFail);

        Predicate<? super ParseTree> p;
        boolean pass;
        boolean fail;

        p = RuleParentPredicate.builder(RuleParentPredicateTest::p2p)
                .withParentType(C.class)
                .build();

        pass = p.test(shouldPass);
        fail = p.test(shouldFail);
        assertTrue(pass, p + " did not match " + shouldPass);
        assertFalse(fail, p + " did match " + shouldFail);

        p = RuleParentPredicate.builder(RuleParentPredicateTest::p2p)
                .withParentType(C.class)
                .withParentType(B.class)
                .build();

        pass = p.test(shouldPass);
        fail = p.test(shouldFail);
        assertTrue(pass, p + " did not match " + shouldPass);
        assertFalse(fail, p + " did match " + shouldFail);

        p = RuleParentPredicate.builder(RuleParentPredicateTest::p2p)
                .withParentType(C.class)
                .withParentType(B.class)
                .withParentType(A.class)
                .build();

        pass = p.test(shouldPass);
        fail = p.test(shouldFail);
        assertTrue(pass, p + " did not match " + shouldPass);
        assertFalse(fail, p + " did match " + shouldFail);
    }

    static Predicate<? super ParseTree> p2p(Predicate<? super ParseTree> pred) {
        return pred;
    }

    enum Trees implements Supplier<RuleContext> {
        a, b, c, d, e;

        public static ARC tree(Trees... all) {
            ARC curr = null;
            for (Trees t : all) {
                if (curr == null) {
                    curr = t.get();
                } else {
                    curr = t.get(curr);
                }
            }
            return curr;
        }

        public ARC get(ARC parent) {
            ARC result = get();
            result.setParent(parent);
            parent.addChild(result);
            assertSame(result.getParent(), parent);
            assertEquals(1, parent.getChildCount());
            assertSame(result, parent.getChild(0));
            return result;
        }

        @Override
        public ARC get() {
            switch (this) {
                case a:
                    return new A();
                case b:
                    return new B();
                case c:
                    return new C();
                case d:
                    return new D();
                case e:
                    return new E();
                default:
                    throw new AssertionError(this);
            }
        }
    }

    static abstract class ARC extends RuleContext {

        private List<ARC> children = new ArrayList<>();

        void addChild(ARC child) {
            children.add(child);
        }

        public String toString() {
            RuleContext par = getParent();
            return par == null ? getClass().getSimpleName()
                    : (getClass().getSimpleName() + " <- " + par);
        }

        @Override
        public ParseTree getChild(int i) {
            return children.get(i);
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

    }

    private static class A extends ARC {

    }

    private static class B extends ARC {

    }

    private static class C extends ARC {

    }

    private static class D extends ARC {

    }

    private static class E extends ARC {

    }

}

package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class SequenceIntPredicateTest {

    @Test
    public void testRetrigger() {
        SequenceIntPredicate pred = SequenceIntPredicate.matching(2).then(4).then(6);
        assertFalse(pred.test(0));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertTrue(pred.test(6));
        assertFalse(pred.test(0));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertTrue(pred.test(6));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertTrue(pred.test(6));
        assertFalse(pred.test(2));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertFalse(pred.test(4));
        assertFalse(pred.test(6));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertFalse(pred.test(7));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertTrue(pred.test(6));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertNotNull(pred.reset());
        assertFalse(pred.test(6));
        assertFalse(pred.test(2));
        assertFalse(pred.test(4));
        assertTrue(pred.test(6));
    }

    @Test
    public void testSequences() {
        SequenceIntPredicate pred = SequenceIntPredicate.matching(2).then(4).then(6);
        testOne(6, pred, 2, 4, 6, 8);
        testOne(6, pred, 0, 1, 2, 4, 6, 8);
        testOne(-1, pred, 0, 1, 2, 3, 4, 6, 8);
        testOne(6, pred, 2, 4, 6, 6, 6, 6);
        testOne(6, pred, 0, 5, 2, 4, 3, 2, 4, 6, 8, 2);
        testOne(6, pred, 4, 2, 4, 2, 2, 4, 2, 4, 2, 2, 4, 4, 2, 4, 6, 2, 4, 10);
        testOne(-1, pred, 0, 1, 2, 4, 2, 4, 2, 4, 4, 2);
        testOne(-1, pred, 6, 2, 4);

        pred.reset();
        assertFalse(pred.isPartiallyMatched());
        pred.test(2);
        assertTrue(pred.isPartiallyMatched());
        pred.test(3);
        assertFalse(pred.isPartiallyMatched());
        pred.test(2);
        pred.test(4);
        assertTrue(pred.isPartiallyMatched());
        assertTrue(pred.test(6));
        assertFalse(pred.isPartiallyMatched());

        pred = pred.then(1).then(1).then(1);
        assertFalse(pred.test(1));
        pred.test(2);
        assertTrue(pred.isPartiallyMatched());
        assertFalse(pred.test(4));
        assertTrue(pred.isPartiallyMatched());
        assertFalse(pred.test(6));
        assertTrue(pred.isPartiallyMatched());
        assertFalse(pred.test(1));
        assertTrue(pred.isPartiallyMatched());
        assertFalse(pred.test(1));
        assertTrue(pred.isPartiallyMatched());
        assertTrue(pred.test(1));
        assertFalse(pred.isPartiallyMatched());
    }

    private void testOne(int shouldPassAfterFirst, SequenceIntPredicate pred, int... vals) {
        pred = pred.copy();
        pred.reset();
        boolean matched = false;
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < vals.length; i++) {
            int v = vals[i];
            seen.add(v);
            boolean result = pred.test(v);
            if (v == shouldPassAfterFirst && !matched) {
                matched = true;
                assertTrue("Should have passed after " + seen + ": " + pred, result);
            } else {
                if (matched && result) {
                    fail("Should not have matched duplicate at " + seen + ": " + pred);
                }
                assertFalse("Should not have passed after " + seen + ": " + pred, result);
            }
        }
    }

}

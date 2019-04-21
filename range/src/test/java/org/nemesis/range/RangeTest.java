package org.nemesis.range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.nemesis.range.Range.RangeRelation;

/**
 *
 * @author Tim Boudreau
 */
public class RangeTest {

    @Test
    public void test() {
        Range a = Range.fixed(0, 10);
        Range b = Range.fixed(10, 10);
        System.out.println("A: " + a + " B: " + b);
        RangeRelation rel = a.relation(b);
        assertEquals(RangeRelation.BEFORE, rel);
    }

}

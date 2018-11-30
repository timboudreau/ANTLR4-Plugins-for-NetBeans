package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.util.BitSet;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.CulpritFinder.Cursors;

/**
 *
 * @author Tim Boudreau
 */
public class CulpritFinderTest {

    @Test
    public void testSomeMethod() {
        int max = 25;
        Cursors cursors = new Cursors(max);
        long count = (long) Math.pow(max, max);
        for (long i = 0; i < count; i++) {
            BitSet set = cursors.next();
            if (set == null) {
                System.out.println("DONE AT " + i);
                break;
            }
            System.out.println(i + ":\t\t" + tos(set, max));
        }
    }

    static String tos(BitSet set, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (set.get(i)) {
                sb.append('-');
            } else {
                sb.append('0');
            }
        }
        return sb.toString();
    }

}

package org.nemesis.data;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
public class HashableTest {

    @Test
    public void testSomeMethod() {
        Hasher h1 = new Hasher();
        Thing t1 = makeThing();
        h1.hashObject(t1);

        Thing t2 = makeThing();
        Hasher h2 = new Hasher();
        h2.hashObject(t2);

        assertEquals(t1.a, t2.a);
        assertEquals(t1.a.hashCode(), t2.a.hashCode());
        assertEquals(t1.b, t2.b);
        assertEquals(t1.b.hashCode(), t2.b.hashCode());
        assertEquals(h1.hash(), h2.hash());
    }

    private Thing makeThing() {
        return new Thing("Hey", HashableTest::doIt, HashableTest::doIt);
    }

    static class Thing implements Hashable {

        private String name;
        private Runnable a;
        private Runnable b;

        public Thing(String name, Runnable a, Runnable b) {
            this.name = name;
            this.a = a;
            this.b = b;
        }

        @Override
        public void hashInto(Hasher hasher) {
            hasher.writeString(name);
            hasher.hashObject(a);
            hasher.hashObject(b);
        }

    }

    static void doIt() {

    }

}

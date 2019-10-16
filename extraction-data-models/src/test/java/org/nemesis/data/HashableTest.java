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
package org.nemesis.data;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.nemesis.data.Hashable;
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

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

package org.nemesis.editor.utils;

import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TlTest {


    @Test
    public void fooTest() {

        ThreadLocal<Set<String>> loc = ThreadLocal.withInitial(HashSet::new);

        Set<String> curr = loc.get();

        int ifo = System.identityHashCode(curr);

        Set<String> next = loc.get();

        int ifo2 = System.identityHashCode(next);

        assertEquals(ifo, ifo2);

        loc.get().add("A");

        assertTrue(loc.get().contains("A"));

        next = null;
        curr = null;

        for (int i = 0; i < 100; i++) {
            System.gc();
            System.runFinalization();
        }

        assertTrue(loc.get().contains("A"));

        loc.remove();

        assertNotNull(loc.get());
        assertFalse(loc.get().contains("A"));

    }
}

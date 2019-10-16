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
package org.nemesis.misc.utils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TimedSoftReferenceTest {


    @Test
    public void testSomeMethod() throws InterruptedException {
        StringBuilder foo = new StringBuilder().append("first".toCharArray());
        WeakReference<StringBuilder> weakFoo = new WeakReference<>(foo);
        TimedSoftReference<StringBuilder> ref = new TimedSoftReference<>(foo, 75, TimeUnit.MILLISECONDS);

        assertSame(foo, ref.get());
        foo = null;
        System.gc();
        assertTrue(ref.isHardReferenced());
        assertFalse(ref.isEmpty());

        assertNotNull(ref.get());

        Thread.sleep(90);

        System.out.println("90ms " + ref.getIfPresent());
        assertFalse(ref.isEmpty());
        assertFalse(ref.isHardReferenced());
        assertNotNull(ref.get());

        assertTrue(ref.isHardReferenced());

        Thread.sleep(500);
        assertFalse(ref.isHardReferenced());

        for (int i = 0; i < 7; i++) {
            System.out.println("gc " + i);
            System.gc();
            System.runFinalization();
            Thread.sleep(75);
        }

        // need to control the vm's memory specs and garbage collector
        // choice to get something repeatable here.

//        assertTrue(ref.isEmpty());
//        assertNull(ref.getIfPresent());
//        assertNull("Huh?", weakFoo.get());
//
//        foo = new StringBuilder().append("second".toCharArray());
//        ref.set(foo);
//        assertTrue(ref.isHardReferenced());
//        assertFalse(ref.isEmpty());
//        assertNotNull(ref.get());
//
//        foo = null;
//
//        Thread.sleep(150);
//
//        for (int i = 0; i < 10; i++) {
//            System.gc();
//            System.runFinalization();;
//            Thread.sleep(5);
//        }
//
//        assertTrue(ref.isEmpty());

    }

}

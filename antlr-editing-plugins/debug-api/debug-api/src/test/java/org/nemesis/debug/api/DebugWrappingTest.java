/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.debug.api;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.debug.api.EmitterImpl.Waiter;
import static org.nemesis.debug.api.DebugTest.assertListsEquals;

/**
 *
 * @author Tim Boudreau
 */
public class DebugWrappingTest {

    private EmitterImpl em;
    static final List<EmitItem> EXPECTED_WRAPPED = Arrays.asList(
            new EmitItem(0, EmitItemType.ENTER, "AA:AA", "TheTest"),
            new EmitItem(0, EmitItemType.MESSAGE, "Boing!", "TheTest"),
            new EmitItem(1, EmitItemType.ENTER, "BB:BB", "TheTest"),
            new EmitItem(1, EmitItemType.MESSAGE, "Moof!", "TheTest"),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(0, EmitItemType.EXIT, "0", null),
            new EmitItem(0, EmitItemType.ENTER, "OtherThread:OtherThread", "FirstWrapped"),
            new EmitItem(1, EmitItemType.ENTER, "AA:AA", "FirstWrapped"),
            new EmitItem(2, EmitItemType.ENTER, "BB:BB", "FirstWrapped"),
            new EmitItem(2, EmitItemType.MESSAGE, "Poof!", "FirstWrapped"),
            new EmitItem(3, EmitItemType.ENTER, "CC:CC", "FirstWrapped"),
            new EmitItem(3, EmitItemType.MESSAGE, "Woof!", "FirstWrapped"),
            new EmitItem(3, EmitItemType.EXIT, "3", null),
            new EmitItem(2, EmitItemType.EXIT, "2", null),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(0, EmitItemType.EXIT, "0", null),
            new EmitItem(0, EmitItemType.ENTER, "AA:AA", "SecondWrapped"),
            new EmitItem(1, EmitItemType.ENTER, "BB:BB", "SecondWrapped"),
            new EmitItem(1, EmitItemType.MESSAGE, "Whinny!", "SecondWrapped"),
            new EmitItem(2, EmitItemType.ENTER, "DD:DD", "SecondWrapped"),
            new EmitItem(2, EmitItemType.MESSAGE, "Meow!", "SecondWrapped"),
            new EmitItem(2, EmitItemType.EXIT, "2", null),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(0, EmitItemType.EXIT, "0", null)
    );

    @Test
    public void testWrapped() throws Exception {
        Runnable[] r = new Runnable[2];
        boolean[] ab = new boolean[5];
        Waiter w = em.waiter();
        Thread.currentThread().setName("TheTest");
        Debug.run("AA", "AA", () -> {
            ab[0] = true;
            Debug.message("Boing!", "Boing!");
            Debug.run("BB", "BB", () -> {
                Debug.message("Moof!", "Moof!");
                r[0] = Debug.wrap(() -> {
                    ab[1] = true;
                    Debug.message("Poof!", "Poof!");
                    Debug.run("CC", "CC", () -> {
                        ab[2] = true;
                        Debug.message("Woof!", "Woof!");
                    });
                });
                r[1] = Debug.wrap(() -> {
                    ab[3] = true;
                    Debug.message("Whinny!", "Whinny!");
                    Debug.run("DD", "DD", () -> "DD", () -> {
                        Debug.message("Meow!", "Meow!");
                        ab[4] = true;
                    });
                });
            });
        });
        assertNotNull(r[0]);
        Thread t = new Thread(() -> {
            Debug.run("OtherThread", "OtherThread", r[0]);
        });
        t.setName("FirstWrapped");
        t.start();
        t.join();
        Thread t1 = new Thread(r[1]);
        t1.setName("SecondWrapped");
        t1.start();
        t1.join();
        w.await(4);
        Thread.sleep(1500);
        assertTrue(ab[0], "Outer was not run");
        assertTrue(ab[1], "Inner was not run on background thread");
        assertTrue(ab[2], "InnerInner was not run on background thread");
        assertTrue(ab[3], "Second outer was not run");
        assertTrue(ab[4], "Second inner was not run");
        assertListsEquals(EXPECTED_WRAPPED, em.received);
    }

    @BeforeEach
    public void setup() throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            System.out.println("warm up event queue");
        });
        Emitters.clear();
        Debug.factory = new SimpleContextFactory();
        em = new EmitterImpl();
        Emitters.subscribe(em);
    }

    @AfterEach()
    public void tearDown() {
        Emitters.unsubscribe(em);
        Emitters.clear();
        Debug.factory = null;
    }
}

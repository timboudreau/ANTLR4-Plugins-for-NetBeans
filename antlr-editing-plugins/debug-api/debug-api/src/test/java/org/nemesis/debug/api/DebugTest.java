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
package org.nemesis.debug.api;

import java.awt.EventQueue;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.debug.api.EmitterImpl.Waiter;
import org.nemesis.debug.spi.ContextFactory;

/**
 *
 * @author Tim Boudreau
 */
public class DebugTest {

    static final List<EmitItem> EXPECTED_ITEMS = Arrays.asList(
            new EmitItem(0, EmitItemType.ENTER, "HH:outer", "testIt"),
            new EmitItem(0, EmitItemType.SUCCESS, "entered outer", "testIt"),
            new EmitItem(1, EmitItemType.ENTER, "foo:inner", "testIt"),
            new EmitItem(1, EmitItemType.FAILURE, "entered inner", "testIt"),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(1, EmitItemType.ENTER, "x:inner2", "testIt"),
            new EmitItem(2, EmitItemType.ENTER, "innerInner:innerInner", "testIt"),
            new EmitItem(3, EmitItemType.ENTER, "y:moreInner", "testIt"),
            new EmitItem(4, EmitItemType.ENTER, "QQ:stillMoreInner", "testIt"),
            new EmitItem(4, EmitItemType.MESSAGE, "foo foo fru", "testIt"),
            new EmitItem(5, EmitItemType.ENTER, "z:wayTooInner", "testIt"),
            new EmitItem(5, EmitItemType.THROWN, "IllegalStateException", "testIt"),
            new EmitItem(5, EmitItemType.EXIT, "5", null),
            new EmitItem(4, EmitItemType.EXIT, "4", null),
            new EmitItem(3, EmitItemType.EXIT, "3", null),
            new EmitItem(2, EmitItemType.EXIT, "2", null),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(0, EmitItemType.EXIT, "0", null),
            new EmitItem(0, EmitItemType.ENTER, "throwSomething:throwSomething", "testIt"),
            new EmitItem(0, EmitItemType.THROWN, "IllegalArgumentException", "testIt"),
            new EmitItem(1, EmitItemType.ENTER, "x:reallyThrowIt", "testIt"),
            new EmitItem(1, EmitItemType.THROWN, "IllegalArgumentException", "testIt"),
            new EmitItem(1, EmitItemType.EXIT, "1", null),
            new EmitItem(0, EmitItemType.EXIT, "0", null)
    );

    EmitterImpl em;

    private void dump() {
        dump(em);
    }

    static void dump(EmitterImpl em) {
        System.out.println("static final List<EmitItem> EXPECTED_WRAPPED = Arrays.asList(");
        for (Iterator<EmitItem> it = em.received.iterator(); it.hasNext();) {
            EmitItem ei = it.next();
            System.out.println(ei.stringify() + (it.hasNext() ? "," : ""));
        }
        System.out.println(");");
    }

    @Test
    public void testWithEmitter() throws Exception {
        testIt();
    }

    @Test
    public void testWithoutEmitter() throws Exception {
        assertTrue(Emitters.unsubscribe(em));
        assertFalse(Emitters.hasSubscribers());
        ContextFactory f = Debug.factory;
        try {
            Debug.factory = null;
            testIt();
        } finally {
            Debug.factory = f;
            Emitters.subscribe(em);
        }
    }

    @Test
    public void testWithFactoryNoEmitter() throws Exception {
        assertTrue(Emitters.unsubscribe(em));
        assertFalse(Emitters.hasSubscribers());
        testIt();
    }

    private void testIt() throws Exception {
        Thread.currentThread().setName("testIt");
        Exception[] e1 = new Exception[1];
        List<String> order = new ArrayList<>();
        Waiter waiter = em.waiter();
        Debug.run("HH", "outer", () -> {
            order.add("a");
            Debug.success("a", () -> "entered outer");
            try {
                int val = Debug.runIntIO("foo", "inner", () -> {
                    order.add("b");
                    Debug.failure("b", "entered inner");
                    return 23;
                });
                assertEquals(val, 23);
                String s = Debug.runObjectThrowing("x", "inner2", () -> {
                    order.add("b1");
                    long ll = Debug.runLong("innerInner", "innerInner", () -> {
                        order.add("c");
                        Debug.run("y", "moreInner", () -> {
                            order.add("d");
                            Debug.run("QQ", "stillMoreInner", () -> {
                                order.add("e");
                                if (e1[0] == null) {
                                    e1[0] = new Exception("First run");
                                } else {
                                    e1[0].addSuppressed(new Exception("Run more than once"));
                                }
                                Debug.message("m", "foo foo fru");
                                Debug.run("z", "wayTooInner", () -> "woo hoo", () -> {
                                    order.add("f");
                                    Debug.thrown(new IllegalStateException("Hey there"));
                                });
                            });
                        });
                        return 53L;
                    });
                    assertEquals(53L, ll);
                    return "foo";
                });
                assertEquals("foo", s);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });

        if (e1[0] == null) {
            fail("D not run");
        } else {
            if (e1[0].getSuppressed().length > 0) {
                throw e1[0];
            }
        }
        if (Emitters.hasSubscribers()) {
            waiter.await(1);
//            em.await();
        }

        assertEquals(Arrays.asList("a", "b", "b1", "c", "d", "e", "f"), order);
        boolean[] runOnce = new boolean[2];
        try {
            Debug.run("throwSomething", "throwSomething", () -> {
                if (runOnce[0]) {
                    fail("throwSomething run more than once");
                }
                runOnce[0] = true;
                order.add("a2");
                Debug.run("x", "reallyThrowIt", () -> {
                    if (runOnce[1]) {
                        fail("reallyThrowIt run more than once");
                    }
                    runOnce[1] = true;
                    order.add("b2");
                    throw new IllegalArgumentException("foo");
                });
            });
            fail("Exception not propagated");
        } catch (IllegalArgumentException ex) {

        }
        if (Emitters.hasSubscribers()) {
//            waiter.await();
            em.await();
        }
        Thread.sleep(500);

        assertListsEquals(Arrays.asList("a", "b", "b1", "c", "d", "e", "f", "a2", "b2"), order);
        if (Emitters.hasSubscribers()) {
//            dump();
            assertListsEquals(EXPECTED_ITEMS, em.received);
        }
    }

    static void assertListsEquals(List<?> a, List<?> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            Object aa = a.get(i);
            Object bb = b.get(i);
            if (!Objects.equals(aa, bb)) {
                List<?> asub = a.subList(i, a.size());
                List<?> bsub = b.subList(i, b.size());
                assertEquals(asub, bsub, "Lists differ starting at " + i);
            }
        }
        assertEquals(a, b);
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

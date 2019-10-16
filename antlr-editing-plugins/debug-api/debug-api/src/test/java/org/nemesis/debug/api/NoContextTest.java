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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.debug.api.EmitterImpl.Waiter;
import static org.nemesis.debug.api.DebugTest.assertListsEquals;

/**
 *
 * @author Tim Boudreau
 */
public class NoContextTest {

    private EmitterImpl em;
    static final List<EmitItem> EXPECTED_EMITTED = Arrays.asList(
            new EmitItem(0, EmitItemType.ENTER, ":root", "main"),
            new EmitItem(0, EmitItemType.MESSAGE, "ouvre tubers", "main"),
            new EmitItem(0, EmitItemType.EXIT, "0", null),
            new EmitItem(0, EmitItemType.ENTER, ":root", "main"),
            new EmitItem(0, EmitItemType.SUCCESS, "uh oh", "main"),
            new EmitItem(0, EmitItemType.EXIT, "0", null),
            new EmitItem(0, EmitItemType.ENTER, ":root", "main"),
            new EmitItem(0, EmitItemType.THROWN, "IOException", "main"),
            new EmitItem(0, EmitItemType.EXIT, "0", null)
    );

    @Test
    public void test() throws InterruptedException {
        if (true) {
            return;
        }
        Waiter waiter = em.waiter();
        Debug.message("ouvre tubers", "ouvre tubers");
        Debug.success("uh oh", "uh oh");
        Debug.thrown(new IOException());
        waiter.await(3);
//        dump(em);
        Thread.sleep(500);
        assertListsEquals(EXPECTED_EMITTED, em.received);
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

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

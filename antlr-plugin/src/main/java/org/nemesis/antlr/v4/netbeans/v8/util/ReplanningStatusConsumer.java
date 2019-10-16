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
package org.nemesis.antlr.v4.netbeans.v8.util;

import java.util.Objects;
import java.util.function.Consumer;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Mutex;

/**
 * Wraps a Consumer&lt:String&gt; to replan status notifications
 * to the Swing event thread.
 *
 * @author Tim Boudreau
 */
public final class ReplanningStatusConsumer implements Consumer<String>, Runnable {

    volatile String data;
    private final Consumer<String> orig;
    private String lastNotified;

    public ReplanningStatusConsumer(Consumer<String> orig) {
        this.orig = orig;
    }

    /**
     * Wraps the passed consumer if non-null; otherwise creates a
     * consumer which routes messages to StatusDisplayer.getDefault().
     *
     * @param orig The original passed consumer or null
     * @return A consumer which either wraps the original or forwards
     * status messages to StatusDisplayer
     */
    public static Consumer<String> wrapConsumer(Consumer<String> orig) {
        if (orig == null) {
            return (st) -> {
                StatusDisplayer.getDefault().setStatusText(st);
            };
        } else {
            return new ReplanningStatusConsumer(orig);
        }
    }

    @Override
    public void accept(String t) {
        data = t;
        Mutex.EVENT.readAccess(this);
    }

    @Override
    public void run() {
        String d = data;
        if (d != null && !Objects.equals(lastNotified, d)) {
            lastNotified = d;
            orig.accept(d);
        }
    }
}

/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.function.Consumer;

/**
 * Given that Swing document render() methods are an everpresent source of
 * deadlocks, for cases where a render call can return asynchronously, this
 * class makes it easy to support hanging the work off of the tail of an already
 * running render job (note this means the work may not run in the origin
 * thread, which may have implications for lock order acquisition).
 *
 * @author Tim Boudreau
 */
public class AsyncRenderSupport {

    private final AtomicLinkedQueue<Runnable> queue = new AtomicLinkedQueue<>();
    private final AtomicLinkedQueue<Runnable> sink = new AtomicLinkedQueue<>();
    private volatile boolean inRender;
    private final Consumer<Runnable> superRender;

    public AsyncRenderSupport(Consumer<Runnable> superRender) {
        this.superRender = superRender;
    }

    public void renderNow(Runnable r) {
        boolean wasInRender = inRender;
        try {
            superRender.accept(() -> {
                inRender = true;
                try {
                    r.run();
                } finally {
                    onExitingRender();
                }
            });
        } finally {
            if (!wasInRender) {
                onExitingRender();
            }
            inRender = wasInRender;
        }
    }

    public void onExitingRender() {
        while (!queue.isEmpty()) {
            queue.drainTo(sink);
            boolean wasInRender = inRender;
            inRender = true;
            try {
                sink.drain(runnable -> {
                    try {
                        superRender.accept(runnable);
                    } catch (Exception | Error ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });
            } finally {
                inRender = wasInRender;
            }
        }
    }

    public void renderWhenPossible(Runnable r) {
        if (inRender) {
            queue.offer(r);
        } else {
            renderNow(r);
        }
    }
}

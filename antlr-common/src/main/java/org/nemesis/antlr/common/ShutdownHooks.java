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
package org.nemesis.antlr.common;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allows for adding shutdown hooks to be run on normal VM exit.S
 *
 * @author Tim Boudreau
 */
public final class ShutdownHooks {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static volatile boolean shuttingDown;
    private static final ThrowingRunnable onShutdown = ThrowingRunnable.oneShot(true).andAlwaysFirst(() -> {
        shuttingDown = true;
    });

    public static void addWeakRunnable(Runnable run) {
        add(new WeakRunnable(run));
    }

    public static void addWeak(ThrowingRunnable run) {
        add(new WeakThrowingRunnable(run));
    }

    public static void addRunnable(Runnable run) {
        onShutdown.andAlways(run::run);
        checkInit();
    }

    public static void add(ThrowingRunnable run) {
        onShutdown.andAlways(run);
        checkInit();
    }

    private static void checkInit() {
        if (INITIALIZED.compareAndSet(false, true)) {
            init();
        }
    }

    private static void init() {
        Thread thread = new Thread(onShutdown.toRunnable());
        thread.setName("antlr-general-shutdown");
        Runtime.getRuntime().addShutdownHook(thread);
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    static final class WeakThrowingRunnable implements ThrowingRunnable {

        private final Reference<ThrowingRunnable> ref;

        WeakThrowingRunnable(ThrowingRunnable r) {
            ref = new WeakReference<>(r);
        }

        @Override
        public void run() throws Exception {
            ThrowingRunnable delegate = ref.get();
            if (delegate != null) {
                delegate.run();
            }
        }
    }

    static final class WeakRunnable implements ThrowingRunnable {

        private final Reference<Runnable> ref;

        WeakRunnable(Runnable r) {
            ref = new WeakReference<>(r);
        }

        @Override
        public void run() throws Exception {
            Runnable delegate = ref.get();
            if (delegate != null) {
                delegate.run();
            }
        }
    }
}

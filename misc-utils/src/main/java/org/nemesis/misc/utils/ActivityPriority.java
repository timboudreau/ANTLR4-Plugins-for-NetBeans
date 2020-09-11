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

import com.mastfrog.function.throwing.ThrowingBooleanSupplier;
import com.mastfrog.function.throwing.ThrowingIntSupplier;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.function.throwing.io.IOBooleanSupplier;
import com.mastfrog.function.throwing.io.IOIntSupplier;
import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOSupplier;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Dynamic priorities that allow calls to operations that can optionally be
 * asynchronous to dynamically decide based on contention whether a call can be
 * deferred.
 *
 * @author Tim Boudreau
 */
public enum ActivityPriority {
    DEFERRABLE,
    UNKNOWN,
    REALTIME;

    private static final ThreadLocal<ActivityPriority> CURRENT = ThreadLocal.withInitial(() -> UNKNOWN);

    public static ActivityPriority get() {
        return CURRENT.get();
    }

    public void wrap(Runnable run) {
        ActivityPriority old = enter();
        try {
            run.run();
        } finally {
            exitTo(old);
        }
    }

    public void wrapThrowing(ThrowingRunnable run) throws Exception {
        ActivityPriority old = enter();
        try {
            run.run();
        } finally {
            exitTo(old);
        }
    }

    public void wrapIO(IORunnable run) throws Exception {
        ActivityPriority old = enter();
        try {
            run.run();
        } finally {
            exitTo(old);
        }
    }

    public <T> T wrap(Supplier<T> run) {
        ActivityPriority old = enter();
        try {
            return run.get();
        } finally {
            exitTo(old);
        }
    }

    public <T> T wrapThrowing(ThrowingSupplier<T> run) throws Exception {
        ActivityPriority old = enter();
        try {
            return run.get();
        } finally {
            exitTo(old);
        }
    }

    public <T> T wrapIO(IOSupplier<T> run) throws IOException {
        ActivityPriority old = enter();
        try {
            return run.get();
        } finally {
            exitTo(old);
        }
    }

    public int wrapIntThrowing(ThrowingIntSupplier run) throws Exception {
        ActivityPriority old = enter();
        try {
            return run.getAsInt();
        } finally {
            exitTo(old);
        }
    }

    public int wrapIntIO(IOIntSupplier run) throws IOException {
        ActivityPriority old = enter();
        try {
            return run.getAsInt();
        } finally {
            exitTo(old);
        }
    }

    public int wrapInt(IntSupplier run) {
        ActivityPriority old = enter();
        try {
            return run.getAsInt();
        } finally {
            exitTo(old);
        }
    }

    public boolean wrapBooleanThrowing(ThrowingBooleanSupplier run) throws Exception {
        ActivityPriority old = enter();
        try {
            return run.getAsBoolean();
        } finally {
            exitTo(old);
        }
    }

    public boolean wrapBooleanIO(IOBooleanSupplier run) throws IOException {
        ActivityPriority old = enter();
        try {
            return run.getAsBoolean();
        } finally {
            exitTo(old);
        }
    }

    public boolean wrapBoolean(BooleanSupplier run) {
        ActivityPriority old = enter();
        try {
            return run.getAsBoolean();
        } finally {
            exitTo(old);
        }
    }

    private ActivityPriority enter() {
        ActivityPriority old = CURRENT.get();
        if (old == this) {
            return null;
        }
        switch (old) {
            case REALTIME:
                return null;
        }
        CURRENT.set(this);
        return old;
    }

    private void exitTo(ActivityPriority old) {
        if (old != null) {
            CURRENT.set(old);
        }
    }

    public boolean isDeferrable() {
        return this == DEFERRABLE;
    }

    public boolean isRealtime() {
        return this == UNKNOWN || this == REALTIME;
    }
}

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

import com.mastfrog.function.throwing.ThrowingBooleanSupplier;
import com.mastfrog.function.throwing.ThrowingIntSupplier;
import com.mastfrog.function.throwing.ThrowingLongSupplier;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.function.throwing.io.IOBooleanSupplier;
import com.mastfrog.function.throwing.io.IOIntSupplier;
import com.mastfrog.function.throwing.io.IOLongSupplier;
import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOSupplier;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A macro-level stack frame for providing a UI which shows what is happening.
 *
 * @author Tim Boudreau
 */
public abstract class Context {

    final Info info;

    protected Context(Object owner, String action, Supplier<String> details) {
        info = new Info(owner, action, details);
    }

    Context() {
        info = new Info();
    }

    public abstract boolean isRoot();

    public String toString() {
        return info.toString();
    }

    static class Info {

        final String ownerType;
        final String ownerTypeSimple;
        final String ownerString;
        final String action;
        final Supplier<String> details;
        final WeakReference<Object> ownerRef;
        final long creationThreadId;
        final String creationThreadString;

        Info(Object owner, String action, Supplier<String> details) {
            this.action = action == null ? "none" : action;
            this.ownerType = owner == null ? "none" : owner.getClass().getName();
            this.ownerTypeSimple = owner == null ? "none" : owner.getClass().getSimpleName();
            this.ownerString = owner == null ? "" : owner.toString();
            this.details = details;
            ownerRef = owner == null ? null : new WeakReference<>(owner);
            creationThreadString = Thread.currentThread().toString();
            creationThreadId = Thread.currentThread().getId();
        }

        Info() {
            this.ownerType = "none";
            this.ownerTypeSimple = "none";
            this.ownerString = "none";
            this.action = "none";
            this.details = () -> "none";
            this.ownerRef = null;
            creationThreadId = 0;
            creationThreadString = "";
        }

        Object owner() {
            return ownerRef == null ? null : ownerRef.get();
        }

        @Override
        public String toString() {
            return "Info{" + "ownerType=" + ownerType + ", ownerTypeSimple=" + ownerTypeSimple + ", ownerString=" + ownerString + ", args=" + details.get() + ", action=" + action + ", ownerRef=" + ownerRef + ", creationThreadId=" + creationThreadId + ", creationThreadString=" + creationThreadString + '}';
        }

    }

    public abstract void run(Runnable run);

    public abstract void runThrowing(ThrowingRunnable run) throws Exception;

    public abstract void runIO(IORunnable run) throws IOException;

    public abstract int runInt(IntSupplier supp);

    public abstract int runIntThrowing(ThrowingIntSupplier supp) throws Exception;

    public abstract int runIntIO(IOIntSupplier supp) throws IOException;

    public abstract long runLong(LongSupplier supp);

    public abstract long runLongThrowing(ThrowingLongSupplier supp) throws Exception;

    public abstract long runLongIO(IOLongSupplier supp) throws IOException;

    public abstract boolean runBoolean(BooleanSupplier supp);

    public abstract boolean runBooleanThrowing(ThrowingBooleanSupplier supp) throws Exception;

    public abstract boolean runBooleanIO(IOBooleanSupplier supp) throws IOException;

    public abstract <T> T runObject(Supplier<T> obj);

    public abstract <T> T runObjectThrowing(ThrowingSupplier<T> obj) throws Exception;

    public abstract <T> T runObjectIO(IOSupplier<T> obj) throws IOException;
}

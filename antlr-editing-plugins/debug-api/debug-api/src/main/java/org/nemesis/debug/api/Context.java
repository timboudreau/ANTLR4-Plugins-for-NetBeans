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

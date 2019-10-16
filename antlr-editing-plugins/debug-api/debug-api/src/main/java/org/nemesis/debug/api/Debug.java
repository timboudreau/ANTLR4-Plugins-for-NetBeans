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
import java.awt.EventQueue;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.nemesis.debug.api.Record.CreationRecord;
import org.nemesis.debug.api.Record.ThrownRecord;
import org.nemesis.debug.spi.ContextFactory;
import org.nemesis.debug.spi.Emitter;
import org.openide.util.Lookup;

/**
 * Allows for running of code in nested "contexts" - basically macro-level
 * stack-frames which can have messages associated with them. Near-zero overhead
 * if no module installs a ContextFactory to provide a UI to see what's going
 * on, but useful for debugging sequences of events, since these modules rely on
 * lots of nested calls to parse things that trigger calls to reparse other
 * things and so on. Simply call one of the run* methods to open a context, and
 * the message methods to attach messages. Note that objects referenced from the
 * lambdas passed for creating string descriptions may live much longer than
 * they otherwise would, since a UI will hold onto them for display until
 * cleared.
 *
 * @author Tim Boudreau
 */
public final class Debug {

    static volatile ContextFactory factory;
    private static final ThreadLocal<ContextEntry> CURRENT = new ThreadLocal<>();
    private static boolean isDefault;
    private static final Supplier<String> NO_DETAILS = () -> "";

    public static void run(Object owner, String action, Runnable run) {
        run(owner, action, NO_DETAILS, run);
    }

    public static void run(Object owner, String action, Supplier<String> details, Runnable run) {
        if (isDefault) {
            run.run();
            return;
        }
        context(owner, action, details).run(run);
    }

    public static void runThrowing(Object owner, String action, ThrowingRunnable run) throws Exception {
        runThrowing(owner, action, NO_DETAILS, run);
    }

    public static void runThrowing(Object owner, String action, Supplier<String> details, ThrowingRunnable run) throws Exception {
        if (isDefault) {
            run.run();
            return;
        }
        context(owner, action, details).runThrowing(run);
    }

    public static void runIO(Object owner, String action, IORunnable run) throws IOException {
        runIO(owner, action, NO_DETAILS, run);
    }

    public static void runIO(Object owner, String action, Supplier<String> details, IORunnable run) throws IOException {
        if (isDefault) {
            run.run();
            return;
        }
        context(owner, action, details).runIO(run);
    }

    public static int runInt(Object owner, String action, IntSupplier supp) {
        return runInt(owner, action, NO_DETAILS, supp);
    }

    public static int runInt(Object owner, String action, Supplier<String> details, IntSupplier supp) {
        if (isDefault) {
            return supp.getAsInt();
        }
        return context(owner, action, details).runInt(supp);
    }

    public static int runIntThrowing(Object owner, String action, ThrowingIntSupplier supp) throws Exception {
        return runIntThrowing(owner, action, NO_DETAILS, supp);
    }

    public static int runIntThrowing(Object owner, String action, Supplier<String> details, ThrowingIntSupplier supp) throws Exception {
        if (isDefault) {
            return supp.getAsInt();
        }
        return context(owner, action, details).runIntThrowing(supp);
    }

    public static int runIntIO(Object owner, String action, IOIntSupplier supp) throws IOException {
        return runIntIO(owner, action, NO_DETAILS, supp);
    }

    public static int runIntIO(Object owner, String action, Supplier<String> details, IOIntSupplier supp) throws IOException {
        if (isDefault) {
            return supp.getAsInt();
        }
        return context(owner, action, details).runIntIO(supp);
    }

    public static long runLong(Object owner, String action, LongSupplier supp) {
        return runLong(owner, action, NO_DETAILS, supp);
    }

    public static long runLong(Object owner, String action, Supplier<String> details, LongSupplier supp) {
        if (isDefault) {
            return supp.getAsLong();
        }
        return context(owner, action, details).runLong(supp);
    }

    public static long runLongThrowing(Object owner, String action, ThrowingLongSupplier supp) throws Exception {
        return runLongThrowing(owner, action, NO_DETAILS, supp);
    }

    public static long runLongThrowing(Object owner, String action, Supplier<String> details, ThrowingLongSupplier supp) throws Exception {
        if (isDefault) {
            return supp.getAsLong();
        }
        return context(owner, action, details).runLongThrowing(supp);
    }

    public static long runLongIO(Object owner, String action, IOLongSupplier supp) throws IOException {
        return runLongIO(owner, action, NO_DETAILS, supp);
    }

    public static long runLongIO(Object owner, String action, Supplier<String> details, IOLongSupplier supp) throws IOException {
        if (isDefault) {
            return supp.getAsLong();
        }
        return context(owner, action, details).runLongIO(supp);
    }

    public static boolean runBoolean(Object owner, String action, BooleanSupplier supp) {
        return runBoolean(owner, action, NO_DETAILS, supp);
    }

    public static boolean runBoolean(Object owner, String action, Supplier<String> details, BooleanSupplier supp) {
        if (isDefault) {
            return supp.getAsBoolean();
        }
        return context(owner, action, details).runBoolean(supp);
    }

    public static boolean runBooleanThrowing(Object owner, String action, ThrowingBooleanSupplier supp) throws Exception {
        return runBooleanThrowing(owner, action, NO_DETAILS, supp);
    }

    public static boolean runBooleanThrowing(Object owner, String action, Supplier<String> details, ThrowingBooleanSupplier supp) throws Exception {
        if (isDefault) {
            return supp.getAsBoolean();
        }
        return context(owner, action, details).runBooleanThrowing(supp);
    }

    public static boolean runBooleanIO(Object owner, String action, IOBooleanSupplier supp) throws IOException {
        return runBooleanIO(owner, action, NO_DETAILS, supp);
    }

    public static boolean runBooleanIO(Object owner, String action, Supplier<String> details, IOBooleanSupplier supp) throws IOException {
        if (isDefault) {
            return supp.getAsBoolean();
        }
        return context(owner, action, details).runBooleanIO(supp);
    }

    public static <T> T runObject(Object owner, String action, Supplier<T> obj) {
        return runObject(owner, action, NO_DETAILS, obj);
    }

    public static <T> T runObject(Object owner, String action, Supplier<String> details, Supplier<T> obj) {
        if (isDefault) {
            return obj.get();
        }
        Context cx = context(owner, action, details);
        return cx.runObject(obj);
    }

    public static <T> T runObjectThrowing(Object owner, String action, ThrowingSupplier<T> obj) throws Exception {
        return runObjectThrowing(owner, action, NO_DETAILS, obj);
    }

    public static <T> T runObjectThrowing(Object owner, String action, Supplier<String> details, ThrowingSupplier<T> obj) throws Exception {
        if (isDefault) {
            return obj.get();
        }
        return context(owner, action, details).runObjectThrowing(obj);
    }

    public static <T> T runObjectIO(Object owner, String action, IOSupplier<T> obj) throws IOException {
        return runObjectIO(owner, action, NO_DETAILS, obj);
    }

    public static <T> T runObjectIO(Object owner, String action, Supplier<String> details, IOSupplier<T> obj) throws IOException {
        if (isDefault) {
            return obj.get();
        }
        return context(owner, action, details).runObjectIO(obj);
    }

    public static void message(String heading, String msg, Object... args) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.MessageRecord(heading, () -> {
            return MessageFormat.format(msg, args);
        }));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void message(String heading) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.MessageRecord(heading, NO_DETAILS));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void message(String heading, Supplier<String> msg) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.MessageRecord(heading, msg));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void success(String heading, String msg, Object... args) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.SuccessFailureMessage(heading, true, () -> {
            return MessageFormat.format(msg, args);
        }));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void success(String heading, Supplier<String> msg) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.SuccessFailureMessage(heading, true, msg));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void failure(String heading, String msg, Object... args) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.SuccessFailureMessage(heading, false, () -> {
            return MessageFormat.format(msg, args);
        }));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void failure(String heading, Supplier<String> msg) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.records.add(new Record.SuccessFailureMessage(heading, false, msg));
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static void thrown(Throwable t) {
        if (isDefault) {
            return;
        }
        ContextEntry ctx = currentContext();
        ctx.maybeAddThrownRecord(t);
        if (ctx.ctx.isRoot()) {
            emit(ctx);
        }
    }

    public static Runnable wrap(Runnable run) {
        ContextEntry en = CURRENT.get();
        return () -> {
            List<ContextEntry> all;
            if (en != null) {
                all = en.chain();
            } else {
                all = Collections.emptyList();
            }
            for (ContextEntry e : all) {
                push(e.ctx);
            }
            try {
                run.run();
            } finally {
                for (ContextEntry e : all) {
                    pop(e.ctx);
                }
            }
        };
    }

    /**
     * Determine if any ContextFactory is installed, for example, before calling
     * toString() on something that could produce a large amount of output that
     * should not be done unless it is likely to be used. Needed for lexer
     * CharSequences, which throw exceptions when touched outside of the scope
     * where they were created - yet we want to show the text from them.
     *
     * @return True if there is something installed which will display things.
     */
    public static boolean isActive() {
        if (factory == null) {
            return factory() == NoOpFactory.INSTANCE;
        }
        return !isDefault;
    }

    private static ContextFactory findFactory() {
        ContextFactory found = Lookup.getDefault().lookup(ContextFactory.class);
        if (found != null) {
            isDefault = false;
            return found;
        }
        isDefault = true;
        return NoOpFactory.INSTANCE;
    }

    private static ContextFactory factory() {
        ContextFactory f = factory;
        if (f == null) {
            synchronized (Debug.class) {
                f = factory;
                if (f == null) {
                    f = factory = findFactory();
                }
            }
        }
        return f;
    }

    static Context context(Object owner, String action, Supplier<String> details) {
        if (isDefault) {
            return NoOpFactory.INSTANCE;
        }
        ContextFactory fact = factory();
        Context ctx = fact.newContext(owner, action, details);
        if (ctx instanceof AbstractContext) {
            ((AbstractContext) ctx).configure(Debug::push, Debug::pop, Debug::caught);
        }
        return ctx;
    }

    static void caught(Context ctx, Throwable thrown) {
        if (isDefault) {
            return;
        }
        ContextEntry en = CURRENT.get();
        if (en == null) {
            return;
        }
        en.records.add(new ThrownRecord(thrown));
    }

    static void push(Context ctx) {
        ContextEntry en = CURRENT.get();
        ContextEntry child = new ContextEntry(en, ctx);
        if (en != null) {
            en.children.add(child);
        }
        CURRENT.set(child);
    }

    static void pop(Context ctx) {
        ContextEntry en = CURRENT.get();
        if (en != null && en.parent != null/* && !en.parent.ctx.isRoot() */) {
            CURRENT.set(en.parent);
        } else if (en != null) {
            emit(en);
        }
        if (en != null && en.parent == null) {
            CURRENT.set(null);
        } else if (en == null) {
            CURRENT.set(null);
        }
    }

    static ContextEntry currentContext() {
        ContextEntry entry = CURRENT.get();
        if (entry == null) {
            entry = new ContextEntry(null, factory().root());
        }
        return entry;
    }

    private static void emit(ContextEntry en) {
        Emitter em = Emitters.emitter();
        if (em != null) {
            EventQueue.invokeLater(() -> {
                en.emit(em);
            });
        }
    }

    private static final class ContextEntry {

        private final ContextEntry parent;

        private final Context ctx;
        private final List<ContextEntry> children = new CopyOnWriteArrayList<>();
        private final List<Record> records = new CopyOnWriteArrayList<>();

        public ContextEntry(ContextEntry parent, Context ctx) {
            this.parent = parent;
            this.ctx = ctx;
            records.add(new Record.CreationRecord(ctx));
        }

        void maybeAddThrownRecord(Throwable thrown) {
            if (CURRENT.get() == this || CURRENT.get() == null) {
                records.add(new Record.ThrownRecord(thrown));
            }
        }

        public List<ContextEntry> chain() {
            LinkedList<ContextEntry> e = new LinkedList<>();
            ContextEntry curr = this;
            e.push(curr);
            while (curr.parent != null) {
                curr = curr.parent;
                e.push(curr);
            }
            return e;
        }

        public ContextEntry copy() {
            ContextEntry result = new ContextEntry(parent == null ? null : parent.copy(), ctx);
            CreationRecord r = (CreationRecord) records.get(0);
            result.records.set(0, r.copyOnCurrentThread());
            return result;
        }

        @Override
        public String toString() {
            Record.CreationRecord r = (Record.CreationRecord) records.get(0);
            return r.action() + (parent == null ? "" : (" <- " + parent.toString()));
        }

        void emit(Emitter emitter) {
            emit(0, emitter);
        }

        void emit(int depth, Emitter emitter) {
            for (Record record : records) {
                record.emitTo(depth, emitter);
            }
            for (ContextEntry child : children) {
                child.emit(depth + 1, emitter);
            }
            emitter.exitContext(depth);
        }
    }

    static final class NoOpFactory extends Context implements ContextFactory {

        static final NoOpFactory INSTANCE = new NoOpFactory();

        @Override
        public Context newContext(Object owner, String action, Supplier<String> details) {
            return this;
        }

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public Context root() {
            return this;
        }

        @Override
        public void run(Runnable run) {
            run.run();
        }

        @Override
        public void runThrowing(ThrowingRunnable run) throws Exception {
            run.run();
        }

        @Override
        public void runIO(IORunnable run) throws IOException {
            run.run();
        }

        @Override
        public int runInt(IntSupplier supp) {
            return supp.getAsInt();
        }

        @Override
        public int runIntThrowing(ThrowingIntSupplier supp) throws Exception {
            return supp.getAsInt();
        }

        @Override
        public int runIntIO(IOIntSupplier supp) throws IOException {
            return supp.getAsInt();
        }

        @Override
        public long runLong(LongSupplier supp) {
            return supp.getAsLong();
        }

        @Override
        public long runLongThrowing(ThrowingLongSupplier supp) throws Exception {
            return supp.getAsLong();
        }

        @Override
        public long runLongIO(IOLongSupplier supp) throws IOException {
            return supp.getAsLong();
        }

        @Override
        public boolean runBoolean(BooleanSupplier supp) {
            return supp.getAsBoolean();
        }

        @Override
        public boolean runBooleanThrowing(ThrowingBooleanSupplier supp) throws Exception {
            return supp.getAsBoolean();
        }

        @Override
        public boolean runBooleanIO(IOBooleanSupplier supp) throws IOException {
            return supp.getAsBoolean();
        }

        @Override
        public <T> T runObject(Supplier<T> obj) {
            return obj.get();
        }

        @Override
        public <T> T runObjectThrowing(ThrowingSupplier<T> obj) throws Exception {
            return obj.get();
        }

        @Override
        public <T> T runObjectIO(IOSupplier<T> obj) throws IOException {
            return obj.get();
        }
    }
}

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
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public class AbstractContext extends Context {

    private Consumer<Context> onEnter;
    private Consumer<Context> onExit;
    private BiConsumer<Context, Throwable> catcher;

    public AbstractContext(Object owner, String action, Supplier<String> details) {
        super(owner, action, details);
    }

    public boolean isRoot() {
        return false;
    }

    void configure(Consumer<Context> onEnter, Consumer<Context> onExit, BiConsumer<Context, Throwable> catcher) {
        this.onEnter = onEnter;
        this.onExit = onExit;
        this.catcher = catcher;
    }

    @Override
    public void run(Runnable run) {
        onEnter.accept(this);
        try {
            run.run();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public void runThrowing(ThrowingRunnable run) throws Exception {
        onEnter.accept(this);
        try {
            run.run();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public void runIO(IORunnable run) throws IOException {
        onEnter.accept(this);
        try {
            run.run();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public int runInt(IntSupplier supp) {
        onEnter.accept(this);
        try {
            return supp.getAsInt();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public int runIntThrowing(ThrowingIntSupplier supp) throws Exception {
        onEnter.accept(this);
        try {
            return supp.getAsInt();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public int runIntIO(IOIntSupplier supp) throws IOException {
        onEnter.accept(this);
        try {
            return supp.getAsInt();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public long runLong(LongSupplier supp) {
        onEnter.accept(this);
        try {
            return supp.getAsLong();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public long runLongThrowing(ThrowingLongSupplier supp) throws Exception {
        onEnter.accept(this);
        try {
            return supp.getAsLong();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public long runLongIO(IOLongSupplier supp) throws IOException {
        onEnter.accept(this);
        try {
            return supp.getAsLong();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public boolean runBoolean(BooleanSupplier supp) {
        onEnter.accept(this);
        try {
            return supp.getAsBoolean();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public boolean runBooleanThrowing(ThrowingBooleanSupplier supp) throws Exception {
        onEnter.accept(this);
        try {
            return supp.getAsBoolean();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public boolean runBooleanIO(IOBooleanSupplier supp) throws IOException {
        onEnter.accept(this);
        try {
            return supp.getAsBoolean();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public <T> T runObject(Supplier<T> obj) {
        onEnter.accept(this);
        try {
            return obj.get();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public <T> T runObjectThrowing(ThrowingSupplier<T> obj) throws Exception {
        onEnter.accept(this);
        try {
            return obj.get();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }

    @Override
    public <T> T runObjectIO(IOSupplier<T> obj) throws IOException {
        onEnter.accept(this);
        try {
            return obj.get();
        } catch (RuntimeException | Error rt) {
            catcher.accept(this, rt);
            throw rt;
        } catch (Exception ex) {
            catcher.accept(this, ex);
            return Exceptions.chuck(ex);
        } finally {
            onExit.accept(this);
        }
    }
}

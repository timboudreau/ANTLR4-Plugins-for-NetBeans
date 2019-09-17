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

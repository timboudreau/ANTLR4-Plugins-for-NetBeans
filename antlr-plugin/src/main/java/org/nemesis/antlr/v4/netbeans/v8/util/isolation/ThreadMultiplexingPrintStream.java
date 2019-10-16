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
package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Wraps the original System.out / err and delegates to them unless called on
 * the thread this object was created on; that output is shuttled to the wrapped
 * output stream for later parsing.
 */
class ThreadMultiplexingPrintStream extends PrintStream {

    private final PrintStream origOut;
    private final Thread creatingThread;

    public ThreadMultiplexingPrintStream(OutputStream out, PrintStream orig) {
        super(out);
        creatingThread = Thread.currentThread();
        this.origOut = orig;
    }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) {
        checkThread(() -> {
            origOut.append(csq, start, end);
        }, () -> {
            super.append(csq, start, end);
        });
        return this;
    }

    @Override
    public PrintStream append(CharSequence csq) {
        checkThread(() -> {
            origOut.append(csq);
        }, () -> {
            super.append(csq);
        });
        return this;
    }

    @Override
    public PrintStream format(Locale l, String format, Object... args) {
        checkThread(() -> {
            origOut.format(l, format, args);
        }, () -> {
            super.format(l, format, args);
        });
        return this;
    }

    @Override
    public PrintStream format(String format, Object... args) {
        checkThread(() -> {
            origOut.format(format, args);
        }, () -> {
            super.format(format, args);
        });
        return this;
    }

    @Override
    public PrintStream printf(Locale l, String format, Object... args) {
        checkThread(() -> {
            origOut.printf(l, format, args);
        }, () -> {
            super.printf(l, format, args);
        });
        return this;
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        checkThread(() -> {
            origOut.printf(format, args);
        }, () -> {
            super.printf(format, args);
        });
        return this;
    }

    @Override
    public void println(Object x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(String x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(char[] x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(double x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(float x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(long x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(int x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(char x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println(boolean x) {
        checkThread(() -> {
            origOut.println(x);
        }, () -> {
            super.println(x);
        });
    }

    @Override
    public void println() {
        checkThread(() -> {
            origOut.println();
        }, () -> {
            super.println();
        });
    }

    @Override
    public void print(Object obj) {
        checkThread(() -> {
            origOut.print(obj);
        }, () -> {
            super.print(obj);
        });
    }

    @Override
    public void print(String s) {
        checkThread(() -> {
            origOut.print(s);
        }, () -> {
            super.print(s);
        });
    }

    @Override
    public void print(char[] s) {
        checkThread(() -> {
            origOut.print(s);
        }, () -> {
            super.print(s);
        });
    }

    @Override
    public void print(double d) {
        checkThread(() -> {
            origOut.print(d);
        }, () -> {
            super.print(d);
        });
    }

    @Override
    public void print(float f) {
        checkThread(() -> {
            origOut.print(f);
        }, () -> {
            super.print(f);
        });
    }

    @Override
    public void print(long l) {
        checkThread(() -> {
            origOut.print(l);
        }, () -> {
            super.print(l);
        });
    }

    @Override
    public void print(int i) {
        checkThread(() -> {
            origOut.print(i);
        }, () -> {
            super.print(i);
        });
    }

    @Override
    public void print(char c) {
        checkThread(() -> {
            origOut.print(c);
        }, () -> {
            super.print(c);
        });
    }

    @Override
    public void print(boolean b) {
        checkThread(() -> {
            origOut.print(b);
        }, () -> {
            super.print(b);
        });
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        checkThread(() -> {
            origOut.write(buf, off, len);
        }, () -> {
            super.write(buf, off, len);
        });
    }

    @Override
    public void write(int b) {
        checkThread(() -> {
            origOut.write(b);
        }, () -> {
            super.write(b);
        });
    }

    @Override
    protected void clearError() {
        super.clearError();
    }

    @Override
    protected void setError() {
        super.setError();
    }

    @Override
    public boolean checkError() {
        return super.checkError();
    }

    void superClose() {
        superFlush();
        super.close();
    }

    void superFlush() {
        super.flush();
        origOut.flush();
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void flush() {
        super.flush();
        origOut.flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        checkThreadThrow(() -> {
            origOut.write(b);
        }, () -> {
            super.write(b);
        });
    }

    private boolean isCaptureOutputThread() {
        return Thread.currentThread() != creatingThread;
    }

    void checkThread(Runnable reroute, Runnable std) {
        if (isCaptureOutputThread()) {
            reroute.run();
        } else {
            std.run();
        }
    }

    void checkThreadThrow(Thrower reroute, Thrower std) throws IOException {
        if (isCaptureOutputThread()) {
            reroute.run();
        } else {
            std.run();
        }
    }

    <T> T checkThreadAndReturn(Supplier<T> reroute, Supplier<T> std) {
        if (isCaptureOutputThread()) {
            return reroute.get();
        } else {
            return std.get();
        }
    }

    @Override
    public PrintStream append(char c) {
        checkThread(() -> {
            origOut.append(c);
        }, () -> {
            origOut.append(c);
        });
        return this;
    }

    interface Thrower {

        public void run() throws IOException;
    }

}

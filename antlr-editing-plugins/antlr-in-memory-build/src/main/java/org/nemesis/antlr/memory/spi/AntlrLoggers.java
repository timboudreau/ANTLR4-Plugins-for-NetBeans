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
package org.nemesis.antlr.memory.spi;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AntlrLoggers {

    private static AntlrLoggers INSTANCE;

    public static AntlrLoggers getDefault() {
        if (INSTANCE == null) {
            INSTANCE = Lookup.getDefault().lookup(AntlrLoggers.class);
            if (INSTANCE == null) {
                INSTANCE = new DummyLoggers();
            }
        }
        return INSTANCE;
    }

    public abstract PrintStream forPath(Path path);

    protected static PrintStream nullPrintStream() {
        return DummyLoggers.NO_OP_STREAM;
    }

    private static final class DummyLoggers extends AntlrLoggers {

        private static final PrintStream NO_OP_STREAM = new NullPrintStream();

        @Override
        public PrintStream forPath(Path path) {
            return NO_OP_STREAM;
        }

        static final class NullPrintStream extends PrintStream {

            public NullPrintStream() {
                super(new NullOutputStream());
            }

            @Override
            public PrintStream append(char c) {
                return this;
            }

            @Override
            public PrintStream append(CharSequence csq, int start, int end) {
                return this;
            }

            @Override
            public PrintStream append(CharSequence csq) {
                return this;
            }

            @Override
            public PrintStream format(Locale l, String format, Object... args) {
                return this;
            }

            @Override
            public PrintStream format(String format, Object... args) {
                return this;
            }

            @Override
            public PrintStream printf(Locale l, String format, Object... args) {
                return this;
            }

            @Override
            public PrintStream printf(String format, Object... args) {
                return this;
            }

            @Override
            public void println(Object x) {
                // do nothing
            }

            @Override
            public void println(String x) {
                // do nothing
            }

            @Override
            public void println(char[] x) {
                // do nothing
            }

            @Override
            public void println(double x) {
                // do nothing
            }

            @Override
            public void println(float x) {
                // do nothing
            }

            @Override
            public void println(long x) {
                // do nothing
            }

            @Override
            public void println(int x) {
                // do nothing
            }

            @Override
            public void println(char x) {
                // do nothing
            }

            @Override
            public void println(boolean x) {
                // do nothing
            }

            @Override
            public void println() {
                // do nothing
            }

            @Override
            public void print(Object obj) {
                // do nothing
            }

            @Override
            public void print(String s) {
                // do nothing
            }

            @Override
            public void print(char[] s) {
                // do nothing
            }

            @Override
            public void print(double d) {
                // do nothing
            }

            @Override
            public void print(float f) {
                // do nothing
            }

            @Override
            public void print(long l) {
                // do nothing
            }

            @Override
            public void print(int i) {
                // do nothing
            }

            @Override
            public void print(char c) {
                // do nothing
            }

            @Override
            public void print(boolean b) {
                // do nothing
            }

            @Override
            public void write(byte[] buf, int off, int len) {
                // do nothing
            }

            @Override
            public void write(int b) {
                // do nothing
            }

            @Override
            protected void clearError() {
                // do nothing
            }

            @Override
            protected void setError() {
                // do nothing
            }

            @Override
            public boolean checkError() {
                return false;
            }

            @Override
            public void close() {
                // do nothing
            }

            @Override
            public void flush() {
                // do nothing
            }
        }

        static final class NullOutputStream extends OutputStream {

            @Override
            public void write(int b) throws IOException {
                // do nothing
            }

            @Override
            public void close() throws IOException {
                // do nothing
            }

            @Override
            public void flush() throws IOException {
                // do nothing
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                // do nothing
            }

            @Override
            public void write(byte[] b) throws IOException {
                // do nothing
            }
        }
    }
}

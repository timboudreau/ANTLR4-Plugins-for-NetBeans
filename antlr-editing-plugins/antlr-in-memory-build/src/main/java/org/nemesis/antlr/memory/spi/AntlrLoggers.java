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

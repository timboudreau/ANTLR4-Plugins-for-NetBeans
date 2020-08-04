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
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.openide.util.Lookup;

/**
 * Factory for streams and writers that can be used as stdout, passed to javac
 * or Tool or otherwise used to capture the output of tools invoked against
 * Antlr grammars in order to display such output to the user; output is
 * associated with a grammar file and a particular task associated with that
 * grammar file, and can be retrieved using the file path and task name from
 * AntlrOutput. Implementations should preserve output until either some timeout
 * has expired that suggests it is not going to be used, or until it has been
 * read and the object used to read it has been garbage collected.
 * <p>
 * This all ensures that:
 * <ul>
 * <li>Output that is useful for debugging the Antlr modules themselves can be
 * found</li>
 * <li>Output that is useful the user to understand problems in their grammar
 * can be found</li>
 * <li>None of the above pollutes the IDE log files</li>
 * </ul>
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class AntlrLoggers {

    public static final String STD_TASK_RUN_ANALYZER = "run-analyzer";
    public static final String STD_TASK_COMPILE_ANALYZER = "compile-analyzer";
    public static final String STD_TASK_GENERATE_ANTLR = "generate-antlr-sources";
    public static final String STD_TASK_GENERATE_ANALYZER = "generate-analyzer";
    public static final String STD_TASK_COMPILE_GRAMMAR = "compile-grammar";

    public static List<String> COMMON_TASKS = Collections.unmodifiableList(Arrays.asList(
            STD_TASK_GENERATE_ANTLR, STD_TASK_COMPILE_GRAMMAR, STD_TASK_GENERATE_ANALYZER,
            STD_TASK_COMPILE_ANALYZER, STD_TASK_RUN_ANALYZER
    ));

    private static AntlrLoggers INSTANCE;

    /**
     * Get the default instance of Antlr loggers.
     *
     * @return The default instance
     */
    public static AntlrLoggers getDefault() {
        if (INSTANCE == null) {
            INSTANCE = Lookup.getDefault().lookup(AntlrLoggers.class);
            if (INSTANCE == null) {
                INSTANCE = DummyLoggers.INSTANCE;
            }
        }
        return INSTANCE;
    }

    /**
     * Get a print stream to write output for some task, which the UI can
     * retrieve later for inspection by the user if desired; the AntlrOutput
     * implementation may provide a means for retrieving these.
     *
     * @param path The path to the grammar file being processed
     * @param task An ad-hoc string, perhaps one of the constants on this class
     * @return A print stream
     */
    public final PrintStream printStream(Path path, String task) {
        if (!OutputEnabledTasks.isOutputEnabled(path, task)) {
            return nullPrintStream();
        }
        return streamForPathAndTask(path, task);
    }

    /**
     * Get a print stream to write output for some task, which the UI can
     * retrieve later for inspection by the user if desired; the AntlrOutput
     * implementation may provide a means for retrieving these.
     *
     * @param path The path to the grammar file being processed
     * @param task An ad-hoc string, perhaps one of the constants on this class
     * @return A print stream
     */
    protected abstract PrintStream streamForPathAndTask(Path path, String task);

    /**
     * Get a print stream to write output for some task, which the UI can
     * retrieve later for inspection by the user if desired; the AntlrOutput
     * implementation may provide a means for retrieving these.
     *
     * @param path The path to the grammar file being processed
     * @param task An ad-hco string, perhaps one of the constants on this class
     * @return
     */
    public final Writer writer(Path path, String task) {
        if (!OutputEnabledTasks.isOutputEnabled(path, task)) {
            return nullWriter();
        }
        return writerForPathAndTask(path, task);
    }

    /**
     * Get a print stream to write output for some task, which the UI can
     * retrieve later for inspection by the user if desired; the AntlrOutput
     * implementation may provide a means for retrieving these.
     *
     * @param path The path to the grammar file being processed
     * @param task An ad-hco string, perhaps one of the constants on this class
     * @return
     */
    protected abstract Writer writerForPathAndTask(Path path, String task);

    /**
     * If expensive logging operations are to be performed, this call can
     * determine if they would actually be writing effectively to /dev/null in
     * order to bypass them; the default implementation returns such streams.
     *
     * @param stream The stream
     * @return true if the output is being stored somewhere as far as this class
     * knows
     */
    public static boolean isActive(PrintStream stream) {
        return stream != null && stream != DummyLoggers.NO_OP_STREAM;
    }

    /**
     * If expensive logging operations are to be performed, this call can
     * determine if they would actually be writing effectively to /dev/null in
     * order to bypass them; the default implementation returns such streams.
     *
     * @param writer The writer
     * @return true if the output is being stored somewhere as far as this class
     * knows
     */
    public static boolean isActive(Writer writer) {
        return writer != null && writer != DummyLoggers.NULL_WRITER;
    }

    /**
     * Get an unclosable print stream that discards output.
     *
     * @return A stream
     */
    protected static PrintStream nullPrintStream() {
        return DummyLoggers.NO_OP_STREAM;
    }

    /**
     * Get an unclosable writer that discards output.
     *
     * @return A stream
     */
    protected static Writer nullWriter() {
        return DummyLoggers.NULL_WRITER;
    }

    /**
     * Get a no-op AntlrLoggers instance that does nothing and discards output;
     * tests may sometimes prefer to use this directly than to get the default
     * implementation.
     *
     * @return An AntlrLoggers
     */
    public static AntlrLoggers noop() {
        return DummyLoggers.INSTANCE;
    }

    private static final class DummyLoggers extends AntlrLoggers {

        private static final PrintStream NO_OP_STREAM = new NullPrintStream();
        private static final Writer NULL_WRITER = new NullWriter();
        private static final DummyLoggers INSTANCE = new DummyLoggers();

        @Override
        protected PrintStream streamForPathAndTask(Path path, String task) {
            return NO_OP_STREAM;
        }

        @Override
        protected Writer writerForPathAndTask(Path path, String task) {
            return NULL_WRITER;
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

            @Override
            public void write(byte[] b) throws IOException {
                // do nothing
            }

            public String toString() {
                return "/dev/null";
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

            public String toString() {
                return "/dev/null";
            }
        }

        static final class NullWriter extends Writer {

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                // do nothing
            }

            @Override
            public void flush() throws IOException {
                // do nothing
            }

            @Override
            public void close() throws IOException {
                // do nothing
            }

            @Override
            public Writer append(char c) throws IOException {
                return this;
            }

            @Override
            public Writer append(CharSequence csq, int start, int end) throws IOException {
                return this;
            }

            @Override
            public Writer append(CharSequence csq) throws IOException {
                return this;
            }

            @Override
            public void write(String str, int off, int len) throws IOException {
                // do noting
            }

            @Override
            public void write(String str) throws IOException {
                // do nothing
            }

            @Override
            public void write(char[] cbuf) throws IOException {
                // do nothing
            }

            @Override
            public void write(int c) throws IOException {
                // do nothing
            }

            public String toString() {
                return "/dev/null";
            }
        }
    }
}

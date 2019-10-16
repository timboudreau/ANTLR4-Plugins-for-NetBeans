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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public final class ForeignInvocationResult<T> {

    Throwable failure;
    int exitCode = -1;
    private final ByteArrayOutputStream sysOutBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream sysErrBytes = new ByteArrayOutputStream();
    private final ThreadMultiplexingPrintStream sysOut;
    private final ThreadMultiplexingPrintStream sysErr;
    T invocationResult;

    public ForeignInvocationResult(PrintStream origOut, PrintStream origErr) {
        sysOut = new ThreadMultiplexingPrintStream(sysOutBytes, origOut);
        sysErr = new ThreadMultiplexingPrintStream(sysErrBytes, origErr);
    }

    public T invocationResult() {
        return invocationResult;
    }

    public void rethrow() throws Throwable {
        if (failure != null) {
            throw failure;
        }
    }

    public void setThrown(Throwable failure) {
        this.failure = failure;
    }

    public Throwable thrown() {
        return failure;
    }

    public boolean isSuccess() {
        return failure == null && exitCode == -1;
    }

    public Optional<Throwable> getFailure() {
        return Optional.ofNullable(failure);
    }

    public Optional<Integer> getExitCode() {
        return exitCode == -1 ? Optional.empty() : Optional.of(exitCode);
    }

    public <T> List<T> parseOutput(OutputParser<T> parser) {
        return parse(parser, output().split("\n"));
    }

    public <T> List<T> parseErrorOutput(OutputParser<T> parser) {
        return parse(parser, errorOutput().split("\n"));
    }

    private <T> List<T> parse(OutputParser<T> parser, String[] lines) {
        List<T> results = null;
        for (String s : lines) {
            s = s.trim();
            if (!s.isEmpty()) {
                T item = parser.onLine(s);
                if (item != null) {
                    if (results == null) {
                        results = new ArrayList<>(lines.length);
                    }
                    results.add(item);
                }
            }
        }
        return results == null ? Collections.emptyList() : results;
    }

    public String output() {
        return new String(sysOutBytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public String errorOutput() {
        return new String(sysErrBytes.toByteArray(), StandardCharsets.UTF_8);
    }

    final PrintStream stdout() {
        return sysOut;
    }

    final PrintStream stderr() {
        return sysErr;
    }

    void closeOuts() {
        try {
            sysOut.superClose();
            sysErr.superClose();
            sysOutBytes.flush();
            sysErrBytes.flush();
            sysOutBytes.close();
            sysErrBytes.close();
        } catch (IOException ioe) {
            Logger.getLogger(ForeignInvocationResult.class.getName()).log(Level.INFO, "Closing outs", ioe);
        }
    }

    private final Map<String, Object> keyValuePairs = new HashMap<>();

    /**
     * Allows invokers to decorate the result with key value pairs.
     *
     * @param <T>
     * @param key The key (gotten from the key() static method).
     * @param value The value, or null to remove
     * @return this
     */
    public <K> ForeignInvocationResult<T> put(Key<K> key, K value) {
        if (value == null) {
            keyValuePairs.remove(key.stringKey);
        } else {
            keyValuePairs.put(key.stringKey, value);
        }
        return this;
    }

    /**
     * Get the value associated with a key.
     *
     * @param <T> The key type
     * @param key The key
     * @return A value
     */
    public <T> Optional<T> get(Key<T> key) {
        Object res = keyValuePairs.get(key.stringKey);
        if (res == null) {
            return Optional.empty();
        }
        return Optional.of(key.type.cast(res));
    }

    public static <T> Key<T> key(Class<T> type, String name) {
        return new Key<>(name, type);
    }

    public static final class Key<T> {

        private final Class<T> type;
        private final String stringKey;

        private Key(String name, Class<T> type) {
            this.type = type;
            this.stringKey = name + "+" + type.getName();
        }
        private Key(Class<T> type) {
            this(type.getName(), type);
        }

        public String toString() {
            return stringKey;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 73 * hash + Objects.hashCode(this.stringKey);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Key<?> other = (Key<?>) obj;
            return Objects.equals(this.stringKey, other.stringKey);
        }
    }
}

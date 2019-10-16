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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.debug.spi.Emitter;

/**
 *
 * @author Tim Boudreau
 */
abstract class Record implements Comparable<Record> {

    private static final AtomicLong ORDER = new AtomicLong();
    private final RecordType type;
    protected final long timestamp;
    protected final long globalOrder = ORDER.getAndIncrement();
    protected final String threadName;
    protected final long threadId;

    Record(RecordType type) {
        this.type = type;
        timestamp = System.currentTimeMillis();
        threadName = Thread.currentThread().getName();
        threadId = Thread.currentThread().getId();
    }

    public String threadName() {
        return threadName();
    }

    public long threadId() {
        return threadId;
    }

    public RecordType type() {
        return type;
    }

    public Duration millisAfter(long when) {
        return Duration.ofMillis(Math.max(0, timestamp - when));
    }

    public long globalOrder() {
        return globalOrder;
    }

    @Override
    public int compareTo(Record o) {
        return Long.compare(globalOrder, o.globalOrder);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (int) (this.globalOrder ^ (this.globalOrder >>> 32));
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
        if (obj instanceof Record) {
            final Record other = (Record) obj;
            if (this.globalOrder != other.globalOrder) {
                return false;
            }
        }
        return true;
    }

    abstract void emitTo(int depth, Emitter emitter);

    static class MessageRecord extends Record {

        private final String heading;
        private final Supplier<String> msg;

        MessageRecord(String heading, Supplier<String> msg) {
            super(RecordType.MESSAGE);
            this.heading = heading;
            this.msg = msg;
        }

        @Override
        void emitTo(int depth, Emitter emitter) {
            emitter.message(depth, globalOrder, timestamp, threadName, threadId,
                    heading, msg);
        }
    }

    static class SuccessFailureMessage extends Record {

        private final String heading;

        private final boolean success;
        Supplier<String> msg;

        SuccessFailureMessage(String heading, boolean success, Supplier<String> msg) {
            super(RecordType.SUCCESS_OR_FAILURE);
            this.heading = heading;
            this.success = success;
            this.msg = msg;
        }

        @Override
        void emitTo(int depth, Emitter emitter) {
            emitter.successOrFailure(depth, globalOrder, timestamp,
                    threadName, threadId,
                    heading, success, msg);
        }
    }

    static class CreationRecord extends Record {

        private final Context.Info info;
        private final boolean isReentry;

        public CreationRecord(Context ctx) {
            super(RecordType.CREATION);
            info = ctx.info;
            isReentry = false;
        }

        private CreationRecord(Context.Info info) {
            super(RecordType.CREATION);
            this.info = info;
            isReentry = true;
        }

        boolean isWrappedReentry() {
            return isReentry;
        }

        CreationRecord copyOnCurrentThread() {
            return new CreationRecord(info);
        }

        String action() {
            return info.action;
        }

        @Override
        void emitTo(int depth, Emitter emitter) {
            emitter.enterContext(depth, globalOrder, timestamp,
                    threadName, threadId, isReentry,
                    info.ownerType, info.ownerString,
                    info.action, info.details, info.creationThreadId,
                    info.creationThreadString);
        }
    }

    static class ThrownRecord extends Record {

        private final Throwable thrown;

        ThrownRecord(Throwable thrown) {
            super(RecordType.THROWN);
            this.thrown = thrown;
        }

        Throwable thrown() {
            return thrown;
        }

        @Override
        public String toString() {
            PrintStream ps = null;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ps = new PrintStream(out, true, "UTF-8");
                thrown.printStackTrace(ps);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Debug.class.getName()).log(Level.SEVERE, null, ex);
                return thrown.toString();
            } finally {
                ps.close();
            }
        }

        @Override
        void emitTo(int depth, Emitter emitter) {
            emitter.thrown(depth, globalOrder, timestamp, threadName, threadId,
                    thrown, this::toString);
        }
    }
}

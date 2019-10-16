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
package org.nemesis.jfs.nio;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A fast, non-blocking mechanism for recording the last few things done, for
 * debugging purposes, without using logging which is less invasive with regard
 * to call ordering under concurrency.
 *
 * @author Tim Boudreau
 */
final class Ops {

    Op[] ops;
    private final AtomicRoundRobin counter;

    static Ops defaultOps() {
        return new Ops(75);
    }

    Ops(int ringSize) {
        counter = new AtomicRoundRobin(ringSize);
        ops = new Op[ringSize];
        for (int i = 0; i < ringSize; i++) {
            ops[i] = new Op();
        }
    }

    Op next() {
        return ops[counter.next()];
    }

    void set(Object... args) {
        next().set(args);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : all()) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    List<String> all() {
//        Op[] all = Arrays.copyOf(ops, ops.length);
        Op[] all = new Op[ops.length];
        long minTs = Long.MAX_VALUE;
        for (int i = 0; i < all.length; i++) {
            all[i] = ops[i].copy();
            if (all[i].args.get() != null && all[i].args.get().length != 0) {
                minTs = Math.min(all[i].lastUpdated, minTs);
            }
        }
        Arrays.sort(all);
        List<String> l = new ArrayList<>(ops.length);
        for (int i = 0; i < all.length; i++) {
            Op op = all[i];
            String s = op.toString(minTs);
            if (!s.isEmpty()) {
                l.add(s);
            }
        }

//        List<String> l = new ArrayList<>(ops.length);
//        for (int i = 0; i < ops.length; i++) {
//            String s = next().toString();
//            if (!s.isEmpty()){
//                l.add(s);
//            }
//        }
        return l;
    }

    private static final class Op implements Comparable<Op> {

        AtomicReference<Object[]> args = new AtomicReference<>(new Object[0]);
        volatile long lastUpdated;
        volatile int lastThread;
        private static final DecimalFormat FMT = new DecimalFormat("#####0000000000");

        Op() {

        }

        Op(Object[] args, long lastUpdated, int lastThread) {
            this.args.set(args);
            this.lastUpdated = lastUpdated;
            this.lastThread = lastThread;
        }

        Op copy() {
            return new Op(args.get(), lastUpdated, lastThread);
        }

        void clear() {
            args.set(new Object[0]);
            lastThread = -1;
            lastUpdated = 0;
        }

        void set(Object[] args) {
            this.args.getAndUpdate(t -> {
                lastUpdated = System.nanoTime();
                lastThread = (int) Thread.currentThread().getId();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Blocks) {
                        // need a snapshot - may have changed by the time
                        // it is logged
                        args[i] = ((Blocks) args[i]).copy();
                    }
                }
                return args;
            });
        }

        public String toString() {
            return toString(0);
        }

        public String toString(long minTs) {
            Object[] a = args.get();
            if (a != null && a.length > 0) {
                StringBuilder sb = new StringBuilder().append(FMT.format(lastUpdated - minTs)).append(':').append(lastThread)
                        .append('\t');
                if (a[0] instanceof String && ((String) a[0]).indexOf('{') >= 0) {
                    Object[] ags = new Object[a.length - 1];
                    System.arraycopy(a, 1, ags, 0, ags.length);
                    sb.append(MessageFormat.format(a[0].toString(), ags));
                    return sb.toString();
                } else {
                    for (int i = 0; i < a.length; i++) {
                        if (i != a.length - 1) {
                            sb.append(" / ");
                        }
                        sb.append(a[i]);
                    }
                    return sb.toString();
                }
            }
            return "";
        }

        @Override
        public int compareTo(Op o) {
            if (o.lastUpdated > lastUpdated) {
                return -1;
            } else if (lastUpdated > o.lastUpdated) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public final class AtomicRoundRobin {

        private final int maximum;
        private volatile int currentValue;
        private final AtomicIntegerFieldUpdater<AtomicRoundRobin> up;

        public AtomicRoundRobin(int maximum) {
            if (maximum <= 0) {
                throw new IllegalArgumentException("Maximum must be > 0");
            }
            this.maximum = maximum;
            up = AtomicIntegerFieldUpdater.newUpdater(AtomicRoundRobin.class, "currentValue");
        }

        /**
         * Get the maximum possible value
         *
         * @return The maximum
         */
        public int maximum() {
            return maximum;
        }

        /**
         * Get the current value
         *
         * @return
         */
        public int get() {
            return currentValue;
        }

        /**
         * Get the next value, incrementing the value or resetting it to zero
         * for the next caller.
         *
         * @return The value
         */
        public int next() {
            if (maximum == 1) {
                return 0;
            }
            for (;;) {
                int current = get();
                int next = current == maximum - 1 ? 0 : current + 1;
                if (up.compareAndSet(this, current, next)) {
                    return current;
                }
            }
        }
    }

}

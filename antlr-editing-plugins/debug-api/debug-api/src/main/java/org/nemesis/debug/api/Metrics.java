/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Collection of adhoc metrics with statistics.
 *
 * @author Tim Boudreau
 */
public final class Metrics {

    public static final String SUFFIX_AVERAGE = ".avg";
    public static final String SUFFIX_MIN = ".min";
    public static final String SUFFIX_MAX = ".max";

    private static final ConcurrentHashMap<String, Metric> registered
            = new ConcurrentHashMap<>();

    public static void register(String name, AtomicLong lng, boolean stats) {
        LongMetricEntry result = new LongMetricEntry(name, lng::get, stats);
        registered.put(name, result);
    }

    public static void register(String name, AtomicInteger ai, boolean stats) {
        IntMetricEntry result = new IntMetricEntry(name, ai::get, stats);
        registered.put(name, result);
    }

    public static void register(String name, AtomicBoolean lng, boolean stats) {
        BooleanEntry result = new BooleanEntry(name, lng::get, stats);
        registered.put(name, result);
    }

    public static void register(String name, LongSupplier lng, boolean stats) {
        LongMetricEntry result = new LongMetricEntry(name, lng, stats);
        registered.put(name, result);
    }

    public static void register(String name, IntSupplier ai, boolean stats) {
        IntMetricEntry result = new IntMetricEntry(name, ai, stats);
        registered.put(name, result);
    }

    public static void register(String name, BooleanSupplier lng, boolean stats) {
        BooleanEntry result = new BooleanEntry(name, lng, stats);
        registered.put(name, result);
    }

    public static int poll(BiConsumer<? super String, ? super Object> consumer) {
        return poll(null, consumer);
    }

    public static int poll(Predicate<String> filter, BiConsumer<? super String, ? super Object> consumer) {
        List<String> keys = filter == null ? new ArrayList<>(registered.keySet()) : new LinkedList<>();
        if (filter != null) {
            for (String key : registered.keySet()) {
                if (filter.test(key)) {
                    keys.add(key);
                }
            }
        }
        int result = 0;
        Collections.sort(keys);
        for (String k : keys) {
            Metric met = registered.get(k);
            if (met != null) {
                met.poll(consumer);
                result++;
            }
        }
        return result;
    }

    interface Metric {

        String name();

        void poll(BiConsumer<? super String, ? super Object> consumer);

        void reset();
    }

    private static abstract class MetricEntry<T> implements Metric {

        protected final String name;

        protected MetricEntry(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        abstract T value();

        public final String toString() {
            return name + ": " + value();
        }

        public final int hashCode() {
            return name.hashCode() * 71;
        }

        public final boolean equals(Object o) {
            return o instanceof Metric && ((Metric) o).name().equals(name);
        }
    }

    private static final class BooleanEntry extends MetricEntry<Boolean> {

        private final BooleanSupplier value;
        private IntSummaryStatistics stats;

        public BooleanEntry(String name, BooleanSupplier bool, boolean stats) {
            super(name);
            this.value = bool;
            if (stats) {
                this.stats = new IntSummaryStatistics();
            } else {
                this.stats = null;
            }
        }

        @Override
        public void reset() {
            synchronized (this) {
                if (stats != null) {
                    stats = new IntSummaryStatistics();
                }
            }
        }

        @Override
        Boolean value() {
            return value.getAsBoolean();
        }

        private static final DecimalFormat PCT = new DecimalFormat("##0.##");

        @Override
        public void poll(BiConsumer<? super String, ? super Object> consumer) {
            boolean val = value.getAsBoolean();
            consumer.accept(name, val);
            IntSummaryStatistics st = null;
            synchronized (this) {
                st = stats;
            }
            if (st != null) {
                synchronized (st) {
                    st.accept(val ? 100 : 0);
                    double pct = st.getAverage() * 100;
                    consumer.accept(name + SUFFIX_AVERAGE, pct);
                }
            }
        }
    }

    private static final class LongMetricEntry extends MetricEntry<Long> {

        private LongSummaryStatistics stats;
        private final LongSupplier lngs;

        LongMetricEntry(String name, LongSupplier lngs, boolean includeStats) {
            super(name);
            this.lngs = lngs;
            stats = includeStats ? new LongSummaryStatistics() : null;
        }

        @Override
        Long value() {
            return lngs.getAsLong();
        }

        public void reset() {
            synchronized (this) {
                if (stats != null) {
                    stats = new LongSummaryStatistics();
                }
            }
        }

        @Override
        public void poll(BiConsumer<? super String, ? super Object> consumer) {
            long val = lngs.getAsLong();
            consumer.accept(name, val);
            LongSummaryStatistics st;
            synchronized (this) {
                st = stats;
            }
            if (st != null) {
                long count, min, max;
                double avg;
                synchronized (st) {
                    st.accept(val);
                    count = st.getCount();
                    avg = st.getAverage();
                    min = st.getMin();
                    max = st.getMax();
                }
                consumer.accept(name + SUFFIX_AVERAGE, avg);
                consumer.accept(name + SUFFIX_MIN, min);
                consumer.accept(name + SUFFIX_MAX, max);
            }
        }
    }

    private static final class IntMetricEntry extends MetricEntry<Integer> {

        private IntSummaryStatistics stats;
        private final IntSupplier value;

        IntMetricEntry(String name, IntSupplier lngs, boolean includeStats) {
            super(name);
            this.value = lngs;
            stats = includeStats ? new IntSummaryStatistics() : null;
        }

        public void reset() {
            synchronized (this) {
                if (stats != null) {
                    stats = new IntSummaryStatistics();
                }
            }
        }

        Integer value() {
            return value.getAsInt();
        }

        @Override
        public void poll(BiConsumer<? super String, ? super Object> consumer) {
            int val = value.getAsInt();
            consumer.accept(name, val);
            IntSummaryStatistics st;
            synchronized (this) {
                st = stats;
            }
            if (st != null) {
                int min, max;
                double avg;
                synchronized (st) {
                    st.accept(val);
                    avg = st.getAverage();
                    min = st.getMin();
                    max = st.getMax();
                }
                consumer.accept(name + SUFFIX_AVERAGE, avg);
                consumer.accept(name + SUFFIX_MIN, min);
                consumer.accept(name + SUFFIX_MAX, max);
            }
        }
    }
}

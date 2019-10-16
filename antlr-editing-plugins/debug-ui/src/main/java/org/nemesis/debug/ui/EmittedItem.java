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
package org.nemesis.debug.ui;

import java.util.List;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
class EmittedItem implements Comparable<EmittedItem> {

    private final long contextEntry;
    private final boolean failure;
    private final String heading;
    private final int depth;
    private final long timestamp;
    private final long globalOrder;
    private final Supplier<String> content;
    final List<String> contexts;
    private final long threadId;
    private final String threadName;

    public EmittedItem(List<String> contexts, long contextEntryTimestamp, boolean failure, String heading, int depth, long timestamp, long globalOrder, Supplier<String> content, long threadId, String threadName) {
        this.contexts = contexts;
        this.contextEntry = contextEntryTimestamp;
        this.failure = failure;
        this.heading = heading;
        this.depth = depth;
        this.timestamp = timestamp;
        this.globalOrder = globalOrder;
        this.content = content;
        this.threadId = threadId;
        this.threadName = threadName;
    }

    String threadName() {
        return threadName;
    }

    long threadId() {
        return threadId;
    }

    String currentContext() {
        return contexts.get(0);
    }

    String outerContext() {
        return contexts.get(contexts.size() - 1);
    }

    boolean isCategory() {
        return currentContext().equals(outerContext());
    }

    boolean isFailure() {
        return failure;
    }

    String heading() {
        return heading;
    }

    @Override
    public String toString() {
        return durationString(contextEntry) + " " + heading;
    }

    public String details() {
        return content.get();
    }

    public String durationString() {
        return durationString(contextEntry);
    }

    public String durationString(long ms) {
        StringBuilder sb = new StringBuilder(8);
        sb.append(Long.toString(timestamp - ms));
        while (sb.length() < 8) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    @Override
    public int compareTo(EmittedItem o) {
        return Long.compare(globalOrder, o.globalOrder);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (int) (this.globalOrder ^ (this.globalOrder >>> 32));
        return hash;
    }

    int depth() {
        return depth;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof EmittedItem)) {
            return false;
        }
        final EmittedItem other = (EmittedItem) obj;
        return this.globalOrder == other.globalOrder;
    }

}

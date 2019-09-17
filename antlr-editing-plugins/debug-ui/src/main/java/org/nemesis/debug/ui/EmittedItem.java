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

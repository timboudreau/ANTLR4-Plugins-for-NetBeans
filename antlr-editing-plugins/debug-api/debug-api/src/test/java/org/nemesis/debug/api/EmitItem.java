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

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
class EmitItem {

    private final int depth;
    private final EmitItemType type;
    private final String obj;
    private final String threadName;

    public EmitItem(int depth, EmitItemType type, String obj, String threadName) {
        this.threadName = threadName;
        this.depth = depth;
        this.type = type;
        this.obj = obj;
    }

    public EmitItem(int depth, EmitItemType type, String obj) {
        this.threadName = null;
        this.depth = depth;
        this.type = type;
        this.obj = obj;
    }

    public String stringify() {
        return "new EmitItem(" + depth + ", EmitItemType." + type.name() + ", \"" + obj + "\", " + (threadName == null ? "null" : "\"" + threadName + "\"") + ")";
    }

    @Override
    public String toString() {
        return depth + ":" + type.name() + ":" + obj;
    }

    @Override
    public int hashCode() {
        int hash = 5 * depth;
        hash = 59 * hash + Objects.hashCode(this.type);
        hash = 59 * hash + Objects.hashCode(this.obj);
        hash = 59 * hash + Objects.hashCode(this.threadName);
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
        final EmitItem other = (EmitItem) obj;
        if (!Objects.equals(this.obj, other.obj) || !Objects.equals(this.threadName, other.threadName)) {
            return false;
        }
        return this.type == other.type && this.depth == other.depth;
    }

}

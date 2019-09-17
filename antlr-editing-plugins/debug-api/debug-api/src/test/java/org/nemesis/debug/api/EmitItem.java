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

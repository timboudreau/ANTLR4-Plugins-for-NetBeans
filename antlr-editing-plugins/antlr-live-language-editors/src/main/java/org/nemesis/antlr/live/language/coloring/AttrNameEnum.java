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


package org.nemesis.antlr.live.language.coloring;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 *
 * @author Tim Boudreau
 */
final class AttrNameEnum implements Enumeration<Object> {

    private final byte flags;
    private int ix = -1;
    private Object next;

    AttrNameEnum(byte flags) {
        this.flags = flags;
        next = findNext();
    }

    private Object findNext() {
        while (++ix < AdhocColoring.STYLE_CONSTS.length) {
            if ((flags & AdhocColoring.STYLE_FLAGS[ix]) != 0) {
                return AdhocColoring.STYLE_CONSTS[ix];
            }
        }
        return null;
    }

    @Override
    public boolean hasMoreElements() {
        return next != null;
    }

    @Override
    public Object nextElement() {
        Object result = next;
        if (result == null) {
            throw new NoSuchElementException();
        }
        next = findNext();
        return result;
    }

}

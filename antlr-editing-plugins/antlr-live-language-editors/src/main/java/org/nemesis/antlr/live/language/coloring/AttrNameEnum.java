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

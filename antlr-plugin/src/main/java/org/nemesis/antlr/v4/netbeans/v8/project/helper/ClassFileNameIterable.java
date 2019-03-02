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
package org.nemesis.antlr.v4.netbeans.v8.project.helper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A fully qualified class name from a Java source might represent an inner
 * class; this iterates all possible class file paths relative to the source
 * root which might map to that fully qualified name from source.
 *
 * @author Tim Boudreau
 */
final class ClassFileNameIterable implements Iterable<Path>, Iterator<Path> {

    private final String fqn;
    private int dotIndex;
    private final int[] dotPositions;

    public ClassFileNameIterable(String fqn) {
        this.fqn = fqn;
        for (int i = 0; i < fqn.length(); i++) {
            char c = fqn.charAt(i);
            if (c == '.') {
                dotIndex++;
            }
        }
        dotPositions = new int[dotIndex];
        int pos = 0;
        for (int i = 0; i < fqn.length(); i++) {
            if (fqn.charAt(i) == '.') {
                dotPositions[pos++] = i;
            }
        }
    }

    private ClassFileNameIterable(int[] dotPositions, String fqn) {
        this.dotIndex = dotPositions.length;
        this.fqn = fqn;
        this.dotPositions = dotPositions;
    }

    @Override
    public Path next() {
        if (dotIndex < 0) {
            throw new NoSuchElementException();
        }
        Path result;
        StringBuilder sb = new StringBuilder(fqn.replace('.', '/'));
        for (int i = dotIndex; i < dotPositions.length; i++) {
            sb.setCharAt(dotPositions[i], '$');
        }
        sb.append(".class");
        result = Paths.get(sb.toString());
        dotIndex--;
        return result;
    }

    @Override
    public Iterator<Path> iterator() {
        if (dotIndex != dotPositions.length) {
            return new ClassFileNameIterable(dotPositions, fqn);
        }
        return this;
    }

    @Override
    public boolean hasNext() {
        return dotIndex >= 0;
    }
}

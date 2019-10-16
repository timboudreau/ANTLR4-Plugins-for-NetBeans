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

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

package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.util.preconditions.Checks;
import java.util.Objects;
import org.openide.filesystems.FileObject;

/**
 * Cache key for the InvocationEnvironment that it responsible for
 * regenerating (if needed), compiling and running code against one Antlr
 * grammar file.
 *
 * @param <T>
 */
class EnvironmentKey<T> {

    final FileObject file;
    final InvocationRunnerLookupKey<T> typeKey;

    public EnvironmentKey(FileObject file, InvocationRunnerLookupKey<T> typeKey) {
        this.file = Checks.notNull("file", file);
        this.typeKey = Checks.notNull("typeKey", typeKey);
    }

    @Override
    public String toString() {
        return "EnvironmentKey{" + file.getPath() + "-" + typeKey + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Objects.hashCode(this.file);
        hash = 13 * hash + Objects.hashCode(this.typeKey);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final EnvironmentKey<?> other = (EnvironmentKey<?>) obj;
        return other.typeKey.equals(typeKey) && other.file.equals(file);
    }

}

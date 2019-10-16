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
package org.nemesis.antlr.memory.tool;

import java.nio.file.Path;
import java.util.Objects;
import javax.tools.JavaFileManager;

/**
 *
 * @author Tim Boudreau
 */
final class LoadAttempt {

    private final Path path;
    private final JavaFileManager.Location location;

    public LoadAttempt(Path path, JavaFileManager.Location location) {
        this.path = path;
        this.location = location;
    }

    @Override
    public String toString() {
        return location + ":" + path;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.path);
        hash = 41 * hash + Objects.hashCode(this.location);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof LoadAttempt)) {
            return false;
        }
        final LoadAttempt other = (LoadAttempt) obj;
        if (!Objects.equals(this.path, other.path)) {
            return false;
        }
        return Objects.equals(this.location, other.location);
    }

}

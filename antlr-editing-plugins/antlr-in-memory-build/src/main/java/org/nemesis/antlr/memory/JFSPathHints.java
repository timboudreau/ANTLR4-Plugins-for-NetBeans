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
package org.nemesis.antlr.memory;

import com.mastfrog.util.path.UnixPath;
import org.nemesis.jfs.JFSCoordinates;

/**
 * Allows the code that built the JFS mapping to provide a way to look up exact paths
 * for grammar from code that knows where to look, rather than guessing locations.
 *
 * @author Tim Boudreau
 */
public interface JFSPathHints {

    UnixPath firstPathForRawName(String rawName, String... exts);

    JFSCoordinates forFileName(String name, UnixPath relativeTo, String... exts);

    static final JFSPathHints NONE = new JFSPathHints() {
        @Override
        public UnixPath firstPathForRawName(String rawName, String... exts) {
            return null;
        }

        @Override
        public JFSCoordinates forFileName(String name, UnixPath relativeTo, String... exts) {
            return null;
        }
    };
}

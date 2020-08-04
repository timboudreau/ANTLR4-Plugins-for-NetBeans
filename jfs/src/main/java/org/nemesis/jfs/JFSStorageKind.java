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
package org.nemesis.jfs;

/**
 * The type of backing storage for a "file" in a JFS - needed by clients
 * up-to-dateness checks when a JFS is retained and reused, to know which files
 * should be re-checked to determine if rerunning a compilation or generation
 * process is needed.
 *
 * @author Tim Boudreau
 */
public enum JFSStorageKind {

    HEAP_BYTES,
    MAPPED_BYTES,
    MASQUERADED_DOCUMENT,
    MASQUERADED_FILE,
    DISCARDED;

    public boolean isFile() {
        return this == MASQUERADED_FILE;
    }

    public boolean isDocument() {
        return this == MASQUERADED_DOCUMENT;
    }

    public boolean isDiscarded() {
        return this == DISCARDED;
    }

    public boolean isBytes() {
        switch (this) {
            case HEAP_BYTES:
            case MAPPED_BYTES:
                return true;
        }
        return false;
    }

    public boolean isMasqueraded() {
        switch (this) {
            case MASQUERADED_DOCUMENT:
            case MASQUERADED_FILE:
                return true;
        }
        return false;
    }
}

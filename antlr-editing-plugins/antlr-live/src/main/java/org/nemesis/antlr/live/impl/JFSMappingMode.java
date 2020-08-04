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
package org.nemesis.antlr.live.impl;

/**
 * Ways a file can be masqueraded into the namespace of a JFS.
 */
enum JFSMappingMode {
    /**
     * The file on disk is wrappered into a JFSFileObject that reads and writes
     * its content.
     */
    FILE, /**
     * The Swing Document for the file is wrappered into a JFSFileObject that
     * reads and writes its live content.
     */
    DOCUMENT, /**
     * Null-state: the file or document has not been mapped into the JFS, or was
     * and has been un-mapped.
     */
    UNMAPPED

}

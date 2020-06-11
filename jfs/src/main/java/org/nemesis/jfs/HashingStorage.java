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

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Optional interface that can be implemented by a JFSBytesStorage if it has an
 * optimized way of computing a hash, or a way to cache a hash and reliably have
 * it up to date before one is requested (e.g. netbeans priority document
 * listeners).
 *
 * @author Tim Boudreau
 */
interface HashingStorage {

    /**
     * Add the stored contents bytes into the passed MessageDigest. If for some
     * reason hashing cannot be performed, returns false and the caller should
     * fall back to the default means of hashing by acquiring the file's bytes.
     *
     * @param into A message digest
     * @return true if the digest was updated
     * @throws IOException if something goes wrong
     */
    boolean hash(MessageDigest into) throws IOException;

    /**
     * Compute a SHA-1 hash of the bytes of this storage at the present time,
     * optionally returning a cached value where appropriate. Callers are
     * expected not to alter the returned array.
     *
     * @return The hash
     * @throws IOException if something goes wrong
     */
    byte[] hash() throws IOException;
}

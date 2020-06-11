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

import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.tools.FileObject;

/**
 * Extension interface to FileObject with some convenience
 * methods.
 *
 * @author Tim Boudreau
 */
public interface JFSFileObject extends FileObject {

    /**
     * Get the content as a byte array.
     *
     * @return Some bytes
     * @throws IOException
     */
    byte[] asBytes() throws IOException;

    /**
     * Get the content as a ByteBuffer.
     *
     * @return A byteBuffer
     * @throws IOException
     */
    ByteBuffer asByteBuffer() throws IOException;

    /**
     * Get the length in bytes of this file.
     *
     * @return The length
     */
    int length();

    /**
     * Returns true if the effective package name of this file
     * matches the passed one.
     *
     * @param pkg A Java package name
     * @return True if it matches
     */
    boolean packageMatches(String pkg);

    /**
     * Set the content of this file as bytes.
     *
     * @param bytes New bytes, non null
     * @param lastModified An updated last modified timestamp
     * @throws IOException If something goes wrong
     * @throws UnsupportedOperationException if the file is read-only (either
     * using read-only allocator for the merged view of the filesystem's
     * contents, or if the file is a proxy for a real file on disk and
     * does not actually have a copy of its bytes in memory in this filesystem)
     */
    void setBytes(byte[] bytes, long lastModified) throws IOException;

    /**
     * Returns a view of this file which implements JavaFileObject, possibly
     * by replacing it in the filesystem with an implementation over the
     * same backing store, of that interface.
     *
     * @return A JavaFileObject
     */
    JFSJavaFileObject toJavaFileObject();

    /**
     * Get a resolvable URL to this JFSFileObject, which will resolve for
     * as long as the owning JFS exists and it is present in it.
     *
     * @return A URL
     */
    URL toURL();

    /**
     * Get the file name as a Java NIO path.
     *
     * @return The fully-qualified file name
     */
    default Path toPath() {
        return Paths.get(getName());
    }

    JFSStorageKind storageKind();

    JFSFileObject setTextContent(String txt) throws IOException;

    default void hash(MessageDigest digest) throws IOException {
        digest.update(asBytes());
    }

    default byte[] hash() throws IOException{
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            hash(digest);
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }
}

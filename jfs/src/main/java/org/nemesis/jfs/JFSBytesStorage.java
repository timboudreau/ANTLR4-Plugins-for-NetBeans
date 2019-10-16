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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.MalformedInputException;

/**
 * Abstraction for storing a wad of bytes which represents one or more
 * FileObjects.  Some methods such as discard() exist so that if needed,
 * an off-heap or memory-mapped version can be created which allocates
 * from a single pool of memory and needs to reclaim what is no longer
 * in use.
 *
 * @author Tim Boudreau
 */
public interface JFSBytesStorage {

    /**
     * Get the content as a byte array.
     *
     * @return
     */
    byte[] asBytes() throws IOException;

    JFSStorageKind storageKind();

    default ByteBuffer asByteBuffer() throws IOException {
        return ByteBuffer.wrap(asBytes());
    }

    default Charset encoding() {
        return storage().encoding();
    }

    default CharBuffer asCharBuffer(boolean ignoreEncodingErrors) throws IOException {
        if (length() == 0) {
            return CharBuffer.allocate(0);
        }
        return asCharBuffer(encoding(), ignoreEncodingErrors);
    }

    default CharBuffer asCharBuffer(Charset encoding, boolean ignoreEncodingErrors) throws IOException {
        if (length() == 0) {
            return CharBuffer.allocate(0);
        }
        CharsetDecoder dec = encoding.newDecoder();
        if (ignoreEncodingErrors) {
            ByteBuffer buf = asByteBuffer();
            try {
                return dec.decode(buf);
            } catch (MalformedInputException mie) {
                throw new IOException("Malformed input decoding " + buf + " as " + encoding.name(), mie);
            }
        }

        float multiplier = dec.averageCharsPerByte(); // Average? This can be wrong?
        int charCount = (int) Math.ceil(length() * multiplier);
        CharBuffer result = CharBuffer.allocate(charCount);

        CoderResult res = dec.decode(asByteBuffer(), result, true);
        if (res.isError() || res.isMalformed() || res.isUnmappable() || res.isOverflow()) {
            throw new IOException("Error decoding " + encoding.name() + ": " + res);
        }
        result.flip();
        return result.slice();
    }

    /**
     * Open an output stream.  Read-only instances may throw an IOException.
     *
     * @return An output stream which, when closed, will replace the content
     * of this storage.
     * @throws IOException If something goes wrong or the storage allocator
     * is read only or this is a stand-in for a file on disk.
     */
    OutputStream openOutputStream() throws IOException;

    default InputStream openInputStream() throws IOException {
        if (length() == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(asBytes());
    }

    /**
     * Get the last modified date, which should be updated by writes.
     *
     * @return a unix timestamp
     */
    long lastModified();

    /**
     * Get the backing storage which is responsible for managing memory
     * allocation.
     *
     * @return A storage
     */
    JFSStorage storage();

    /**
     * Hint to this JFSBytesStorage that any cached bytes will not be
     * refrenced again and it may be discarded.
     */
    void discard();

    /**
     * Get the number of bytes represented.
     *
     * @return the length
     */
    int length();

    /**
     * Set the bytes stored here.  May throw an IllegalStateException if
     * read-only.
     *
     * @param bytes New bytes, non-null
     * @param lastModified A new last modified time
     */
    void setBytes(byte[] bytes, long lastModified) throws IOException;
}

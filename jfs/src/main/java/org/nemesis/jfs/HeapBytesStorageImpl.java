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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author Tim Boudreau
 */
final class HeapBytesStorageImpl implements JFSBytesStorage, HashingStorage {

    private final JFSStorage storage;
    private byte[] bytes;
    private volatile long lastModified = 0;
    private volatile boolean writing;

    HeapBytesStorageImpl(JFSStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean hash(MessageDigest into) {
        into.update(bytes == null ? new byte[0] : bytes);
        return true;
    }

    @Override
    public byte[] hash() {
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-1");
            hash(dig);
            return dig.digest();
        } catch (NoSuchAlgorithmException ex) {
            return Exceptions.chuck(ex);
        }
    }


    @Override
    public synchronized String toString() {
        return  "HeapBytesStorageImpl(" + (bytes == null ? "unalloacated" : bytes.length) + " bytes)";
    }

    @Override
    public JFSStorageKind storageKind() {
        return JFSStorageKind.HEAP_BYTES;
    }

    @Override
    public synchronized byte[] asBytes() {
        return bytes == null ? new byte[0] : bytes;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        byte[] bytes;
        synchronized(this) {
            bytes = this.bytes;
        }
        if (bytes == null) {
            return ByteBuffer.allocate(0);
        }
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        if (writing) {
            throw new IOException("Already open for writing: " + this);
        }
        writing = true;
        return new BytesOutput();
    }

    @Override
    public long lastModified() {
        // does not need synchronization, volatile + writes inside a
        // lock guarantees a cache flush
        return lastModified;
    }

    public synchronized int length() {
        return bytes == null ? 0 : bytes.length;
    }

    @Override
    public JFSStorage storage() {
        return storage;
    }

    @Override
    public synchronized void discard() {
        lastModified = 0;
        bytes = null;
    }

    @Override
    public synchronized void setBytes(byte[] bytes, long lastModified) {
        assert bytes != null : "Bytes null";
        this.lastModified = lastModified;
        this.bytes = bytes;
    }

    class BytesOutput extends ByteArrayOutputStream {

        BytesOutput() {
            super(8192);
        }

        @Override
        public void close() throws IOException {
            super.close();
            setBytes(toByteArray(), System.currentTimeMillis());
            writing = false;
        }
    }
}

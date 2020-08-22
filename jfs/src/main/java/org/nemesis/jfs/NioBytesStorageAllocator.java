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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileManager;
import org.nemesis.jfs.nio.BlockStorage;
import org.nemesis.jfs.nio.BlockStorage.StoredBytes;
import org.nemesis.jfs.nio.BlockStorageKind;

/**
 *
 * @author Tim Boudreau
 */
final class NioBytesStorageAllocator implements JFSStorageAllocator<NioBytesStorageAllocator.BytesStorageWrapper> {

    private static final int DEFAULT_BLOCK_SIZE = 256;
    private static final int DEFAULT_INITIAL_BLOCKS = 32;

    private final BlockStorage storage;

    NioBytesStorageAllocator() throws IOException {
        this(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS);
    }

    static JFSStorageAllocator<?> allocator() {
        try {
            return new NioBytesStorageAllocator(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS, BlockStorageKind.MAPPED_TEMP_FILE);
        } catch (IOException ex) {
            Logger.getLogger(NioBytesStorageAllocator.class.getName()).log(Level.SEVERE, "Allocating nio storage failed", ex);
            return JFSStorageAllocator.HEAP;
        }
    }

    NioBytesStorageAllocator(int blockSize, int initialBlockCount) throws IOException {
        this(blockSize, initialBlockCount, BlockStorageKind.OFF_HEAP);
    }

    NioBytesStorageAllocator(int blockSize, int initialBlockCount, BlockStorageKind kind) throws IOException {
        // XXX shouldn't this be kind.create()???
        storage = kind.create(blockSize, initialBlockCount);
    }

    NioBytesStorageAllocator(BlockStorageKind kind) throws IOException {
        storage = kind.create(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS);
    }

    Supplier<String> opsSupplier() {
        return storage::toString;
    }

    @Override
    public synchronized BytesStorageWrapper allocate(JFSStorage storage, Name name, JavaFileManager.Location location) {
        return new BytesStorageWrapper(storage, name, location);
    }

    @Override
    public void destroy() {
        try {
            storage.close();
        } catch (IOException ex) {
            Logger.getLogger(NioBytesStorageAllocator.class.getName()).log(Level.SEVERE, "Exception destroying storage", ex);
        }
    }

    final class BytesStorageWrapper implements JFSBytesStorage {

        private StoredBytes file;
        private final JFSStorage storage;
        private volatile long lastModified;
        final Name name;
        private final Object lock = NioBytesStorageAllocator.this;

        BytesStorageWrapper(JFSStorage storage, Name name, JavaFileManager.Location loc) {
            this.storage = storage;
            this.name = name;
        }

        @Override
        public JFSStorageKind storageKind() {
            return JFSStorageKind.MAPPED_BYTES;
        }

        public String toString() {
            return name + "{" + file + "}";
        }

        private StoredBytes file() {
            synchronized (lock) {
                return file;
            }
        }

        @Override
        public byte[] asBytes() throws IOException {
            synchronized (lock) {
                StoredBytes file = file();
                if (file == null) {
                    return new byte[0];
                }
                return file.getBytes();
            }
        }

        @Override
        public ByteBuffer asByteBuffer() throws IOException {
            synchronized (lock) {
                StoredBytes file = file();
                return file == null ? ByteBuffer.allocate(0) : file.readBuffer();
            }
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            synchronized (lock) {
                StoredBytes file = file();
                if (file != null) {
                    return file.openOutputStream();
                }
                return new InitialContentStream();
            }
        }

        class InitialContentStream extends ByteArrayOutputStream {

            @Override
            public void close() throws IOException {
                super.close();
                synchronized (lock) {
                    StoredBytes file = file();
                    byte[] bytes = toByteArray();
                    if (file != null) {
                        if (bytes.length > 0) {
                            file.setBytes(bytes);
                        } else {
                            file.delete();
                            BytesStorageWrapper.this.file = null;
                        }
                    } else {
                        if (bytes.length > 0) {
                            BytesStorageWrapper.this.file = NioBytesStorageAllocator.this.storage.allocate(bytes);
                        }
                    }
                    lastModified = System.currentTimeMillis();
                }
            }
        }

        @Override
        public InputStream openInputStream() throws IOException {
            synchronized (lock) {
                StoredBytes file = file();
                return file == null ? new ByteArrayInputStream(new byte[0]) : file.openInputStream();
            }
        }

        @Override
        public long lastModified() {
            synchronized (lock) {
                return lastModified;
            }
        }

        @Override
        public JFSStorage storage() {
            return storage;
        }

        @Override
        public void discard() {
            try {
                synchronized (lock) {
                    if (file != null) {
                        file.delete();
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(BytesStorageWrapper.class.getName()).log(Level.SEVERE,
                        "Exception discarding", ex);
            }
        }

        @Override
        public int length() {
            synchronized (lock) {
                StoredBytes file = file();
                return file == null ? 0 : file.size();
            }
        }

        @Override
        public void setBytes(byte[] bytes, long lastModified) throws IOException {
            synchronized (lock) {
                StoredBytes file = this.file;
                if (file == null) {
                    this.file = NioBytesStorageAllocator.this.storage.allocate(bytes);
                } else {
                    file.setBytes(bytes);
                }
                this.lastModified = lastModified;
            }
        }
    }
}

package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import javax.tools.JavaFileManager;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio.BlockStorage;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio.BlockStorage.StoredBytes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio.BlockStorageKind;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class NioBytesStorageAllocator implements JFSStorageAllocator<NioBytesStorageAllocator.BytesStorageWrapper> {

    private static final int DEFAULT_BLOCK_SIZE = 256;
    private static final int DEFAULT_INITIAL_BLOCKS = 8;

    private final BlockStorage storage;

    public NioBytesStorageAllocator() throws IOException {
        this(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS);
    }

    static JFSStorageAllocator<?> allocator() {
        try {
            return new NioBytesStorageAllocator(DEFAULT_BLOCK_SIZE, DEFAULT_INITIAL_BLOCKS, BlockStorageKind.MAPPED_TEMP_FILE);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return JFSStorageAllocator.HEAP;
        }
    }

    NioBytesStorageAllocator(int blockSize, int initialBlockCount) throws IOException {
        this(blockSize, initialBlockCount, BlockStorageKind.OFF_HEAP);
    }

    NioBytesStorageAllocator(int blockSize, int initialBlockCount, BlockStorageKind kind) throws IOException {
        storage = BlockStorageKind.OFF_HEAP.create(blockSize, initialBlockCount);
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
            Exceptions.printStackTrace(ex);
        }
    }

    final class BytesStorageWrapper implements JFSBytesStorage {

        private StoredBytes file;
        private final JFSStorage storage;
        private volatile long lastModified;
        final Name name;
        private final Object lock = NioBytesStorageAllocator.this;

        public BytesStorageWrapper(JFSStorage storage, Name name, JavaFileManager.Location loc) {
            this.storage = storage;
            this.name = name;
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
                Exceptions.printStackTrace(ex);
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

package org.nemesis.jfs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 *
 * @author Tim Boudreau
 */
final class HeapBytesStorageImpl implements JFSBytesStorage {

    private final JFSStorage storage;
    private byte[] bytes;
    private volatile long lastModified = 0;
    private volatile boolean writing;

    HeapBytesStorageImpl(JFSStorage storage) {
        this.storage = storage;
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

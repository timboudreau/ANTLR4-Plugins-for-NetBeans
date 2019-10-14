package org.nemesis.jfs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.logging.Level;
import static org.nemesis.jfs.JFS.LOG;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * Allows a file to masquerade as an element in the filesystem.
 *
 * @author Tim Boudreau
 */
final class FileBytesStorageWrapper implements JFSBytesStorage {

    private final JFSStorage storage;
    final Path path;
    private byte[] bytes;
    private volatile long lastModifiedAtLoad = 0;
    private final Charset encoding;

    FileBytesStorageWrapper(JFSStorage storage, Path path, Charset encoding) {
        this.storage = storage;
        this.path = path;
        this.encoding = encoding;
    }

    @Override
    public Charset encoding() {
        if (encoding != null) {
            return encoding;
        }
        Charset result = JFSUtilities.encodingFor(path);
        return result == null ? JFSBytesStorage.super.encoding() : result;
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Override
    public JFSStorageKind storageKind() {
        return JFSStorageKind.MASQUERADED_FILE;
    }

    /*
    @Override
    public InputStream openInputStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path), 512);
    }

    @Override
    public ByteBuffer asByteBuffer() throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) ch.size());
            ch.read(buf);
            buf.flip();
            return buf;
        }
    }
     */
    @Override
    public byte[] asBytes() throws IOException {
        boolean nukeBytes = lastModifiedAtLoad != lastModified();
        synchronized (this) {
            if (nukeBytes) {
                bytes = null;
            } else if (bytes != null) {
                return bytes;
            }
            try {
                bytes = Files.readAllBytes(path);
            } catch (ClosedByInterruptException ex) {
                // Clear the flag
                Thread.interrupted();
                // WTF?
                LOG.log(Level.FINE, "Read of FileBytesStorageWrapper for " + path
                        + "closed by interrupt. Rereading and then setting "
                        + "interrupted flag.", ex);
                bytes = Files.readAllBytes(path);
//                Thread.currentThread().interrupt();
            } catch (NoSuchFileException ex) {
                throw new MappedObjectDeletedException(ex);
            }
        }
        return bytes;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new IOException("Will not overwrite files on disk");
    }

    @Override
    public long lastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            JFS.LOG.log(Level.WARNING, "Could not get modified time for " + path, ex);
            return 0;
        }
    }

    @Override
    public JFSStorage storage() {
        return storage;
    }

    @Override
    public synchronized void discard() {
        bytes = null;
    }

    @Override
    public int length() {
        byte[] b;
        synchronized (this) {
            b = bytes;
        }
        if (b != null && b.length > 0) {
            return b.length;
        }
        try {
            return (int) Files.size(path);
        } catch (IOException ex) {
            JFS.LOG.log(Level.WARNING, "Could not get size of " + path, ex);
            return 0;
        }
    }

    @Override
    public void setBytes(byte[] bytes, long lastModified) {
        throw new UnsupportedOperationException("Writes not supported on files.");
    }

}

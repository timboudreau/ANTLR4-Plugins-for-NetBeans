package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileUtil;

/**
 * Allows a file to masquerade as an element in the filesystem.
 *
 * @author Tim Boudreau
 */
final class FileBytesStorageWrapper implements JFSBytesStorage {

    private final JFSStorage storage;
    private final Path path;
    private byte[] bytes;
    private volatile long lastModifiedAtLoad = 0;

    public FileBytesStorageWrapper(JFSStorage storage, Path path) {
        this.storage = storage;
        this.path = path;
    }

    @Override
    public Charset encoding() {
        Charset charset = null;
        File file = FileUtil.normalizeFile(path.toFile());
        org.openide.filesystems.FileObject fo = FileUtil.toFileObject(file);
        if (fo != null) {
            charset = FileEncodingQuery.getEncoding(fo);
        }
        return charset != null ? charset : JFSBytesStorage.super.encoding();
    }

    @Override
    public byte[] asBytes() throws IOException {
        boolean nukeBytes = lastModifiedAtLoad != lastModified();
        synchronized (this) {
            if (nukeBytes) {
                bytes = null;
            } else if (bytes != null) {
                return bytes;
            }
        }
        byte[] b = Files.readAllBytes(path);
        synchronized (this) {
            bytes = b;
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

package org.nemesis.jfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import javax.tools.JavaFileManager.Location;

/**
 *
 * @author Tim Boudreau
 */
class JFSFileObjectImpl implements JFSFileObject {

    final JFSBytesStorage storage;
    private final Location location;
    private final Name name;
    private final Charset encoding;
    private volatile boolean deleted;

    JFSFileObjectImpl(JFSBytesStorage storage, Location location, Name name, Charset encoding) {
        this.storage = storage;
        this.location = location;
        this.name = name;
        this.encoding = encoding;
    }

    private void checkDeleted() throws IOException {
        if (deleted) {
            throw new IOException(name + " was already deleted");
        }
    }

    @Override
    public JFSJavaFileObjectImpl toJavaFileObject() {
        if (this instanceof JFSJavaFileObjectImpl) {
            return ((JFSJavaFileObjectImpl) this);
        }
        return new JFSJavaFileObjectImpl(storage, location, name, encoding);
    }

    @Override
    public URI toUri() {
        try {
            return new URI(toURLString());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public URL toURL() {
        try {
            return new URL(toURLString());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String toURLString() {
        return "jfs://" + storage.storage().urlPath() + "/" + name;
    }

    @Override
    public String toString() {
        String result = name + " (" + storage.length() + ")";
        if (storage instanceof DocumentBytesStorageWrapper) {
            result += " -> " + storage;
        }
        return result;
    }

    @Override
    public boolean packageMatches(String pkg) {
        return name.packageMatches(pkg);
    }

    public Name name() {
        return name;
    }

    @Override
    public String getName() {
        return name.toString();
    }

    @Override
    public void setBytes(byte[] bytes, long lastModified) throws IOException {
        checkDeleted();
        storage.setBytes(bytes, lastModified);
    }

    @Override
    public ByteBuffer asByteBuffer() throws IOException {
        checkDeleted();
        return storage.asByteBuffer();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        checkDeleted();
        return storage.openInputStream();
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        checkDeleted();
        return storage.openOutputStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream(), storage.encoding());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return storage.asCharBuffer(ignoreEncodingErrors);
    }

    @Override
    public Writer openWriter() throws IOException {
        checkDeleted();
        return new OutputStreamWriter(openOutputStream(), storage.encoding());
    }

    @Override
    public long getLastModified() {
        return deleted ? Long.MAX_VALUE : storage.lastModified();
    }

    @Override
    public int length() {
        return storage.length();
    }

    @Override
    public boolean delete() {
        storage.discard();
        boolean result = storage.storage().delete(name, storage);
        if (result) {
            deleted = true;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof JFSFileObjectImpl) {
            JFSFileObjectImpl oth = (JFSFileObjectImpl) o;
            return storage.storage().id().equals(oth.storage.storage().id())
                    && name.equals(oth.name) && location.getName().equals(oth.location.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(storage.storage().id(), name, location.getName());
    }

    public byte[] asBytes() throws IOException {
        checkDeleted();
        return storage.asBytes();
    }
}

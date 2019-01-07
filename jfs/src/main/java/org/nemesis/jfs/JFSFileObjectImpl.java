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

    private final JFSBytesStorage storage;
    private final Location location;
    private final Name name;
    private final Charset encoding;

    public JFSFileObjectImpl(JFSBytesStorage storage, Location location, Name name, Charset encoding) {
        this.storage = storage;
        this.location = location;
        this.name = name;
        this.encoding = encoding;
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

    public String toString() {
        return name + " (" + storage.length() + ")";
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
        storage.setBytes(bytes, lastModified);
    }

    @Override
    public ByteBuffer asByteBuffer() throws IOException {
        return storage.asByteBuffer();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return storage.openInputStream();
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
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
        return new OutputStreamWriter(openOutputStream(), storage.encoding());
    }

    @Override
    public long getLastModified() {
        return storage.lastModified();
    }

    @Override
    public int length() {
        return storage.length();
    }

    @Override
    public boolean delete() {
        storage.discard();
        return storage.storage().delete(name, storage);
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
        return storage.asBytes();
    }
}

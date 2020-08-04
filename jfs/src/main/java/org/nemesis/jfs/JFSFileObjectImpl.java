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

import com.mastfrog.util.path.UnixPath;
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
import java.security.MessageDigest;
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

    @Override
    public JFSStorageKind storageKind() {
        return storage.storageKind();
    }

    private void checkDeleted() throws IOException {
        if (deleted) {
            throw new IOException(name + " was already deleted");
        }
    }

    public JFSFileObjectImpl setTextContent(String txt) throws IOException {
        setBytes(txt.getBytes(storage.encoding()), System.currentTimeMillis());
        return this;
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

    String toURLString() {
        return "jfs://" + storage.storage().urlPath() + "/" + name;
    }

    @Override
    public JFSFileReference toReference() {
        return new JFSFileReference(toURLString());
    }

    @Override
    public JFSFileCoordinates toCoordinates() {
        return new JFSFileCoordinates(name.toPath(), storage.storage().location());
    }

    @Override
    public UnixPath path() {
        return name.toPath();
    }

    @Override
    public String toString() {
        String result = name + " (" + storage.length() + ")";
//        if (storage instanceof DocumentBytesStorageWrapper
//                || storage instanceof FileBytesStorageWrapper) {
        result += " -> " + storage;
//        }
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
    public byte[] hash() throws IOException {
        if (storage instanceof HashingStorage) {
            byte[] result = ((HashingStorage) storage).hash();
            if (result != null) {
                return result;
            }
        }
        return JFSFileObject.super.hash();
    }

    @Override
    public void hash(MessageDigest digest) throws IOException {
        if (storage instanceof HashingStorage && ((HashingStorage) storage).hash(digest)) {
            return;
        }
        JFSFileObject.super.hash(digest);
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
        return new InputStreamReader(openInputStream(), encoding == null ? storage.encoding() : encoding);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return storage.asCharBuffer(ignoreEncodingErrors);
    }

    @Override
    public Writer openWriter() throws IOException {
        checkDeleted();
        return new OutputStreamWriter(openOutputStream(), encoding == null ? storage.encoding() : encoding);
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

    @Override
    public byte[] asBytes() throws IOException {
        checkDeleted();
        return storage.asBytes();
    }
}

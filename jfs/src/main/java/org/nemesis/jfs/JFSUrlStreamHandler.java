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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.UnknownHostException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import static org.nemesis.jfs.JFSUrlStreamHandlerFactory.URL_PATTERN;

final class JFSUrlStreamHandler extends URLStreamHandler {

    private final Supplier<Iterable<JFS>> filesystemsProvider;

    // do nothing
    JFSUrlStreamHandler(Supplier<Iterable<JFS>> filesystemsProvider) {
        this.filesystemsProvider = filesystemsProvider;
    }

    String availableFileSystemIds() {
        StringBuilder sb = new StringBuilder("[");
        for (JFS jfs : filesystemsProvider.get()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(jfs.id());
        }
        return sb.append(']').toString();
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        Matcher m = URL_PATTERN.matcher(u.toString());
        if (!m.find()) {
            throw new IOException("Invalid storage URL: " + u);
        }
        String fsid = m.group(1);
        JFS instance = null;
        for (JFS jfs : filesystemsProvider.get()) {
            if (jfs.is(fsid)) {
                instance = jfs;
                break;
            }
        }
        if (instance == null) {
            throw new FileNotFoundException("No JFS filesystem for id '" + fsid + "' " + "to satisfy " + u + " - perhaps it was garbage " + "collected? Available: " + availableFileSystemIds());
        }
        String loc = m.group(2);
        String path = m.group(3);
        JFSFileObject fo = instance.find(loc, path);
        return new JFSURLConnection(u, fo);
    }

    JFSFileObject resolve(String url) throws FileNotFoundException {
        Matcher m = URL_PATTERN.matcher(url);
        if (!m.find()) {
            return null;
        }
        String fsid = m.group(1);
        JFS instance = null;
        for (JFS jfs : filesystemsProvider.get()) {
            if (jfs.is(fsid)) {
                instance = jfs;
                break;
            }
        }
        if (instance == null) {
            return null;
        }
        String loc = m.group(2);
        String path = m.group(3);
        return instance.find(loc, path);
    }

    @Override
    protected synchronized InetAddress getHostAddress(URL u) {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            try {
                return Inet4Address.getByAddress(new byte[]{127, 0, 0, 1});
            } catch (UnknownHostException ex1) {
                return null;
            }
        }
    }

    static final class JFSURLConnection extends URLConnection {

        private final JFSFileObject file;

        JFSURLConnection(URL url, JFSFileObject file) {
            super(url);
            this.file = file;
            setUseCaches(false);
        }

        @Override
        public void connect() throws IOException {
            // do nothing
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return file.openOutputStream();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return file.openInputStream();
        }

        @Override
        public long getDate() {
            return file.getLastModified();
        }

        @Override
        public int getContentLength() {
            return file.length();
        }
    }
} 

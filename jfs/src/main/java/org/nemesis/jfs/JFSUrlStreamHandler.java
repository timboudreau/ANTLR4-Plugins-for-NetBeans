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

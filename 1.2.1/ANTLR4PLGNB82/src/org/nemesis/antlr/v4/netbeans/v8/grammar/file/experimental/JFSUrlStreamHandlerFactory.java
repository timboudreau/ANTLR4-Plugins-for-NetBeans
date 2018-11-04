package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.util.Lookup;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;

/**
 * Allows URLs into this memory filesystem to be resolved.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = URLStreamHandlerFactory.class)
public final class JFSUrlStreamHandlerFactory implements URLStreamHandlerFactory {

    public static final String PROTOCOL = "jfs";
    private static JFSUrlStreamHandlerFactory INSTANCE;
    private final Set<JFS> filesystems = new WeakSet<>();
    private final Throwable backtrace;

    public JFSUrlStreamHandlerFactory() {
        if (INSTANCE != null) {
            if (INSTANCE.backtrace != null) {
                throw new AssertionError("Constructed more than once",
                        INSTANCE.backtrace);
            } else {
                throw new Error("Constructed more than once");
            }
        }
        INSTANCE = this;
        backtrace = allocationBacktrace();
    }

    @SuppressWarnings("AssertWithSideEffects")
    private final Throwable allocationBacktrace() {
        boolean asserts = false;
        assert asserts = true;
        if (asserts) {
            return new Exception();
        }
        return null;
    }

    static void register(JFS filesystem) {
        Lookup.getDefault().lookup(JFSUrlStreamHandlerFactory.class)._register(filesystem);
    }

    static void unregister(JFS filesystem) {
        Lookup.getDefault().lookup(JFSUrlStreamHandlerFactory.class)._unregister(filesystem);
    }

    // for tests of deregistration
    static boolean noLongerRegistered(int filesystemIdentityHashCode) {
        for (JFS jfs : INSTANCE.filesystems) {
            if (System.identityHashCode(jfs) == filesystemIdentityHashCode) {
                return false;
            }
        }
        return true;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (PROTOCOL.equals(protocol)) {
            return new JFSUrlStreamHandler();
        }
        return null;
    }

    private void _register(JFS filesystem) {
        filesystems.add(filesystem);
    }

    private void _unregister(JFS filesystem) {
        filesystems.remove(filesystem);
    }

    static final Pattern URL_PATTERN = Pattern.compile(
            "^jfs\\:\\/\\/([^/]+?)\\/([^/]+?)\\/(.*)$");

    static final class JFSUrlStreamHandler extends URLStreamHandler {

        static String availableFileSystemIds() {
            StringBuilder sb = new StringBuilder("[");
            for (JFS jfs : INSTANCE.filesystems) {
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
            for (JFS jfs : INSTANCE.filesystems) {
                if (jfs.is(fsid)) {
                    instance = jfs;
                    break;
                }
            }
            if (instance == null) {
                throw new FileNotFoundException("No JFS filesystem for id '" + fsid + "' "
                        + "to satisfy " + u + " - perhaps it was garbage "
                        + "collected? Available: "
                        + availableFileSystemIds());
            }
            String loc = m.group(2);
            JFSStorage storage = instance.storageForLocation(loc);
            if (storage == null) {
                throw new FileNotFoundException("JFS '" + fsid + " does not contain a location '"
                        + loc + "' to satisfy " + u);
            }
            String path = m.group(3);
            JFSFileObjectImpl fo = storage.find(Name.forFileName(path), false);
            if (fo == null) {
                throw new FileNotFoundException("No file " + path + " in " + storage);
            }
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

            private final JFSFileObjectImpl file;

            public JFSURLConnection(URL url, JFSFileObjectImpl file) {
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
}

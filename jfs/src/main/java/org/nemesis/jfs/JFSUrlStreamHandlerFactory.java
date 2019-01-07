package org.nemesis.jfs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * Allows URLs into this memory filesystem to be resolved.
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = URLStreamHandlerFactory.class)
public final class JFSUrlStreamHandlerFactory implements URLStreamHandlerFactory {

    public static final String PROTOCOL = "jfs";
    private static JFSUrlStreamHandlerFactory INSTANCE;
    private final Set<JFS> filesystems = JFSUtilities.newWeakSet();
    private final Throwable backtrace;
    public static final Pattern URL_PATTERN = Pattern.compile(
            "^jfs\\:\\/\\/([^/]+?)\\/([^/]+?)\\/(.*)$");

    /**
     * Do not instantiate directly, use
     * <code>ServiceLoader.load(JFSUrlStreamHandlerFactory.class)</code> or
     * <code>Lookup.getDefault().lookup(JFSUrlStreamHandlerFactory.class)</code>.
     * The default constructor is only for its use, and will throw an Error if
     * constructed subsequently.
     */
    public JFSUrlStreamHandlerFactory() {
        if (INSTANCE != null) {
            if (INSTANCE.backtrace != null) {
                throw new Error("Constructed more than once",
                        INSTANCE.backtrace);
            } else {
                throw new Error("Constructed more than once");
            }
        }
        INSTANCE = this;
        backtrace = allocationBacktrace();
    }

    @SuppressWarnings("AssertWithSideEffects")
    private Throwable allocationBacktrace() {
        boolean asserts = false;
        assert asserts = true;
        if (asserts) {
            return new Exception("Initial instance");
        }
        return null;
    }

    private static JFSUrlStreamHandlerFactory getDefault() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        JFSUrlStreamHandlerFactory result = getViaLookupReflectively(JFSUrlStreamHandlerFactory.class);
        if (result != null) {
            return result;
        }
        ServiceLoader<JFSUrlStreamHandlerFactory> ldr = ServiceLoader.load(JFSUrlStreamHandlerFactory.class);
        if (ldr.iterator().hasNext()) {
            return ldr.iterator().next();
        }
        return INSTANCE = new JFSUrlStreamHandlerFactory();
    }

    static <T> T getViaLookupReflectively(Class<T> type) {
        try {
            Class<?> Lookup = Class.forName("org.openide.util.Lookup");
            Method getDefault = Lookup.getMethod("getDefault");
            Object defaultLookup = getDefault.invoke(null);
            assert defaultLookup != null : "Lookup.getDefault() returned null";
            Method lookup = defaultLookup.getClass().getMethod("lookup", Class.class);
            return (T) lookup.invoke(defaultLookup, type);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(JFSUrlStreamHandlerFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    static void register(JFS filesystem) {
        getDefault()._register(filesystem);
    }

    static void unregister(JFS filesystem) {
        getDefault()._unregister(filesystem);
    }

    // for tests of deregistration
    public static boolean noLongerRegistered(int filesystemIdentityHashCode) {
        for (JFS jfs : INSTANCE.filesystems) {
            if (System.identityHashCode(jfs) == filesystemIdentityHashCode) {
                return false;
            }
        }
        return true;
    }

    private Iterable<JFS> filesystems() {
        return filesystems;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (PROTOCOL.equals(protocol)) {
            return new JFSUrlStreamHandler(this::filesystems);
        }
        return null;
    }

    private void _register(JFS filesystem) {
        filesystems.add(filesystem);
    }

    private void _unregister(JFS filesystem) {
        filesystems.remove(filesystem);
    }

}

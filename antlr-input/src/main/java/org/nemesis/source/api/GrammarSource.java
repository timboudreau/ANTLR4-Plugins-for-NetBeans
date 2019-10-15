package org.nemesis.source.api;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.nemesis.source.impl.GSAccessor;
import org.nemesis.source.spi.GrammarSourceImplementation;

/**
 * Abstraction for files, documents or even text strings which can provide
 * input, and can resolve sibling documents/files/whatever.
 *
 * @author Tim Boudreau
 */
public final class GrammarSource<T> implements Serializable {

    private final GrammarSourceImplementation<T> impl;

    static final GrammarSource<Object> NONE = new GrammarSource<>(new None());

    @SuppressWarnings("unchecked")
    public static <X> GrammarSource<X> none() {
        return (GrammarSource<X>) NONE;
    }

    private GrammarSource(GrammarSourceImplementation<T> impl) {
        assert impl != null : "impl null";
        this.impl = impl;
    }

    public final String name() {
        return GSAccessor.getDefault().nameOf(impl);
    }

    public final CharStream stream() throws IOException {
        return GSAccessor.getDefault().stream(impl);
    }

    public final GrammarSource<?> resolveImport(String name) {
        return GSAccessor.getDefault().resolve(impl, name);
    }

    public final T source() throws IOException {
        return GSAccessor.getDefault().source(impl);
    }

    public final long lastModified() throws IOException {
        return GSAccessor.getDefault().lastModified(impl);
    }

    public final <L extends Lexer> Supplier<L> toLexerSupplier(Function<CharStream, L> f) {
        return () -> {
            try {
                return f.apply(stream());
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        };
    }

    public final <R> Optional<R> lookup(Class<R> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.ofNullable(GSAccessor.getDefault().lookup(impl, type));
    }

    public final <R> boolean lookup(Class<R> type, Consumer<R> ifPresent) {
        Optional<R> val = lookup(type);
        if (val.isPresent()) {
            ifPresent.accept(val.get());
        }
        return val.isPresent();
    }

    @Override
    public final String toString() {
        return impl.toString();
    }

    @Override
    public final int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.impl);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GrammarSource<?> other = (GrammarSource<?>) obj;
        return Objects.equals(this.impl, other.impl);
    }

    public static <T> GrammarSource<T> find(T document, String mimeType) {
        return GSAccessor.getDefault().newGrammarSource(mimeType, document);
    }

    private String id;

    /**
     * Get an identifier for this data source. The id must be usable as a file
     * name (no path characters), and for grammar sources that are backed by a
     * file, should always return the same value for the same file.
     * <p>
     * The default implementation calls
     * <code>lookup(Path.class).get().toURI()</code> and takes the SHA-256 hash
     * of that (if a Path instance is present). If no Path instance is present,
     * it will read the bytes from the <code>stream()</code> method and hash
     * that. Implementations are encouraged to override this with more efficient
     * means of creating an ID.
     * </p>
     *
     * @return An ID, non-null
     * @throws IOException
     */
    public final String id() {
        if (this.id != null) {
            return this.id;
        }
        String foundId = computeId();
        if (foundId == null) {
            foundId = defaultId();
        } else {
            if (foundId.indexOf(File.separatorChar) >= 0 || foundId.indexOf(File.pathSeparatorChar) >= 0) {
                Logger.getLogger(GrammarSource.class.getName()).log(Level.SEVERE,
                        "Id computed by {0} {1} contains path or directory "
                        + "separator characters. Using default",
                        new Object[]{getClass().getName(), name()});
                foundId = defaultId();
            }
        }
        return this.id = foundId;
    }

    private String computeId() {
        return GSAccessor.getDefault().computeId(impl);
    }

    final String defaultId() {
        Optional<Path> path = lookup(Path.class);
        if (path.isPresent()) {
            return hashString(path.get().toUri().toString());
        }
        Optional<File> file = lookup(File.class);
        if (file.isPresent()) {
            return hashString(toURI(file.get()).toString());
        }
        try {
            CharStream stream = stream();
            int ix = stream.index();
            try {
                stream.seek(0);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = new byte[4];
                for (int i = 1, type = stream.LA(i); type != CharStream.EOF; i++, type = stream.LA(i)) {
                    ByteBuffer buf = ByteBuffer.wrap(bytes);
                    buf.putInt(type);
                    digest.update(bytes);
                }
                return byteArrayToString(digest.digest());
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(GrammarSource.class.getName()).log(Level.SEVERE, null, ex);
                return Integer.toString(System.identityHashCode(source()), 36);
            } finally {
                stream.seek(ix);
            }
        } catch (IOException ioe) {
            Logger.getLogger(GrammarSource.class.getName()).log(Level.SEVERE, null, ioe);
            try {
                return Integer.toString(System.identityHashCode(source()), 36);
            } catch (IOException ex) {
                Logger.getLogger(GrammarSource.class.getName()).log(Level.SEVERE, null, ex);
                return Integer.toString(System.identityHashCode(this), 36);
            }
        }
    }

    private static String byteArrayToString(byte[] bytes) {
        StringBuilder hashed = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xff & bytes[i]);
            if (hex.length() == 1) {
                hashed.append('0');
            }
            hashed.append(hex);
        }
        return hashed.toString();
    }

    private static String hashString(String str) {
        try {
            // XXX to do this more flexibly and correctly, hash the *tokens*
            // after alpha-sorting the rules - that would be whitespace and
            // rule order independent
            // Now we hash the project path in a string of constant length
            MessageDigest digest1 = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = digest1.digest(str.getBytes("UTF-8"));
            return byteArrayToString(hash1);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            Logger.getLogger(GrammarSource.class.getName()).log(Level.SEVERE,
                    "Hashing failed", ex);
            // SHA-1 cannot be unsupported
            // UTF-8 cannot be unsupported
            return str.replace(File.separatorChar, '_')
                    .replace('.', '-')
                    .replace(' ', '_')
                    .replace('\n', '_')
                    .replace('\t', '_');
        }
    }

    static final class GSAccessorImpl extends GSAccessor {

        @Override
        public <T> GrammarSource<T> newGrammarSource(GrammarSourceImplementation<T> impl) {
            return new GrammarSource<>(impl);
        }

        public String hashString(String string) {
            return GrammarSource.hashString(string);
        }
    }

    static {
        GSAccessor.DEFAULT = new GSAccessorImpl();
    }


    private static final class None extends GrammarSourceImplementation<Object> {

        None() {
            super(Object.class);
        }

        @Override
        public String name() {
            return "<disposed>";
        }

        @Override
        public CharStream stream() throws IOException {
            return CharStreams.fromString("");
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            return null;
        }

        @Override
        public Object source() {
            return this;
        }

        @Override
        public String toString() {
            return "<disposed-grammar-source>";
        }

        @Override
        public long lastModified() throws IOException {
            return 0;
        }
    }

    // Copy of BaseUtilities from NetBeans to avoid dependency
    private static final Logger LOG = Logger.getLogger(GrammarSource.class.getName());
    private static Boolean pathURIConsistent;
    private static boolean pathToURISupported() {
        Boolean res = pathURIConsistent;
        if (res == null) {
            boolean c;
            try {
                final File f = new File("küñ"); //NOI18N
                c = f.toPath().toUri().equals(f.toURI());
            } catch (InvalidPathException e) {
                c = false;
            }
            if (!c) {
                LOG.fine("The java.nio.file.Path.toUri is inconsistent with java.io.File.toURI");   //NOI18N
            }
            res = pathURIConsistent = c;
        }
        return res;
    }

    /**
     * Converts a file to a URI while being safe for UNC paths.
     * Uses {@link File f}.{@link File#toPath() toPath}().{@link java.nio.file.Path#toUri() toUri}()
     * which results into {@link URI} that works with {@link URI#normalize()}
     * and {@link URI#resolve(URI)}.
     * @param f a file
     * @return a {@code file}-protocol URI which may use the host field
     * @see java.nio.file.Path.toUri
     * @since 8.25
     */
    public static URI toURI(File f) {
        URI u;
        if (pathToURISupported()) {
            try {
                u = f.toPath().toUri();
            } catch (java.nio.file.InvalidPathException ex) {
                u = f.toURI();
                LOG.log(Level.FINE, "can't convert " + f + " falling back to " + u, ex);
            }
        } else {
            u = f.toURI();
        }
        if (u.toString().startsWith("file:///")) {
            try {
                // #214131 workaround
                return new URI(
                    /* "file" */u.getScheme(), /* null */u.getUserInfo(),
                    /* null (!) */u.getHost(), /* -1 */u.getPort(),
                    /* "/..." */u.getPath(), /* null */u.getQuery(),
                    /* null */u.getFragment()
                );
            } catch (URISyntaxException ex) {
                LOG.log(Level.FINE, "could not convert " + f + " to URI", ex);
            }
        }
        return u;
    }
}

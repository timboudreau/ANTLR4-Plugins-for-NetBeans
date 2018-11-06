package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.filesystems.MultiFileSystem;
import org.openide.filesystems.Repository;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;

/**
 * Resolves custom MIME types generated from Antlr grammar file paths.
 * Note that we *must* use the deprecated constructor which does not
 * specify a particular MIME type, since we handle multiple types and
 * they are unknown at initialization-time and the list can change.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = MIMEResolver.class, position = Integer.MAX_VALUE - 1)
@SuppressWarnings("deprecation")
public class AdhocMimeResolver extends MIMEResolver {

    private final int[] codes;

    @SuppressWarnings("deprecation")
    public AdhocMimeResolver() {
        // In order to have the fastest possible rejection test of whether
        // a file is interesting or not, cache the array of identity hash codes
        // of all members of the system filesystem.  This is subject to change if
        // modules are loaded on the fly, but will still give us a fast negative
        // test for the common case.  Since this resolver may be called for
        // every single file in the known universe, it can slow down *all*
        // operations of the IDE - this minimizes that.
        org.openide.filesystems.FileSystem configFileSystem = Repository.getDefault().getDefaultFileSystem();
        Set<Integer> idHashCodes = new HashSet<>();
        idHashCodes.add(System.identityHashCode(configFileSystem));
        if (configFileSystem instanceof MultiFileSystem) {
            MultiFileSystem mfs = (MultiFileSystem) configFileSystem;
            findFileSystems(mfs, idHashCodes);
        }
        int[] codes = (int[]) Utilities.toPrimitiveArray(idHashCodes.toArray(new Integer[idHashCodes.size()]));
        Arrays.sort(codes);
        this.codes = codes;
    }

    private static void findFileSystems(MultiFileSystem mfs, Set<Integer> all) {
        // Reflectively get the current set of all file systems that are
        // participating in the system filesystem.  It is possible that this
        // will change if modules are loaded and unloaded, but it still improves
        // performance of negative tests in the common case - we do not need to
        // do a bunch of work to determine if every .instance or .shadow file in
        // the system filesystem might be an Antlr grammar generated mime type
        // during startup, since they won't be.
        try {
            Field field = MultiFileSystem.class.getDeclaredField("systems");
            field.setAccessible(true);
            synchronized (mfs) {
                FileSystem[] fses = (FileSystem[]) field.get(mfs);
                if (fses != null && fses.length > 0) {
                    for (FileSystem fs : fses) {
                        int hashCode = System.identityHashCode(fs);
                        if (!all.contains(hashCode)) {
                            all.add(hashCode);
                            if (fs instanceof MultiFileSystem) {
                                findFileSystems((MultiFileSystem) fs, all);
                            }
                        }
                    }
                }
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(AdhocMimeResolver.class.getName()).log(Level.INFO, "Failed reflecting MultiFileSystem " + mfs, ex);
        }
    }

    private boolean isConfigFileSystem(FileObject fo) {
        try {
            int code = System.identityHashCode(fo.getFileSystem());
            return Arrays.binarySearch(codes, code) >= 0;
        } catch (FileStateInvalidException ex) {
            Logger.getLogger(AdhocMimeResolver.class.getName()).log(Level.FINE, "Dead file " + fo.getPath(), ex);
            return true;
        }
    }

    private boolean isUninteresting(FileObject fo) {
        if (isConfigFileSystem(fo)) {
            // In the system filesystem, the only things we ever want to
            // resolve are sample grammars
            String pth = fo.getPath();
            if (!pth.startsWith("antlr/grammar-samples")) {
                return true;
            }
        }
        // Weed out standard file extensions that can't possibly be
        // relevant
        try {
            String ext = fo.getExt();
            if (ext == null || FileUtil.isArchiveArtifact(fo)) {
                return true;
            }
            switch (ext) {
                case "":
                case "java":
                case "class":
                case "jar":
                case "zip":
                case "gz":
                case "bz2":
                case "xz":
                case "shadow":
                case "instance":
                case "xml":
                case "txt":
                case "md":
                case "html":
                case "js":
                case "c":
                case "g4":
                case "g":
                case "interp":
                case "tokens":
                case "properties":
                    return true;
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return false;
    }

    static final class Pair {
        private final Reference<FileObject> fo;
        private final String type;

        public Pair(FileObject fo, String type) {
            this.fo = fo == null ? null : new WeakReference<FileObject>(fo);
            this.type = type;
        }

        String typeOf(FileObject fo) {
            if (this.fo != null && this.fo.get() == fo) {
                return type;
            }
            return null;
        }
    }

    private String setLast(FileObject fo, String mime) {
        last.set(new Pair(fo, mime));
        return mime;
    }

    private final AtomicReference<Pair> last = new AtomicReference<>(new Pair(null, null));

    @Override
    public String findMIMEType(FileObject fo) {
        if (isUninteresting(fo)) {
            return null;
        }
        // We are frequently asked multiple times in a row for the type for
        // the same file, so cache the result and use it where possible
        String previousResult = last.get().typeOf(fo);
        if (previousResult != null) {
            return previousResult;
        }
        String ext = fo.getExt();
        if (ext != null && !ext.isEmpty()) {
            if (AdhocMimeTypes.isAdhocMimeTypeFileExtension(ext) || AdhocMimeTypes.isRegisteredExtension(ext)) {
                String mime = AdhocMimeTypes.mimeTypeForFileExtension(ext);
                if (mime != null) {
                    return setLast(fo, mime);
                }
            } else {
                System.out.println("not an adhoc extension: " + ext + " for " + fo.getPath()
                        + " registered: " + AdhocMimeTypes.EXTENSIONS_REGISTRY.toString());
            }
        }
        return null;
    }
}

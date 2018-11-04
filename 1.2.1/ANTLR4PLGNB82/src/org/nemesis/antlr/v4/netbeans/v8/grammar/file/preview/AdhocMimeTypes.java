package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nemesis.antlr.v4.netbeans.v8.util.TimedCache;
import org.nemesis.antlr.v4.netbeans.v8.util.TimedCache.BidiCache;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.EditableProperties;
import org.openide.util.RequestProcessor;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.grammarFilePathForMimeType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.InvalidMimeTypeRegistrationException.Reason;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;
import org.openide.util.Parameters;

/**
 * As a way to freely pass references to a grammar file throughout the vast
 * plumbing of parsing and lexing, and dealing with registration by mime type,
 * we generate mime types on a per-grammar-file basis which include the complete
 * file path, with various munging in order to meet the limitations of mime
 * parsing in MimeType and MimePath, both of which impose limitations greater
 * than that of the actual spec for mime types. Basically the only common thread
 * that runs across all of the infrastructure involved is mime types.
 * <p>
 * What would be preferable is to generate something like:
 * <pre>
 * text/x-antlr-$GRAMMAR_NAME; path="/path/to/SomeGrammar.g4"
 * </pre> What we actually have to do looks like this (in the simple case):
 * <pre>
 * text/a-somegrammar$$path$$to$$SomeGrammar
 * </pre>
 * </p><p>
 * The format is the prefix text/x-antlr- followed by the lowercased name of the
 * grammar file sans extension, followed by the absolute path to the grammar
 * file with file separators replaced with double dollars signs sans extension:
 * <code>text/a-$grammarNameLowerCase$$path$$delimited$$by$$doubleDollarSigns</code>
 * </p><p>
 * Since these can be somewhat huge strings, a convenience method for logging
 * purposes, <code>loggableMimeType</code> will trim out the file path for
 * readability.
 * </p><p>
 * That's the simple case, where the path can be fully encoded into the mime
 * type unambiguously. In the case of special characters, we *could* encode them
 * as code-points ala URLs, but alas MimePath also rejects any mime type longer
 * than 127 characters, and such escaping inflates the character count (hence
 * the short text/a- prefix rather than something prettier like text/x-antlr-).
 * </p><p>
 * With no good means of encoding *all possible* grammar file paths into the
 * mime type, what we do is this: Generate a hash of the path which will be
 * unique enough not to collide, using a different mime type prefix and
 * concatenate that, the grammar name and the hash as base36. If that still
 * results in an unusable mime type length-wise (really long grammar name), then
 * we omit the grammar name, which is less readable in logs but will always
 * work.
 * </p>
 * <p>
 * The mapping between these mime types and paths is then stored in a file in
 * the system filesystem (on deserializing a topcomponent, what we have is the
 * mime type, not necessarily the grammar file it originated from).
 * </p>
 * <p>
 * Ain't pretty but it solves the problem.
 * </p>
 *
 * @author Tim Boudreau
 */
public class AdhocMimeTypes {

    private static final Logger LOG = Logger.getLogger(AdhocMimeTypes.class.getName());

    static {
        LOG.setLevel(Level.SEVERE);
    }

    static BidiCache<Path, String, RuntimeException> typeForPath
            = TimedCache.create(30000, AdhocMimeTypes::_mimeTypeForGrammarFilePath)
                    .toBidiCache(AdhocMimeTypes::_pathForMimeType);

    static BidiCache<String, String, RuntimeException> typeForExtension
            = TimedCache.create(30000, AdhocMimeTypes::_fileExtensionForMimeType)
                    .toBidiCache(AdhocMimeTypes::_mimeTypeForFileExtension);

    private static TimedCache<String, String, RuntimeException> friendlyForMimeType
            = TimedCache.create(10000, AdhocMimeTypes::_friendlyNameForMimeType);

    private static TimedCache<Path, Boolean, RuntimeException> fileExtensionForPath
            = TimedCache.create(30000, AdhocMimeTypes::_isAdhocMimeTypeFileExtension);

    private static TimedCache<String, Boolean, RuntimeException> isFileExtension
            = TimedCache.create(30000, AdhocMimeTypes::_isAdhocMimeTypeFileExtension);

    private static final String ALL_MIME_TYPES_CATEGORY = "text/";
    public static final String FILE_EXTENSION_PREFIX = "alr_";
    private static final String LONG_INFIX = "x-antlr-";
    private static final String LONG_WITH_NAME_INFIX = "l-";
    private static final String SIMPLE_INFIX = "a-";
    private static final String MIME_TYPE_PREFIX = ALL_MIME_TYPES_CATEGORY + SIMPLE_INFIX;
    private static final String MIME_TYPE_PREFIX_LONG = ALL_MIME_TYPES_CATEGORY + LONG_INFIX;
    private static final String MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME = ALL_MIME_TYPES_CATEGORY
            + LONG_WITH_NAME_INFIX;
    public static final String INVALID_MIME_TYPE = ALL_MIME_TYPES_CATEGORY + "unresolvable-antlr";
    private static final ComplexMimeTypeMapper COMPLEX_MAPPER = new ComplexMimeTypeMapper();
    static final GrammarFileExtensionRegistry EXTENSIONS_REGISTRY
            = new GrammarFileExtensionRegistry();

    private static void checkMimeType(String mimeType) {
        if (!isAdhocMimeType(mimeType)) {
            throw new IllegalArgumentException(mimeType
                    + " is not an Antlr-module generated mime type");
        }
    }

    public static void listenForRegistrations(BiConsumer<String, String> listener) {
        EXTENSIONS_REGISTRY.listen(listener);
    }

    private static boolean isRegisterableMimeType(String mimeType) {
        if (isAdhocMimeType(mimeType)) {
            return true;
        }
        if (!mimeType.startsWith("text/")) {
            return false;
        }
        FileObject fo = FileUtil.getConfigFile("Editors/" + mimeType);
        if (fo != null) {
            return false;
        }
        return true;
    }

    public static void validatePotentialRegistration(String extension, String mimeType) throws InvalidMimeTypeRegistrationException {
        if (extension == null || extension.isEmpty()) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.EMPTY_EXTENSION);
        }
        if (extension.charAt(0) == '.') {
            extension = extension.substring(1);
        }
        if (isCommonExtension(extension)) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.COMMON_EXTENSION);
        }
        for (int i = 0; i < extension.length(); i++) {
            char c = extension.charAt(i);
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '_' && c != '-') {
                throw new InvalidMimeTypeRegistrationException(new String(
                        new char[]{'\'', c, '\''}), mimeType, Reason.INVALID_EXTENSION);
            }
        }
        if (EXTENSIONS_REGISTRY.allExtensions().contains(extension)) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.IN_USE);
        }
        if (mimeType == null || mimeType.isEmpty()) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.EMPTY_MIME_TYPE);
        }
        try {
            MimePath.parse(mimeType);
        } catch (Exception e) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.INVALID_MIME_TYPE);
        }
        if (!isAdhocMimeType(mimeType)) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.INVALID_MIME_TYPE);
        }
        if (!isAdhocMimeType(mimeType) && !isRegisterableMimeType(mimeType)) {
            throw new InvalidMimeTypeRegistrationException(extension, mimeType, Reason.INVALID_MIME_TYPE);
        }
    }

    public static void unregisterFileExtension(String mimeType, String extension) {
        typeForExtension.remove(mimeType);
        isFileExtension.remove(extension);
        EXTENSIONS_REGISTRY.unregister(extension, mimeType);
    }

    public static void unregisterMimeType(String mimeType) {
        Path pth = _pathForMimeType(mimeType);
        if (pth != null) {
            String defExt = _fileExtensionForMimeType(mimeType);
            Set<String> extensions = allExtensionsForMimeType(mimeType);
            EXTENSIONS_REGISTRY.unregister(mimeType);
            typeForExtension.remove(mimeType);
            friendlyForMimeType.remove(mimeType);
            typeForPath.remove(pth);
            isFileExtension.remove(defExt);
            for (String ext : extensions) {
                isFileExtension.remove(ext);
            }
        }
    }

    public static boolean registerFileNameExtension(String extension, String mimeType) throws InvalidMimeTypeRegistrationException {
        Parameters.notNull("extension", extension);
        Parameters.notNull("mimeType", mimeType);
        validatePotentialRegistration(extension, mimeType);
        boolean newlyRegistered = EXTENSIONS_REGISTRY.register(extension, mimeType);
        if (newlyRegistered) {
            isFileExtension.remove(extension);
        }
        return newlyRegistered;
    }

    public static Set<String> getRegisteredFileNameExtensions() {
        return EXTENSIONS_REGISTRY.allExtensions();
    }

    public static Set<String> registeredExtensionsFor(String mimeType) {
        checkMimeType(mimeType);
        Set<String> result = new LinkedHashSet<>(EXTENSIONS_REGISTRY.extsForMimeType(mimeType));
        return result;
    }

    public static Set<String> allExtensionsForMimeType(String mimeType) {
        Set<String> result = registeredExtensionsFor(mimeType);
        result.add(AdhocMimeTypes.fileExtensionFor(mimeType));
        return result;
    }

    public static boolean isRegisteredExtension(String ext) {
        return EXTENSIONS_REGISTRY.isRegisteredExtension(ext);
    }

    private AdhocMimeTypes() {
        throw new AssertionError();
    }

    /**
     * Determine if a mime type is one of ours.
     *
     * @param mimeType The mime type
     * @return True if it looks like one of ours
     */
    public static boolean isAdhocMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        if (!mimeType.startsWith(ALL_MIME_TYPES_CATEGORY)) {
            return false;
        }
        if (mimeType.startsWith(MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME)) {
            return mimeType.length() > MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME.length();
        }
        if (mimeType.startsWith(MIME_TYPE_PREFIX_LONG)) {
            return mimeType.length() > MIME_TYPE_PREFIX_LONG.length();
        }
        return mimeType.startsWith(MIME_TYPE_PREFIX)
                && mimeType.contains("$$");
    }

    public static boolean isMimeTypeWithExistingGrammar(String mimeType) {
        if (isAdhocMimeType(mimeType) || EXTENSIONS_REGISTRY.isRegisteredMimeType(mimeType)) {
            Path path = grammarFilePathForMimeType(mimeType);
            if (path != null) {
                return Files.exists(path);
            }
            return false;
        }
        return false;
    }

    public static String rawGrammarNameForMimeType(String mimeType) {
        Path pth = grammarFilePathForMimeType(mimeType);
        return pth == null ? null : rawFileName(pth);
    }

    /**
     * Get the mime type for a path to a grammar file.
     *
     * @param path The grammar file
     * @return A mime type
     */
    public static String mimeTypeForPath(Path path) {
        if (isAdhocMimeTypeFileExtension(path)) {
            return _mimeTypeForFilePath(path);
        }
        String ext = extensionFor(path);
        if (ext != null && !ext.isEmpty() && isRegisteredExtension(ext)) {
            return EXTENSIONS_REGISTRY.mimeTypeForExt(ext);
        }
        return typeForPath.get(path);
    }

    public static String mimeTypeForFileExtension(String ext) {
        if (ext == null || ext.isEmpty() || isCommonExtension(ext)) {
            return null;
        }
        String result = EXTENSIONS_REGISTRY.mimeTypeForExt(ext);
        if (result == null) {
            result = typeForExtension.getKey(ext);
        }
        return result;
    }

    /**
     * Get the grammar file path encoded in a mime type. Note that this method
     * does <i>not</i> check that the file actually exists.
     *
     * @param mimeType A mime type
     * @return A file path or null
     */
    public static Path grammarFilePathForMimeType(String mimeType) {
        Path result = typeForPath.getKey(mimeType);
        if (result != null && result.toString().startsWith("text/")) {
            throw new IllegalStateException("Cache returning a mime type as "
                    + "a path: " + result + ": " + typeForPath);
        }
        return result;
    }

    /**
     * Reduce a lengthy antlr mime type to a simple one for logging purposes.
     *
     * @param mimeType
     * @return
     */
    public static String loggableMimeType(String mimeType) {
        return friendlyForMimeType.get(mimeType);
    }

    /**
     * Get a file extension for this mime type. This is the second half of the
     * mime type, with . characters replaced with dashes and double-dollar-signs
     * replaced with underscores. It is used to allow sample files to be opened
     * with syntax support from the grammar file in question, and is used to
     * store sample code for various types in the system filesystem.
     *
     * @param mimeType A mime type
     * @return The file extension
     */
    public static String fileExtensionFor(String mimeType) {
        return typeForExtension.get(mimeType);
    }

    /**
     * Determine if a file extension is one created from an adhoc mime type.
     *
     * @param path A file path
     * @return
     */
    public static boolean isAdhocMimeTypeFileExtension(Path path) {
        String ext = extensionFor(path);
        if (ext == null || ext.isEmpty()) {
            return false;
        }
        return isAdhocMimeTypeFileExtension(ext);
    }

    public static boolean isAdhocMimeTypeFileExtension(String ext) {
        if (isCommonExtension(ext)) {
            return false;
        }
        return isFileExtension.get(ext);
    }

    private static boolean isCommonExtension(String extension) {
        switch (extension) {
            case "java":
            case "php":
            case "html":
            case "form":
            case "xsl":
            case "txt":
            case "class":
            case "xml":
            case "properties":
            case "g4":
            case "mf":
            case "ser":
            case "png":
            case "gif":
            case "jpg":
            case "c":
            case "py":
            case "js":
            case "css":
                return true;
        }
        return false;
    }

    // Implementation methods fromm here down
    private static boolean _isAdhocMimeTypeFileExtension(Path path) {
        String ext = extensionFor(path);
        return _isAdhocMimeTypeFileExtension(ext);
    }

    private static boolean _isAdhocMimeTypeFileExtension(String ext) {
        if (ext == null || ext.isEmpty()) {
            return false;
        }
        if (isRegisteredExtension(ext)) {
            return true;
        }
        if (!ext.startsWith(FILE_EXTENSION_PREFIX) || ext.length() <= FILE_EXTENSION_PREFIX.length()) {
            return false;
        }
        String subname = ext.substring(FILE_EXTENSION_PREFIX.length());
        boolean isSimple = subname.startsWith(SIMPLE_INFIX) && subname.length() > SIMPLE_INFIX.length();
        boolean isLong = subname.startsWith(LONG_INFIX) && subname.length() > LONG_INFIX.length();
        boolean isLongWithGrammarName = subname.startsWith(LONG_WITH_NAME_INFIX)
                && subname.length() > LONG_WITH_NAME_INFIX.length();
        return isSimple || isLong || isLongWithGrammarName;
    }

    private static String extensionFor(Path path) {
        if (path.getFileName() == null) {
            return "";
        }
        String name = path.getFileName().toString();
        int ix = name.lastIndexOf('.');
        if (ix >= 0 && ix < name.length() - 1) {
            return name.substring(ix + 1);
        }
        return null;
    }

    private static String _mimeTypeForFilePath(Path path) {
        String ext = extensionFor(path);
        return _mimeTypeForFileExtension(ext);
    }

    private static String _mimeTypeForFileExtension(String ext) {
        if (ext.contains("/")) {
            throw new IllegalStateException("Not a valid file extension: '" + ext + "'");
        }
        if (ext == null || ext.isEmpty()) {
            return null;
        }
        if (isRegisteredExtension(ext)) {
            String result = EXTENSIONS_REGISTRY.mimeTypeForExt(ext);
            return result;
        }
        if (ext.length() <= FILE_EXTENSION_PREFIX.length()) {
            return null;
        }
        String subname = ext.substring(FILE_EXTENSION_PREFIX.length());
        return ALL_MIME_TYPES_CATEGORY + subname;
    }

    private static String _fileExtensionForMimeType(String mimeType) {
        if (isAdhocMimeType(mimeType)) {
            int ix = mimeType.indexOf('/');
            if (ix > 0) {
                return FILE_EXTENSION_PREFIX + mimeType.substring(ix + 1).replace('.', '_');
            }
        }
        return null;
    }

    private static String _friendlyNameForMimeType(String s) {
        if (s.startsWith(MIME_TYPE_PREFIX)) {
            int ix = s.indexOf("$$");
            if (ix > 0) {
                return s.substring(0, ix);
            }
        }
        if (s.startsWith(MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME) || s.startsWith(MIME_TYPE_PREFIX_LONG)) {
            Path p = grammarFilePathForMimeType(s);
            if (p != null) {
                String raw = rawFileName(p);
                if (raw.length() > 24) {
                    raw = raw.substring(0, 24) + "...";
                }
                return ALL_MIME_TYPES_CATEGORY + "x-" + raw.toLowerCase()
                        + "-" + pathHashString(p);
            }
            int ix = s.lastIndexOf("-");
            String result = s.substring(0, ix);
            if (result.length() > 1) {
                if (result.charAt(result.length() - 1) == '-') {
                    result = result.substring(0, result.length() - 1);
                }
            }
        }
        return s;
    }

    private static String _mimeTypeForProblematicPath(Path path) {
        return COMPLEX_MAPPER._mimeTypeForPath(path);
    }

    private static String _mimeTypeForGrammarFilePath(Path path) {
        String ext = extensionFor(path);
        if (!"g4".equals(ext)) {
            return null;
        }
        int nc = path.getNameCount();
        if (nc < 1) {
            throw new IllegalArgumentException("Empty path");
        }
        // Sigh - MIMEPath can handle up to 127 chars, but something
        // in the bowels of the project infrastructure tries to set
        // the mime type as a Preferences key, and Preferences complains
        // that that is too long a key.  So, Preferences limit of 80
        // is what we're stuck with
        if (path.toString().length() > 80) {
            return _mimeTypeForProblematicPath(path);
        }
        for (int i = 0; i < path.getNameCount(); i++) {
            String test = path.getName(i).toString();
            if (test.isEmpty()) {
                throw new IllegalArgumentException("Empty path");
            }
            if (".".equals(test) || "..".equals(test)) {
                throw new IllegalArgumentException("Mime types for "
                        + "paths must be absolute: " + path);
            }
            if (test.contains("$")) {
                return _mimeTypeForProblematicPath(path);
            }
        }
        String grammarName = path.getFileName().toString()
                .replaceAll("\\s", "")
                .replaceAll("/", "$$")
                .toLowerCase(); // paranoia
        if (grammarName.length() == 0 || grammarName.charAt(0) == '.') {
            return _mimeTypeForProblematicPath(path);
        }
        int ix = grammarName.lastIndexOf('.');
        if (ix > 0) {
            grammarName = grammarName.substring(0, ix);
        }
        String pathString = path.toString();
        if (!pathString.startsWith("/")) {
            throw new IllegalArgumentException("Mime type for relative"
                    + " path not usable: " + pathString);
        }
        ix = pathString.lastIndexOf('.');
        if (ix > 0) {
            pathString = pathString.substring(0, ix);
        }
        pathString = pathString.replace('.', '_');
        String result = MIME_TYPE_PREFIX + grammarName + pathString.replaceAll("/", "\\$\\$");
        if (!Objects.equals(path, _pathForMimeType(result))) {
            return _mimeTypeForProblematicPath(path);
        }
        try {
            MimePath.parse(result);
        } catch (IllegalArgumentException ex) {
            String oldResult = result;
            result = _mimeTypeForProblematicPath(path);
            LOG.log(Level.INFO, "Could not generate a legal mime type for {0} the "
                    + "default way - ''{1}'' was invalid - used alternate "
                    + "strategy to get ''{2}''", new Object[]{path, oldResult, result});
        }
        return result;
    }

    private static Path _pathForProblematicMimeType(String mimeType) {
        return COMPLEX_MAPPER.pathForMimeType(mimeType);
    }

    private static Path _pathForMimeType(String mimeType) {
        if (mimeType.startsWith(MIME_TYPE_PREFIX_LONG) || mimeType.startsWith(MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME)) {
            return _pathForProblematicMimeType(mimeType);
        }
        String[] result = extractPathAndSimpleMimeType(mimeType);
        return result == null ? null : Paths.get(result[2]);
    }

    static final Pattern pattern
            = Pattern.compile("(" + MIME_TYPE_PREFIX + "(\\S*?))(\\$\\$.*)$");

    static String[] extractPathAndSimpleMimeType(String mimeType) {
        Matcher m = pattern.matcher(mimeType);
        if (m.find()) {
            String pth = m.group(3).replaceAll("\\$\\$", "/");
            return new String[]{m.group(1), m.group(2), pth + ".g4"};
        }
        return null;
    }

    static String rawFileName(Path path) {
        String result = path.getFileName().toString();
        int ix = result.lastIndexOf('.');
        if (ix >= 0 && ix < result.length() - 1) {
            result = result.substring(0, ix);
        }
        return result;
    }

    static long charsHash(String s) {
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            result += prime(i + 1, false) * (s.charAt(i) + 503);
        }
        return result;
    }

    static long pathHash(Path path) {
        int count = path.getNameCount();
        long hash = count * 224491;
        for (int i = 0; i < count; i++) {
            String elem = path.getName(i).toString();
            hash += prime(i, true) * charsHash(elem);
        }
        return hash;
    }

    private static long prime(int val, boolean set) {
        int[] pr = set ? primes2 : primes;
        return pr[val % pr.length];
    }

    static char parentFolderFirstCharacter(Path p) {
        if (p.getParent() == null || p.getParent().getFileName() == null) {
            return '_';
        }
        return p.getParent().getFileName().toString().charAt(0);
    }

    static String pathHashString(Path path) {
        StringBuilder sb = new StringBuilder();
        sb.append(longAsString(pathHash(path)));
        char c = parentFolderFirstCharacter(path);
        if (isAsciiNumeric(c) || isAsciiAlphabetic(c)) {
            sb.insert(0, c);
        }
        return sb.toString();
    }

    static boolean isAsciiAlphabetic(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isAsciiNumeric(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isLegal(char c) {
        return isAsciiAlphabetic(c) || isAsciiNumeric(c)
                || c == '-' || c == '_';
    }

    static boolean isAsciiPunctuation(char c) {
        switch (c) {
            case ';':
            case ':':
            case '.':
            case '"':
            case '(':
            case ')':
            case '[':
            case ']':
            case '{':
            case '}':
            case '?':
            case '=':
            case '+':
            case '#':
            case '@':
            case '!':
            case ',':
            case '\'':
            case '%':
            case '^':
                return true;
            default:
                return false;
        }
    }

    static boolean isWhitespaceOrPunctuation(char c) {
        return Character.isWhitespace(c) || isAsciiPunctuation(c);
    }

    static String longAsString(long val) {
        String result = Long.toString(val, 36);
        if (result.charAt(0) == '-') {
            result = '$' + result.substring(1);
        }
        return result;
    }

    private static String mimeSafeLegalRawFileName(Path path) {
        String s = rawFileName(path);
        StringBuilder sb = new StringBuilder();
        boolean lastWasPunctuation = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isLegal(c)) {
                sb.append(c);
                lastWasPunctuation = false;
            } else if (isWhitespaceOrPunctuation(c)) {
                if (!lastWasPunctuation) {
                    if (i != s.length() - 1 && i != 0) {
                        sb.append('_');
                    }
                }
                lastWasPunctuation = true;
            }
        }
        return sb.toString();
    }

    // methods for unit tests
    static void _clearCaches() {
        isFileExtension.clear();
        typeForPath.clear();
        friendlyForMimeType.clear();
        typeForExtension.clear();
        fileExtensionForPath.clear();
    }

    static void _reinit() throws IOException {
        _clearCaches();
        COMPLEX_MAPPER.reinit(false);
        EXTENSIONS_REGISTRY.reinit(false);
    }

    static void _reinitAndDeleteCache() throws IOException {
        _clearCaches();
        COMPLEX_MAPPER.reinit(true);
        EXTENSIONS_REGISTRY.reinit(true);
    }

    static final class ComplexMimeTypeMapper {

        private static final String SFS_FOLDER = "antlr";
        private static final String SFS_CACHE_FILE = "antlr-generated-mime-type-paths.properties";
        static final String SFS_PATH = SFS_FOLDER + "/" + SFS_CACHE_FILE;
        final Map<String, Path> pathForMimeType = new ConcurrentHashMap<>(10);
        final Map<Path, String> mimeTypeForPath = new ConcurrentHashMap<>(10);
        private boolean loaded;

        synchronized void reinit(boolean deleteCache) throws IOException {
            loaded = false;
            loading = false;
            pathForMimeType.clear();
            mimeTypeForPath.clear();
            if (deleteCache) {
                FileObject fo = FileUtil.getConfigFile(SFS_PATH);
                if (fo != null) {
                    fo.delete();
                }
            }
        }

        public Path pathForMimeType(String type) {
            ensureCacheLoaded();
            return pathForMimeType.get(type);
        }

        public String mimeTypeForPath(Path path) {
            String result = mimeTypeForPath.get(path);
            if (result == null) {
                result = _mimeTypeForPath(path);
            }
            return result;
        }

        private String _mimeTypeForPath(Path path) {
            String raw = mimeSafeLegalRawFileName(path).toLowerCase();
            String charsHash = pathHashString(path);
            if (!raw.isEmpty()) {
                String result = MIME_TYPE_PREFIX_LONG_WITH_LEGAL_NAME + raw
                        + '-' + charsHash;
                try {
                    MimePath.parse(result);
                    return ensureCached(path, result);
                } catch (IllegalArgumentException e) {
                    LOG.log(Level.WARNING, "Invented mime type ''{0}''"
                            + " for {1} not parsed by MimePath; trying "
                            + "without file name", new Object[]{result, path});
                }
            }
            StringBuilder sb = new StringBuilder(MIME_TYPE_PREFIX_LONG);
            sb.append(charsHash);
            String result = sb.toString();
            try {
                MimePath.parse(result);
                return ensureCached(path, result);
            } catch (IllegalArgumentException ex) {
                // The above should work for anything, so something is
                // wrong if we get here
                LOG.log(Level.SEVERE, "Could not invent a mime type "
                        + "for {0} - MimePath could not parse {1}", ex);
                return AdhocMimeTypes.INVALID_MIME_TYPE;
            }
        }

        private String ensureCached(Path path, String mimeType) {
            String oldMime = mimeTypeForPath.put(path, mimeType);
            Path oldPath = pathForMimeType.put(mimeType, path);
            if (oldMime == null || oldPath == null) {
                flushCache();
            }
            return mimeType;
        }

        private synchronized void flushCache() {
            if (loading) {
                return;
            }
            try {
                _flushCache();
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Exception flushing mime type cache", ioe);
            }
        }

        private boolean loading;

        private synchronized void ensureCacheLoaded() {
            if (!loaded) {
                loading = true;
                try {
                    loaded = true;
                    _ensureCacheLoaded();
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Could not load mime cache", ex);
                } finally {
                    loading = false;
                }
            }
        }

        private void _ensureCacheLoaded() throws IOException {
            FileObject fo = FileUtil.getConfigFile(SFS_PATH);
            if (fo != null) {
                LOG.log(Level.FINER, "Read mime-to-path cache {0}", fo.getPath());
                try (InputStream in = fo.getInputStream()) {
                    EditableProperties props = new EditableProperties(false);
                    props.load(in);
                    for (Map.Entry<String, String> e : props.entrySet()) {
                        ensureCached(Paths.get(e.getValue()), e.getKey());
                    }
                }
            }
        }

        private void _flushCache() throws IOException {
            List<String> types = new ArrayList<>(pathForMimeType.keySet());
            if (types.isEmpty()) {
                return;
            }
            FileObject fo = FileUtil.getConfigFile(SFS_PATH);
            if (fo == null) {
                FileObject folder = FileUtil.getConfigRoot().getFileObject(SFS_FOLDER);
                if (folder == null) {
                    folder = FileUtil.createFolder(FileUtil.getConfigRoot(), SFS_FOLDER);
                }
                fo = folder.createData(SFS_CACHE_FILE);
                LOG.log(Level.FINER, "Created mime to path cache {0}", fo.getPath());
            }
            EditableProperties props = new EditableProperties(true);
            for (String key : types) {
                Path path = pathForMimeType.get(key);
                if (path != null) { // we are using a snapshot of the cache, so it's possible
                    props.setProperty(key, path.toString());
                }
            }
            try (OutputStream out = fo.getOutputStream()) {
                props.store(out);
            }
        }
    }

    // Some collision-proofing
    private static final int[] primes = {701, 719, 769, 787, 811, 1231, 1429, 1439, 1481, 1607, 255163, 255367, 255431,
        255515, 255601, 255673, 255827, 256201, 256555, 256565, 256577, 256615, 256693,
        256753, 256787, 256891, 256913, 256915, 256931, 256985, 257011, 257267, 257333,
        565791, 565961, 566201, 566351, 566363, 566399, 566459, 566493, 566571, 566753,
        567327, 567347, 567441, 567491, 567657, 567669, 568257, 896351, 896417, 896523,
        896537, 897039, 897297, 897443, 897543, 897641, 897719, 897863, 897963, 898133,
        898697};

    private static final int[] primes2 = {
        73, 107, 109, 353, 373, 431, 433, 443, 653, 739, 751, 761, 823, 829, 1061, 1129, 1321,
        1399, 1459, 1471, 1523, 1583, 1597, 1601, 1721, 1823, 1861, 1913, 1951, 255011,
        255205, 255487, 255725, 255781, 255817, 255871, 255901, 256033, 256051, 256073,
        256213, 256355, 256357, 256433, 256763, 256835, 257057, 257075, 257213, 257243,
        257357, 565937, 566003, 566069, 566073, 566123, 566271, 566361, 566379, 566421,
        566537, 566721, 567083, 567141
    };

    static class GrammarFileExtensionRegistry {

        private static final String SFS_FOLDER = "antlr";
        private static final String SFS_CACHE_FILE = "mime-type-extensions.properties";
        static final String SFS_PATH = SFS_FOLDER + "/" + SFS_CACHE_FILE;

        final SetSupplyingMap extensionsForMimeType = new SetSupplyingMap();
        final Map<String, String> mimeTypeForExtension = new ConcurrentHashMap<>();
        private final List<Reference<BiConsumer<String, String>>> listeners
                = new LinkedList<>();
        private static RequestProcessor THREAD_POOL = new RequestProcessor("GrammarFileExtensionRegistry", 1);

        private boolean loading;
        private boolean loaded;

        public String toString() {
            StringBuilder sb = new StringBuilder("extensionsForMimeType:\n");
            for (Map.Entry<String, Set<String>> e : extensionsForMimeType.entrySet()) {
                sb.append('\t').append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            sb.append("mimeTypeForExtension:\n");
            for (Map.Entry<String, String> e : mimeTypeForExtension.entrySet()) {
                sb.append('\t').append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            return sb.toString();
        }

        void reinit(boolean deleteCache) throws IOException {
            if (deleteCache) {
                THREAD_POOL.shutdown();
            }
            extensionsForMimeType.clear();
            mimeTypeForExtension.clear();
            loaded = false;
            loading = false;
            listeners.clear();
            if (deleteCache) {
                FileObject fo = FileUtil.getConfigFile(SFS_PATH);
                if (fo != null) {
                    fo.delete();
                }
            }
            THREAD_POOL = new RequestProcessor("GrammarFileExtensionRegistry", 1);
        }

        public synchronized GrammarFileExtensionRegistry listen(BiConsumer<String, String> listener) {
            listeners.add(new LoggableWeakReference<>(listener));
            return this;
        }

        static final class LoggableWeakReference<T> extends WeakReference<T> {

            private final String stringValue;

            LoggableWeakReference(T obj) {
                super(obj);
                this.stringValue = obj.toString() + " (" + obj.getClass().getName() + ")";
            }

            public String toString() {
                return stringValue;
            }
        }

        private void notifyListeners(String ext, String mimeType) {
            List<Reference<BiConsumer<String, String>>> copy = new LinkedList<>();
            synchronized (this) {
                copy.addAll(listeners);
            }
            for (Iterator<Reference<BiConsumer<String, String>>> it = listeners.iterator(); it.hasNext();) {
                Reference<BiConsumer<String, String>> ref = it.next();
                BiConsumer<String, String> listener = ref.get();
                if (listener == null) {
                    it.remove();
                } else {
                    try {
                        listener.accept(ext, mimeType);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Exception notifying listener of " + mimeType + " -> " + ext, e);
                    }
                }
            }
        }

        public GrammarFileExtensionRegistry visitAll(BiConsumer<String, String> cons) {
            checkLoaded();
            extensionsForMimeType.visitAll(cons);
            return this;
        }

        public Set<String> allExtensions() {
            checkLoaded();
            return new HashSet<>(mimeTypeForExtension.keySet());
        }

        public String mimeTypeForExt(String ext) {
            checkLoaded();
            return mimeTypeForExtension.get(ext);
        }

        public Set<String> extsForMimeType(String mimeType) {
            checkLoaded();
            Set<String> result = extensionsForMimeType.get(mimeType);
            return result == null ? Collections.emptySet() : new LinkedHashSet<>(result);
        }

        public boolean isRegisteredMimeType(String mt) {
            checkLoaded();
            return extensionsForMimeType.containsKey(mt);
        }

        public boolean isRegisteredExtension(String ext) {
            checkLoaded();
            return mimeTypeForExtension.containsKey(ext);
        }

        private boolean firstLoad = true;

        public void unregister(String mimeType) {
            Set<String> exts = extensionsForMimeType.remove(mimeType);
            for (String ext : exts) {
                mimeTypeForExtension.remove(ext);
            }
            save();
        }

        public void unregister(String ext, String mimeType) {
            if (extensionsForMimeType.removeElement(mimeType, ext)) {
                mimeTypeForExtension.remove(ext);
                save();
            }
        }

        public boolean register(String ext, String mimeType) {
            checkLoaded();
            if (!loading && !AdhocMimeTypes.isAdhocMimeType(mimeType)) {
                throw new IllegalArgumentException("Not an adhoc mime type: " + mimeType);
            }
            if (loading) {
                // ugly, but seems the earliest place we can get things initialized in
                // time to catch any data objects
                if (firstLoad) {
                    firstLoad = false;
                    Enumeration<DataLoader> ldrs = DataLoaderPool.getDefault().producersOf(AdhocDataObject.class);
                    while (ldrs.hasMoreElements()) {
                        ldrs.nextElement(); // force initialization
                    }
                }
            }
            boolean isNew = extensionsForMimeType.putValue(mimeType, ext) && !loading;
            if (isNew) {
                mimeTypeForExtension.put(ext, mimeType);
                if (!save()) {
                    THREAD_POOL.post(this::save, 100);
                }
                THREAD_POOL.post(() -> {
                    notifyListeners(ext, mimeType);
                }, 200);
            }

            return isNew;
        }

        private void checkLoaded() {
            if (loading) {
                return;
            }
            if (!loaded) {
                load();
            }
        }

        private synchronized boolean save() {
            if (loading) {
                return false;
            }
            try {
                _save();
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Exception saving explicit registry", ioe);
            }
            return true;
        }

        private synchronized void _save() throws IOException {
            List<String> types = new ArrayList<>(extensionsForMimeType.keySet());
            if (types.isEmpty()) {
                return;
            }
            FileObject fo = FileUtil.getConfigFile(SFS_PATH);
            if (fo == null) {
                FileObject folder = FileUtil.getConfigRoot().getFileObject(SFS_FOLDER);
                if (folder == null) {
                    folder = FileUtil.createFolder(FileUtil.getConfigRoot(), SFS_FOLDER);
                }
                fo = folder.createData(SFS_CACHE_FILE);
                LOG.log(Level.FINER, "Created mime to path cache {0}", fo.getPath());
            }
            EditableProperties props = new EditableProperties(true);
            extensionsForMimeType.visitAll((mime, ext) -> {
                props.setProperty(ext, mime);
            });
            try (OutputStream out = fo.getOutputStream()) {
                props.store(out);
            }
        }

        private synchronized void load() {
            if (loaded) {
                return;
            }
            loading = true;
            try {
                _load();
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Exception saving explicit registry", ioe);
            } finally {
                loading = false;
                loaded = true;
            }
        }

        private void _load() throws IOException {
            FileObject fo = FileUtil.getConfigFile(SFS_PATH);
            if (fo != null) {
                LOG.log(Level.FINER, "Read explicit mime registrations cache {0}", fo.getPath());
                try (InputStream in = fo.getInputStream()) {
                    EditableProperties props = new EditableProperties(false);
                    props.load(in);
                    for (Map.Entry<String, String> e : props.entrySet()) {
                        register(e.getKey(), e.getValue());
                    }
                }
            }
        }

        private static final class SetSupplyingMap extends TreeMap<String, Set<String>> {

            public synchronized boolean containsKey(String key) {
                return super.containsKey(key);
            }

            public synchronized boolean removeElement(String key, String value) {
                Set<String> result = super.get(key);
                if (result != null) {
                    return result.remove(value);
                }
                return false;
            }

            public synchronized Set<String> remove(String key) {
                Set<String> result = super.remove(key);
                return result == null ? Collections.emptySet() : result;
            }

            @Override
            public synchronized Set<Map.Entry<String, Set<String>>> entrySet() {
                return super.entrySet();
            }

            public synchronized void visitAll(BiConsumer<String, String> cons) {
                for (Map.Entry<String, Set<String>> e : entrySet()) {
                    String ext = e.getKey();
                    for (String mime : e.getValue()) {
                        cons.accept(ext, mime);
                    }
                }
            }

            public synchronized Set<String> get(Object key, boolean create) {
                if (!(key instanceof String)) {
                    return null;
                }
                Set<String> result = super.get((String) key);
                if (result == null && create) {
                    result = new TreeSet<>();
                    super.put((String) key, result);
                }
                return result;
            }

            public synchronized boolean putValue(String key, String value) {
                Set<String> result = get(key, true);
                return result.add(value);
            }

            @Override
            public Set<String> put(String key, Set<String> value) {
                throw new UnsupportedOperationException("Should not be called");
            }
        }
    }
}

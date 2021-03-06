package org.nemesis.registration.typenames;

import com.mastfrog.annotation.AnnotationUtils;
import java.util.EnumSet;
import java.util.Set;

/**
 * <b>DO NOT MODIFY THIS FILE!</b> This file is generated by analyzing the POMs
 * and class files used in all subprojects of theparent project for class names
 * that are likely to be used in codegeneration. It will be regenerated at build
 * time.
 **/
public enum JdkTypes implements TypeName {

    AUTO_CLOSEABLE("java.lang.AutoCloseable"),
    CHAR_SEQUENCE("java.lang.CharSequence"),
    COMPARABLE("java.lang.Comparable"),
    ITERABLE("java.lang.Iterable"),
    BOOLEAN("java.lang.Boolean"),
    BYTE("java.lang.Byte"),
    CHARACTER("java.lang.Character"),
    CLASS("java.lang.Class"),
    CLASS_LOADER("java.lang.ClassLoader"),
    ENUM("java.lang.Enum"),
    DOUBLE("java.lang.Double"),
    FLOAT("java.lang.Float"),
    INTEGER("java.lang.Integer"),
    LONG("java.lang.Long"),
    MATH("java.lang.Math"),
    NUMBER("java.lang.Number"),
    OBJECT("java.lang.Object"),
    PACKAGE("java.lang.Package"),
    PROCESS("java.lang.Process"),
    PROCESS_BUILDER("java.lang.ProcessBuilder"),
    RUNTIME("java.lang.Runtime"),
    SHORT("java.lang.Short"),
    STRING("java.lang.String"),
    STRING_BUILDER("java.lang.StringBuilder"),
    THREAD("java.lang.Thread"),
    THREAD_LOCAL("java.lang.ThreadLocal"),
    THROWABLE("java.lang.Throwable"),
    VOID("java.lang.Void"),
    SYSTEM("java.lang.System"),
    ILLEGAL_ARGUMENT_EXCEPTION("java.lang.IllegalArgumentException"),
    ILLEGAL_STATE_EXCEPTION("java.lang.IllegalStateException"),
    NULL_POINTER_EXCEPTION("java.lang.NullPointerException"),
    UNSUPPORTED_OPERATION_EXCEPTION("java.lang.UnsupportedOperationException"),
    SUPPRESS_WARNINGS("java.lang.SuppressWarnings"),
    AWTEVENT("java.awt.AWTEvent"),
    BASIC_STROKE("java.awt.BasicStroke"),
    COLOR("java.awt.Color"),
    COMPONENT("java.awt.Component"),
    CONTAINER("java.awt.Container"),
    CURSOR("java.awt.Cursor"),
    EVENT_QUEUE("java.awt.EventQueue"),
    FONT("java.awt.Font"),
    FRAME("java.awt.Frame"),
    IMAGE("java.awt.Image"),
    CLIPBOARD("java.awt.datatransfer.Clipboard"),
    STRING_SELECTION("java.awt.datatransfer.StringSelection"),
    LINE_METRICS("java.awt.font.LineMetrics"),
    BUFFERED_IMAGE("java.awt.image.BufferedImage"),
    PROPERTY_CHANGE_EVENT("java.beans.PropertyChangeEvent"),
    PROPERTY_CHANGE_LISTENER("java.beans.PropertyChangeListener"),
    PROPERTY_CHANGE_SUPPORT("java.beans.PropertyChangeSupport"),
    PROPERTY_VETO_EXCEPTION("java.beans.PropertyVetoException"),
    BUFFERED_INPUT_STREAM("java.io.BufferedInputStream"),
    CLOSEABLE("java.io.Closeable"),
    EXTERNALIZABLE("java.io.Externalizable"),
    FILE("java.io.File"),
    FILE_NOT_FOUND_EXCEPTION("java.io.FileNotFoundException"),
    IO_EXCEPTION("java.io.IOException"),
    INPUT_STREAM("java.io.InputStream"),
    OUTPUT_STREAM("java.io.OutputStream"),
    OUTPUT_STREAM_WRITER("java.io.OutputStreamWriter"),
    PRINT_STREAM("java.io.PrintStream"),
    PRINT_WRITER("java.io.PrintWriter"),
    SERIALIZABLE("java.io.Serializable"),
    STRING_WRITER("java.io.StringWriter"),
    WRITER("java.io.Writer"),
    ELEMENT_TYPE("java.lang.annotation.ElementType"),
    RETENTION("java.lang.annotation.Retention"),
    REFERENCE("java.lang.ref.Reference"),
    REFERENCE_QUEUE("java.lang.ref.ReferenceQueue"),
    SOFT_REFERENCE("java.lang.ref.SoftReference"),
    WEAK_REFERENCE("java.lang.ref.WeakReference"),
    URI("java.net.URI"),
    URI_SYNTAX_EXCEPTION("java.net.URISyntaxException"),
    URL("java.net.URL"),
    BUFFER_UNDERFLOW_EXCEPTION("java.nio.BufferUnderflowException"),
    BYTE_BUFFER("java.nio.ByteBuffer"),
    INT_BUFFER("java.nio.IntBuffer"),
    CLOSED_BY_INTERRUPT_EXCEPTION(
        "java.nio.channels.ClosedByInterruptException"),
    FILE_CHANNEL("java.nio.channels.FileChannel"),
    READABLE_BYTE_CHANNEL("java.nio.channels.ReadableByteChannel"),
    SEEKABLE_BYTE_CHANNEL("java.nio.channels.SeekableByteChannel"),
    WRITABLE_BYTE_CHANNEL("java.nio.channels.WritableByteChannel"),
    CHARSET("java.nio.charset.Charset"),
    ILLEGAL_CHARSET_NAME_EXCEPTION(
        "java.nio.charset.IllegalCharsetNameException"),
    STANDARD_CHARSETS("java.nio.charset.StandardCharsets"),
    UNSUPPORTED_CHARSET_EXCEPTION(
        "java.nio.charset.UnsupportedCharsetException"),
    COPY_OPTION("java.nio.file.CopyOption"),
    FILE_VISIT_OPTION("java.nio.file.FileVisitOption"),
    FILE_VISIT_RESULT("java.nio.file.FileVisitResult"),
    FILE_VISITOR("java.nio.file.FileVisitor"),
    FILES("java.nio.file.Files"),
    NO_SUCH_FILE_EXCEPTION("java.nio.file.NoSuchFileException"),
    PATH("java.nio.file.Path"),
    PATHS("java.nio.file.Paths"),
    STANDARD_COPY_OPTION("java.nio.file.StandardCopyOption"),
    STANDARD_OPEN_OPTION("java.nio.file.StandardOpenOption"),
    BASIC_FILE_ATTRIBUTES("java.nio.file.attribute.BasicFileAttributes"),
    DECIMAL_FORMAT("java.text.DecimalFormat"),
    NUMBER_FORMAT("java.text.NumberFormat"),
    DURATION("java.time.Duration"),
    INSTANT("java.time.Instant"),
    LOCAL_DATE_TIME("java.time.LocalDateTime"),
    ZONE_ID("java.time.ZoneId"),
    ZONED_DATE_TIME("java.time.ZonedDateTime"),
    DATE_TIME_FORMATTER("java.time.format.DateTimeFormatter"),
    ABSTRACT_LIST("java.util.AbstractList"),
    ABSTRACT_SET("java.util.AbstractSet"),
    ARRAY_LIST("java.util.ArrayList"),
    ARRAYS("java.util.Arrays"),
    BASE64("java.util.Base64"),
    BIT_SET("java.util.BitSet"),
    COLLECTION("java.util.Collection"),
    COLLECTIONS("java.util.Collections"),
    COMPARATOR("java.util.Comparator"),
    DATE("java.util.Date"),
    DICTIONARY("java.util.Dictionary"),
    ENUM_MAP("java.util.EnumMap"),
    ENUM_SET("java.util.EnumSet"),
    ENUMERATION("java.util.Enumeration"),
    HASH_MAP("java.util.HashMap"),
    HASH_SET("java.util.HashSet"),
    IDENTITY_HASH_MAP("java.util.IdentityHashMap"),
    ITERATOR("java.util.Iterator"),
    LINKED_HASH_MAP("java.util.LinkedHashMap"),
    LINKED_HASH_SET("java.util.LinkedHashSet"),
    LINKED_LIST("java.util.LinkedList"),
    LIST("java.util.List"),
    LIST_ITERATOR("java.util.ListIterator"),
    MAP("java.util.Map"),
    MISSING_RESOURCE_EXCEPTION("java.util.MissingResourceException"),
    NO_SUCH_ELEMENT_EXCEPTION("java.util.NoSuchElementException"),
    OBJECTS("java.util.Objects"),
    OPTIONAL("java.util.Optional"),
    PRIMITIVE_ITERATOR("java.util.PrimitiveIterator"),
    PROPERTIES("java.util.Properties"),
    RANDOM("java.util.Random"),
    SCANNER("java.util.Scanner"),
    SERVICE_LOADER("java.util.ServiceLoader"),
    SET("java.util.Set"),
    SORTED_SET("java.util.SortedSet"),
    SPLITERATOR("java.util.Spliterator"),
    STACK("java.util.Stack"),
    STRING_TOKENIZER("java.util.StringTokenizer"),
    TIMER("java.util.Timer"),
    TIMER_TASK("java.util.TimerTask"),
    TREE_MAP("java.util.TreeMap"),
    TREE_SET("java.util.TreeSet"),
    WEAK_HASH_MAP("java.util.WeakHashMap"),
    CALLABLE("java.util.concurrent.Callable"),
    COMPLETABLE_FUTURE("java.util.concurrent.CompletableFuture"),
    COMPLETION_STAGE("java.util.concurrent.CompletionStage"),
    CONCURRENT_HASH_MAP("java.util.concurrent.ConcurrentHashMap"),
    CONCURRENT_SKIP_LIST_SET("java.util.concurrent.ConcurrentSkipListSet"),
    COPY_ON_WRITE_ARRAY_LIST("java.util.concurrent.CopyOnWriteArrayList"),
    DELAY_QUEUE("java.util.concurrent.DelayQueue"),
    DELAYED("java.util.concurrent.Delayed"),
    EXECUTION_EXCEPTION("java.util.concurrent.ExecutionException"),
    EXECUTOR_SERVICE("java.util.concurrent.ExecutorService"),
    FUTURE("java.util.concurrent.Future"),
    SYNCHRONOUS_QUEUE("java.util.concurrent.SynchronousQueue"),
    TIME_UNIT("java.util.concurrent.TimeUnit"),
    ATOMIC_BOOLEAN("java.util.concurrent.atomic.AtomicBoolean"),
    ATOMIC_INTEGER("java.util.concurrent.atomic.AtomicInteger"),
    ATOMIC_INTEGER_FIELD_UPDATER(
        "java.util.concurrent.atomic.AtomicIntegerFieldUpdater"),
    ATOMIC_LONG("java.util.concurrent.atomic.AtomicLong"),
    ATOMIC_LONG_ARRAY("java.util.concurrent.atomic.AtomicLongArray"),
    ATOMIC_REFERENCE("java.util.concurrent.atomic.AtomicReference"),
    LOCK_SUPPORT("java.util.concurrent.locks.LockSupport"),
    BI_CONSUMER("java.util.function.BiConsumer"),
    BI_FUNCTION("java.util.function.BiFunction"),
    BI_PREDICATE("java.util.function.BiPredicate"),
    BOOLEAN_SUPPLIER("java.util.function.BooleanSupplier"),
    CONSUMER("java.util.function.Consumer"),
    FUNCTION("java.util.function.Function"),
    INT_CONSUMER("java.util.function.IntConsumer"),
    INT_FUNCTION("java.util.function.IntFunction"),
    INT_PREDICATE("java.util.function.IntPredicate"),
    INT_TO_DOUBLE_FUNCTION("java.util.function.IntToDoubleFunction"),
    INT_UNARY_OPERATOR("java.util.function.IntUnaryOperator"),
    LONG_CONSUMER("java.util.function.LongConsumer"),
    LONG_SUPPLIER("java.util.function.LongSupplier"),
    PREDICATE("java.util.function.Predicate"),
    SUPPLIER("java.util.function.Supplier"),
    TO_INT_FUNCTION("java.util.function.ToIntFunction"),
    UNARY_OPERATOR("java.util.function.UnaryOperator"),
    LEVEL("java.util.logging.Level"),
    LOGGER("java.util.logging.Logger"),
    BACKING_STORE_EXCEPTION("java.util.prefs.BackingStoreException"),
    NODE_CHANGE_LISTENER("java.util.prefs.NodeChangeListener"),
    PREFERENCE_CHANGE_EVENT("java.util.prefs.PreferenceChangeEvent"),
    PREFERENCE_CHANGE_LISTENER("java.util.prefs.PreferenceChangeListener"),
    PREFERENCES("java.util.prefs.Preferences"),
    MATCHER("java.util.regex.Matcher"),
    PATTERN("java.util.regex.Pattern"),
    STREAM_INT_STREAM("java.util.stream.IntStream"),
    STREAM("java.util.stream.Stream"),
    ACTION("javax.swing.Action"),
    ACTION_MAP("javax.swing.ActionMap"),
    BUTTON_MODEL("javax.swing.ButtonModel"),
    COMBO_BOX_MODEL("javax.swing.ComboBoxModel"),
    ICON("javax.swing.Icon"),
    IMAGE_ICON("javax.swing.ImageIcon"),
    KEY_STROKE("javax.swing.KeyStroke"),
    LIST_MODEL("javax.swing.ListModel"),
    LIST_SELECTION_MODEL("javax.swing.ListSelectionModel"),
    CHANGE_EVENT("javax.swing.event.ChangeEvent"),
    CHANGE_LISTENER("javax.swing.event.ChangeListener"),
    ABSTRACT_DOCUMENT("javax.swing.text.AbstractDocument"),
    ATTRIBUTE_SET("javax.swing.text.AttributeSet"),
    BAD_LOCATION_EXCEPTION("javax.swing.text.BadLocationException"),
    CARET("javax.swing.text.Caret"),
    DOCUMENT("javax.swing.text.Document"),
    EDITOR_KIT("javax.swing.text.EditorKit"),
    POSITION("javax.swing.text.Position"),
    SEGMENT("javax.swing.text.Segment"),
    STYLED_DOCUMENT("javax.swing.text.StyledDocument"),
    TEXT_ACTION("javax.swing.text.TextAction")
    ;
    private final String fqn;
    private final Libraries lib;
    private static final Set<Libraries> TOUCHED = EnumSet.noneOf(
                Libraries.class);

    private static final Set<Libraries> REPORTED = EnumSet.noneOf(
                Libraries.class);

    JdkTypes(String fqn, Libraries lib) {
        this.fqn = fqn;
        this.lib = lib;
    }

    JdkTypes(String fqn) {
        this.fqn = fqn;
        this.lib = null;
    }

    @Override
    public String toString() {
        return qnameNotouch() + ":" + fqn + "<-" + (lib == null ? "jdk" : lib);
    }

    /**
     * Fetch fully qualified name of the class.
     **/
    @Override
    public String qname() {
        touch();
        return fqn;
    }

    /**
     * Fetch the fully qualified name without adding this libraries origin to
     * the set of known-used libraries
     **/
    @Override
    public String qnameNotouch() {
        return fqn;
    }

    /**
     * Fetch the simple name of the class
     **/
    @Override
    public String simpleName() {
        touch();
        return AnnotationUtils.simpleName(fqn);
    }

    /**
     * Fetch the library this class is in, if non-JDK
     **/
    @Override
    public Library origin() {
        return lib;
    }

    private void touch() {
        if (lib != null) {
            if (!REPORTED.contains(lib)) {
                TOUCHED.add(lib);
            }
        }
    }

    public static String touchedMessage() {
        if (TOUCHED.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(1024);
        result.append("\nGenerated code requires the following dependencies:\n");
        for (Libraries lib : TOUCHED) {
            if (lib != null) {
                result.append(lib.toXML());
                REPORTED.add(lib);
            }
        }
        TOUCHED.clear();
        return result.toString();
    }

    public static String touchedMessage(Object what) {
        if (TOUCHED.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(1024);
        result.append("\nCode generated by ");
        result.append(what.getClass().getSimpleName());
        result.append(
            " and other annotation processors requires the following dependencies be set for this project:\n");
        for (Libraries lib : TOUCHED) {
            if (lib != null) {
                result.append(lib.toXML());
                REPORTED.add(lib);
            }
        }
        TOUCHED.clear();
        return result.toString();
    }

    public static JdkTypes forName(String fqn) {
        for (JdkTypes type : values()) {
            if (type.fqn.equals(fqn) == true) {
                return type;
            }
        }
        return null;
    }

}

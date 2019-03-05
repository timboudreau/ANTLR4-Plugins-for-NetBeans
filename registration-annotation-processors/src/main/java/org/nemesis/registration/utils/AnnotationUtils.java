package org.nemesis.registration.utils;

import org.nemesis.registration.utils.function.ThrowingRunnable;
import org.nemesis.registration.utils.function.ThrowingBooleanSupplier;
import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.openide.util.WeakSet;

/**
 * Useful methods for extracting information from Java sources in an annotation
 * processor, which in particular avoid the annotation processor having to
 * directly reference the Java type of the annotation.
 *
 * @author Tim Boudreau
 */
public final class AnnotationUtils {

    private final ProcessingEnvironment processingEnv;
    private final Set<String> supportedAnnotationTypes;
    public static final String AU_LOG = "annoLog";
    private boolean log;
    private String logName;
    private static Set<AnnotationUtils> INSTANCES = new WeakSet<>();

    public AnnotationUtils(ProcessingEnvironment processingEnv, Set<String> supportedAnnotationTypes, Class<?> processorClass) {
        this.processingEnv = processingEnv;
        this.supportedAnnotationTypes = supportedAnnotationTypes == null
                ? Collections.emptySet() : new HashSet<>(supportedAnnotationTypes);
        INSTANCES.add(this);
        String logSpec = processingEnv.getOptions().get(AU_LOG);
        boolean log = forcedLogging || "true".equals(logSpec) || "on".equals(logSpec) || "yes".equals(logSpec);
        String processorName2 = logName = processorClass.getSimpleName();
        if (!log && logSpec != null && !logSpec.isEmpty()) {
            String processorName = processorClass.getName();
            for (String type : logSpec.split(",")) {
                type = type.trim();
                if (processorName.equals(type) || processorName2.equals(type)) {
                    log = true;
                    break;
                }
            }
        }
        this.log = log;
    }

    static boolean forcedLogging;

    public static void forceLogging() {
        forcedLogging = true;
        for (AnnotationUtils u : INSTANCES) {
            u.log = true;
        }
    }

    public enum TypeComparisonResult {
        TRUE,
        FALSE,
        TYPE_NAME_NOT_RESOLVABLE;

        public boolean isSubtype() {
            return this == TRUE;
        }

        static TypeComparisonResult forBoolean(boolean val) {
            return val ? TRUE : FALSE;
        }
    }

    public TypeMirror firstTypeParameter(Element var) {
        TypeMirror mir = var.asType();
        if (mir.getKind() != TypeKind.DECLARED) {
            fail("Field must have a fully reified type, but found " + mir, var);
            return null;
        }
        DeclaredType dt = (DeclaredType) mir;
        List<? extends TypeMirror> args = dt.getTypeArguments();

        if (args.isEmpty()) {
            fail("No type parameters - cannot generate code", var);
            return null;
        }
        return args.get(0);
    }

    public TypeMirror getTypeParameter(int index, Element var) {
        if (var == null) {
            fail("Null element");
            return null;
        }
        if (index < 0) {
            throw new IllegalArgumentException("Type param " + index);
        }
        TypeMirror mir = var.asType();
        if (mir.getKind() != TypeKind.DECLARED) {
            fail("Field must have a fully reified type, but found " + mir, var);
            return null;
        }
        DeclaredType dt = (DeclaredType) mir;
        List<? extends TypeMirror> args = dt.getTypeArguments();

        if (index >= args.size()) {
            fail("No type parameters - cannot generate code", var);
            return null;
        }
        return args.get(index);
    }

    /**
     * Determine if the passed element is a subtype of the passed type name.
     * Will return false if the class is not a subtype, <i>or if the passed type
     * name is not resolvable on the compiler's classpath</i>.
     *
     * @param e A source element
     * @param typeName A fully qualified type name
     * @return
     */
    public TypeComparisonResult isSubtypeOf(Element e, String typeName) {
        Types types = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement namedType = elementUtils.getTypeElement(typeName);
        if (namedType == null) {
            return TypeComparisonResult.TYPE_NAME_NOT_RESOLVABLE;
        }
        TypeMirror tp;
        if (e instanceof ExecutableElement) {
            tp = erasureOf(((ExecutableElement) e).getReturnType());
        } else {
            tp = erasureOf(e.asType());
        }

        TypeMirror named = erasureOf(namedType.asType());
        boolean res = types.isSubtype(tp, named);
        return TypeComparisonResult.forBoolean(res);
    }

    public TypeComparisonResult isSubtypeOf(TypeMirror tp, String typeName) {
        Types types = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement namedType = elementUtils.getTypeElement(typeName);
        if (namedType == null) {
            return TypeComparisonResult.TYPE_NAME_NOT_RESOLVABLE;
        }
        tp = erasureOf(tp);
        TypeMirror named = erasureOf(namedType.asType());
        boolean res = types.isSubtype(tp, named);
        return TypeComparisonResult.forBoolean(res);
    }

    public boolean isAssignable(TypeMirror tp, TypeMirror other) {
        return processingEnv.getTypeUtils().isAssignable(tp, other);
    }

    public boolean isAssignable(TypeMirror what, String to) {
        TypeElement el = processingEnv.getElementUtils().getTypeElement(to);
        if (el == null) {
            return false;
        }
        TypeMirror type = el.asType();
        return isAssignable(what, type);
    }

    public boolean isSubtypeOf(TypeMirror tp, TypeMirror other) {
        return processingEnv.getTypeUtils().isSubtype(tp, other);
    }

    public TypeMirror erasureOf(TypeMirror mir) {
        if (mir instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) mir;
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (args != null && !args.isEmpty()) {
                return processingEnv.getTypeUtils().erasure(mir);
            }
        }
        return mir;
    }

    /**
     * Determine if the passed element is a subtype of the passed type name.
     * Will return false if the class is not a subtype, <i>or if the passed type
     * name is not resolvable on the compiler's classpath</i>.
     *
     * @param e A source element
     * @param typeName A fully qualified type name
     * @return
     */
    public TypeComparisonResult isSupertypeOf(Element e, String typeName) {
        Types types = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement namedType = elementUtils.getTypeElement(typeName);
        if (namedType == null) {
            return TypeComparisonResult.TYPE_NAME_NOT_RESOLVABLE;
        }
        boolean res = types.isSubtype(erasureOf(namedType.asType()), erasureOf(e.asType()));
        return TypeComparisonResult.forBoolean(res);
    }

    /**
     * Find the AnnotationMirror for a specific annotation type.
     *
     * @param el The annotated element
     * @param annotationTypeFqn The fully qualified name of the annotation class
     * @return An annotation mirror
     */
    public AnnotationMirror findMirror(Element el, String annotationTypeFqn) {
        for (AnnotationMirror mir : el.getAnnotationMirrors()) {
            TypeMirror type = mir.getAnnotationType().asElement().asType();
            String typeName = canonicalize(type);
            if (annotationTypeFqn.equals(typeName)) {
                return mir;
            }
        }
        return null;
    }

    /**
     * Find the AnnotationMirror for a specific annotation type.
     *
     * @param el The annotated element
     * @param annoType The annotation type
     * @return An annotation mirror
     */
    public AnnotationMirror findMirror(Element el,
            Class<? extends Annotation> annoType) {
        for (AnnotationMirror mir : el.getAnnotationMirrors()) {
            TypeMirror type = mir.getAnnotationType().asElement().asType();
            if (annoType.getName().equals(type.toString())) {
                return mir;
            }
        }
        return null;
    }

    /**
     * Failover stringification.
     *
     * @param m A type mirror
     * @return A string representation
     */
    private String stripGenericsFromStringRepresentation(TypeMirror m) {
        String result = m.toString();
        result = result.replaceAll("\\<.*\\>", "");
        return result;
    }

    /**
     * Convert a class name to a loadable one which delimits nested class names
     * using $ - i.e. transforms com.foo.MyClass.MyInner.MyInnerInner to
     * com.foo.MyClass$MyInner$MyInnerInner.
     *
     * @param tm The type
     * @return A string representation
     */
    public String canonicalize(TypeMirror tm) {
        Types types = processingEnv.getTypeUtils();
        Element maybeType = types.asElement(tm);
        if (maybeType == null) {
            if (tm.getKind().isPrimitive()) {
                return types.getPrimitiveType(tm.getKind()).toString();
            } else {
                return stripGenericsFromStringRepresentation(tm);
            }
        }
        if (maybeType instanceof TypeParameterElement) {
            maybeType = ((TypeParameterElement) maybeType).getGenericElement();
        }
        if (maybeType instanceof TypeElement) {
            TypeElement e = (TypeElement) maybeType;
            StringBuilder nm = new StringBuilder(e.getQualifiedName().toString());
            Element enc = e.getEnclosingElement();
            while (enc != null && enc.getKind() != ElementKind.PACKAGE) {
                int ix = nm.lastIndexOf(".");
                if (ix > 0) {
                    nm.setCharAt(ix, '$');
                }
                enc = enc.getEnclosingElement();
            }
            return nm.toString();
        }

        warn("Cannot canonicalize " + tm);
        return null;
    }

    /**
     * Print a warning.
     *
     * @param warning The warning
     */
    public void warn(String warning) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, warning);
    }

    public static String types(Object o) { //debug stuff
        List<String> s = new ArrayList<>();
        Class<?> x = o.getClass();
        while (x != Object.class) {
            s.add(x.getName());
            for (Class<?> c : x.getInterfaces()) {
                s.add(c.getName());
            }
            x = x.getSuperclass();
        }
        StringBuilder sb = new StringBuilder();
        for (String ss : s) {
            sb.append(ss);
            sb.append(", ");
        }
        return sb.toString();
    }

    public TypeElement typeElementOfField(VariableElement el) {
        TypeMirror type = el.asType();
        return processingEnv.getElementUtils().getTypeElement(type.toString());
    }

    /**
     * Concatenate strings using a delimiter.
     *
     * @param delim The delimiter
     * @param strings The strings
     * @return A string that concatenates the passed one placing delimiters
     * between elements as necessary
     */
    public static String join(char delim, String... strings) {
        return StringUtils.join(delim, strings);
    }

    /**
     * Concatenate strings using a delimiter.
     *
     * @param delim The delimiter
     * @param strings The strings
     * @return A string that concatenates the passed one placing delimiters
     * between elements as necessary
     */
    public static String join(char delim, Iterable<?> strings) {
        return StringUtils.join(delim, strings);
    }

    /**
     * Get a list of fully qualified, canonicalized Java class names for a
     * member of an annotation. Typically such classes cannnot actually be
     * loaded by the compiler, so referencing the Class objects for them results
     * in a compile failure; this method produces string representations of such
     * names without relying on being able to load the classes in question.
     * Handles the case that the annotation member is not an array by returning
     * a one-element list.
     *
     * @param mirror An annotation mirror
     * @param param The annotation's method which should be queried
     * @param failIfNotSubclassesOf Fail the compile if the types listed are not
     * subclasses of one of the passed type FQNs.
     * @return A list of stringified, canonicalized type names
     */
    public List<String> typeList(AnnotationMirror mirror, String param, String... failIfNotSubclassesOf) {
        List<String> result = new ArrayList<>();
        if (mirror != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> x : mirror.getElementValues()
                    .entrySet()) {
                String annoParam = x.getKey().getSimpleName().toString();
                if (param.equals(annoParam)) {
                    if (x.getValue().getValue() instanceof List<?>) {
                        List<?> list = (List<?>) x.getValue().getValue();
                        for (Object o : list) {
                            if (o instanceof AnnotationValue) {
                                AnnotationValue av = (AnnotationValue) o;
                                if (av.getValue() instanceof DeclaredType) {
                                    DeclaredType dt = (DeclaredType) av.getValue();
                                    if (failIfNotSubclassesOf.length > 0) {
                                        boolean found = false;
                                        for (String f : failIfNotSubclassesOf) {
                                            if (!isSubtypeOf(dt.asElement(), f).isSubtype()) {
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (!found) {
                                            fail("Not a " + join('/', failIfNotSubclassesOf) + " subtype: "
                                                    + av);
                                            continue;
                                        }
                                    }
                                    // Convert e.g. mypackage.Foo.Bar.Baz to mypackage.Foo$Bar$Baz
                                    String canonical = canonicalize(dt.asElement().asType());
                                    if (canonical == null) {
                                        // Unresolvable generic or broken source
                                        fail("Could not canonicalize " + dt.asElement(), x.getKey());
                                    } else {
                                        result.add(canonical);
                                    }
                                } else {
                                    // Unresolvable type?
                                    warn("Not a declared type: " + av);
                                }
                            } else {
                                // Probable invalid source
                                warn("Annotation value for value() is not an AnnotationValue " + types(o));
                            }
                        }
                    } else if (x.getValue().getValue() instanceof DeclaredType) {
                        DeclaredType dt = (DeclaredType) x.getValue().getValue();
                        if (failIfNotSubclassesOf.length > 0) {
                            boolean found = false;
                            for (String f : failIfNotSubclassesOf) {
                                if (isSubtypeOf(dt.asElement(), f).isSubtype()) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                fail("Not a " + join('/', failIfNotSubclassesOf) + " subtype: " + dt);
                                continue;
                            }
                        }
                        // Convert e.g. mypackage.Foo.Bar.Baz to mypackage.Foo$Bar$Baz
                        String canonical = canonicalize(dt.asElement().asType());
                        if (canonical == null) {
                            // Unresolvable generic or broken source
                            fail("Could not canonicalize " + dt, x.getKey());
                        } else {
                            result.add(canonical);
                        }
                    } else {
                        warn("Annotation value for is not a List of types or a DeclaredType on " + mirror + " - "
                                + types(x.getValue().getValue()) + " - invalid source?");
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get an enum constant from an annotation member as a string, returning a
     * default value if not present.
     *
     * @param mirror An annotation
     * @param param The annotation attribute name
     * @param defaultValue The default value to use if not present
     * @return A value from the annotation as a string, or the default
     */
    public String enumConstantValue(AnnotationMirror mirror, String param, String defaultValue) {
        String result = enumConstantValue(mirror, param);
        return result == null ? defaultValue : result;
    }

    /**
     * Get an enum constant from an annotation member as a string, returning
     * null if not present.
     *
     * @param mirror An annotation
     * @param param The annotation attribute name
     * @return A value from the annotation as a string, or the default
     */
    public String enumConstantValue(AnnotationMirror mirror, String param) {
        Set<String> result = enumConstantValues(mirror, param);
        switch (result.size()) {
            case 0:
                return null;
            case 1:
                return result.iterator().next();
            default:
                fail("Expecting a single value, but got an array for '" + param + "' of "
                        + mirror.getAnnotationType() + ": " + result, null, mirror);
                return result.iterator().next();
        }
    }

    /**
     * For cases where an array of enum constant values is expected, return
     * them, optionally returning a default set, as strings rather than
     * VariableElement instances as they are represented internally.
     *
     * @param mirror An annotation mirror
     * @param param A parameter name
     * @param defaultValues An array of default values to use if no value is
     * present (careful if there can legitimately be an empty value array)
     * @return A set of strings representing enum constant names
     */
    public Set<String> enumConstantValues(AnnotationMirror mirror, String param, String... defaultValues) {
        Set<String> all = new HashSet<>();
        List<VariableElement> els = annotationValues(mirror, param, VariableElement.class);
        for (VariableElement e : els) {
            all.add(e.getSimpleName().toString());
        }
        if (defaultValues.length > 0 && all.isEmpty()) {
            all.addAll(Arrays.asList(defaultValues));
        }
        return all;
    }

    /**
     * Get a list of values for a particular annotation member, cast as the
     * passed type.
     *
     * @param <T> The member type
     * @param mirror The type mirror
     * @param param The annotation method name
     * @param type The type to cast the result as (build will fail if it is of
     * the wrong type, which is possible on broken sources)
     * @return A list of instanceof of T
     */
    public <T> List<T> annotationValues(AnnotationMirror mirror, String param, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (mirror != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> x : mirror.getElementValues()
                    .entrySet()) {
                String annoParam = x.getKey().getSimpleName().toString();
                if (param.equals(annoParam)) {
                    if (x.getValue().getValue() instanceof List<?>) {
                        List<?> list = (List<?>) x.getValue().getValue();
                        for (Object o : list) {
                            if (o instanceof AnnotationValue) {
                                AnnotationValue av = (AnnotationValue) o;
                                try {
                                    result.add(type.cast(av.getValue()));
                                } catch (ClassCastException | AnnotationTypeMismatchException ex) {
                                    fail("Not an instance of " + type.getName() + " for value of "
                                            + param + " on " + mirror.getAnnotationType()
                                            + " found a " + x.getValue().getClass().getName()
                                            + " instead: " + ex.getMessage(), x.getKey());
                                }
                            } else {
                                // Probable invalid source
                                warn("Annotation value for value() is not an AnnotationValue " + types(o));
                            }
                        }
                    } else {
                        try {
                            result.add(type.cast(x.getValue().getValue()));
                        } catch (ClassCastException | AnnotationTypeMismatchException ex) {
                            fail("Not an instance of " + type.getName() + " for value of " + param + " on "
                                    + mirror.getAnnotationType() + " found a " + x.getValue().getClass().getName()
                                    + " instead: " + ex.getMessage(), x.getKey());
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get the value of a method on an annotation, where the return type is
     * <i>not an array</i>.
     *
     * @param <T> The type to cast as
     * @param mirror The annotation mirror
     * @param param The name of the annotation's method whose value should be
     * returned
     * @param type The type to cast each value to
     * @param defaultValue Value to return if no such parameter is found
     * @return A single instance of the value type or null
     */
    public <T> T annotationValue(AnnotationMirror mirror, String param, Class<T> type, T defaultValue) {
        T result = annotationValue(mirror, param, type);
        return result == null ? defaultValue : result;
    }

    /**
     * Get the value of a method on an annotation, where the return type is
     * <i>not an array</i>.
     *
     * @param <T> The type to cast as
     * @param mirror The annotation mirror
     * @param param The name of the annotation's method whose value should be
     * returned
     * @param type The type to cast each value to
     * @return A single instance of the value type or null
     */
    public <T> T annotationValue(AnnotationMirror mirror, String param, Class<T> type) {
        T result = null;
        if (mirror != null) {
//            System.out.println("AV ON " + mirror.getAnnotationType() + " for " + param + " of " + type.getSimpleName());
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> x : mirror.getElementValues()
                    .entrySet()) {
                String annoParam = x.getKey().getSimpleName().toString();
                if (param.equals(annoParam)) {
//                    System.out.println(" - TEST " + annoParam + " with " + x.getValue() + " val " + x.getValue().getValue());
                    if (x.getValue().getValue() instanceof List<?>) {
                        List<?> list = (List<?>) x.getValue().getValue();
                        for (Object o : list) {
                            if (o instanceof AnnotationValue) {
                                AnnotationValue av = (AnnotationValue) o;
                                try {
                                    result = type.cast(av.getValue());
                                    break;
                                } catch (ClassCastException | AnnotationTypeMismatchException ex) {
                                    ex.printStackTrace(System.out);
                                    fail("Not an instance of " + type.getName() + " for value of " + param + " on "
                                            + mirror.getAnnotationType()
                                            + ", but was " + type.getName() + ": " + ex.getMessage(), x.getKey());
                                }
                            } else {
                                result = type.cast(result);
                            }
                        }
                    } else {
                        try {
//                            System.out.println("  try single cast");
                            result = type.cast(x.getValue().getValue());
                        } catch (ClassCastException | AnnotationTypeMismatchException ex) {
                            ex.printStackTrace(System.out);
                            fail("Not an instance of " + type.getName() + " for value of "
                                    + param + " on " + mirror.getAnnotationType()
                                    + ", but was " + type.getName()
                                    + ": " + ex.getMessage(), x.getKey());
                        }
                    }
                }
            }
        }
//        System.out.println("   returning " + result);
        return result;
    }

    /**
     * Look up an annotation by type, call a method on that annotation which can
     * return one or an array of Class objects, and return the class names as a
     * list of strings.
     *
     * @param el The element that is annotated
     * @param annotationType The annotation type
     * @param memberName The method on the annotation whose value is sought
     * @param mustBeSubtypesOf If this array is non-empty, fail the build if any
     * value found is not a subtype of one of the passed fully-qualified Java
     * class names
     * @return A list of canonicalized Java class names
     */
    public List<String> classNamesForAnnotationMember(Element el,
            Class<? extends Annotation> annotationType, String memberName,
            String... mustBeSubtypesOf) {
        AnnotationMirror mirror = findMirror(el, annotationType);
        List<String> result;
        if (mirror != null) {
            result = typeList(mirror, memberName, mustBeSubtypesOf);
        } else {
            result = Collections.emptyList();
        }
        return result;
    }

    /**
     * Look up an annotation by fully qualified type name, call a method on that
     * annotation which can return one or an array of Class objects, and return
     * the class names as a list of strings.
     *
     * @param el The element that is annotated
     * @param annotationType The annotation type
     * @param memberName The method on the annotation whose value is sought
     * @param mustBeSubtypesOf If this array is non-empty, fail the build if any
     * value found is not a subtype of one of the passed fully-qualified Java
     * class names
     * @return A list of canonicalized Java class names
     */
    public List<String> classNamesForAnnotationMember(Element el,
            String annotationTypeFqn, String memberName,
            String... mustBeSubtypesOf) {
        AnnotationMirror mirror = findMirror(el, annotationTypeFqn);
        List<String> result = typeList(mirror, memberName, mustBeSubtypesOf);
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * Look up an annotation by fully qualified type name, call a method on that
     * annotation which can return one or an array of Class objects, and return
     * the class names as a list of strings.
     *
     * @param el The element that is annotated
     * @param annotationType The annotation type
     * @param memberName The method on the annotation whose value is sought
     * @param mustBeSubtypesOf If this array is non-empty, fail the build if any
     * value found is not a subtype of one of the passed fully-qualified Java
     * class names
     * @return A list of canonicalized Java class names
     */
    public List<String> classNamesForAnnotationMember(Element el,
            String annotationTypeFqn, String memberName,
            Collection<String> mustBeSubtypesOf) {
        String[] subtypesOf = mustBeSubtypesOf.toArray(new String[mustBeSubtypesOf.size()]);
        AnnotationMirror mirror = findMirror(el, annotationTypeFqn);
        List<String> result;
        if (mirror != null) {
            result = typeList(mirror, memberName, subtypesOf);
        } else {
            result = Collections.emptyList();
        }
        return result;
    }

    public TypeMirror typeForSingleClassAnnotationMember(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {
            if (name.equals(e.getKey().getSimpleName().toString())) {
                Object v = e.getValue().getValue();
                if (v instanceof TypeMirror) {
                    return (TypeMirror) v;
                } else {
                    System.out.println("GOT A " + v.getClass().getName());
                }
            }
        }
        return null;
    }

    /**
     * Fail the build with a message tied to a particular element.
     *
     * @param msg The message
     * @param el The element
     */
    public void warn(String msg, Element el, AnnotationMirror mir) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message(msg), el, mir);
    }

    /**
     * Fail the build with a message tied to a particular element.
     *
     * @param msg The message
     * @param el The element
     */
    public void warn(String msg, Element el) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message(msg), el);
    }

    /**
     * Fail the build with a message tied to a particular element.
     *
     * @param msg The message
     * @param el The element
     */
    public void fail(String msg, Element el) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message(msg), el);
    }

    /**
     * Fail the build with a message tied to a particular element.
     *
     * @param msg The message
     * @param el The element
     */
    public void fail(String msg, Element el, AnnotationMirror mir) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message(msg), el, mir);
    }

    /**
     * Fail the build with a message
     *
     * @param msg The message
     */
    public void fail(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message(msg));
    }

    private String message(String msg) {
        if (log) {
            StackTraceElement el = new Exception().getStackTrace()[2];
            return el + ": " + msg;
        }
        return msg;
    }

    public static String simpleName(TypeMirror mirror) {
        return simpleName(mirror.toString().replace('$', '.'));
    }

    public static String simpleName(String dotted) {
        int ix = dotted.lastIndexOf('.');
        if (ix > 0 && ix < dotted.length() - 1) {
            return dotted.substring(ix + 1);
        }
        return dotted;
    }

    /**
     * Find elements annotated with the set of supported annotation types passed
     * to the constructor.
     *
     * @param roundEnv The environment
     * @return A set of elements
     */
    public Set<Element> findAnnotatedElements(RoundEnvironment roundEnv) {
        return findAnnotatedElements(roundEnv, supportedAnnotationTypes);
    }

    /**
     * Find elements annotated with the set of supported annotation types passed
     * to the constructor.
     *
     * @param roundEnv The environment for this round of processing
     * @param typeOne A type to look for as a fully qualified java class name
     * @param more More types to look for as a fully qualified java class name
     * @return A set of all elements annotated with one or more of the passed
     * types
     */
    public Set<Element> findAnnotatedElements(RoundEnvironment roundEnv, String typeOne, String... more) {
        Set<String> fqns = new LinkedHashSet<>(more.length + 1);
        fqns.add(typeOne);
        fqns.addAll(Arrays.asList(more));
        return findAnnotatedElements(roundEnv, fqns);
    }

    /**
     * Find elements annotated with the set of supported annotation types passed
     * to the constructor.
     *
     * @param roundEnv The environment
     * @return A set of elements
     */
    public Set<Element> findAnnotatedElements(RoundEnvironment roundEnv, Iterable<String> annotationTypes) {
        Set<Element> result = new HashSet<>(20);
        Elements elementUtils = processingEnv.getElementUtils();

        for (String typeName : annotationTypes) {
            TypeElement currType = elementUtils.getTypeElement(typeName);
            if (currType != null) {
                Set<? extends Element> allWith = roundEnv.getElementsAnnotatedWith(currType);
                result.addAll(allWith);
            } else {
            }
        }
        return result;
    }

    /**
     * Find all annotation mirrors
     *
     * @param e
     * @return
     */
    public Set<AnnotationMirror> findAnnotationMirrors(Element e) {
        return findAnnotationMirrors(e, supportedAnnotationTypes);
    }

    public AnnotationMirror findAnnotationMirror(Element e, String annotationFqn) {
        Set<AnnotationMirror> mirrors = findAnnotationMirrors(e, annotationFqn);
        if (mirrors.size() > 1) {
            fail("Found more than one annotation of type " + annotationFqn + " on " + e, e, mirrors.iterator().next());
        }
        AnnotationMirror result = mirrors.isEmpty() ? null : mirrors.iterator().next();
        return result;
    }

    public Set<AnnotationMirror> findAnnotationMirrors(Element e, String firstAnnotationTypeFqn, String... moreFqns) {
        Set<String> fqns = new LinkedHashSet<>();
        fqns.add(firstAnnotationTypeFqn);
        fqns.addAll(Arrays.asList(moreFqns));
        return findAnnotationMirrors(e, fqns);
    }

    public Set<AnnotationMirror> findAnnotationMirrors(Element e, Iterable<String> annotationTypeFqns) {
        Set<AnnotationMirror> annos = new LinkedHashSet<>();
        Elements elementUtils = processingEnv.getElementUtils();
        for (String typeName : annotationTypeFqns) {
            TypeElement currType = elementUtils.getTypeElement(typeName);
            if (currType != null) {
                for (AnnotationMirror mirror : e.getAnnotationMirrors()) {

                    TypeElement test = (TypeElement) mirror.getAnnotationType().asElement();
                    if (test != null) {
                        if (test.getQualifiedName().equals(currType.getQualifiedName())) {
                            annos.add(mirror);
                        }
                    }
                }
            }
        }
        return annos;
    }

    private void logLine(String line) {
        // Maven and others do various things to supress the output of
        // processingEnv.getMessager() to cause quiet builds.  That has the
        // effect of making it completely unpredictable, and sometimes
        // impossible, or at least extremely irritating, to find out what
        // what your annotation processors are actually doing.  Fortunately,
        // most build systems do not replace javac's System.out, so that
        // still works.
        System.out.println(line);
    }

    public void logException(Throwable thrown, boolean fail) {
        if (fail) {
            fail(thrown.toString());
        }
        if (log || forcedLogging) {
            thrown.printStackTrace(System.out);
        } else {
            warn("Exception thrown: " + thrown);
        }
    }

    public void log(String val) {
        if (log || forcedLogging) {
            String names = logContext == null ? logName : logName + ":" + logContext;
            logLine(names + ": " + val);
        }
    }

    public void log(String val, Object... args) {
        if (log || forcedLogging) {
            String names = logContext == null ? logName : logName + ":" + logContext;
            logLine(names + ": " + MessageFormat.format(val, args));
        }
    }

    public static TypeElement enclosingType(Element el) {
        if (el == null) {
            return null;
        }
        do {
            el = el.getEnclosingElement();
        } while (el != null && el.getKind() != ElementKind.CLASS);
        return el.getKind() == ElementKind.CLASS ? (TypeElement) el : null;
    }

    public static TypeElement topLevelType(Element el) {
        TypeElement type = enclosingType(el);
        while (type.getEnclosingElement() instanceof TypeElement) {
            type = (TypeElement) type.getEnclosingElement();
        }
        return type;
    }

    public <E extends Element, B extends TestBuilder<E, Predicate<E>, B>> TestBuilder<E, Predicate<E>, B> testsBuilder() {
        return TestBuilder.<E, B>create(this);
    }

    public <E extends ExecutableElement, B extends MethodTestBuilder<E, Predicate<E>, B>> TestBuilder<E, Predicate<E>, B> methodTestBuilder() {
        return MethodTestBuilder.<E, B>createMethod(this);
    }

    public AnnotationMirrorTestBuilder<Predicate<AnnotationMirror>> testMirror() {
        return new AnnotationMirrorTestBuilder<>(this, amtb -> amtb.predicate());
    }

    private String logContext;

    public void withLogContext(String logContext, ThrowingRunnable run) throws Exception {
        String old = this.logContext;
        try {
            run.run();
        } finally {
            this.logContext = old;
        }
    }

    public boolean withLogContext(String logContext, ThrowingBooleanSupplier run) throws Exception {
        String old = this.logContext;
        try {
            return run.getAsBoolean();
        } finally {
            this.logContext = old;
        }
    }

    private static boolean validateMimeType(String value, AnnotationUtils utils) {
        if (value == null || value.length() < 3) {
            utils.fail("Mime type unset or too short to be one");
            return false;
        }
        if (value.length() > 80) {
            utils.fail("Mime type too long: " + value.length() + " (" + value + ")");
            return false;
        }
        int ix = value.indexOf('/');
        int lix = value.lastIndexOf('/');
        if (ix < 0) {
            utils.fail("No / character in mime type");
            return false;
        }
        if (lix != ix) {
            utils.fail("More than one / character in mime type");
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                utils.fail("Whitespace at " + i + " in mime type '" + value + "'");
                return false;
            }
            if (Character.isUpperCase(value.charAt(i))) {
                utils.fail("Mime type should not contain upper case letters");
            }
            if (';' == value.charAt(i)) {
                utils.fail("Complex mime types unsupported");
                return false;
            }
        }
        return true;
    }

    public static class AnnotationMirrorTestBuilder<T> {

        private final AnnotationUtils utils;
        private Predicate<AnnotationMirror> predicate;
        private final Function<AnnotationMirrorTestBuilder<T>, T> converter;

        AnnotationMirrorTestBuilder(AnnotationUtils utils, Function<AnnotationMirrorTestBuilder<T>, T> converter) {
            this.utils = utils;
            this.converter = converter;
        }

        Predicate<AnnotationMirror> predicate() {
            return predicate == null ? ignored -> true : predicate;
        }

        public T build() {
            return converter.apply(this);
        }

        public AnnotationMirrorTestBuilder<T> atLeastOneMemberMayBeSet(String... memberNames) {
            return addPredicate(am -> {
                List<Object> all = new ArrayList<>();
                Set<String> names = new HashSet<>();
                for (String name : memberNames) {
                    Object o = utils.annotationValue(am, name, Object.class);
                    if (o != null) {
                        names.add(name);
                        all.add(o);
                    }
                }
                boolean result = !all.isEmpty();
                if (!result) {
                    utils.fail("At least one of " + join(',', memberNames) + " MUST be used", null, am);
                }
                return result;
            });
        }

        public AnnotationMirrorTestBuilder<T> onlyOneMemberMayBeSet(String... memberNames) {
            return addPredicate(am -> {
                List<Object> all = new ArrayList<>();
                Set<String> names = new HashSet<>();
                for (String name : memberNames) {
                    Object o = utils.annotationValue(am, name, Object.class);
                    if (o != null) {
                        names.add(name);
                        all.add(o);
                    }
                }
                boolean result = all.size() <= 1;
                if (!result) {
                    utils.fail("Only one of " + join(',', memberNames) + " may be used, but found "
                            + join(',', names.toArray(new String[names.size()])), null, am);
                }

                return result;
            });
        }

        public AnnotationMirrorTestBuilder<T> addPredicate(Predicate<AnnotationMirror> pred) {
            if (this.predicate == null) {
                this.predicate = pred;
            } else {
                this.predicate = this.predicate.and(pred);
            }
            return this;
        }

        public AnnotationMirrorMemberTestBuilder<AnnotationMirrorTestBuilder<T>> testMember(String memberName) {
            return new AnnotationMirrorMemberTestBuilder<>(ammtb -> {
                if (ammtb.predicate != null) {
                    addPredicate(ammtb.predicate);
                }
                return this;
            }, memberName, utils);
        }

        public AnnotationMirrorMemberTestBuilder<AnnotationMirrorTestBuilder<T>> testMemberIfPresent(String memberName) {
            return new AnnotationMirrorMemberTestBuilder<>(ammtb -> {
                if (ammtb.predicate != null) {
                    addPredicate(outer -> {
                        List<AnnotationMirror> values = utils.annotationValues(outer, memberName, AnnotationMirror.class);
                        if (values.isEmpty()) {
                            return true;
                        }
                        return ammtb.predicate.test(outer);
                    });
                }
                return this;
            }, memberName, utils);
        }

        public AnnotationMirrorTestBuilder<AnnotationMirrorTestBuilder<T>> testMemberAsAnnotation(String memberName) {
            return new AnnotationMirrorTestBuilder<>(utils, amtb -> {
                addPredicate((AnnotationMirror a) -> {
                    List<AnnotationMirror> mir = utils.annotationValues(a, memberName, AnnotationMirror.class);
                    Predicate<AnnotationMirror> p = amtb.predicate;
                    boolean result = true;
                    if (p != null) {
                        for (AnnotationMirror am : mir) {
                            result &= p.test(am);
                        }
                    }
                    return result;
                });
                return this;
            });
        }

        public static final class AnnotationMirrorMemberTestBuilder<T> {

            private final Function<AnnotationMirrorMemberTestBuilder<T>, T> converter;
            private final String memberName;
            private Predicate<AnnotationMirror> predicate;
            private final AnnotationUtils utils;

            AnnotationMirrorMemberTestBuilder(Function<AnnotationMirrorMemberTestBuilder<T>, T> converter, String memberName, AnnotationUtils utils) {
                this.converter = converter;
                this.memberName = memberName;
                this.utils = utils;
            }

            public AnnotationMirrorMemberTestBuilder<T> addPredicate(Predicate<AnnotationMirror> pred) {
                if (this.predicate == null) {
                    this.predicate = pred;
                } else {
                    this.predicate = this.predicate.and(pred);
                }
                return this;
            }

            public AnnotationMirrorMemberTestBuilder<T> validateStringValueAsMimeType() {
                return stringValueMustMatch(AnnotationUtils::validateMimeType);
            }

            public AnnotationMirrorMemberTestBuilder<T> stringValueMustNotContainWhitespace() {
                return addPredicate(am -> {
                    List<String> vals = utils.annotationValues(am, memberName, String.class);
                    boolean result = true;
                    for (String val : vals) {
                        for (int i = 0; i < val.length(); i++) {
                            if (Character.isWhitespace(val.charAt(i))) {
                                utils.fail("Value of " + memberName + " may not contain whitespace", null, am);
                                result = false;
                            }
                        }
                    }
                    return result;
                });
            }

            public AnnotationMirrorMemberTestBuilder<T> stringValueMustNotBeEmpty() {
                return stringValueMustMatch((val, ut) -> {
                    boolean result = val != null && !val.isEmpty();
                    if (!result) {
                        ut.fail("Value must not be empty");
                    }
                    return result;
                });
            }

            public AnnotationMirrorMemberTestBuilder<T> stringValueMustBeValidJavaIdentifier() {
                return stringValueMustMatch((val, ut) -> {
                    if (val == null || val.isEmpty()) {
                        return true;
                    }
                    int max = val.length();
                    for (int i = 0; i < max; i++) {
                        char c = val.charAt(i);
                        boolean result;
                        if (i == 0) {
                            result = Character.isJavaIdentifierStart(c);
                        } else {
                            result = Character.isJavaIdentifierPart(c);
                        }
                        if (!result) {
                            ut.fail("Invalid identifier '" + val + "' may not contain: " + c);
                            return result;
                        }
                    }
                    return true;
                });
            }

            public AnnotationMemberAsTypeTestBuilder<TypeElement, AnnotationMirrorMemberTestBuilder<T>> asTypeSpecifier() {
                return new AnnotationMemberAsTypeTestBuilder<>(utils, b -> {
                    Predicate<TypeElement> teTest = b.getPredicate();
                    addPredicate(am -> {
                        List<String> types = utils.typeList(am, memberName);
                        boolean result = true;
                        for (String type : types) {
                            TypeElement te = utils.processingEnv.getElementUtils().getTypeElement(type);
                            result &= teTest.test(te);
                        }
                        return result;
                    });
                    return this;
                });
            }

            public AnnotationMirrorMemberTestBuilder<T> stringValueMustMatch(BiPredicate<String, AnnotationUtils> bp) {
                return addPredicate(mirror -> {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {

                        if (memberName.equals(e.getKey().getSimpleName().toString())) {
                            AnnotationValue v = e.getValue();
                            if (!(v.getValue() instanceof String)) {
                                utils.fail("Not a string: " + v, e.getKey(), mirror);
                                return false;
                            } else {
                                boolean result = bp.test((String) v.getValue(), utils);
                                if (!result) {
                                    utils.fail("Invalid string value", e.getKey(), mirror);
                                }
                                return result;
                            }
//                            break;
                        }
                    }
//                    String val = bldr.utils.annotationValue(mirror, memberName, String.class);
//                    boolean result = bp.test(val, bldr.utils);
//                    System.out.println("TEST STRING VALUE " + val + " with " + bp + " res " + result);
//                    return result;
                    return true;
                });
            }

            public AnnotationMirrorMemberTestBuilder<T> mustBeSubtypeOf(String... names) {
                return addPredicate(mir -> {
                    List<String> l = utils.typeList(mir, memberName, names);
                    System.out.println("GOT TYPES " + l + " looking for " + Arrays.toString(names) + " for " + memberName);
                    return !l.isEmpty();
                });
            }

            public T build() {
                return converter.apply(this);
            }

            public <R> AnnotationMirrorMemberTestBuilder<T> valueMustBeOfSimpleType(Class<R> type) {
                return addPredicate(mir -> {
                    return utils.annotationValue(mir, memberName, type) != null;
                });
            }
        }
    }

    public static class MethodTestBuilder<E extends ExecutableElement, R, B extends MethodTestBuilder<E, R, B>> extends TestBuilder<E, R, B> {

        public MethodTestBuilder(AnnotationUtils utils, Function<B, R> builder) {
            super(utils, builder);
        }

        public B returns(String type, String... moreTypes) {
            Set<String> all = new HashSet<>(Arrays.asList(moreTypes));
            all.add(type);
            System.out.println("check return type " + all);
            Predicate<E> pred = el -> {
                for (String oneType : all) {
                    TypeComparisonResult res = utils.isSubtypeOf(el, oneType);
                    if (res.isSubtype()) {
                        return true;
                    }
                }
                utils.fail("Return type is not one of " + join(',', all), el);
                return false;
            };
            return addPredicate(pred);
        }

        public B mustNotTakeArguments() {
            return addPredicate(e -> {
                boolean result = e.getParameters().isEmpty();
                if (!result) {
                    utils.fail("Method must not take any arguments", e);
                }
                return result;
            });
        }

        private static <E1 extends ExecutableElement, B1 extends MethodTestBuilder<E1, Predicate<E1>, B1>> MethodTestBuilder<E1, Predicate<E1>, B1> createMethod(AnnotationUtils utils) {
            return new MethodTestBuilder<E1, Predicate<E1>, B1>(utils, defaultMethodBuilder());
        }

        private static <E1 extends ExecutableElement, B extends MethodTestBuilder<E1, Predicate<E1>, B>> Function<B, Predicate<E1>> defaultMethodBuilder() {
            return tb -> {
                return tb.getPredicate();
            };
        }

        public B argumentTypesMustBe(String... types) {
            addPredicate(e -> {
                int count = e.getParameters().size();
                if (count != types.length) {
                    utils.fail("Wrong number of arguments - " + count + " - expecting " + types.length
                            + " of types " + Arrays.toString(types), e);
                    return false;
                }
                return true;
            });
            for (int i = 0; i < types.length; i++) {
                final int index = i;
                addPredicate(e -> {
                    List<? extends VariableElement> params = e.getParameters();
                    VariableElement toTest = params.get(index);
                    TypeComparisonResult isSubtype = utils.isSubtypeOf(toTest, types[index]);
                    boolean result = isSubtype.isSubtype();
                    if (!result) {
                        switch (isSubtype) {
                            case FALSE:
                                utils.fail("Type of argument " + index
                                        + " should be " + types[index]
                                        + " not " + toTest.asType());
                                break;
                            case TYPE_NAME_NOT_RESOLVABLE:
                                utils.fail("Type of argument " + index
                                        + " should be " + types[index]
                                        + " but that type is not resolvable on "
                                        + "the compilation class path.");
                        }
                    }
                    return result;
                });
            }
            return (B) this;
        }
    }

    public static class AnnotationMemberAsTypeTestBuilder<E extends Element, R> extends TestBuilder<E, R, AnnotationMemberAsTypeTestBuilder<E, R>> {

        public AnnotationMemberAsTypeTestBuilder(AnnotationUtils utils, Function<AnnotationMemberAsTypeTestBuilder<E, R>, R> builder) {
            super(utils, builder);
        }
    }

    public static class TestBuilder<E extends Element, R, B extends TestBuilder<E, R, B>> {

        protected final AnnotationUtils utils;
        private final Function<B, R> builder;
        private Predicate<E> predicate;

        TestBuilder(AnnotationUtils utils, Function<B, R> builder) {
            this.utils = utils;
            this.builder = builder;
        }

        private static <E extends Element, B extends TestBuilder<E, Predicate<E>, B>> TestBuilder<E, Predicate<E>, B> create(AnnotationUtils utils) {
            return new TestBuilder<E, Predicate<E>, B>(utils, defaultBuilder());
        }

        private static <E extends Element, B extends TestBuilder<E, Predicate<E>, B>> Function<B, Predicate<E>> defaultBuilder() {
            return tb -> {
                return tb.getPredicate();
            };
        }

        public <B1 extends TestBuilder<TypeElement, B, B1>> B1 testContainingClass() {
            Function<B1, B> func = tb -> {
                addPredicate(el -> {
                    TypeElement enclosing = AnnotationUtils.enclosingType(el);
                    if (enclosing == null) {
                        utils.fail("Could not find enclosing type of " + el.asType(), el);
                        return false;
                    }
                    return true;
                });
                addPredicate(AnnotationUtils::enclosingType, tb.getPredicate());
                return (B) TestBuilder.this;
            };
            B1 result = (B1) new TestBuilder<>(utils, func);
            return result;
        }

        public R build() {
            return builder.apply((B) this);
        }

        Predicate<E> getPredicate() {
            if (this.predicate == null) {
                return e -> {
                    return true;
                };
            }
            return predicate;
        }

        public B addPredicate(Predicate<E> test) {
            if (this.predicate == null) {
                this.predicate = test;
            } else {
                this.predicate = this.predicate.and(test);
            }
            return (B) this;
        }

        public <V> B addPredicate(Function<E, V> converter, Predicate<V> predicate) {
            return addPredicate(e -> {
                V v = converter.apply(e);
                return predicate.test(v);
            });
        }

        public B mustBeFullyReifiedType() {
            return addPredicate((E e) -> {
                if (e.asType().getKind() != TypeKind.DECLARED) {
                    utils.fail("Not a fully reified type: " + e.asType(), e);
                }
                return true;
            });
        }

        public B typeParameterExtends(int ix, String name) {
            return addPredicate(e -> {
                TypeMirror expected = utils.getTypeParameter(ix, e);
                TypeElement el = utils.processingEnv.getElementUtils().getTypeElement(name);
                if (el == null) {
                    utils.fail("Could not load " + name + " from the classpath", e);
                    return false;
                }
                TypeMirror real = e.asType();
                boolean result = utils.processingEnv.getTypeUtils().isSameType(real, expected)
                        || utils.processingEnv.getTypeUtils().isAssignable(expected, real);
                if (!result) {
                    utils.fail(real + " is not assignable as " + expected);
                }
                return result;
            });
        }

        public B typeParameterExtendsOneOf(int ix, String name, String... more) {
            return addPredicate(e -> {
                TypeMirror expected = utils.getTypeParameter(ix, e);
                List<String> all = new ArrayList<>();
                all.add(name);
                all.addAll(Arrays.asList(more));
                TypeMirror real = e.asType();
                boolean result = false;
                for (String a : all) {
                    TypeElement el = utils.processingEnv.getElementUtils().getTypeElement(name);
                    if (el == null) {
                        utils.fail("Could not load " + name + " from the classpath", e);
                        return false;
                    }
                    result |= utils.processingEnv.getTypeUtils().isSameType(real, expected)
                            || utils.processingEnv.getTypeUtils().isAssignable(expected, real);
                    if (result) {
                        break;
                    }
                }
                if (!result) {
                    utils.fail(real + " is not assignable as " + expected);
                }
                return result;
            });
        }

        public B typeParameterMatches(int ix, BiPredicate<TypeMirror, AnnotationUtils> tester) {
            return addPredicate(e -> {
                TypeMirror tm = utils.getTypeParameter(ix, e);
                return tester.test(tm, utils);
            });
        }

        public B isKind(ElementKind kind) {
            return addPredicate(e -> {
                if (kind != e.getKind()) {
                    utils.fail("Element type must be " + kind + " but is " + e.getKind(), e);
                    return false;
                }
                return true;
            });
        }

        public B hasModifier(Modifier m) {
            return addPredicate(e -> {
                boolean result = e.getModifiers() != null
                        && e.getModifiers().contains(m);
                if (!result) {
                    utils.fail("Must be " + m, e);
                }
                return result;
            });
        }

        public B hasModifiers(Modifier a, Modifier... more) {
            hasModifier(a);
            for (Modifier m : more) {
                hasModifier(m);
            }
            return (B) this;
        }

        public B doesNotHaveModifier(Modifier m) {
            return addPredicate(e -> {
                boolean result = e.getModifiers() == null
                        || !e.getModifiers().contains(m);
                if (!result) {
                    utils.fail("Modifier " + m + " must not be used here", e);
                }
                return result;
            });
        }

        public B doesNotHaveModifiers(Modifier a, Modifier... more) {
            doesNotHaveModifier(a);
            for (Modifier m : more) {
                doesNotHaveModifier(m);
            }
            return (B) this;
        }

        public B isSubTypeOf(String typeName, String... moreTypeNames) {
            return addPredicate(e -> {
                if (e == null) {
                    return true;
                }
                if (moreTypeNames.length == 0) {
                    TypeComparisonResult res = utils.isSubtypeOf(e, typeName);
                    if (!res.isSubtype()) {
                        switch (res) {
                            case FALSE:
                                utils.fail("Not a subtype of " + typeName + ": " + e.asType(), e);
                                break;
                            case TYPE_NAME_NOT_RESOLVABLE:
                                utils.fail("Could not resolve on classpath: " + typeName, e);
                                break;
                        }
                    }
                    return res.isSubtype();
                } else {
                    List<String> all = new ArrayList<>(Arrays.asList(typeName));
                    all.addAll(Arrays.asList(moreTypeNames));
                    for (String test : all) {
                        TypeComparisonResult res = utils.isSubtypeOf(e, test);
                        if (res.isSubtype()) {
                            return true;
                        }
                    }
                    utils.fail("Not a subtype of any of " + join(',', all.toArray(new String[0])) + ": " + e.asType(), e);
                    return false;
                }
            });
        }
    }
}

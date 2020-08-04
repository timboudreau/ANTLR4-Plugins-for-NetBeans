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
package org.nemesis.registration;

import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.fileformat.PropertiesFileUtils;
import com.mastfrog.util.strings.Strings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import static org.nemesis.registration.LocalizeAnnotationProcessor.LOCALIZE_ANNO_TYPE;
import static org.nemesis.registration.typenames.JdkTypes.ICON;
import static org.nemesis.registration.typenames.JdkTypes.MAP;
import static org.nemesis.registration.typenames.JdkTypes.MISSING_RESOURCE_EXCEPTION;
import static org.nemesis.registration.typenames.JdkTypes.STRING;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.ENUM_CONSTANT_LOCALIZER;
import static org.nemesis.registration.typenames.KnownTypes.IMAGE_UTILITIES;
import static org.nemesis.registration.typenames.KnownTypes.LOCALIZER;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.SINGLE_INSTANCE_LOCALIZER;
import org.nemesis.registration.typenames.TypeName;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@SupportedAnnotationTypes(LOCALIZE_ANNO_TYPE)
@ServiceProvider(service = Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LocalizeAnnotationProcessor extends AbstractProcessor implements Processor {

    public static final String LOCALIZER_TYPE = "org.nemesis.antlr.refactoring.common.Localizer";
    public static final String LOCALIZE_ANNO_TYPE = "org.nemesis.localizers.annotations.Localize";
    private static final String DISPLAY_NAME_SUFFIX = "DISPLAY_NAME";
    private static final String ICON_SUFFIX = "ICON";
    private AnnotationUtils utils;
    private final Map<String, Map<String, String>> bundlesForBundleNames = CollectionUtils.supplierMap(HashMap::new);
    private final Map<ClassBuilder<String>, Set<Element>> classesForElements
            = CollectionUtils.supplierMap(LinkedHashSet::new);
    private final Map<EnumConstantKey, Set<EnumConstantInfo>> enumInfo
            = CollectionUtils.supplierMap(TreeSet::new);
    // The following fields MUST be kept in sync with the equivalent constants
    // on Localizer
    public static final String ENUM_LOOKUP_BASE = "loc/enums/";
    public static final String TYPE_LOOKUP_BASE = "loc/types/";
    public static final String INSTANCE_LOOKUP_BASE = "loc/instances/";
    private BiPredicate<? super AnnotationMirror, ? super Element> test;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        utils = new AnnotationUtils(processingEnv, Collections.singleton(LOCALIZE_ANNO_TYPE), LocalizeAnnotationProcessor.class);

        test = utils.multiAnnotations().whereAnnotationType(LOCALIZE_ANNO_TYPE, mb -> {
            mb.addPredicate("empty", (AnnotationMirror t, Consumer<? super String> u) -> {
                String dn = utils.annotationValue(t, "displayName", String.class);
                String icon = utils.annotationValue(t, "icon", String.class);
                List<AnnotationMirror> values = utils.annotationValues(t, "hints", AnnotationMirror.class);
                if (dn == null && icon == null && values.isEmpty()) {
                    u.accept("At least one of displayName, iconPath or hints must be set: " + t);
                }
                return true;
            }).testMember("iconPath").stringValueMustNotContain(' ', '\t', '\n').build()
                    .testMember("displayName").stringValueMustNotContain('\t', '\n').build()
                    .whereClassIsAnnotated(cb -> {
                        cb.nestingKindMayNotBe(NestingKind.LOCAL).nestingKindMayNotBe(NestingKind.ANONYMOUS)
                                .doesNotHaveModifier(PRIVATE);
                    }).whereFieldIsAnnotated(fb -> {
                fb.doesNotHaveModifier(PRIVATE)
                        .addPredicate("Must be enum or static final field", b -> {
                            switch (b.getKind()) {
                                case ENUM_CONSTANT:
                                case FIELD:
                                    return true;
                                default:
                                    return false;
                            }
                        });
            });
        }).build();
    }

    int round = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        round++;
        if (roundEnv.errorRaised()) {
            return false;
        }
        boolean over = roundEnv.processingOver();
        boolean anythingProcessed = false;
//        Map<TypeElement, Set<VariableElement>> enumElements = CollectionUtils.supplierMap(HashSet::new);
        for (Element el : utils.findAnnotatedElements(roundEnv, KnownTypes.LOCALIZE.qname())) {
            utils.log("Process {0} {1} {2}", el.getKind(), el.getSimpleName(), el.asType());
            AnnotationMirror annotation = utils.findAnnotationMirror(el, LOCALIZE_ANNO_TYPE);
            if (!test.test(annotation, el)) {
                // The predicate will have logged a failure message
                continue;
            }
            TypeElement on = enclosingType(el);
            switch (el.getKind()) {
                case FIELD:
                    VariableElement var = (VariableElement) el;
                    if (!var.getModifiers().contains(Modifier.STATIC)) {
                        utils.fail("Localized fields must be static", el, annotation);
                    }
                    if (!var.getModifiers().contains(Modifier.FINAL)) {
                        utils.warn("Localized fields really should be final", el, annotation);
                    }
                    handleField(var, annotation, on);
                    anythingProcessed = true;
                    break;
                case CLASS:
                case INTERFACE:
                case ENUM:
                    TypeElement type = (TypeElement) el;
                    utils.log("Have a type annotated: {0} of {1}", type.getQualifiedName(), type.getKind());
                    handleType(type, annotation);
                    anythingProcessed = true;
                    break;
                case ENUM_CONSTANT:
                    handleEnumConstant((VariableElement) el, annotation, on);
//                        enumElements.get(on).add(annotation);
                    anythingProcessed = true;
                    break;
                default:
                    utils.fail("Unsupported target type " + el.getKind() + ": " + el, el, annotation);
            }
        }
        try {
            writeJavaSources(roundEnv);
            classesForElements.clear();
            if (!roundEnv.errorRaised()) {
                if (over && !anythingProcessed) {
                    writePropertiesFiles(roundEnv);
                    return true;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            utils.fail(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
        return true;
    }

    static TypeElement enclosingType(Element el) {
        Element e = el.getEnclosingElement();
        while (e != null) {
            if (e instanceof TypeElement) {
                return (TypeElement) e;
            }
            e = e.getEnclosingElement();
        }
        return null;
    }

    private void handleEnumConstant(VariableElement el, AnnotationMirror mirror, TypeElement on) {
        withAnnotationParams(mirror, el, on,
                (String generateIntoPackage, String classNameToGenerate,
                        String fieldType, String iconPath, String fieldFqn,
                        String displayName, String bundlePath,
                        List<String> hintsAsList, String erasureOfFieldType) -> {
                    EnumConstantKey key = new EnumConstantKey(on, generateIntoPackage, fieldType, classNameToGenerate, erasureOfFieldType);
                    EnumConstantInfo val = new EnumConstantInfo(el.getSimpleName().toString(), displayName, iconPath, bundlePath, hintsAsList, el);
                    enumInfo.get(key).add(val);
                });
    }

    private static boolean anyHints(Iterable<? extends EnumConstantInfo> infos) {
        boolean hasAnyHints = false;
        for (EnumConstantInfo info : infos) {
            if (info.hints != null && !info.hints.isEmpty()) {
                hasAnyHints = true;
                break;
            }
        }
        return hasAnyHints;
    }

    private static boolean anyIcons(Iterable<? extends EnumConstantInfo> infos) {
        boolean hasAnyIcons = false;
        for (EnumConstantInfo info : infos) {
            if (info.iconPath != null && !info.iconPath.isEmpty()) {
                hasAnyIcons = true;
                break;
            }
        }
        return hasAnyIcons;
    }

    private static boolean anyNames(Iterable<? extends EnumConstantInfo> infos) {
        boolean hasAnyNames = false;
        for (EnumConstantInfo info : infos) {
            if (info.displayName != null && !info.displayName.isEmpty()) {
                hasAnyNames = true;
                break;
            }
        }
        return hasAnyNames;
    }

    private static String defaultBundle(EnumConstantKey key) {
        return key.pkg + ".Bundle";
    }

    private static String generatedBundle(EnumConstantKey key) {
        return key.pkg + "." + key.type.getSimpleName() + "Bundle";
    }

    private static String generatedBundle(String pkg, Element el) {
        return pkg + "." + el.getSimpleName() + "Bundle";
    }

    private static boolean isDefaultBundle(EnumConstantKey key, EnumConstantInfo info) {
        if (info.displayName == null) {
            return true;
        }
        String expectedPrefix = defaultBundle(key) + '#';
        if (info.displayName.startsWith(expectedPrefix)) {
            return true;
        }
        return false;
    }

    static final Pattern BUNDLE_KEY_PATTERN = Pattern.compile("^([a-zA-Z0-9\\-_]+\\..*)\\#(\\S+?)\\s*?$");

    private boolean isBundlePath(String displayName) {
        return displayName != null && BUNDLE_KEY_PATTERN.matcher(displayName).matches();
    }

    private int enumConstantsCount(TypeElement el) {
        assert el.getKind() == ElementKind.ENUM;
        int result = 0;
        for (Element enc : el.getEnclosedElements()) {
            switch (enc.getKind()) {
                case ENUM_CONSTANT:
                    result++;
            }
        }
        return result;
    }

    private static String staticFieldName(EnumConstantInfo info, String postFix) {
        return info.enumConstantName.toUpperCase() + "_" + postFix;
    }

    private void generateEnumLocalizer(EnumConstantKey key, Set<EnumConstantInfo> infos) throws IOException {
        if (infos.size() != enumConstantsCount(key.type)) {
            utils.warn("Not all enum elements annotated with @Localize", key.type);
        }
        boolean hasAnyHints = anyHints(infos);
        boolean hasAnyIcons = anyIcons(infos);
        boolean hasNames = anyNames(infos);
        String enumTypeSimple = key.type.getSimpleName().toString();
        TypeName theType = ENUM_CONSTANT_LOCALIZER.parameterizedOn(TypeName.fromQualifiedName(key.type.getSimpleName().toString()));
        TypeName string = TypeName.fromQualifiedName("java.lang.String");
        ClassBuilder<String> cb = ClassBuilder.forPackage(key.pkg).named(key.generatedClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(key.type.getQualifiedName().toString())
                .extending(theType.simpleName())
                .docComment("Generated from annotations on ", key.erasure + ".")
                .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                    ab.addClassArgument("service", LOCALIZER.simpleName())
                            .addArgument("path", enumLookupPath(key.type));
                })
                .constructor(constructor -> {
                    constructor.setModifier(PUBLIC).body(constructorBody -> {
                        constructorBody.invoke("super").withArgument(key.type.getSimpleName().toString()
                                + ".class").inScope();
                    });
                })
                .iteratively(infos, (ClassBuilder<?> cbb, EnumConstantInfo info) -> {
                    if (info.displayName != null && !info.displayName.isEmpty()) {
                        if (isBundlePath(info.displayName)) {
                            cbb.lineComment("Annotation value placed in generated resource bundle");
                            cbb.field(staticFieldName(info, DISPLAY_NAME_SUFFIX))
                                    .withModifier(PRIVATE, STATIC, FINAL)
                                    .docComment("Annotation value placed "
                                            + "in generated resource bundle. "
                                            + "Original: '" + info.displayName
                                            + "'")
                                    .initializedWith(info.displayName);
                        } else {
                            String genBundlePath = key.addProperty(
                                    info.enumConstantName, info.displayName);

                            cbb.field(staticFieldName(info, DISPLAY_NAME_SUFFIX))
                                    .withModifier(PRIVATE, STATIC, FINAL)
                                    .initializedWith(genBundlePath);
                        }
                    }
                    if (info.iconPath != null && !info.iconPath.isEmpty()) {
                        String iconPath = info.iconPath;
                        if (iconPath.indexOf('/') < 0) {
                            iconPath = key.pkg.replace('.', '/') + '/' + iconPath;
                        }
                        cbb.field(staticFieldName(info, ICON_SUFFIX))
                                .withModifier(PRIVATE, STATIC, FINAL)
                                .initializedWith(iconPath);
                    }
                })
                .conditionally(hasAnyHints,
                        cbb -> {
                            MAP.addImport(cbb);
                            cbb.overridePublic("hints").addArgument(enumTypeSimple, "key").returning(MAP.parameterizedOn(string, string).simpleName())
                                    .body(bb -> {
                                        bb.switchingOn("key", sw -> {
                                            for (Iterator<EnumConstantInfo> it = infos.iterator(); it.hasNext();) {
                                                EnumConstantInfo info = it.next();
                                                sw.inCase(info.enumConstantName, switchBlock -> {
                                                    switchBlock.returningInvocationOf("mapFromHintList")
                                                            .withNewArrayArgument("String", arr -> {
                                                                info.hints.forEach((hint) -> {
                                                                    arr.literal(hint);
                                                                });
                                                            }).on("super");
                                                });
                                            }
                                        }).returningInvocationOf("hints").withArgument("key").on("super");
                                    });
                        })
                .conditionally(hasAnyIcons, cbb -> {
                    cbb.overridePublic("icon").addArgument(enumTypeSimple, "key").returning(ICON.simpleName())
                            .body(bb -> {
                                bb.switchingOn("key", sw -> {
                                    for (Iterator<EnumConstantInfo> it = infos.iterator(); it.hasNext();) {
                                        EnumConstantInfo info = it.next();
                                        if (info.iconPath != null) {
                                            IMAGE_UTILITIES.addImport(cbb);
                                            sw.inCase(info.enumConstantName, switchBlock -> {
                                                // Allow annotations to include a relative path
                                                // to the icon, and expand it to the full path
                                                switchBlock.returningInvocationOf("loadImageIcon")
                                                        .withArgument(staticFieldName(info, ICON_SUFFIX))
                                                        .withArgument(true).on(IMAGE_UTILITIES.simpleName());
                                            });
                                        }
                                    }
                                }).returningInvocationOf("icon").withArgument("key").on("super");
                            });
                })
                .conditionally(hasNames, cbb -> {
                    cbb.overridePublic("displayName").addArgument(enumTypeSimple, "key").returning(STRING.simpleName())
                            .body(bb -> {
                                bb.switchingOn("key", sw -> {
                                    for (Iterator<EnumConstantInfo> it = infos.iterator(); it.hasNext();) {
                                        EnumConstantInfo info = it.next();
                                        if (info.displayName != null) {
                                            sw.inCase(info.enumConstantName, switchBlock -> {
                                                // Allow annotations to include a relative path
                                                // to the icon, and expand it to the full path
                                                switchBlock.trying(tb -> {
                                                    String field = staticFieldName(info, DISPLAY_NAME_SUFFIX);
                                                    tb.returningInvocationOf("localizedString")
                                                            .withArgument(field)
                                                            .on("super")
                                                            .catching(cat -> {
                                                                cat.lineComment(
                                                                        "already logged by localizedString()");
                                                                cat.returning(field);
                                                            }, MISSING_RESOURCE_EXCEPTION.simpleName());
                                                });
                                            });
                                        }
                                    }
                                })
                                        .lineComment("Fall through to NbBundle.getMessage(getClass(), \"${ENUM_CONST_NAME}\")")
                                        .returningInvocationOf("displayName").withArgument("key").on("super");

                            });
                });
        TypeName.addImports(cb, LOCALIZER, ENUM_CONSTANT_LOCALIZER,
                SERVICE_PROVIDER, ICON,
                MISSING_RESOURCE_EXCEPTION);
        for (EnumConstantInfo e : infos) {
            classesForElements.get(cb).add(e.element);
        }
//        writeOne(cb);
    }

    private List<String> hintsToStringList(AnnotationMirror mirror, Element el) {
        List<String> hints = new ArrayList<>(6);
        Set<String> keys = new HashSet<>();
        for (AnnotationMirror hintPair : utils.annotationValues(mirror, "hints", AnnotationMirror.class)) {
            String key = utils.annotationValue(hintPair, "key", String.class);
            String val = utils.annotationValue(hintPair, "value", String.class);
            if (keys.contains(key)) {
                utils.fail("Same hint key is present multiple times: " + key, el);
                continue;
            }
            keys.add(key);
            if (key != null && val != null) {
                hints.add(key);
                hints.add(val);
            }
        }
        return hints;
    }

    private void withAnnotationParams(AnnotationMirror mirror, Element var, TypeElement on, ParamsConsumer c) {
        String pkg = utils.packageName(var);
        String fieldType = var.asType().toString();
        String iconPath = utils.annotationValue(mirror, "iconPath", String.class);
        String fieldFqn = on.getQualifiedName() + "." + var.getSimpleName();
        String displayName = utils.annotationValue(mirror, "displayName", String.class);
        String bundlePath = null;
        if (displayName != null) {
            if (!isBundlePath(displayName)) {
//                String bundle = generatedBundle(pkg, processingEnv.getTy);
//                bundlePath = bundle + "#" + var.getSimpleName();
//                bundlesForBundleNames.get(bundle).put(var.getSimpleName().toString(), displayName);
            } else {
                bundlePath = displayName;
            }
        }
        String erased = processingEnv.getTypeUtils().erasure(var.asType()).toString();
        String generatedClassName;
        String kindName = Strings.dashesToCamelCase(
                var.getKind()
                        .name()
                        .replace('_', '-')
                        .toLowerCase());

        switch (var.getKind()) {
            case FIELD:
                generatedClassName
                        = on.getSimpleName()
                        + "_"
                        + var.getSimpleName()
                        + '_'
                        + kindName
                        + "Localizer";
                break;
            case INTERFACE:
            case ENUM_CONSTANT:
            case CLASS:
            case ANNOTATION_TYPE:
            case ENUM:
                generatedClassName
                        = on.getSimpleName()
                        + "_"
                        + kindName
                        + "Localizer";
                break;
            default:
                utils.fail("Not a handled element kind: " + var.getKind() + ": " + var);
                return;

        }
        List<String> hints = hintsToStringList(mirror, var);
        c.accept(pkg, generatedClassName, fieldType, iconPath, fieldFqn, displayName, bundlePath, hints,
                erased);
    }

    private void handleType(TypeElement el, AnnotationMirror mirror) {
        withAnnotationParams(mirror, el, el,
                (String generateIntoPackage, String classNameToGenerate,
                        String fieldType, String iconPath, String fieldFqn,
                        String displayName, String bundlePath,
                        List<String> hintsAsList, String erasureOfFieldType) -> {

                    TypeName target = TypeName.fromQualifiedName(el.getQualifiedName().toString());
                    String regPath = this.typeLookupPath(el);
                    ClassBuilder<String> cl = ClassBuilder.forPackage(generateIntoPackage)
                            .named(classNameToGenerate);
                    TypeName.addImports(cl, SERVICE_PROVIDER, LOCALIZER, target, SINGLE_INSTANCE_LOCALIZER);
                    cl.extending(KnownTypes.SINGLE_INSTANCE_LOCALIZER.simpleName() + "<Class<?>>")
                            .withModifier(PUBLIC, FINAL)
                            .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                                ab.addClassArgument("service", LOCALIZER.simpleName())
                                        .addArgument("position", 10)
                                        .addArgument("path", regPath);
                            })
                            .constructor(con -> {
                                con.setModifier(PUBLIC).body(bb -> {
                                    String dn = utils.annotationValue(mirror, "displayName", String.class);
                                    if (dn != null) {
                                        if (!isBundlePath(dn)) {
                                            String key = el.getSimpleName().toString() + "Type";
                                            String bp = generatedBundle(generateIntoPackage, el);
                                            addBundleValue(bp, key, dn, el);
                                            dn = bp + '#' + key;
                                        }
                                    }

                                    ClassBuilder.InvocationBuilder<?> ib = bb.invoke("super");
                                    ib.withArgument(target.simpleName() + ".class")
                                            .withArgument(dn != null ? '"' + dn + '"' : "null")
                                            .withArgument(iconPath != null ? '"' + iconPath + '"' : "null");
                                    for (String hint : hintsAsList) {
                                        ib = ib.withStringLiteral(hint);
                                    }
                                    ib.inScope();
                                });
                            });

                    classesForElements.get(cl).add(el);
                });

    }

    private void handleField(VariableElement field, AnnotationMirror mirror, TypeElement on) {
        withAnnotationParams(mirror, field, on,
                (String generateIntoPackage, String classNameToGenerate,
                        String fieldType, String iconPath, String fieldFqn,
                        String displayName, String bundlePath,
                        List<String> hintsAsList, String erasureOfFieldType) -> {

                    String regPath = instanceLookupPath(field.asType());
                    ClassBuilder<String> cl = ClassBuilder.forPackage(generateIntoPackage)
                            .named(classNameToGenerate);
                    TypeName.addImports(cl, SERVICE_PROVIDER, LOCALIZER, SINGLE_INSTANCE_LOCALIZER);
                    cl.extending(SINGLE_INSTANCE_LOCALIZER.simpleName() + "<" + fieldType + ">")
                            .staticImport(fieldFqn)
                            .withModifier(PUBLIC, FINAL)
                            .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                                ab.addClassArgument("service", LOCALIZER.simpleName())
                                        .addArgument("position", 100)
                                        .addArgument("path", regPath);
                            })
                            .constructor(con -> {
                                con.setModifier(PUBLIC).body(bb -> {
                                    String dn = utils.annotationValue(mirror, "displayName", String.class);
                                    if (dn != null) {
                                        if (!isBundlePath(dn)) {
                                            String key = field.getSimpleName().toString() + "Field";
                                            String bp = generatedBundle(generateIntoPackage, field);
//                                            bundlesForBundleNames.get(bp).put(key, dn);
                                            addBundleValue(bp, key, dn, field);
                                            dn = bp + '#' + key;
                                        }
                                    }
                                    ClassBuilder.InvocationBuilder<?> ib = bb.invoke("super");
                                    ib.withArgument(simpleName(fieldFqn))
                                            .withArgument(dn != null ? '"' + dn + '"' : "null")
                                            .withArgument(iconPath != null ? '"' + iconPath + '"' : "null");
                                    for (String hint : hintsAsList) {
                                        ib = ib.withStringLiteral(hint);
                                    }
                                    ib.inScope();
                                });
                            });

                    classesForElements.get(cl).add(field);
                });
    }

    private final Map<String, Set<Element>> elementsForBundlePaths
            = CollectionUtils.supplierMap(HashSet::new);

    private void addBundleValue(String bundlePath, String key, String value, Element el) {
        if (key != null && value != null) {
            bundlesForBundleNames.get(bundlePath).put(key, value);
            elementsForBundlePaths.get(bundlePath).add(el);
        }
    }

    private void writeJavaSources(RoundEnvironment env) throws IOException {
        for (Map.Entry<EnumConstantKey, Set<EnumConstantInfo>> e : this.enumInfo.entrySet()) {
            generateEnumLocalizer(e.getKey(), e.getValue());
        }
        for (Map.Entry<ClassBuilder<String>, Set<Element>> e : classesForElements.entrySet()) {
            writeOne(e.getKey(), e.getValue().toArray(new Element[e.getValue().size()]));
        }
        enumInfo.clear();
        classesForElements.clear();
    }

    static boolean inIDE = System.getProperty("netbeans.home") != null;
    private void writePropertiesFiles(RoundEnvironment env) throws IOException {
        for (Map.Entry<String, Map<String, String>> e : bundlesForBundleNames.entrySet()) {
            Element[] els = elementsForBundlePaths.get(e.getKey()).toArray(new Element[0]);
            writeBundle(e.getKey().replace('.', '/')
                    + ".properties", e.getValue(), processingEnv.getFiler(), utils, els);
        }
        if (!env.errorRaised() && env.processingOver() && !inIDE) {
            System.out.println(KnownTypes.touchedMessage(this));
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING,
//                    KnownTypes.touchedMessage());
        }
        bundlesForBundleNames.clear();
        elementsForBundlePaths.clear();
    }

    protected final void writeOne(ClassBuilder<String> cb, Element... elems) throws IOException {
        Filer filer = processingEnv.getFiler();
        JavaFileObject file = filer.createSourceFile(cb.fqn(), elems);
        try (OutputStream out = file.openOutputStream()) {
            out.write(cb.build().getBytes(UTF_8));
        }
    }

    private static <T extends Enum<T>> String enumLookupPath(TypeElement annotatedEnumConstantType) {
        return ENUM_LOOKUP_BASE
                + annotatedEnumConstantType
                        .getQualifiedName()
                        .toString()
                        .replace('.', '/');
    }

    private String typeLookupPath(TypeElement annotatedType) {
        return TYPE_LOOKUP_BASE
                + utils.erasureOf(annotatedType.asType())
                        .toString()
                        .replace('.', '/');
    }

    private String instanceLookupPath(TypeMirror fieldType) {
        return INSTANCE_LOOKUP_BASE
                + utils.erasureOf(fieldType)
                        .toString()
                        .replace('.', '/');
    }

    private static void writeBundle(String path, Map<String, String> properties, Filer filer, AnnotationUtils utils, Element... elements) throws IOException {
        FileObject file;
        Properties contents = new Properties();
        try {
            file = filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
            try (InputStream in = file.openInputStream()) {
                contents.load(in);
            }
        } catch (FileNotFoundException | NoSuchFileException ex) {
            // ok
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            utils.fail(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage(), elements.length == 0 ? null : elements[0]);
            return;
        }
        contents.putAll(properties);
        file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path, elements);
        utils.log("Write properties with {0} values to {1}. Keys: ", contents.size(), file.getName(), contents.keySet());
        try (OutputStream out = file.openOutputStream()) {
            // Do not use Properties.store() - it insists on adding a
            // timestamp, which defeats repeatable builds.  This library
            // behaves identically, sans that misfeature.
            PropertiesFileUtils.savePropertiesFile(contents, out,
                    "Localized strings for " + Strings.join(',', (Object[]) elements), true);
        }
    }

    @FunctionalInterface
    interface ParamsConsumer {

        public void accept(String pkg, String generatedClassName,
                String fieldType, String iconPath, String fieldFqn,
                String displayName, String bundlePath,
                List<String> hints, String erased);
    }

    final class EnumConstantKey {

        final TypeElement type;
        final String pkg;
        final String fieldType;
        final String generatedClassName;
        final String erasure;

        public EnumConstantKey(TypeElement type, String pkg, String fieldType, String generatedClassName, String erasure) {
            this.type = type;
            this.pkg = pkg;
            this.fieldType = fieldType;
            this.generatedClassName = generatedClassName;
            this.erasure = erasure;
        }

        public String addProperty(String key, String value) {
            String bundle = generatedBundle(this);
            addBundleValue(bundle, key, value, type);
            return bundle + '#' + key;
        }

        public String toString() {
            return pkg + "." + generatedClassName + " " + erasure + " " + fieldType;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + Objects.hashCode(this.pkg);
            hash = 47 * hash + Objects.hashCode(this.generatedClassName);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EnumConstantKey other = (EnumConstantKey) obj;
            if (!Objects.equals(this.pkg, other.pkg)) {
                return false;
            }
            return Objects.equals(this.generatedClassName,
                    other.generatedClassName);
        }
    }

    static final class EnumConstantInfo implements Comparable<EnumConstantInfo> {

        final String displayName;
        final String iconPath;
        final String bundlePath;
        final List<String> hints;
        final String enumConstantName;
        final VariableElement element;

        public EnumConstantInfo(String enumConstantName, String displayName,
                String iconPath, String bundlePath, List<String> hints,
                VariableElement el) {
            this.enumConstantName = enumConstantName;
            this.displayName = displayName;
            this.iconPath = iconPath;
            this.bundlePath = bundlePath;
            this.hints = hints;
            this.element = el;
        }

        @Override
        public String toString() {
            return enumConstantName + " " + displayName;
        }

        @Override
        public int compareTo(EnumConstantInfo o) {
            return o == this ? 0 : enumConstantName.compareTo(o.enumConstantName);
        }
    }
}

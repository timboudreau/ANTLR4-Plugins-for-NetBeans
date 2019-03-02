package org.nemesis.registration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import org.nemesis.registration.api.LayerGeneratingDelegate;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.codegen.ClassBuilder.BlockBuilder;
import org.nemesis.registration.codegen.LinesBuilder;
import org.nemesis.registration.utils.AnnotationUtils;
import org.openide.filesystems.annotations.LayerBuilder;

/**
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = Processor.class)
//@javax.annotation.processing.SupportedAnnotationTypes(HighlighterKeyRegistrationProcessor.ANNO)
public class HighlighterKeyRegistrationProcessor extends LayerGeneratingDelegate {

    static final String PKG_NAME = "org.nemesis.antlr.spi.language.highlighting.semantic";
    static final String ANNO_NAME = "HighlighterKeyRegistration";
    static final String ANNO = PKG_NAME + "." + ANNO_NAME;
    static final String[] COLORING_ATTR_NAMES = new String[]{"coloringName", "colorFinder", "attributeSetFinder", "colors"};
    private Predicate<AnnotationMirror> mirrorTest;
    private Predicate<Element> typeTest;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        log("init");
        mirrorTest = utils.testMirror()
                .testMember("mimeType").stringValueMustNotBeEmpty().validateStringValueAsMimeType().build()
                .testMember("coloringName").stringValueMustNotContainWhitespace().build()
                //                .testMember("colorFinder").asTypeSpecifier().mustBeFullyReifiedType()
                //                    .typeParameterExtends(0, PKG_NAME)
                .atLeastOneMemberMayBeSet(COLORING_ATTR_NAMES)
                .onlyOneMemberMayBeSet(COLORING_ATTR_NAMES)
                .build();

        typeTest = utils.testsBuilder().doesNotHaveModifier(PRIVATE)
                .hasModifier(PUBLIC)
                .hasModifier(STATIC)
                .mustBeFullyReifiedType().hasModifier(FINAL).testContainingClass()
                .doesNotHaveModifier(PRIVATE).build().build();
    }

    final class HighlightingInfo implements Iterable<HighlightingInfo.Entry> {

        private final String mimeType;
        private final List<Entry> entries = new ArrayList<>(5);
        private boolean sorted;

        public HighlightingInfo(String mimeType) {
            this.mimeType = mimeType;
        }

        HighlightingInfo update(VariableElement var, AnnotationMirror mirror, ConversionKind kind) {
            log("HighlightingInfo.update {0} with {1}", kind);
            entries.add(new Entry(var, mirror, kind));
            sorted = false;
            return this;
        }

        public Iterator<HighlightingInfo.Entry> iterator() {
            if (!sorted) {
                Collections.sort(entries);
                sorted = true;
            }
            return entries.iterator();
        }

        public String packageName() {
            if (entries.isEmpty()) {
                return "x.noentries";
            }
            return iterator().next().pkg().getQualifiedName().toString();
        }

        public String[] fieldFqns() {
            Set<String> fields = new TreeSet<>();
            for (Entry e : this) {
                fields.add(e.fieldFqn());
            }
            return fields.toArray(new String[fields.size()]);
        }

        public String generatedClassName() {
            if (entries.isEmpty()) {
                return "Highlighter_NoEntries";
            }
            Entry e = iterator().next();
            return e.enclosingType.getSimpleName() + "_" + e.var.getSimpleName()
                    + "_Highlighting";
        }

        public String generatedFqn() {
            return packageName() + "." + generatedClassName();
        }

        void generateConstructorCode(ClassBuilder<?> classBuilder, BlockBuilder<?> bb, String builderVar) {
            classBuilder.importing("org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger",
                    "org.netbeans.spi.editor.highlighting.ZOrder");
            int ix = 0;
            for (Entry en : this) {
                log("generateConstructorCode", en);
                en.generateConstructorCode(bb, builderVar, ix++);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(mimeType).append('{');
            for (Iterator<Entry> it = entries.iterator(); it.hasNext();) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            return sb.append('}').toString();
        }

        public VariableElement[] allVars() {
            Set<VariableElement> els = new HashSet<>();
            for (Entry e : this) {
                els.add(e.var);
            }
            return els.toArray(new VariableElement[els.size()]);
        }

        final class Entry implements Comparable<Entry> {

            private final VariableElement var;
            private final AnnotationMirror mirror;
            private final ConversionKind kind;
            private final TypeElement enclosingType;

            public Entry(VariableElement var, AnnotationMirror mirror, ConversionKind kind) {
                this.var = var;
                this.mirror = mirror;
                this.kind = kind;
                enclosingType = AnnotationUtils.enclosingType(var);
            }

            void generateConstructorCode(BlockBuilder<?> bb, String builderVar, int entryIndex) {
                /*
                HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType,
                 */
                ClassBuilder.InvocationBuilder<?> iv = bb.invoke("add")
                        .withArgument(refreshTrigger()).withArgument(zOrder())
                        .withArgument(fixedSize()).withArgument(positionInZOrder(entryIndex)) //                        .withStringLiteral(mimeType)
                        ;
                kind.generateColorCode(this, builderVar, var, coloringName(), functionClassName(), iv, mimeType);
                iv.on(builderVar);
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder("{");
                pair("functionClassName", functionClassName(), pair("coloringName", coloringName(),
                        pair("refreshTrigger", refreshTrigger(), pair("zOrder", zOrder(), pair("positionInZOrder",
                                positionInZOrder(0), sb)))));
                sb.append(" on ").append(fieldFqn());
                return sb.append('}').toString();
            }

            private StringBuilder pair(String name, Object val, StringBuilder into) {
                return into.append(" ").append(name).append('=').append(val);
            }

            String functionClassName() {
                List<String> type = utils().typeList(mirror, "colorFinder", "java.util.Function");
                if (type.isEmpty()) {
                    type = utils().typeList(mirror, "attributeSetFinder", "java.util.Function");
                }
                return type.isEmpty() ? null : type.get(0);
            }

            String coloringName() {
                String result = utils().annotationValue(mirror, "coloringName", String.class);
                if (result == null) {
                    AnnotationMirror coloring = utils().annotationValue(mirror, "colors", AnnotationMirror.class);
                    if (coloring != null) {
                        result = utils().annotationValue(coloring, "name", String.class);
                    }
                }
                return result;
            }

            String refreshTrigger() {
                return "HighlightRefreshTrigger."
                        + utils().annotationValue(mirror, "trigger", String.class, "DOCUMENT_CHANGED");
            }

            String zOrder() {
                return "ZOrder."
                        + utils().annotationValue(mirror, "zOrder", String.class, "SYNTAX_RACK");
            }

            int positionInZOrder(int indexOfEntry) {
                return utils().annotationValue(mirror, "positionInZOrder", Integer.class, 100 * indexOfEntry);
            }

            boolean fixedSize() {
                return utils().annotationValue(mirror, "fixedSize", Boolean.class, false);
            }

            String fieldFqn() {
                return enclosingType.getQualifiedName() + "." + var.getSimpleName();
            }

            private PackageElement pkg() {
                return processingEnv.getElementUtils().getPackageOf(enclosingType);
            }

            private int packageDepth(PackageElement el) {
                int result = 1;
                while (el.getEnclosingElement() instanceof PackageElement) {
                    result++;
                    el = (PackageElement) el.getEnclosingElement();
                }
                return result;
            }

            @Override
            public int compareTo(Entry o) {
                PackageElement mine = pkg();
                PackageElement theirs = o.pkg();
                if (mine.getQualifiedName().contentEquals(theirs.getQualifiedName())) {
                    return 0;
                }
                int myPd = packageDepth(mine);
                int theirPd = packageDepth(theirs);
                int result = myPd == theirPd ? 0 : myPd > theirPd ? 1 : -1;
                if (result == 0) {
                    int myLength = mine.getQualifiedName().length();
                    int theirLength = theirs.getQualifiedName().length();
                    result = myLength == theirLength ? 0 : myLength > theirLength ? 1 : -1;
                }
                if (result == 0) {
                    result = mine.getQualifiedName().toString().compareToIgnoreCase(theirs.getQualifiedName().toString());
                }
                return result;
            }
        }
    }

    private final Map<String, HighlightingInfo> highlightingForMimeType = new HashMap<>();

    private HighlightingInfo infoForMimeType(String mimeType) {
        HighlightingInfo result = highlightingForMimeType.get(mimeType);
        if (result == null) {
            result = new HighlightingInfo(mimeType);
            highlightingForMimeType.put(mimeType, result);
        }
        return result;
    }

    private HighlightingInfo updateInfo(String mimeType, VariableElement var, AnnotationMirror mirror, ConversionKind kind) {
        return infoForMimeType(mimeType).update(var, mirror, kind);
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment roundEnv) throws IOException {
        if (roundEnv.processingOver()) {
            for (Map.Entry<String, HighlightingInfo> e : highlightingForMimeType.entrySet()) {
                generateForMimeType(e.getKey(), e.getValue(), false, true);
            }
        }
        return false;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!typeTest.test(var)) {
            return true;
        }
        ConversionKind conversion = ConversionKind.forAnnotationMirror(mirror, utils());
        if (conversion == null) {
            utils().fail("Could not determine what code to generate for coloration", var);
            return false;
        }
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        HighlightingInfo info = updateInfo(mimeType, var, mirror, conversion);
        generateForMimeType(mimeType, info, true, false);
        return false;
    }

    private final Set<String> savedToLayer = new HashSet<>();
    private boolean generateForMimeType(String mimeType, HighlightingInfo info, boolean saveLayer, boolean saveClass) throws IOException {
        log("Generate highlighting for {0} saveLayer {1}, saveClass {2}", mimeType, saveLayer, saveClass);
        String generatedClassName = info.generatedClassName();
        String pkg = info.packageName();
        String fqn = pkg + "." + generatedClassName;

        if (saveClass) {
            ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(generatedClassName)
                    .staticImport(info.fieldFqns())
                    .importing("javax.annotation.processing.Generated", "org.netbeans.spi.editor.highlighting.HighlightsLayerFactory",
                            "org.netbeans.spi.editor.highlighting.HighlightsLayer", "org.nemesis.antlr.highlighting.AntlrHighlightingLayerFactory",
                            "org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger",
                            "org.netbeans.api.editor.mimelookup.MimeRegistrations", "org.netbeans.api.editor.mimelookup.MimeRegistration")
                    //                .annotatedWith("MimeRegistrations", mrOuter -> {
                    //                    mrOuter.addAnnotationArgument("value", "MimeRegistration", mrInner -> {
                    //                        mrInner.addStringArgument("mimeType", mimeType)
                    //                                .addClassArgument("service", "HighlightsLayerFactory")
                    //                                .addArgument("position", 1000)
                    //                                .closeAnnotation();
                    //                    }).closeAnnotation();
                    //                })
                    //                .annotatedWith("MimeRegistration", mrOuter -> {
                    //                    mrOuter.addStringArgument("mimeType", mimeType)
                    //                            .addClassArgument("service", "HighlightsLayerFactory")
                    //                            .addArgument("position", 1000)
                    //                            .closeAnnotation();
                    //                })
                    .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                    .addModifier(PUBLIC).addModifier(FINAL)
                    .implementing("HighlightsLayerFactory")
                    .field("delegate").withModifier(PRIVATE).withModifier(FINAL).ofType("HighlightsLayerFactory");
            cl.docComment(info.entries.toArray());
            cl.constructor(cb -> {
                cb.addModifier(PUBLIC).body(bb -> {
                    bb.log("Create {0}: ", Level.WARNING, LinesBuilder.stringLiteral(generatedClassName), LinesBuilder.stringLiteral(info.toString()));
                    bb.declare("bldr").initializedByInvoking("builder")
                            .on("AntlrHighlightingLayerFactory")
                            .as("AntlrHighlightingLayerFactory.Builder");
                    info.generateConstructorCode(cl, bb, "bldr");
                    bb.statement("delegate = bldr.build()");
                    bb.endBlock();
                }).endConstructor();
            })
                    .override("createLayers").returning("HighlightsLayer[]").addArgument("HighlightsLayerFactory.Context", "ctx")
                    .body(mb -> {
                        mb.debugLog("Create " + info);
                        mb.log("Create layer {0}", Level.INFO, LinesBuilder.stringLiteral(info.toString()));
                        mb.statement("return delegate.createLayers(ctx)").endBlock();
                    }).withModifier(PUBLIC).closeMethod();

            cl.generateDebugLogCode();
            writeOne(cl);
        }

        if (saveLayer && !savedToLayer.contains(fqn)) {
            log("Saving layer for " + fqn);
            savedToLayer.add(fqn);
            String layerFileName = fqn.replace('.', '-');
            String registrationFile = "Editors/" + info.mimeType + "/" + layerFileName + ".instance";
            LayerBuilder layer = layer(info.allVars());
            layer.file(registrationFile)
                    .stringvalue("instanceClass", fqn)
                    .stringvalue("instanceOf", "org.netbeans.spi.editor.highlighting.HighlightsLayerFactory")
                    .write();
            ;
        }
        return false;
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind) {
        return mirrorTest.test(mirror);
    }

    static enum ConversionKind {
        COLORING_NAME(COLORING_ATTR_NAMES[0]),
        COLOR_FINDER(COLORING_ATTR_NAMES[1]),
        ATTRIBUTE_SET_FINDER(COLORING_ATTR_NAMES[2]),
        COLORS_SPECIFIER(COLORING_ATTR_NAMES[3]);
        private final String member;

        ConversionKind(String mem) {
            this.member = mem;
        }

        boolean isFunction() {
            return this == COLOR_FINDER || this == ATTRIBUTE_SET_FINDER;
        }

        boolean isColoringName() {
            return this == COLORS_SPECIFIER || this == COLORING_NAME;
        }

        @Override
        public String toString() {
            return member;
        }

        public static ConversionKind forAnnotationMirror(AnnotationMirror mir, AnnotationUtils utils) {
            for (ConversionKind k : values()) {
                List<Object> all = utils.annotationValues(mir, k.toString(), Object.class);
                if (!all.isEmpty()) {
                    return k;
                }
            }
            return null;
        }

        private void generateColorCode(HighlightingInfo.Entry aThis, String builderVar,
                VariableElement var, String coloringName, String functionClassName,
                ClassBuilder.InvocationBuilder<?> iv, String mimeType) {
            iv.withArgument(var.getSimpleName().toString());
            if (isFunction()) {
                iv.withStringLiteral(mimeType);
                iv.withArgument("new " + functionClassName + "()");
            } else {
                iv.withStringLiteral(mimeType).withStringLiteral(coloringName);
            }
        }
    }
}

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
import static org.nemesis.registration.LanguageFontsColorsProcessor.GROUP_SEMANTIC_HIGHLIGHTING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.SEMANTIC_HIGHLIGHTING_ANNO;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.LinesBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import java.util.Comparator;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_HIGHLIGHTING_LAYER_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.HIGHLIGHTS_LAYER;
import static org.nemesis.registration.typenames.KnownTypes.HIGHLIGHTS_LAYER_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.HIGHLIGHT_REFRESH_TRIGGER;
import static org.nemesis.registration.typenames.KnownTypes.ZORDER;
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
    static final String GROUP_ANNO_NAME = "HighlighterKeyRegistrations";
    static final String ANNO = PKG_NAME + "." + ANNO_NAME;
    static final String GROUP_ANNO = PKG_NAME + "." + GROUP_ANNO_NAME;
    static final String[] COLORING_ATTR_NAMES = new String[]{"coloringName", "colorFinder", "attributeSetFinder", "colors"};
    private Predicate<? super AnnotationMirror> mirrorTest;
    private Predicate<? super AnnotationMirror> groupMirrorTest;
    private Predicate<? super Element> typeTest;

    // XXX currently you HAVE to have at least one highlighter key,
    // or your syntax highlighting for tokens is not registered.  Needs fixing.
    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        log("init");
        mirrorTest = utils.testMirror()
                .testMember("mimeType").stringValueMustNotBeEmpty().addPredicate("Mime type", mir -> {
            Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
            String value = utils().annotationValue(mir, "mimeType", String.class);
            return mimeTest.test(value);
        }).build()
                .testMember("coloringName").stringValueMustNotContainWhitespace().build()
                //                .testMember("colorFinder").asTypeSpecifier().mustBeFullyReifiedType()
                //                    .typeParameterExtends(0, PKG_NAME)
                .atLeastOneMemberMayBeSet(COLORING_ATTR_NAMES)
                .onlyOneMemberMayBeSet(COLORING_ATTR_NAMES)
                .build();

        groupMirrorTest = utils().testMirror()
                .testMemberAsAnnotation("value").addPredicate(mirrorTest).build().build();

        typeTest = utils.testsBuilder().doesNotHaveModifier(PRIVATE)
                .hasModifier(PUBLIC)
                .hasModifier(STATIC)
                .mustBeFullyReifiedType().hasModifier(FINAL).testContainingClass()
                .doesNotHaveModifier(PRIVATE).build().build();
    }

    class HighlightingInfo implements Iterable<HighlightingInfo.Entry> {

        private final String mimeType;
        private final List<Entry> entries = new ArrayList<>(5);
        private boolean sorted;

        public HighlightingInfo(String mimeType) {
            this.mimeType = mimeType;
        }

        private List<HighlightingInfo> splitByZOrder() {
            Set<String> zorders = zordersPresent();
            List<HighlightingInfo> result = new ArrayList<>();
            for (String z : zorders) {
                result.add(filter(z));
            }
            return result;
        }

        private HighlightingInfo filter(String zorder) {
            HighlightingInfo result = new HighlightingInfo(mimeType) {
                @Override
                public String generatedClassName() {
                    return super.generatedClassName() + "_" + zorder.toLowerCase();
                }
            };
            for (Entry e : entries) {
                if (zorder.equals(e.rawZOrder())) {
                    result.entries.add(e);
                }
            }
            return result;
        }

        private Set<String> zordersPresent() {
            Set<String> result = new HashSet<>();
            for (Entry e : entries) {
                result.add(e.rawZOrder());
            }
            return result;
        }

        HighlightingInfo update(VariableElement var, AnnotationMirror mirror, ConversionKind kind, int index) {
            log("HighlightingInfo.update {0} with {1}", kind);
            entries.add(new Entry(var, mirror, kind, index));
            sorted = false;
            return this;
        }

        int order() {
            return orderForFqn.getOrDefault(fqn(), Integer.MAX_VALUE);
        }

        String fqn() {
            return packageName() + "." + generatedClassName();
        }

        @Override
        public Iterator<HighlightingInfo.Entry> iterator() {
            if (!sorted) {
                Collections.sort(entries, (a, b) -> {
                    int ap = a.positionInZOrder(0);
                    int bp = b.positionInZOrder(0);
                    return ap > bp ? 1 : ap == bp ? 0 : -1;
                });
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
//            Entry e = iterator().next();
            List<String> enclosingTypes = new ArrayList<>();
            List<String> varNames = new ArrayList<>();
            for (Entry e : this) {
                varNames.add(e.variableName());
                enclosingTypes.add(e.enclosingType.getSimpleName().toString());
            }
            enclosingTypes.sort(Comparator.naturalOrder());
            varNames.sort(Comparator.naturalOrder());
            StringBuilder sb = new StringBuilder(enclosingTypes.get(0));
            for (String name : varNames) {
                sb.append('_').append(name);
            }
            sb.append("_HighlighterRegistrations");
            return sb.toString();
        }

        public String generatedFqn() {
            return packageName() + "." + generatedClassName();
        }

        void generateConstructorCode(ClassBuilder<?> classBuilder, BlockBuilder<?> bb, String builderVar) {
            classBuilder.importing(HIGHLIGHT_REFRESH_TRIGGER.qname(), ZORDER.qname());
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

        public Element[] allVars() {
            Set<VariableElement> els = new HashSet<>();
            for (Entry e : this) {
                els.add(e.var);
            }
            return els.toArray(new Element[els.size()]);
        }

        final class Entry implements Comparable<Entry> {

            private final VariableElement var;
            private final AnnotationMirror mirror;
            private final ConversionKind kind;
            private final TypeElement enclosingType;
            private final int index;

            public Entry(VariableElement var, AnnotationMirror mirror, ConversionKind kind, int index) {
                this.var = var;
                this.mirror = mirror;
                this.kind = kind;
                enclosingType = AnnotationUtils.enclosingType(var);
                this.index = index;
            }

            void generateConstructorCode(BlockBuilder<?> bb, String builderVar, int entryIndex) {
                /*
                HighlightRefreshTrigger trigger, ZOrder zorder, boolean fixedSize,
                int positionInZOrder, String mimeType,
                 */
                bb.lineComment(mirror.toString());
                InvocationBuilder<?> iv = bb.invoke("add")
                        .withArgument(refreshTrigger()).withArgument(zOrder())
                        .withArgument(fixedSize()).withArgument(positionInZOrder(entryIndex)) //                        .withStringLiteral(mimeType)
                        ;
                kind.generateColorCode(this, builderVar, var, coloringName(), functionClassName(), iv, mimeType,
                        hasColorFinder(), utils());
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

            boolean hasColorFinder() {
                return !utils().typeList(mirror, "colorFinder").isEmpty();
            }

            boolean hasAttributeSetFinder() {
                return !utils().typeList(mirror, "attributeSetFinder").isEmpty();
            }

            String functionClassName() {
                List<String> type = utils().typeList(mirror, "colorFinder" /*, "java.util.Function" */);
                if (type.isEmpty()) {
                    type = utils().typeList(mirror, "attributeSetFinder" /*, "java.util.Function" */);
                }
                if (!type.isEmpty()) {
                    return type.get(0).replace('$', '.');
                }
                return null;
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
                return HIGHLIGHT_REFRESH_TRIGGER.simpleName() + "."
                        + utils().enumConstantValue(mirror, "trigger", "DOCUMENT_CHANGED");
            }

            String zOrder() {
                return "ZOrder."
                        + rawZOrder();
            }

            String rawZOrder() {
                return utils().enumConstantValue(mirror, "zOrder", "SYNTAX_RACK");
            }

            int positionInZOrder(int indexOfEntry) {
                return utils().annotationValue(mirror, "positionInZOrder", Integer.class, 100 * indexOfEntry);
            }

            boolean fixedSize() {
                return utils().annotationValue(mirror, "fixedSize", Boolean.class, true);
            }

            String fieldFqn() {
                return enclosingType.getQualifiedName() + "." + var.getSimpleName();
            }

            private PackageElement pkg() {
                return processingEnv.getElementUtils().getPackageOf(enclosingType);
            }

            String variableName() {
                return var.getSimpleName().toString();
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

    private HighlightingInfo updateInfo(String mimeType, VariableElement var, AnnotationMirror mirror, ConversionKind kind, int index) {
        return infoForMimeType(mimeType).update(var, mirror, kind, index);
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment roundEnv) throws IOException {
        if (roundEnv.processingOver()) {
            for (Map.Entry<String, HighlightingInfo> e : highlightingForMimeType.entrySet()) {
                generateForMimeType(e.getKey(), e.getValue(), false, true, e.getValue().order());
            }
        }
        if (!roundEnv.errorRaised() && roundEnv.processingOver() && System.getProperty("netbeans.home") == null) {
            System.out.println(KnownTypes.touchedMessage(this));
        }
        return false;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        boolean result = true;
        if (GROUP_SEMANTIC_HIGHLIGHTING_ANNO.equals(mirror.getAnnotationType().toString())) {
            List<AnnotationMirror> multi = utils().annotationValues(mirror, "value", AnnotationMirror.class);
            log("Process multiple annotations of size {0}", multi.size());
            int ix = 0;
            for (AnnotationMirror individual : multi) {
                result &= processSingleFieldAnnotation(var, individual, roundEnv, ix++);
            }
        } else {
            log("Process individual annotation");
            result = processSingleFieldAnnotation(var, mirror, roundEnv, 0);
        }
        return result;
    }

    boolean processSingleFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv, int index) throws Exception {
        if (!SEMANTIC_HIGHLIGHTING_ANNO.equals(mirror.getAnnotationType().toString())) {
            throw new IllegalArgumentException("Wrong annotation type: " + mirror.getAnnotationType());
        }
        if (!typeTest.test(var)) {
            return true;
        }
        ConversionKind conversion = ConversionKind.forAnnotationMirror(mirror, utils());
        if (conversion == null) {
            utils().fail("Could not determine what code to generate for coloration for " + mirror, var);
            return false;
        }
        String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));

        int position = utils().annotationValue(mirror, "order", Integer.class, 100);

        HighlightingInfo info = updateInfo(mimeType, var, mirror, conversion, index);
        generateForMimeType(mimeType, info, true, false, position);
        return false;
    }

    private final Set<String> savedToLayer = new HashSet<>();
    private final Map<String, Integer> orderForFqn = new HashMap<>();

    private int baseZOrder(String z) {
        switch (z) {
            case "BOTTOM_RACK":
                return 0;
            case "SYNTAX_RACK":
                return 10;
            case "CARET_RACK":
                return 20;
            case "DEFAULT_RACK":
                return 30;
            case "SHOW_OFF_RACK":
                return 40;
            case "TOP_RACK":
                return 50;
            default:
                return 30;
        }
    }

    private int inc = 0;

    private boolean generateForMimeType(String mimeType, HighlightingInfo master, boolean saveLayer, boolean saveClass, int position) throws IOException {
        // Highlights clobber each other if the same factory generates instances
        // that belong at different z-orders
        int[] ct = new int[1];
        for (HighlightingInfo info : master.splitByZOrder()) {
            String generatedClassName = info.generatedClassName();
            String pkg = info.packageName();
            String fqn = pkg + "." + generatedClassName;
            log("Generate highlighting for {0} saveLayer {1}, saveClass {2} as {3} in {4}",
                    mimeType, saveLayer, saveClass, generatedClassName, pkg);

            String z = info.zordersPresent().iterator().next();
            // Let the order be influenced by the z order
            int basePosition = (baseZOrder(z) * 10) + inc++;

            orderForFqn.put(fqn, basePosition);

            if (saveClass) {
                ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(generatedClassName)
                        //                        .generateDebugLogCode()
                        .staticImport(info.fieldFqns())
                        .importing(
                                //                                "javax.annotation.processing.Generated",
                                HIGHLIGHTS_LAYER.qname(),
                                HIGHLIGHTS_LAYER_FACTORY.qname(),
                                HIGHLIGHT_REFRESH_TRIGGER.qname(),
                                ANTLR_HIGHLIGHTING_LAYER_FACTORY.qname()
                        //                        .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                        )
                        .withModifier(PUBLIC).withModifier(FINAL)
                        .implementing(HIGHLIGHTS_LAYER_FACTORY.simpleName())
                        .field("delegate").withModifier(PRIVATE).withModifier(FINAL)
                        .ofType(HIGHLIGHTS_LAYER_FACTORY.simpleName());
                cl.docComment(info.entries.toArray());
                cl.constructor(cb -> {
                    cb.setModifier(PUBLIC).body(bb -> {
                        bb.log("Create {0}: ", Level.FINE, LinesBuilder.stringLiteral(generatedClassName), LinesBuilder.stringLiteral(info.toString()));
                        bb.declare("builder").initializedByInvoking("builder")
                                .on(ANTLR_HIGHLIGHTING_LAYER_FACTORY.simpleName())
                                .as(ANTLR_HIGHLIGHTING_LAYER_FACTORY.simpleName() + ".Builder");
                        info.generateConstructorCode(cl, bb, "builder");
                        bb.assign("delegate").toInvocation("build").on("builder");
//                        bb.statement("delegate = bldr.build()");
//                        bb.endBlock();
                    });
                }).overridePublic("createLayers", mb -> {
                    mb.returning(HIGHLIGHTS_LAYER.simpleNameArray()).addArgument(HIGHLIGHTS_LAYER_FACTORY.simpleName() + ".Context", "ctx")
                            .body(bb -> {
                                bb.debugLog("Create " + info);
                                bb.log("Create layer {0}", Level.FINEST, LinesBuilder.stringLiteral(info.toString()));
                                bb.returningInvocationOf("createLayers").withArgument("ctx").on("delegate");
//                                bb.statement("return delegate.createLayers(ctx)").endBlock();
                            });
                });
                writeOne(cl);
            }

            if (saveLayer && !savedToLayer.contains(fqn)) {
                log("Saving layer for {0}", fqn);
                savedToLayer.add(fqn);
                String layerFileName = fqn.replace('.', '-');
                String registrationFile = "Editors/" + info.mimeType + "/" + layerFileName + ".instance";
                int pos = position;
                withLayer((LayerBuilder layer) -> {
                    layer.file(registrationFile)
                            .stringvalue("instanceClass", fqn)
                            .stringvalue("instanceOf", "org.netbeans.spi.editor.highlighting.HighlightsLayerFactory")
                            .intvalue("position", pos + ct[0]++)
                            .write();

                }, info.allVars());
            }
        }
        return false;
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        if (!GROUP_SEMANTIC_HIGHLIGHTING_ANNO.equals(mirror.getAnnotationType().toString())) {
            return mirrorTest.test(mirror);
        }
        return true;
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
                ClassBuilder.InvocationBuilder<?> iv, String mimeType, boolean hasColorFinder, AnnotationUtils utils) {
            iv.withArgument(var.getSimpleName().toString());
            // Adding some comments here - javac on JDK 8 does not get this right
            boolean isNamed = utils.isAssignable(utils.erasureOf(var.asType()), "org.nemesis.extraction.key.NamedRegionKey")
                    || utils.isAssignable(var.asType(), "org.nemesis.extraction.key.NamedRegionKey")
                    || var.asType().toString().startsWith("org.nemesis.extraction.key.NamedRegionKey");
            if (isNamed && !hasColorFinder && functionClassName != null) {
                // Builder uses a different argument order when passing a NamedRegionKey to
                // avoid an erasure clash
                iv.withNewInstanceArgument().ofType(functionClassName)
                        .withStringLiteral(mimeType);
                return;
            }
            if (isFunction()) {
                iv.withStringLiteral(mimeType);
                iv.withArgument("new " + functionClassName + "()");
            } else {
                iv.withStringLiteral(mimeType).withStringLiteral(coloringName);
            }
        }
    }
}

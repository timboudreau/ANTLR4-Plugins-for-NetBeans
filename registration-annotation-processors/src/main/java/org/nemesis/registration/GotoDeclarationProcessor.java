package org.nemesis.registration;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.utils.AnnotationUtils;
import static org.nemesis.registration.utils.AnnotationUtils.capitalize;
import static org.nemesis.registration.utils.AnnotationUtils.simpleName;
import static org.nemesis.registration.utils.AnnotationUtils.stripMimeType;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({GOTO_ANNOTATION, REGISTRATION_ANNO})
public class GotoDeclarationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    static final String GOTO_ANNOTATION = "org.nemesis.antlr.spi.language.Goto";
    private Predicate<Element> fieldTest;
    private Predicate<AnnotationMirror> annoTest;
    static final String NAMED_REFERENCE_SEMANTIC_REGION_REFERENCE_TYPE = "org.nemesis.data.named.NamedSemanticRegionReference";
    static final String NAME_REFERENCE_SET_KEY_TYPE = "org.nemesis.extraction.key.NameReferenceSetKey";
    static final String REF_SET_KEY_TYPE = NAME_REFERENCE_SET_KEY_TYPE;
    static final String ABSTRACT_EDITOR_ACTION_TYPE = "org.netbeans.spi.editor.AbstractEditorAction";
    static final String TOKEN_CONTEXT_PATH_TYPE = "org.netbeans.editor.TokenContextPath";
    static final String EXTRACTION_TYPE = "org.nemesis.extraction.Extraction";
    static final String NAMED_REGION_REFERENCE_SETS_TYPE = "org.nemesis.data.named.NamedRegionReferenceSets";
    static final String NB_ANTLR_UTILS_TYPE = "org.nemesis.antlr.spi.language.NbAntlrUtils";
    static final String EXCEPTIONS_TYPE = "org.openide.util.Exceptions";
    static final String DECLARATION_TOKEN_PROCESSOR_TYPE = "org.netbeans.editor.ext.ExtSyntaxSupport.DeclarationTokenProcessor";
    static final String EDITOR_TOKEN_ID_TYPE = "org.netbeans.editor.TokenID";
    static final String BASE_DOCUMENT_TYPE = "org.netbeans.editor.BaseDocument";

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        fieldTest = utils.testsBuilder().mustBeFullyReifiedType()
                .isKind(ElementKind.FIELD)
                .isSubTypeOf(REF_SET_KEY_TYPE)
                .hasModifier(STATIC)
                .doesNotHaveModifier(PRIVATE)
                .testContainingClass().doesNotHaveModifier(PRIVATE)
                .build().build();
        ;
        annoTest = utils().testMirror().testMember("mimeType").validateStringValueAsMimeType().build().build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind) {
        return annoTest.test(mirror);
    }

    private final Map<String, Set<String>> all = new HashMap<>();
    private final Map<String, Set<Element>> variablesForItem = new HashMap<>();
    private final Map<String, String> targetPackageForMimeType = new HashMap<>();

    private Set<Element> setFor(String keyField) {
        return variablesForItem.getOrDefault(keyField, Collections.emptySet());
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        // We need to consume this annotation to accurately get the package the
        // data object type will be generated into, in order to generate the
        // declaration token processor next to it
        if (REGISTRATION_ANNO.equals(mirror.getAnnotationType().toString())) {
            String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
            if (mimeType != null) {
                AnnotationMirror file = utils().annotationValue(mirror, "file", AnnotationMirror.class);
                if (file != null) {
                    String pkg = utils().packageName(type);
                    System.out.println("Update target for " + mimeType + " to " + pkg);
                    targetPackageForMimeType.put(mimeType, pkg);
                }
            }
        }
        return false;
    }

    private void addField(String mimeType, String targetType, VariableElement on) {
        if (!targetPackageForMimeType.containsKey(mimeType)) {
            targetPackageForMimeType.put(mimeType, processingEnv.getElementUtils().getPackageOf(AnnotationUtils.enclosingType(on)).getQualifiedName().toString());
        }
        Set<String> fields = all.get(mimeType);
        if (fields == null) {
            fields = new HashSet<>();
            all.put(mimeType, fields);
        }
        fields.add(targetType);
        Set<Element> vars = variablesForItem.get(targetType);
        if (vars == null) {
            vars = new HashSet<>();
            variablesForItem.put(targetType, vars);
        }
        vars.add(on);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!fieldTest.test(var)) {
            return false;
        }
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        TypeElement targetType = AnnotationUtils.enclosingType(var);
        addField(mimeType, targetType.getQualifiedName() + "." + var.getSimpleName(), var);
        return true;
    }

    private final Set<String> builtMimeTypes = new HashSet<>();

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        if (!all.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : all.entrySet()) {
                if (!builtMimeTypes.contains(e.getKey())) {
                    ClassBuilder<String> bldr = generateCustomGotoDeclarationAction(e.getKey(), e.getValue());
                    if (bldr != null) {
                        writeOne(bldr);
                    }
                    ClassBuilder<String> declBuilder = declarationFinder(e.getKey(), e.getValue());
                    writeOne(declBuilder);
                }
                builtMimeTypes.add(e.getKey());
            }
        }
        return false;
    }

    private Set<Element> setsFor(Set<String> keyFqns) {
        Set<Element> result = new HashSet<>();
        for (String fqn : keyFqns) {
            result.addAll(setFor(fqn));
        }
        return result;
    }

    private String targetPackage(String mimeType) {
        String result = targetPackageForMimeType.get(mimeType);
        if (result == null) {
            throw new IllegalArgumentException("No target package for " + mimeType);
        }
        return result;
    }

    private String dataObjectPackageForMimeType(String mimeType) {
        return targetPackage(mimeType) + ".file";
    }

    private ClassBuilder<String> declarationFinder(String mimeType, Set<String> fieldFqns) {
        String targetPackage = dataObjectPackageForMimeType(mimeType);
        String generatedClassName = capitalize(stripMimeType(mimeType)) + "DeclarationTokenProcessor";
        ClassBuilder<String> cb = ClassBuilder.forPackage(targetPackage)
                .named(generatedClassName)
                .importing(DECLARATION_TOKEN_PROCESSOR_TYPE, BASE_DOCUMENT_TYPE, EDITOR_TOKEN_ID_TYPE,
                        EXCEPTIONS_TYPE, NB_ANTLR_UTILS_TYPE, NAMED_REGION_REFERENCE_SETS_TYPE,
                        NAME_REFERENCE_SET_KEY_TYPE, TOKEN_CONTEXT_PATH_TYPE,
                        NAMED_REFERENCE_SEMANTIC_REGION_REFERENCE_TYPE,
                        EXTRACTION_TYPE)
                .implementing(simpleName(DECLARATION_TOKEN_PROCESSOR_TYPE))
                .withModifier(PUBLIC, FINAL)
                .field("KEYS", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedAsArrayLiteral(simpleName(REF_SET_KEY_TYPE) + "<?>", ab -> {
                                fieldFqns.forEach(ab::add);
                                ab.closeArrayLiteral();
                            });
                })
                .field("varName").withModifier(PRIVATE, FINAL).ofType("String")
                .field("startPos").withModifier(PRIVATE, FINAL).ofType("int")
                .field("endPos").withModifier(PRIVATE, FINAL).ofType("int")
                .field("doc").withModifier(PRIVATE, FINAL).ofType(simpleName(BASE_DOCUMENT_TYPE))
                .constructor(con -> {
                    con.addModifier(PUBLIC)
                            .addArgument("String", "varName")
                            .addArgument("int", "startPos")
                            .addArgument("int", "endPos")
                            .addArgument("BaseDocument", "doc")
                            .body(bb -> {
                                bb.statement("this.varName = varName")
                                        .statement("this.startPos = startPos")
                                        .statement("this.endPos = endPos")
                                        .statement("this.doc = doc");
                                bb.log(Level.FINEST).argument("varName").argument("startPos").argument("endPos").argument("doc")
                                        .logging("Create " + generatedClassName + " for {0} at {1}:{2} in {3}")
                                        .endBlock();
                            }).endConstructor();
                })
                .override("getDeclarationPosition", mb -> {
                    mb.withModifier(PUBLIC).returning("int")
                            .body(bb -> {
                                bb.declare("result").initializedWith("new int[] {-1}").as("int[]");
                                bb.invoke("parseImmediately").withArgument("doc")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("Extraction", "extraction").withArgument("Exception", "thrown")
                                                    .body(lbb -> {
                                                        lbb.ifCondition().variable("thrown").notEquals().literal("null")
                                                                .endCondition().thenDo(cbb -> {
                                                                    cbb.log("Thrown in extracting", Level.FINER)
                                                                            .statement("Exceptions.printStackTrace(thrown)")
                                                                            .statement("return")
                                                                            .endBlock();
                                                                }).endIf();
                                                        lbb.simpleLoop(simpleName(REF_SET_KEY_TYPE), "key")
                                                                .over("KEYS", loopBody -> {

                                                                    loopBody.declare("regions").initializedByInvoking("references")
                                                                            .withArgument("key")
                                                                            .on("extraction").as("NamedRegionReferenceSets<?>");

                                                                    // NamedSemanticRegionReference<?> set = x.at( startPos );
                                                                    loopBody.declare("set").initializedByInvoking("at")
                                                                            .withArgument("startPos")
                                                                            .on("regions").as("NamedSemanticRegionReference<?>");

                                                                    loopBody.log(Level.FINE)
                                                                            .argument("startPos")
                                                                            .argument("key")
                                                                            .argument("set")
                                                                            .logging("Start {0} for {1} gets {2}");

                                                                    loopBody.ifCondition().variable("set").notEquals().literal("null").endCondition()
                                                                            .thenDo(doNav -> {
                                                                                doNav.log(Level.FINER)
                                                                                        .argument("set")
                                                                                        .argument("set.referencing()")
                                                                                        .argument("set.referencing().start()")
                                                                                        .logging("Found ref {0} navigating to {1} at {2}");
                                                                                doNav.statement("result[0] = set.referencing().start()");
                                                                                doNav.endBlock();
                                                                            })
                                                                            .endIf().endBlock();
                                                                }).endBlock();

                                                    });
                                        })
                                        .on("NbAntlrUtils")
                                        .returning("result[0]")
                                        .endBlock();

                            }).closeMethod();
                }).override("token", mb -> {
                    mb.withModifier(PUBLIC).returning("boolean")
                            .addArgument(simpleName(EDITOR_TOKEN_ID_TYPE), "tokenId")
                            .addArgument(simpleName(TOKEN_CONTEXT_PATH_TYPE), "path")
                            .addArgument("int", "tokenBufferOffset")
                            .addArgument("int", "tokenLength")
                            .body().returning("true").endBlock().closeMethod();
                            ;

                }).override("nextBuffer", mb -> {
                    mb.withModifier(PUBLIC)
                            .addArgument("char[]", "buffer")
                            .addArgument("int", "offset")
                            .addArgument("int", "length")
                            .addArgument("int", "startPos")
                            .addArgument("int", "preScan")
                            .addArgument("boolean", "lastBuffer")
                            .emptyBody()
                            ;
                }).override("eot").withModifier(PUBLIC)
                .addArgument("int", "offset").returning("int").body().returning(0).endBlock().closeMethod();
        return cb;
    }

    private ClassBuilder<String> generateCustomGotoDeclarationAction(String mimeType, Set<String> value) {
        log("Generate one: {0} with {1}", mimeType, value);
        String gotoDeclarationActionClassName = capitalize(stripMimeType(mimeType))
                + "GotoDeclarationAction";
        ClassBuilder<String> cb = ClassBuilder.forPackage(dataObjectPackageForMimeType(mimeType))
                .named(gotoDeclarationActionClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(ABSTRACT_EDITOR_ACTION_TYPE, "org.netbeans.api.editor.EditorActionNames",
                        "java.awt.event.ActionEvent", "javax.swing.text.JTextComponent", REF_SET_KEY_TYPE,
                        "java.awt.EventQueue",
                        "javax.swing.text.Caret", EXCEPTIONS_TYPE,
                        EXTRACTION_TYPE, NAMED_REGION_REFERENCE_SETS_TYPE, NB_ANTLR_UTILS_TYPE,
                        NAMED_REFERENCE_SEMANTIC_REGION_REFERENCE_TYPE,
                        "org.openide.util.NbBundle", "org.netbeans.editor.ext.ExtKit",
                        "org.netbeans.editor.BaseAction", "org.netbeans.editor.BaseKit"
                )
                .extending(simpleName(ABSTRACT_EDITOR_ACTION_TYPE))
                .field("KEYS", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedAsArrayLiteral(simpleName(REF_SET_KEY_TYPE) + "<?>", ab -> {
                                value.forEach(ab::add);
                                ab.closeArrayLiteral();
                            });
                }).constructor(con -> {
            con.addModifier(PUBLIC)
                    .body(bb -> {
                        bb.log("Create a new " + gotoDeclarationActionClassName, Level.FINE);
                        bb.invoke("putValue")
                                .withArgument("ASYNCHRONOUS_KEY")
                                .withArgument("true")
                                .inScope();
                        bb.invoke("putValue")
                                .withArgument("NAME")
                                .withArgument("EditorActionNames.gotoDeclaration")
                                .inScope();
                        bb.declare("trimmed")
                                .initializedWith("NbBundle.getBundle(BaseKit.class).getString(\"goto-declaration-trimmed\")")
                                .as("String");
                        bb.invoke("putValue")
                                .withArgument("ExtKit.TRIMMED_TEXT")
                                .withArgument("trimmed").inScope();
                        bb.invoke("putValue")
                                .withArgument("BaseAction.POPUP_MENU_TEXT")
                                .withArgument("trimmed").inScope();
                        bb.endBlock();
                    }).endConstructor();

        }).override("actionPerformed", mb -> {
            mb.withModifier(PUBLIC)
                    .addArgument("ActionEvent", "evt").addArgument("JTextComponent", "component")
                    .body(bb -> {
                        bb.declare("caret").initializedByInvoking("getCaret").on("component").as("Caret");
                        bb.ifCondition().variable("caret").equals().literal("null").endCondition()
                                .thenDo().statement("return").endBlock().endIf();
                        bb.declare("position").initializedByInvoking("getDot").on("caret").as("int");
                        bb.log(Level.FINER).argument("position").stringLiteral(gotoDeclarationActionClassName)
                                .logging("Invoke {0} at {1}");
                        bb.invoke("parseImmediately").withArgument("component.getDocument()")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("Extraction", "extraction").withArgument("Exception", "thrown")
                                            .body(lbb -> {
                                                lbb.ifCondition().variable("thrown").notEquals().literal("null")
                                                        .endCondition().thenDo(cbb -> {
                                                            cbb.log("Thrown in extracting", Level.FINER)
                                                                    .statement("Exceptions.printStackTrace(thrown)")
                                                                    .statement("return")
                                                                    .endBlock();
                                                        }).endIf();
                                                lbb.invoke("invokeLater")
                                                        .withLambdaArgument(invokeLater -> {
                                                            invokeLater.body()
                                                                    .invoke("navigateTo")
                                                                    .withArgument("evt")
                                                                    .withArgument("component")
                                                                    .withArgument("extraction")
                                                                    .withArgument("position")
                                                                    .inScope().endBlock();

                                                        })
                                                        .on("EventQueue").endBlock();

                                            });
                                })
                                .on("NbAntlrUtils").endBlock();
                    }).closeMethod();
        }).method("navigateTo", navToBuilder -> {
            navToBuilder.addArgument("ActionEvent", "evt")
                    .addArgument("JTextComponent", "component")
                    .addArgument("Extraction", "extraction")
                    .addArgument("int", "position")
                    .withModifier(PRIVATE)
                    .body(bb -> {
                        bb.log(Level.FINER).argument("position").argument("extraction.logString()")
                                .logging("Find ref at {0} in {1}");
                        bb.simpleLoop(simpleName(REF_SET_KEY_TYPE), "key")
                                .over("KEYS", loopBody -> {

                                    loopBody.declare("regions").initializedByInvoking("references")
                                            .withArgument("key")
                                            .on("extraction").as("NamedRegionReferenceSets<?>");

                                    // NamedSemanticRegionReference<?> set = x.at( startPos );
                                    loopBody.declare("set").initializedByInvoking("at")
                                            .withArgument("position")
                                            .on("regions").as("NamedSemanticRegionReference<?>");

                                    loopBody.ifCondition().variable("set").notEquals().literal("null").endCondition()
                                            .thenDo(doNav -> {
                                                doNav.log(Level.FINER)
                                                        .argument("set")
                                                        .argument("set.referencing()")
                                                        .argument("set.referencing().start()")
                                                        .logging("Found ref {0} navigating to {1} at {2}");
                                                doNav.invoke("navigateTo").withArgument("component")
                                                        .withArgument("set.referencing().start()").inScope();
                                                doNav.statement("return").endBlock();
                                            }).endIf().endBlock();
                                }).endBlock();
                    }).closeMethod();
        }).method("navigateTo", nav -> {
            nav.addArgument("JTextComponent", "component")
                    .addArgument("int", "position")
                    .withModifier(PRIVATE)
                    .body(bb -> {
                        bb.declare("caret").initializedByInvoking("getCaret").on("component").as("Caret");
                        bb.ifCondition().variable("caret").notEquals().literal("null").endCondition()
                                .thenDo(then -> {
                                    then.log(Level.FINER)
                                            .argument("position")
                                            .argument("component")
                                            .logging("Setting caret to {0} in {1}");
                                    then.invoke("resetCaretMagicPosition").withArgument("component").inScope();
                                    then.invoke("setDot").withArgument("position").on("caret").endBlock();
                                }).endIf().endBlock();
                    }).closeMethod();
        });

        Element[] els = setsFor(value).toArray(new Element[0]);
        LayerBuilder layer = layer(els);
        String layerPath = "Editors/" + mimeType + "/Actions/"
                + "goto-declaration"  /* cb.fqn().replace('.', '-') */ + ".instance";
        layer.file(layerPath)
                .stringvalue("instanceClass", cb.fqn())
                .stringvalue("instanceOf", "javax.swing.Action")
                .intvalue("position", 1)
                .write();
        return cb;
    }
}

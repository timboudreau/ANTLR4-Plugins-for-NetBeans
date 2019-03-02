package org.nemesis.registration;

import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import org.nemesis.registration.utils.AnnotationUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import static org.nemesis.registration.LanguageFontsColorsProcessor.SEMANTIC_HIGHLIGHTING_ANNO;
import static org.nemesis.registration.LanguageRegistrationProcessor.ANNO;
import org.nemesis.registration.api.Delegates;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.codegen.LinesBuilder;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({ANNO, SEMANTIC_HIGHLIGHTING_ANNO})
@SupportedOptions(AU_LOG)
public class LanguageRegistrationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    public static final String ANNO = "org.nemesis.antlr.spi.language.AntlrLanguageRegistration";

//    static {
//        AnnotationUtils.forceLogging();
//    }
    @Override
    protected void installDelegates(Delegates delegates) {
        LanguageFontsColorsProcessor proc = new LanguageFontsColorsProcessor();
        HighlighterKeyRegistrationProcessor hkProc = new HighlighterKeyRegistrationProcessor();
        delegates.apply(proc).to(ElementKind.CLASS, ElementKind.INTERFACE)
                .onAnnotationTypesAnd(ANNO)
                .to(ElementKind.FIELD).whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(hkProc).to(ElementKind.FIELD).whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO);
        ;
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        super.onInit(env, utils);
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        TypeMirror lexerClass = utils().typeForSingleClassAnnotationMember(mirror, "lexer");
        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");

        TypeElement lexerClassElement = processingEnv.getElementUtils().getTypeElement(lexerClass.toString());

        if (lexerClassElement == null) {
            utils().fail("Cannot resolve " + lexerClass + " as an element");
        }
        Map<Integer, String> tokenNameForIndex = new HashMap<>();

        int last = -2;
        for (Element el : lexerClassElement.getEnclosedElements()) {
            if (el instanceof VariableElement) {
                VariableElement ve = (VariableElement) el;
                String nm = ve.getSimpleName().toString();
                if (nm == null || nm.startsWith("_") || !"int".equals(ve.asType().toString())) {
                    continue;
                }
                if (ve.getConstantValue() != null) {
                    Integer val = (Integer) ve.getConstantValue();
                    if (val < last) {
                        break;
                    }
                    tokenNameForIndex.put(val, nm);
                    last = val;
                }
            }
        }
        tokenNameForIndex.put(-1, "EOF");
        tokenNameForIndex.put(last+1, "$ERRONEOUS");
        String prefix = generateTokensClasses(tokenNameForIndex, type, mirror, lexerClass, tokenCategorizerClass);

        AnnotationMirror parserHelper = utils().annotationValue(mirror, "parser", AnnotationMirror.class);
        if (parserHelper != null) {
            int entryPointRuleNumber = utils().annotationValue(parserHelper, "entryPointRule", Integer.class, Integer.MIN_VALUE);
            if (entryPointRuleNumber >= 0) {
                TypeMirror parserClass = utils().typeForSingleClassAnnotationMember(parserHelper, "type");
                TypeElement parserClassElement = parserClass == null ? null
                        : processingEnv.getElementUtils().getTypeElement(parserClass.toString());
                if (parserClassElement != null) {
                    Map<Integer, String> ruleIdForName = new TreeMap<>();
                    ExecutableElement parserEntryPointMethod = null;
                    String entryPointRule = null;
                    for (Element el : parserClassElement.getEnclosedElements()) {
                        if (el instanceof VariableElement) {
                            VariableElement ve = (VariableElement) el;
                            String nm = ve.getSimpleName().toString();
                            if (nm == null || !nm.startsWith("RULE_") || !"int".equals(ve.asType().toString())) {
                                continue;
                            }
                            String ruleName = nm.substring(5);
                            if (!ruleName.isEmpty() && ve.getConstantValue() != null) {
                                int val = (Integer) ve.getConstantValue();
                                ruleIdForName.put(val, ruleName);
                                if (val == entryPointRuleNumber) {
                                    entryPointRule = ruleName;
                                }
                            }
                        } else if (entryPointRule != null && el instanceof ExecutableElement) {
                            Name name = el.getSimpleName();
                            if (name != null && name.contentEquals(entryPointRule)) {
                                parserEntryPointMethod = (ExecutableElement) el;
                            }
                        }
                    }
                    if (parserEntryPointMethod == null) {
                        utils().fail("Did not find a method named " + entryPointRule + " or a rule field with value " + entryPointRuleNumber);
                    } else {
                        generateParserClasses(ruleIdForName, parserEntryPointMethod, type, mirror, lexerClass, parserClass, parserHelper, prefix);
                    }
                }
            }
        }
        AnnotationMirror fileInfo = utils().annotationValue(mirror, "file", AnnotationMirror.class);
        if (fileInfo != null && !roundEnv.errorRaised()) {
            String extension = utils().annotationValue(fileInfo, "extension", String.class, ".");
            if (extension != null || !".".equals(extension)) {
                for (char c : extension.toCharArray()) {
                    if (Character.isWhitespace(c)) {
                        utils().fail("File extension '" + extension + "' contains whitespace", type, fileInfo);
                        return false;
                    }
                }
                generateDataObjectClassAndRegistration(fileInfo, extension, mirror, prefix, type);
            }
        }
        // Returning true could stop LanguageFontsColorsProcessor from being run
        return true;
    }

    // DataObject generation
    private void generateDataObjectClassAndRegistration(AnnotationMirror fileInfo, String extension, AnnotationMirror mirror, String prefix, TypeElement type) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if ("<error>".equals(mimeType)) {
            utils().fail("Could not get mime type", type);
            return;
        }
        String iconBaseTmp = utils().annotationValue(fileInfo, "iconBase", String.class, "");
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        if (pkg != null && !pkg.isEmpty() && !iconBaseTmp.isEmpty() && iconBaseTmp.indexOf('/') < 0) {
            // Allow relative paths if the icon file is in the same package
            iconBaseTmp = pkg.replace('.', '/') + '/' + iconBaseTmp;
        }
        String iconBase = iconBaseTmp;
        String dataObjectPackage = pkg + ".file";
        String dataObjectClassName = prefix + "DataObject";
        String fqn = dataObjectPackage + "." + dataObjectClassName;
        String actionPath = "Loaders/" + mimeType + "/Actions";
        boolean multiview = utils().annotationValue(fileInfo, "multiview", Boolean.class, false);
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(dataObjectClassName)
                .importing("org.openide.awt.ActionID", "org.openide.awt.ActionReference",
                        "org.openide.awt.ActionReferences", "org.openide.filesystems.FileObject",
                        "org.openide.filesystems.MIMEResolver", "org.openide.loaders.DataObject",
                        "org.openide.loaders.DataObjectExistsException", "org.openide.loaders.MultiDataObject",
                        "org.openide.loaders.MultiFileLoader", "org.openide.util.Lookup",
                        "org.openide.util.NbBundle.Messages", "javax.annotation.processing.Generated"
                ).staticImport(fqn + ".ACTION_PATH")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .extending("MultiDataObject")
                .generateDebugLogCode()
                .addModifier(PUBLIC).addModifier(FINAL)
                .field("ACTION_PATH").withModifier(STATIC).withModifier(PUBLIC)
                .withModifier(FINAL).withInitializer(LinesBuilder.stringLiteral(actionPath)).ofType(STRING);

        if (multiview) {
            cl.importing("org.netbeans.core.spi.multiview.MultiViewElement",
                    "org.netbeans.core.spi.multiview.text.MultiViewEditorElement",
                    "org.openide.windows.TopComponent"
            );
        }
        addActionAnnotations(cl, fileInfo);
        String msgName = "LBL_" + prefix + "_LOADER";
        cl.annotatedWith("Messages").addStringArgument("value", msgName + "=" + prefix + " files").closeAnnotation();
        cl.annotatedWith("DataObject.Registration", annoBuilder -> {
            annoBuilder.addStringArgument("mimeType", mimeType)
                    .addStringArgument("iconBase", iconBase)
                    .addStringArgument("displayName", "#" + msgName) // xxx localize
                    .addArgument("position", 1536)
                    .closeAnnotation();
        });
        cl.annotatedWith("MIMEResolver.ExtensionRegistration", ab -> {
            ab.addStringArgument("displayName", "#" + msgName)
                    .addStringArgument("mimeType", mimeType)
                    .addStringArgument("extension", extension).closeAnnotation();
        });
        cl.constructor().addArgument("FileObject", "pf")
                .addArgument("MultiFileLoader", "loader")
                .throwing("DataObjectExistsException")
                .body(bb -> {
                    bb.invoke("super").withArgument("pf").withArgument("loader").inScope();
                    bb.log(Level.FINE).stringLiteral(dataObjectClassName)
                            .argument("pf.getPath()")
                            .logging("Create a new {0} for {1}");
//                    bb.statement("if (pf.getPath().startsWith(\"Editors\")) new Exception(pf.getPath()).printStackTrace(System.out)");
                    bb.invoke("registerEditor").withArgument(LinesBuilder.stringLiteral(mimeType))
                            .withArgument(multiview)
                            .inScope().endBlock();
                }).addModifier(PUBLIC).endConstructor();
        cl.override("associateLookup").returning("int").withModifier(PROTECTED).body().returning("1").endBlock().closeMethod();

        if (multiview) {
            cl.method("createEditor").addArgument("Lookup", "lkp").withModifier(PUBLIC).withModifier(STATIC)
                    .annotateWith("MultiViewElement.Registration", ab -> {
                        ab.addStringArgument("displayName", msgName)
                                .addStringArgument("iconBase", iconBase)
                                .addStringArgument("mimeType", mimeType)
                                .addArgument("persistenceType", "TopComponent.PERSISTENCE_ONLY_OPENED")
                                .addStringArgument("preferredID", prefix)
                                .addArgument("position", 1000).closeAnnotation();
                    })
                    .returning("MultiViewEditorElement")
                    .body()
                    .log("Create editor for", Level.FINER, "lkp.lookup(DataObject.class)")
                    .returning("new MultiViewEditorElement(lkp)").endBlock().closeMethod();
        }

        for (String s : new String[]{"copyAllowed", "renameAllowed", "moveAllowed", "deleteAllowed"}) {
            Boolean val = utils().annotationValue(fileInfo, s, Boolean.class);
            if (val != null) {
                createFoMethod(cl, s, val);
            }
        }
        generateEditorKitClassAndRegistration(dataObjectPackage, dataObjectClassName, fileInfo, extension, mirror, prefix, type, mimeType);
        writeOne(cl);
    }

    private static final String EDITOR_KIT_TYPE = "javax.swing.text.EditorKit";
    private static final String NB_EDITOR_KIT_TYPE = "org.netbeans.modules.editor.NbEditorKit";

    private void generateEditorKitClassAndRegistration(String dataObjectPackage, String dataObjectClassName, AnnotationMirror fileInfo, String extension, AnnotationMirror registrationAnno, String prefix, TypeElement type, String mimeType) throws Exception {
        String editorKitName = prefix + "EditorKit";
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage)
                .named(editorKitName)
                .generateDebugLogCode()
                .addModifier(FINAL).addModifier(PUBLIC)
                .importing("javax.annotation.processing.Generated", NB_EDITOR_KIT_TYPE,
                        EDITOR_KIT_TYPE, "org.openide.filesystems.FileObject")
                .extending("NbEditorKit")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .initializedWithStringLiteral(mimeType)
                .field("INSTANCE").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .withInitializer("new " + editorKitName + "()").ofType("EditorKit")
                .constructor().addModifier(PRIVATE).emptyBody()
                .override("getContentType").withModifier(PUBLIC).returning(STRING).body().returning("MIME_TYPE").endBlock().closeMethod()
                .method("create", mb -> {
                    mb.addArgument("FileObject", "file")
                            .withModifier(PUBLIC).withModifier(STATIC)
                            .body(bb -> {
                                bb.log(Level.FINER)
                                        .argument("file.getPath()")
                                        .stringLiteral(editorKitName)
                                        .logging("Fetch editor kit {1} for {0}");
                                bb.returning("INSTANCE").endBlock();
                            }).returning("EditorKit")
                            .closeMethod();
                });
        writeOne(cl);
        String editorKitFqn = dataObjectPackage + "." + editorKitName;
        String editorKitFile = "Editors/" + mimeType + "/" + editorKitFqn.replace('.', '-') + ".instance";
        layer(type).file(editorKitFile)
                .methodvalue("instanceCreate", editorKitFqn, "create")
                .stringvalue("instanceOf", EDITOR_KIT_TYPE)
                //                .stringvalue("instanceOf", NB_EDITOR_KIT_TYPE)
                .write();
    }

    private void createFoMethod(ClassBuilder<String> bldr, String name, boolean value) {
        char[] c = name.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        String methodName = "is" + new String(c);
        bldr.override(methodName).withModifier(PUBLIC).returning("boolean").body().returning(Boolean.toString(value)).endBlock().closeMethod();
    }

    private void addActionAnnotations(ClassBuilder<String> cl, AnnotationMirror fileInfo) {
        Set<String> excludedActions = new HashSet<>(utils().annotationValues(fileInfo, "excludedActions", String.class));
        int position = 1;
        ClassBuilder.ArrayValueBuilder<ClassBuilder.AnnotationBuilder<ClassBuilder<String>>> annoBuilder = cl.annotatedWith("ActionReferences").addArrayArgument("value");
        for (int i = 0; i < DEFAULT_ACTIONS.length; i++) {
            String a = DEFAULT_ACTIONS[i];
            boolean separator = a.charAt(0) == '-';
            if (separator) {
                a = a.substring(1);
            }
            if (excludedActions.contains(a)) {
                continue;
            }
            String[] parts = a.split("/");
            if (parts.length != 2) {
                utils().fail("parts must be action-category / action-id, e.g. "
                        + "System/org.openide.filesystems.FilesystemAction - "
                        + "'" + a + "' does not match this spec.");
                continue;
            }
            String category = parts[0];
            String actionId = parts[1];
            ClassBuilder.AnnotationBuilder<?> ab = annoBuilder.annotation("ActionReference")
                    .addArgument("path", "ACTION_PATH")
                    .addArgument("position", position * 100);
            if (separator) {
                ab.addArgument("separatorAfter", ++position * 100);
            }
            ab.addAnnotationArgument("id", "ActionID", aid -> {
                aid.addStringArgument("category", category).addStringArgument("id", actionId).closeAnnotation();
            });
            ab.closeAnnotation();
            position++;
        }
        annoBuilder.closeArray().closeAnnotation();
    }

    // Parser generation
    private void generateParserClasses(Map<Integer, String> ruleIdForName, ExecutableElement parserEntryPointMethod, TypeElement type, AnnotationMirror mirror, TypeMirror lexerClass, TypeMirror parserClass, AnnotationMirror parserInfo, String prefix) throws IOException {
        String nbParserName = prefix + "NbParser";
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokenTypeName = prefix + "Token";

        boolean changeSupport = utils().annotationValue(parserInfo, "changeSupport", Boolean.class, false);
        int streamChannel = utils().annotationValue(parserInfo, "streamChannel", Integer.class, 0);

        TypeMirror helperClass = utils().typeForSingleClassAnnotationMember(parserInfo, "helper");
        boolean hasExplicitHelperClass = helperClass != null
                && !"org.nemesis.antlr.spi.language.NbParserHelper".equals(helperClass.toString());
        String helperClassName = hasExplicitHelperClass ? helperClass.toString() : prefix + "ParserHelper";

        String entryPointType = parserEntryPointMethod.asType().toString();
        String entryPointSimple = entryPointType;
        if (entryPointType.startsWith("()")) {
            entryPointType = entryPointType.substring(2);
        }
        int ix = entryPointType.lastIndexOf('.');
        if (ix > 0) {
            entryPointSimple = entryPointType.substring(ix + 1);
        }
        final String eSimple = entryPointSimple;
        String parserResultType = "AntlrParseResult";

        ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(nbParserName)
                .addModifier(PUBLIC).addModifier(FINAL)
                .importing("org.netbeans.modules.parsing.api.Snapshot", "org.netbeans.modules.parsing.api.Task",
                        "org.netbeans.modules.parsing.spi.Parser", "org.netbeans.modules.parsing.spi.SourceModificationEvent",
                        "javax.annotation.processing.Generated", "org.nemesis.source.api.GrammarSource",
                        "org.nemesis.source.api.ParsingBag", "org.openide.util.Exceptions", "javax.swing.event.ChangeListener",
                        entryPointType, "org.netbeans.modules.parsing.spi.ParserFactory", "java.util.Collection",
                        "java.util.concurrent.atomic.AtomicBoolean", "java.util.function.BooleanSupplier",
                        "org.nemesis.antlr.spi.language.NbParserHelper", "org.nemesis.antlr.spi.language.AntlrParseResult",
                        "org.nemesis.antlr.spi.language.SyntaxError", "java.util.function.Supplier",
                        "org.nemesis.extraction.Extractor", "org.nemesis.extraction.Extraction",
                        "org.antlr.v4.runtime.CommonTokenStream", "java.util.Optional", "java.util.List"
                )
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .extending("Parser")
                .generateDebugLogCode()
                .docComment("NetBeans parser wrapping ", parserClass, " using entry point method ", parserEntryPointMethod.getSimpleName(), "()");
        if (changeSupport) {
            cl.importing("java.util.Set", "org.openide.util.WeakSet", "org.openide.util.ChangeSupport")
                    .field("INSTANCES").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).withInitializer("new WeakSet<>()").ofType("Set<" + nbParserName + ">")
                    .field("changeSupport").withModifier(FINAL).withModifier(PRIVATE).withInitializer("new ChangeSupport(this)").ofType("ChangeSupport");
        }
        cl.field("HELPER").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).withInitializer("new " + helperClassName + "()").ofType(helperClassName)
                .field("cancelled").withModifier(FINAL).withModifier(PRIVATE).withInitializer("new AtomicBoolean()").ofType("AtomicBoolean")
                .field("lastResult").withModifier(PRIVATE).ofType(parserResultType)
                .constructor().annotatedWith("SuppressWarnings").addStringArgument("value", "LeakingThisInConstructor").closeAnnotation().addModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.lineComment("Languages which have alterable global configuration need to fire changes from their parser;");
                        bb.lineComment("This allows us to track all live instances without leaking memory, so languageSettingsChanged()");
                        bb.lineComment("can trigger events from all live parsers for this language");
                        bb.debugLog("Created a new " + nbParserName);
                        bb.invoke("add").withArgument("this").on("INSTANCES").endBlock();
                    } else {
                        bb.lineComment("To enable support for firing changes, set changeSupport on the")
                                .lineComment("generating annotation to true").endBlock();
                    }
                }).endConstructor()
                .method("parse", (mb) -> {
                    mb.override().addArgument("Snapshot", "snapshot").addArgument("Task", "task").addArgument("SourceModificationEvent", "event")
                            .withModifier(PUBLIC)
                            .body(block -> {
                                block.statement("assert snapshot != null")
                                        .debugLog(nbParserName + ".parse on snapshot")
                                        .invoke("set").withArgument("false").on("cancelled")
                                        .declare("snapshotSource").initializedByInvoking("find")
                                        .withArgument("snapshot").withArgument(tokenTypeName + ".MIME_TYPE")
                                        .on("GrammarSource").as("GrammarSource<Snapshot>")
                                        .declare("bag").initializedByInvoking("forGrammarSource")
                                        .withArgument("snapshotSource")
                                        .on("ParsingBag").as("ParsingBag")
                                        .trying().declare("result").initializedByInvoking("parse")
                                        .withArgument("bag").withArgument("cancelled::get").on(nbParserName).as(parserResultType)
                                        .synchronizeOn("this")
                                        .statement("lastResult = result").endBlock()
                                        .endBlock().catching("Exception")
                                        .statement("Exceptions.printStackTrace(thrown)")
                                        .endBlock().endTryCatch().endBlock();
                            }).closeMethod();
                })
                .method("parse", mb -> {
                    mb.addArgument("ParsingBag", "bag").addArgument("BooleanSupplier", "cancelled").returning("AntlrParseResult")
                            .withModifier(PUBLIC).withModifier(STATIC)
                            .body(bb -> {
                                bb.declare("lexer").initializedByInvoking("createAntlrLexer").withArgument("bag.source().stream()")
                                        .on(prefix + "Hierarchy").as(lexerClass.toString());
                                bb.declare("stream").initializedWith("new CommonTokenStream(lexer, " + streamChannel + ")")
                                        .as("CommonTokenStream");
                                bb.declare("parser").initializedWith("new " + parserClass.toString() + "(stream)").as(parserClass.toString());
                                bb.blankLine();
                                bb.lineComment("Invoke the hook method allowing the helper to attach error listeners, or ");
                                bb.lineComment("configure the parser or lexer before using them.");
                                bb.declare("maybeSnapshot").initializedByInvoking("lookup").withArgument("Snapshot.class").onInvocationOf("source").on("bag").as("Optional<Snapshot>");
                                bb.blankLine();
                                bb.lineComment("The registered GrammarSource implementations will synthesize a Snapshot if the GrammarSource");
                                bb.lineComment("was not created from one (as happens in tests), if a Document or a FileObject is present.");
                                bb.lineComment("Most code touching extraction does not require a snapshot be present.  So this will only");
                                bb.lineComment("be null for a GrammarSource created from a String or CharStream.");
                                bb.declare("snapshot").initializedWith("maybeSnapshot.isPresent() ? maybeSnapshot.get() : null").as("Snapshot");

                                bb.declare("errors").initializedByInvoking("parserCreated").withArgument("lexer").withArgument("parser")
                                        .withArgument("snapshot")
                                        .on("HELPER").as("Supplier<List<? extends SyntaxError>>");
                                bb.blankLine();
                                bb.lineComment("Here we actually trigger the Antlr parse");
                                bb.declare("tree").initializedByInvoking(parserEntryPointMethod.getSimpleName().toString())
                                        .on("parser").as(eSimple);
                                bb.blankLine();
                                bb.lineComment("Look up the extrator(s) for this mime type");
                                bb.declare("extractor")
                                        .initializedByInvoking("forTypes").withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument(eSimple + ".class").on("Extractor")
                                        .as("Extractor<? super " + eSimple + ">");
                                bb.blankLine();
                                bb.lineComment("Run extraction, pulling out data needed to create navigator panels,");
                                bb.lineComment("code folds, etc.  Anything needing the extracted sets of regions and data");
                                bb.lineComment("can get it from the parse result");
                                bb.declare("extraction").initializedByInvoking("extract")
                                        .withArgument("tree")
                                        .withArgumentFromInvoking("source").on("bag")
                                        //.withArgument("bag.source()")
                                        .withLambdaArgument().body().returning("new CommonTokenStream(lexer)").endBlock()
                                        .on("extractor").as("Extraction");
                                bb.blankLine();
                                bb.lineComment("Now create a parser result and object to populate it, and allow the");
                                bb.lineComment("helper's hook method to add anything it needs, such as semantic error");
                                bb.lineComment("checking, or other data resolved from the extraction or parse tree - ");
                                bb.lineComment("this allows existing Antlr code to be used.");
                                bb.declare("result").initializedWith("new AntlrParseResult[1]").as("AntlrParseResult[]");
                                bb.declare("thrown").initializedWith("new Exception[1]").as("Exception[]");
                                bb.invoke("newParseResult").withArgument("snapshot")
                                        .withArgument("extraction").withLambdaArgument().withArgument("pr").withArgument("contents").body()
                                        .trying()
                                        .lineComment("Call the hook method to let the helper run any additional analysis,")
                                        .lineComment("semantic error checking, etc")
                                        .invoke("parseCompleted").withArgument("tree").withArgument("extraction")
                                        .withArgument("contents").withArgument("cancelled")
                                        .withArgument("errors")
                                        .on("HELPER")
                                        .statement("result[0] = pr").endBlock()
                                        .catching("Exception")
                                        .lineComment("The hook method may throw an exception, which we will need to catch")
                                        .statement("thrown[0] = ex")
                                        .endBlock().namingException("ex").endTryCatch().endBlock()
                                        .on(prefix + "Hierarchy");
                                bb.ifCondition().variable("thrown[0]").notEquals().literal("null").endCondition()
                                        .thenDo().statement("Exceptions.printStackTrace(thrown[0])").endBlock().endIf();
                                bb.returning("result[0]");
                                bb.endBlock();
                            }).throwing("Exception").closeMethod();
                })
                .method("getResult", (mb) -> {
                    mb.withModifier(PUBLIC)
                            .withModifier(SYNCHRONIZED)
                            .addArgument("Task", "task").override().returning(parserResultType)
                            .body().returning("lastResult").endBlock().closeMethod();
                })
                .method("addChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC).override()
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.addChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                }).closeMethod()
                .method("removeChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC).override()
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.removeChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                }).closeMethod();
        if (changeSupport) {
            cl.method("forceReparse").withModifier(PRIVATE).body().invoke("fireChange").on("changeSupport").endBlock().closeMethod()
                    .method("languageSettingsChanged").withModifier(PUBLIC).withModifier(STATIC)
                    .body(bb -> {
                        bb.lineComment("Cause all existing instances of this class to fire a change, triggering");
                        bb.lineComment("a re-parse, in the event of a global change of some sort.");
                        bb.simpleLoop(nbParserName, "parser").over("INSTANCES")
                                .invoke("forceReparse").on("parser").endBlock();
                        bb.endBlock();
                    }).closeMethod();
        }

        if (!hasExplicitHelperClass) {
            // <P extends Parser, L extends Lexer, R extends Result, T extends ParserRuleContext> {
            cl.innerClass(helperClassName).extending("NbParserHelper<" + parserClass + ", " + lexerClass
                    + ", AntlrParseResult, " + eSimple + ">")
                    .addModifier(PRIVATE).addModifier(FINAL).addModifier(STATIC).build();
        }

        /*
import org.netbeans.api.editor.mimelookup.MimeRegistration;
@MimeRegistration(mimeType = "text/x-g4", service = ParserFactory.class)
         */
        cl.importing("org.netbeans.api.editor.mimelookup.MimeRegistration")
                .innerClass(prefix + "ParserFactory").publicStaticFinal().extending("ParserFactory")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .annotatedWith("MimeRegistration").addArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                .addClassArgument("service", "ParserFactory").closeAnnotation()
                .method("createParser").override().withModifier(PUBLIC).addArgument("Collection<Snapshot>", "snapshots")
                .body().returning("new " + nbParserName + "()").endBlock().returning("Parser").closeMethod()
                .build();

        cl.importing("org.netbeans.modules.parsing.spi.TaskFactory",
                "org.nemesis.antlr.spi.language.NbAntlrUtils")
                .method("createErrorHighlighter", mb -> {
                    mb.withModifier(PUBLIC).withModifier(STATIC)
                            .annotateWith("MimeRegistration").addArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                            .addClassArgument("service", "TaskFactory").closeAnnotation()
                            .returning("TaskFactory")
                            .body(bb -> {
                                bb.returningInvocationOf("createErrorHighlightingTaskFactory")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .on("NbAntlrUtils").endBlock();
                            })
                            .closeMethod();
                });

        writeOne(cl);
    }

    // Lexer generation
    private String generateTokensClasses(Map<Integer, String> tokenNameForIndex, TypeElement type, AnnotationMirror mirror, TypeMirror lexerClass, TypeMirror tokenCategorizerClass) throws Exception {
        List<AnnotationMirror> categories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        String prefix = utils().annotationValue(mirror, "name", String.class);
        // Figure out if we are generating a token categorizer now, so we have the class name for it
        String tokenCatName = willHandleCategories(categories, tokenCategorizerClass, prefix, utils());
        String tokenTypeName = prefix + "Token";
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if ("<error>".equals(mimeType)) {
            utils().fail("Mime type invalid: " + mimeType);
            return null;
        }
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokensTypeName = prefix + "Tokens";
        // First class will be an interface that extends NetBeans' TokenId - we will implement it
        // as an inner class on our class with constants for each token type
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(prefix + "Token").toInterface().extending("Comparable<" + tokenTypeName + ">")
                .extending("TokenId")
                .docComment("Token interface for ", prefix, " extending the TokenId type from NetBeans' Lexer API.",
                        " Instances for types defined by the lexer " + lexerClass + " can be found as static fields on ",
                        tokensTypeName, " in this package (", pkg, "). ",
                        "Generated by ", getClass().getSimpleName(), " from fields on ", lexerClass, " specified by "
                        + "an annotation on ", type.getSimpleName())
                .makePublic()
                .importing("org.netbeans.api.lexer.TokenId")
                .importing("javax.annotation.processing.Generated")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(FINAL).withModifier(STATIC).withModifier(PUBLIC).withInitializer(LinesBuilder.stringLiteral(mimeType)).ofType("String")
                .method("ordinal").override().returning("int").closeMethod()
                .method("symbolicName").returning(STRING).closeMethod()
                .method("literalName").returning(STRING).closeMethod()
                .method("displayName").returning(STRING).closeMethod()
                .method("primaryCategory").override().returning(STRING).closeMethod()
                .method("compareTo").override().returning("int").addArgument(tokenTypeName, "other").withModifier(DEFAULT)
                .body().returning("ordinal() > other.ordinal() ? 1 : ordinal() == other.ordinal() ? 0 : -1").endBlock().closeMethod()
                .method("name").annotateWith("Override").closeAnnotation().returning("String").withModifier(DEFAULT)
                .body().returning("literalName() != null ? literalName() : symbolicName() != null ? symbolicName() : "
                        + "displayName() != null ? displayName() : Integer.toString(ordinal())").endBlock().closeMethod();

        String tokensImplName = prefix + "TokenImpl";

        // This class will contain constants for every token type, and methods
        // for looking them up by name, character, etc.
        ClassBuilder<String> toks = ClassBuilder.forPackage(pkg).named(tokensTypeName)
                .makePublic().makeFinal()
                .docComment("Provides all tokens for the  ", prefix,
                        "Generated by ", getClass().getSimpleName(), " from fields on ", lexerClass,
                        " from annotation on ", type.getSimpleName(), ": <pre>",
                        mirror.toString().replaceAll("@", "&#064;"), "</pre>")
                .importing("java.util.Arrays", "java.util.HashMap", "java.util.Map", "java.util.Optional",
                        "java.util.List", "java.util.ArrayList", "java.util.Collections",
                        "org.nemesis.antlr.spi.language.highlighting.TokenCategorizer",
                        "javax.annotation.processing.Generated")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                // Static categorizer field - fall back to a heuristic categorizer if nothing is specified
                .field("CATEGORIZER").withModifier(Modifier.FINAL).withModifier(Modifier.PRIVATE).withModifier(STATIC)
                .withInitializer(tokenCatName == null ? "TokenCategorizer.heuristicCategorizer()" : "new " + tokenCatName + "()")
                .ofType("TokenCategorizer")
                // Make it non-instantiable
                .constructor().addModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock().endConstructor();

        // Initialize arrays and maps of name -> token type for use by lookup methods
        // Some of this information we cannot collect at annotation processing time, because name arrays
        // are lazily initialized in the code generated by Antlr.  So loop over all tokens
        // and collect that at runtime here:
        ClassBuilder.BlockBuilder<ClassBuilder<String>> initMapsBlock = toks.staticBlock();
        initMapsBlock.declare("charsList").initializedWith("new ArrayList<>(ALL.length)").as("List<Character>");
        initMapsBlock.declare("tokForCharMap").initializedWith("new HashMap<>(ALL.length)").as("Map<Character, " + tokenTypeName + ">");
        initMapsBlock.simpleLoop(tokenTypeName, "tok")
                .over("ALL")
                .declare("symName").initializedByInvoking("symbolicName").on("tok").as(STRING)
                .declare("litName").initializedByInvoking("literalName").on("tok").as(STRING)
                .declare("litStripped").initializedWith("stripQuotes(litName)").as(STRING)
                .ifCondition().variable("symName").notEquals().literal("null").endCondition().thenDo()
                .invoke("put").withArgument("symName").withArgument("tok").on("bySymbolicName")
                .endBlock().endIf()
                .ifCondition().variable("litStripped").notEquals().literal("null").endCondition().thenDo()
                .invoke("put").withArgument("litStripped").withArgument("tok").on("byLiteralName")
                .ifCondition().invoke("length").on("litStripped").equals().literal(1).endCondition().thenDo()
                .invoke("put").withArgument("litStripped.charAt(0)").withArgument("tok").on("tokForCharMap")
                .invoke("add").withArgument("litStripped.charAt(0)").on("charsList")
                .endBlock().endIf()
                .endBlock()
                .endIf()
                .endBlock()
                .invoke("sort").withArgument("charsList").on("Collections")
                .statement("chars = new char[charsList.size()]")
                .statement("tokForChar = new " + tokenTypeName + "[charsList.size()]")
                .forVar("i").condition().lessThan().invoke("size").on("charsList").endCondition().running()
                .statement("chars[i] = charsList.get(i)")
                .statement("tokForChar[i] = tokForCharMap.get(charsList.get(i))")
                .endBlock()
                .endBlock();

        // Lookup for tokens which are a single character, look them up by character
        toks.method("forSymbol").addArgument("char", "ch").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body()
                .declare("ix").initializedWith("Arrays.binarySearch(chars, ch)").as("int")
                .ifCondition().variable("ix").greaterThanOrEqualto().literal(0).endCondition()
                .thenDo().returning("Optional.of(tokForChar[ix])").endBlock().endIf()
                .returning("Optional.empty()").endBlock().closeMethod();

        // Lookup by symbolic name method
        toks.method("forSymbolicName").addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(bySymbolicName.get(name))").endBlock().closeMethod();

        // Lookup by literal name method
        toks.method("forLiteralName").addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(byLiteralName.get(name))").endBlock().closeMethod();

        // Helper method - literal names in antlr generated code come surrounded in
        // single quotes - strip those
        toks.method("stripQuotes").addArgument("String", "name").returning("String")
                .withModifier(STATIC).withModifier(PRIVATE).body().ifCondition().variable("name").notEquals().literal("null").and().invoke("isEmpty").on("name").equals().literal("false").and()
                .invoke("charAt").withArgument("0").on("name").equals().literal('\'').and().invoke("charAt").withArgument("name.length()-1")
                .on("name").equals().literal('\'').endCondition().thenDo().returning("name.substring(1, name.length()-1)").endBlock().endIf()
                .returning("name").endBlock().closeMethod();

        // Our implementation of the TokenId sub-interface defined as the first class above,
        // implementing all of its methods
        toks.innerClass(tokensImplName).addModifier(FINAL).addModifier(STATIC).addModifier(PRIVATE)
                .docComment("Implementation of " + tokenTypeName + " implementing NetBeans' TokenId class from the Lexer API.")
                .field("id").withModifier(PRIVATE).withModifier(FINAL).ofType("int")
                .implementing(tokenTypeName).constructor().addArgument("int", "id").body()
                .statement("this.id = id").endBlock().endConstructor()
                .method("ordinal").annotateWith("Override").closeAnnotation().withModifier(PUBLIC).returning("int").body().returning("this.id").endBlock().closeMethod()
                .method("symbolicName").annotateWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning("stripQuotes(" + lexerClass + ".VOCABULARY.getSymbolicName(id))").endBlock().closeMethod()
                .method("literalName").annotateWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(lexerClass + ".VOCABULARY.getLiteralName(id)").endBlock().closeMethod()
                .method("displayName").annotateWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(lexerClass + ".VOCABULARY.getDisplayName(id)").endBlock().closeMethod()
                .method("primaryCategory").annotateWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning("CATEGORIZER.categoryFor(ordinal(), displayName(), symbolicName(), literalName())").endBlock().closeMethod()
                .method("toString").withModifier(PUBLIC).annotateWith("Override").closeAnnotation().returning("String").body().returning("ordinal() + \"/\" + displayName()").endBlock().closeMethod()
                .method("equals").withModifier(PUBLIC).annotateWith("Override").closeAnnotation().addArgument("Object", "o")
                .returning("boolean").body().returning("o == this || (o instanceof " + tokensImplName + " && ((" + tokensImplName + ") o).ordinal() == ordinal())").endBlock().closeMethod()
                .method("hashCode").withModifier(PUBLIC).annotateWith("Override").closeAnnotation().returning("int").body().returning("id * 51").endBlock().closeMethod()
                .build();

        // Now build a giant switch statement for lookup by id
        List<Integer> keys = new ArrayList<>(tokenNameForIndex.keySet());
        ClassBuilder.SwitchBuilder<ClassBuilder.BlockBuilder<ClassBuilder.MethodBuilder<ClassBuilder<String>>>> idSwitch
                = toks.method("forId").withModifier(PUBLIC).withModifier(STATIC).returning(tokenTypeName).addArgument("int", "id").body().switchingOn("id");

        toks.method("all").withModifier(STATIC).withModifier(PUBLIC)
                .returning(tokenTypeName + "[]").body()
                .returning("Arrays.copyOf(ALL, ALL.length)").endBlock().closeMethod();

        ClassBuilder.ArrayLiteralBuilder<ClassBuilder<String>> allArray = toks.field("ALL").withModifier(STATIC).withModifier(PRIVATE).withModifier(FINAL).assignedTo().toArrayLiteral(tokenTypeName);
        // Loop and build cases in the switch statement for each token id, plus EOF,
        // and also populate our static array field which contains all of the token types
        for (Integer k : keys) {
            String fieldName = "TOK_" + tokenNameForIndex.get(k);
            toks.field(fieldName).withModifier(STATIC).withModifier(PUBLIC).withModifier(FINAL)
                    .withInitializer("new " + tokensImplName + "(" + k + ")")
                    .ofType(tokenTypeName);
            idSwitch.inCase(k).returning(fieldName).endBlock();
            if (k != -1) {
                allArray.add(fieldName);
            }
        }
        idSwitch.inDefaultCase().statement("throw new IllegalArgumentException(\"No such id \" + id)").endBlock();
        idSwitch.build().endBlock().closeMethod();
        allArray.closeArrayLiteral();

        // Create our array and map fields here (so they are placed below the
        // constants in the source file - these are populated in the static block
        // defined above)
        toks.field("bySymbolicName").withInitializer("new HashMap<>()").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("Map<String, " + tokenTypeName + ">");
        toks.field("byLiteralName").withInitializer("new HashMap<>()").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("Map<String, " + tokenTypeName + ">");
        toks.field("chars").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType("char[]");
        toks.field("tokForChar").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).ofType(tokenTypeName + "[]");

        String adapterClassName = prefix + "LexerAdapter";

        // Build an implementation of LanguageHierarchy here
        String hierName = prefix + "Hierarchy";
        String hierFqn = pkg + "." + hierName;
        String languageMethodName = lc(prefix) + "Language";
        ClassBuilder<String> lh = ClassBuilder.forPackage(pkg).named(hierName)
                .importing("org.netbeans.api.lexer.Language", "org.netbeans.spi.lexer.LanguageHierarchy",
                        "org.netbeans.spi.lexer.Lexer", "org.netbeans.spi.lexer.LexerRestartInfo",
                        "java.util.Collection", "java.util.Arrays", "java.util.Collections",
                        "org.nemesis.antlr.spi.language.NbAntlrUtils", "org.netbeans.spi.lexer.Lexer",
                        "org.antlr.v4.runtime.CharStream", "org.nemesis.antlr.spi.language.NbLexerAdapter",
                        "java.util.Map", "java.util.HashMap", "java.util.ArrayList", "org.antlr.v4.runtime.CharStream",
                        "java.util.function.BiConsumer", "org.nemesis.antlr.spi.language.AntlrParseResult.ParseResultContents",
                        "org.netbeans.modules.parsing.api.Snapshot", "org.nemesis.extraction.Extraction",
                        "org.nemesis.antlr.spi.language.AntlrParseResult", "org.netbeans.api.editor.mimelookup.MimeRegistration",
                        "org.antlr.v4.runtime.Vocabulary"
                )
                .generateDebugLogCode()
                .docComment("LanguageHierarchy implementation for ", prefix,
                        ". Generated by ", getClass().getSimpleName(), " from fields on ", lexerClass, ".")
                .importing("javax.annotation.processing.Generated")
                .makePublic().makeFinal()
                .constructor().addModifier(PUBLIC).body().debugLog("Create a new " + hierName).endBlock().endConstructor()
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .extending("LanguageHierarchy<" + tokenTypeName + ">")
                .field("LANGUAGE").withModifier(FINAL).withModifier(STATIC).withModifier(PRIVATE)
                /*.withInitializer("new " + hierName + "().language()").*/.ofType("Language<" + tokenTypeName + ">")
                .field("CATEGORIES").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Map<String, Collection<" + tokenTypeName + ">>")
                .staticBlock(bb -> {
                    bb.declare("map").initializedWith("new HashMap<>()").as("Map<String, Collection<" + tokenTypeName + ">>")
                            .lineComment("Assign fields here to guarantee initialization order")
                            .statement("IDS = Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()));")
                            .simpleLoop(tokenTypeName, "tok").over("IDS")
                            .ifCondition().invoke("ordinal").on("tok").equals().literal(-1).endCondition()
                            .thenDo()
                            .lineComment("Antlr has a token type -1 for EOF; editor's cache cannot handle negative ordinals")
                            .statement("continue").endBlock().endIf()
                            .declare("curr").initializedByInvoking("get").withArgument("tok.primaryCategory()").on("map").as("Collection<" + tokenTypeName + ">")
                            .ifCondition().variable("curr").equals().literal("null").endCondition()
                            .thenDo().statement("curr = new ArrayList<>()")
                            .invoke("put").withArgument("tok.primaryCategory()").withArgument("curr").on("map").endBlock()
                            .endIf()
                            .invoke("add").withArgument("tok").on("curr")
                            .endBlock()
                            .statement("CATEGORIES = Collections.unmodifiableMap(map)")
                            .statement("LANGUAGE = new " + hierName + "().language()")
                            .endBlock();
                })
                .field("IDS").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Collection<" + tokenTypeName + ">")
                //                .withInitializer("Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()))").ofType("Collection<" + tokenTypeName + ">")
                .method("mimeType")
                .returning(STRING)
                .annotateWith("Override").closeAnnotation()
                .withModifier(PROTECTED).withModifier(FINAL).body()
                .returning(tokenTypeName + ".MIME_TYPE").endBlock().closeMethod()
                .method(languageMethodName).withModifier(PUBLIC).withModifier(STATIC).returning("Language<" + tokenTypeName + ">")
                //                .annotateWith("MimeRegistration", ab -> {
                //                    ab.addStringArgument("mimeType", mimeType)
                //                            .addClassArgument("service", "Language")
                //                            .addArgument("position", 500).closeAnnotation();
                //                })
                .body().returning("LANGUAGE").endBlock().closeMethod()
                .method("createTokenIds").returning("Collection<" + tokenTypeName + ">").override().withModifier(PROTECTED).withModifier(FINAL).body().returning("IDS").endBlock().closeMethod()
                .method("createLexer").override().returning("Lexer<" + tokenTypeName + ">").addArgument("LexerRestartInfo<" + tokenTypeName + ">", "info").withModifier(PROTECTED).withModifier(FINAL)
                .body(bb -> {
                    bb.debugLog("Create a new " + prefix + " Lexer");
                    bb.returning("NbAntlrUtils.createLexer(info, LEXER_ADAPTER)").endBlock();
                }).closeMethod()
                .method("isRetainTokenText").override().withModifier(PROTECTED).withModifier(FINAL).returning("boolean").addArgument(tokenTypeName, "tok")
                .body().returning("tok.literalName() == null").endBlock().closeMethod()
                .method("createTokenCategories").override().withModifier(PROTECTED).withModifier(FINAL)
                .returning("Map<String, Collection<" + tokenTypeName + ">>").body().returning("CATEGORIES").endBlock().closeMethod()
                .method("createAntlrLexer").addArgument("CharStream", "stream")
                .body().returning("LEXER_ADAPTER.createLexer(stream)").endBlock().returning(lexerClass.toString()).withModifier(PUBLIC).withModifier(STATIC).closeMethod()
                .method("newParseResult", mth -> {
                    mth.withModifier(STATIC)
                            .addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .body(bb -> {
                                bb.debugLog("new " + prefix + " parse result");
                                bb.log("New {0} parse result: {1}", Level.WARNING, LinesBuilder.stringLiteral(prefix), "extraction");
                                bb.invoke("newParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("LEXER_ADAPTER").endBlock();
                            })
                            .closeMethod();
                });

        // Inner implementation of NbLexerAdapter, which allows us to use a generic
        // Lexer class and takes care of creating the Antlr lexer and calling methods
        // that will exist on the lexer implementation class but not on the parent class
        lh.innerClass(adapterClassName).addModifier(PRIVATE).addModifier(STATIC).addModifier(FINAL)
                .extending("NbLexerAdapter<" + tokenTypeName + ", " + lexerClass + ">")
                .method("createLexer").override().withModifier(PUBLIC).returning(lexerClass.toString()).addArgument("CharStream", "stream")
                .body(bb -> {
                    bb.declare("result").initializedWith("new " + lexerClass + "(stream)").as(lexerClass.toString());
                    bb.invoke("removeErrorListeners").on("result");
                    bb.returning("result").endBlock();
                })
                //                .body().returning("new " + lexerClass + "(stream)").endBlock().withModifier(PUBLIC)
                .closeMethod()
                .override("vocabulary").withModifier(PROTECTED).returning("Vocabulary")
                .body().returning(lexerClass + ".VOCABULARY").endBlock().closeMethod()
                .method("tokenId").override().addArgument("int", "ordinal").returning(tokenTypeName)
                .body().returning(tokensTypeName + ".forId(ordinal)").endBlock().withModifier(PUBLIC).closeMethod()
                .method("setInitialStackedModeNumber").override().addArgument(lexerClass.toString(), "lexer")
                .addArgument("int", "modeNumber").body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .statement("lexer.setInitialStackedModeNumber(modeNumber)").endBlock().withModifier(PUBLIC).closeMethod()
                .method("getInitialStackedModeNumber").override().withModifier(PUBLIC).returning("int").addArgument(lexerClass.toString(), "lexer")
                .body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .returning("lexer.getInitialStackedModeNumber()").endBlock().withModifier(PUBLIC).closeMethod()
                // createParseResult(Snapshot snapshot, Extraction extraction, BiConsumer<AntlrParseResult, ParseResultContents> receiver)
                .method("newParseResult", mth -> {
                    mth.addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .body(bb -> {
                                bb.invoke("createParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("super").endBlock();
                            })
                            .closeMethod();
                })
                .build();

        lh.field("LEXER_ADAPTER").withInitializer("new " + adapterClassName + "()").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .ofType(adapterClassName);
        //.ofType("NbLexerAdapter<" + tokenTypeName + ", " + lexerClass + ">");

        // Save generated source files to disk
        writeOne(cb);
        writeOne(toks);
        writeOne(lh);

        if (categories != null) {
            handleCategories(categories, tokenNameForIndex, type, mirror, lexerClass, tokenCategorizerClass, tokenCatName, pkg);
        }

        String bundle = utils().annotationValue(mirror, "localizingBundle", String.class);
        addLayerTask(lb -> {
            // For some reason, the generated @MimeRegistration annotation is resulting
            // in an attempt to load a *class* named with the fqn + method name.
            // So, do it the old fashioned way
            String layerFileName = hierFqn.replace('.', '-');
            LayerBuilder.File reg = lb.file("Editors/" + mimeType + "/" + layerFileName + ".instance")
                    //            LayerBuilder.File reg = lb.instanceFile("Editors/" + mimeType, layerFileName)
                    .methodvalue("instanceCreate", hierFqn, languageMethodName)
                    .stringvalue("instanceOf", "org.netbeans.api.lexer.Language")
                    .intvalue("position", 1000);

            if (bundle != null) {
                reg.stringvalue("SystemFilesystem.localizingBundle", bundle);
            }
            reg.write();
        }, type);
        return prefix;
    }

    static String willHandleCategories(List<AnnotationMirror> categories, TypeMirror specifiedClass, String prefix, AnnotationUtils utils) {
        if (categories == null || categories.isEmpty() || (categories.size() == 1 && "_".equals(utils.annotationValue(categories.get(0), "name", String.class)))) {
            return specifiedClass == null ? null : specifiedClass.toString();
        }
        return prefix + "TokenCategorizer";
    }

    private void handleCategories(List<AnnotationMirror> categories, Map<Integer, String> tokenNameForIndex, TypeElement type, AnnotationMirror mirror, TypeMirror lexerClass, TypeMirror tokenCategorizerClass, String catName, Name pkg) throws Exception {
        // Create an implementation of TokenCategorizer based on the annotation values
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(catName).addModifier(FINAL)
                .importing("org.nemesis.antlr.spi.language.highlighting.TokenCategorizer")
                .implementing("TokenCategorizer");

        // categoryFor(ordinal(), displayName(), symbolicName(), literalName())
        ClassBuilder.BlockBuilder<ClassBuilder.MethodBuilder<ClassBuilder<String>>> meth = cb.method("categoryFor").withModifier(PUBLIC)
                .addArgument("int", "id")
                .addArgument(STRING, "displayName").addArgument(STRING, "symbolicName")
                .addArgument(STRING, "literalName").returning(STRING)
                .body();
        if (tokenCategorizerClass != null) {
            cb.field("DEFAULT_CATEGORIZER").withInitializer("new " + tokenCategorizerClass + "()").ofType("TokenCategorizer");
            meth.declare("result").initializedByInvoking("categoryFor")
                    .withArgument("id")
                    .withArgument("displayName")
                    .withArgument("symbolicName")
                    .withArgument("literalName")
                    .on("DEFAULT_CATEGORIZER");
            meth.ifCondition().variable("result").notEquals().literal("null").endCondition().thenDo().returning("result").endBlock().endIf();
        }
        ClassBuilder.SwitchBuilder<ClassBuilder.BlockBuilder<ClassBuilder.MethodBuilder<ClassBuilder<String>>>> sw = meth
                .switchingOn("id");

        Set<Integer> dups = new HashSet<>();
        for (AnnotationMirror cat : categories) {
            String name = utils().annotationValue(cat, "name", String.class);
            List<Integer> values = utils().annotationValues(cat, "tokenIds", Integer.class);
            for (Integer val : values) {
                if (dups.contains(val)) {
                    utils().fail("More than one token category specifies the token type " + val
                            + " - " + tokenNameForIndex.get(val), type);
                    return;
                }
                sw.inCase(val)
                        .lineComment("Token " + tokenNameForIndex.get(val))
                        .returning(LinesBuilder.stringLiteral(name)).endBlock();
                dups.add(val);
            }
        }
        sw.inDefaultCase().returningStringLiteral("other").endBlock();

        ClassBuilder.BlockBuilder<ClassBuilder.MethodBuilder<ClassBuilder<String>>> n = sw.build();

        Set<Integer> unhandled = new TreeSet<>(tokenNameForIndex.keySet());
        unhandled.remove(-1);
        unhandled.removeAll(dups);
        if (!unhandled.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following tokens have no category:\n");
            int ix = 0;
            for (Iterator<Integer> it = unhandled.iterator(); it.hasNext();) {
                Integer unh = it.next();
                String name = tokenNameForIndex.get(unh);
                sb.append(name).append('(').append(unh).append(')');
                if (it.hasNext()) {
                    if (++ix % 3 == 0) {
                        sb.append(",\n");
                    } else {
                        sb.append(", ");
                    }
                }
            }
            n.lineComment(sb.toString());
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, sb, type, mirror);
        }

        ClassBuilder.MethodBuilder<ClassBuilder<String>> m = n.endBlock();
        m.closeMethod();

        writeOne(cb);

    }
    private static final String STRING = "String";

    private static String lc(String s) {
        char[] c = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private static final String DEFAULT_ACTIONS[] = {
        "-System/org.openide.actions.OpenAction",
        "Edit/org.openide.actions.CutAction",
        "Edit/org.openide.actions.CopyAction",
        "Edit/org.openide.actions.DeleteAction",
        "-System/org.openide.actions.RenameAction",
        "-System/org.openide.actions.SaveAsTemplateAction",
        "System/org.openide.actions.FileSystemAction",
        "System/org.openide.actions.ToolsAction",
        "System/org.openide.actions.PropertiesAction"

    };
}

package org.nemesis.registration;

import java.io.ByteArrayOutputStream;
import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import org.nemesis.registration.utils.AnnotationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import static org.nemesis.registration.FoldRegistrationAnnotationProcessor.versionString;
import static org.nemesis.registration.GotoDeclarationProcessor.DECLARATION_TOKEN_PROCESSOR_TYPE;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.LanguageFontsColorsProcessor.GROUP_SEMANTIC_HIGHLIGHTING_ANNO;
import static org.nemesis.registration.LanguageFontsColorsProcessor.SEMANTIC_HIGHLIGHTING_ANNO;
import org.nemesis.registration.api.Delegates;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.codegen.ClassBuilder.BlockBuilder;
import org.nemesis.registration.codegen.LinesBuilder;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import static org.nemesis.registration.utils.AnnotationUtils.simpleName;
import org.nemesis.registration.utils.StringUtils;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import org.nemesis.registration.codegen.ClassBuilder.SwitchBuilder;
import static org.nemesis.registration.utils.AnnotationUtils.capitalize;
import static org.nemesis.registration.utils.AnnotationUtils.stripMimeType;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({REGISTRATION_ANNO, SEMANTIC_HIGHLIGHTING_ANNO, GROUP_SEMANTIC_HIGHLIGHTING_ANNO, GOTO_ANNOTATION})
@SupportedOptions(AU_LOG)
public class LanguageRegistrationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    public static final String REGISTRATION_ANNO = "org.nemesis.antlr.spi.language.AntlrLanguageRegistration";

    public static final int DEFAULT_MODE = 0;
    public static final int MORE = -2;
    public static final int SKIP = -3;
    public static final int DEFAULT_TOKEN_CHANNEL = 0;
    public static final int HIDDEN = 1;
    public static final int MIN_CHAR_VALUE = 0;
    public static final int MAX_CHAR_VALUE = 1114111;

    private static final Map<String, Integer> lexerClassConstants() {
        Map<String, Integer> result = new HashMap<>();
        result.put("DEFAULT_MODE", 0);
        result.put("MORE", -2);
        result.put("SKIP", -3);
        result.put("DEFAULT_TOKEN_CHANNEL", 0);
        result.put("HIDDEN", 1);
        result.put("MIN_CHAR_VALUE", 0);
        result.put("MAX_CHAR_VALUE", 1114111);
        return result;
    }

    @Override
    protected void installDelegates(Delegates delegates) {
        LanguageFontsColorsProcessor proc = new LanguageFontsColorsProcessor();
        HighlighterKeyRegistrationProcessor hkProc = new HighlighterKeyRegistrationProcessor();
        delegates
                .apply(proc).to(ElementKind.CLASS, ElementKind.INTERFACE)
                .onAnnotationTypesAnd(REGISTRATION_ANNO)
                .to(ElementKind.FIELD).whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(proc).to(ElementKind.CLASS, ElementKind.INTERFACE)
                .onAnnotationTypesAnd(REGISTRATION_ANNO)
                .to(ElementKind.FIELD).whenAnnotationTypes(GROUP_SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(hkProc)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(SEMANTIC_HIGHLIGHTING_ANNO)
                .apply(hkProc)
                .to(ElementKind.FIELD)
                .whenAnnotationTypes(GROUP_SEMANTIC_HIGHLIGHTING_ANNO);
    }

    Predicate<AnnotationMirror> mirrorTest;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        mirrorTest = utils.testMirror().testMember("name").stringValueMustNotBeEmpty()
                .stringValueMustBeValidJavaIdentifier().build()
                .testMember("mimeType").validateStringValueAsMimeType().build().build();
    }

    private final Map<String, Set<VariableElement>> gotos = new HashMap<>();

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (GOTO_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            String mime = utils().annotationValue(mirror, "mimeType", String.class);
            if (mime != null) {
                Set<VariableElement> ves = gotos.get(mime);
                if (ves == null) {
                    ves = new HashSet<>();
                    gotos.put(mime, ves);
                }
                ves.add(var);
            }
            return false;
        }
        return true;
    }

    private boolean useDeclarationTokenProcessor(String mimeType) {
        return gotos.containsKey(mimeType);
    }

    private void preemptivelyCheckGotos(RoundEnvironment env) throws Exception {
        for (Element e : utils().findAnnotatedElements(env, GOTO_ANNOTATION)) {
            if (e instanceof VariableElement) {
                AnnotationMirror am = utils().findAnnotationMirror(e, GOTO_ANNOTATION);
                if (am != null) {
                    processFieldAnnotation((VariableElement) e, am, env);
                }
            }
        }
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (!mirrorTest.test(mirror)) {
            return true;
        }
        preemptivelyCheckGotos(roundEnv);
        String prefix = utils().annotationValue(mirror, "name", String.class);
        LexerProxy lexerProxy = createLexerProxy(mirror, type);
        if (lexerProxy == null) {
            return true;
        }
        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");

        AnnotationMirror parserHelper = utils().annotationValue(mirror, "parser", AnnotationMirror.class);
        ParserProxy parser = null;
        if (parserHelper != null) {
            parser = createParserProxy(parserHelper);
            if (parser == null) {
                return true;
            }
            generateParserClasses(parser, lexerProxy, type, mirror, parserHelper, prefix);
        }

        generateTokensClasses(type, mirror, lexerProxy, tokenCategorizerClass, prefix);

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
                generateDataObjectClassAndRegistration(fileInfo, extension, mirror, prefix, type, parser, lexerProxy);
            }
        }
        // Returning true could stop LanguageFontsColorsProcessor from being run
        return false;
    }

    private LexerProxy createLexerProxy(AnnotationMirror mirror, Element target) {
        TypeMirror lexerClass = utils().typeForSingleClassAnnotationMember(mirror, "lexer");
        if (mirror == null) {
            utils().fail("Could not locate lexer class on classpath", target);
            return null;
        }
        TypeElement lexerClassElement = processingEnv.getElementUtils().getTypeElement(lexerClass.toString());
        if (lexerClassElement == null) {
            utils().fail("Could not resolve a TypeElement for " + lexerClass, target);
            return null;
        }
        Map<Integer, String> tokenNameForIndex = new HashMap<>();
        Map<String, Integer> indexForTokenName = new HashMap<>();

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
                    indexForTokenName.put(nm, val);
                    last = val;
                }
            }
        }
        tokenNameForIndex.put(-1, "EOF");
        tokenNameForIndex.put(last + 1, "$ERRONEOUS");
        return new LexerProxy(lexerClass, lexerClassElement, tokenNameForIndex, indexForTokenName, last);
    }

    private static class LexerProxy {

        private final TypeMirror lexerClass;
        private final TypeElement lexerClassElement;
        private final Map<Integer, String> tokenNameForIndex;
        private final Map<String, Integer> indexForTokenName;
        private final int last;

        private LexerProxy(TypeMirror lexerClass, TypeElement lexerClassElement, Map<Integer, String> tokenNameForIndex, Map<String, Integer> indexForTokenName, int last) {
            this.lexerClass = lexerClass;
            this.lexerClassElement = lexerClassElement;
            this.tokenNameForIndex = tokenNameForIndex;
            this.indexForTokenName = indexForTokenName;
            this.last = last;
        }

        public Set<String> tokenNames() {
            return new TreeSet<>(indexForTokenName.keySet());
        }

        public Set<Integer> tokenTypes() {
            return new TreeSet<>(tokenNameForIndex.keySet());
        }

        public String tokenName(int type) {
            return tokenNameForIndex.get(type);
        }

        public Integer tokenIndex(String name) {
            return indexForTokenName.get(name);
        }

        public String lexerClassFqn() {
            return lexerClass.toString();
        }

        public int erroneousTokenId() {
            return last + 1;
        }

        public int maxToken() {
            return last;
        }

        String lcs;

        public String lexerClassSimple() {
            return lcs == null ? lcs = simpleName(lexerClassFqn()) : lcs;
        }

        public TypeMirror lexerType() {
            return lexerClass;
        }

        public TypeElement lexerClass() {
            return lexerClassElement;
        }

        public int lastDeclaredTokenType() {
            return last;
        }

        public boolean isSynthetic(int tokenType) {
            return tokenType < 0 || tokenType > last;
        }

        public String toFieldName(String tokenName) {
            return "TOK_" + tokenName.toUpperCase();
        }

        public String toFieldName(int tokenId) {
            String tokenName = tokenName(tokenId);
            return tokenName == null ? null : toFieldName(tokenName);
        }

        public List<Integer> allTypesSorted() {
            List<Integer> result = new ArrayList<>(tokenNameForIndex.keySet());
            Collections.sort(result);
            return result;
        }

        public List<Integer> allTypesSortedByName() {
            List<Integer> result = new ArrayList<>(tokenNameForIndex.keySet());
            Collections.sort(result, (a, b) -> {
                String an = tokenName(a);
                String bn = tokenName(b);
                return an.compareToIgnoreCase(bn);
            });
            return result;
        }

        private boolean typeExists(Integer val) {
            if (val == null) {
                throw new IllegalArgumentException("Null value");
            }
            return tokenNameForIndex.containsKey(val);
        }
    }

    private ParserProxy createParserProxy(AnnotationMirror parserHelper) {
        Map<Integer, String> ruleIdForName = new HashMap<>();
        Map<String, Integer> nameForRuleId = new HashMap<>();
        ExecutableElement parserEntryPointMethod = null;

        int entryPointRuleId = utils().annotationValue(parserHelper, "entryPointRule", Integer.class, Integer.MIN_VALUE);
        TypeMirror parserClass = utils().typeForSingleClassAnnotationMember(parserHelper, "type");
        TypeElement parserClassElement = parserClass == null ? null
                : processingEnv.getElementUtils().getTypeElement(parserClass.toString());
        Map<String, ExecutableElement> methodsForNames = new HashMap<>();
        if (parserClassElement != null) {
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
                        if (val == entryPointRuleId) {
                            entryPointRule = ruleName;
                        }
                    }
                } else if (entryPointRule != null && el instanceof ExecutableElement) {
                    Name name = el.getSimpleName();
                    if (name != null && name.contentEquals(entryPointRule)) {
                        parserEntryPointMethod = (ExecutableElement) el;
                    }
                    methodsForNames.put(name.toString(), (ExecutableElement) el);
                } else if (el instanceof ExecutableElement) {
                    Name name = el.getSimpleName();
                    methodsForNames.put(name.toString(), (ExecutableElement) el);
                }
            }
        }
        if (parserEntryPointMethod == null) {
            utils().fail("Could not find entry point method for rule id " + entryPointRuleId + " in " + parserClass);
            return null;
        }
        return new ParserProxy(ruleIdForName, nameForRuleId, parserEntryPointMethod, parserClassElement, parserClass, methodsForNames, entryPointRuleId);
    }

    private static class ParserProxy {

        private final Map<Integer, String> ruleIdForName;
        private final Map<String, Integer> nameForRuleId;
        private final ExecutableElement parserEntryPointMethod;
        private final TypeElement parserClassElement;
        private final TypeMirror parserClass;
        private final Map<String, ExecutableElement> methodsForNames;
        private final int entryPointRuleNumber;

        static String typeName(TypeMirror mirror) {
            String result = mirror.toString();
            if (result.startsWith("()")) {
                return result.substring(2);
            }
            return result;
        }

        public ExecutableElement parserEntryPoint() {
            return parserEntryPointMethod;
        }

        public TypeMirror parserEntryPointReturnType() {
            return parserEntryPointMethod.getReturnType();
        }

        public String parserEntryPointReturnTypeFqn() {
            return typeName(parserEntryPointReturnType());
        }

        public String parserEntryPointReturnTypeSimple() {
            return simpleName(parserEntryPointReturnType().toString());
        }

        public int entryPointRuleId() {
            return entryPointRuleNumber;
        }

        public Integer ruleId(String name) {
            return nameForRuleId.get(name);
        }

        public String nameForRule(int ruleId) {
            return ruleIdForName.get(ruleId);
        }

        public TypeMirror parserClass() {
            return parserClass;
        }

        public TypeElement parserType() {
            return parserClassElement;
        }

        public String parserClassFqn() {
            return typeName(parserClass());
        }

        public String parserClassSimple() {
            return simpleName(parserClassFqn());
        }

        public ExecutableElement method(String name) {
            return methodsForNames.get(name);
        }

        public ExecutableElement methodForId(int id) {
            String name = ruleIdForName.get(id);
            return name == null ? null : methodsForNames.get(name);
        }

        public TypeMirror ruleTypeForId(int ix) {
            ExecutableElement el = methodForId(ix);
            return el == null ? null : el.getReturnType();
        }

        public TypeMirror ruleTypeForRuleName(String ruleName) {
            Integer i = ruleId(ruleName);
            return i == null ? null : ruleTypeForId(i);
        }

        private ParserProxy(Map<Integer, String> ruleIdForName,
                Map<String, Integer> nameForRuleId,
                ExecutableElement parserEntryPointMethod,
                TypeElement parserClassElement,
                TypeMirror parserClass,
                Map<String, ExecutableElement> methodsForNames,
                int entryPointRuleNumber) {
            this.ruleIdForName = ruleIdForName;
            this.nameForRuleId = nameForRuleId;
            this.parserEntryPointMethod = parserEntryPointMethod;
            this.parserClassElement = parserClassElement;
            this.parserClass = parserClass;
            this.methodsForNames = methodsForNames;
            this.entryPointRuleNumber = entryPointRuleNumber;
        }

    }

    // DataObject generation
    private void generateDataObjectClassAndRegistration(AnnotationMirror fileInfo, String extension, AnnotationMirror mirror, String prefix, TypeElement type, ParserProxy parser, LexerProxy lexer) throws Exception {
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
        String dataObjectFqn = dataObjectPackage + "." + dataObjectClassName;
        String actionPath = "Loaders/" + mimeType + "/Actions";
        boolean multiview = utils().annotationValue(fileInfo, "multiview", Boolean.class, false);
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(dataObjectClassName)
                .importing("org.openide.awt.ActionID", "org.openide.awt.ActionReference",
                        "org.openide.awt.ActionReferences", "org.openide.filesystems.FileObject",
                        "org.openide.filesystems.MIMEResolver", "org.openide.loaders.DataObject",
                        "org.openide.loaders.DataObjectExistsException", "org.openide.loaders.MultiDataObject",
                        "org.openide.loaders.MultiFileLoader", "org.openide.util.Lookup",
                        "org.openide.util.NbBundle.Messages", "javax.annotation.processing.Generated"
                ).staticImport(dataObjectFqn + ".ACTION_PATH")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .extending("MultiDataObject")
                .withModifier(PUBLIC).withModifier(FINAL)
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
        cl.constructor().setModifier(PUBLIC)
                .addArgument("FileObject", "pf")
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
                });
        cl.override("associateLookup").returning("int").withModifier(PROTECTED).body().returning("1").endBlock();

        if (multiview) {
            cl.method("createEditor").addArgument("Lookup", "lkp").withModifier(PUBLIC).withModifier(STATIC)
                    .annotatedWith("MultiViewElement.Registration", ab -> {
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
                    .returning("new MultiViewEditorElement(lkp)").endBlock();
        }

        for (String s : new String[]{"copyAllowed", "renameAllowed", "moveAllowed", "deleteAllowed"}) {
            Boolean val = utils().annotationValue(fileInfo, s, Boolean.class);
            if (val != null) {
                createFoMethod(cl, s, val);
            }
        }
        String editorKitFqn = generateEditorKitClassAndRegistration(dataObjectPackage, mirror, prefix, type, mimeType, parser, lexer);
        cl.importing(EDITOR_KIT_TYPE)
                .method("createEditorKit", mb -> {
                    mb.returning("EditorKit");
                    mb.addArgument("FileObject", "file")
                            .withModifier(STATIC)
                            .body(bb -> {
                                bb.log(Level.FINER)
                                        .argument("file.getPath()")
                                        .stringLiteral(simpleName(editorKitFqn))
                                        .logging("Fetch editor kit {1} for {0}");
                                bb.returning(simpleName(editorKitFqn) + ".INSTANCE").endBlock();
                            });
                });

        LayerBuilder layer = layer(type);

        String editorKitFile = "Editors/" + mimeType + "/" + editorKitFqn.replace('.', '-') + ".instance";
        layer(type).file(editorKitFile)
                .methodvalue("instanceCreate", dataObjectFqn, "createEditorKit")
                .stringvalue("instanceOf", EDITOR_KIT_TYPE)
                .write();

        layer.folder("Actions/" + AnnotationUtils.stripMimeType(mimeType))
                .stringvalue("displayName", prefix).write();
        writeOne(cl);
    }

    private static final String EDITOR_KIT_TYPE = "javax.swing.text.EditorKit";
    private static final String NB_EDITOR_KIT_TYPE = "org.netbeans.modules.editor.NbEditorKit";

    private String generateEditorKitClassAndRegistration(String dataObjectPackage, AnnotationMirror registrationAnno, String prefix, TypeElement type, String mimeType, ParserProxy parser, LexerProxy lexer) throws Exception {
        String editorKitName = prefix + "EditorKit";
        String syntaxName = generateSyntaxSupport(mimeType, type, dataObjectPackage, registrationAnno, prefix, lexer);
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage)
                .named(editorKitName)
                .withModifier(FINAL)
                .importing("javax.annotation.processing.Generated",
                        NB_EDITOR_KIT_TYPE,
                        EDITOR_KIT_TYPE, "org.openide.filesystems.FileObject")
                .extending(simpleName(NB_EDITOR_KIT_TYPE))
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .initializedWithStringLiteral(mimeType)
                .field("INSTANCE").withModifier(STATIC).withModifier(FINAL)
                .withInitializer("new " + editorKitName + "()").ofType("EditorKit")
                .constructor().setModifier(PRIVATE).emptyBody()
                .override("getContentType").withModifier(PUBLIC).returning(STRING).body().returning("MIME_TYPE").endBlock();
        if (syntaxName != null) {
            cl.importing("org.netbeans.editor.SyntaxSupport", "org.netbeans.editor.BaseDocument")
                    .override("createSyntaxSupport").returning("SyntaxSupport")
                    .withModifier(PUBLIC).addArgument("BaseDocument", "doc")
                    .body(bb -> {
                        bb.log(Level.FINEST).argument("doc").logging(
                                "Create ExtSyntax " + syntaxName + " for {0}");
                        bb.returning("new " + syntaxName + "(doc)").endBlock();
                    });
        }

        String lineComment = utils().annotationValue(registrationAnno, "lineCommentPrefix", String.class);

        List<String> actionTypes = new ArrayList<>();
        if (lineComment != null) {
            actionTypes.add("ToggleCommentAction");
        }
        if (gotos.containsKey(mimeType)) {
//            actionTypes.add("org.netbeans.modules.editor.actions.GotoAction");
//            actionTypes.add("GotoDeclarationAction");
            String gotoDeclarationActionClassName = capitalize(stripMimeType(mimeType)) + "GotoDeclarationAction";
            actionTypes.add(gotoDeclarationActionClassName);
        }

        if (!actionTypes.isEmpty()) {
            cl.importing("javax.swing.Action", "javax.swing.text.TextAction");
            for (String actionType : actionTypes) {
                if (actionType.indexOf('.') > 0) {
                    cl.importing(actionType);
                }
            }
            cl.overrideProtected("createActions")
                    .returning("Action[]")
                    .body(bb -> {
                        bb.log(Level.FINEST)
                                .stringLiteral(lineComment)
                                .logging("Return actions enhanced with ToggleCommentAction for ''{0}''");

                        bb.declare("additionalActions").initializedAsNewArray("Action", alb -> {
                            for (String tp : actionTypes) {
                                if ("ToggleCommentAction".equals(tp)) {
                                    alb.add("new " + simpleName(tp) + "(" + LinesBuilder.stringLiteral(lineComment) + ")");
                                } else {
                                    alb.add("new " + /*simpleName(tp)*/ tp + "()");
                                }
                            }
                            alb.closeArrayLiteral();
                        });
                        /*
        ArrayList<Action> all = new ArrayList<>(MimeLookup.getLookup( MIME_TYPE).lookupAll( Action.class));
        all.addAll(Arrays.asList(additionalActions));
        
        return TextAction.augmentList(super.createActions(), all.toArray(new Action[0]));

                         */
                        cl.importing("java.util.Arrays", "java.util.ArrayList",
                                "org.netbeans.api.editor.mimelookup.MimeLookup",
                                "java.util.List");

                        bb.declare("all").initializedWith("new ArrayList<>(MimeLookup.getLookup( MIME_TYPE).lookupAll( Action.class))")
                                .as("List<Action>");
                        bb.invoke("addAll").withArgument("Arrays.asList(additionalActions)").on("all");

                        bb.returningInvocationOf("augmentList").withArgumentFromInvoking("createActions")
                                .on("super").withArgument("all.toArray(new Action[all.size()])").on("TextAction")
                                .endBlock();
//                        bb.returningInvocationOf("augmentList").withArgumentFromInvoking("createActions")
//                                .on("super").withArgument("additionalActions").on("TextAction")
//                                .endBlock();
                    });
        }

        writeOne(cl);
        return dataObjectPackage + "." + editorKitName;
    }

    private boolean hasSyntax(AnnotationMirror registrationAnno) {
        AnnotationMirror syntax = utils().annotationValue(registrationAnno, "syntax", AnnotationMirror.class);
        if (syntax == null) {
            return false;
        }
        List<Integer> commentTokens = utils().annotationValues(syntax, "commentTokens", Integer.class);
        List<Integer> whitespaceTokens = utils().annotationValues(syntax, "whitespaceTokens", Integer.class);
        List<Integer> bracketSkipTokens = utils().annotationValues(syntax, "bracketSkipTokens", Integer.class);
        return !commentTokens.isEmpty() || !whitespaceTokens.isEmpty() || !bracketSkipTokens.isEmpty();
    }

    private static final String EXT_SYNTAX_TYPE = "org.netbeans.editor.ext.ExtSyntaxSupport";
    private static final String BASE_DOCUMENT_TYPE = "org.netbeans.editor.BaseDocument";
    private static final String EDITOR_TOKEN_ID_TYPE = "org.netbeans.editor.TokenID";
    private static final String EDITOR_TOKEN_CATEGORY_TYPE = "org.netbeans.editor.TokenCategory";

    private String generateSyntaxSupport(String mimeType, Element on, String dataObjectPackage, AnnotationMirror registrationAnno, String prefix, LexerProxy lexer) throws IOException {
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(on);
        AnnotationMirror syntax = utils().annotationValue(registrationAnno, "syntax", AnnotationMirror.class);
        if (syntax == null) {
            return null;
        }
        List<Integer> commentTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "commentTokens", Integer.class)));
        List<Integer> whitespaceTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "whitespaceTokens", Integer.class)));
        List<Integer> bracketSkipTokens = new ArrayList<>(new HashSet<>(utils().annotationValues(syntax, "bracketSkipTokens", Integer.class)));
        Collections.sort(commentTokens);
        Collections.sort(whitespaceTokens);
        Collections.sort(bracketSkipTokens);
        if (commentTokens.isEmpty() && whitespaceTokens.isEmpty() && bracketSkipTokens.isEmpty()) {
            return null;
        }
        String generatedClassName = prefix + "ExtSyntax";
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(generatedClassName)
                .importing(EXT_SYNTAX_TYPE, BASE_DOCUMENT_TYPE, EDITOR_TOKEN_ID_TYPE, "java.util.Arrays",
                        pkg.getQualifiedName() + "." + prefix + "Tokens"
                )
                .extending(simpleName(EXT_SYNTAX_TYPE))
                .withModifier(FINAL)
                .constructor(cb -> {
                    cb.addArgument(simpleName(BASE_DOCUMENT_TYPE), "doc");
                    cb.body(bb -> {
                        bb.invoke("super").withArgument("doc").inScope();
                        bb.endBlock();
                    });
                });
        if (!commentTokens.isEmpty()) {
            cl.field("COMMENT_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL).withInitializer(tokensArraySpec(prefix, commentTokens, lexer))
                    .ofType("TokenID[]");
            cl.override("getCommentTokens").withModifier(PUBLIC).returning("TokenID[]")
                    .body(fieldReturner("COMMENT_TOKENS"));
        }
        if (!bracketSkipTokens.isEmpty()) {
            cl.field("BRACKET_SKIP_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL).withInitializer(tokensArraySpec(prefix, bracketSkipTokens, lexer))
                    .ofType("TokenID[]");
            cl.override("getBracketSkipTokens").withModifier(PROTECTED).returning("TokenID[]")
                    .body(fieldReturner("BRACKET_SKIP_TOKENS"));
        }
        if (!whitespaceTokens.isEmpty()) {
            if (whitespaceTokens.size() == 1) {
                cl.overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument("TokenID", "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("tokenId.getNumericID() == " + whitespaceTokens.get(0)).endBlock();
                        });
            } else {
                cl.overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument("TokenID", "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("tokenId.getNumericID() == " + whitespaceTokens.get(0)).endBlock();
                        });
                cl.field("WHITESPACE_TOKEN_IDS", fb -> {
                    fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL);
                    fb.withInitializer("new int[] {" + StringUtils.commas(whitespaceTokens) + "}").ofType("int[]");
                }).overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument("TokenID", "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("Arrays.binarySearch(WHITESPACE_TOKEN_IDS, tokenId.getNumericID()) >= 0").endBlock();
                        });
            }
        }
        if (useDeclarationTokenProcessor(mimeType)) {
            String className = capitalize(stripMimeType(mimeType)) + simpleName(DECLARATION_TOKEN_PROCESSOR_TYPE);
            cl.overridePublic("createDeclarationTokenProcessor").returning(DECLARATION_TOKEN_PROCESSOR_TYPE)
                    .addArgument("String", "varName")
                    .addArgument("int", "startPos")
                    .addArgument("int", "endPos")
                    .bodyReturning("new " + className + "(varName, startPos, endPos, getDocument())");
            cl.overridePublic("findDeclarationPosition")
                    .addArgument("String", "varName")
                    .addArgument("int", "varPos")
                    .returning("int")
                    .bodyReturning("new " + className + "(varName, varPos, -1, getDocument()).getDeclarationPosition()");
            cl.overridePublic("findLocalDeclarationPosition")
                    .addArgument("String", "varName")
                    .addArgument("int", "varPos")
                    .returning("int")
                    .bodyReturning("new " + className + "(varName, varPos, -1, getDocument()).getDeclarationPosition()");
        }
        writeOne(cl);
        return generatedClassName;
    }

    private Consumer<BlockBuilder<?>> fieldReturner(String name) {
        return bb -> {
//            bb.returning(name).endBlock();
            bb.returningInvocationOf("copyOf").withArgument(name).withArgument(name + ".length").on("Arrays").endBlock();
        };
    }

    String tokensArraySpec(String prefix, List<Integer> ids, LexerProxy lexer) {
        String tokensClass = prefix + "Tokens";
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String fieldName = lexer.toFieldName(id);
            if (fieldName == null) {
                utils().fail("No token id for " + id);
                continue;
            }
            sb.append(tokensClass).append('.').append(fieldName);
        }
        sb.insert(0, "new TokenID[] {");
        return sb.append('}').toString();
    }

    private void createFoMethod(ClassBuilder<String> bldr, String name, boolean value) {
        char[] c = name.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        String methodName = "is" + new String(c);
        bldr.override(methodName).withModifier(PUBLIC).returning("boolean").body().returning(Boolean.toString(value)).endBlock();
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
    private void generateParserClasses(ParserProxy parser, LexerProxy lexer,
            TypeElement type, AnnotationMirror mirror,
            AnnotationMirror parserInfo, String prefix) throws IOException {
        String nbParserName = prefix + "NbParser";
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokenTypeName = prefix + "Token";

        boolean changeSupport = utils().annotationValue(parserInfo, "changeSupport", Boolean.class, false);
        int streamChannel = utils().annotationValue(parserInfo, "streamChannel", Integer.class, 0);

        TypeMirror helperClass = utils().typeForSingleClassAnnotationMember(parserInfo, "helper");
        boolean hasExplicitHelperClass = helperClass != null
                && !"org.nemesis.antlr.spi.language.NbParserHelper".equals(helperClass.toString());
        String helperClassName = hasExplicitHelperClass ? helperClass.toString() : prefix + "ParserHelper";

        String entryPointType = parser.parserEntryPointReturnTypeFqn();
        String entryPointSimple = parser.parserEntryPointReturnTypeSimple();
        String parserResultType = "AntlrParseResult";

        ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(nbParserName)
                .withModifier(PUBLIC).withModifier(FINAL)
                .importing("org.netbeans.modules.parsing.api.Snapshot", "org.netbeans.modules.parsing.api.Task",
                        "org.netbeans.modules.parsing.spi.Parser", "org.netbeans.modules.parsing.spi.SourceModificationEvent",
                        "javax.annotation.processing.Generated", "org.nemesis.source.api.GrammarSource",
                        "org.nemesis.source.api.ParsingBag", "org.openide.util.Exceptions", "javax.swing.event.ChangeListener",
                        entryPointType, "org.netbeans.modules.parsing.spi.ParserFactory", "java.util.Collection",
                        "java.util.concurrent.atomic.AtomicBoolean", "java.util.function.BooleanSupplier",
                        "org.nemesis.antlr.spi.language.NbParserHelper", "org.nemesis.antlr.spi.language.AntlrParseResult",
                        "org.nemesis.antlr.spi.language.SyntaxError", "java.util.function.Supplier",
                        "org.nemesis.extraction.Extractor", "org.nemesis.extraction.Extraction",
                        "org.antlr.v4.runtime.CommonTokenStream", "java.util.Optional", "java.util.List",
                        "org.nemesis.antlr.spi.language.IterableTokenSource", lexer.lexerClassFqn(),
                        parser.parserClassFqn()
                )
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .extending("Parser")
                .docComment("NetBeans parser wrapping ", parser.parserClassSimple(), " using entry point method ", parser.parserEntryPoint().getSimpleName(), "()");
        if (changeSupport) {
            cl.importing("java.util.Set", "org.openide.util.WeakSet", "org.openide.util.ChangeSupport")
                    .field("INSTANCES").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).withInitializer("new WeakSet<>()").ofType("Set<" + nbParserName + ">")
                    .field("changeSupport").withModifier(FINAL).withModifier(PRIVATE).withInitializer("new ChangeSupport(this)").ofType("ChangeSupport");
        }
        cl.field("HELPER").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).withInitializer("new " + helperClassName + "()").ofType(helperClassName)
                .field("cancelled").withModifier(FINAL).withModifier(PRIVATE).withInitializer("new AtomicBoolean()").ofType("AtomicBoolean")
                .field("lastResult").withModifier(PRIVATE).ofType(parserResultType)
                .constructor().annotatedWith("SuppressWarnings").addStringArgument("value", "LeakingThisInConstructor").closeAnnotation().setModifier(PUBLIC)
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
                })
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
                            });
                })
                .method("parse", mb -> {
                    mb.addArgument("ParsingBag", "bag").throwing("Exception").addArgument("BooleanSupplier", "cancelled").returning("AntlrParseResult")
                            .withModifier(PUBLIC).withModifier(STATIC)
                            .body(bb -> {
                                bb.declare("lexer").initializedByInvoking("createAntlrLexer").withArgument("bag.source().stream()")
                                        .on(prefix + "Hierarchy").as(lexer.lexerClassSimple());
                                bb.declare("tokenSource").initializedByInvoking("createWrappedTokenSource")
                                        .withArgument("lexer")
                                        .on(prefix + "Hierarchy").as("IterableTokenSource");
                                bb.declare("stream").initializedWith("new CommonTokenStream(tokenSource, " + streamChannel + ")")
                                        .as("CommonTokenStream");
                                bb.declare("parser").initializedWith("new " + parser.parserClassSimple() + "(stream)").as(parser.parserClassSimple());
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
                                bb.declare("tree").initializedByInvoking(parser.parserEntryPoint().getSimpleName().toString())
                                        .on("parser").as(entryPointSimple);
                                bb.blankLine();
                                bb.lineComment("Look up the extrator(s) for this mime type");
                                bb.declare("extractor")
                                        .initializedByInvoking("forTypes").withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument(entryPointSimple + ".class").on("Extractor")
                                        .as("Extractor<? super " + entryPointSimple + ">");
                                bb.blankLine();
                                bb.lineComment("Run extraction, pulling out data needed to create navigator panels,");
                                bb.lineComment("code folds, etc.  Anything needing the extracted sets of regions and data");
                                bb.lineComment("can get it from the parse result");
                                bb.declare("extraction").initializedByInvoking("extract")
                                        .withArgument("tree")
                                        .withArgumentFromInvoking("source").on("bag")
                                        .withArgument("cancelled")
                                        .withArgument("tokenSource")
                                        //.withArgument("bag.source()")
                                        //                                        .withLambdaArgument().body().returning("new CommonTokenStream(lexer, -1)").endBlock()
                                        .on("extractor").as("Extraction");
                                bb.lineComment("// discard the cached tokens used for token extraction");
                                bb.invoke("dispose").on("tokenSource");
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
                                        .invoke("parseCompleted")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument("tree").withArgument("extraction")
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
                            });
                })
                .method("getResult", (mb) -> {
                    mb.withModifier(PUBLIC)
                            .withModifier(SYNCHRONIZED)
                            .addArgument("Task", "task").override().returning(parserResultType)
                            .body().returning("lastResult").endBlock();
                })
                .override("addChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.addChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                .override("removeChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.statement("changeSupport.removeChangeListener(l)").endBlock();
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                // public void cancel (@NonNull CancelReason reason, @NullAllowed SourceModificationEvent event) {}
                .override("cancel").withModifier(PUBLIC).addArgument("CancelReason", "reason")
                .addArgument("SourceModificationEvent", "event")
                .body(bb -> {
                    bb.log("Parse cancelled", Level.FINEST);
                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                })
                .override("cancel")
                .annotatedWith("SuppressWarnings").addStringArgument("value", "deprecation").closeAnnotation()
                .withModifier(PUBLIC)
                .body(bb -> {
                    bb.log("Parse cancelled", Level.FINEST);
                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                });
        if (changeSupport) {
            cl.method("forceReparse").withModifier(PRIVATE).body().invoke("fireChange").on("changeSupport").endBlock()
                    .method("languageSettingsChanged").withModifier(PUBLIC).withModifier(STATIC)
                    .body(bb -> {
                        bb.lineComment("Cause all existing instances of this class to fire a change, triggering");
                        bb.lineComment("a re-parse, in the event of a global change of some sort.");
                        bb.simpleLoop(nbParserName, "parser").over("INSTANCES")
                                .invoke("forceReparse").on("parser").endBlock();
                        bb.endBlock();
                    });
        }

        if (!hasExplicitHelperClass) {
            // <P extends Parser, L extends Lexer, R extends Result, T extends ParserRuleContext> {
            cl.innerClass(helperClassName).extending("NbParserHelper<" + parser.parserClassSimple() + ", " + lexer.lexerClassSimple()
                    + ", AntlrParseResult, " + entryPointSimple + ">")
                    .withModifier(PRIVATE).withModifier(FINAL).withModifier(STATIC).build();
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
                .method("createParser").override().withModifier(PUBLIC).returning("Parser")
                .addArgument("Collection<Snapshot>", "snapshots")
                .body().returning("new " + nbParserName + "()").endBlock()
                .build();

        cl.importing("org.netbeans.modules.parsing.spi.TaskFactory",
                "org.nemesis.antlr.spi.language.NbAntlrUtils")
                .method("createErrorHighlighter", mb -> {
                    mb.withModifier(PUBLIC).withModifier(STATIC)
                            .annotatedWith("MimeRegistration").addArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                            .addClassArgument("service", "TaskFactory").closeAnnotation()
                            .returning("TaskFactory")
                            .body(bb -> {
                                bb.returningInvocationOf("createErrorHighlightingTaskFactory")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .on("NbAntlrUtils").endBlock();
                            });
                });

        writeOne(cl);
        if (utils().annotationValue(parserInfo, "generateSyntaxTreeNavigatorPanel", Boolean.class, false)) {
            generateSyntaxTreeNavigatorPanel(type, mirror, parser.parserEntryPoint());
        }
    }

    // Navigator generation
    private void generateSyntaxTreeNavigatorPanel(TypeElement type, AnnotationMirror mirror, ExecutableElement entryPointMethod) throws IOException {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        AnnotationMirror fileType = utils().annotationValue(mirror, "file", AnnotationMirror.class);

        String icon = fileType == null ? null : utils().annotationValue(fileType, "iconBase", String.class);
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String generatedClassName = type.getSimpleName() + "_SyntaxTreeNavigator_Registration";
        String entryPointType = entryPointMethod.getReturnType().toString();
        String entryPointSimple = simpleName(entryPointType.toString());
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .importing("java.util.function.BiConsumer", "org.antlr.v4.runtime.ParserRuleContext",
                        "org.antlr.v4.runtime.tree.ParseTree", "org.nemesis.antlr.navigator.SimpleNavigatorRegistration",
                        "org.nemesis.extraction.ExtractionRegistration", "org.nemesis.extraction.ExtractorBuilder",
                        "org.nemesis.extraction.key.RegionsKey", "org.antlr.v4.runtime.Token", entryPointType)
                .withModifier(PUBLIC)
                .constructor(c -> {
                    c.setModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock();
                }).field("TREE_NODES", fb -> {
            fb.annotatedWith("SimpleNavigatorRegistration", ab -> {
                if (icon != null) {
                    ab.addStringArgument("icon", icon);
                }
                ab.addStringArgument("mimeType", mimeType)
                        .addArgument("order", 20000)
                        .addStringArgument("displayName", "Syntax Tree").closeAnnotation();
            });
            fb.withModifier(STATIC).withModifier(FINAL)
                    .initializedFromInvocationOf("create").withArgument("String.class").withStringLiteral("tree")
                    .on("RegionsKey").ofType("RegionsKey<String>");
            ;
        }).method("extractTree", mb -> {
            mb.withModifier(STATIC)
                    .annotatedWith("ExtractionRegistration")
                    .addStringArgument("mimeType", mimeType)
                    .addClassArgument("entryPoint", entryPointSimple)
                    .closeAnnotation()
                    .addArgument("ExtractorBuilder<? super " + entryPointSimple + ">", "bldr")
                    .body(bb -> {
                        bb.statement("bldr.extractingRegionsUnder( TREE_NODES ).whenRuleType( ParserRuleContext.class )\n"
                                + "                .extractingKeyAndBoundsFromRuleWith( " + generatedClassName + "::ruleToString ).finishRegionExtractor()")
                                .endBlock();
                    });
        }).insertText(additionalSyntaxNavigatorMethods());
        writeOne(cb);
    }

    private String additionalSyntaxNavigatorMethods() throws IOException {
        InputStream in = LanguageRegistrationProcessor.class.getResourceAsStream("additional-syntax-navigator-methods.txt");
        if (in == null) {
            throw new Error("additional-syntax-navigator-methods.txt is not on classpath next to " + LanguageRegistrationProcessor.class);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtil.copy(in, out);
        StringBuilder sb = new StringBuilder();
        for (String line : new String(out.toByteArray(), UTF_8).split("\n")) {
            String l = line.trim();
            if (l.startsWith("//") || l.startsWith("#")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String generateTokenCategoryWrapper(TypeElement on, String prefix) throws IOException {
        String generatedClassName = prefix + "TokenCategory";
        ClassBuilder<String> cb = ClassBuilder.forPackage(processingEnv.getElementUtils().getPackageOf(on).getQualifiedName())
                .named(generatedClassName)
                .importing(EDITOR_TOKEN_CATEGORY_TYPE)
                .implementing("TokenCategory")
                .withModifier(FINAL)
                .field("category").withModifier(PRIVATE, FINAL).ofType(STRING)
                .constructor(con -> {
                    con.addArgument(STRING, "category").body().statement("this.category = category").endBlock();
                })
                .override("getName").withModifier(PUBLIC).returning(STRING).body().returning("category").endBlock()
                .override("getNumericID").withModifier(PUBLIC).returning("int").body().returning("0").endBlock()
                .override("equals", mb -> {
                    mb.withModifier(PUBLIC).returning("boolean").addArgument("Object", "o")
                            .body(bb -> {
                                bb.ifCondition().variable("this").equals().literal("o").endCondition()
                                        .thenDo().returning("true").endBlock()
                                        .elseIf().variable("o").equals().literal("null").endCondition()
                                        .returning("false").endBlock()
                                        .elseIf().variable("o").instanceOf().literal(generatedClassName).endCondition()
                                        .returningInvocationOf("equals")
                                        .withArgument("o.toString()")
                                        .on("category").endBlock()
                                        .elseDo().returning("false").endBlock().endIf().endBlock();
                                ;
                            });
                })
                .override("hashCode", mb -> {
                    mb.withModifier(PUBLIC).returning("int").body().returningInvocationOf("hashCode").on("category").endBlock();
                })
                .override("toString", mb -> {
                    mb.withModifier(PUBLIC).returning(STRING).body().returning("category").endBlock();
                });
        writeOne(cb);
        return generatedClassName;
    }

    // Lexer generation
    private void generateTokensClasses(TypeElement type, AnnotationMirror mirror, LexerProxy proxy, TypeMirror tokenCategorizerClass, String prefix) throws Exception {

        List<AnnotationMirror> categories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        // Figure out if we are generating a token categorizer now, so we have the class name for it
        String tokenCatName = willHandleCategories(categories, tokenCategorizerClass, prefix, utils());
        String tokenTypeName = prefix + "Token";
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if ("<error>".equals(mimeType)) {
            utils().fail("Mime type invalid: " + mimeType);
            return;
        }
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokensTypeName = prefix + "Tokens";

        boolean hasSyntaxInfo = hasSyntax(mirror);
        String tokenWrapperClass = hasSyntaxInfo ? generateTokenCategoryWrapper(type, prefix) : null;
        // First class will be an interface that extends NetBeans' TokenId - we will implement it
        // as an inner class on our class with constants for each token type
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(prefix + "Token").toInterface().extending("Comparable<" + tokenTypeName + ">")
                .extending("TokenId")
                .docComment("Token interface for ", prefix, " extending the TokenId type from NetBeans' Lexer API.",
                        " Instances for types defined by the lexer " + proxy.lexerType() + " can be found as static fields on ",
                        tokensTypeName, " in this package (", pkg, "). ",
                        "Generated by ", getClass().getSimpleName(), " from fields on ", proxy.lexerClassSimple(), " specified by "
                        + "an annotation on ", type.getSimpleName())
                .makePublic()
                .importing("org.netbeans.api.lexer.TokenId", proxy.lexerClassFqn())
                .importing("javax.annotation.processing.Generated")
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(FINAL).withModifier(STATIC).withModifier(PUBLIC).withInitializer(LinesBuilder.stringLiteral(mimeType)).ofType("String")
                .method("ordinal").override().returning("int").closeMethod()
                .method("symbolicName").returning(STRING).closeMethod()
                .method("literalName").returning(STRING).closeMethod()
                .method("displayName").returning(STRING).closeMethod()
                .method("primaryCategory").override().returning(STRING).closeMethod()
                .method("compareTo").override().returning("int").addArgument(tokenTypeName, "other").withModifier(DEFAULT)
                .body().returning("ordinal() > other.ordinal() ? 1 : ordinal() == other.ordinal() ? 0 : -1").endBlock()
                .method("name").annotatedWith("Override").closeAnnotation().returning("String").withModifier(DEFAULT)
                .body().returning("literalName() != null ? literalName() \n: symbolicName() != null \n? symbolicName() \n: "
                        + "displayName() != null \n? displayName() \n: Integer.toString(ordinal())").endBlock();

        if (tokenWrapperClass != null) {
            cb.implementing(EDITOR_TOKEN_ID_TYPE);
            cb.importing(EDITOR_TOKEN_CATEGORY_TYPE);
            cb.override("getCategory").returning("TokenCategory")
                    .withModifier(DEFAULT).body()
                    .returning("new " + tokenWrapperClass + "(primaryCategory())").endBlock();
            cb.override("getName").returning(STRING)
                    .withModifier(DEFAULT).body()
                    .returning("name()").endBlock();
            cb.override("getNumericID").returning("int").withModifier(DEFAULT).body()
                    .returning("ordinal()").endBlock();
        }

        String tokensImplName = prefix + "TokenImpl";

        // This class will contain constants for every token type, and methods
        // for looking them up by name, character, etc.
        ClassBuilder<String> toks = ClassBuilder.forPackage(pkg).named(tokensTypeName)
                .makePublic().makeFinal()
//                .docComment("Provides all tokens for the  ", prefix,
//                        "Generated by ", getClass().getSimpleName(),
//                        " from fields on ", proxy.lexerClassSimple(),
//                        " from annotation on ", type.getSimpleName())
                .importing("java.util.Arrays", "java.util.HashMap", "java.util.Map", "java.util.Optional",
                        "java.util.List", "java.util.ArrayList", "java.util.Collections",
                        "org.nemesis.antlr.spi.language.highlighting.TokenCategorizer",
                        "javax.annotation.processing.Generated", proxy.lexerClassFqn())
                .annotatedWith("Generated").addStringArgument("value", getClass().getName()).addStringArgument("comments", versionString()).closeAnnotation()
                // Static categorizer field - fall back to a heuristic categorizer if nothing is specified
                .field("CATEGORIZER").withModifier(FINAL)/*.withModifier(PRIVATE)*/.withModifier(STATIC)
                .withInitializer(tokenCatName == null ? "TokenCategorizer.heuristicCategorizer()" : "new " + tokenCatName + "()")
                .ofType("TokenCategorizer")
                // Make it non-instantiable
                .constructor().setModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock();

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
                .returning("Optional.empty()").endBlock();

        // Lookup by symbolic name method
        toks.method("forSymbolicName").addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(bySymbolicName.get(name))").endBlock();

        // Lookup by literal name method
        toks.method("forLiteralName").addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(byLiteralName.get(name))").endBlock();

        // Helper method - literal names in antlr generated code come surrounded in
        // single quotes - strip those
        toks.method("stripQuotes").addArgument("String", "name").returning("String")
                .withModifier(STATIC)/*.withModifier(PRIVATE)*/.body().ifCondition().variable("name").notEquals().literal("null").and().invoke("isEmpty").on("name").equals().literal("false").and()
                .invoke("charAt").withArgument("0").on("name").equals().literal('\'').and().invoke("charAt").withArgument("name.length()-1")
                .on("name").equals().literal('\'').endCondition().thenDo().returning("name.substring(1, name.length()-1)").endBlock().endIf()
                .returning("name").endBlock();

        // Our implementation of the TokenId sub-interface defined as the first class above,
        // implementing all of its methods
        ClassBuilder<String> tokensImpl = ClassBuilder.forPackage(pkg).named(tokensImplName)
                .makeFinal().importing(proxy.lexerClassFqn())
                .lineComment("This ought to be a private inner class of\n" + prefix + "Tokens, but that "
                        + "wreaks havoc with NetBeans parsing, since it\ntries to access the nested"
                        + " class before javac has reached \nit - only the case with generated "
                        + "sources.\nSo we do it like this for now.")
//        toks.innerClass(tokensImplName).withModifier(FINAL).withModifier(STATIC).withModifier(PRIVATE)
//                .docComment("Implementation of " + tokenTypeName + " implementing NetBeans' TokenId class from the Lexer API.")
                .field("id").withModifier(PRIVATE).withModifier(FINAL).ofType("int")
                .implementing(tokenTypeName).constructor().addArgument("int", "id").body()
                .statement("this.id = id").endBlock()
                .method("ordinal").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("int").body().returning("this.id").endBlock()
                .method("symbolicName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(toks.className() + "." + "stripQuotes(" + proxy.lexerClassSimple() + ".VOCABULARY.getSymbolicName(id))").endBlock()
                .method("literalName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(proxy.lexerClassSimple() + ".VOCABULARY.getLiteralName(id)").endBlock()
                .method("displayName").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(proxy.lexerClassSimple() + ".VOCABULARY.getDisplayName(id)").endBlock()
                .method("primaryCategory").annotatedWith("Override").closeAnnotation().withModifier(PUBLIC).returning("String").body().returning(toks.className() + "." + "CATEGORIZER.categoryFor(ordinal(), displayName(), symbolicName(), literalName())").endBlock()
                .method("toString").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().returning("String").body().returning("ordinal() + \"/\" + displayName()").endBlock()
                .method("equals").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().addArgument("Object", "o")
                .returning("boolean").body().returning("o == this || (o instanceof " + tokensImplName + " && ((" + tokensImplName + ") o).ordinal() == ordinal())").endBlock()
                .method("hashCode").withModifier(PUBLIC).annotatedWith("Override").closeAnnotation().returning("int").body().returning("id * 51").endBlock();

        writeOne(tokensImpl);

        // Now build a giant switch statement for lookup by id
        List<Integer> keys = proxy.allTypesSortedByName();
        ClassBuilder.SwitchBuilder<BlockBuilder<ClassBuilder<String>>> idSwitch
                = toks.method("forId").withModifier(PUBLIC)
                        .withModifier(STATIC).returning(tokenTypeName)
                        .addArgument("int", "id").body().switchingOn("id");

        toks.method("all").withModifier(STATIC).withModifier(PUBLIC)
                .returning(tokenTypeName + "[]").body()
                .returning("Arrays.copyOf(ALL, ALL.length)").endBlock();

        ClassBuilder.ArrayLiteralBuilder<ClassBuilder<String>> allArray = toks.field("ALL").withModifier(STATIC).withModifier(PRIVATE).withModifier(FINAL).assignedTo().toArrayLiteral(tokenTypeName);
        // Loop and build cases in the switch statement for each token id, plus EOF,
        // and also populate our static array field which contains all of the token types
        for (Integer k : keys) {
            String fieldName = proxy.toFieldName(k);
            toks.field(fieldName).withModifier(STATIC).withModifier(PUBLIC).withModifier(FINAL)
                    .withInitializer("new " + tokensImplName + "(" + k + ")")
                    .ofType(tokenTypeName);
            idSwitch.inCase(k).returning(fieldName).endBlock();
            if (k != -1) {
                allArray.add(fieldName);
            }
        }
        idSwitch.inDefaultCase().statement("throw new IllegalArgumentException(\"No such id \" + id)").endBlock();
        idSwitch.build().endBlock();
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
                        "java.util.function.BiConsumer", "org.nemesis.antlr.spi.language.ParseResultContents",
                        "org.netbeans.modules.parsing.api.Snapshot", "org.nemesis.extraction.Extraction",
                        "org.nemesis.antlr.spi.language.AntlrParseResult", "org.nemesis.antlr.spi.language.IterableTokenSource",
                        "org.netbeans.api.editor.mimelookup.MimeRegistration",
                        "org.antlr.v4.runtime.Vocabulary", proxy.lexerClassFqn()
                )
                .docComment("LanguageHierarchy implementation for ", prefix,
                        ". Generated by ", getClass().getSimpleName(), " from fields on ", proxy.lexerClassSimple(), ".")
                .importing("javax.annotation.processing.Generated")
                .makePublic().makeFinal()
                .constructor().setModifier(PUBLIC).body().debugLog("Create a new " + hierName).endBlock()
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
                .annotatedWith("Override").closeAnnotation()
                .withModifier(PROTECTED).withModifier(FINAL).body()
                .returning(tokenTypeName + ".MIME_TYPE").endBlock()
                .method(languageMethodName).withModifier(PUBLIC).withModifier(STATIC).returning("Language<" + tokenTypeName + ">")
                //                .annotatedWith("MimeRegistration", ab -> {
                //                    ab.addStringArgument("mimeType", mimeType)
                //                            .addClassArgument("service", "Language")
                //                            .addArgument("position", 500).closeAnnotation();
                //                })
                .body().returning("LANGUAGE").endBlock()
                .method("createTokenIds").returning("Collection<" + tokenTypeName + ">").override().withModifier(PROTECTED).withModifier(FINAL).body().returning("IDS").endBlock()
                .method("createLexer").override().returning("Lexer<" + tokenTypeName + ">").addArgument("LexerRestartInfo<" + tokenTypeName + ">", "info").withModifier(PROTECTED).withModifier(FINAL)
                .body(bb -> {
                    bb.debugLog("Create a new " + prefix + " Lexer");
                    bb.returning("NbAntlrUtils.createLexer(info, LEXER_ADAPTER)").endBlock();
                })
                .method("isRetainTokenText").override().withModifier(PROTECTED).withModifier(FINAL).returning("boolean").addArgument(tokenTypeName, "tok")
                .body().returning("tok.literalName() == null").endBlock()
                .method("createTokenCategories").override().withModifier(PROTECTED).withModifier(FINAL)
                .returning("Map<String, Collection<" + tokenTypeName + ">>").body().returning("CATEGORIES").endBlock()
                .method("createAntlrLexer")
                .returning(proxy.lexerClassSimple()).withModifier(PUBLIC).withModifier(STATIC)
                .addArgument("CharStream", "stream")
                .body().returning("LEXER_ADAPTER.createLexer(stream)").endBlock()
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
                            });
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.addArgument(proxy.lexerClassSimple(), "lexer")
                            .withModifier(STATIC)
                            .returning("IterableTokenSource")
                            .body(body -> {
                                body.returningInvocationOf("createWrappedTokenSource")
                                        .withArgument("lexer").on("LEXER_ADAPTER").endBlock();
                            });
                });

        // Inner implementation of NbLexerAdapter, which allows us to use a generic
        // Lexer class and takes care of creating the Antlr lexer and calling methods
        // that will exist on the lexer implementation class but not on the parent class
        lh.innerClass(adapterClassName).withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .extending("NbLexerAdapter<" + tokenTypeName + ", " + proxy.lexerClassSimple() + ">")
                .method("createLexer").override().withModifier(PUBLIC).returning(proxy.lexerClassSimple()).addArgument("CharStream", "stream")
                .body(bb -> {
                    bb.declare("result").initializedWith("new " + proxy.lexerClassSimple() + "(stream)").as(proxy.lexerClassSimple());
                    bb.invoke("removeErrorListeners").on("result");
                    bb.returning("result").endBlock();
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.addArgument(proxy.lexerClassSimple(), "lexer").returning("IterableTokenSource")
                            .body().returningInvocationOf("wrapLexer")
                            .withArgument("lexer").on("super").endBlock();
                })
                .override("vocabulary").withModifier(PROTECTED).returning("Vocabulary")
                .body().returning(proxy.lexerClassSimple() + ".VOCABULARY").endBlock()
                .method("tokenId").withModifier(PUBLIC).override().addArgument("int", "ordinal").returning(tokenTypeName)
                .body().returning(tokensTypeName + ".forId(ordinal)").endBlock()
                .method("setInitialStackedModeNumber").override().withModifier(PUBLIC).addArgument(proxy.lexerClassSimple(), "lexer")
                .addArgument("int", "modeNumber").body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .statement("lexer.setInitialStackedModeNumber(modeNumber)").endBlock()
                .overridePublic("getInitialStackedModeNumber").returning("int").addArgument(proxy.lexerClassSimple(), "lexer")
                .body()
                .lineComment("This method will be exposed on the implementation type")
                .lineComment("but is not exposed in the parent class Lexer")
                .returning("lexer.getInitialStackedModeNumber()").endBlock()
                .method("newParseResult", mth -> {
                    mth.addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .body(bb -> {
                                bb.invoke("createParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("super").endBlock();
                            });
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
            handleCategories(categories, proxy, type, mirror, tokenCategorizerClass, tokenCatName, pkg, prefix);
        }
        String bundle = utils().annotationValue(mirror, "localizingBundle", String.class);

        String layerFileName = hierFqn.replace('.', '-');
        String languageRegistrationPath = "Editors/" + mimeType + "/" + layerFileName + ".instance";

        String genBundleName = prefix + "LanguageBundle";
        String genBundleInfo = pkg + "." + genBundleName;
        Filer filer = processingEnv.getFiler();
        String bundlePath = genBundleInfo.replace('.', '/') + ".properties";
        FileObject altBundle = filer.createResource(StandardLocation.CLASS_OUTPUT, "", bundlePath, type);
        Properties props = new Properties();
        props.setProperty("prefix", prefix);
        props.setProperty(mimeType, prefix);
        props.setProperty("Editors/" + mimeType, prefix);
        props.setProperty(languageRegistrationPath, prefix);

        try (OutputStream out = altBundle.openOutputStream()) {
            props.store(out, getClass().getName());
        }

        addLayerTask(lb -> {
            // For some reason, the generated @MimeRegistration annotation is resulting
            // in an attempt to load a *class* named with the fqn + method name.
            // So, do it the old fashioned way
            try {
                lb.folder("Editors/" + mimeType).bundlevalue("displayName",
                        bundle + "#" + mimeType).write();

                lb.file(languageRegistrationPath)
                        //            LayerBuilder.File reg = lb.instanceFile("Editors/" + mimeType, layerFileName)
                        .methodvalue("instanceCreate", hierFqn, languageMethodName)
                        .stringvalue("instanceOf", "org.netbeans.api.lexer.Language")
                        .intvalue("position", 1000)
                        .bundlevalue("displayName", bundle + "#" + mimeType)
                        .write();
            } catch (LayerGenerationException ex) {
                logException(ex, true);
                utils().fail("No bundle key found for the language name in the"
                        + " specified bundle. Try specifying '"
                        + mimeType + "=" + prefix
                        + "' in the properties file " + bundle, type);
            }
        }, type);
    }

    static String willHandleCategories(List<AnnotationMirror> categories, TypeMirror specifiedClass, String prefix, AnnotationUtils utils) {
        if (categories == null || categories.isEmpty() || (categories.size() == 1 && "_".equals(utils.annotationValue(categories.get(0), "name", String.class)))) {
            return specifiedClass == null ? null : specifiedClass.toString();
        }
        return prefix + "TokenCategorizer";
    }

    private void handleCategories(List<AnnotationMirror> categories, LexerProxy lexer, TypeElement type, AnnotationMirror mirror, TypeMirror tokenCategorizerClass, String catName, Name pkg, String prefix) throws Exception {
        // Create an implementation of TokenCategorizer based on the annotation values
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(catName).withModifier(FINAL)
                .importing("org.nemesis.antlr.spi.language.highlighting.TokenCategorizer")
                .implementing("TokenCategorizer")
                .conditionally(tokenCategorizerClass != null, cbb -> {
                    cbb.field("DEFAULT_CATEGORIZER").withInitializer("new " + tokenCategorizerClass + "()").ofType("TokenCategorizer");
                });

        // categoryFor(ordinal(), displayName(), symbolicName(), literalName())
        cb.publicMethod("categoryFor", catFor -> {
            catFor.addArgument("int", "id")
                    .addArgument(STRING, "displayName").addArgument(STRING, "symbolicName")
                    .addArgument(STRING, "literalName").returning(STRING)
                    .body(meth -> {
                        Set<Integer> usedTokenTypes = new HashSet<>();
                        meth.conditionally(tokenCategorizerClass != null, bb -> {
                            bb.declare("result").initializedByInvoking("categoryFor")
                                    .withArgument("id")
                                    .withArgument("displayName")
                                    .withArgument("symbolicName")
                                    .withArgument("literalName")
                                    .on("DEFAULT_CATEGORIZER");
                            bb.ifCondition().variable("result").notEquals().literal("null").endCondition()
                                    .thenDo().returning("result").endBlock().endIf();
                        }).switchingOn("id", (SwitchBuilder<?> sw) -> {
                            for (AnnotationMirror cat : categories) {
                                String name = utils().annotationValue(cat, "name", String.class);
                                List<Integer> values = utils().annotationValues(cat, "tokenIds", Integer.class);
                                Collections.sort(values);
                                for (Iterator<Integer> it = values.iterator(); it.hasNext();) {
                                    Integer val = it.next();
                                    if (usedTokenTypes.contains(val)) {
                                        utils().fail("More than one token category specifies the token type " + val
                                                + " - " + lexer.tokenName(val) + " - in category '" + name + "' of " + cat, type, cat);
                                        return;
                                    }
                                    if (!lexer.typeExists(val)) {
                                        Map<String, Integer> consts = lexerClassConstants();
                                        if (consts.values().contains(val)) {
                                            List<String> all = new ArrayList<>();
                                            for (Map.Entry<String, Integer> e : consts.entrySet()) {
                                                if (val.equals(e.getValue())) {
                                                    all.add(e.getKey());
                                                }
                                            }
                                            if (all.size() == 1) {
                                                utils().fail(val + " is not a token type on your lexer; it is likely the constant "
                                                        + all.get(0) + ", declared on its parent class, Lexer"
                                                        + " and accidentally included.", type, cat);
                                            } else {
                                                utils().fail(val + " is not a token type on your lexer; it is likely a constant "
                                                        + "declared on its parent class Lexer, one of " + all, type, cat);
                                            }
                                            return;
                                        }
                                        utils().fail("No token with type " + val + " exists on as a field on " + lexer.lexerClassFqn()
                                                + " specified in category '" + name + "' of " + cat, type, cat);
                                        return;
                                    }
                                    sw.inCase(val, caseBody -> {
                                        String tokenName = lexer.tokenName(val);
                                        String fieldName = lexer.toFieldName(val);
                                        caseBody.lineComment(prefix + "Tokens." + fieldName);
                                        caseBody.lineComment(lexer.lexerClassSimple() + "." + tokenName);
                                        if (!it.hasNext()) {
                                            caseBody.returningStringLiteral(name);
                                        }
//                                        caseBody.endBlock();
                                    });
                                    usedTokenTypes.add(val);
                                }
                            }
                            sw.inCase(lexer.erroneousTokenId(), caseBody -> {
                                caseBody.lineComment(prefix + ".TOK_$ERRONEOUS");
                                caseBody.lineComment("This token does not exist in \n"
                                        + lexer.lexerClassSimple()
                                        + ", but is \nused by the generated NetBeans lexer to "
                                        + "handle trailing tokens\nif the Antlr lexer aborts.");
                                caseBody.returningStringLiteral("error");
                            });
                            sw.inDefaultCase().returningStringLiteral("default").endBlock();
                        });
                        Set<Integer> unhandled = lexer.tokenTypes();
                        unhandled.remove(lexer.erroneousTokenId());
                        unhandled.remove(-1);
                        unhandled.removeAll(usedTokenTypes);
                        if (!unhandled.isEmpty()) {
                            StringBuilder sb = new StringBuilder("The following tokens "
                                    + "were not given a category in the annotation that "
                                    + "generated this class:\n");
                            int ix = 0;
                            for (Iterator<Integer> it = unhandled.iterator(); it.hasNext();) {
                                Integer unh = it.next();
                                String name = lexer.tokenName(unh);
                                sb.append(name).append('(').append(unh).append(')');
                                if (it.hasNext()) {
                                    if (++ix % 3 == 0) {
                                        sb.append(",\n");
                                    } else {
                                        sb.append(", ");
                                    }
                                }
                            }
                            meth.lineComment(sb.toString());
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                    sb, type, mirror);
                        } else {
                            meth.lineComment("All token types defined in "
                                    + lexer.lexerClassSimple() + " have categories");;
                        }
                    });
        });
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

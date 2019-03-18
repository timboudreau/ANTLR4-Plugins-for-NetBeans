package org.nemesis.registration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.FormattingRegistrationProcessor.FORMATTER_REGISTRATION_ANNO_TYPE;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import org.nemesis.registration.api.AbstractLayerGeneratingRegistrationProcessor;
import org.nemesis.registration.codegen.ClassBuilder;
import org.nemesis.registration.utils.AnnotationUtils;
import static org.nemesis.registration.utils.AnnotationUtils.AU_LOG;
import static org.nemesis.registration.utils.AnnotationUtils.simpleName;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@SupportedOptions(AU_LOG)
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({FORMATTER_REGISTRATION_ANNO_TYPE, REGISTRATION_ANNO})
public class FormattingRegistrationProcessor extends AbstractLayerGeneratingRegistrationProcessor {

    public static final String FORMATTER_REGISTRATION_PACKAGE
            = "org.nemesis.antlrformatting.spi";
    public static final String FORMATTER_REGISTRATION_NAME
            = "AntlrFormatterRegistration";
    public static final String FORMATTER_REGISTRATION_ANNO_TYPE
            = FORMATTER_REGISTRATION_PACKAGE + "."
            + FORMATTER_REGISTRATION_NAME;

    public static final String STUB_TYPE = FORMATTER_REGISTRATION_PACKAGE + "."
            + "AntlrFormatterStub";
    public static final String FORMATTER_TYPE = FORMATTER_REGISTRATION_PACKAGE
            + "." + "AntlrFormatterProvider";
    public static final String ANTLR_LEXER_TYPE = "org.antlr.v4.runtime.Lexer";

    private Predicate<? super AnnotationMirror> annoCheck;
    private Predicate<? super Element> typeCheck;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        annoCheck = utils.testMirror()
                .addPredicate(this::warnIfWhitespaceElementsEmpty)
                .testMember("mimeType").validateStringValueAsMimeType()
                .build().build();
        typeCheck = utils.testsBuilder().mustBeFullyReifiedType()
                .doesNotHaveModifier(Modifier.PRIVATE)
                .isSubTypeOf(STUB_TYPE)
                .build();
    }

    boolean warnIfWhitespaceElementsEmpty(AnnotationMirror mirror) {
        List<Integer> vals = utils().annotationValues(mirror, "whitespaceTokens", Integer.class);
        if (vals.isEmpty()) {
            utils().warn("whitespaceTokens() not specified on "
                    + FORMATTER_REGISTRATION_NAME + " - resulting formatter "
                    + "may not work correctly");
        }
        return true;
    }

    private AnnotationMirror preemptivelyFindLanguageRegistration(RoundEnvironment env, String mimeType) {
        Set<Element> elements = utils().findAnnotatedElements(env, REGISTRATION_ANNO);
        for (Element e : elements) {
            AnnotationMirror mir = utils().findAnnotationMirror(e, REGISTRATION_ANNO);
            if (mir != null) {
                String mime = utils().annotationValue(mir, "mimeType", String.class);
                if (mimeType.equals(mime)) {
                    return mir;
                }
            }
        }
        return null;
    }

    private TypeElement findParserClass(TypeMirror lexerClass) {
        String name = lexerClass.toString();
        if (!name.endsWith("Lexer")) {
            return null;
        }
        name = name.substring(0, name.length() - 5) + "Parser";
        return processingEnv.getElementUtils().getTypeElement(name);
    }

    private List<Integer> findWhitespaceTokens(RoundEnvironment env, String mimeType) {
        AnnotationMirror mir = preemptivelyFindLanguageRegistration(env, mimeType);
        if (mir != null) {
            AnnotationMirror syntaxAnno = utils().annotationValue(mir, "syntax", AnnotationMirror.class);
            if (syntaxAnno != null) {
                List<Integer> whitespaceTokens = utils().annotationValues(syntaxAnno, "whitespaceTokens", Integer.class);
                if (!whitespaceTokens.isEmpty()) {
                    return whitespaceTokens;
                }
            }
        }
        return Collections.emptyList();
    }

    private final Map<String, Integer> streamChannelForMimeType = new HashMap<>();

    private Integer streamChannelForMimeType(String mimeType) {
        return streamChannelForMimeType.getOrDefault(mimeType, 0);
    }

    private List<Integer> whitespaceTokenListFromSyntaxInfoForMimeType(RoundEnvironment env, String mimeType) {
        AnnotationMirror mir = preemptivelyFindLanguageRegistration(env, mimeType);
        if (mir != null) {
            AnnotationMirror syntaxInfo = utils().annotationValue(mir, "syntax", AnnotationMirror.class);
            if (syntaxInfo != null) {
                return utils().annotationValues(syntaxInfo, "whitespaceTokens", Integer.class);
            }
        }
        return Collections.emptyList();
    }

    private ParserProxy findParserInfo(RoundEnvironment env, String mimeType, TypeMirror lexerClass) {
        AnnotationMirror mir = preemptivelyFindLanguageRegistration(env, mimeType);
        if (mir == null && lexerClass != null) {
            TypeElement parserClass = findParserClass(lexerClass);
            if (parserClass != null) {
                return ParserProxy.create(0, parserClass.asType(), utils());
            }
            return null;
        }
        AnnotationMirror parserInfo = utils().annotationValue(mir, "parser", AnnotationMirror.class);
        if (parserInfo != null) {
            List<String> parserType = utils().typeList(parserInfo, "type");
            if (!parserType.isEmpty()) {
                Integer streamChannel = utils().annotationValue(parserInfo, "parserStreamChannel", Integer.class, 0);
                streamChannelForMimeType.put(mimeType, streamChannel);
                TypeElement parserClass = processingEnv.getElementUtils().getTypeElement(parserType.get(0));
                int rootRule = utils().annotationValue(parserInfo, "entryPointRule", Integer.class, 0);
                return ParserProxy.create(rootRule, parserClass.asType(), utils());
            }
        }
        return null;
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (FORMATTER_REGISTRATION_ANNO_TYPE.equals(mirror.getAnnotationType().toString())) {
            if (annoCheck.test(mirror) && typeCheck.test(type)) {
                processFormatterRegistration(type, mirror, roundEnv);
            }
        }
        return false;
    }

    private TypeMirror findTypeArgumentOnInterfacesImplementedBy(TypeElement el, AnnotationMirror anno, String subtypeOf) {
        if ("java.lang.Object".equals(el.getQualifiedName().toString())) {
            return null;
        }
        for (TypeMirror m : el.getInterfaces()) {
            DeclaredType dt = (DeclaredType) m;
            for (TypeMirror arg : dt.getTypeArguments()) {
                if (utils().isSubtypeOf(arg, subtypeOf).isSubtype()) {
                    return arg;
                }
            }
        }
        TypeMirror sup = el.getSuperclass();
        TypeElement el1 = processingEnv.getElementUtils().getTypeElement(sup.toString());
        if (el1 == null) {
            utils().fail("Could not load type " + sup + " to look type "
                    + "parameter subtype of " + subtypeOf);
            return null;
        }
        return findTypeArgumentOnInterfacesImplementedBy(el1, anno, subtypeOf);
    }

    private static final String REFORMAT_TASK_TYPE = "org.netbeans.modules.editor.indent.spi.ReformatTask";
    private static final String MIME_REG_TYPE = "org.netbeans.api.editor.mimelookup.MimeRegistration";

    private void processFormatterRegistration(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws IOException {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        TypeMirror lexerClass = findTypeArgumentOnInterfacesImplementedBy(type, mirror, ANTLR_LEXER_TYPE);
        TypeMirror enumClass = findTypeArgumentOnInterfacesImplementedBy(type, mirror, java.lang.Enum.class.getName());
        if (lexerClass == null) {
            utils().fail("Could not find lexer class on " + type
                    + " for " + mimeType);
            return;
        }
        if (enumClass == null) {
            utils().fail("Could not find enum class on " + type
                    + " for " + mimeType);
            return;
        }
        String pkg = utils().packageName(type);
        String generatedClassName = type.getSimpleName() + "_" + "ReformatTaskFactory";

        // AntlrFormatterProvider
        /*
String mimeType, Class<StateEnum> enumType, Vocabulary vocab, String[] modeNames,
            Function<CharStream, L> lexerFactory, int... whitespaceTokens
         */
        ParserProxy parser = findParserInfo(roundEnv, mimeType, lexerClass);

        String formatterSignature = simpleName(FORMATTER_TYPE) + "<"
                + simpleName(lexerClass.toString())
                + ","
                + simpleName(enumClass.toString())
                + ">";

        LexerProxy lexer = LexerProxy.create(lexerClass, type, utils());

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .importing(FORMATTER_TYPE, lexer.lexerClassFqn(), enumClass.toString(), "org.antlr.v4.runtime.CharStream",
                        REFORMAT_TASK_TYPE, "org.netbeans.modules.editor.indent.spi.Context",
                        MIME_REG_TYPE
                ).withModifier(PUBLIC, FINAL)
                .implementing(simpleName(REFORMAT_TASK_TYPE) + ".Factory")
                .annotatedWith(simpleName(MIME_REG_TYPE))
                .addStringArgument("mimeType", mimeType)
                .addClassArgument("service", simpleName(REFORMAT_TASK_TYPE) + ".Factory")
                .closeAnnotation()
                .overridePublic("createTask", mb -> {
                    mb.returning(simpleName(REFORMAT_TASK_TYPE))
                            .addArgument("Context", "ctx")
                            .body(bb -> {
                                bb.returningInvocationOf("createFormatter")
                                        .withArgument("ctx").onInvocationOf("get").inScope();
                            });
                })
                .method("get", mb -> {
                    mb.returning(formatterSignature)
                            .body(bb -> {
                                bb.returningInvocationOf("toFormatterProvider")
                                        .withStringLiteral(mimeType)
                                        .withArgument(simpleName(enumClass) + ".class")
                                        .withArgument(simpleName(lexerClass) + ".VOCABULARY")
                                        .withArgument(simpleName(lexerClass) + ".modeNames")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("CharStream", "stream")
                                                    .body(lbb -> {
                                                        lbb.returningInvocationOf("new " + simpleName(lexerClass))
                                                                .withArgument("stream").inScope().endBlock();
                                                    });
                                        })
                                        .withNewArrayArgument("int", ints -> {

                                            List<Integer> white = utils().annotationValues(mirror,
                                                    "whitespaceTokens", Integer.class);
                                            if (white.isEmpty()) {
                                                white = whitespaceTokenListFromSyntaxInfoForMimeType(
                                                        roundEnv, mimeType);
                                            }
                                            Collections.sort(white);

                                            List<String> names = new ArrayList<>();
                                            white.forEach((w) -> {
                                                String constantName = lexer.tokenName(w);
                                                names.add(constantName);
                                                ints.value(lexer.lexerClassSimple() + "." + constantName);
                                            });
                                            if (new HashSet<>(names).size() != names.size()) {
                                                utils().fail("Whitespace token list contains duplicates for either "
                                                        + "language registration or formatter registration: "
                                                        + names, type);
                                            }
                                        })
//                                        .withNewArrayArgument("int", ints -> {
//                                            List<Integer> debug = utils()
//                                                    .annotationValues(mirror,
//                                                            "debugTokens", Integer.class);
//                                            Collections.sort(debug);
//                                            debug.forEach((w) -> {
//                                                ints.number(w);
//                                            });
//                                            ints.closeArray();
//                                        })
                                        .withArgument(parser == null ? "null" : parser.parserClassSimple() + ".ruleNames")
                                        // String[] parserRuleNames, Function<Lexer,RuleNode> rootRuleFinder
                                        /*
                                        .withNewArrayArgument("String", rns -> {
                                            if (parser != null) {
                                                parser.ruleNamesSortedById().forEach((rule) -> {
                                                    rns.stringLiteral(rule);
                                                });
                                            }
                                        })
                                         */
                                        .withLambdaArgument(lam -> {
                                            lam.withArgument("Lexer", "lexer").body(lamb -> {
                                                if (parser != null) {
                                                    int streamChannel = streamChannelForMimeType(mimeType);
                                                    lamb.declare("stream")
                                                            .initializedWith("new CommonTokenStream(lexer, " + streamChannel + ")")
                                                            .as("CommonTokenStream");

                                                    lamb.returningInvocationOf(parser.parserEntryPoint().getSimpleName().toString())
                                                            .onInvocationOf("new " + parser.parserClassSimple())
                                                            .withArgument("stream").inScope();
                                                } else {
                                                    lamb.returning("null");
                                                }
                                            });
                                        })
                                        .on("new " + type.getSimpleName().toString() + "()");
                            });
                });
        if (parser != null) {
            cb.importing("org.antlr.v4.runtime.CommonTokenStream");
            cb.importing(parser.parserClassFqn());
            cb.importing("org.antlr.v4.runtime.Lexer");
        }
        writeOne(cb);
    }

}

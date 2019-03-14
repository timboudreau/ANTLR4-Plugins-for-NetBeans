package org.nemesis.registration;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
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

    private Predicate<AnnotationMirror> annoCheck;
    private Predicate<Element> typeCheck;

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

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (FORMATTER_REGISTRATION_ANNO_TYPE.equals(mirror.getAnnotationType().toString())) {
            if (annoCheck.test(mirror) && typeCheck.test(type)) {
                processFormatterRegistration(type, mirror, roundEnv);
            }
        }
        return false;
    }

    private String findLexerClass(TypeElement el, AnnotationMirror anno, String subtypeOf) {
        if ("java.lang.Object".equals(el.getQualifiedName().toString())) {
            return null;
        }
        System.out.println("TYPE " + el.asType());
        for (TypeMirror m : el.getInterfaces()) {
            System.out.println("IFACE " + m + " kind " + m.getKind());
            DeclaredType dt = (DeclaredType) m;
            System.out.println("TYPE ARGS " + dt.getTypeArguments());
            for (TypeMirror arg : dt.getTypeArguments()) {
                System.out.println(" TEST " + arg);
                if (utils().isSubtypeOf(arg, subtypeOf).isSubtype()) {
                    System.out.println("GOT IT: " + arg);
                    return arg.toString();
                }
            }
        }
        TypeMirror sup = el.getSuperclass();
        TypeElement el1 = processingEnv.getElementUtils().getTypeElement(sup.toString());
        System.out.println("TRY SUPERTYPE " + el1);
        if (el1 == null) {
            utils().fail("Could not load type " + sup + " to look type "
                    + "parameter subtype of " + subtypeOf);
            return null;
        }
        return findLexerClass(el1, anno, subtypeOf);
    }

    private static final String REFORMAT_TASK_TYPE = "org.netbeans.modules.editor.indent.spi.ReformatTask";
    private static final String MIME_REG_TYPE = "org.netbeans.api.editor.mimelookup.MimeRegistration";
    private void processFormatterRegistration(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws IOException {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String lexerClass = findLexerClass(type, mirror, ANTLR_LEXER_TYPE);
        String enumClass = findLexerClass(type, mirror, java.lang.Enum.class.getName());
        System.out.println("LEXER CLASS " + lexerClass);
        System.out.println("ENUM CLASS " + enumClass);
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
        String generatedClassName = type.getSimpleName() + "_" + "Formatter";

        // AntlrFormatterProvider
        /*
String mimeType, Class<StateEnum> enumType, Vocabulary vocab, String[] modeNames,
            Function<CharStream, L> lexerFactory, int... whitespaceTokens
         */
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .importing(FORMATTER_TYPE, lexerClass, enumClass, "org.antlr.v4.runtime.CharStream",
                        REFORMAT_TASK_TYPE, "org.netbeans.modules.editor.indent.spi.Context",
                        MIME_REG_TYPE
                        )
                .withModifier(PUBLIC, FINAL)
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
                .privateMethod("get", mb -> {
                    mb.returning(simpleName(FORMATTER_TYPE))
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
                                            Collections.sort(white);
                                            white.forEach((w) -> {
                                                ints.number(w);
                                            });
//                                            ints.closeArray();
                                        })
                                        .withNewArrayArgument("int", ints -> {
                                            List<Integer> debug = utils()
                                                    .annotationValues(mirror,
                                                            "debugTokens", Integer.class);
                                            Collections.sort(debug);
                                            debug.forEach((w) -> {
                                                ints.number(w);
                                            });
                                            ints.closeArray();
                                        })
                                        .on("new " + type.getSimpleName().toString() + "()");
                            });
                });
        writeOne(cb);
    }

}

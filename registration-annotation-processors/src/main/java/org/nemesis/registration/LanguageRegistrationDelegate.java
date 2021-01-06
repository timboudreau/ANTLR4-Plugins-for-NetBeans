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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.GotoDeclarationProcessor.GOTO_ANNOTATION;
import static org.nemesis.registration.KeybindingsAnnotationProcessor.ANTLR_ACTION_ANNO_TYPE;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.annotation.validation.AnnotationMirrorMemberTestBuilder;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import com.mastfrog.annotation.processor.Key;
import com.mastfrog.java.vogon.ClassBuilder.FieldBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.LinesBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.immutableSetOf;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.processing.Filer;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import org.nemesis.registration.typenames.JdkTypes;
import static org.nemesis.registration.typenames.JdkTypes.ACTION;
import static org.nemesis.registration.typenames.JdkTypes.ARRAYS;
import static org.nemesis.registration.typenames.JdkTypes.ARRAY_LIST;
import static org.nemesis.registration.typenames.JdkTypes.ATOMIC_BOOLEAN;
import static org.nemesis.registration.typenames.JdkTypes.ATOMIC_REFERENCE;
import static org.nemesis.registration.typenames.JdkTypes.BI_CONSUMER;
import static org.nemesis.registration.typenames.JdkTypes.BI_PREDICATE;
import static org.nemesis.registration.typenames.JdkTypes.BOOLEAN_SUPPLIER;
import static org.nemesis.registration.typenames.JdkTypes.CHANGE_LISTENER;
import static org.nemesis.registration.typenames.JdkTypes.COLLECTION;
import static org.nemesis.registration.typenames.JdkTypes.COLLECTIONS;
import static org.nemesis.registration.typenames.JdkTypes.DOCUMENT;
import static org.nemesis.registration.typenames.JdkTypes.EDITOR_KIT;
import static org.nemesis.registration.typenames.JdkTypes.HASH_MAP;
import static org.nemesis.registration.typenames.JdkTypes.HASH_SET;
import static org.nemesis.registration.typenames.JdkTypes.ILLEGAL_ARGUMENT_EXCEPTION;
import static org.nemesis.registration.typenames.JdkTypes.IO_EXCEPTION;
import static org.nemesis.registration.typenames.JdkTypes.LIST;
import static org.nemesis.registration.typenames.JdkTypes.MAP;
import static org.nemesis.registration.typenames.JdkTypes.OPTIONAL;
import static org.nemesis.registration.typenames.JdkTypes.SET;
import static org.nemesis.registration.typenames.JdkTypes.SUPPLIER;
import static org.nemesis.registration.typenames.JdkTypes.SUPPRESS_WARNINGS;
import static org.nemesis.registration.typenames.JdkTypes.TEXT_ACTION;
import static org.nemesis.registration.typenames.JdkTypes.WEAK_HASH_MAP;
import static org.nemesis.registration.typenames.JdkTypes.WEAK_REFERENCE;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.ABSTRACT_ANTLR_LIST_NAVIGATOR_PANEL;
import static org.nemesis.registration.typenames.KnownTypes.ABSTRACT_LOOKUP;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_BINDING;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_BINDINGS;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_ID;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_REFERENCE;
import static org.nemesis.registration.typenames.KnownTypes.ACTION_REFERENCES;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_MIME_TYPE_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_PARSE_RESULT;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_V4_LEXER;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_V4_PARSER;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_V4_TOKEN;
import static org.nemesis.registration.typenames.KnownTypes.BASE_DOCUMENT;
import static org.nemesis.registration.typenames.KnownTypes.BASE_KIT;
import static org.nemesis.registration.typenames.KnownTypes.BUILT_IN_ACTION;
import static org.nemesis.registration.typenames.KnownTypes.CHANGE_SUPPORT;
import static org.nemesis.registration.typenames.KnownTypes.CHAR_STREAM;
import static org.nemesis.registration.typenames.KnownTypes.COMMON_TOKEN_STREAM;
import static org.nemesis.registration.typenames.KnownTypes.COMPLETION_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.CRITERIA;
import static org.nemesis.registration.typenames.KnownTypes.DATA_FOLDER;
import static org.nemesis.registration.typenames.KnownTypes.DATA_OBJECT;
import static org.nemesis.registration.typenames.KnownTypes.DATA_OBJECT_EXISTS_EXCEPTION;
import static org.nemesis.registration.typenames.KnownTypes.DATA_OBJECT_HOOKS;
import static org.nemesis.registration.typenames.KnownTypes.DECLARATION_TOKEN_PROCESSOR;
import static org.nemesis.registration.typenames.KnownTypes.EMBEDDING_HELPER;
import static org.nemesis.registration.typenames.KnownTypes.EMBEDDING_PRESENCE;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTOR;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTOR_BUILDER;
import static org.nemesis.registration.typenames.KnownTypes.EXT_SYNTAX_SUPPORT;
import static org.nemesis.registration.typenames.KnownTypes.FILE_OBJECT;
import static org.nemesis.registration.typenames.KnownTypes.FILE_OWNER_QUERY;
import static org.nemesis.registration.typenames.KnownTypes.GRAMMAR_COMPLETION_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.GRAMMAR_SOURCE;
import static org.nemesis.registration.typenames.KnownTypes.HIGHLIGHTER_KEY_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.INPUT_ATTRIBUTES;
import static org.nemesis.registration.typenames.KnownTypes.INSTANCE_CONTENT;
import static org.nemesis.registration.typenames.KnownTypes.INT_PREDICATES;
import static org.nemesis.registration.typenames.KnownTypes.ITERABLE_TOKEN_SOURCE;
import static org.nemesis.registration.typenames.KnownTypes.KEY;
import static org.nemesis.registration.typenames.KnownTypes.KEYBINDING;
import static org.nemesis.registration.typenames.KnownTypes.KEY_MODIFIERS;
import static org.nemesis.registration.typenames.KnownTypes.LANGUAGE;
import static org.nemesis.registration.typenames.KnownTypes.LANGUAGE_EMBEDDING;
import static org.nemesis.registration.typenames.KnownTypes.LANGUAGE_HIERARCHY;
import static org.nemesis.registration.typenames.KnownTypes.LEXER;
import static org.nemesis.registration.typenames.KnownTypes.LEXER_RESTART_INFO;
import static org.nemesis.registration.typenames.KnownTypes.LEXER_TOKEN;
import static org.nemesis.registration.typenames.KnownTypes.LEXER_TOKEN_ID;
import static org.nemesis.registration.typenames.KnownTypes.LOOKUP;
import static org.nemesis.registration.typenames.KnownTypes.MESSAGES;
import static org.nemesis.registration.typenames.KnownTypes.MIME_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.MIME_RESOLVER;
import static org.nemesis.registration.typenames.KnownTypes.MULTI_DATA_OBJECT;
import static org.nemesis.registration.typenames.KnownTypes.MULTI_FILE_LOADER;
import static org.nemesis.registration.typenames.KnownTypes.MULTI_VIEW_EDITOR_ELEMENT;
import static org.nemesis.registration.typenames.KnownTypes.MULTI_VIEW_ELEMENT;
import static org.nemesis.registration.typenames.KnownTypes.NAVIGATOR_PANEL;
import static org.nemesis.registration.typenames.KnownTypes.NB_ANTLR_UTILS;
import static org.nemesis.registration.typenames.KnownTypes.NB_EDITOR_DOCUMENT;
import static org.nemesis.registration.typenames.KnownTypes.NB_EDITOR_KIT;
import static org.nemesis.registration.typenames.KnownTypes.NB_LEXER_ADAPTER;
import static org.nemesis.registration.typenames.KnownTypes.NB_PARSER_HELPER;
import static org.nemesis.registration.typenames.KnownTypes.NODE;
import static org.nemesis.registration.typenames.KnownTypes.PARSER;
import static org.nemesis.registration.typenames.KnownTypes.PARSER_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.PARSER_RULE_CONTEXT;
import static org.nemesis.registration.typenames.KnownTypes.PARSE_RESULT_CONTENTS;
import static org.nemesis.registration.typenames.KnownTypes.PARSE_TREE;
import static org.nemesis.registration.typenames.KnownTypes.PROJECT;
import static org.nemesis.registration.typenames.KnownTypes.PROXY_LOOKUP;
import static org.nemesis.registration.typenames.KnownTypes.REGIONS_KEY;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.SIMPLE_NAVIGATOR_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.SNAPSHOT;
import static org.nemesis.registration.typenames.KnownTypes.SOURCE;
import static org.nemesis.registration.typenames.KnownTypes.SOURCE_MODIFICATION_EVENT;
import static org.nemesis.registration.typenames.KnownTypes.SYNTAX_ERROR;
import static org.nemesis.registration.typenames.KnownTypes.SYNTAX_SUPPORT;
import static org.nemesis.registration.typenames.KnownTypes.TASK;
import static org.nemesis.registration.typenames.KnownTypes.TASK_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.TERMINAL_NODE;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_CATEGORIZER;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_CATEGORY;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_ID;
import static org.nemesis.registration.typenames.KnownTypes.TOKEN_STREAM;
import static org.nemesis.registration.typenames.KnownTypes.TOP_COMPONENT;
import static org.nemesis.registration.typenames.KnownTypes.UTIL_EXCEPTIONS;
import static org.nemesis.registration.typenames.KnownTypes.VOCABULARY;
import static org.nemesis.registration.typenames.KnownTypes.WEAK_SET;
import org.nemesis.registration.typenames.TypeName;
import org.openide.filesystems.annotations.LayerGenerationException;

/**
 *
 * @author Tim Boudreau
 */
public class LanguageRegistrationDelegate extends LayerGeneratingDelegate {

    public static final int DEFAULT_MODE = 0;
    public static final int MORE = -2;
    public static final int SKIP = -3;
    public static final int DEFAULT_TOKEN_CHANNEL = 0;
    public static final int HIDDEN = 1;
    public static final int MIN_CHAR_VALUE = 0;
    public static final int MAX_CHAR_VALUE = 1114111;
    private BiPredicate<? super AnnotationMirror, ? super Element> mirrorTest;

    static final Key<String> COMMENT_STRING = key(String.class, "comment");
    static final Key<LexerParserTasks> LEXER_PARSER_TASKS = key(LexerParserTasks.class, "lpt");
    static final String TOGGLE_COMMENT_ACTION = "ToggleCommentAction";
    private final LexerParserTasks tasks = new LexerParserTasks();

    private static Map<String, Integer> lexerClassConstants() {
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
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element element) {
        return mirrorTest.test(mirror, element);
    }

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        super.share(LEXER_PARSER_TASKS, tasks);
        mirrorTest = utils.multiAnnotations().whereAnnotationType(REGISTRATION_ANNO, mb -> {
            mb.testMember("name").stringValueMustNotBeEmpty()
                    .stringValueMustBeValidJavaIdentifier()
                    .build()
                    .testMember("mimeType")
                    .addPredicate("Mime type", mir -> {
                        Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                        String value = utils().annotationValue(mir, "mimeType", String.class);
                        return mimeTest.test(value);
                    })
                    .build()
                    .testMember("lexer", lexer -> {
                        lexer.asTypeSpecifier()
                                .hasModifier(PUBLIC)
                                .isSubTypeOf("org.antlr.v4.runtime.Lexer")
                                .build();
                    })
                    .testMemberAsAnnotation("parser", parserMember -> {
                        parserMember.testMember("type").asTypeSpecifier(pType -> {
                            pType.isSubTypeOf("org.antlr.v4.runtime.Parser")
                                    .hasModifier(PUBLIC);
                        }).build()
                                .testMember("entryPointRule")
                                .intValueMustBeGreaterThanOrEqualTo(0)
                                .build()
                                .testMember("parserStreamChannel")
                                .intValueMustBeGreaterThanOrEqualTo(0)
                                .build()
                                .testMember("helper").asTypeSpecifier(helperType -> {
                            helperType.isSubTypeOf("org.nemesis.antlr.spi.language.NbParserHelper")
                                    .doesNotHaveModifier(PRIVATE)
                                    .mustHavePublicNoArgConstructor();
                        }).build();
                    })
                    .testMemberAsAnnotation("categories", cb -> {
                        cb.testMember("tokenIds")
                                .intValueMustBeGreaterThan(0).build();
                        cb.testMemberAsAnnotation("colors", colors -> {
                            Consumer<AnnotationMirrorMemberTestBuilder<?>> c = ammtb -> {
                                ammtb.arrayValueSizeMustEqualOneOf(
                                        "Color arrays must be empty, or be RGB "
                                        + "or RGBA values from 0-255", 0, 3, 4)
                                        .intValueMustBeLessThanOrEqualTo(255)
                                        .intValueMustBeGreaterThanOrEqualTo(0);
                            };
                            for (String s : new String[]{"fg", "bg", "underline", "waveUnderline"}) {
                                colors.testMember(s, c);
                            }
                            colors.testMember("themes")
                                    .stringValueMustNotContain('/').build();
                        });
                    })
                    .testMemberAsAnnotation("syntax", stb -> {
                        stb.testMember("hooks")
                                .asType(hooksTypes -> {
                                    hooksTypes.isAssignable(
                                            "org.nemesis.antlr.spi.language.DataObjectHooks")
                                            .nestingKindMustNotBe(NestingKind.LOCAL)
                                            .build();
                                })
                                .valueAsTypeElement()
                                .mustHavePublicNoArgConstructor()
                                .testContainingClasses(cc -> {
                                    cc.nestingKindMayNotBe(NestingKind.LOCAL)
                                            .doesNotHaveModifier(PRIVATE);
                                })
                                .build()
                                .build();
                    }).testMemberAsAnnotation("genericCodeCompletion", gcc -> {
                gcc.testMember("ignoreTokens")
                        .intValueMustBeGreaterThan(0).build();
            });
            ;
        }).build();
    }

    private final Map<String, Set<VariableElement>> gotos = new HashMap<>();

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (GOTO_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            String mime = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
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

    private List<Element> findAntlrActionElements(RoundEnvironment env, String mimeType) {
        Set<Element> all = utils().findAnnotatedElements(env, ANTLR_ACTION_ANNO_TYPE);
        Map<AnnotationMirror, Element> result = new HashMap<>();
        for (Element el : all) {
            AnnotationMirror mir = utils().findAnnotationMirror(el, ANTLR_ACTION_ANNO_TYPE);
            String mime = cleanMimeType(utils().annotationValue(mir, "mimeType", String.class));
            if (mimeType.equals(mime)) {
                result.put(mir, el);
            }
        }
        List<Map.Entry<AnnotationMirror, Element>> l = new ArrayList<>(result.entrySet());

        Collections.sort(l, (a, b) -> {
            Integer first = utils().annotationValue(a.getKey(), "order", Integer.class);
            Integer second = utils().annotationValue(b.getKey(), "order", Integer.class);
            return first == null || second == null ? 0 : first.compareTo(second);
        });
        List<Element> res = new ArrayList<>(l.size());
        for (Map.Entry<AnnotationMirror, Element> e : l) {
            res.add(e.getValue());
        }
        return res;
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        // System.err is the only way for output to actually escape Maven's
        // swallowing of javac output, so we can debug processor failures
        try {
            return doProcessTypeAnnotation(type, mirror, roundEnv);
        } catch (Exception | Error ex) {
            ex.printStackTrace(System.err);
            return Exceptions.chuck(ex);
        }
    }

    protected boolean doProcessTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (ANTLR_ACTION_ANNO_TYPE.equals(mirror.getAnnotationType().toString())) {
            return false;
        }
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
//        String prefix = utils().annotationValue(mirror, "name", String.class);
        LexerProxy lexerProxy = LexerProxy.create(mirror, type, utils());
        if (lexerProxy == null) {
            utils().warn("Could not find Lexer info for " + mimeType + " on " + type.getQualifiedName(), type);
            return true;
        }
        boolean useImplicit = utils().annotationValue(mirror, "useImplicitLanguageNameFromLexerName", Boolean.class, Boolean.FALSE);
        String prefix = NameAndMimeTypeUtils.prefixFromMimeTypeOrLexerName(mimeType, useImplicit ? lexerProxy.lexerClassFqn() : null);
        mimeType = cleanMimeType(mimeType);
        preemptivelyCheckGotos(roundEnv);
        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");

        AnnotationMirror parserHelper = utils().annotationValue(mirror, "parser", AnnotationMirror.class);
        ParserProxy parser = null;
        String parserClassName = null;
        if (parserHelper != null) {
            parser = createParserProxy(parserHelper, utils());
            if (parser == null) {
                utils().warn("Could not find parser info for " + mimeType + " on " + type.getQualifiedName()
                        + " will generate basic syntax support and no more.",
                        type);
                generateTokensClasses(mimeType, type, mirror, lexerProxy, tokenCategorizerClass, prefix, parser);
                return true;
            }
            parserClassName = generateParserClasses(mimeType, parser, lexerProxy, type, mirror, parserHelper, prefix);
            writeOne(parser.createRuleTypeMapper(utils().packageName(type), prefix, mimeType));
        }
        tasks.set(mimeType, lexerProxy, parser, prefix, utils().packageName(type));
        generateRegistration(prefix, mirror, mimeType, type, lexerProxy, parser, parserClassName);

        generateTokensClasses(mimeType, type, mirror, lexerProxy, tokenCategorizerClass, prefix, parser);

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
                generateDataObjectClassAndRegistration(mimeType, fileInfo, extension, mirror, prefix, type, parser, lexerProxy, roundEnv);
            }
        }
        AnnotationMirror codeCompletion = utils().annotationValue(mirror, "genericCodeCompletion", AnnotationMirror.class);
        if (codeCompletion != null) {
            generateCodeCompletion(prefix, mirror, codeCompletion, type, mimeType, lexerProxy, parser);
        }
        // Returning true could stop LanguageFontsColorsProcessor from being run
        return false;
    }

    private static final String ANTLR_MIME_REG_TYPE = "org.nemesis.antlr.spi.language.AntlrMimeTypeRegistration";

    private void generateRegistration(String prefix, AnnotationMirror mirror, String mimeType, TypeElement on, LexerProxy lexer, ParserProxy parser, String nbParserClassName) throws IOException {
        String generatedClassName = prefix + "AntlrLanguageRegistration";
        String regSimple = simpleName(ANTLR_MIME_REG_TYPE);
        ClassBuilder<String> cb = ClassBuilder.forPackage(utils().packageName(on))
                .named(generatedClassName)
                .importing(ANTLR_MIME_TYPE_REGISTRATION.qname(), SERVICE_PROVIDER.qname(),
                        ANTLR_V4_LEXER.qname(), lexer.lexerClassFqn())
                .conditionally(parser != null, cbb -> {
                    cbb.importing(parser.parserClassFqn())
                            .importing(ANTLR_V4_PARSER.qname());
                })
                .extending(ANTLR_MIME_TYPE_REGISTRATION.simpleName())
                .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                    // should do *something* to provide ordering - hash code will do
                    ab.addClassArgument("service", regSimple)
                            .addArgument("position", Math.abs(generatedClassName.hashCode()));
                })
                .withModifier(PUBLIC, FINAL)
                .docComment("Generated from ", mirror.getAnnotationType().toString(),
                        " annotation on ", on.getQualifiedName(), " for MIME type ",
                        mimeType, " by " + getClass().getSimpleName())
                .constructor(con -> {
                    con.setModifier(PUBLIC)
                            .body(bb -> {
                                bb.invoke("super")
                                        .withStringLiteral(mimeType)
                                        .withArgument(lexer.lexerClassFqn() + ".VOCABULARY")
                                        .withArgument(nbParserClassName == null ? "null" : nbParserClassName + ".HELPER")
                                        .inScope();
                            });
                }).method("lexerType", mb -> {
            mb.returning("Class<? extends " + ANTLR_V4_LEXER.simpleName() + ">")
                    .withModifier(PUBLIC)
                    .bodyReturning(lexer.lexerClassSimple() + ".class");
            ;
        }).method("parserType", mb -> {
            mb.withModifier(PUBLIC).returning("Class<? extends Parser>")
                    .body(bb -> {
                        if (parser == null) {
                            bb.returningNull();
                        } else {
                            bb.returning(parser.parserClassSimple() + ".class");
                        }
                    });
        });
        writeOne(cb);
    }

    private void generateCodeCompletion(String prefix, AnnotationMirror mirror,
            AnnotationMirror codeCompletion, TypeElement on, String mimeType, LexerProxy lexer,
            ParserProxy parser) throws IOException {
        String generatedClassName = prefix + "GenericCodeCompletion";
        List<AnnotationMirror> list = utils().annotationValues(codeCompletion, "tokenCompletions", AnnotationMirror.class);
        // In case of duplicates, use a map
        IntMap<String> tokenCompletions = CollectionUtils.intMap(Math.max(1, list.size()));
        for (AnnotationMirror am : list) {
            Integer tokenId = utils().annotationValue(am, "tokenId", Integer.class);
            String text = utils().annotationValue(am, "text", String.class);
            if (tokenId != null && text != null) {
                if (tokenCompletions.containsKey(tokenId.intValue())) { // broken source
                    utils().fail("The token id " + tokenId
                            + "(" + lexer.tokenName(tokenId) + ")"
                            + " appears more than once in "
                            + "supplementary token completions of " + codeCompletion);
                    return;
                }
                tokenCompletions.put(tokenId.intValue(), text);
            }
        }
        List<AnnotationMirror> substs = utils().annotationValues(codeCompletion, "ruleSubstitutions", AnnotationMirror.class);
        IntMap<Integer> ruleSubstitutions = CollectionUtils.intMap(Math.max(1, substs.size()));
        for (AnnotationMirror am : substs) {
            Integer ruleId = utils().annotationValue(am, "complete", Integer.class);
            Integer with = utils().annotationValue(am, "withCompletionsOf", Integer.class);
            if (ruleId != null && with != null) { // broken source
                if (ruleSubstitutions.containsKey(ruleId.intValue())) {
                    utils().fail("The rule id " + ruleId
                            + "(" + parser.nameForRule(ruleId) + ")"
                            + " appears more than once in "
                            + "supplementary rule substitutions of " + codeCompletion);
                }
                ruleSubstitutions.put(ruleId, with);
            }
        }

        ClassBuilder<String> cb = ClassBuilder.forPackage(utils().packageName(on)).named(generatedClassName)
                .importing(lexer.lexerClassFqn(), parser.parserClassFqn(),
                        IO_EXCEPTION.qname(),
                        DOCUMENT.qname(),
                        COMMON_TOKEN_STREAM.qname(),
                        TOKEN_STREAM.qname(),
                        ANTLR_V4_PARSER.qname(),
                        GRAMMAR_COMPLETION_PROVIDER.qname(),
                        GRAMMAR_SOURCE.qname(),
                        MIME_REGISTRATION.qname(),
                        COMPLETION_PROVIDER.qname(),
                        parser.parserClassFqn(),
                        CRITERIA.qname()
                ).extending(GRAMMAR_COMPLETION_PROVIDER.simpleName())
                .annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                    ab.addArgument("mimeType", mimeType)
                            .addClassArgument("service", COMPLETION_PROVIDER.simpleName());
                })
                .withModifier(PUBLIC, FINAL)
                .conditionally(utils().annotationValues(codeCompletion, "preferredRules", Integer.class).isEmpty()
                        || utils().annotationValues(codeCompletion, "ignoreTokens", Integer.class).isEmpty(), cbb -> {
                    cbb.importing(INT_PREDICATES.qname());
                })
                .staticImport(lexer.lexerClassFqn() + ".*")
                //                .generateDebugLogCode()
                .privateMethod("createParser", mb -> {
                    mb.withModifier(STATIC)
                            .addArgument(DOCUMENT.simpleName(), "doc")
                            .returning(ANTLR_V4_PARSER.simpleName())
                            .throwing(IO_EXCEPTION.simpleName())
                            .body(bb -> {
                                bb.declare("lexer")
                                        .initializedByInvoking("createAntlrLexer")
                                        .withArgumentFromInvoking("stream")
                                        .onInvocationOf("find")
                                        .withArgument("doc")
                                        .withStringLiteral(mimeType)
                                        .on(GRAMMAR_SOURCE.simpleName())
                                        .on(prefix + "Hierarchy").as(lexer.lexerClassSimple());
                                bb.declare("stream").initializedWithNew(nb -> {
                                    int streamChannel = 0;
                                    AnnotationMirror parserInfo = utils()
                                            .annotationValue(mirror, "parser", AnnotationMirror.class);
                                    if (parserInfo != null) {
                                        streamChannel = utils().annotationValue(parserInfo, "parserStreamChannel", Integer.class, 0);
                                    }
                                    if (streamChannel != 0) {
                                        nb.withArgument("lexer").withArgument(streamChannel)
                                                .ofType(COMMON_TOKEN_STREAM.simpleName());
                                    } else {
                                        nb.withArgument("lexer").ofType(COMMON_TOKEN_STREAM.simpleName());
                                    }
//                                    nb.withArgument(parser.parserClassFqn()+"::"+parser.parserEntryPoint().getSimpleName());
                                }).as(TOKEN_STREAM.simpleName());
//
//                                bb.declare("stream").initializedByInvoking("new CommonTokenStream")
//                                        .withArgument("lexer").withArgument(streamChannel)
//                                        .inScope().as("CommonTokenStream");
//
                                bb.declare("parser").initializedWithNew(nb -> {
                                    nb.withArgument("stream").ofType(parser.parserClassSimple());
                                }).as(parser.parserClassSimple());

                                // XXX fix impoerative NewBuilder - this is wrong
//                                bb.declare("parser")
//                                        .initializedWithNew(parser.parserClassSimple())
//                                        .withArgument("stream")
//                                        .inScope();
                                bb.invoke("removeErrorListeners").on("parser");
//                                bb.returningInvocationOf("new " + parser.parserClassSimple()).withArgument("stream").inScope();
                                bb.returning("parser");
                            });
                })
                .field("CRITERIA", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedFromInvocationOf("forVocabulary")
                            .withArgument("VOCABULARY")
                            .on(CRITERIA.simpleName()).ofType(CRITERIA.simpleName());
                })
                .constructor(con -> {
                    con.setModifier(PUBLIC).body(bb -> {
                        Consumer<InvocationBuilder<?>> c = sup -> {
                            sup = sup.withStringLiteral(mimeType);
                            sup = sup.withArgument(generatedClassName + "::createParser");
                            Set<Integer> preferredRules = new TreeSet<>(utils().annotationValues(codeCompletion, "preferredRules", Integer.class));
                            if (!preferredRules.isEmpty()) {
                                // Criteria.rulesMatchPredicate(ANTLRv4Parser.ruleNames,
                                sup = sup.withArgumentFromInvoking("rulesMatchPredicate", ib -> {
                                    ib.withArgument().field("ruleNames").of(parser.parserClassSimple());
                                    for (Integer i : preferredRules) {
                                        String fieldName
                                                = parser.ruleFieldForRuleId(i.intValue());
                                        if (fieldName == null) {
                                            utils().fail("No parser rule exists for " + i);
                                            return;
                                        }
                                        String fieldQual
                                                = parser.parserClassSimple() + "." + parser.ruleFieldForRuleId(i.intValue());
                                        ib = ib.withArgument(fieldQual);
                                    }
                                    ib.on(CRITERIA.simpleName());
                                });
                            } else {
                                sup = sup.withArgumentFromInvoking("alwaysFalse", ib -> {
                                    ib.on(INT_PREDICATES.simpleName());
                                });
                            }
                            Set<Integer> ignore = new TreeSet<>(utils().annotationValues(codeCompletion, "ignoreTokens", Integer.class));
                            if (!ignore.isEmpty()) {
                                sup.withArgumentFromInvoking("anyOf", ib -> {
                                    for (Integer i : ignore) {
                                        ib = ib.withArgument(lexer.lexerClassSimple()
                                                + "." + lexer.tokenName(i));
                                    }
                                    ib.on("CRITERIA");
                                });
                            } else {
                                sup.withArgumentFromInvoking("alwaysFalse", ib -> {
                                    ib.on(INT_PREDICATES.simpleName());
                                });
                            }
                            sup.withNewArrayArgument("int", (ClassBuilder.ArrayValueBuilder<?> avb) -> {
                                int[] keys = tokenCompletions.keysArray();
                                for (int i = 0; i < keys.length; i++) {
                                    avb.expression(lexer.tokenFieldReference(keys[i]));
//                                    avb.number(keys[i]);
                                }
                            });
                            sup.withNewArrayArgument("String", (ClassBuilder.ArrayValueBuilder<?> avb) -> {
                                Object[] vals = tokenCompletions.valuesArray();
                                for (int i = 0; i < vals.length; i++) {
                                    avb.literal((String) vals[i]);
                                }
                            });
                            sup.withNewArrayArgument("int", (ClassBuilder.ArrayValueBuilder<?> avb) -> {
                                int[] keys = ruleSubstitutions.keysArray();
                                for (int i = 0; i < keys.length; i++) {
//                                    avb.number(keys[i]);
                                    avb.expression(parser.parserClassSimple() + "." + parser.ruleFieldForRuleId(keys[i]));
                                }
                            });
                            sup.withNewArrayArgument("int", (ClassBuilder.ArrayValueBuilder<?> avb) -> {
                                Object[] vals = ruleSubstitutions.valuesArray();
                                for (int i = 0; i < vals.length; i++) {
                                    avb.expression(parser.parserClassSimple() + "." + parser.ruleFieldForRuleId((Integer) vals[i]));
//                                    avb.number((Integer) vals[i]);
                                }
                            });
                        };
                        InvocationBuilder<?> ib = bb.invoke("super");
                        c.accept(ib);
                        ib.inScope();

//                        bb.invoke("super")
//                                .withArgument(generatedClassName + "::createParser")
//                                .withArgumentFromInvoking("anyOf", ib -> {
//                                    Set<Integer> preferredRules = new TreeSet<>(utils().annotationValues(codeCompletion, "preferredRules", Integer.class));
//                                    if (preferredRules.isEmpty()) {
//
//                                    }
//                                    for (Integer i : preferredRules) {
//                                        String fieldName = parser.ruleFieldForRuleId(i.intValue());
//                                        String fieldQual = parser.parserClassSimple() + "." + fieldName;
//                                        ib.withArgument(fieldQual);
//                                    }
//                                    ib.on(INT_PREDICATES.simpleName());
//                                })
//                                .withArgumentFromInvoking("anyOf", ib -> {
//                                    Set<Integer> ignore = new TreeSet<>(utils().annotationValues(codeCompletion, "ignoreTokens", Integer.class));
//                                    for (Integer i : ignore) {
//                                        ib.withArgument(lexer.lexerClassSimple() + "." + lexer.tokenName(i));
//                                    }
//                                    ib.on("CRITERIA");
//                                })
//                                .inScope();
                    });
                });
        writeOne(cb);
    }

    static ParserProxy createParserProxy(AnnotationMirror parserHelper, AnnotationUtils utils) {
        int entryPointRuleId = utils.annotationValue(parserHelper, "entryPointRule", Integer.class, Integer.MIN_VALUE);
        TypeMirror parserClass = utils.typeForSingleClassAnnotationMember(parserHelper, "type");
        return ParserProxy.create(entryPointRuleId, parserClass, utils);
    }

    private static int dataObjectClassCount = 0;

    // DataObject generation
    private void generateDataObjectClassAndRegistration(String mimeType, AnnotationMirror fileInfo, String extension, AnnotationMirror mirror, String prefix, TypeElement type, ParserProxy parser, LexerProxy lexer, RoundEnvironment roundEnv) throws Exception {
        dataObjectClassCount++;
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
        // Leave DataObject subclass non-final for subclassing to programmatically
        // register it in tests, in order to be able to create a fake DataEditorSupport.Env
        // over it
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage).named(dataObjectClassName)
                .importing(
                        ACTION_ID.qname(),
                        ACTION_REFERENCE.qname(),
                        ACTION_REFERENCES.qname(),
                        FILE_OBJECT.qname(),
                        MIME_RESOLVER.qname(),
                        DATA_OBJECT.qname(),
                        DATA_OBJECT_EXISTS_EXCEPTION.qname(),
                        MULTI_DATA_OBJECT.qname(),
                        MULTI_FILE_LOADER.qname(),
                        LOOKUP.qname(),
                        MESSAGES.qname()
                //                        "javax.annotation.processing.Generated",
                ).staticImport(dataObjectFqn + ".ACTION_PATH")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending(MULTI_DATA_OBJECT.simpleName())
                .withModifier(PUBLIC)/* .withModifier(FINAL) */
                .field("ACTION_PATH").withModifier(STATIC).withModifier(PUBLIC)
                .withModifier(FINAL)
                .initializedWith(actionPath) //                    .initializedTo(
                //                        LinesBuilder.stringLiteral(actionPath)).ofType(STRING)
                ;

        List<String> hooksClass = utils().typeList(fileInfo, "hooks", DATA_OBJECT_HOOKS.qnameNotouch());
        Set<String> hooksMethods
                = hooksClass.isEmpty()
                ? Collections.emptySet()
                : generateHookMethods(fileInfo, type, hooksClass.get(0), cl);

        if (multiview) {
            cl.importing(MULTI_VIEW_ELEMENT.qname(),
                    MULTI_VIEW_EDITOR_ELEMENT.qname(),
                    TOP_COMPONENT.qname()
            );
//            cl.importing("org.netbeans.core.spi.multiview.MultiViewElement",
//                    "org.netbeans.core.spi.multiview.text.MultiViewEditorElement",
//                    "org.openide.windows.TopComponent"
//            );
        }
        addActionAnnotations(cl, fileInfo);
        String msgName = "LBL_" + prefix + "_LOADER";
        String sourceTabName = multiview ? "LBL_" + prefix + "_SOURCE_TAB" : null;
        cl.annotatedWith("Messages", ab -> {
            String msgValue = msgName + "=" + prefix + " files";
            if (sourceTabName != null) {
                ab.addArrayArgument("value", arr -> {
                    arr.literal(msgValue).literal(sourceTabName + "=" + prefix + " Source");
                });
            } else {
                ab.addArgument("value", msgValue);
            }
        });
//        cl.annotatedWith("Messages").addArgument("value", msgName + "=" + prefix + " files\n" + sourceTabName + "=" + prefix + " Source").closeAnnotation();
        cl.annotatedWith(DATA_OBJECT.simpleName() + ".Registration", annoBuilder -> {
            annoBuilder.addArgument("mimeType", mimeType)
                    .addArgument("iconBase", iconBase)
                    .addArgument("displayName", "#" + msgName) // xxx localize
                    .addArgument("position", 1536 + dataObjectClassCount)
                    .closeAnnotation();
        });
        cl.annotatedWith(MIME_RESOLVER.simpleName() + ".ExtensionRegistration", ab -> {
            ab.addArgument("displayName", "#" + msgName)
                    .addArgument("mimeType", mimeType)
                    .addArgument("extension", extension)
                    .addArgument("position", 1536 + dataObjectClassCount)
                    .closeAnnotation();
        });
        cl.constructor(cb -> {
            cb.setModifier(PUBLIC)
                    .addArgument(FILE_OBJECT.simpleName(), "pf")
                    .addArgument(MULTI_FILE_LOADER.simpleName(), "loader")
                    .throwing(DATA_OBJECT_EXISTS_EXCEPTION.simpleName())
                    .body(bb -> {
                        bb.invoke("super").withArgument("pf").withArgument("loader").inScope();
                        bb.log(Level.FINE).stringLiteral(dataObjectClassName)
                                .argument("pf.getPath()")
                                .logging("Create a new {0} for {1}");
//                    bb.statement("if (pf.getPath().startsWith(\"Editors\")) new Exception(pf.getPath()).printStackTrace(System.out)");
                        bb.invoke("registerEditor").withStringLiteral(mimeType)
                                .withArgument(multiview)
                                .inScope();
                        if (hooksMethods.contains("notifyCreated")) {
                            cb.annotatedWith(SUPPRESS_WARNINGS.simpleName())
                                    .addArgument("value", "LeakingThisInConstructor")
                                    .closeAnnotation();
                            bb.invoke("notifyCreated").withArgument("this").on("HOOKS");
                        }
                        bb.endBlock();
                    });
        });
        cl.override("associateLookup").returning("int").withModifier(PROTECTED).body().returning("1").endBlock();

        if (multiview) {
            cl.method("createEditor").addArgument(LOOKUP.simpleName(), "lkp").withModifier(PUBLIC).withModifier(STATIC)
                    .annotatedWith(MULTI_VIEW_ELEMENT.simpleName() + ".Registration", ab -> {
                        ab.addArgument("displayName", '#' + sourceTabName)
                                .addArgument("iconBase", iconBase)
                                .addArgument("mimeType", mimeType)
                                .addExpressionArgument("persistenceType", "TopComponent.PERSISTENCE_ONLY_OPENED")
                                .addArgument("preferredID", prefix)
                                .addArgument("position", 1000).closeAnnotation();
                    })
                    .returning(MULTI_VIEW_EDITOR_ELEMENT.simpleName())
                    .body()
                    .log("Create editor for", Level.FINER, "lkp.lookup(DataObject.class)")
                    .returningNew().withArgument("lkp").ofType(MULTI_VIEW_EDITOR_ELEMENT.simpleName());
        }

        for (String s : new String[]{"copyAllowed", "renameAllowed", "moveAllowed", "deleteAllowed"}) {
            Boolean val = utils().annotationValue(fileInfo, s, Boolean.class);
            if (val != null) {
                createFoMethod(cl, s, val);
            }
        }
        String editorKitFqn = generateEditorKitClassAndRegistration(dataObjectPackage, mirror, prefix, type, mimeType, parser, lexer, roundEnv);
        cl.importing(EDITOR_KIT.qname())
                .method("createEditorKit", mb -> {
                    mb.returning(EDITOR_KIT.simpleName());
                    mb.addArgument(FILE_OBJECT.simpleName(), "file")
                            .withModifier(STATIC)
                            .body(bb -> {
                                bb.log(Level.FINER)
                                        .argument("file.getPath()")
                                        .stringLiteral(simpleName(editorKitFqn))
                                        .logging("Fetch editor kit {1} for {0}");
                                bb.returning(simpleName(editorKitFqn) + ".INSTANCE").endBlock();
                            });
                });
        withLayer(layer -> {
            String editorKitFile = "Editors/" + mimeType + "/" + editorKitFqn.replace('.', '-') + ".instance";
            layer(type).file(editorKitFile)
                    .methodvalue("instanceCreate", dataObjectFqn, "createEditorKit")
                    .stringvalue("instanceOf", EDITOR_KIT.qname() + "," + editorKitFqn + ','
                            + NB_EDITOR_KIT.qname() + ',' + BASE_KIT.qname())
                    .write();

            layer.folder("Actions/" + mimeType)
                    .stringvalue("displayName", prefix).write();
        }, type);
        writeOne(cl);
    }

    static final Set<String> EXPECTED_HOOK_METHODS = immutableSetOf(
            "notifyCreated",
            "decorateLookup",
            "createNodeDelegate",
            "handleDelete",
            "handleRename",
            "handleCopy",
            "handleMove",
            "handleCreateFromTemplate",
            "handleCopyRename");

    static final Consumer<ClassBuilder<?>> hookMethodGenerator(String hookMethod) {
        switch (hookMethod) {
            case "notifyCreated":
                return cb -> {
                };
            case "decorateLookup":
                return decorateLookupInvoker;
            case "createNodeDelegate":
                return createNodeDelegateInvoker;
            case "handleDelete":
                return handleDeleteInvoker;
            case "handleRename":
                return handleRenameInvoker;
            case "handleCopy":
                return handleCopyInvoker;
            case "handleMove":
                return handleMoveInvoker;
            case "handleCreateFromTemplate":
                return handleCreateFromTemplateInvoker;
            case "handleCopyRename":
                return handleCopyRenameInvoker;
            default:
                throw new AssertionError("Not a hook method: " + hookMethod);
        }
    }

    private static final Consumer<ClassBuilder<?>> decorateLookupInvoker = (cb) -> {
        cb.importing(LOOKUP.qname(), DATA_OBJECT.qname(),
                SUPPLIER.qname(), INSTANCE_CONTENT.qname(),
                ABSTRACT_LOOKUP.qname(), PROXY_LOOKUP.qname())
                //                .field("lookupContent").withModifier(PRIVATE, FINAL).initializedTo("new InstanceContent()").ofType("InstanceContent")
                .field("lookupContent").withModifier(PRIVATE, FINAL).initializedWithNew(nb -> {
            nb.ofType(INSTANCE_CONTENT.simpleName());
        }).ofType(INSTANCE_CONTENT.simpleName())
                //                .field("contributedLookup").withModifier(PRIVATE, FINAL).initializedTo("new AbstractLookup(lookupContent)").ofType("Lookup")
                .field("contributedLookup").withModifier(PRIVATE, FINAL).initializedWithNew(nb -> {
            nb.withArgument("lookupContent").ofType(ABSTRACT_LOOKUP.simpleName());
        }).ofType(LOOKUP.simpleName())
                .field("lkp").withModifier(PRIVATE).ofType(LOOKUP.simpleName())
                .overridePublic("getLookup").returning(LOOKUP.simpleName())
                .body(bb -> {
                    bb.synchronizeOn("lookupContent", sbb -> {
                        sbb.ifNotNull("lkp").returning("lkp").endIf();
                        sbb.declare("superLookup").initializedByInvoking("getLookup")
                                .onInvocationOf("getCookieSet").inScope().as("Lookup");
                        sbb.invoke("decorateLookup")
                                .withArgument("this")
                                .withArgument("lookupContent")
                                .withLambdaArgument().body().returning("superLookup").endBlock()
                                .on("HOOKS");
                        sbb.assign("lkp").toNewInstance(nb -> {
                            nb.withArgument("contributedLookup")
                                    .withArgument("superLookup")
                                    .ofType(PROXY_LOOKUP.simpleName());
                        });
                        sbb.returning("lkp");
                    });
                });
    };

    private static final Consumer<ClassBuilder<?>> createNodeDelegateInvoker = cb -> {
        cb.importing(NODE.qname())
                .overrideProtected("createNodeDelegate", mb -> {
                    mb.returning(NODE.simpleName())
                            .body(bb -> {
                                bb.returningInvocationOf("createNodeDelegate")
                                        .withArgument("this")
                                        .withLambdaArgument().body()
                                        .returningInvocationOf("createNodeDelegate").on("super").endBlock()
                                        .on("HOOKS");
                            });
                });
    };

    private static final Consumer<ClassBuilder<?>> handleDeleteInvoker = cb -> {
        cb.importing(IO_EXCEPTION.qname());
        cb.overrideProtected("handleDelete", mb -> {
            mb.throwing(IO_EXCEPTION.simpleName()).body(bb -> {
                bb.invoke("handleDelete")
                        .withArgument("this")
                        .withLambdaArgument().body()
                        .invoke("handleDelete").on("super").endBlock()
                        .on("HOOKS");
            });
        });
    };

    private static final Consumer<ClassBuilder<?>> handleRenameInvoker = cb -> {
        cb.importing(FILE_OBJECT.qname(), IO_EXCEPTION.qname())
                .overrideProtected("handleRename")
                .addArgument("String", "name")
                .returning(FILE_OBJECT.simpleName())
                .throwing(IO_EXCEPTION.simpleName())
                .body(bb -> {
                    bb.returningInvocationOf("handleRename")
                            .withArgument("this")
                            .withArgument("name")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("String", "newName")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleRename")
                                                    .withArgument("newName")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
    };

    private static final Consumer<ClassBuilder<?>> handleCopyInvoker = cb -> {
        cb.importing(
                DATA_FOLDER.qname(),
                FILE_OBJECT.qname(),
                DATA_OBJECT.qname(),
                IO_EXCEPTION.qname()
        ).overrideProtected("handleCopy")
                .throwing(IO_EXCEPTION.simpleName())
                .addArgument(DATA_FOLDER.simpleName(), "fld")
                .returning(DATA_OBJECT.simpleName())
                .throwing(IO_EXCEPTION.simpleName())
                .body(bb -> {
                    bb.returningInvocationOf("handleCopy")
                            .withArgument("this")
                            .withArgument("fld")
                            .withLambdaArgument(lb -> {
                                lb.withArgument(DATA_FOLDER.simpleName(), "newFld")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCopy")
                                                    .withArgument("newFld")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleMoveInvoker = cb -> {
        cb.importing(
                IO_EXCEPTION.qname(),
                DATA_FOLDER.qname(),
                DATA_OBJECT.qname(),
                FILE_OBJECT.qname())
                .overrideProtected("handleMove")
                .returning(FILE_OBJECT.simpleName())
                .addArgument(DATA_FOLDER.simpleName(), "target")
                .throwing(IO_EXCEPTION.simpleName())
                .body(bb -> {
                    bb.returningInvocationOf("handleMove")
                            .withArgument("this")
                            .withArgument("target")
                            .withLambdaArgument(lb -> {
                                lb.withArgument(DATA_FOLDER.simpleName(), "newTarget")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleMove")
                                                    .withArgument("newTarget")
                                                    .on("super");
                                        });

                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleCreateFromTemplateInvoker = cb -> {
        cb.importing(
                IO_EXCEPTION.qname(),
                DATA_FOLDER.qname(),
                DATA_OBJECT.qname())
                .overrideProtected("handleCreateFromTemplate")
                .addArgument(DATA_FOLDER.simpleName(), "in")
                .addArgument("String", "name")
                .returning(DATA_OBJECT.simpleName())
                .throwing(IO_EXCEPTION.simpleName())
                .body(bb -> {
                    bb.returningInvocationOf("handleCreateFromTemplate")
                            .withArgument("this")
                            .withArgument("in")
                            .withArgument("name")
                            .withLambdaArgument(lb -> {
                                lb.withArgument(DATA_FOLDER.simpleName(), "newIn")
                                        .withArgument("String", "newName")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCreateFromTemplate")
                                                    .withArgument("newIn")
                                                    .withArgument("newName")
                                                    .on("super");
                                        });
                            })
                            .on("HOOKS");
                });
        ;
    };

    private static final Consumer<ClassBuilder<?>> handleCopyRenameInvoker = cb -> {
        cb.importing(
                IO_EXCEPTION.qname(), DATA_FOLDER.qname(), DATA_OBJECT.qname())
                .overrideProtected("handleCopyRename")
                .addArgument(DATA_FOLDER.simpleName(), "in")
                .addArgument("String", "name")
                .addArgument("String", "ext")
                .returning(DATA_OBJECT.simpleName())
                .throwing(IO_EXCEPTION.simpleName())
                .body(bb -> {
                    bb.returningInvocationOf("handleCopyRename")
                            .withArgument("this")
                            .withArgument("in")
                            .withArgument("name")
                            .withArgument("ext")
                            .withLambdaArgument(lb -> {
                                lb.withArgument(DATA_FOLDER.simpleName(), "newIn")
                                        .withArgument("String", "newName")
                                        .withArgument("String", "newExt")
                                        .body(lbb -> {
                                            lbb.returningInvocationOf("handleCopyRename")
                                                    .withArgument("newIn")
                                                    .withArgument("newName")
                                                    .withArgument("newExt")
                                                    .on("super");
                                        });
                                ;
                            })
                            .on("HOOKS");
                });
    };

    private Set<String> findMethods(TypeElement type, AnnotationMirror fileInfo, String hooksClassFqn) {
        TypeElement te = processingEnv.getElementUtils().getTypeElement(hooksClassFqn);
        if (te == null) {
            utils().fail("Could not find " + hooksClassFqn + " on classpath", type, fileInfo);
        }
        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> names = new HashSet<>();
        boolean foundReachableConstructor = false;
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                ExecutableElement ee = (ExecutableElement) e;
                if (EXPECTED_HOOK_METHODS.contains(ee.getSimpleName().toString())) {
                    names.add(ee.getSimpleName().toString());
                    methods.add(ee);
                }
            } else if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement con = (ExecutableElement) e;
                if (con.getModifiers().contains(PUBLIC) && con.getParameters().isEmpty()) {
                    foundReachableConstructor = true;
                }
            }
        }
        if (!foundReachableConstructor) {
            utils().fail("Could not find a public no-argument constructor on '" + hooksClassFqn
                    + " to generate hook methods", te);
        }
//        List<String> methods = new ArrayList<>();
        return names;
    }

    private Set<String> generateHookMethods(AnnotationMirror fileInfo, TypeElement type, String hooksClassFqn, ClassBuilder<?> cl) {
        Set<String> overridden = findMethods(type, fileInfo, hooksClassFqn);
        if (overridden.isEmpty()) {
            return overridden;
        }
        cl.importing(hooksClassFqn, IO_EXCEPTION.qname(), DATA_OBJECT.qname());
        cl.field("HOOKS")
                .withModifier(PRIVATE, STATIC, FINAL)
                .initializedWithNew(nb -> {
                    nb.ofType(simpleName(hooksClassFqn));
                })
                //                .initializedTo("new " + simpleName(hooksClassFqn) + "()")
                .ofType(simpleName(hooksClassFqn));

        for (String mth : overridden) {
            hookMethodGenerator(mth).accept(cl);
        }
        return overridden;
    }

    private String generateEditorKitClassAndRegistration(String dataObjectPackage, AnnotationMirror registrationAnno, String prefix, TypeElement type, String mimeType,
            ParserProxy parser, LexerProxy lexer, RoundEnvironment env) throws Exception {
        String editorKitName = prefix + "EditorKit";
        String syntaxName = generateSyntaxSupport(mimeType, type, dataObjectPackage, registrationAnno, prefix, lexer);
        ClassBuilder<String> cl = ClassBuilder.forPackage(dataObjectPackage)
                .named(editorKitName)
                .docComment("This is the Swing editor kit that will be used for ", prefix, " files.",
                        " It adds some custom actions invokable by keyboard.")
                .withModifier(FINAL)
                .importing(
                        NB_EDITOR_KIT.qname(),
                        //                        "javax.annotation.processing.Generated",
                        EDITOR_KIT.qname(),
                        FILE_OBJECT.qname())
                .extending(NB_EDITOR_KIT.simpleName())
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .initializedWith(mimeType)
                .field("INSTANCE").withModifier(STATIC).withModifier(FINAL)
                .initializedWithNew(nb -> {
                    nb.ofType(editorKitName);
                }).ofType(EDITOR_KIT.simpleName())
                //                .initializedTo("new " + editorKitName + "()").ofType("EditorKit")
                .constructor().setModifier(PRIVATE).emptyBody()
                .override("getContentType").withModifier(PUBLIC).returning(STRING).body().returning("MIME_TYPE").endBlock();
        if (syntaxName != null) {

            cl.importing(SYNTAX_SUPPORT.qname(), BASE_DOCUMENT.qname())
                    .override("createSyntaxSupport").returning(SYNTAX_SUPPORT.simpleName())
                    .withModifier(PUBLIC).addArgument(BASE_DOCUMENT.simpleName(), "doc")
                    .body(bb -> {
                        bb.log(Level.FINEST).argument("doc").logging(
                                "Create ExtSyntax " + syntaxName + " for {0}");
                        bb.returningNew(nb -> {
                            nb.withArgument("doc").ofType(syntaxName);
                        });
                    });
        }

        try {
            // Workaround to allow unit tests to avoid initializing the entire module system:
            cl.field("UNIT_TEST", fb -> {
                fb.docComment("There is a bug workaround in NbEditorDocument which causes a background "
                        + "thread to try to initialize the entire module system, which will wreak "
                        + "havoc in a unit test.  So set the system property 'unit.test' in your unit test runner "
                        + "to avoid this problem (e.g. in the systemPropertyVariables configuration subsection of "
                        + "the maven surefire plugin).");
                fb.withModifier(PRIVATE, STATIC, FINAL)
                        .initializedFromInvocationOf("getBoolean").withStringLiteral("unit.test").on("Boolean").ofType("boolean");
            });
            cl.importing(DOCUMENT.qname(), ATOMIC_REFERENCE.qname(), WEAK_REFERENCE.qname(), EXTRACTION.qname())
                    .overridePublic("createDefaultDocument", mb -> {
                        mb.withModifier(FINAL).returning(DOCUMENT.simpleName()).body(bb -> {
                            bb.declare("result").as(DOCUMENT.simpleName());
                            bb.iff(ifb -> {
                                ClassBuilder.IfBuilder<?> ib = ifb.booleanExpression("UNIT_TEST");
                                ib.lineComment("There is a workaround for a bug in NbEditorDocument in the NetBeans editor API");
                                ib.lineComment("which tries to aggresively initialize some information related to ");
                                ib.lineComment("indenting, as soon as document properties are initialized, calling");
                                ib.lineComment("IndentUtils.indentLevelSize ( NbEditorDocument. this).");
                                ib.blankLine();
                                ib.lineComment("When that code runs in a unit test, it has the effect of trying to initialize");
                                ib.lineComment("the module system and launch entire IDE, which wreaks all sorts of havoc.");
                                ib.lineComment("So, when in a unit test, use an alternate implementation of Document.");
                                ib.assign("result").toNewInstance().withStringLiteral(mimeType)
                                        .ofType("AvoidInitializingModuleSystemDocument")
                                        .orElse()
                                        .assign("result")
                                        .toInvocation("createDefaultDocument").on("super")
                                        .endIf();
                            });
                            bb.lineComment("Used by NbAntlrUtils.extractionFor(), which allows the caller to");
                            bb.lineComment("get the most recent Extraction for a document without calling into");
                            bb.lineComment("ParserManager unless it is out-of-date.");
                            bb.invoke("putProperty").withStringLiteral("_ext")
                                    .withNewInstanceArgument(nb -> {
                                        nb.ofType(ATOMIC_REFERENCE.parametrizedName(WEAK_REFERENCE.parametrizedName(EXTRACTION.simpleName())));
                                    }).on("result");
                            bb.returning("result");
                        });
                    });
            cl.importing(NB_EDITOR_DOCUMENT.qname(), JdkTypes.DICTIONARY.qname())
                    .innerClass("AvoidInitializingModuleSystemDocument", ic -> {
                        ic.withModifier(PRIVATE, STATIC, FINAL)
                                .extending(NB_EDITOR_DOCUMENT.simpleName())
                                .constructor(con -> {
                                    con.addArgument("String", "mimeType")
                                            .body(cb -> {
                                                cb.invoke("super").withArgument("mimeType").inScope();
                                            });
                                })
                                .overrideProtected("createDocumentProperties", mb -> {
                                    mb.docComment("The default implementation of this method will start a background "
                                            + "thread that calls <code>IndentUtils.indentLevelSize(NbEditorDocument.this);</code>, "
                                            + "which will trigger full initialization of the module system, which wreaks "
                                            + "havoc with tests.  So we return this class if the system property <code>unit.test</code> "
                                            + "is true, which overrides this method to bypass launching the background thread.");
                                    mb.returning("Dictionary").addArgument("Dictionary", "orig")
                                            .bodyReturning("orig");
                                });
                    });
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            com.mastfrog.util.preconditions.Exceptions.chuck(ex);
        }

        String lineComment = utils().annotationValue(registrationAnno, "lineCommentPrefix", String.class);

        List<String> actionTypes = new ArrayList<>();
        if (lineComment != null) {
            share(LanguageRegistrationDelegate.COMMENT_STRING, lineComment);
            actionTypes.add(TOGGLE_COMMENT_ACTION);
            cl.importing(ACTION_BINDINGS.qname(), ACTION_BINDING.qname(),
                    KEY_MODIFIERS.qname(), KEY.qname(),
                    KEYBINDING.qname(), BUILT_IN_ACTION.qname())
                    .annotatedWith(ACTION_BINDINGS.simpleName(), ab -> {
                        ab.addArgument("mimeType", mimeType)
                                .addAnnotationArgument("bindings", ACTION_BINDING.simpleName(), actB -> {
                                    actB.addExpressionArgument("action", "BuiltInAction.ToggleComment")
                                            .addAnnotationArgument("bindings", KEYBINDING.simpleName(), keyb -> {
                                                keyb.addExpressionArgument("modifiers", "KeyModifiers.CTRL_OR_COMMAND")
                                                        .addExpressionArgument("key", "Key.SLASH");
                                            });
                                });
                    });
        }
        if (!actionTypes.isEmpty()) {
            cl.importing(ACTION.qname(), TEXT_ACTION.qname());
            for (String actionType : actionTypes) {
                if (actionType.indexOf('.') > 0) {
                    cl.importing(actionType);
                }
            }
            cl.overrideProtected("createActions")
                    .returning(ACTION.simpleName() + "[]")
                    .body(bb -> {
                        bb.log(Level.FINEST)
                                .stringLiteral(lineComment)
                                .logging("Return actions enhanced with ToggleCommentAction for ''{0}''");

                        bb.declare("additionalActions").initializedAsNewArray(ACTION.simpleName(), (ClassBuilder.ArrayLiteralBuilder<?> alb) -> {
                            if (gotos.containsKey(mimeType)) {
                                Set<VariableElement> all = gotos.get(mimeType);
                                if (!all.isEmpty()) {
                                    cl.importing(NB_ANTLR_UTILS.qname());
                                    alb.invoke("createGotoDeclarationAction", ib -> {
                                        ib.withStringLiteral(mimeType);
                                        for (VariableElement el : all) {
                                            Element enc = el.getEnclosingElement();
                                            if (enc instanceof TypeElement) {
                                                TypeElement te = (TypeElement) enc;
                                                String exp = te.getQualifiedName() + "."
                                                        + el.getSimpleName();
                                                ib.withArgument(exp);
                                            }
                                        }
                                        ib.on(NB_ANTLR_UTILS.simpleName());
                                    });
                                }
                            }
                            for (String tp : actionTypes) {
                                // ToggleCommentAction is an inner class of NbEditorKit,
                                // which we're subclassing, so no need for an import
                                if ("ToggleCommentAction".equals(tp)) {
                                    alb.newInstance(nb -> {
                                        nb.withStringLiteral(lineComment)
                                                .ofType(tp);
                                    });
//                                    alb.add("new " + simpleName(tp) + "(" + LinesBuilder.stringLiteral(lineComment) + ")");
                                } else {
                                    alb.newInstance(nb -> {
                                        nb.ofType(tp);
                                    });
//                                    alb.add("new " + /*simpleName(tp)*/ tp + "()");
                                }
                                List<Element> found = findAntlrActionElements(env, mimeType);
                                for (Element e : found) {
                                    if (e instanceof TypeElement) {
                                        cl.importing(((TypeElement) e).getQualifiedName().toString());
                                        alb.add("new " + e.getSimpleName() + "()");
                                    } else if (e instanceof ExecutableElement) {
                                        TypeElement owner = AnnotationUtils.enclosingType(e);
                                        String pkg = utils().packageName(owner);
                                        if (!pkg.equals(cl.packageName())) {
                                            cl.importing(owner.getQualifiedName().toString());
                                        }
                                        alb.add(owner.getSimpleName() + "." + e.getSimpleName() + "()");
                                    }
                                }
                            }
                            // Get the actions from keybindings annotations
                            for (String invocation : getAll(KeybindingsAnnotationProcessor.actionsKey(mimeType))) {
                                alb.add(invocation);
                            }
                            alb.closeArrayLiteral();
                        });

                        cl.importing(TEXT_ACTION.qname());
                        bb.returningInvocationOf("augmentList").withArgumentFromInvoking("createActions")
                                .on("super").withArgument("additionalActions").on(TEXT_ACTION.simpleName())
                                .endBlock();
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
                .importing(EXT_SYNTAX_SUPPORT.qname(), BASE_DOCUMENT.qname(), TOKEN_ID.qname(),
                        JdkTypes.ARRAYS.qname(),
                        pkg.getQualifiedName() + "." + prefix + "Tokens"
                )
                .extending(EXT_SYNTAX_SUPPORT.simpleName())
                .withModifier(FINAL)
                .constructor(cb -> {
                    cb.addArgument(BASE_DOCUMENT.simpleName(), "doc");
                    cb.body(bb -> {
                        bb.invoke("super").withArgument("doc").inScope();
                        bb.endBlock();
                    });
                });
        if (!commentTokens.isEmpty()) {
            cl.importing(TOKEN_ID.qname());
            cl.field("COMMENT_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL)
                    .docComment("The set of tokens specified to represent "
                            + "comments in the " + registrationAnno.getAnnotationType()
                            + " that generated this class")
                    .initializedAsArrayLiteral(TOKEN_ID.simpleName(), alb -> {
                        for (int id : commentTokens) {
                            String fieldName = lexer.toFieldName(id);
                            if (fieldName == null) {
                                utils().fail("No token id for " + id);
                                continue;
                            }
                            alb.field(fieldName).of(prefix + "Tokens");
                        }
                    });
            cl.override("getCommentTokens").withModifier(PUBLIC).returning("TokenID[]")
                    .body(fieldReturner("COMMENT_TOKENS"));
        }
        if (!bracketSkipTokens.isEmpty()) {
            cl.field("BRACKET_SKIP_TOKENS").withModifier(PRIVATE).withModifier(STATIC)
                    .withModifier(FINAL).initializedTo(tokensArraySpec(prefix, bracketSkipTokens, lexer))
                    .ofType("TokenID[]");
            cl.override("getBracketSkipTokens").withModifier(PROTECTED).returning(TOKEN_ID.simpleNameArray())
                    .body(fieldReturner("BRACKET_SKIP_TOKENS"));
        }
        if (!whitespaceTokens.isEmpty()) {
            cl.importing(TOKEN_ID.qname());
            if (whitespaceTokens.size() == 1) {
                cl.overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument(TOKEN_ID.simpleName(), "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body(bb -> {
                            bb.returning("tokenId.getNumericID() == " + whitespaceTokens.get(0)).endBlock();
                        });
            } else {
                cl.field("WHITESPACE_TOKEN_IDS", fb -> {
                    fb.withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL);
                    fb.docComment("A sorted and usable for binary search list "
                            + "of token ids");
                    cl.importing(lexer.lexerClassFqn(), TOKEN_ID.qname());
                    fb.initializedAsArrayLiteral("int", alb -> {
                        for (int ws : whitespaceTokens) {
                            String tokenField = lexer.lexerClassSimple()
                                    + "." + lexer.tokenName(ws);
                            alb.add(tokenField);
                        }
                    });
                }).importing(ARRAYS.qname())
                        .overridePublic("isWhitespaceToken").returning("boolean")
                        .addArgument(TOKEN_ID.simpleName(), "tokenId").addArgument("char[]", "buffer")
                        .addArgument("int", "offset").addArgument("int", "tokenLength")
                        .body((ClassBuilder.BlockBuilder<?> bb) -> {
//                            bb.returningInvocationOf("binarySearch")
//                                    .withArgumentFromField("WHITESPACE_TOKEN_IDS").ofThis()
//                                    .withArgument("tokenId.getNumericID()")
//                                    .on(ARRAYS.simpleName());

                            bb.returning("Arrays.binarySearch(WHITESPACE_TOKEN_IDS, "
                                    + "tokenId.getNumericID()) >= 0");
                        });
//
            }
        }
        if (useDeclarationTokenProcessor(mimeType)) {
            String className = prefix + DECLARATION_TOKEN_PROCESSOR.simpleName();
            cl.overridePublic("createDeclarationTokenProcessor").returning(className)
                    .addArgument("String", "varName")
                    .addArgument("int", "startPos")
                    .addArgument("int", "endPos")
                    .body(bb -> {
                        bb.log(Level.FINER).argument("varName").argument("startPos").argument("endPos")
                                .logging(cl.className() + ".createDeclarationTokenProcessor({0},{1},{2}");
                        bb.returningNew(nb -> {
                            nb.withArgument("varName")
                                    .withArgument("startPos")
                                    .withArgument("endPos")
                                    .withArgumentFromInvoking("getDocument").inScope()
                                    .ofType(className);
                        });
                    });

            declPositionMethod("findDeclarationPosition", className, cl);
            declPositionMethod("findLocalDeclarationPosition", className, cl);
        }
        writeOne(cl);
        return generatedClassName;
    }

    static void declPositionMethod(String name, String className, ClassBuilder<?> cl) {
        cl.overridePublic(name)
                .addArgument("String", "varName")
                .addArgument("int", "varPos")
                .returning("int")
                .body(bb -> {
                    bb.declare("result").initializedByInvoking("getDeclarationPosition").onInvocationOf("createDeclarationTokenProcessor")
                            .withArgument("varName").withArgument("varPos").withArgument(-1)
                            .inScope().as("int");
                    bb.log(Level.FINEST).argument("varName").argument("varPos").argument("result")
                            .logging(className + "." + name + "({0}, {1}) returning {2}");
                    bb.returning("result");
                });
    }

    private Consumer<ClassBuilder.BlockBuilder<?>> fieldReturner(String name) {
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
        cl.importing(ACTION_REFERENCES.qname());
        cl.importing(ACTION_REFERENCE.qname());
        cl.importing(ACTION_ID.qname());
        ClassBuilder.ArrayValueBuilder<ClassBuilder.AnnotationBuilder<ClassBuilder<String>>> annoBuilder = cl.annotatedWith(ACTION_REFERENCES.simpleName()).addArrayArgument("value");
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
            ClassBuilder.AnnotationBuilder<?> ab = annoBuilder.annotation(ACTION_REFERENCE.simpleName())
                    .addExpressionArgument("path", "ACTION_PATH")
                    .addArgument("position", position * 100);
            if (separator) {
                ab.addArgument("separatorAfter", ++position * 100);
            }
            ab.addAnnotationArgument("id", ACTION_ID.simpleName(), aid -> {
                aid.addArgument("category", category).addArgument("id", actionId).closeAnnotation();
            });
            ab.closeAnnotation();
            position++;
        }
        annoBuilder.closeArray().closeAnnotation();
    }

    // Parser generation
    private String generateParserClasses(String mimeType, ParserProxy parser, LexerProxy lexer,
            TypeElement type, AnnotationMirror mirror,
            AnnotationMirror parserInfo, String prefix) throws IOException {
        String nbParserName = prefix + "NbParser";
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String tokenTypeName = prefix + "Token";

        boolean changeSupport = utils().annotationValue(parserInfo, "changeSupport", Boolean.class, false);
        int streamChannel = utils().annotationValue(parserInfo, "streamChannel", Integer.class, 0);

        TypeMirror helperClass = utils().typeForSingleClassAnnotationMember(parserInfo, "helper");
        boolean hasExplicitHelperClass = helperClass != null
                && !NB_PARSER_HELPER.qnameNotouch().equals(helperClass.toString());
        String helperClassName = hasExplicitHelperClass ? helperClass.toString() : prefix + "ParserHelper";

        String entryPointType = parser.parserEntryPointReturnTypeFqn();
        String entryPointSimple = parser.parserEntryPointReturnTypeSimple();
        String parserResultType = "AntlrParseResult";

        ClassBuilder<String> cl = ClassBuilder.forPackage(pkg).named(nbParserName)
                .withModifier(PUBLIC).withModifier(FINAL)
                .importing(
                        SNAPSHOT.qname(),
                        TASK.qname(),
                        PARSER.qname(),
                        SOURCE_MODIFICATION_EVENT.qname(),
                        //                        "javax.annotation.processing.Generated",
                        GRAMMAR_SOURCE.qname(),
                        UTIL_EXCEPTIONS.qname(),
                        CHANGE_LISTENER.qname(),
                        SOURCE.qname(),
                        entryPointType,
                        PARSER_FACTORY.qname(),
                        //                        "org.netbeans.modules.parsing.spi.ParserFactory",
                        COLLECTION.qname(),
                        //                        "java.util.Collection",
                        ATOMIC_BOOLEAN.qname(),
                        //                        "java.util.concurrent.atomic.AtomicBoolean",
                        BOOLEAN_SUPPLIER.qname(),
                        //                        "java.util.function.BooleanSupplier",
                        NB_PARSER_HELPER.qname(),
                        ANTLR_PARSE_RESULT.qname(),
                        //                        "org.nemesis.antlr.spi.language.NbParserHelper",
                        //                        "org.nemesis.antlr.spi.language.AntlrParseResult",
                        SYNTAX_ERROR.qname(),
                        //                        "org.nemesis.antlr.spi.language.SyntaxError",
                        SUPPLIER.qname(),
                        //                        "java.util.function.Supplier",
                        EXTRACTOR.qname(),
                        EXTRACTION.qname(),
                        OPTIONAL.qname(),
                        FILE_OBJECT.qname(),
                        PROJECT.qname(),
                        FILE_OWNER_QUERY.qname(),
                        //                        "org.nemesis.extraction.Extractor",
                        //                        "org.nemesis.extraction.Extraction",
                        COMMON_TOKEN_STREAM.qname(),
                        //                        "org.antlr.v4.runtime.CommonTokenStream",
                        OPTIONAL.qname(),
                        //                        "java.util.Optional",
                        LIST.qname(),
                        //                        "java.util.List",
                        //                        "org.nemesis.antlr.spi.language.IterableTokenSource",
                        ITERABLE_TOKEN_SOURCE.qname(),
                        lexer.lexerClassFqn(),
                        parser.parserClassFqn()
                )
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending(PARSER.simpleName())
                .docComment("NetBeans parser wrapping ", parser.parserClassFqn(), " using entry point method ", parser.parserEntryPoint().getSimpleName(), "()."
                        + "  For the most part, you will not use this class directly, but rather register classes that are interested in processing"
                        + " parse results for this MIME type, and then get passed them when a file is modified, opened, or reparsed for some other reason.");
        if (changeSupport) {
            cl.importing(SET.qname(), WEAK_SET.qname(), CHANGE_SUPPORT.qname())
                    .field("INSTANCES").withModifier(FINAL).withModifier(PRIVATE).withModifier(STATIC).initializedTo("new WeakSet<>()").ofType("Set<" + nbParserName + ">")
                    .field("changeSupport").withModifier(FINAL).withModifier(PRIVATE).initializedTo("new ChangeSupport(this)").ofType(CHANGE_SUPPORT.simpleName());
        }
        cl.field("HELPER")
                .docComment("Helper class which can talk to the NetBeans adapter layer via protected methods which are not otherwise exposed.")
                .withModifier(FINAL).withModifier(STATIC).initializedTo("new " + helperClassName + "()").ofType(helperClassName)
                .field("CANCELLATION_FOR_SOURCE", (FieldBuilder<?> fb) -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL);
                    cl.importing(SOURCE.qname(), WEAK_HASH_MAP.qname(), COLLECTIONS.qname());
                    String mapType = MAP.parameterizedOn(SOURCE, ATOMIC_BOOLEAN).simpleName();
                    fb.initializedFromInvocationOf("synchronizedMap")
                            .withNewInstanceArgument().ofType(WEAK_HASH_MAP.parameterizedInferenced())
                            .on(COLLECTIONS.simpleName()).ofType(mapType);
                })
                .field("RESULT_FOR_TASK", (FieldBuilder<?> fb) -> {
                    cl.importing(MAP.qname());
                    fb.withModifier(PRIVATE, FINAL);
                    String mapType = MAP.parameterizedOn(TASK, ANTLR_PARSE_RESULT).simpleName();
                    fb.initializedFromInvocationOf("synchronizedMap")
                            .withNewInstanceArgument().ofType(WEAK_HASH_MAP.parameterizedInferenced())
                            .on(COLLECTIONS.simpleName()).ofType(mapType);
                })
                .field("lastResult").withModifier(PRIVATE).ofType(parserResultType)
                .method("doCancel", mb -> {
                    mb.withModifier(PRIVATE).addArgument(SOURCE_MODIFICATION_EVENT.simpleName(), "evt")
                            .body(bb -> {
                                bb.ifNotNull("evt", ib -> {
                                    ib.declare("canceller").initializedByInvoking("get")
                                            .withArgumentFromInvoking("getModifiedSource").on("evt")
                                            .on("CANCELLATION_FOR_SOURCE").as(ATOMIC_BOOLEAN.simpleName());
                                    ib.ifNotNull("canceller", ibb -> {
                                        ibb.invoke("set").withArgument(true).on("canceller");
                                    }).orElse(eb -> {
                                        eb.log("Attempt to cancel a parse not currently being run: {0}", Level.FINE, "evt");
                                    });
                                });
                            });
                })
                //                .staticBlock(sb -> {
                //                    sb.invoke("setLevel").withArgument("Level.ALL").on("LOGGER").endBlock();
                //                })
                .constructor().annotatedWith(SUPPRESS_WARNINGS.simpleName()).addArgument("value", "LeakingThisInConstructor").closeAnnotation().setModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.lineComment("Languages which have alterable global configuration need to fire changes from their parser;");
                        bb.lineComment("This allows us to track all live instances without leaking memory, so languageSettingsChanged()");
                        bb.lineComment("can trigger events from all live parsers for this language");
                        bb.debugLog("Created a new " + nbParserName);
                        bb.invoke("add").withArgument("this").on("INSTANCES").endBlock();
                        bb.log("Created {0}", Level.FINER, "this");
                    } else {
                        bb.debugLog("Created a new " + nbParserName);
                        bb.lineComment("To enable support for firing changes, set changeSupport on the")
                                .lineComment("generating annotation to true").endBlock();
                        bb.log("Created {0}", Level.FINER, "this");
                    }
                })
                .method("parse", (mb) -> {
                    mb.docComment("Parse a document or file snapshot associated with a parser task.");
                    mb.override().addArgument(SNAPSHOT.simpleName(), "snapshot")
                            .addArgument(TASK.simpleName(), "task")
                            .addArgument(SOURCE_MODIFICATION_EVENT.simpleName(), "event")
                            .withModifier(PUBLIC)
                            .body(block -> {
                                block.statement("assert snapshot != null")
                                        .log("{0}.parse({1}) for {2}", Level.FINER, "this", "snapshot", "task");
                                block.lineComment("Use a short-lived AtomicBoolean to allow cancellation of");
                                block.lineComment("the parse to notify the plumbing that it was cancelled");
                                block.declare("cancelled").initializedWithNew(nb -> {
                                    nb.ofType(ATOMIC_BOOLEAN.simpleName());
                                }).as(ATOMIC_BOOLEAN.simpleName());

                                block.debugLog("Parse");
                                block.lineComment("Get the source we're going to parse");
                                block.declare("src").initializedByInvoking("getSource").on("snapshot")
                                        .as(SOURCE.simpleName());
                                block.lineComment("Convert that into a GrammarSource<Snapshot> - GrammarSource is a ")
                                        .lineComment("lingua fraca for things that can make a CharStream that can be fed ")
                                        .lineComment("to an Antlr lexer, so that code elsewhere does not have to have special")
                                        .lineComment("handling for being passed a String, File, Path, FileObject, Document, Source or Snapshot,")
                                        .lineComment("any of which may represent the contents of a file, and all of which are")
                                        .lineComment("tossed around freely by NetBeans' editor infrastructure")
                                        .blankLine()
                                        .lineComment("A GrammarSource also contains the language-specific code for looking up")
                                        .lineComment("other files that are related to a given file - in Antlr imported grammars,")
                                        .lineComment("or in Java, imported classes.  That logic can be separated from the code that")
                                        .lineComment("simply translates between file-like things, but both get encapsulated into a")
                                        .lineComment("GrammarSource.");
                                block.declare("snapshotSource").initializedByInvoking("find")
                                        .withArgument("snapshot").withArgument(tokenTypeName + ".MIME_TYPE")
                                        .on(GRAMMAR_SOURCE.simpleName()).as(GRAMMAR_SOURCE.parametrizedName("?"));
                                block.asserting(ab -> {
                                    ab.variable("snapshotSource").notEquals().expression("null")
                                            .endCondition().withMessage(msg -> {
                                                msg.append("Null returned from GrammarSource.find() with an instance of Snapshot of mimeType ")
                                                        .append(mimeType).append(". Is the antlr-input-nb module is installed?");
                                            });
                                });
                                block.lineComment("Store the source and canceller temporarily in the map of source to canceller");
                                block.invoke("put")
                                        .withArgument("src")
                                        .withArgument("cancelled").on("CANCELLATION_FOR_SOURCE");
                                block.trying().declare("result").initializedByInvoking("parse")
                                        .withArgument("snapshotSource").withArgument("cancelled::get").on(nbParserName).as(parserResultType)
                                        .ifNotNull("result")
                                        .invoke("put").withArgument("task").withArgument("result").on("RESULT_FOR_TASK")
                                        .synchronizeOn("this")
                                        .assign("lastResult").toExpression("result")
                                        .endBlock().endIf()
                                        .catching("Exception")
                                        .logException(Level.SEVERE, "thrown")
                                        //                                        .invoke("printStackTrace").withArgument("thrown")
                                        //                                        .on(UTIL_EXCEPTIONS.simpleName())
                                        .fynalli()
                                        .lineComment("Another thread can enter and replace the canceller before this "
                                                + "method completes; it will likely be stalled ")
                                        .lineComment("on the parser lock, but we do not want to clobber its canceller")
                                        .invoke("remove").withArgument("src").withArgument("cancelled").on("CANCELLATION_FOR_SOURCE")
                                        .endBlock();
                            });
                })
                .method("parse", mb -> {
                    mb.addArgument(GRAMMAR_SOURCE.parametrizedName("?"), "src")
                            .throwing("Exception")
                            .addArgument("BooleanSupplier", "cancelled")
                            .returning(ANTLR_PARSE_RESULT.simpleName())
                            .withModifier(PUBLIC).withModifier(STATIC)
                            .docComment("Convenience method for initiating a parse programmatically "
                                    + "rather than via the NetBeans parser infrastructure.")
                            .body(bb -> {
                                bb.lineComment("This requires some explanation:")
                                        .lineComment("  There are some rare cases where it is useful to *completely* disable the parsing plumbing")
                                        .lineComment("  briefly, such as when creating new files from a template or when adding support to a project")
                                        .lineComment("  where the file is going to be created (FileObject.createData()) as a zero-byte file, then")
                                        .lineComment("  get populated, and possibly formatted.  Running a bunch of lexes and parses during that")
                                        .lineComment("  is at best useless and at worst harmful.")
                                        .blankLine()
                                        .lineComment("  So, we have " + ANTLR_MIME_TYPE_REGISTRATION.qnameNotouch() + ".runExclusiveForProject which")
                                        .lineComment("  will cause all parses of documents with a given MIME type *within a single project* to simply")
                                        .lineComment("  return an empty parser result.")
                                        .blankLine()
                                        .lineComment("  Here, we wrap our parse in a call to HELPER's super method, which checks the locked state of")
                                        .lineComment("  documents of our MIME type for the project that owns the document a parse request was initiated")
                                        .lineComment("  for, and if locked, never does a parse at all, just returns a fake empty parser result.")
                                        .blankLine()
                                        .lineComment("  The locking involved is optimistic in the extreme (and thus, very low-overhead) - it")
                                        .lineComment("  assumes that locking a mime type for a project is an extremely rare event, and while it is")
                                        .lineComment("  reentrant, two threads trying to lock at the same time will simply result in the second arrival")
                                        .lineComment("  throwing an exception.  Do not use except for the stated (and rare) use case.");

                                bb.declare("foOpt").initializedByInvoking("lookup")
                                        .withClassArgument(FILE_OBJECT.simpleName())
                                        .on("src").as(OPTIONAL.parametrizedName(FILE_OBJECT.simpleName()));
                                bb.lineComment("The GrammarSourceImplementation plumbing (you need to write it) needs to be smart")
                                        .lineComment("enough to find a FileObject, Path, File or Document from instances of")
                                        .lineComment("Snapshot, Source, Document, FileObject, File or Path")
                                        .blankLine()
                                        .lineComment("See AbstractFileObjectGrammarSourceImplementation in the antlr-input-nb module for inspiration");
                                bb.iff(ifb -> {
                                    ClassBuilder.IfBuilder<?> ibb = ifb.invokeAsBoolean("isPresent").on("foOpt");
                                    ibb.lineComment("We may be running against an in-memory file, a wad of text, or")
                                            .lineComment("a file that is not inside a project, so don't assume that")
                                            .lineComment("there will actually BE a file, or a project.  If either one ")
                                            .lineComment("is not present, this sort of locking is irrelevant and we should ")
                                            .lineComment("just go ahead and run the parse.");
                                    ibb.declare("project").initializedByInvoking("getOwner")
                                            .withArgumentFromInvoking("get").on("foOpt")
                                            .on(FILE_OWNER_QUERY.simpleName()).as(PROJECT.simpleName());
                                    ibb.iff(ifProject -> {
                                        ClassBuilder.IfBuilder<?> ifProjectBlock = ifProject.variable("project").notEquals().expression("null")
                                                .and().variable("project").notEqualToField("UNOWNED").of(FILE_OWNER_QUERY.simpleName());
                                        ifProjectBlock.lineComment("Here is where we will get back an empty AntlrParserResult if our MIME type is locked for this project");
                                        ifProjectBlock.returningInvocationOf("returnDummyResultIfProjectLocked")
                                                .withArgument("project")
                                                .withArgumentFromInvoking("getProjectDirectory").on("project")
                                                .withLambdaArgument(lbb -> {
                                                    lbb.body()
                                                            .lineComment("This will only be called if the document was not locked at the time of invocation.")
                                                            .blankLine()
                                                            .lineComment("Technically, this can race (a thread started a parse before the locking was initiated)")
                                                            .lineComment("but what we are after here is not to parse files created *while* locked, and those didn't ")
                                                            .lineComment("exist at the point any thread might have entered here, so that is a non-problem.")
                                                            .returningInvocationOf("reallyParse")
                                                            .withArgument("src")
                                                            .withArgument("cancelled")
                                                            .inScope().endBlock();
                                                })
                                                .on("HELPER").endIf();
                                    }).endIf();
                                });
                                bb.lineComment("Fallthrough: no project, or no file so just do it.");
                                bb.returningInvocationOf("reallyParse").withArgument("src")
                                        .withArgument("cancelled").inScope();
                            });
                })
                .method("reallyParse", mb -> {
                    mb.docComment("Convenience method for initiating a parse programmatically rather than via the NetBeans parser infrastructure.");
                    mb.addArgument(GRAMMAR_SOURCE.parametrizedName("?"), "src")
                            .throwing("Exception")
                            .addArgument("BooleanSupplier", "cancelled")
                            .returning(ANTLR_PARSE_RESULT.simpleName())
                            .withModifier(PRIVATE).withModifier(STATIC)
                            .body(bb -> {
                                bb.declare("lexer").initializedByInvoking("createAntlrLexer")
                                        .withArgumentFromInvoking("stream")
                                        .on("src")
                                        .on(prefix + "Hierarchy").as(lexer.lexerClassSimple());
                                bb.declare("tokenSource").initializedByInvoking("createWrappedTokenSource")
                                        .withArgument("lexer")
                                        .on(prefix + "Hierarchy").as(ITERABLE_TOKEN_SOURCE.simpleName());
                                bb.declare("stream")
                                        .initializedWithNew(nb -> {
                                            nb.withArgument("tokenSource")
                                                    .withArgument(streamChannel)
                                                    .ofType(COMMON_TOKEN_STREAM.simpleName());
                                        })
                                        //                                        .initializedWith("new CommonTokenStream(tokenSource, " + streamChannel + ")")
                                        .as(COMMON_TOKEN_STREAM.simpleName());
                                bb.declare("parser").initializedWith("new " + parser.parserClassSimple() + "(stream)").as(parser.parserClassSimple());
                                bb.lineComment("By default, ANTLR parsers come with a listener that writes to stdout attached.  Remove it");
                                bb.invoke("removeErrorListeners").on("parser");
                                bb.blankLine();
                                bb.declare("maybeSnapshot").initializedByInvoking("lookup").withClassArgument(SNAPSHOT.simpleName()).on("src").as(OPTIONAL.parameterizedOn(SNAPSHOT).simpleName());
                                bb.blankLine();
                                bb.lineComment("The registered GrammarSource implementations will synthesize a Snapshot if the GrammarSource");
                                bb.lineComment("was not created from one (as happens in tests), if a Document or a FileObject is present.");
                                bb.lineComment("Most code touching extraction does not require a snapshot be present.  So this will only");
                                bb.lineComment("be null for a GrammarSource created from a String or CharStream.");
                                bb.declare("snapshot").initializedWith("maybeSnapshot.isPresent() ? maybeSnapshot.get() : null").as(SNAPSHOT.simpleName());

                                String suppType = SUPPLIER.parametrizedName(LIST.parametrizedName("? extends " + SYNTAX_ERROR.simpleName()));

                                bb.lineComment("Invoke the hook method allowing the helper to attach error listeners, or ");
                                bb.lineComment("configure the parser or lexer before using them.");
                                bb.declare("errors").initializedByInvoking("parserCreated")
                                        .withArgument("lexer").withArgument("parser")
                                        .withArgument("snapshot")
                                        .on("HELPER").as(suppType);

                                bb.blankLine();
                                bb.lineComment("Here we actually trigger the Antlr parse");
                                bb.declare("tree").initializedByInvoking(parser.parserEntryPoint().getSimpleName().toString())
                                        .on("parser").as(entryPointSimple);
                                bb.blankLine();
                                bb.lineComment("Look up the extrator(s) for this mime type");
                                String extType = EXTRACTOR.parametrizedName("? super " + entryPointSimple);

                                bb.declare("extractor")
                                        .initializedByInvoking("forTypes").withArgument(prefix + "Token.MIME_TYPE")
                                        .withArgument(entryPointSimple + ".class").on(EXTRACTOR.simpleName())
                                        .as(extType);
                                bb.blankLine();
                                bb.lineComment("Run extraction, pulling out data needed to create navigator panels,");
                                bb.lineComment("code folds, etc.  Anything needing the extracted sets of regions and data");
                                bb.lineComment("can get it from the parse result");

                                bb.declare("extraction").as(EXTRACTION.simpleName());

                                bb.trying().assign("extraction").toInvocation("extract")
                                        .withArgument("tree")
                                        .withArgument("src")
                                        .withArgument("tokenSource")
                                        .on("extractor")
                                        .fynalli()
                                        .lineComment("discard the cached tokens used for token extraction")
                                        .invoke("dispose").on("tokenSource")
                                        .endBlock();

//                                bb.trying(tri -> {
//                                    tri.assign("extraction").toInvocation("extract")
//                                            .withArgument("tree")
//                                            .withArgumentFromInvoking("source").on("bag")
//                                            .withArgument("tokenSource")
//                                            .on("extractor");
//                                    tri.fynalli(fin -> {
//                                        fin.lineComment("discard the cached tokens used for token extraction");
//                                        fin.invoke("dispose").on("tokenSource");
//                                        // XXX should not be needed
//                                        fin.endBlock();
//                                    });
//                                });
//                                bb.declare("extraction").initializedByInvoking("extract")
//                                        .withArgument("tree")
//                                        .withArgumentFromInvoking("source").on("bag")
//                                        .withArgument("cancelled")
//                                        .withArgument("tokenSource")
//                                        .on("extractor").as("Extraction");
//                                bb.lineComment("discard the cached tokens used for token extraction");
//                                bb.invoke("dispose").on("tokenSource");
//                                bb.blankLine();
                                bb.lineComment("Now create a parser result and object to populate it, and allow the");
                                bb.lineComment("helper's hook method to add anything it needs, such as semantic error");
                                bb.lineComment("checking, or other data resolved from the extraction or parse tree - ");
                                bb.lineComment("this allows existing Antlr code to be used.");
                                bb.declare("result").initializedWith("new " + ANTLR_PARSE_RESULT.simpleNameArray(1))
                                        .as(ANTLR_PARSE_RESULT.simpleNameArray());
                                bb.declare("thrown").initializedWith("new Exception[1]").as("Exception[]");
                                bb.lineComment("Doing this via " + prefix + "Hierarchy simply ensures that");
                                bb.lineComment("we don't publicly expose a method for constructing parser results");
                                bb.lineComment("which ought to be private to this module.");
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
                                        .assign("result[0]").toExpression("pr")
                                        //                                        .statement("result[0] = pr")
                                        .catching("Exception")
                                        .lineComment("The hook method may throw an exception, which we will need to catch")
                                        .assign("thrown[0]").toExpression("ex")
                                        //                                        .statement("thrown[0] = ex")
                                        .as("ex").endTryCatch().endBlock()
                                        .on(prefix + "Hierarchy");

                                bb.ifNotNull("thrown[0]")
                                        .invoke("printStackTrace").withArgument("thrown[0]").on(UTIL_EXCEPTIONS.simpleName()).endIf();
                                bb.returning("result[0]");
                                bb.endBlock();
                            });
                })
                .overridePublic("getResult", (mb) -> {
                    mb.withModifier(SYNCHRONIZED)
                            .addArgument(TASK.simpleName(), "task")
                            .returning(parserResultType)
                            .body(bb -> {
                                bb.lineComment("The parser API is nothing if not ambiguous");
                                bb.lineComment("An instance may be used once and thrown away, or");
                                bb.lineComment("used multiple times, in which case it needs to");
                                bb.lineComment("keep some kind of task:result mapping.");
                                bb.declare("result")
                                        .initializedByInvoking("getOrDefault")
                                        .withArgument("task")
                                        .withArgument("lastResult")
                                        .on("RESULT_FOR_TASK")
                                        .as(simpleName(parserResultType));

                                bb.invoke("remove").withArgument("task").on("RESULT_FOR_TASK");

                                bb.log("Get result for {0} on {1} present? {2} isLastResult? {3}", Level.FINEST,
                                        "task", "this", "(result != null)", "(lastResult == result)");
                                bb.returning("result");
//                                bb.returningInvocationOf("getOrDefault")
//                                        .withArgument("task")
//                                        .withArgument("lastResult")
//                                        .on("RESULT_FOR_TASK");
                            });
                })
                .override("addChangeListener")
                .addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.invoke("addChangeListener").withArgument("l").on("changeSupport");
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                .override("removeChangeListener").addArgument("ChangeListener", "l").withModifier(PUBLIC)
                .body(bb -> {
                    if (changeSupport) {
                        bb.invoke("removeChangeListener").withArgument("l").on("changeSupport");
                    } else {
                        bb.lineComment("do nothing").endBlock();
                    }
                })
                // public void cancel (@NonNull CancelReason reason, @NullAllowed SourceModificationEvent event) {}
                .override("cancel")
                .docComment("Cancel the last parse if it is still running.")
                .withModifier(PUBLIC).addArgument("CancelReason", "reason")
                .addArgument(SOURCE_MODIFICATION_EVENT.simpleName(), "event")
                .body(bb -> {
                    bb.debugLog("Parse cancelled");
                    bb.log("Parse cancelled due to {0} on {1}", Level.FINEST, "reason", "this");
                    bb.invoke("doCancel").withArgument("event").inScope();
//                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                })
                .override("cancel")
                .annotatedWith("SuppressWarnings").addArgument("value", "deprecation").closeAnnotation()
                .withModifier(PUBLIC)
                .body(bb -> {
                    bb.debugLog("Deprecated cancel");
                    bb.log("Deprecated Parser.cancel() method called on {0}, do nothing", Level.FINEST, "this");
//                    bb.invoke("set").withArgument("true").on("cancelled").endBlock();
                });
        if (changeSupport) {
            cl.method("forceReparse")
                    .docComment("Cause all instance of this parser to fire a change event, triggering a "
                            + "reparse.  This is used when the underlying <i>model</i> of the language or "
                            + "project changes (say, a library was added or the source level was changed).")
                    .withModifier(PRIVATE).body().invoke("fireChange").on("changeSupport").endBlock()
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
            TypeName helperExtends = NB_PARSER_HELPER.parameterizedOn(
                    TypeName.fromQualifiedName(parser.parserClassFqn()),
                    TypeName.fromQualifiedName(lexer.lexerClassFqn()),
                    ANTLR_PARSE_RESULT, TypeName.fromQualifiedName(
                            entryPointType));
            // <P extends Parser, L extends Lexer, R extends Result, T extends ParserRuleContext> {
            cl.importing(entryPointType, lexer.lexerClassFqn(), parser.parserClassFqn(),
                    ANTLR_PARSE_RESULT.qname(), "org.openide.util.Lookup.Provider");
            ClassBuilder<ClassBuilder<String>> cb = cl.innerClass(helperClassName)
                    .extending(helperExtends.simpleName())
                    .withModifier(PRIVATE).withModifier(FINAL).withModifier(STATIC)
                    .constructor(con -> {
                        con.setModifier(PRIVATE).body().invoke("super").withStringLiteral(mimeType).inScope().endBlock();
                    })
                    .method("returnDummyResultIfProjectLocked", mb -> {
                        cl.importing(KnownTypes.LOOKUP.qname() + ".Provider",
                                KnownTypes.FILE_OBJECT.qname(),
                                KnownTypes.THROWING_SUPPLIER.qname());

                        mb.returning(ANTLR_PARSE_RESULT.simpleName()).throwing("Exception")
                                .addArgument("Provider", "project")
                                .addArgument(FILE_OBJECT.simpleName(), "projectIdentifier")
                                .addArgument(KnownTypes.THROWING_SUPPLIER.parametrizedName(ANTLR_PARSE_RESULT.simpleName()), "supp")
                                .body(bb -> {
// return super.runParsingTaskIfProjectNotLocked(project, projectIdentifier, runner);
                                    bb.returningInvocationOf("runParsingTaskIfProjectNotLocked")
                                            .withArgument("project")
                                            .withArgument("projectIdentifier")
                                            .withArgument("supp")
                                            .on("super");
                                });

                    });

            boolean useDefaultErrorHandling = utils()
                    .annotationValue(parserInfo, "defaultErrorHighlightingEnabled", Boolean.class, true);

            if (!useDefaultErrorHandling) {
                cb.overridePublic("isDefaultErrorHandlingEnabled").returning("boolean")
                        .body().returning("false").endBlock();
            }

            cb.build();
        }

        cl.importing(MIME_REGISTRATION.qname(), PARSER_FACTORY.qname(), SNAPSHOT.qname(),
                COLLECTION.qname())
                .innerClass(prefix + "ParserFactory").publicStaticFinal().extending("ParserFactory")
                .docComment("Registers our parse with the NetBeans parser infrastructure.")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .annotatedWith(MIME_REGISTRATION.simpleName()).addExpressionArgument("mimeType", prefix + "Token.MIME_TYPE").addArgument("position", Integer.MAX_VALUE - 1000)
                .addClassArgument("service", PARSER_FACTORY.simpleName()).closeAnnotation()
                .method("createParser").override().withModifier(PUBLIC).returning("Parser")
                .addArgument(COLLECTION.parameterizedOn(SNAPSHOT).simpleName(), "snapshots")
                .body(bb -> {
                    bb.returningNew(nb -> {
                        nb.ofType(simpleName(nbParserName));
                    });
                })
                //                .body().returning("new " + nbParserName + "()").endBlock()
                .build();

        cl.importing(TASK_FACTORY.qname(),
                MIME_REGISTRATION.qname(),
                NB_ANTLR_UTILS.qname())
                .method("createErrorHighlighter", mb -> {
                    mb.docComment("Creates a highlighter for source errors from the parser.");
                    mb.withModifier(PUBLIC).withModifier(STATIC)
                            .annotatedWith(MIME_REGISTRATION.qname())
                            .addExpressionArgument("mimeType", prefix + "Token.MIME_TYPE")
                            .addArgument("position", Integer.MAX_VALUE - 1000)
                            .addClassArgument("service", TASK_FACTORY.simpleName()).closeAnnotation()
                            .returning(TASK_FACTORY.simpleName())
                            .body(bb -> {
                                bb.returningInvocationOf("createErrorHighlightingTaskFactory")
                                        .withArgument(prefix + "Token.MIME_TYPE")
                                        .on(NB_ANTLR_UTILS.simpleName()).endBlock();
                            });
                });

        writeOne(cl);
        if (utils().annotationValue(parserInfo, "generateSyntaxTreeNavigatorPanel", Boolean.class, false)) {
            generateSyntaxTreeNavigatorPanel(mimeType, type, mirror, parser.parserEntryPoint(), lexer);
        }
        if (utils().annotationValue(parserInfo, "generateExtractionDebugNavigatorPanel", Boolean.class, false)) {
            generateExtractionDebugPanel(mimeType, type, mirror, parser.parserEntryPoint());
        }
        return nbParserName;
    }

    // Navigator generation
    private void generateExtractionDebugPanel(String mimeType, TypeElement type, AnnotationMirror mirror, ExecutableElement entryPointMethod) throws IOException {
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String generatedClassName = type.getSimpleName() + "_ExtractionNavigator_Registration";
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName)
                .withModifier(PUBLIC, FINAL)
                .docComment("Provides a generic navigator panel for the current extraction for plugin debugging")
                .importing(
                        NAVIGATOR_PANEL.qname(),
                        ABSTRACT_ANTLR_LIST_NAVIGATOR_PANEL.qname()
                )
                .method("createExtractorNavigatorPanel", mb -> {
                    mb.returning(NAVIGATOR_PANEL.qname())
                            .annotatedWith(NAVIGATOR_PANEL.simpleName() + ".Registration", ab -> {
                                ab.addArgument("displayName", "Extraction")
                                        .addArgument("mimeType", mimeType)
                                        .addArgument("position", 1000);
                            }).withModifier(PUBLIC, STATIC)
                            .body().returningInvocationOf("createExtractionDebugPanel")
                            .on(ABSTRACT_ANTLR_LIST_NAVIGATOR_PANEL.simpleName()).endBlock();
                });
        writeOne(cb);

    }

    private void generateSyntaxTreeNavigatorPanel(String mimeType, TypeElement type, AnnotationMirror mirror, ExecutableElement entryPointMethod, LexerProxy lexer) throws IOException {
        AnnotationMirror fileType = utils().annotationValue(mirror, "file", AnnotationMirror.class);

        String icon = fileType == null ? null : utils().annotationValue(fileType, "iconBase", String.class);
        Name pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName();
        String generatedClassName = type.getSimpleName() + "_SyntaxTreeNavigator_Registration";
        String entryPointType = entryPointMethod.getReturnType().toString();
        String entryPointSimple = simpleName(entryPointType);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(generatedClassName);

        TypeName.addImports(cb, BI_PREDICATE, PARSER_RULE_CONTEXT, PARSE_TREE,
                SIMPLE_NAVIGATOR_REGISTRATION, EXTRACTION_REGISTRATION, EXTRACTOR_BUILDER,
                REGIONS_KEY, TERMINAL_NODE, VOCABULARY, SET, HASH_SET);

        cb.importing(ANTLR_V4_TOKEN.qname(), entryPointType)
                .withModifier(PUBLIC)
                .docComment("Provides a generic Navigator panel which displays the syntax tree of the current file.")
                .utilityClassConstructor()
                .field("VOCABULARY", fb -> {
                    fb.withModifier(PRIVATE, STATIC, FINAL)
                            .initializedTo(lexer.lexerClassFqn() + ".VOCABULARY")
                            .ofType(VOCABULARY.simpleName());
                })
                //                .constructor(c -> {
                //                    c.setModifier(PRIVATE).body().statement("throw new AssertionError()").endBlock();
                //                })
                .field("TREE_NODES", fb -> {
                    fb.annotatedWith(SIMPLE_NAVIGATOR_REGISTRATION.simpleName(), ab -> {
                        if (icon != null) {
                            ab.addArgument("icon", icon);
                        }
                        ab.addArgument("trackCaret", true);
                        ab.addArgument("mimeType", mimeType)
                                .addArgument("order", 20000)
                                .addArgument("displayName", "Syntax Tree").closeAnnotation();
                    });
                    fb.withModifier(STATIC).withModifier(FINAL)
                            .initializedFromInvocationOf("create").withClassArgument("String").withStringLiteral("tree")
                            .on(REGIONS_KEY.simpleName()).ofType(REGIONS_KEY.parametrizedName("String"));
                    ;
                }).method("extractTree", mb -> {
            mb.withModifier(STATIC)
                    .annotatedWith(EXTRACTION_REGISTRATION.simpleName())
                    .addArgument("mimeType", mimeType)
                    .addClassArgument("entryPoint", entryPointSimple)
                    .closeAnnotation()
                    //                    .addArgument("ExtractorBuilder<? super " + entryPointSimple + ">", "bldr")
                    .addArgument(EXTRACTOR_BUILDER.parametrizedName("? super " + entryPointSimple), "bldr")
                    .body(bb -> {
                        bb.invoke("finishRegionExtractor")
                                .onInvocationOf("extractingKeyAndBoundsFromRuleWith")
                                .withArgument(simpleName(generatedClassName) + "::ruleToString")
                                .onInvocationOf("whenRuleType")
                                .withClassArgument("ParserRuleContext")
                                .onInvocationOf("extractingRegionsUnder")
                                .withArgument("TREE_NODES")
                                .on("bldr");
                    });
        }).insertText(additionalSyntaxNavigatorMethods(cb));
        writeOne(cb);
    }

    private String additionalSyntaxNavigatorMethods(ClassBuilder<?> cb) throws IOException {
        InputStream in = LanguageRegistrationProcessor.class.getResourceAsStream("additional-syntax-navigator-methods.txt");
        if (in == null) {
            throw new Error("additional-syntax-navigator-methods.txt is not on classpath next to " + LanguageRegistrationProcessor.class);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        KeybindingsAnnotationProcessor.copy(in, out);
        StringBuilder sb = new StringBuilder();
        for (String line : new String(out.toByteArray(), UTF_8).split("\n")) {
            String l = line.trim();
            if (l.startsWith("//") || l.startsWith("#")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        cb.importing(Logger.class.getName());
        cb.importing(Level.class.getName());
        cb.importing(Objects.class.getName());
        return Strings.literalReplaceAll("TARGET_CLASS", cb.fqn(), sb, false).toString();
    }

    private String generateTokenCategoryWrapper(TypeElement on, String prefix) throws IOException {
        String generatedClassName = prefix + "TokenCategory";
        ClassBuilder<String> cb = ClassBuilder.forPackage(processingEnv.getElementUtils().getPackageOf(on).getQualifiedName())
                .named(generatedClassName)
                .importing(TOKEN_CATEGORY.qname())
                .implementing(TOKEN_CATEGORY.simpleName())
                .withModifier(FINAL)
                .field("category").withModifier(PRIVATE, FINAL).ofType(STRING)
                .constructor(con -> {
                    con.addArgument(STRING, "category").body()
                            .assign("this.category").toExpression("category")
//                            .statement("this.category = category")
                            .endBlock();
                })
                .override("getName").withModifier(PUBLIC).returning(STRING).body().returning("category").endBlock()
                .override("getNumericID").withModifier(PUBLIC).returning("int").bodyReturning("0")
                .override("equals", mb -> {
                    mb.withModifier(PUBLIC).returning("boolean").addArgument("Object", "o")
                            .body(bb -> {
                                bb.iff().variable("this").equals().expression("o").endCondition()
                                        .returning("true")
                                        .elseIf().variable("o").equals().expression("null").endCondition()
                                        .returning("false")
                                        .elseIf().variable("o").instanceOf().expression(generatedClassName).endCondition()
                                        .returningInvocationOf("equals")
                                        .withArgumentFromInvoking("toString").on("o")
                                        //                                        .withArgument("o.toString()")
                                        .on("category")
                                        .orElse().returning("false").endIf();
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
    private void generateTokensClasses(String mimeType, TypeElement type, AnnotationMirror mirror, LexerProxy proxy, TypeMirror tokenCategorizerClass, String prefix, ParserProxy parser) throws Exception {

        List<AnnotationMirror> categories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        // Figure out if we are generating a token categorizer now, so we have the class name for it
        String tokenCatName = willHandleCategories(categories, tokenCategorizerClass, prefix, utils());
        String tokenTypeName = prefix + "Token";
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
                .importing(LEXER_TOKEN_ID.qname(), proxy.lexerClassFqn())
                //                .importing("javax.annotation.processing.Generated")
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .field("MIME_TYPE").withModifier(FINAL).withModifier(STATIC).withModifier(PUBLIC).initializedTo(LinesBuilder.stringLiteral(mimeType)).ofType("String")
                .method("ordinal").override().docComment("Returns the same ordinal as the generated ANTLR grammar uses for this token, e.g. " + proxy.lexerClassSimple() + ".VOCABULARY.someTokenName")
                .returning("int").closeMethod()
                .method("symbolicName").docComment("Returns the symbolic name of this token - the name it has in the originating ANTLR grammar.").returning(STRING).closeMethod()
                .method("literalName").docComment("Returns the literal name of this token's ordinal "
                + "in the generated ANTLR grammar's Vocabulary.  The literal name is only defined "
                + "for tokens which have no wildcard content (e.g. keywords, symbols - not, say, user-defined names of methods)").returning(STRING).closeMethod()
                .method("displayName").docComment("Returns the display name of this token, as defined in the generated lexer's Vocabulary.").returning(STRING).closeMethod()
                .method("primaryCategory").override().docComment("Returns the category specified in the annotation (or, if unspecified, a heuristic-derived category).").returning(STRING).closeMethod()
                .method("compareTo").docComment("Tokens compare on their ordinal.").override().returning("int").addArgument(tokenTypeName, "other").withModifier(DEFAULT)
                .body().returning("ordinal() > other.ordinal() ? 1 : ordinal() == other.ordinal() ? 0 : -1").endBlock()
                .method("name").annotatedWith("Override").closeAnnotation().returning("String").withModifier(DEFAULT)
                .docComment("Returns a name for this token, which is one of the following in order of precedence:\n"
                        + "<ul><li>The literal name</li>\n<li>The symbolic name</li>\n<li>The display name</li>\n"
                        + "<li>The string value of this token's ordinal</li></ul>")
                .body(bb -> {
                    bb.returningValue().ternary().invoke("literalName").inScope().notEquals().expression("null")
                            .endCondition().invoke("literalName").inScope()
                            .ternary().invoke("symbolicName").inScope().notEquals().expression("null")
                            .endCondition().invoke("symbolicName").inScope().ternary()
                            .invoke("displayName").inScope().notEquals().expression("null")
                            .endCondition().invoke("displayName").inScope().invoke("toString").withArgumentFromInvoking("ordinal").inScope().on("Integer");

                });
//                .body().returning("literalName() != null ? literalName() \n: symbolicName() != null \n? symbolicName() \n: "
//                        + "displayName() != null \n? displayName() \n: Integer.toString(ordinal())").endBlock();

        if (tokenWrapperClass != null) {
            cb.implementing(TOKEN_ID.qname());
            cb.importing(TOKEN_CATEGORY.qname());
            cb.override("getCategory").returning(TOKEN_CATEGORY.simpleName())
                    .withModifier(DEFAULT)
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("primaryCategory").inScope()
                                    .ofType(tokenWrapperClass);
                        });
                    });
//                    .body().returning("new " + tokenWrapperClass + "(primaryCategory())").endBlock();

            cb.override("getName").returning(STRING)
                    .withModifier(DEFAULT).body()
                    .returningInvocationOf("name").inScope().endBlock();
//                    .returning("name()").endBlock();
            cb.override("getNumericID").returning("int").withModifier(DEFAULT)
                    .body().returningInvocationOf("ordinal").inScope().endBlock();
//                    .body().returning("ordinal()").endBlock();
        }

        String tokensImplName = prefix + "TokenImpl";

        // This class will contain constants for every token type, and methods
        // for looking them up by name, character, etc.
        ClassBuilder<String> toks = ClassBuilder.forPackage(pkg).named(tokensTypeName)
                .makePublic().makeFinal()
                .docComment("Entry point for actual Token instances for the " + prefix + " language."
                        + "\nGenerated by ", getClass().getSimpleName(),
                        " from fields on ", proxy.lexerClassSimple(),
                        " from annotations on ", type.getSimpleName() + ".")
                .importing(
                        ARRAY_LIST.qname(), LIST.qname(), COLLECTIONS.qname(),
                        OPTIONAL.qname(), HASH_MAP.qname(), ARRAYS.qname(),
                        MAP.qname(), OPTIONAL.qname(),
                        TOKEN_CATEGORIZER.qname(),
                        //                        "javax.annotation.processing.Generated",
                        proxy.lexerClassFqn())
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                // Static categorizer field - fall back toExpression a heuristic categorizer if nothing is specified
                .field("CATEGORIZER").withModifier(FINAL)/*.withModifier(PRIVATE)*/.withModifier(STATIC)
                .initializedTo(tokenCatName == null ? "TokenCategorizer.heuristicCategorizer()" : "new " + tokenCatName + "()")
                .ofType("TokenCategorizer")
                // Make it non-instantiable
                .constructor().setModifier(PRIVATE).body()
                    .andThrow().ofType("AssertionError");
//                    .statement("throw new AssertionError()").endBlock();

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
                .declare("litStripped").initializedByInvoking("stripQuotes").withArgument("litName").inScope().as(STRING)
                .ifNotNull("symName")
                .invoke("put").withArgument("symName").withArgument("tok").on("bySymbolicName")
                .endIf()
                .ifNotNull("litStripped")
                .invoke("put").withArgument("litStripped").withArgument("tok").on("byLiteralName")
                .iff().invoke("length").on("litStripped").isEqualTo(1)
                .invoke("put").withArgument("litStripped.charAt(0)").withArgument("tok").on("tokForCharMap")
                .invoke("add").withArgument("litStripped.charAt(0)").on("charsList")
                .endIf()
                .endIf()
                .endBlock()
                .invoke("sort").withArgument("charsList").on("Collections")
                .assign("chars").toExpression("new char[charsList.size()]")
//                .statement("chars = new char[charsList.size()]")
//                .statement("tokForChar = new " + tokenTypeName + "[charsList.size()]")
                .assign("tokForChar").toExpression("new " + tokenTypeName + "[charsList.size()]")
                
                .forVar("i").condition().lessThan().invoke("size").on("charsList").endCondition().running()
//                .statement("chars[i] = charsList.get(i)")
//                .assign("chars[i]").toInvocation("get").withArgument("i").on("charsList")
                .assignArrayElement("i").of("chars").toInvocation("get").withArgument("i").on("charsList")
//                .statement("tokForChar[i] = tokForCharMap.get(charsList.get(i))")

                .assignArrayElement("i").of("tokForChar").toInvocation("get").withArgumentFromInvoking("get")
                .withArgument("i").on("charsList").on("tokForCharMap")
//                .assign("tokForChar[i]").toInvocation("get").withArgumentFromInvoking("get").withArgument("i")
//                    .on("charsList").on("tokForCharMap")

                .endBlock()
                .endBlock();

        // Lookup for tokens which are a single character, look them up by character
        toks.method("forSymbol").addArgument("char", "ch").returning("Optional<" + tokenTypeName + ">")
                .docComment("Look up the token id for a single character such as ';'.")
                .withModifier(STATIC).withModifier(PUBLIC).body()
                .declare("ix").initializedWith("Arrays.binarySearch(chars, ch)").as("int")
                .iff().variable("ix").isGreaterThanOrEqualTo(0)
                .returning("Optional.of(tokForChar[ix])").endIf()
                .returning("Optional.empty()").endBlock();

        // Lookup by symbolic name method
        toks.method("forSymbolicName")
                .docComment("Look up a token by its name in the ANTLR grammar.")
                .addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(bySymbolicName.get(name))").endBlock();

        // Lookup by expression name method
        toks.method("forLiteralName")
                .docComment("Look up a token by its literal text content, usable for tokens whose text is "
                        + "entirely defined in the ANTLR grammar (symbols, keywords, things with no wildcards or user defined text).")
                .addArgument("String", "name").returning("Optional<" + tokenTypeName + ">")
                .withModifier(STATIC).withModifier(PUBLIC).body().returning("Optional.ofNullable(byLiteralName.get(name))").endBlock();

        // Helper method - expression names in antlr generated code come surrounded in
        // single quotes - strip those
        toks.method("stripQuotes").addArgument("String", "name").returning("String")
                .docComment("ANTLR Vocabulary instances return text content in quotes, which need to be removed.")
                .withModifier(STATIC)/*.withModifier(PRIVATE)*/.body()
                .iff().variable("name").notEquals().expression("null")
                .and().invoke("isEmpty").on("name").equals().expression("false")
                .and().invoke("length").on("name").greaterThan().literal(1)
                .and().invoke("charAt").withArgument("0").on("name").equals().literal('\'')
                .and().invoke("charAt").withArgument("name.length()-1")
                .on("name").equals().literal('\'').endCondition().returning("name.substring(1, name.length()-1)").endIf()
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
        ClassBuilder.SwitchBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> idSwitch
                = toks.method("forId")
                        .docComment("Look up a NetBeans token by its ANTLR token ID.")
                        .withModifier(PUBLIC)
                        .withModifier(STATIC).returning(tokenTypeName)
                        .addArgument("int", "id").body().switchingOn("id");

        toks.method("all")
                .docComment("Returns the set of all tokens defined by the grammar.")
                .withModifier(STATIC).withModifier(PUBLIC)
                .returning(tokenTypeName + "[]").body()
                .returning("Arrays.copyOf(ALL, ALL.length)").endBlock();

        ClassBuilder.ArrayLiteralBuilder<ClassBuilder<String>> allArray
                = toks.field("ALL").withModifier(STATIC)
                        .withModifier(PRIVATE).withModifier(FINAL)
                        .assignedTo().toArrayLiteral(tokenTypeName);

        List<Integer> bySize = new ArrayList<>(keys);
        Collections.sort(bySize);
        // Loop and build cases in the switch statement for each token id, plus EOF,
        // and also populate our static array field which contains all of the token types
        for (Integer k : bySize) {
            String fieldName = proxy.toFieldName(k);
            String ref;

            if (!proxy.isSynthetic(k)) {
                ref = proxy.lexerClassSimple() + "." + proxy.tokenName(k);
            } else {
                ref = k.toString();
            }
            String docComment;
            if (k.intValue() == proxy.erroneousTokenId()) {
                docComment
                        = "Placeholder token used by the NetBeans lexer for content which the lexer parses as erroneous or unparseable.";
            } else {
                docComment = "Constant for NetBeans token corresponding to token "
                        + proxy.tokenName(k) + " in " + proxy.lexerClassSimple()
                        + ".VOCABULARY.";
            }

            toks.field(fieldName).withModifier(STATIC).withModifier(PUBLIC).withModifier(FINAL)
                    .docComment(docComment)
                    .initializedTo("new " + tokensImplName + "(" + ref + ")")
                    .ofType(tokenTypeName);
            idSwitch.inCase(ref).returning(fieldName).endBlock();
            if (k != -1) {
                allArray.add(fieldName);
            }
        }
        idSwitch.inDefaultCase().andThrow(nb -> {
            nb.withStringConcatentationArgument("No such ID:").with().expression("id").endConcatenation()
                    .ofType(ILLEGAL_ARGUMENT_EXCEPTION.simpleName());
        }).endBlock();
        idSwitch.build().endBlock();
        allArray.closeArrayLiteral();

        // Create our array and map fields here (so they are placed below the
        // constants in the source file - these are populated in the static block
        // defined above)
        toks.importing(HASH_MAP.qname(), MAP.qname());
        toks.lineComment("Initialize maps to token count (" + proxy.maxToken() + ") for erroneous and eof tokens");
        toks.field("bySymbolicName")
                .docComment("Mapping of Antlr vocabulary symbolic name values to token ids")
                .initializedWithNew(nb -> {
                    nb.withArgument(proxy.maxToken() + 2).ofType(HASH_MAP.parameterizedInferenced());
                }).withModifier(FINAL, PRIVATE, STATIC).ofType(MAP.parameterizedOn(JdkTypes.STRING,
                TypeName.fromQualifiedName(tokenTypeName)).simpleName());

        toks.field("byLiteralName")
                .docComment("Mapping of Antlr vocabulary literal name values to token ids")
                .initializedWithNew(nb -> {
                    nb.withArgument(proxy.maxToken() + 2).ofType(HASH_MAP.parameterizedInferenced());
                }).withModifier(FINAL, PRIVATE, STATIC).ofType(MAP.parameterizedOn(JdkTypes.STRING,
                TypeName.fromQualifiedName(tokenTypeName)).simpleName());

        toks.field("chars").withModifier(FINAL, PRIVATE, STATIC).ofType("char[]");
        toks.field("tokForChar").withModifier(FINAL, PRIVATE, STATIC).ofType(tokenTypeName + "[]");

        String adapterClassName = prefix + "LexerAdapter";

        // Build an implementation of LanguageHierarchy here
        String hierName = prefix + "Hierarchy";
        String hierFqn = pkg + "." + hierName;
        String languageMethodName = lc(prefix) + "Language";
        ClassBuilder<String> lh = ClassBuilder.forPackage(pkg).named(hierName)
                .importing(
                        LANGUAGE.qname(), LANGUAGE_HIERARCHY.qname(),
                        LEXER.qname(), LEXER_RESTART_INFO.qname(),
                        COLLECTIONS.qname(), ARRAYS.qname(), COLLECTION.qname(),
                        NB_ANTLR_UTILS.qname(),
                        CHAR_STREAM.qname(),
                        NB_LEXER_ADAPTER.qname(),
                        MAP.qname(), HASH_MAP.qname(), ARRAY_LIST.qname(),
                        CHAR_STREAM.qname(), BI_CONSUMER.qname(),
                        PARSE_RESULT_CONTENTS.qname(),
                        SNAPSHOT.qname(),
                        EXTRACTION.qname(), ANTLR_PARSE_RESULT.qname(),
                        ITERABLE_TOKEN_SOURCE.qname(),
                        //                        MIME_REGISTRATION.qname(),
                        proxy.lexerClassFqn(),
                        VOCABULARY.qname()
                )
                .docComment("LanguageHierarchy implementation for ", prefix,
                        ". Generated by ", getClass().getSimpleName(), " from fields on ", proxy.lexerClassSimple(), ".")
                //                .importing("javax.annotation.processing.Generated")
                .makePublic().makeFinal()
                .constructor().setModifier(PRIVATE).body().debugLog("Create a new " + hierName).endBlock()
                //                .annotatedWith("Generated").addArgument("value", getClass().getName()).addArgument("comments", versionString()).closeAnnotation()
                .extending("LanguageHierarchy<" + tokenTypeName + ">")
                .field("LANGUAGE").withModifier(FINAL).withModifier(STATIC).withModifier(PRIVATE)
                /*.initializedTo("new " + hierName + "().language()").*/.ofType("Language<" + tokenTypeName + ">")
                .field("CATEGORIES").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Map<String, Collection<" + tokenTypeName + ">>")
                .staticBlock(bb -> {
                    bb.declare("map")
                            .initializedWithNew(nb -> {
                                nb.ofType(HASH_MAP.parameterizedInferenced());
                            }).as(MAP.parametrizedName("String", COLLECTION.parametrizedName(simpleName(tokenTypeName))))
                            //                            .initializedWith("new " + HASH_MAP.parameterizedInferenced()).as("Map<String, Collection<" + tokenTypeName + ">>")
                            .lineComment("Assign fields here to guarantee initialization order")
                            .statement("IDS = Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()));")
                            .simpleLoop(tokenTypeName, "tok").over("IDS")
                            .iff().invoke("ordinal").on("tok").isEqualTo(-1)
                            .lineComment("Antlr has a token type -1 for EOF; editor's cache cannot handle negative ordinals")
                            .statement("continue").endIf()
                            .declare("curr").initializedByInvoking("get").withArgument("tok.primaryCategory()").on("map").as("Collection<" + tokenTypeName + ">")
                            .iff().variable("curr").equals().expression("null").endCondition()
                            .statement("curr = new ArrayList<>()")
                            .invoke("put").withArgument("tok.primaryCategory()").withArgument("curr").on("map")
                            .endIf()
                            .invoke("add").withArgument("tok").on("curr")
                            .endBlock()
                            .statement("CATEGORIES = Collections.unmodifiableMap(map)")
                            .statement("LANGUAGE = new " + hierName + "().language()")
                            .endBlock();
                })
                .field("IDS").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL).ofType("Collection<" + tokenTypeName + ">")
                //                .initializedTo("Collections.unmodifiableList(Arrays.asList(" + tokensTypeName + ".all()))").ofType("Collection<" + tokenTypeName + ">")
                .method("mimeType")
                .docComment("Get the MIME type.")
                .returning(STRING)
                .annotatedWith("Override").closeAnnotation()
                .withModifier(PROTECTED).withModifier(FINAL).body()
                .returning(tokenTypeName + ".MIME_TYPE").endBlock()
                .method(languageMethodName).withModifier(PUBLIC).withModifier(STATIC).returning(LANGUAGE.parametrizedName(simpleName(tokenTypeName)))
                //                .annotatedWith("MimeRegistration", ab -> {
                //                    ab.addArgument("mimeType", mimeType)
                //                            .addArgument("service", "Language")
                //                            .addExpressionArgument("position", 500).closeAnnotation();
                //                })
                .body().returning("LANGUAGE").endBlock()
                .method("createTokenIds").returning("Collection<" + tokenTypeName + ">").override().withModifier(PROTECTED).withModifier(FINAL).body().returning("IDS").endBlock()
                .method("createLexer").override().returning("Lexer<" + tokenTypeName + ">").addArgument("LexerRestartInfo<" + tokenTypeName + ">", "info").withModifier(PROTECTED).withModifier(FINAL)
                //                .method("createTokenIds").returning(COLLECTION.parametrizedName(simpleName(tokenTypeName))).override().withModifier(PROTECTED).withModifier(FINAL).body().returning("IDS").endBlock()
                //                .method("createLexer").override().returning(LEXER.parametrizedName(simpleName(tokenTypeName))).addArgument(
                //                        LEXER_RESTART_INFO.parametrizedName(simpleName(tokenTypeName)), "info").withModifier(PROTECTED).withModifier(FINAL)

                .body(bb -> {
                    bb.debugLog("Create a new " + prefix + " Lexer");
//                    bb.returningInvocationOf("createLexer")
//                            .withArgument("info").withArgument("LEXER_ADAPTER")
//                            .on(NB_ANTLR_UTILS.simpleName());
                    bb.returning("NbAntlrUtils.createLexer(info, LEXER_ADAPTER)").endBlock();
                })
                .method("isRetainTokenText")
                .docComment("Returns true for tokens which are entirely defined within the ANTLR grammar (keywords, symbols).")
                .override().withModifier(PROTECTED).withModifier(FINAL).returning("boolean").addArgument(tokenTypeName, "tok")
                .body().returning("tok.literalName() == null").endBlock()
                .method("createTokenCategories").override().withModifier(PROTECTED).withModifier(FINAL)
                .returning("Map<String, Collection<" + tokenTypeName + ">>").body().returning("CATEGORIES").endBlock()
                .method("createAntlrLexer")
                .returning(proxy.lexerClassSimple()).withModifier(PUBLIC).withModifier(STATIC)
                .addArgument(CHAR_STREAM.simpleName(), "stream")
                .body().returning("LEXER_ADAPTER.createLexer(stream)").endBlock()
                .method("newParseResult", mth -> {
                    mth.docComment("Parse a document and pass it to the BiConsumer.");
                    mth.withModifier(STATIC)
                            .addArgument(SNAPSHOT.simpleName(), "snapshot")
                            .addArgument(EXTRACTION.simpleName(), "extraction")
                            //                            .addArgument("BiConsumer<AntlrParseResult, ParseResultContents>", "receiver")
                            .addArgument(BI_CONSUMER.parameterizedOn(ANTLR_PARSE_RESULT, PARSE_RESULT_CONTENTS).simpleName(), "receiver")
                            .body(bb -> {
                                bb.debugLog("new " + prefix + " parse result");
                                bb.log("New {0} parse result: {1}", Level.FINE,
                                        LinesBuilder.stringLiteral(prefix),
                                        "extraction.logString()");
                                bb.invoke("newParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("LEXER_ADAPTER").endBlock();
                            });
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.docComment("Creates a token source which wraps the lexer contents and can "
                            + "have its position wound forward and backwards, for processing tools "
                            + "to process it individually without a reparse.");
                    mb.addArgument(proxy.lexerClassSimple(), "lexer")
                            .withModifier(STATIC)
                            .returning(ITERABLE_TOKEN_SOURCE.simpleName())
                            .body(body -> {
                                body.returningInvocationOf("createWrappedTokenSource")
                                        .withArgument("lexer").on("LEXER_ADAPTER").endBlock();
                            });
                });

        String adapterExtends = NB_LEXER_ADAPTER.parameterizedOn(TypeName.fromQualifiedName(tokenTypeName), TypeName.fromQualifiedName(proxy.lexerClassFqn())).simpleName();
        // BiConsumer<AntlrParseResult, ParseResultContents>
        String callbackType = BI_CONSUMER.parameterizedOn(ANTLR_PARSE_RESULT, PARSE_RESULT_CONTENTS).simpleName();

        // Inner implementation of NbLexerAdapter, which allows us toExpression use a generic
        // Lexer class and takes care of creating the Antlr lexer and calling methods
        // that will exist on the lexer implementation class but not on the parent class
        lh.innerClass(adapterClassName).withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .extending(adapterExtends)
                //                .extending("NbLexerAdapter<" + tokenTypeName + ", " + proxy.lexerClassSimple() + ">")
                .method("createLexer").override().withModifier(PUBLIC).returning(proxy.lexerClassSimple()).addArgument("CharStream", "stream")
                .docComment("Creates a NetBeans lexer wrapper for an ANTLR lexer.")
                .body(bb -> {
                    bb.declare("result").initializedWith("new " + proxy.lexerClassSimple() + "(stream)").as(proxy.lexerClassSimple());
                    bb.invoke("removeErrorListeners").on("result");
                    bb.returning("result").endBlock();
                })
                .method("createWrappedTokenSource", mb -> {
                    mb.docComment("Creates a token source which wraps the lexer contents and can "
                            + "have its position wound forward and backwards, for processing tools "
                            + "to process it individually without a reparse.");
                    mb.addArgument(proxy.lexerClassSimple(), "lexer").returning("IterableTokenSource")
                            .body().returningInvocationOf("wrapLexer")
                            .withArgument("lexer").on("super").endBlock();
                })
                .override("vocabulary").withModifier(PROTECTED).returning(VOCABULARY.simpleName())
                .body().returning(proxy.lexerClassSimple() + ".VOCABULARY").endBlock()
                .method("tokenId").withModifier(PUBLIC).override().addArgument("int", "ordinal").returning(tokenTypeName)
                .body().returning(tokensTypeName + ".forId(ordinal)").endBlock()
                .method("setInitialStackedModeNumber").override().withModifier(PUBLIC).addArgument(proxy.lexerClassSimple(), "lexer")
                .addArgument("int", "modeNumber").body(
                bb -> {
                    // XXX proxy should detect if there are multiple mode names and
                    // if so, warn the module author that they should add these methods,
                    // if they are still used
                    if (proxy.hasInitialStackedModeNumberMethods(utils())) {
                        bb.lineComment("This method does not exist by default in ANTLR lexers, but is");
                        bb.lineComment("detected by the annotation processor, and can be used to ensure correct");
                        bb.lineComment("lexer restarting in the presence of a lexer grammar that has mode.");
                        bb.statement("lexer.setInitialStackedModeNumber(modeNumber)");
                    } else {
                        bb.lineComment("If the lexer contains a @lexer::members block defining a getter/setter pair for initialStackedModeNumber");
                        bb.lineComment("an implementation will be generated for this method to call it, e.g.");
                        bb.lineComment("protected int initialStackedModeNumber = -1;");
                        bb.lineComment("public int getInitialStackedModeNumber() {");
                        bb.lineComment("    return initialStackedModeNumber;");
                        bb.lineComment("}");
                        bb.lineComment("public void setInitialStackedModeNumber(int initialStackedModeNumber) {");
                        bb.lineComment("    this.initialStackedModeNumber = initialStackedModeNumber;");
                        bb.lineComment("}");
                        bb.lineComment(" ");
                        bb.lineComment("It is only needed in the case of lexer grammars that have modes, to ensure");
                        bb.lineComment("NetBeans restartable lexers restart lexing in the correct state");
                    }
                })
                .overridePublic("getInitialStackedModeNumber").returning("int").addArgument(proxy.lexerClassSimple(), "lexer")
                .body(bb -> {
                    if (proxy.hasInitialStackedModeNumberMethods(utils())) {
                        bb.lineComment("This method does not exist by default in ANTLR lexers, but is");
                        bb.lineComment("detected by the annotation processor, and can be used to ensure correct");
                        bb.lineComment("lexer restarting in the presence of a lexer grammar that has mode.");
                        bb.returning("lexer.getInitialStackedModeNumber()").endBlock();
                    } else {
                        bb.lineComment("If the lexer contains a @lexer::members block defining a getter/setter pair for initialStackedModeNumber");
                        bb.lineComment("an implementation will be generated for this method to call it, e.g.");
                        bb.lineComment("protected int initialStackedModeNumber = -1;");
                        bb.lineComment("public int getInitialStackedModeNumber() {");
                        bb.lineComment("    return initialStackedModeNumber;");
                        bb.lineComment("}");
                        bb.lineComment("public void setInitialStackedModeNumber(int initialStackedModeNumber) {");
                        bb.lineComment("    this.initialStackedModeNumber = initialStackedModeNumber;");
                        bb.lineComment("}");
                        bb.lineComment("It is only needed in the case of lexer grammars that have modes, to ensure");
                        bb.lineComment("NetBeans restartable lexers restart lexing in the correct state");
                        bb.returning(-1);
                    }
                })
                .method("newParseResult", mth -> {
                    mth.docComment("Invokes ANTLR and creates a parse result.");
                    mth.addArgument("Snapshot", "snapshot")
                            .addArgument("Extraction", "extraction")
                            .addArgument(callbackType, "receiver")
                            .body(bb -> {
                                bb.lineComment("This method simply allows classes in this package to have access to");
                                bb.lineComment("the createParseResult() super method of " + NB_LEXER_ADAPTER.simpleName());
                                bb.lineComment("without exposing it to any code that can see this package");
                                bb.invoke("createParseResult")
                                        .withArgument("snapshot")
                                        .withArgument("extraction")
                                        .withArgument("receiver").on("super").endBlock();
                            });
                })
                .build();

        lh.field("LEXER_ADAPTER")
                .docComment("Adapter which lets us talk to ANTLR.")
                .initializedTo("new " + adapterClassName + "()").withModifier(PRIVATE).withModifier(STATIC).withModifier(FINAL)
                .ofType(adapterClassName);
        //.ofType("NbLexerAdapter<" + tokenTypeName + ", " + lexerClass + ">");

        List<AnnotationMirror> embeddings = utils().annotationValues(mirror, "embeddedLanguages", AnnotationMirror.class);
        if (!embeddings.isEmpty()) {
            String tokenInterface = cb.className();
            Map<Integer, String> mimeTypeForToken = new TreeMap<>();
            Map<Integer, String> helperForToken = new TreeMap<>();
            Map<String, Set<Integer>> tokensForMimeType = CollectionUtils.supplierMap(TreeSet::new);
            Map<String, Set<Integer>> tokensForHelper = CollectionUtils.supplierMap(TreeSet::new);
            Map<String, Integer> startSkipLengths = new HashMap<>();
            Map<String, Integer> endSkipLengths = new HashMap<>();
            Set<String> joinSections = new HashSet<>();
            Map<String, String> embPresences = new HashMap<>();

            Set<Integer> seenTokens = new HashSet<>();
            boolean ok = true;

            for (AnnotationMirror emb : embeddings) {
                String key;
                List<Integer> embToks = utils().annotationValues(emb, "tokens", Integer.class);
                if (embToks.isEmpty()) {
                    ok = false;
                    utils().fail("Language embedding does not define any tokens to match", type, emb);
                    continue;
                }
                int oldSize = seenTokens.size();
                seenTokens.addAll(embToks);
                if (seenTokens.size() != oldSize + embToks.size()) {
                    ok = false;
                    utils().fail("The same token is used for more than one embedding", type, emb);
                    continue;
                }
                String mime = utils().annotationValue(emb, "mimeType", String.class);
                if (mime == null || mime.isEmpty()) {
                    List<String> helperClass = utils().classNamesForAnnotationMember(type,
                            "org.nemesis.antlr.spi.language.AntlrLanguageRegistration$Embedding",
                            "helper", EMBEDDING_HELPER.qname());
                    if (helperClass.isEmpty()) {
                        ok = false;
                        utils().fail("Either a mime type or a helper must be specified", type, emb);
                        continue;
                    } else {
                        String helper = helperClass.iterator().next();
                        key = helper;
                        for (Integer tok : embToks) {
                            helperForToken.put(tok, helper);
                            tokensForHelper.get(helper).add(tok);
                        }
                    }
                } else {
                    key = mime;
                    for (Integer tok : embToks) {
                        mimeTypeForToken.put(tok, mime);
                        tokensForMimeType.get(mime).add(tok);
                    }
                }
                Integer startSkipLength = utils().annotationValue(emb, "startSkipLength", Integer.class, 0);
                Integer endSkipLength = utils().annotationValue(emb, "endSkipLength", Integer.class, 0);
                boolean joinSectionsValue = utils().annotationValue(emb, "joinSections", Boolean.class, Boolean.TRUE);
                if (joinSectionsValue) {
                    joinSections.add(key);
                }
                startSkipLengths.put(key, startSkipLength);
                endSkipLengths.put(key, endSkipLength);
                String embPresence = utils().enumConstantValue(mirror, "presence", "CACHED_FIRST_QUERY");
                embPresences.put(key, embPresence);
            }
            if (ok) {
                Map<String, String> fieldNameForHelper = new HashMap<>();
                for (String h : tokensForHelper.keySet()) {
                    String fn = h.replace('.', '_').toLowerCase();
                    fieldNameForHelper.put(h, fn);
                    lh.importing(h).field(fn).initializedWithNew().ofType(simpleName(h));
                }
                lh.importing(LANGUAGE_EMBEDDING.qname(), EMBEDDING_PRESENCE.qname(), LANGUAGE_HIERARCHY.qname(),
                        LANGUAGE.qname(), LEXER_TOKEN.qname(), proxy.lexerClassFqn(),
                        TOKEN_ID.qname(), "org.netbeans.api.lexer.LanguagePath", INPUT_ATTRIBUTES.qname())
                        .overridePublic("embeddingPresence", mb -> {
                            mb.returning(KnownTypes.EMBEDDING_PRESENCE.simpleName())
                                    .addArgument(simpleName(tokenInterface), "id")
                                    .body(bb -> {
                                        ClassBuilder.SwitchBuilder<?> sw = bb.switchingOn("id.ordinal()");
                                        for (Map.Entry<Integer, String> e : mimeTypeForToken.entrySet()) {
//                                            proxy.toFieldName(e.getKey());
                                            sw.inCase(proxy.tokenFieldReference(e.getKey()), swb -> {
                                                String mime = e.getValue();
                                                swb.returning(EMBEDDING_PRESENCE.simpleName() + "." + embPresences.get(mime));
                                            });
                                        }
                                        for (Map.Entry<Integer, String> e : helperForToken.entrySet()) {
                                            sw.inCase(proxy.tokenFieldReference(e.getKey()), swb -> {
                                                String helper = e.getValue();
                                                swb.returning(EMBEDDING_PRESENCE.simpleName() + "." + embPresences.get(helper));
                                            });
                                        }
                                        sw.inDefaultCase().returningNull().endBlock();
//                                        bb.returningNull();
                                        sw.build();
                                    });
                        })
                        /*
protected LanguageEmbedding<?> embedding(Token<AntlrToken> token, LanguagePath languagePath, InputAttributes inputAttributes) {
                         */
                        .overrideProtected("embedding", mb -> {
                            mb.returning(LANGUAGE_EMBEDDING.parametrizedName("?"))
                                    .addArgument(LEXER_TOKEN.parametrizedName(tokenInterface), "token")
                                    .addArgument("LanguagePath", "path")
                                    .addArgument(INPUT_ATTRIBUTES.simpleName(), "attrs")
                                    .body(bb -> {
                                        bb.declare("lang").as(LANGUAGE.parametrizedName("?"));
                                        ClassBuilder.SwitchBuilder<?> sw = bb.switchingOn("token.id().ordinal()");
                                        for (Map.Entry<Integer, String> e : mimeTypeForToken.entrySet()) {
//                                            proxy.toFieldName(e.getKey());
                                            sw.inCase(proxy.tokenFieldReference(e.getKey()), swb -> {
//                                                swb.returningInvocationOf("create")
                                                String mime = e.getValue();
                                                int startSkip = startSkipLengths.getOrDefault(mime, 0);
                                                int endSkip = endSkipLengths.getOrDefault(mime, 0);
                                                boolean join = joinSections.contains(mime);
                                                swb.assign("lang").toInvocation("find").withStringLiteral(mime)
                                                        .on(LANGUAGE.simpleName());
//                                                swb.assign("lang").toExpression("(Language<T>) Language.find(\"" + mime + "\"");
                                                swb.ifNotNull("lang").returningInvocationOf("create")
                                                        .withArgument("lang")
                                                        .withArgument(startSkip)
                                                        .withArgument(endSkip)
                                                        .withArgument(join)
                                                        .on(LANGUAGE_EMBEDDING.simpleName()).endIf();
                                                swb.statement("break");
                                            });
                                        }
                                        for (Map.Entry<Integer, String> e : helperForToken.entrySet()) {
                                            sw.inCase(proxy.tokenFieldReference(e.getKey()), swb -> {
                                                String helper = e.getValue();
                                                String field = fieldNameForHelper.get(helper);
                                                assert field != null;
                                                int startSkip = startSkipLengths.getOrDefault(helper, 0);
                                                int endSkip = endSkipLengths.getOrDefault(helper, 0);
                                                boolean join = joinSections.contains(helper);
                                                // Ugly, but java-vogon doesn't have a "cast()" method yet for
                                                // assignments.  Should be an alternate to as(type), castTo(type)
                                                swb.assign("lang").toExpression("Language.find("
                                                        + field + ".mimeTypeForEmbedding (hierarchy, token, path, attrs"
                                                        + ")");
                                                swb.ifNotNull("lang").returningInvocationOf("create")
                                                        .withArgument("lang")
                                                        .withArgument(startSkip)
                                                        .withArgument(endSkip)
                                                        .withArgument(join)
                                                        .on(LANGUAGE_EMBEDDING.simpleName()).endIf();
                                                swb.statement("break");
                                            });
                                        }
                                        sw.inDefaultCase().returningNull().endBlock();
                                        sw.build();
                                        bb.returningNull();
                                    });
                        });
            }
        }

        // Save generated source files toExpression disk
        writeOne(cb);
        writeOne(toks);
        writeOne(lh);

        if (categories != null) {
            handleCategories(categories, proxy, type, mirror, tokenCategorizerClass, tokenCatName, pkg, prefix);
            if (parser != null) {
                maybeGenerateParserRuleExtractors(mimeType, categories, proxy, type, mirror, tokenCategorizerClass, hierName, pkg, prefix, parser);
            }
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

        withLayer(lb -> {
            log("Run layer task to create bundle info");
            // For some reason, the generated @MimeRegistration annotation is resulting
            // in an attempt toExpression load a *class* named with the fqn + method name.
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

    private void maybeGenerateParserRuleExtractors(String mimeType, List<AnnotationMirror> categories, LexerProxy lexer, TypeElement type, AnnotationMirror mirror, TypeMirror tokenCategorizerClass, String catName, Name pkg, String prefix, ParserProxy parser) throws Exception {
        List<AnnotationMirror> relevant = new ArrayList<>(categories.size() / 2);
        Map<Integer, Set<String>> ownership = CollectionUtils.supplierMap(TreeSet::new);
        for (AnnotationMirror am : categories) {
            List<Integer> parserRuleIds = utils().annotationValues(am, "parserRuleIds", Integer.class);
            if (!parserRuleIds.isEmpty()) {
                relevant.add(am);
                Set<Integer> overlap = new HashSet<>(ownership.keySet());
                overlap.retainAll(parserRuleIds);
                String categoryName = utils().annotationValue(am, "name", String.class, "<unnamed>");
                if (!overlap.isEmpty()) {
                    for (Integer i : overlap) {
                        String name = parser.nameForRule(i);
                        Set<String> others = ownership.get(i);
                        StringBuilder sb = new StringBuilder(64).append(name)
                                .append(" is used by multiple token categories: ");
                        for (Iterator<String> it = others.iterator(); it.hasNext();) {
                            sb.append(it.next());
                            if (it.hasNext()) {
                                sb.append(", ");
                            }
                        }
                        sb.append(" and ").append(categoryName).append(". ")
                                .append("One will win and others will lose.");
                        utils().warn(sb.toString(), type, am);
                    }
                }
            }
        }
        for (AnnotationMirror am : relevant) {
            Set<Integer> parserRuleIds = new TreeSet<>(utils().annotationValues(am, "parserRuleIds", Integer.class));
            StringBuilder genClassName = new StringBuilder(prefix).append('_');
            StringBuilder keyFieldName = new StringBuilder("KEY");
            String categoryName = utils().annotationValue(am, "name", String.class, "<unnamed>");
            genClassName.append(toUsableFieldName(categoryName));
            List<String> fieldNames = new ArrayList<>(parserRuleIds.size());
            for (int val : parserRuleIds) {
                String nm = parser.nameForRule(val);
                genClassName.append('_').append(nm);
                keyFieldName.append('_').append(nm.toUpperCase());
                fieldNames.add(parser.ruleFieldForRuleId(val));
            }
            genClassName.append("RuleHighlighting");
            ClassBuilder<String> cb = ClassBuilder.forPackage(utils().packageName(type)).named(genClassName.toString())
                    .makePublic().makeFinal()
                    .importing(
                            EXTRACTION_REGISTRATION.qname(),
                            EXTRACTOR_BUILDER.qname(),
                            REGIONS_KEY.qname(),
                            HIGHLIGHTER_KEY_REGISTRATION.qname(),
                            parser.parserEntryPointReturnTypeFqn(),
                            parser.parserClassFqn())
                    .field(keyFieldName.toString(), fb -> {
                        fb.withModifier(PUBLIC, STATIC, FINAL)
                                .annotatedWith(HIGHLIGHTER_KEY_REGISTRATION.simpleName(), ab -> {
                                    ab.addArgument("mimeType", mimeType)
                                            .addArgument("coloringName", categoryName);
                                })
                                .initializedFromInvocationOf("create")
                                .withClassArgument("String")
                                .withStringLiteral(keyFieldName.toString())
                                .on(REGIONS_KEY.simpleName())
                                .ofType(REGIONS_KEY.parametrizedName("String"));
                    })
                    .method("extract", mb -> {
                        mb.annotatedWith(EXTRACTION_REGISTRATION.simpleName(), ab -> {
                            ab.addArgument("mimeType", mimeType)
                                    .addClassArgument("entryPoint", parser.parserEntryPointReturnTypeSimple());
                        }).withModifier(PUBLIC, STATIC)
                                .addArgument(EXTRACTOR_BUILDER.simpleName() + "<? super "
                                        + parser.parserEntryPointReturnTypeSimple() + ">", "bldr")
                                .body(bb -> {
                                    bb.invoke("finishRegionExtractor")
                                            .onInvocationOf("extractingKeyWith")
                                            .withLambdaArgument(lb -> {
                                                lb.withArgument("rule")
                                                        .body(lbb -> {
                                                            lbb.returning(parser.parserClassSimple()
                                                                    + ".ruleNames[rule.getRuleIndex()]");
                                                        });
                                            }).onInvocationOf("whenRuleIdIn")
                                            .withNewArrayArgument("int", avb -> {
                                                fieldNames.forEach((fn) -> {
                                                    avb.expression(parser.parserClassSimple() + "." + fn);
                                                });
                                            }).onInvocationOf("extractingRegionsUnder")
                                            .withArgument(keyFieldName.toString())
                                            .on("bldr");
                                });

                    });
            ;
            writeOne(cb);
        }
    }

    static String toUsableFieldName(String s) {
        StringBuilder sb = new StringBuilder();
        int max = s.length();
        for (int i = 0; i < max; i++) {
            char c = s.charAt(i);
            boolean valid;
            switch (i) {
                case 0:
                    valid = Character.isJavaIdentifierStart(c);
                    if (valid) {
                        c = Character.toLowerCase(c);
                    }
                    break;
                default:
                    valid = Character.isJavaIdentifierPart(c);
            }
            if (!valid) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void handleCategories(List<AnnotationMirror> categories, LexerProxy lexer, TypeElement type, AnnotationMirror mirror, TypeMirror tokenCategorizerClass, String catName, Name pkg, String prefix) throws Exception {
        // Create an implementation of TokenCategorizer based on the annotation values
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(catName).withModifier(FINAL)
                .importing(TOKEN_CATEGORIZER.qname())
                .implementing(TOKEN_CATEGORIZER.simpleName())
                .conditionally(tokenCategorizerClass != null, cbb -> {
                    cbb.importing(tokenCategorizerClass.toString());
                    cbb.field("DECLARATION_CATEGORIZER")
                            .initializedWithNew(nb -> {

                                nb.ofType(simpleName(tokenCategorizerClass));
                            }).ofType(TOKEN_CATEGORIZER.simpleName());
//                    cbb.field("DEFAULT_CATEGORIZER").initializedTo("new " + tokenCategorizerClass + "()").ofType("TokenCategorizer");
                });

        // categoryFor(ordinal(), displayName(), symbolicName(), literalName())
        cb.overridePublic("categoryFor", catFor -> {
            catFor.docComment("Find the category for a token, either by returning the value "
                    + "specified in an annotation, or using a heuristic.");
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
                            bb.iff().variable("result").notEquals().expression("null").endCondition()
                                    .returning("result").endIf();
                        }).switchingOn("id", (ClassBuilder.SwitchBuilder<?> sw) -> {
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
//                            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
//                                    sb, type, mirror);
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

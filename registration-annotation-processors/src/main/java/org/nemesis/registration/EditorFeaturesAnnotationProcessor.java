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
import static com.mastfrog.annotation.AnnotationUtils.stripMimeType;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.function.throwing.io.IOConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.EditorFeaturesAnnotationProcessor.EDITOR_FEATURES_ANNO;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.CRITERIA;
import static org.nemesis.registration.typenames.KnownTypes.DELETED_TEXT_INTERCEPTOR;
import static org.nemesis.registration.typenames.KnownTypes.EDITOR_FEATURES;
import static org.nemesis.registration.typenames.KnownTypes.INT_PREDICATES;
import static org.nemesis.registration.typenames.KnownTypes.MIME_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.NB_BUNDLE;
import static org.nemesis.registration.typenames.KnownTypes.OPTIONS_PANEL_CONTROLLER;
import static org.nemesis.registration.typenames.KnownTypes.TYPED_TEXT_INTERCEPTOR;

/**
 *
 * @author Tim Boudreau
 */
//@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(EDITOR_FEATURES_ANNO)
//@ServiceProvider(service = Processor.class, position = 100)
public final class EditorFeaturesAnnotationProcessor extends LayerGeneratingDelegate {

    public static final String EDITOR_FEATURES_ANNO = "com.mastfrog.editor.features.annotations.EditorFeaturesRegistration";

    /*
    Boilerplate[] insertBoilerplate() default {};
    Elision[] elideTypedChars() default {};
    DelimiterPair[] deleteMatchingDelimiter() default {};
     */
    @Override
    protected boolean processTypeAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        return handleAnnotation(type, mirror, roundEnv, type);
    }

    private boolean handleAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv, Element orig) throws Exception {
        return handleAnnotation(type, mirror, roundEnv, orig, utils(), this::writeOne);
    }

    private static LexerInfo lexer(AnnotationMirror mirror, Element target, AnnotationUtils utils) {
        TypeMirror lexerType = utils.typeForSingleClassAnnotationMember(mirror, "lexer");
        if (lexerType == null) {
            return LexerInfo.UNKNOWN;
        }
        LexerProxy lexer = LexerProxy.create(lexerType, target, utils);
        return lexer;
    }

    static boolean handleAnnotation(TypeElement type, AnnotationMirror mirror, RoundEnvironment roundEnv, Element orig, AnnotationUtils utils, IOConsumer<ClassBuilder<String>> classes) throws Exception {

        List<AnnotationMirror> boilerplates = utils.annotationValues(mirror, "insertBoilerplate", AnnotationMirror.class);
        List<AnnotationMirror> elisions = utils.annotationValues(mirror, "elideTypedChars", AnnotationMirror.class);
        List<AnnotationMirror> delimiterPairs = utils.annotationValues(mirror, "deleteMatchingDelimiter", AnnotationMirror.class);

        System.out.println("HANDLE ANNO " + mirror + " with " + boilerplates.size() + " elisions " + elisions.size() + " " + delimiterPairs.size());
        boolean hasNames = false;
        for (AnnotationMirror m : CollectionUtils.concatenate(boilerplates, elisions, delimiterPairs)) {
            String nm = utils.annotationValue(m, "name", String.class);
            hasNames = nm != null;
            if (hasNames) {
                break;
            }
        }
        if (!boilerplates.isEmpty() || !elisions.isEmpty() || !delimiterPairs.isEmpty()) {
            String mime = utils.annotationValue(mirror, "mimeType", String.class);
            String generatedClassName = stripMimeType(mime).toUpperCase() + type.getSimpleName() + "EditorFeatures";
            int pos = utils.annotationValue(mirror, "order", Integer.class, Math.min(Integer.MAX_VALUE - 1000, Math.abs(type.getQualifiedName().toString().hashCode())));
            AtomicInteger layerPos = new AtomicInteger(pos);

            LexerInfo lexer = lexer(mirror, type, utils);
            final boolean isAntlrLexer = lexer.lexerClassFqn() != null;

            Map<String, String> bundleItems = new TreeMap<>();

            final String crit = isAntlrLexer ? "CRIT" : INT_PREDICATES.simpleName();

            ClassBuilder<String> cb = ClassBuilder
                    .forPackage(utils.packageName(type))
                    .named(generatedClassName)
                    .withModifier(PUBLIC, FINAL)
                    .importing(EDITOR_FEATURES.qname())
                    .conditionally(isAntlrLexer, classb -> {
                        classb.importing(lexer.lexerClassFqn(), CRITERIA.qname());
                        classb.field("CRIT", fb -> {
                            fb.withModifier(PRIVATE, STATIC, FINAL)
                                    .initializedFromInvocationOf("forVocabulary")
                                    .withArgument(lexer.lexerClassSimple() + ".VOCABULARY")
                                    .on(CRITERIA.simpleName())
                                    .ofType(CRITERIA.simpleName());
                        });
                    })
                    .conditionally(!isAntlrLexer, classb -> {
                        classb.importing(INT_PREDICATES.qname());
                    })
                    .extending(EDITOR_FEATURES.simpleName())
                    .conditionally(hasNames, classb -> {
                        classb.method("instance")
                                .withModifier(PUBLIC, STATIC)
                                .annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                                    ab.addArgument("mimeType", mime)
                                            .addArgument("position", layerPos.getAndIncrement())
                                            .addClassArgument("service", EDITOR_FEATURES.simpleName());
                                }).returning(EDITOR_FEATURES.simpleName()).bodyReturning("INSTANCE");
                        classb.importing(OPTIONS_PANEL_CONTROLLER.qname(), NB_BUNDLE.qname(), MIME_REGISTRATION.qname());
                        String dnKey = "AdvancedOption_DisplayName_" + stripMimeType(mime) + "_Options";
                        String keywordsKey = "AdvancedOptions_Keywords_" + stripMimeType(mime) + "_Options";
                        String dn = utils.annotationValue(mirror, "languageDisplayName", String.class, stripMimeType(mime));

                        classb.method("optionsPanelRegistration")
                                .withModifier(PUBLIC, STATIC)
                                .returning(OPTIONS_PANEL_CONTROLLER.simpleName())
                                .annotatedWith(OPTIONS_PANEL_CONTROLLER.simpleName() + ".SubRegistration", ab -> {
                                    ab.addArgument("location", "Editor")
                                            .addArgument("displayName", "#" + dnKey)
                                            .addArgument("keywords", "#" + keywordsKey)
                                            .addArgument("keywordsCategory", "Editor/" + stripMimeType(mime) + "Options");
                                })
                                .annotatedWith(NB_BUNDLE.simpleName() + ".Messages", ab -> {
                                    ab.addArrayArgument("value")
                                            .literal(dnKey + "=" + dn)
                                            .literal(keywordsKey + "=" + dn + " " + stripMimeType(mime))
                                            .closeArray();
                                })
                                .body().returningInvocationOf("optionsPanelController")
                                .on("INSTANCE").endBlock();
                    }).field("INSTANCE")
                    .withModifier(PRIVATE, STATIC, FINAL)
                    .initializedWithNew().ofType(generatedClassName).ofType(generatedClassName)
                    .conditionally(!boilerplates.isEmpty() || !elisions.isEmpty(), classb -> {
                        classb.importing(TYPED_TEXT_INTERCEPTOR.qname())
                                .method("typingFactoryRegistration")
                                .withModifier(PUBLIC, STATIC)
                                .returning(TYPED_TEXT_INTERCEPTOR.simpleName() + ".Factory")
                                .annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                                    ab.addArgument("mimeType", mime)
                                            .addArgument("position", layerPos.getAndIncrement())
                                            .addClassArgument("service",
                                                    TYPED_TEXT_INTERCEPTOR.simpleName() + ".Factory");
                                }).bodyReturning("INSTANCE.typingFactory()");
                        ;
                    })
                    .conditionally(!delimiterPairs.isEmpty(), classb -> {
                        classb.importing(DELETED_TEXT_INTERCEPTOR.qname())
                                .method("deletionFactoryRegistration")
                                .withModifier(PUBLIC, STATIC)
                                .returning(DELETED_TEXT_INTERCEPTOR.simpleName() + ".Factory")
                                .annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                                    ab.addArgument("mimeType", mime)
                                            .addArgument("position", layerPos.getAndIncrement())
                                            .addClassArgument("service",
                                                    DELETED_TEXT_INTERCEPTOR.simpleName() + ".Factory");
                                }).bodyReturning("INSTANCE.deletionFactory()");
                    })
                    .constructor(con -> {
                        con.setModifier(PUBLIC).body(bb -> {
                            bb.invoke("super")
                                    .withStringLiteral(mime).withLambdaArgument(lb -> {
                                lb.withArgument("bldr").body(lbb -> {
                                    for (AnnotationMirror bp : boilerplates) {
                                        ClassBuilder.InvocationBuilder<?> ib = lbb
                                                .invoke("whenKeyTyped").withArgument()
                                                .literal(utils.annotationValue(bp, "onChar", Character.class));

                                        String lp = utils.enumConstantValue(bp, "linePosition", "ANY");
                                        switch (lp) {
                                            case "AT_START":
                                                ib = ib.onInvocationOf("onlyWhenNotAtLineEnd");
                                            case "AT_END":
                                                ib = ib.onInvocationOf("onlyWhenAtLineEnd");
                                                break;
                                        }
                                        List<Integer> not = utils.annotationValues(bp, "whenCurrentTokenNot", Integer.class);
                                        if (!not.isEmpty()) {
                                            if (not.size() == 1) {
                                                ib = ib.onInvocationOf("whenCurrentTokenNot")
                                                        .withArgumentFromInvoking("matching")
                                                        .withArgument(lexer.tokenFieldReference(lexer.tokenName(not.iterator().next())))
                                                        .on(crit);
                                            } else {
                                                ClassBuilder.InvocationBuilder<?> xib = ib
                                                        .onInvocationOf("whenCurrentTokenNot")
                                                        .withArgumentFromInvoking("anyOf");
                                                for (Integer nm : CollectionUtils.reversed(not)) {
                                                    xib = xib.withArgument(lexer.tokenFieldReference(lexer.tokenName(nm)));
                                                }
                                                ib = (ClassBuilder.InvocationBuilder<?>) xib.on(crit);
                                            }
                                        }
                                        List<Integer> preceding = utils.annotationValues(bp, "whenPrecedingToken", Integer.class);
                                        if (!preceding.isEmpty()) {
                                            if (preceding.size() == 1) {
                                                ib = ib.onInvocationOf("whenPrecedingToken")
                                                        .withArgumentFromInvoking("matching")
                                                        .withArgument(lexer.tokenFieldReference(preceding.iterator().next()))
                                                        .on(crit);
                                            } else {
                                                ClassBuilder.InvocationBuilder<ClassBuilder.InvocationBuilder<?>> xib = (ClassBuilder.InvocationBuilder<ClassBuilder.InvocationBuilder<?>>) ib
                                                        .onInvocationOf("whenPrecedingToken")
                                                        .withArgumentFromInvoking("anyOf");
                                                for (Integer nm : CollectionUtils.reversed(preceding)) {
                                                    xib = xib.withArgument(lexer.tokenFieldReference(nm));
                                                }
                                                ib = xib.on(crit);
                                            }
                                        }

                                        String precedingPattern = utils.annotationValue(bp, "whenPrecededByPattern", String.class);
                                        if (precedingPattern != null) {
                                            ib = handleTokenPattern(utils, crit, orig, lexer, bp, precedingPattern, type, mirror, ib);
                                        }
                                        String followingPattern = utils.annotationValue(bp, "whenFollowedByPattern", String.class);
                                        if (followingPattern != null) {
                                            ib = handleTokenPattern(utils, crit, orig, lexer, bp, followingPattern, type, mirror, ib);
                                        }
                                        String name = utils.annotationValue(bp, "name", String.class);
                                        if (name != null) {
                                            ib = applyNameDescriptionAndCategory(bp, utils, ib, lbb, bundleItems);
                                        }
                                        ib.onInvocationOf("insertBoilerplate")
                                                .withStringLiteral(utils.annotationValue(bp, "inserting", String.class))
                                                .on("bldr");
                                    }
                                    for (AnnotationMirror el : elisions) {

                                        ClassBuilder.InvocationBuilder<?> ib = null;
//                                        lbb
//                                                .invoke("whenKeyTyped").withArgument()
//                                                .literal(utils().annotationValue(el, "onChar",
//                                                        Character.class));

                                        List<Integer> whenNotIn = utils.annotationValues(el, "whenNotIn", Integer.class);
                                        if (!whenNotIn.isEmpty()) {
                                            ib = lbb.invoke("whenCurrentTokenNot");
                                            if (whenNotIn.size() == 1) {
                                                ib = ib.withArgumentFromInvoking("matching")
                                                        .withArgument(lexer.tokenFieldReference(whenNotIn.get(0)))
                                                        .on(crit);
                                            } else {
                                                ClassBuilder.InvocationBuilder<?> xib = ib.withArgumentFromInvoking("anyOf");
                                                for (Integer wni : whenNotIn) {
                                                    xib = xib.withArgument(lexer.tokenFieldReference(wni));
                                                }
                                                xib.on(crit);
                                            }
                                        }
                                        String name = utils.annotationValue(el, "name", String.class);
                                        if (name != null) {
                                            ib = applyNameDescriptionAndCategory(el, utils, ib, lbb, bundleItems);
                                        }
                                        String targ = utils.annotationValue(el, "backward", Boolean.class, Boolean.FALSE)
                                                ? "elidePreceding" : "elide";
                                        ib.onInvocationOf(targ).withArgument().literal(utils.annotationValue(el, "onKeyTyped", Character.class))
                                                .on("bldr");
                                    }
                                    for (AnnotationMirror delim : delimiterPairs) {
                                        ClassBuilder.InvocationBuilder<?> ib = lbb.invoke("closingPairWith")
                                                .withArgument(lexer.tokenFieldReference(utils.annotationValue(delim, "closingToken", Integer.class)));

                                        List<Integer> ignore = utils.annotationValues(delim, "ignoring", Integer.class);
                                        if (!ignore.isEmpty()) {
                                            if (ignore.size() == 1) {
                                                ib.onInvocationOf("ignoring").withArgument(lexer.tokenFieldReference(ignore.get(0))).on(crit);
                                            } else {
                                                ClassBuilder.InvocationBuilder<?> xib = ib.onInvocationOf("ignoring");
                                                for (Integer ign : ignore) {
                                                    xib.withArgument(lexer.tokenFieldReference(ign));
                                                }
                                                xib.on(crit);
                                            }
                                        }
                                        ib = applyNameDescriptionAndCategory(delim, utils, ib, lbb, bundleItems)
                                                .onInvocationOf("deleteMateOf");
                                        ib.withArgument(lexer.tokenFieldReference(utils.annotationValue(delim, "openingToken", Integer.class)))
                                                .on("bldr");
                                    }
                                });
                            }).inScope();

                        });
                    });
            if (!bundleItems.isEmpty()) {
                cb.importing(NB_BUNDLE.qname())
                        .annotatedWith(NB_BUNDLE.simpleName() + ".Messages", ab -> {
                            ClassBuilder.ArrayValueBuilder<?> all = ab.addArrayArgument("value");
                            for (Map.Entry<String, String> e : bundleItems.entrySet()) {
                                String line = e.getKey() + "=" + e.getValue();
                                all.literal(line);
                            }
                            all.closeArray();
                        });
            }
            classes.accept(cb);
        }
        return false;
    }

    /**
     * Generate the name of a character for use in bundle keys.
     *
     * @param c A character
     * @return A string name of the character understandable to a human
     * translator
     */
    static String c2s(char c) {
        // The point here isn't accurracy so much as consistency, so
        // for example "Insert )" and "Insert :" aren't converged to
        // the same bundle key "insert".
        switch (c) {
            case '(':
                return "lparen";
            case ')':
                return "rparen";
            case '#':
                return "pound";
            case '!':
                return "bang";
            case '@':
                return "at";
            case '$':
                return "dollars";
            case '%':
                return "pct";
            case '^':
                return "caren";
            case '&':
                return "amp";
            case '*':
                return "asterisk";
            case '-':
                return "dash";
            case '_':
                return "underscore";
            case '=':
                return "equals";
            case '{':
                return "obrace";
            case '}':
                return "cbrace";
            case '[':
                return "obrack";
            case ']':
                return "cbrack";
            case ';':
                return "semi";
            case ':':
                return "colon";
            case '\'':
                return "squote";
            case '"':
                return "quote";
            case '<':
                return "lt";
            case '>':
                return "gt";
            case ',':
                return "comma";
            case '.':
                return "dot";
            case '?':
                return "question";
            case '/':
                return "slash";
            case '\\':
                return "backslash";
            case '|':
                return "pipe";
            case '~':
                return "tilde";
            case '`':
                return "backtick";
            default:
                return null;
        }
    }

    private static String namify(String s, AnnotationMirror mir) {
        StringBuilder sb = new StringBuilder(
                mir.getAnnotationType().asElement().getSimpleName().toString().toLowerCase()).append('_');

        boolean lastWasUnderscore = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || (!Character.isAlphabetic(c) && !Character.isDigit(c))) {
                String cname = c2s(c);
                if (cname != null) {
                    if (!lastWasUnderscore) {
                        sb.append('_');
                    }
                    sb.append(cname).append('_');
                    lastWasUnderscore = true;
                } else {
                    if (!lastWasUnderscore) {
                        sb.append('_');
                        lastWasUnderscore = true;
                    }
                }
                continue;
            }
            sb.append(Character.toLowerCase(c));
            lastWasUnderscore = false;
        }
        if (sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        System.out.println("NAMIFY '" + s + "' to '" + sb + "'");
        return sb.toString();
    }

    public static ClassBuilder.InvocationBuilder<?> applyNameDescriptionAndCategory(AnnotationMirror mir, AnnotationUtils utils, ClassBuilder.InvocationBuilder<?> ib, ClassBuilder.BlockBuilder<?> lbb, Map<String, String> bundleItems) {
        String name = utils.annotationValue(mir, "name", String.class);
        if (name != null) {
            String keyBase = namify(name, mir);
            String nameKey = keyBase + "_name";
            bundleItems.put(nameKey, name);
            String category = utils.annotationValue(mir, "category", String.class);
            String description = utils.annotationValue(mir, "description", String.class);
            if (category != null) {
                String catKey = keyBase + "_category";
                bundleItems.put(catKey, category);
                if (ib == null) {
                    ib = lbb.invoke("setCategory")
                            .withArgumentFromInvoking(catKey).on("Bundle");
                } else {
                    ib = ib.onInvocationOf("setCategory")
                            .withArgumentFromInvoking(catKey).on("Bundle");
                }
            }
            if (description != null) {
                String descKey = keyBase + "_description";
                bundleItems.put(descKey, description);
                if (ib == null) {
                    ib = lbb.invoke("setDescription")
                            .withArgumentFromInvoking(descKey).on("Bundle");
                } else {
                    ib = ib.onInvocationOf("setDescription")
                            .withArgumentFromInvoking(descKey).on("Bundle");
                }
            }
            if (ib == null) {
                ib = lbb.invoke("setName")
                        .withArgumentFromInvoking(nameKey).on("Bundle");
            } else {
                ib = ib.onInvocationOf("setName")
                        .withArgumentFromInvoking(nameKey).on("Bundle");
            }
        }
        return ib;
    }

    private static ClassBuilder.InvocationBuilder<?> handleTokenPattern(AnnotationUtils utils, String critName, Element originating, LexerInfo lexer, AnnotationMirror bp, String precedingPattern, TypeElement type, AnnotationMirror mirror, ClassBuilder.InvocationBuilder<?> ib) {
        ParsedTokenPattern ptb = parse(precedingPattern);
        if (ptb.validate(lexer, err -> {
            utils.fail(err, originating, bp);
        })) {
            return ptb.apply(critName, lexer, ib);
        } else {
            return ib;
        }
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var,
            AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        return handleAnnotation(AnnotationUtils.enclosingType(var), mirror, roundEnv, var);
    }

    @Override
    protected boolean processMethodAnnotation(ExecutableElement method,
            AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        return handleAnnotation(AnnotationUtils.enclosingType(method), mirror, roundEnv, method);
    }

    @Override
    protected boolean processConstructorAnnotation(ExecutableElement constructor,
            AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        return handleAnnotation(AnnotationUtils.enclosingType(constructor), mirror, roundEnv, constructor);
    }

    Predicate<? super AnnotationMirror> test;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        test = utils.testMirror()
                .testMember("mimeType")
                .validateStringValueAsMimeType()
                .build()
                .testMember("lexer")
                .mustBeSubtypeOf(KnownTypes.ANTLR_V4_LEXER.qnameNotouch())
                .build()
                //                .testMember("boilerplates")
                //                .build()
                .addPredicate(mirror -> {
                    boolean result = true;
                    String mime = utils.annotationValue(mirror, "mimeType", String.class, "");
                    String dn = utils.annotationValue(mirror, "languageDisplayName", String.class);
                    List<AnnotationMirror> boilerplates = utils.annotationValues(mirror, "insertBoilerplate", AnnotationMirror.class);
                    List<AnnotationMirror> elisions = utils.annotationValues(mirror, "elideTypedChars", AnnotationMirror.class);
                    List<AnnotationMirror> delimiterPairs = utils.annotationValues(mirror, "deleteMatchingDelimiter", AnnotationMirror.class);
                    TypeMirror lexerTypeMirror = utils.typeForSingleClassAnnotationMember(mirror, "lexer");
                    TypeElement lexerType = (TypeElement) utils.processingEnv().getTypeUtils().asElement(lexerTypeMirror);
                    LexerProxy lexer = LexerProxy.create(lexerType, lexerType, utils);
                    boolean hasNames = false;
                    for (AnnotationMirror am : CollectionUtils.concatenate(boilerplates, elisions, delimiterPairs)) {
                        if (utils.annotationValue(am, "name", String.class) != null) {
                            hasNames = true;
                            break;
                        }
                    }
                    if (dn == null && hasNames) {
                        utils.warn("languageDisplayName not set - will use '" + mime + "' for the Editor options tab", null, mirror);
                    }
                    for (AnnotationMirror bp : boilerplates) {
                        List<Integer> whenCurrentTokenNot = utils.annotationValues(bp, "whenCurrentTokenNot", Integer.class);
                        for (Integer i : whenCurrentTokenNot) {
                            String tn = lexer.tokenName(i);
                            if (tn == null) {
                                utils.fail("No known token type " + i + " in whenCurrentToken for " + mime + ": " + bp);
                                result = false;
                            }
                        }
                        List<Integer> whenPrecedingToken = utils.annotationValues(bp, "whenPrecedingToken", Integer.class);
                        for (Integer i : whenPrecedingToken) {
                            String tn = lexer.tokenName(i);
                            if (tn == null) {
                                utils.fail("No known token type " + i + " in whenCurrentToken for " + mime + ": " + bp);
                                result = false;
                            }
                        }
                        String precedingPattern = utils.annotationValue(bp, "whenPrecededByPattern", String.class);
                        if (precedingPattern != null) {
                            try {
                                ParsedTokenPattern pt = parse(precedingPattern);
                                result &= pt.validate(lexer, utils::fail);
                            } catch (IllegalArgumentException ex) {
                                result = false;
                                utils.fail(ex.getMessage());
                            }
                        }
                        String followingPattern = utils.annotationValue(bp, "whenFollowedByPattern", String.class);
                        if (followingPattern != null) {
                            try {
                                ParsedTokenPattern pt = parse(followingPattern);
                                result &= pt.validate(lexer, utils::fail);
                            } catch (IllegalArgumentException ex) {
                                result = true;
                                utils.fail(ex.getMessage());
                            }
                        }
                    }
                    return result;
                })
                .build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
//        return test.test(mirror);
        return true;
    }

    /*
    Use a microformat something like this:
     > or < forward or backwards
    | allow end of doc
    LIST OF TOKENS IN PATTERN
    ! LIST OF STOP TOKENS
    ? LIST OF IGNORE TOKENS

    >| RPAREN {TOK_A, TOK_B} * LPAREN SEMI ! STOP_TOK_1 STOP_TOK_2 ? WHITESPACE BLOCK_COMMENT

     */
    public static final ParsedTokenPattern parse(String val) {
        val = val.trim();
        if (val.length() < 4) {
            throw new IllegalArgumentException("Pattern string too short");
        }
        if (val.charAt(0) == '>' || val.charAt(0) == '<') {
            boolean forward = val.charAt(0) == '>';
            boolean endOk = val.charAt(1) == '|';
            val = val.substring(endOk ? 2 : 1).trim();
            int bangIx = val.indexOf('!');
            if (bangIx < 0) {
                throw new IllegalArgumentException("! not found - "
                        + "stop tokens not specified");
            }
            String patternTokenList = val.substring(0, bangIx).trim();
            System.out.println("PATTERN '" + patternTokenList + "'");
            List<Object> patternTokens = scanGroup(patternTokenList);
            if (patternTokens.isEmpty()) {
                throw new IllegalArgumentException("Pattern token sequence '"
                        + patternTokenList + " is empty in '" + val + "'");
            }

            String remainder = val.substring(bangIx + 1, val.length());
            String stopTokenList;

            int qix = remainder.indexOf('?');
            if (qix < 0) {
                stopTokenList = remainder;
                remainder = "";
            } else {
                stopTokenList = remainder.substring(0, qix);
                remainder = remainder.substring(qix + 1, remainder.length());
            }
            System.out.println("STOPS: " + stopTokenList);
            Set<String> stopTokens = new HashSet<>(5);
            for (String stop : stopTokenList.split("\\s")) {
                if (!stop.isEmpty()) {
                    stopTokens.add(stop);
                }
            }
            System.out.println("IGNORE: " + remainder);
            Set<String> ignoreTokens = new HashSet<>(5);
            if (!remainder.isEmpty()) {
                for (String ign : remainder.split("\\s")) {
                    if (!ign.isEmpty()) {
                        ignoreTokens.add(ign);
                    }
                }
            }
            return new ParsedTokenPattern(forward, patternTokens, stopTokens,
                    endOk, ignoreTokens);
        } else {
            throw new IllegalArgumentException("Pattern must start with > or "
                    + "< to indicate forward or backward search");
        }
    }

    private static class ParsedTokenPattern {

        private final boolean forward;
        private final List<Object> patternTokens;
        private final Set<String> stopTokens;
        private final Set<String> ignoreTokens;
        private final boolean endOk;

        ParsedTokenPattern(boolean forward, List<Object> patternTokens, Set<String> stopTokens, boolean endOk, Set<String> ignoreTokens) {
            this.forward = forward;
            this.patternTokens = patternTokens;
            this.stopTokens = stopTokens;
            this.ignoreTokens = ignoreTokens;
            this.endOk = endOk;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(forward ? '>' : '<');
            if (endOk) {
                sb.append('|');
            }
            sb.append(patternTokens);
            sb.append(" !");
            sb.append(stopTokens);
            sb.append(" ?");
            sb.append(ignoreTokens);
            return sb.toString();
        }

        public boolean validate(LexerInfo lexer, Consumer<String> onFailure) {
            if (!(lexer instanceof LexerProxy)) {
                return true;
            }
            return validate(lexer, patternTokens, onFailure)
                    || validate(lexer, stopTokens, onFailure)
                    || validate(lexer, ignoreTokens, onFailure);
        }

        private boolean validate(LexerInfo lexer, Collection<? extends Object> all, Consumer<String> onFailure) {
            boolean result = true;
            for (Object o : all) {
                if (o instanceof String) {
                    String s = (String) o;
                    Integer ti = lexer.tokenIndex(s);
                    if (ti == null) {
                        onFailure.accept("No field named '" + s + "' on " + lexer.lexerClassSimple());
                        result = false;
                    }
                } else if (o instanceof List<?>) {
                    List<Object> l = (List<Object>) o;
                    result &= validate(lexer, l, onFailure);
                } else {
                    onFailure.accept("Unexpected object in parse: " + o
                            + " of type " + (o == null ? "null" : o.getClass()));
                    result = false;
                }
            }
            return result;
        }

        public ClassBuilder.InvocationBuilder<?> apply(String critName, LexerInfo lexer, ClassBuilder.InvocationBuilder<?> ib) {
            ClassBuilder.InvocationBuilder<?> iv = ib
                    .onInvocationOf("stoppingOn");
            if (stopTokens.size() == 1) {
                iv = iv.withArgumentFromInvoking("matching")
                        .withArgument(lexer.tokenFieldReference(stopTokens.iterator().next())).on(critName);
            } else if (!stopTokens.isEmpty()) {
                ClassBuilder.InvocationBuilder<?> xiv = iv.withArgumentFromInvoking("anyOf");
                for (String name : stopTokens) {
                    xiv.withArgument(lexer.tokenFieldReference(name));
                }
                xiv.on(critName);
            }
            if (!ignoreTokens.isEmpty()) {
                iv = iv.onInvocationOf("ignoring");
                if (ignoreTokens.size() == 1) {
                    iv = iv.withArgumentFromInvoking("matching").withArgument(lexer.tokenFieldReference(ignoreTokens.iterator().next())).on(critName);
                } else {
                    ClassBuilder.InvocationBuilder<?> xiv = iv.withArgumentFromInvoking("anyOf");
                    for (String name : ignoreTokens) {
                        xiv.withArgument(lexer.tokenFieldReference(name));
                    }
                    xiv.on(critName);
                }
            }

            if (endOk) {
                iv = iv.onInvocationOf("orDocumentStartOrEnd");
            }

            iv = iv.onInvocationOf(forward ? "whenFollowedByPattern" : "whenPrecededByPattern");
            for (Object o : CollectionUtils.reversed(patternTokens)) {
                if (o instanceof String) {
                    iv = iv.withArgumentFromInvoking("matching").withArgument(lexer.tokenFieldReference((String) o)).on(critName);
                } else {
                    List<?> l = (List<?>) o;
                    List<String> names = flatten(l);
                    iv = iv.withArgumentFromInvoking("anyOf", aib -> {
                        for (String n : names) {
                            aib.withArgument(lexer.tokenFieldReference(n));
                        }
                        aib.on(critName);
                    });
                }
            }
            return iv;
        }
    }

    private static List<String> flatten(List<?> o) {
        List<String> result = new ArrayList<>();
        for (Object oo : o) {
            if (oo instanceof String) {
                result.add((String) oo);
            } else {
                List<?> l = (List<?>) oo;
                result.addAll(flatten(l));
            }
        }
        return result;
    }

    private static List<Object> scanGroup(String s) {
        return new BraceGroupScanner(s).listify();
    }
}

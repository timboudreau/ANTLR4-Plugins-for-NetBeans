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
import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.annotation.AnnotationUtils.stripMimeType;
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.annotation.processor.LayerTask;
import com.mastfrog.annotation.validation.AnnotationMirrorTestBuilder;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.supplierMap;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import static org.nemesis.registration.ImportsAndResolvableProcessor.IMPORTS_ANNOTATION;
import static org.nemesis.registration.InplaceRenameProcessor.INPLACE_RENAME_ANNO_TYPE;
import static org.nemesis.registration.LocalizeAnnotationProcessor.inIDE;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import org.nemesis.registration.typenames.KnownTypes;
import static org.nemesis.registration.typenames.KnownTypes.ANTLR_REFACTORING_PLUGIN_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.CHAR_FILTER;
import static org.nemesis.registration.typenames.KnownTypes.CHAR_PREDICATE;
import static org.nemesis.registration.typenames.KnownTypes.CHAR_PREDICATES;
import static org.nemesis.registration.typenames.KnownTypes.EDITOR_ACTION_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION_KEY;
import static org.nemesis.registration.typenames.KnownTypes.HIGHLIGHTS_LAYER_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.INSTANT_RENAME_ACTION;
import static org.nemesis.registration.typenames.KnownTypes.MESSAGES;
import static org.nemesis.registration.typenames.KnownTypes.MIME_REGISTRATION;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_REGION_KEY;
import static org.nemesis.registration.typenames.KnownTypes.NAME_REFERENCE_SET_KEY;
import static org.nemesis.registration.typenames.KnownTypes.REFACTORABILITY;
import static org.nemesis.registration.typenames.KnownTypes.REFACTORINGS_BUILDER;
import static org.nemesis.registration.typenames.KnownTypes.REFACTORING_PLUGIN_FACTORY;
import static org.nemesis.registration.typenames.KnownTypes.RENAME_PARTICIPANT;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDERS;
import static org.nemesis.registration.typenames.KnownTypes.SINGLETON_KEY;
import org.nemesis.registration.typenames.TypeName;
import org.openide.filesystems.annotations.LayerBuilder;

/**
 * Generates inplace rename refactoring support for extraction keys annotated
 * with &#064;InplaceRename.
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({INPLACE_RENAME_ANNO_TYPE, IMPORTS_ANNOTATION})
public class InplaceRenameProcessor extends LayerGeneratingDelegate {

    private final Map<InplaceKey, List<InplaceInfo>> infos = supplierMap(ArrayList::new);
    public static final String INPLACE_RENAME_ANNO_TYPE
            = "org.nemesis.antlr.instantrename.annotations.InplaceRename";

    private BiPredicate<? super AnnotationMirror, ? super Element> test;
    private Set<String> packagesWithBundleKey = new HashSet<>();

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        // Test that code generation will not die horribly before generating anything
        test = utils.multiAnnotations()
                .whereAnnotationType(INPLACE_RENAME_ANNO_TYPE, b -> {
                    // Make sure the mime type is good
                    b.testMember("mimeType").addPredicate("Mime type", mir -> {
                        Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                        String value = utils().annotationValue(mir, "mimeType", String.class);
                        return mimeTest.test(value);
                    }).build();
                    b.onlyOneMemberMayBeSet("useRefactoringApi", "renameParticipant");
                    // Test the filters don't contain duplicate elements or types that
                    // are not what is required
                    b.testMemberAsAnnotation("filter", bldr -> {
                        Consumer<AnnotationMirrorTestBuilder<?, ? extends AnnotationMirrorTestBuilder<?, ?>>> c
                                = cscb -> {
                                    // Broken sources can produce weird things - don't try to survive them.
                                    cscb.testMember("including").valueMustBeOfSimpleType(Character.class)
                                            .addPredicate("no-duplicate-chars", (am, errs) -> {
                                                // Check for duplicates - can screw up binary searches
                                                List<Character> all = utils().annotationValues(am, "including", Character.class);
                                                if (all.size() != new HashSet<>(all).size()) {
                                                    errs.accept("Character list contains duplicates: "
                                                            + Strings.join(',', all));
                                                    return false;
                                                }
                                                return true;
                                            }).build().build();
                                    cscb.testMember("instantiate").mustBeSubtypeOfIfPresent(CHAR_PREDICATE.qnameNotouch())
                                            .build();
                                    cscb.testMember("include").addPredicate("duplicate-builtins", (am, errs) -> {
                                        List<Object> all = utils().annotationValues(am, "include", Object.class);
                                        List<String> strings = CollectionUtils.transform(all, Object::toString);
                                        if (new HashSet<>(strings).size() != strings.size()) {
                                            errs.accept("include contains duplicate CharPredicates: "
                                                    + Strings.join(',', all));
                                            return false;
                                        }
                                        return true;
                                    }).build();
                                };
                        // Now apply that to both predicate members
                        bldr.testMemberAsAnnotation("initialCharacter", c)
                                .testMemberAsAnnotation("subsequentCharacters", c)
                                .atLeastOneMemberMayBeSet("initialCharacter", "subsequentCharacters").build();
                    });

                    b.testMember("renameParticipant")
                            .addPredicate("generic-types-match", (am, errs) -> {
                                return true;
                            })
                            .asTypeSpecifier(tetb -> {
                                tetb.mustHavePublicNoArgConstructor()
                                        .mustBeFullyReifiedType()
                                        .nestingKindMayNotBe(NestingKind.LOCAL)
                                        .nestingKindMayNotBe(NestingKind.ANONYMOUS)
                                        .doesNotHaveModifier(PRIVATE)
                                        .build();
                            }).build();
                    // No built-in test builder for this:  if the participant is
                    // set, find its type signature and look up the enum (or not)
                    // type of what it is parameterized on, and make sure that
                    // is compatible with the key
                    b.whereFieldIsAnnotated(eltest -> {
                        eltest.hasModifiers(Modifier.FINAL, Modifier.STATIC)
                                .isSubTypeOf(NAME_REFERENCE_SET_KEY.qnameNotouch(), NAMED_REGION_KEY.qnameNotouch(),
                                        SINGLETON_KEY.qnameNotouch());
                        eltest.addPredicate("check-generic-signature", (var, errs) -> {
                            AnnotationMirror mir = utils.findAnnotationMirror(var, INPLACE_RENAME_ANNO_TYPE);
                            if (mir == null) {
                                // Should never happen, but better than an NPE
                                errs.accept("Could not re-locate annotation mirror for " + var);
                                return false;
                            }
                            TypeMirror participantTypeFromEnum = utils()
                                    .typeForSingleClassAnnotationMember(mir, "renameParticipant");
                            if (participantTypeFromEnum == null) {
                                // No participant type specified. We're done here.
                                return true;
                            }
                            TypeMirror annotatedVariableType = var.asType();
                            annotatedVariableType = utils().processingEnv().getTypeUtils().capture(annotatedVariableType);
                            TypeMirror extractionKeySupertypeSignature = findSupertype(annotatedVariableType, EXTRACTION_KEY.qnameNotouch());

                            if (!(extractionKeySupertypeSignature instanceof DeclaredType)) {
                                // what's going on?
                                errs.accept("Not an instance of DeclaredType: " + extractionKeySupertypeSignature);
                                return false;
                            }

                            TypeMirror participantSupertypeSignature = findSupertype(participantTypeFromEnum, RENAME_PARTICIPANT.qnameNotouch());
                            if (!(participantSupertypeSignature instanceof DeclaredType)) {
                                // what's going on?
                                errs.accept("Not an instance of DeclaredType: " + participantSupertypeSignature);
                                return false;
                            }

                            DeclaredType participantAsRenameParticipantSupertype = (DeclaredType) participantSupertypeSignature;
                            DeclaredType extractionKeyAsExtractionKeySupertype = (DeclaredType) extractionKeySupertypeSignature;

                            List<? extends TypeMirror> participantTypeParameters = participantAsRenameParticipantSupertype.getTypeArguments();
                            List<? extends TypeMirror> extractionKeyTypeParameters = extractionKeyAsExtractionKeySupertype.getTypeArguments();

                            if (participantTypeParameters.size() > 0 && extractionKeyTypeParameters.size() > 0) {
                                // Make sure it's RenameParticipant<Foo,...> matched to KeyType<Foo>
                                TypeMirror participantExpectsKeyParamaterizedOn = participantTypeParameters.get(0);
                                TypeMirror extractionKeyIsParameterizedOn = extractionKeyTypeParameters.get(0);
                                if (!processingEnv.getTypeUtils().isSameType(participantExpectsKeyParamaterizedOn, extractionKeyIsParameterizedOn)) {
                                    errs.accept("Key type conflict: Key is parameterized on " + extractionKeyIsParameterizedOn + " but "
                                            + "trying to use a RenameParticipant parameterized on " + participantExpectsKeyParamaterizedOn);
                                    return false;
                                }
                                if (participantTypeParameters.size() > 1) { // should always be
                                    // Make sure the extraction key type in the rename participant's signature
                                    // is, for example, SingletonKey if we have annotated a SingletonKey, etc.
                                    TypeMirror keyType = participantTypeParameters.get(1);
                                    if (!processingEnv.getTypeUtils().isAssignable(keyType, var.asType())) {
                                        errs.accept("Extraction key type conflict - annotation is on a "
                                                + annotatedVariableType + " but RenameParticipant expectes a key of " + keyType);
                                        return false;
                                    }
                                } else {
                                    errs.accept("Could not find key type param on " + participantTypeFromEnum + " in " + participantTypeParameters);
                                    return false;
                                }
                            } else {
                                if (participantTypeParameters.isEmpty()) {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Could not find a generic signature on "
                                            + participantTypeFromEnum + " to test for compatibility with " + extractionKeyAsExtractionKeySupertype, var);
                                } else if (extractionKeyTypeParameters.isEmpty()) {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Could not find a generic signature on "
                                            + extractionKeyAsExtractionKeySupertype + " to test for compatibility with " + participantTypeFromEnum, var);
                                } else {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Could not find generic signatures on either of "
                                            + extractionKeyAsExtractionKeySupertype + " or " + participantTypeFromEnum + " to test for compatibility."
                                            + "Generated implementation may throw ClassCastExceptions at runtime.", var);

                                }
                            }
                            return true;
                        });
                    });
                }).build();
    }

    private TypeMirror findSupertype(TypeMirror tm, String name) {
        TypeMirror base = utils().type(name);
        if (base == null) {
            return null;
        }
        base = processingEnv.getTypeUtils().erasure(base);
        return findSupertype(tm, base, new HashSet<>());
    }

    private boolean sameTypes(TypeMirror t, TypeMirror base) {
        if (t.toString().startsWith(base.toString())) {
            String tn = processingEnv.getTypeUtils().erasure(t).toString();
            if (tn.indexOf('<') > 0) {
                // erasure(t) seems to be broken on JDK 10
                tn = tn.substring(0, tn.indexOf('<'));
            }
            if (tn.equals(base.toString())) {
                return true;
            }
        }
        return false;
    }

    private TypeMirror findSupertype(TypeMirror tm, TypeMirror base, Set<TypeMirror> seen) {
        if (sameTypes(tm, base)) {
            return tm;
        }
        for (TypeMirror t : processingEnv.getTypeUtils().directSupertypes(tm)) {
            if (seen.contains(t)) {
                continue;
            } else if (sameTypes(t, base)) {
                return t;
            }
            TypeMirror result = findSupertype(t, base, seen);
            if (result != null) {
                return result;
            }
        }
        seen.add(tm);
        return null;
    }

    private boolean isReferenceKey(VariableElement el) {
        return findSupertype(el.asType(), NAME_REFERENCE_SET_KEY.qnameNotouch()) != null;
    }

    private boolean isSingletonKey(VariableElement el) {
        boolean result = findSupertype(el.asType(), SINGLETON_KEY.qnameNotouch()) != null;
        return result;
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        return test.test(mirror, el);
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws Exception {
        if (env.errorRaised()) {
            return false;
        }
        processItems();
        if (env.processingOver() && !env.errorRaised() && !inIDE) {
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, KnownTypes.touchedMessage());
            System.out.println(KnownTypes.touchedMessage(this));
        }
        return true;
    }

    private void processItems() throws IOException {
        for (Iterator<Map.Entry<InplaceKey, List<InplaceInfo>>> it = infos.entrySet().iterator(); it.hasNext();) {
            Map.Entry<InplaceKey, List<InplaceInfo>> e = it.next();
            InvocationBuilder<BlockBuilder<ClassBuilder<String>>> creatingInstantRenameAction = null;
            InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>> refactoringPluginFactoryConstructor = null;
            for (Iterator<InplaceInfo> infosIterator = e.getValue().iterator(); infosIterator.hasNext();) {
                InplaceInfo info = infosIterator.next();
                if (info.generateInstantRenameSupport) {
                    creatingInstantRenameAction = processOneItem(e.getKey(), info, e.getKey().classBuilder(), !infosIterator.hasNext(), creatingInstantRenameAction);
                }
                if (info.generateRefactoringSupport) {
                    refactoringPluginFactoryConstructor = processOneRefactoring(e.getKey(), info, e.getKey().refactoringsClassBuilder(), !infosIterator.hasNext(), refactoringPluginFactoryConstructor);
                }
            }
            ensureLayerGenerationTaskAdded(e.getKey());
        }
        infos.clear();
    }

    private <T> void processCharPredicateSpec(AnnotationMirror cp,
            InvocationBuilder<InvocationBuilder<T>> ib,
            ClassBuilder<?> cb) {
        List<Character> chars = utils().annotationValues(cp, "including", Character.class);
        List<String> instantiate = utils().typeList(cp, "instantiate", CHAR_PREDICATE.qnameNotouch());
        Set<String> builtIn = utils().enumConstantValues(cp, "include", CHAR_PREDICATES.qnameNotouch());
        builtIn.remove("org.nemesis.charfilter.CharPredicates"); // bug in AU

        boolean or = utils().annotationValue(cp, "logicallyOr", Boolean.class, true);
        int setCount = (chars.isEmpty() ? 0 : 1) + (instantiate.isEmpty() ? 0 : 1)
                + (builtIn.isEmpty() ? 0 : 1);

        switch (setCount) {
            case 0:
                // Just accept everything
                CHAR_PREDICATE.addImport(cb);
                ib.withArgumentFromField("EVERYTHING").of(CHAR_PREDICATE.simpleName());
                break;
            case 1:
                // Don't use a builder, only one predicate specified
                if (!chars.isEmpty()) {
                    CHAR_PREDICATE.addImport(cb);
                    ib.withArgumentFromInvoking("anyOf").withNewArrayArgument("char", ab -> {
                        for (Character c : chars) {
                            ab.literal(c.charValue());
                        }
                    }).on(CHAR_PREDICATE.simpleName());
                } else if (!instantiate.isEmpty()) {
                    ib.withArgument("new " + instantiate + "()");
                } else if (!builtIn.isEmpty()) {
                    applyBuiltInPredicates(cb, builtIn, ib, or);
                }
                break;
            default:
                // Use a builder for multiple combined character filters
                CHAR_FILTER.addImport(cb);
                if (utils().annotationValue(cp, "negated", Boolean.class, false)) {
                    InvocationBuilder<InvocationBuilder<InvocationBuilder<T>>> cbib = ib.withArgumentFromInvoking("negate").onInvocationOf("build");
                    cbib = applyMultiplePredicates(chars, cbib, or, builtIn, cb, instantiate);
                    cbib.onInvocationOf("builder").on(CHAR_PREDICATE.simpleName());
                } else {
                    InvocationBuilder<InvocationBuilder<InvocationBuilder<T>>> cbib
                            = ib.withArgumentFromInvoking("build");
                    cbib = applyMultiplePredicates(chars, cbib, or, builtIn, cb, instantiate);
                    cbib.onInvocationOf("builder").on(CHAR_PREDICATE.simpleName());
                }
                break;
        }
    }

    private <T> InvocationBuilder<InvocationBuilder<T>> applyMultiplePredicates(List<Character> chars, InvocationBuilder<InvocationBuilder<T>> cbib, boolean or, Set<String> builtIn, ClassBuilder<?> cb, List<String> instantiate) {
        if (!chars.isEmpty()) {
            cbib = cbib.onInvocationOf("include").withNewArrayArgument("char", avb -> {
                for (Character c : chars) {
                    avb.literal(c.charValue());
                }
            });
        }
        String meth = or ? "or" : "and";
        if (!builtIn.isEmpty()) {
            InvocationBuilder<InvocationBuilder<T>> addBuiltins = cbib.onInvocationOf(meth);
            cbib = applyBuiltInPredicates(cb, builtIn, addBuiltins, or);
        }
        if (!instantiate.isEmpty()) {
            for (String instantiated : instantiate) {
                instantiated = instantiated.replace('$', '.');
                TypeName tn = TypeName.fromQualifiedName(instantiated);
                tn.addImport(cb);

                cbib = cbib.onInvocationOf(meth).withArgument("new " + tn.simpleName() + "()");
            }
        }
        return cbib;
    }

    private <T> InvocationBuilder<InvocationBuilder<T>> applyBuiltInPredicates(ClassBuilder<?> cb, Set<String> builtIn, InvocationBuilder<InvocationBuilder<T>> ib, boolean or) {
        CHAR_PREDICATES.addImport(cb);
        CHAR_PREDICATE.addImport(cb);
        InvocationBuilder<InvocationBuilder<T>> res;
        if (builtIn.size() == 1) {
            res = ib.withArgumentFromField(builtIn.iterator().next()).of(CHAR_PREDICATES.simpleName());
        } else {
            List<String> all = CollectionUtils.reversed(new ArrayList<>(builtIn));
            InvocationBuilder<InvocationBuilder<InvocationBuilder<T>>> curr
                    = ib.withArgumentFromInvoking("combine")
                            .withArgument(or);

            for (String item : all) {
                cb.staticImport(CHAR_PREDICATES.qname() + "." + item);
                curr = curr.withArgument(item);
            }
            res = curr.on(CHAR_PREDICATE.simpleName());
        }
        return res;
    }

    private <T> void charFilterSpec(AnnotationMirror mirror, InvocationBuilder<InvocationBuilder<T>> ib, ClassBuilder<?> cb) {
        AnnotationMirror initial = utils().annotationValue(mirror, "initialCharacter", AnnotationMirror.class);
        if (initial == null) {
            CHAR_PREDICATE.addImport(cb);
            ib.withArgument(CHAR_PREDICATE.simpleName() + ".EVERYTHING");
        } else {
            processCharPredicateSpec(initial, ib, cb);
        }

        AnnotationMirror subsequent = utils().annotationValue(mirror, "subsequentCharacters", AnnotationMirror.class);
        if (subsequent == null) {
            CHAR_PREDICATE.addImport(cb);
            ib.withArgument(CHAR_PREDICATE.simpleName() + ".EVERYTHING");
        } else {
            processCharPredicateSpec(subsequent, ib, cb);
        }
        ib.on(CHAR_FILTER.simpleName());

    }

    /**
     * Handle one inplace rename operation, adding the necessary calls to the
     * builder-in-progress, adding the statements for creating it if not
     * present.
     *
     * @param key The key
     * @param info The annotated item
     * @param cb The class builder for adding imports to
     * @param last Whether or not this is the last one and the builder should be
     * closed
     * @param subInvocation The call-in-progress which we will be chaining calls
     * to
     * @return The call-in-progress, to be added to by the next item if need be.
     * @throws IOException If something goes wrong
     */
    private InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> processOneItem(
            InplaceKey key, InplaceInfo info, ClassBuilder<String> cb, boolean last, InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> subInvocation) throws IOException {
        AnnotationMirror mirror = info.mirror;

        // Get the owning type of the annotated variable and generate a static import
        // for the variable
        TypeElement encType = AnnotationUtils.enclosingType(info.var);
        String fqn = encType.getQualifiedName() + "." + info.var.getSimpleName();
        cb.staticImport(fqn);

        if (subInvocation == null) {
            // Open the builder - first invocation
            subInvocation = key.invocation().onInvocationOf("add")
                    .withArgument(info.var.getSimpleName().toString());
        } else {
            // Continue the builder - subsequent invocation
            subInvocation = subInvocation.onInvocationOf("add")
                    .withArgument(info.var.getSimpleName().toString());
        }
        // The test will have already flagged it if both renameParticipant and useRefactoringApi
        // are present, and we will never get here
        boolean isUseRefactoringApi = utils().annotationValue(mirror, "useRefactoringApi", Boolean.class, false);
        if (!isUseRefactoringApi) {
            // If not the refactoring API, see if a type is specified - if it is, we have
            // already validated that it is reachable and type-compatible
            List<String> rpTypes = utils().typeList(info.mirror, "renameParticipant", RENAME_PARTICIPANT.qnameNotouch());
            if (!rpTypes.isEmpty()) {
                RENAME_PARTICIPANT.addImport(cb);
                TypeName participantType = TypeName.fromQualifiedName(rpTypes.get(0).replace('$', '.'));
                participantType.addImport(cb);
                subInvocation = subInvocation.withArgument("new " + participantType.simpleName() + "()");
            }
        } else {
            // Add a call to RenameParticipant.useRefactoringParticipant, which always
            // forwards inplace rename invocations to the refactoring api
            RENAME_PARTICIPANT.addImport(cb);
            subInvocation = subInvocation.withArgumentFromInvoking("useRefactoringParticipant")
                    .on(RENAME_PARTICIPANT.simpleName());
        }

        // See if we have a char filter configured, and if so, add the calls to add that
        // before completing this entry in the builder
        AnnotationMirror charFilterSpec = utils().annotationValue(mirror, "filter", AnnotationMirror.class);
        if (charFilterSpec != null) {
            CHAR_FILTER.addImport(cb);
            InvocationBuilder<InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>>> cfSub
                    = subInvocation.withArgumentFromInvoking("of");
            charFilterSpec(charFilterSpec, cfSub, cb);
        }

        if (last) {
            // Add the outermost (FIRST!) call and close the block
            subInvocation.onInvocationOf("builder").on(INSTANT_RENAME_ACTION.simpleName()).endBlock();
            writeOne(cb);
        }
        return subInvocation;
    }

    private InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>> processOneRefactoring(
            InplaceKey key, InplaceInfo info, ClassBuilder<String> cb, boolean last, InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>> subInvocation)
            throws IOException {

        // Create our final call on the builder - everything we do here is
        // chained BEHIND that (as in, we add the calls in reverse order of how
        // they will show up in source code), so the final on(what) or inScope()
        // indicates to the InvocationBuilder that it is complete and should be
        // added to the block it is in
        if (subInvocation == null) {
            key.refactoringsInvocationBuilder().lineComment(info + " for " + key);
            InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>> b
                    = key.refactoringsInvocationBuilder().invoke("finished");
            subInvocation = b;
        }

        // Get the FQN of the field that was annotated, and generate a static import
        String targetFieldName = info.var.getSimpleName().toString();
        String targetFieldFqn = AnnotationUtils.enclosingType(info.var).getQualifiedName() + "." + targetFieldName;
        cb.staticImport(targetFieldFqn);

        // Add the usages calls in reverse order
        subInvocation = subInvocation
                .onInvocationOf("finding").withArgument(targetFieldName)
                .onInvocationOf("usages");

        AnnotationMirror mirror = info.mirror;
        AnnotationMirror charFilterSpec = utils().annotationValue(mirror, "filter", AnnotationMirror.class);
        // If we have a character filter, add that for the rename we're about to add
        // (again, this must be done in reverse order)
        if (charFilterSpec != null) {
            subInvocation = subInvocation.onInvocationOf("withCharFilter");
            CHAR_FILTER.addImport(cb);
            InvocationBuilder<InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>>> cfSub
                    = subInvocation.withArgumentFromInvoking("of");
            charFilterSpec(charFilterSpec, cfSub, cb);
        } else {
            subInvocation = subInvocation.onInvocationOf("build");
        }

        // Now add rename info
        subInvocation = subInvocation
                .onInvocationOf("renaming").withArgument(targetFieldName)
                .onInvocationOf("rename");
        if (isSingletonKey(info.var)) {
            VariableElement importsKey = importsAnnotated.get(key.mimeType);
            if (importsKey != null) {
                importsAnnotated.remove(key.mimeType);
                TypeElement importKeyOwner = AnnotationUtils.enclosingType(importsKey);
                String importKeyName = importsKey.getSimpleName().toString();
                String importKeyFqn = importKeyOwner.getQualifiedName() + "." + importKeyName;
                cb.staticImport(importKeyFqn);

                subInvocation = subInvocation.onInvocationOf("finding")
                        .withArgument(importKeyName)
                        .withArgument(targetFieldName)
                        .onInvocationOf("usages");

                if (charFilterSpec != null) {
                    subInvocation = subInvocation.onInvocationOf("withCharFilter");
                    CHAR_FILTER.addImport(cb);
                    InvocationBuilder<InvocationBuilder<BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>>>> cfSub
                            = subInvocation.withArgumentFromInvoking("of");
                    charFilterSpec(charFilterSpec, cfSub, cb);
                } else {
                    subInvocation = subInvocation.onInvocationOf("build");
                }

                subInvocation = subInvocation
                        .onInvocationOf("renaming")
                        .withArgument(importKeyName)
                        .withArgument(targetFieldName)
                        .onInvocationOf("rename");
            }
        }
        // Prepend the call that opens the builder in the first place
        if (last) {
            subInvocation.on("bldr")
                    // Close the lambda
                    .endBlock()
                    // Close the super invocation
                    .inScope()
                    // Close the constructor body
                    .endBlock();

            writeOne(cb);
        }
        return subInvocation;
    }

    private final Map<String, VariableElement> importsAnnotated = new HashMap<>();

    protected boolean processImportsAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
        if (mimeType != null) {
            importsAnnotated.put(mimeType, var);
        }
        return false;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (IMPORTS_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            return processImportsAnnotation(var, mirror, roundEnv);
        }
        String mimeType = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
        String pkg = utils().packageName(var);
        String className = capitalize(AnnotationUtils.stripMimeType(mimeType)) + "InplaceRenameAction";

        InplaceKey key = new InplaceKey(mimeType, pkg, className, packagesWithBundleKey);
        InplaceInfo info = new InplaceInfo(var, mirror);

        infos.get(key).add(info);
        return false;
    }

    private Set<InplaceKey> haveRegisteredLayerGenerationTasks = new HashSet<>();

    private void ensureLayerGenerationTaskAdded(InplaceKey key) {
        if (!haveRegisteredLayerGenerationTasks.contains(key)) {
            Set<VariableElement> allElements = new HashSet<>();
            for (InplaceInfo item : this.infos.get(key)) {
                if (item.generateRefactoringSupport) {
                    allElements.add(item.var);
                }
            }
            if (!allElements.isEmpty()) {
                try {
//                    addLayerTask(new GenerateLayerRefactoringActions(key), allElements.toArray(new Element[allElements.size()]));
                    new GenerateLayerRefactoringActions(key).run(layer(allElements.toArray(new Element[allElements.size()])));
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                    com.mastfrog.util.preconditions.Exceptions.chuck(ex);
                }
            }
        }
    }

    static enum Refactorings {
        RENAME("org.netbeans.modules.refactoring.api.ui.RenameAction.instance",
                "org.netbeans.modules.refactoring.api.ui.RefactoringActionsFactory", "renameAction"),
        WHERE_USED("org.netbeans.modules.refactoring.api.ui.WhereUsedAction.instance",
                "org.netbeans.modules.refactoring.api.ui.RefactoringActionsFactory", "whereUsedAction");
        private final String layerFileName;
        private final String factoryClass;
        private final String factoryMethod;

        private Refactorings(String layerFileName, String factoryClass, String factoryMethod) {
            this.layerFileName = layerFileName;
            this.factoryClass = factoryClass;
            this.factoryMethod = factoryMethod;
        }

        void addToLayer(LayerBuilder bldr, String folderPath, int index) {
            LayerBuilder.File file = bldr.file(folderPath + "/" + layerFileName);
            file.methodvalue("instanceCreate", factoryClass, factoryMethod);
            file.stringvalue("instanceOf", "javax.swing.Action");
            // Space the positions so user code can insert in between if needed
            file.intvalue("position", index * 100);
            file.write();
        }
    }

    class GenerateLayerRefactoringActions implements LayerTask {

        private final InplaceKey key;

        public GenerateLayerRefactoringActions(InplaceKey key) {
            this.key = key;
        }

        private Set<Refactorings> generatingRefactorings() {
            return EnumSet.allOf(Refactorings.class);
        }

        @Override
        public void run(LayerBuilder bldr) throws Exception {
            haveRegisteredLayerGenerationTasks.remove(key);
            Set<Refactorings> refactorings = generatingRefactorings();
            if (refactorings.isEmpty()) {
                return;
            }
            String basePath = "Editors/" + key.mimeType + "/";
            for (String layerFolder : new String[]{"RefactoringActions", "Popup"}) {
                int ix = 1;
                for (Refactorings ref : refactorings) {
                    ref.addToLayer(bldr, basePath + layerFolder, ix++);
                }
            }
        }
    }

    static class InplaceInfo {

        final VariableElement var;
        final AnnotationMirror mirror;
        final boolean generateRefactoringSupport;
        final boolean generateInstantRenameSupport;

        public InplaceInfo(VariableElement el, AnnotationMirror mirror, boolean generateInstantRenameSupport, boolean generateRefactoringSupport) {
            this.var = el;
            this.mirror = mirror;
            this.generateRefactoringSupport = generateRefactoringSupport;
            this.generateInstantRenameSupport = generateInstantRenameSupport;
        }

        public InplaceInfo(VariableElement el, AnnotationMirror mirror) {
            this(el, mirror, true, true);
        }

        @Override
        public String toString() {
            return var + " -> " + mirror + "(" + (generateInstantRenameSupport ? "instant-rename" : "no-instant-rename")
                    + " " + (generateInstantRenameSupport ? "instant-rename" : "no-instant-rename") + ")";
        }
    }

    static class InplaceKey {

        private final String mimeType;
        private final String pkg;
        private final String className;
        private ClassBuilder<String> classBuilder;
        private ClassBuilder<String> refactoringsClassBuilder;

        private InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> invocation;
        private final Set<String> packagesWithBundleKey;
        private BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>> refactoringsBuilder;

        public InplaceKey(String mimeType, String pkg, String className, Set<String> packagesWithBundleKey) {
            this.mimeType = mimeType;
            this.pkg = pkg;
            this.className = className;
            this.packagesWithBundleKey = packagesWithBundleKey;
        }

        BlockBuilder<InvocationBuilder<BlockBuilder<ClassBuilder<String>>>> refactoringsInvocationBuilder() {
            return refactoringsBuilder == null ? refactoringsBuilder
                    = refactoringsClassBuilder().constructor().setModifier(PUBLIC)
                            .body().invoke("super").withStringLiteral(mimeType)
                            .withLambdaArgument().withArgument(REFACTORINGS_BUILDER.simpleName(), "bldr").body()
                    : refactoringsBuilder;
        }

        ClassBuilder<String> refactoringsClassBuilder() {
            if (refactoringsClassBuilder == null) {
                refactoringsClassBuilder = ClassBuilder.forPackage(pkg)
                        .named(stripMimeType(mimeType).toUpperCase() + "RefactoringPluginFactory")
                        .extending(ANTLR_REFACTORING_PLUGIN_FACTORY.simpleName())
                        .withModifier(PUBLIC, FINAL)
                        .docComment("Generated from annotations")
                        .annotatedWith(SERVICE_PROVIDERS.simpleName(), ab -> {
                            ClassBuilder.ArrayValueBuilder<?> arr = ab.addArrayArgument("value");
                            for (KnownTypes type : new KnownTypes[]{REFACTORING_PLUGIN_FACTORY, REFACTORABILITY}) {
                                arr.annotation(SERVICE_PROVIDER.simpleName())
                                        .addClassArgument("service", type.simpleName())
                                        .addArgument("position", 107).closeAnnotation();
                            }
                            arr.closeArray();
                        });
                TypeName.addImports(refactoringsClassBuilder,
                        ANTLR_REFACTORING_PLUGIN_FACTORY, SERVICE_PROVIDERS,
                        SERVICE_PROVIDER, REFACTORING_PLUGIN_FACTORY,
                        REFACTORABILITY, REFACTORINGS_BUILDER);

            }
            return refactoringsClassBuilder;
        }

        InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> invocation() {
            if (invocation == null) {
                invocation = classBuilder()
                        .method("inplaceRename")
                        .withModifier(PUBLIC, STATIC)
                        .returning(INSTANT_RENAME_ACTION.simpleName())
                        .annotatedWith(EDITOR_ACTION_REGISTRATION.simpleName(), ab -> {
                            ab.addArgument("mimeType", mimeType)
                                    .addArgument("noIconInMenu", true)
                                    .addArgument("category", "Refactoring")
                                    .addArgument("name", "in-place-refactoring");
                        })
                        .body()
                        .returningInvocationOf("build");
            }
            return invocation;
        }

        public ClassBuilder<String> classBuilder() {
            if (classBuilder == null) {
                classBuilder = ClassBuilder.forPackage(pkg).named(className)
                        .withModifier(PUBLIC, FINAL);
                TypeName.addImports(classBuilder,
                        INSTANT_RENAME_ACTION,
                        HIGHLIGHTS_LAYER_FACTORY,
                        EDITOR_ACTION_REGISTRATION,
                        MIME_REGISTRATION,
                        MESSAGES)
                        .utilityClassConstructor()
                        .annotatedWith(MESSAGES.simpleName(), ab -> {
                            ab.addArgument("value", "in-place-refactoring=&Rename");
                            packagesWithBundleKey.add(pkg);
                        })
                        .method("highlights", mb -> {
                            mb.annotatedWith(MIME_REGISTRATION.simpleName(), ab -> {
                                ab.addArgument("mimeType", mimeType)
                                        .addClassArgument("service", HIGHLIGHTS_LAYER_FACTORY.simpleName()
                                        ).addArgument("position", 107);
                            });
                            mb.withModifier(PUBLIC, STATIC)
                                    .returning(HIGHLIGHTS_LAYER_FACTORY.simpleName())
                                    .body(bb -> {
                                        bb.returningInvocationOf("highlightsFactory").on(INSTANT_RENAME_ACTION.simpleName());
                                    });
                        });
                ;
            }
            return classBuilder;
        }

        @Override
        public String toString() {
            return mimeType + "(" + pkg + "." + className + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.mimeType);
            hash = 29 * hash + Objects.hashCode(this.pkg);
            hash = 29 * hash + Objects.hashCode(this.className);
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
            final InplaceKey other = (InplaceKey) obj;
            if (!Objects.equals(this.mimeType, other.mimeType)) {
                return false;
            }
            if (!Objects.equals(this.pkg, other.pkg)) {
                return false;
            }
            return Objects.equals(this.className, other.className);
        }
    }

    private SupportedExtractionKeyType keyType(VariableElement el) {
        TypeMirror t = el.asType();
        for (SupportedExtractionKeyType s : SupportedExtractionKeyType.values()) {
            TypeMirror result = this.findSupertype(t, s.typeName());
            if (result != null) {
                return s;
            }
        }
        return null;
    }

    static enum SupportedExtractionKeyType {
        NAME_REFERENCE_SETS(NAME_REFERENCE_SET_KEY.qnameNotouch()),
        NAMED_REGIONS(NAMED_REGION_KEY.qnameNotouch()),
        SINGLETON(SINGLETON_KEY.qnameNotouch());
        private final String typeName;

        private SupportedExtractionKeyType(String typeName) {
            this.typeName = typeName;
        }

        public TypeMirror type(AnnotationUtils utils) {
            return utils.type(typeName);
        }

        String typeName() {
            return typeName;
        }

        @Override
        public String toString() {
            return simpleName(typeName);
        }
    }
}

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
import com.mastfrog.annotation.processor.AbstractLayerGeneratingDelegatingProcessor;
import com.mastfrog.annotation.validation.AnnotationMirrorTestBuilder;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.supplierMap;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
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
import static org.nemesis.registration.InplaceRenameProcessor.INPLACE_RENAME_ANNO_TYPE;
import org.nemesis.registration.typenames.KnownTypes;
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
import static org.nemesis.registration.typenames.KnownTypes.RENAME_PARTICIPANT;
import static org.nemesis.registration.typenames.KnownTypes.SINGLETON_KEY;
import org.nemesis.registration.typenames.TypeName;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generates inplace rename refactoring support for extraction keys annotated
 * with &#064;InplaceRename.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes(INPLACE_RENAME_ANNO_TYPE)
public class InplaceRenameProcessor extends AbstractLayerGeneratingDelegatingProcessor {

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
                    b.testMember("mimeType").validateStringValueAsMimeType().build();
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

    private TypeMirror findSupertype(TypeMirror tm, TypeMirror base, Set<TypeMirror> seen) {
        for (TypeMirror t : processingEnv.getTypeUtils().directSupertypes(tm)) {
            if (seen.contains(t)) {
                continue;
            }
            if (t.toString().startsWith(base.toString())) {
                if (processingEnv.getTypeUtils().erasure(t).toString().equals(base.toString())) {
                    return t;
                }
            }
            TypeMirror result = findSupertype(t, base, seen);
            if (result != null) {
                return result;
            }
        }
        seen.add(tm);
        return null;
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
        processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, KnownTypes.touchedMessage());
        return true;
    }

    private void processItems() throws IOException {
        for (Iterator<Map.Entry<InplaceKey, List<InplaceInfo>>> it = infos.entrySet().iterator(); it.hasNext();) {
            Map.Entry<InplaceKey, List<InplaceInfo>> e = it.next();
            InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> ib = null;
            for (Iterator<InplaceInfo> infosIterator = e.getValue().iterator(); infosIterator.hasNext();) {
                ib = processOneItem(e.getKey(), infosIterator.next(), e.getKey().classBuilder(), !infosIterator.hasNext(), ib);
            }
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

    private void charFilterSpec(AnnotationMirror mirror, InvocationBuilder<InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>>> ib, ClassBuilder<?> cb) {
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

    private InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> processOneItem(
            InplaceKey key, InplaceInfo info, ClassBuilder<String> cb, boolean last, InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> subInvocation) throws IOException {
        AnnotationMirror mirror = info.mirror;

        TypeElement encType = AnnotationUtils.enclosingType(info.el);
        String fqn = encType.getQualifiedName() + "." + info.el.getSimpleName();
        cb.staticImport(fqn);

        if (subInvocation == null) {
            subInvocation = key.invocation().onInvocationOf("add")
                    .withArgument(info.el.getSimpleName().toString());
        } else {
            subInvocation = subInvocation.onInvocationOf("add")
                    .withArgument(info.el.getSimpleName().toString());
        }
        List<String> rpTypes = utils().typeList(info.mirror, "renameParticipant", RENAME_PARTICIPANT.qnameNotouch());
        if (!rpTypes.isEmpty()) {
            RENAME_PARTICIPANT.addImport(cb);
            TypeName participantType = TypeName.fromQualifiedName(rpTypes.get(0).replace('$', '.'));
            participantType.addImport(cb);
            subInvocation = subInvocation.withArgument("new " + participantType.simpleName() + "()");
        }

        AnnotationMirror charFilterSpec = utils().annotationValue(mirror, "filter", AnnotationMirror.class);
        if (charFilterSpec != null) {
            CHAR_FILTER.addImport(cb);
            InvocationBuilder<InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>>> cfSub
                    = subInvocation.withArgumentFromInvoking("of");
            charFilterSpec(charFilterSpec, cfSub, cb);
        }

        if (last) {
            subInvocation.onInvocationOf("builder").on(INSTANT_RENAME_ACTION.simpleName()).endBlock();
            writeOne(cb);
        }
        return subInvocation;
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String pkg = utils().packageName(var);
        String className = capitalize(AnnotationUtils.stripMimeType(mimeType)) + "InplaceRenameAction";

        InplaceKey key = new InplaceKey(mimeType, pkg, className, packagesWithBundleKey);
        InplaceInfo info = new InplaceInfo(var, mirror);

        infos.get(key).add(info);
        return true;
    }

    static class InplaceInfo {

        final VariableElement el;
        final AnnotationMirror mirror;

        public InplaceInfo(VariableElement el, AnnotationMirror mirror) {
            this.el = el;
            this.mirror = mirror;
        }

        public String toString() {
            return el + " -> " + mirror;
        }
    }

    static class InplaceKey {

        private final String mimeType;
        private final String pkg;
        private final String className;
        private ClassBuilder<String> classBuilder;
        private InvocationBuilder<ClassBuilder.BlockBuilder<ClassBuilder<String>>> invocation;
        private final Set<String> packagesWithBundleKey;

        public InplaceKey(String mimeType, String pkg, String className, Set<String> packagesWithBundleKey) {
            this.mimeType = mimeType;
            this.pkg = pkg;
            this.className = className;
            this.packagesWithBundleKey = packagesWithBundleKey;
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
                                        ).addArgument("position", 100);
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
}

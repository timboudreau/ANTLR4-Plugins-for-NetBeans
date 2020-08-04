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
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Level;
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import static org.nemesis.registration.ImportsAndResolvableProcessor.IMPORTS_ANNOTATION;
import static org.nemesis.registration.ImportsAndResolvableProcessor.RESOLVER_ANNOTATION;
import static org.nemesis.registration.NameAndMimeTypeUtils.cleanMimeType;
import static org.nemesis.registration.typenames.JdkTypes.IO_EXCEPTION;
import static org.nemesis.registration.typenames.JdkTypes.MAP;
import static org.nemesis.registration.typenames.JdkTypes.SET;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION;
import static org.nemesis.registration.typenames.KnownTypes.GRAMMAR_SOURCE;
import static org.nemesis.registration.typenames.KnownTypes.IMPORT_FINDER;
import static org.nemesis.registration.typenames.KnownTypes.IMPORT_KEY_SUPPLIER;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_REGION_KEY;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGION;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGIONS;
import static org.nemesis.registration.typenames.KnownTypes.REGISTERABLE_RESOLVER;
import static org.nemesis.registration.typenames.KnownTypes.RESOLUTION_CONSUMER;
import static org.nemesis.registration.typenames.KnownTypes.SEMANTIC_REGIONS;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDER;
import static org.nemesis.registration.typenames.KnownTypes.UNKNOWN_NAME_REFERENCE;

/**
 *
 * @author Tim Boudreau
 */
//@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({IMPORTS_ANNOTATION, RESOLVER_ANNOTATION})
public class ImportsAndResolvableProcessor extends LayerGeneratingDelegate {

    static final String IMPORTS_ANNOTATION = "org.nemesis.antlr.spi.language.Imports";
    static final String RESOLVER_ANNOTATION = "org.nemesis.antlr.spi.language.ReferenceableFromImports";
    private BiPredicate<? super AnnotationMirror, ? super Element> test;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        test = utils.multiAnnotations()
                .whereAnnotationType(IMPORTS_ANNOTATION, b -> {
                    b.testMember("mimeType").validateStringValueAsMimeType();
                    b.testMember("strategy").mustBeSubtypeOf(IMPORTS_ANNOTATION + ".ImportStrategy");
                    b.onlyOneMemberMayBeSet("strategy", "simpleStrategy");
                    b.whereFieldIsAnnotated(eltest -> {
                        eltest.hasModifiers(Modifier.FINAL, Modifier.STATIC)
                                .isSubTypeOf(NAMED_REGION_KEY.qnameNotouch());
                    });
                }).whereAnnotationType(RESOLVER_ANNOTATION, b -> {
            b.testMember("mimeType").addPredicate("Mime type", mir -> {
                Predicate<String> mimeTest = NameAndMimeTypeUtils.complexMimeTypeValidator(true, utils(), null, mir);
                String value = utils().annotationValue(mir, "mimeType", String.class);
                return mimeTest.test(value);
            }).build();
            b.whereFieldIsAnnotated(eltest -> {
                eltest.hasModifiers(Modifier.FINAL, Modifier.STATIC)
                        .isSubTypeOf(NAMED_REGION_KEY.qnameNotouch());
            }).build();
        }).build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        return test.test(mirror, el);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String pkg = utils().packageName(var);
        String mime = cleanMimeType(utils().annotationValue(mirror, "mimeType", String.class));
        if (IMPORTS_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            writeOne(handleImportsAnnotation(pkg, var, mirror, utils(), mime));
        } else if (RESOLVER_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
            writeOne(handleResolverAnnotation(pkg, var, mirror, utils(), mime));
        }
        return false;
    }

    private ClassBuilder<String> handleImportsAnnotation(String pkg, VariableElement var, AnnotationMirror mirror, AnnotationUtils utils, String mimeType) {
        String importFinderClassName = capitalize(AnnotationUtils.stripMimeType(mimeType) + "ImportFinder_" + var.getSimpleName());
        String varQName = AnnotationUtils.enclosingType(var).getQualifiedName() + "."
                + var.getSimpleName();
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(importFinderClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(
                        SERVICE_PROVIDER.qname(),
                        IMPORT_FINDER.qname(),
                        EXTRACTION.qname(),
                        GRAMMAR_SOURCE.qname(),
                        REGISTERABLE_RESOLVER.qname(),
                        NAMED_SEMANTIC_REGION.qname(),
                        UNKNOWN_NAME_REFERENCE.qname(),
                        NAMED_REGION_KEY.qname(),
                        IMPORT_KEY_SUPPLIER.qname(),
                        SET.qname())
                .implementing(IMPORT_FINDER.simpleName(), IMPORT_KEY_SUPPLIER.simpleName())
                .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                    ab.addClassArgument("service", IMPORT_FINDER.simpleName())
                            .addArgument("path", "antlr/resolvers/" + mimeType);
                })
                .field("DELEGATE", fb -> {
                    fb.withModifier(STATIC, FINAL)
                            // ImportFinder.forKeys(AntlrKeys.IMPORTS);
                            .initializedFromInvocationOf("forKeys").withArgument(varQName)
                            .on(IMPORT_FINDER.simpleName())
                            .ofType(IMPORT_FINDER.simpleName());
                })
                .constructor(con -> {
                    con.setModifier(PUBLIC)
                            .body(bb -> {
                                bb.log("Created {0} over {1}", Level.FINEST, "this", "DELEGATE");
                            });
                })
                .overridePublic("allImports", mb -> {
                    mb.returning("Set<" + GRAMMAR_SOURCE.simpleName() + "<?>>")
                            .addArgument(EXTRACTION.simpleName(), "importer")
                            .addArgument("Set<? super " + NAMED_SEMANTIC_REGION.simpleName()
                                    + "<? extends Enum<?>>>", "notFound")
                            .body(bb -> {
                                bb.declare("result")
                                        .initializedByInvoking("allImports")
                                        .withArgument("importer").withArgument("notFound")
                                        .on("DELEGATE").as("Set<" + GRAMMAR_SOURCE.simpleName() + "<?>>");
                                bb.log("{0} found imports {1} for {2} ", Level.FINE, "DELEGATE",
                                        "result", "importer.source()");
                                bb.returning("result");
                            });
                })
                .overridePublic("possibleImportersOf", mb -> {
                    mb.withTypeParam("K extends Enum<K>")
                            .returning("Set<" + GRAMMAR_SOURCE.simpleName() + "<?>>")
                            .addArgument(UNKNOWN_NAME_REFERENCE.simpleName() + "<K>", "ref")
                            .addArgument(EXTRACTION.simpleName(), "in")
                            .body(bb -> {
                                bb.returningInvocationOf("possibleImportersOf")
                                        .withArgument("ref")
                                        .withArgument("in")
                                        .on("DELEGATE").endBlock();
                            });
                })
                .overridePublic("createReferenceResolver", mb -> {
                    mb.withTypeParam("K extends Enum<K>")
                            .addArgument(NAMED_REGION_KEY.simpleName() + "<K>", "key")
                            .returning(REGISTERABLE_RESOLVER.simpleName() + "<K>")
                            .body().returningInvocationOf("createReferenceResolver")
                            .withArgument("key").on("DELEGATE").endBlock();
                    ;
                })
                .overridePublic("get", mb -> {
                    mb.returning(NAMED_REGION_KEY.simpleName() + "<?>[]")
                            .body()
                            .returningValue().toArrayLiteral(NAMED_REGION_KEY.simpleName() + "<?>")
                            .add(varQName).closeArrayLiteral();

                });
        // implement Key based
        return cb;
    }

    private ClassBuilder<String> handleResolverAnnotation(String pkg, VariableElement var, AnnotationMirror mirror, AnnotationUtils utils, String mimeType) {
        String resolverClassName = NameAndMimeTypeUtils.prefixFromMimeType(mimeType)
                + "Resolver_" + var.getSimpleName();
        TypeMirror mir = utils.getTypeParameter(0, var);
        if (mir == null) {
            utils.fail("Could not find a type parameter on " + var);
        }

        String rcType = RESOLUTION_CONSUMER.simpleName() + "<"
                + GRAMMAR_SOURCE.simpleName() + "<?>,"
                + NAMED_SEMANTIC_REGIONS.simpleName() + "<" + simpleName(mir.toString()) + ">,"
                + NAMED_SEMANTIC_REGION.simpleName()+ "<" + simpleName(mir.toString()) + ">,"
                + simpleName(mir) + ",X>";

        // ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes, X> c
        String varQName = AnnotationUtils.enclosingType(var).getQualifiedName() + "."
                + var.getSimpleName();
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(resolverClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(
                        SERVICE_PROVIDER.qname(),
                        IMPORT_FINDER.qname(),
                        EXTRACTION.qname(),
                        GRAMMAR_SOURCE.qname(),
                        REGISTERABLE_RESOLVER.qname(),
                        NAMED_SEMANTIC_REGION.qname(),
                        UNKNOWN_NAME_REFERENCE.qname(),
                        NAMED_SEMANTIC_REGIONS.qname(),
                        IO_EXCEPTION.qname(),
                        SEMANTIC_REGIONS.qname(),
                        RESOLUTION_CONSUMER.qname(),
                        SET.qname(),
                        MAP.qname(),
                        mir.toString()
                ).implementing(REGISTERABLE_RESOLVER.parametrizedName(simpleName(mir.toString())))
                .annotatedWith("ServiceProvider", ab -> {
                    ab.addClassArgument("service", REGISTERABLE_RESOLVER.simpleName())
                            .addArgument("path", "antlr/resolvers/" + mimeType);
                }).field("delegate").withModifier(PRIVATE, FINAL).ofType(REGISTERABLE_RESOLVER.parametrizedName(simpleName(mir)))
                .constructor(con -> {
                    con.setModifier(PUBLIC).body(bb -> {
                        bb.declare("importFinder").initializedByInvoking("forMimeType")
                                .withStringLiteral(mimeType)
                                .on(IMPORT_FINDER.simpleName())
                                .as(IMPORT_FINDER.simpleName());
                        bb.assign("delegate").toInvocation("createReferenceResolver")
                                .withArgument(varQName)
                                .on("importFinder");
                    });
                })
                .overridePublic("resolve", mb -> {
                    mb.withTypeParam("X")
                            .addArgument(EXTRACTION.simpleName(), "extraction")
                            .addArgument(UNKNOWN_NAME_REFERENCE.parametrizedName(simpleName(mir.toString())), "ref")
                            .addArgument(rcType, "c")
                            .returning("X")
                            .throwing(simpleName(IO_EXCEPTION.simpleName()))
                            .body(bb -> {
                                bb.returningInvocationOf("resolve")
                                        .withArguments("extraction", "ref", "c")
                                        .on("delegate");
                            });

                })
                .overridePublic("resolveAll", mb -> {
                    mb.withTypeParam("X")
                            .addArgument(EXTRACTION.simpleName(), "extraction")
                            .addArgument(SEMANTIC_REGIONS.simpleName() + "<" + UNKNOWN_NAME_REFERENCE.simpleName() + "<" + simpleName(mir.toString()) + ">>", "refs")
                            .addArgument(rcType, "c")
                            .returning(simpleName(MAP.simpleName()) + "<" + UNKNOWN_NAME_REFERENCE.simpleName() + "<" + simpleName(mir.toString()) + ">, X>")
                            .throwing(simpleName(IO_EXCEPTION.simpleName()))
                            .body(bb -> {
                                bb.log("Find imports of {0} with {1}", Level.FINE, "extraction.source()", "delegate");
                                bb.returningInvocationOf("resolveAll")
                                        .withArguments("extraction", "refs", "c")
                                        .on("delegate");
                            });
                })
                .overridePublic("type", mb -> {
                    mb.returning("Class<" + simpleName(mir) + ">")
                            .body().returning(simpleName(mir) + ".class").endBlock();
                });
        return cb;
    }
}

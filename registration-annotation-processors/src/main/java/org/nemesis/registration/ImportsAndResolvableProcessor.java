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
import static org.nemesis.registration.typenames.JdkTypes.SET;
import static org.nemesis.registration.typenames.KnownTypes.EXTRACTION;
import static org.nemesis.registration.typenames.KnownTypes.GRAMMAR_SOURCE;
import static org.nemesis.registration.typenames.KnownTypes.IMPORT_FINDER;
import static org.nemesis.registration.typenames.KnownTypes.IMPORT_KEY_SUPPLIER;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_REGION_KEY;
import static org.nemesis.registration.typenames.KnownTypes.NAMED_SEMANTIC_REGION;
import static org.nemesis.registration.typenames.KnownTypes.REGISTERABLE_RESOLVER;
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
    static final String NAMED_REGION_KEY_TYPE = "org.nemesis.extraction.key.NamedRegionKey";
    static final String NAMED_REGION_REFERENCE_KEY_TYPE = "org.nemesis.extraction.key.NameReferenceSetKey";
    static final String IMPORT_FINDER_TYPE = "org.nemesis.extraction.attribution.ImportFinder";
    static final String IMPORT_KEY_SUPPLIER_TYPE = "org.nemesis.extraction.attribution.ImportKeySupplier";
    static final String SERVICE_PROVIDER_ANNOTATION_TYPE = "org.openide.util.lookup.ServiceProvider";
    static final String EXTRACTION_TYPE = "org.nemesis.extraction.Extraction";
    static final String GRAMMAR_SOURCE_TYPE = "org.nemesis.source.api.GrammarSource";
    static final String UNKNOWN_NAME_REFERENCE_TYPE = "org.nemesis.extraction.UnknownNameReference";
    static final String NAMED_SEMANTIC_REGION_TYPE = "org.nemesis.data.named.NamedSemanticRegion";
    static final String NAMED_SEMANTIC_REGIONS_TYPE = "org.nemesis.data.named.NamedSemanticRegions";
    static final String REGISTERABLE_RESOLVER_TYPE = "org.nemesis.extraction.attribution.RegisterableResolver";
    static final String IO_EXCEPTION_TYPE = "java.io.IOException";
    static final String MAP_TYPE = "java.util.Map";
    static final String RESOLUTION_CONSUMER_TYPE = "org.nemesis.extraction.ResolutionConsumer";
    static final String SEMANTIC_REGIONS_TYPE = "org.nemesis.data.SemanticRegions";
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
                                .isSubTypeOf(NAMED_REGION_KEY_TYPE);
                    });
                }).whereAnnotationType(RESOLVER_ANNOTATION, b -> {
            b.testMember("mimeType").validateStringValueAsMimeType();
            b.whereFieldIsAnnotated(eltest -> {
                eltest.hasModifiers(Modifier.FINAL, Modifier.STATIC)
                        .isSubTypeOf(NAMED_REGION_KEY_TYPE);
            });
        }).build();
    }

    @Override
    protected boolean validateAnnotationMirror(AnnotationMirror mirror, ElementKind kind, Element el) {
        return test.test(mirror, el);
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        String pkg = utils().packageName(var);
        String mime = utils().annotationValue(mirror, "mimeType", String.class);
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
                .implementing(simpleName(IMPORT_FINDER_TYPE), simpleName(IMPORT_KEY_SUPPLIER_TYPE))
                .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                    ab.addClassArgument("service", simpleName(IMPORT_FINDER_TYPE))
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
                                bb.log("Created {0} over {1}", Level.INFO, "this", "DELEGATE");
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
//                            .returning("new NamedRegionKey<?>[]{" + varQName + "}")
//                            .endBlock();
//                    mb.returning("NamedRegionKey<?>[]")
//                            .body()
//                            .returning("new NamedRegionKey<?>[]{" + varQName + "}")
//                            .endBlock();

                });
        // implement Key based
        return cb;
    }

    private ClassBuilder<String> handleResolverAnnotation(String pkg, VariableElement var, AnnotationMirror mirror, AnnotationUtils utils, String mimeType) {
        String resolverClassName = capitalize(AnnotationUtils.stripMimeType(mimeType) + "Resolver_" + var.getSimpleName());
        TypeMirror mir = utils.getTypeParameter(0, var);
        if (mir == null) {
            utils.fail("Could not find a type parameter on " + var);
        }

        String rcType = simpleName(RESOLUTION_CONSUMER_TYPE) + "<"
                + simpleName(GRAMMAR_SOURCE_TYPE) + "<?>,"
                + simpleName(NAMED_SEMANTIC_REGIONS_TYPE) + "<" + simpleName(mir.toString()) + ">,"
                + simpleName(NAMED_SEMANTIC_REGION_TYPE) + "<" + simpleName(mir.toString()) + ">,"
                + simpleName(mir) + ",X>";

        // ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes, X> c
        String varQName = AnnotationUtils.enclosingType(var).getQualifiedName() + "."
                + var.getSimpleName();
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(resolverClassName)
                .withModifier(PUBLIC, FINAL)
                .importing(
                        SERVICE_PROVIDER_ANNOTATION_TYPE,
                        IMPORT_FINDER_TYPE,
                        EXTRACTION_TYPE,
                        GRAMMAR_SOURCE_TYPE,
                        REGISTERABLE_RESOLVER_TYPE,
                        NAMED_SEMANTIC_REGION_TYPE,
                        UNKNOWN_NAME_REFERENCE_TYPE,
                        NAMED_SEMANTIC_REGIONS_TYPE,
                        IO_EXCEPTION_TYPE,
                        SEMANTIC_REGIONS_TYPE,
                        RESOLUTION_CONSUMER_TYPE,
                        MAP_TYPE,
                        mir.toString(),
                        "java.util.Set")
                .implementing(simpleName(REGISTERABLE_RESOLVER_TYPE) + "<" + simpleName(mir.toString()) + ">")
                .annotatedWith("ServiceProvider", ab -> {
                    ab.addClassArgument("service", simpleName(REGISTERABLE_RESOLVER_TYPE))
                            .addArgument("path", "antlr/resolvers/" + mimeType);
                }).field("delegate").withModifier(PRIVATE, FINAL).ofType(simpleName(REGISTERABLE_RESOLVER_TYPE)
                + "<" + simpleName(mir.toString()) + ">")
                .constructor(con -> {
                    con.setModifier(PUBLIC).body(bb -> {
                        bb.declare("importFinder").initializedByInvoking("forMimeType")
                                .withStringLiteral(mimeType)
                                .on(simpleName(IMPORT_FINDER_TYPE))
                                .as("ImportFinder");
                        bb.assign("delegate").toInvocation("createReferenceResolver")
                                .withArgument(varQName)
                                .on("importFinder");
                    });
                })
                .overridePublic("resolve", mb -> {
                    mb.withTypeParam("X")
                            .addArgument(simpleName(EXTRACTION_TYPE), "extraction")
                            .addArgument(simpleName(UNKNOWN_NAME_REFERENCE_TYPE) + "<" + simpleName(mir.toString()) + ">", "ref")
                            .addArgument(rcType, "c")
                            .returning("X")
                            .throwing(simpleName(IO_EXCEPTION_TYPE))
                            .body(bb -> {
                                bb.returningInvocationOf("resolve")
                                        .withArguments("extraction", "ref", "c")
                                        .on("delegate");
                            });

                })
                .overridePublic("resolveAll", mb -> {
                    mb.withTypeParam("X")
                            .addArgument(simpleName(EXTRACTION_TYPE), "extraction")
                            .addArgument(simpleName(SEMANTIC_REGIONS_TYPE) + "<" + simpleName(UNKNOWN_NAME_REFERENCE_TYPE) + "<" + simpleName(mir.toString()) + ">>", "refs")
                            .addArgument(rcType, "c")
                            .returning(simpleName(MAP_TYPE) + "<" + simpleName(UNKNOWN_NAME_REFERENCE_TYPE) + "<" + simpleName(mir.toString()) + ">, X>")
                            .throwing(simpleName(IO_EXCEPTION_TYPE))
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

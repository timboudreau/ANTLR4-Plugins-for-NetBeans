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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.LinesBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static org.nemesis.registration.typenames.JdkTypes.SUPPLIER;
import static org.nemesis.registration.typenames.KnownTypes.PARSER_RULE_CONTEXT;
import static org.nemesis.registration.typenames.KnownTypes.RULES_MAPPING;
import static org.nemesis.registration.typenames.KnownTypes.SERVICE_PROVIDER;

/**
 *
 * @author Tim Boudreau
 */
class ParserProxy {

    private final Map<Integer, String> ruleIdForName;
    private final Map<String, Integer> nameForRuleId;
    private final ExecutableElement parserEntryPointMethod;
    private final TypeElement parserClassElement;
    private final TypeMirror parserClass;
    private final Map<String, ExecutableElement> methodsForNames;
    private final int entryPointRuleNumber;
    private final Map<Integer, String> ruleConstantFieldNameForRuleId;
    private final Map<String, TypeMirror> classForRuleName;

    public List<String> ruleNamesSortedById() {
        List<String> result = new ArrayList<>();
        List<Integer> keys = new ArrayList<>(ruleIdForName.keySet());
        Collections.sort(keys);
        for (Integer key : keys) {
            result.add(ruleIdForName.get(key));
        }
        return result;
    }

    static ParserProxy create(int entryPointRuleId, TypeMirror parserClass, AnnotationUtils utils) {
        ExecutableElement parserEntryPointMethod = null;
        TypeElement parserClassElement = parserClass == null ? null : utils.processingEnv().getElementUtils().getTypeElement(parserClass.toString());
        Map<Integer, String> ruleIdForName = new HashMap<>();
        Map<String, Integer> nameForRuleId = new HashMap<>();
        Map<String, ExecutableElement> methodsForNames = new HashMap<>();
        Map<Integer, String> ruleConstantFieldNameForRuleId = new HashMap<>();
        Map<String, TypeMirror> classForRuleName = new HashMap<>();
        TypeMirror parserRuleContextType = utils.type(PARSER_RULE_CONTEXT.qname());
        if (parserClassElement != null) {
            String entryPointRule = null;
            for (Element el : parserClassElement.getEnclosedElements()) {
                if (el instanceof VariableElement) {
                    VariableElement ve = (VariableElement) el;
                    String nm = ve.getSimpleName().toString();
                    if (nm == null || !nm.startsWith("RULE_") || !"int".equals(ve.asType().toString()) || ve.getConstantValue() == null || nm.length() <= 5) {
                        continue;
                    }
                    String ruleName = nm.substring(5);

                    int val = (Integer) ve.getConstantValue();

                    nameForRuleId.put(ruleName, val);
                    ruleConstantFieldNameForRuleId.put(val, ve.getSimpleName().toString());

                    ruleIdForName.put(val, ruleName);
                    if (val == entryPointRuleId) {
                        entryPointRule = ruleName;
                    }
                } else if (entryPointRule != null && el instanceof ExecutableElement) {
                    Name name = el.getSimpleName();
                    ExecutableElement ex = (ExecutableElement) el;
                    if (utils.processingEnv().getTypeUtils().isAssignable(ex.getReturnType(), parserRuleContextType)) {
                        if (name != null && name.contentEquals(entryPointRule)) {
                            parserEntryPointMethod = ex;
                        }
                        methodsForNames.put(name.toString(), ex);
                        classForRuleName.put(name.toString(), ex.getReturnType());

                    }
                } else if (el instanceof ExecutableElement) {
                    Name name = el.getSimpleName();
                    methodsForNames.put(name.toString(), (ExecutableElement) el);
                }
            }
        }
        if (parserEntryPointMethod == null) {
            utils.fail("Could not find entry point method for rule id " + entryPointRuleId + " in " + parserClass);
            return null;
        }
        return new ParserProxy(ruleIdForName, nameForRuleId, parserEntryPointMethod,
                parserClassElement, parserClass, methodsForNames,
                entryPointRuleId, ruleConstantFieldNameForRuleId,
                classForRuleName);
    }

    ClassBuilder<String> createRuleTypeMapper(String pkg, String prefix, String mimeType) {
        ClassBuilder<String> result = ClassBuilder.forPackage(pkg)
                .named(prefix + "RulesMapping")
                .importing(
                        parserClassFqn(),
                        SUPPLIER.qname(),
                        PARSER_RULE_CONTEXT.qname(),
                        RULES_MAPPING.qname(),
                        SERVICE_PROVIDER.qname()
                )
                .extending(RULES_MAPPING.parametrizedName(parserClassSimple()))
                .withModifier(PUBLIC, FINAL)
                .constructor(cb -> {
                    cb.setModifier(PUBLIC)
                            .body().invoke("super")
                            .withStringLiteral(mimeType)
                            .withClassArgument(parserClassSimple()).inScope().endBlock();
                })
                .annotatedWith(SERVICE_PROVIDER.simpleName(), ab -> {
                    ab.addClassArgument("service", RULES_MAPPING.simpleName())
                            .addArgument("path", "antlr/" + mimeType + "/rules");
                })
                .method("ruleTypeForName", (ClassBuilder.MethodBuilder<?> mb) -> {
                    mb.addArgument("String", "name")
                            .withModifier(PUBLIC, FINAL)
                            .returning("Class<? extends ParserRuleContext>")
                            .body((BlockBuilder<?> bb) -> {
                                bb.switchingOn("name", (ClassBuilder.SwitchBuilder<?> sw) -> {
                                    for (Map.Entry<String, TypeMirror> e : classForRuleName.entrySet()) {
                                        sw.inStringLiteralCase(e.getKey()).returning(e.getValue().toString() + ".class").endBlock();
                                    }
                                    sw.inDefaultCase().returningNull().endBlock();
                                });
                            });
                }).method("nameForRuleType", (ClassBuilder.MethodBuilder<?> mb) -> {
            mb.addArgument("Class<? extends ParserRuleContext>", "type")
                    .withModifier(PUBLIC, FINAL)
                    .returning("String")
                    .body((BlockBuilder<?> bb) -> {
                        bb.switchingOn("type.getName()", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (Map.Entry<String, TypeMirror> e : classForRuleName.entrySet()) {
                                sw.inStringLiteralCase(e.getValue().toString())
                                        .returningStringLiteral(e.getKey()).endBlock();
                            }
                            sw.inDefaultCase().returningNull().endBlock();
                        });
                    });
        }).method("ruleIdForType", (ClassBuilder.MethodBuilder<?> mb) -> {
            mb.addArgument("Class<? extends ParserRuleContext>", "type")
                    .withModifier(PUBLIC, FINAL)
                    .returning("int")
                    .body((BlockBuilder<?> bb) -> {
                        bb.switchingOn("type.getName()", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (Map.Entry<String, TypeMirror> e : classForRuleName.entrySet()) {
//                                this.nameForRuleId.get(e.getKey());
                                Integer val = this.ruleId(e.getKey());
                                if (val != null) {

                                    sw.inStringLiteralCase(e.getValue().toString())
                                            .returning(val).endBlock();
                                }
                            }
                            sw.inDefaultCase().returning(-1).endBlock();
                        });
                    });
        }).method("typeForRuleId", (ClassBuilder.MethodBuilder<?> mb) -> {
            mb.addArgument("int", "ruleId")
                    .withModifier(PUBLIC, FINAL)
                    .returning("Class<? extends ParserRuleContext>")
                    .body((BlockBuilder<?> bb) -> {
                        bb.switchingOn("ruleId", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (Map.Entry<String, TypeMirror> e : classForRuleName.entrySet()) {
//                                this.nameForRuleId.get(e.getKey());
                                Integer val = this.ruleId(e.getKey());
                                if (val != null) {
                                    sw.inCase(val)
                                            .returning(e.getValue().toString() + ".class").endBlock();
                                }
                            }
                            sw.inDefaultCase().returningNull().endBlock();
                        });
                    });
        }).method("nameForRuleId", (ClassBuilder.MethodBuilder<?> mb) -> {
            mb.addArgument("int", "ruleId")
                    .withModifier(PUBLIC, FINAL)
                    .returning("String")
                    .body((BlockBuilder<?> bb) -> {
                        bb.switchingOn("ruleId", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (Map.Entry<Integer, String> e : ruleIdForName.entrySet()) {
//                                this.nameForRuleId.get(e.getKey());
                                sw.inCase(e.getKey())
                                        .returningStringLiteral(
                                                e.getValue().toString()).endBlock();
                            }
                            sw.inDefaultCase().returningNull().endBlock();
                        });
                    });
        }).method("ruleIdForName", (ClassBuilder.MethodBuilder<?> mb) -> {
            mb.addArgument("String", "name")
                    .withModifier(PUBLIC, FINAL)
                    .returning("int")
                    .body((BlockBuilder<?> bb) -> {
                        bb.switchingOn("name", (ClassBuilder.SwitchBuilder<?> sw) -> {
                            for (Map.Entry<String, Integer> e : nameForRuleId.entrySet()) {
//                                this.nameForRuleId.get(e.getKey());
                                sw.inStringLiteralCase(e.getKey())
                                        .returning(e.getValue()).endBlock();
                            }
                            sw.inDefaultCase().returning(-1).endBlock();
                        });
                    });
        }).method("invokeRule", mb -> {
            mb.addArgument("int", "ruleId").addArgument(parserClassSimple(), "on")
                    .withModifier(PUBLIC, FINAL)
                    .returning(PARSER_RULE_CONTEXT.simpleName())
                    .body((BlockBuilder<?> bb) -> {
                        bb.declare("targetMethod").as("Supplier<? extends "
                                + PARSER_RULE_CONTEXT.simpleName() + ">");
                        bb.switchingOn("ruleId", sw -> {
                            for (Map.Entry<Integer, String> e : ruleIdForName.entrySet()) {
                                sw.inCase(e.getKey(), switchCase -> {
                                    switchCase.assign("targetMethod")
                                            .toExpression("on::"
                                                    + this.methodsForNames.get(
                                                            e.getValue()).getSimpleName());
                                    switchCase.statement("break");
                                });
                            }
                            sw.inDefaultCase().andThrow(nb -> {
                                nb.withArgument(LinesBuilder.stringLiteral("No such id: ")
                                        + " + ruleId + "
                                        + LinesBuilder.stringLiteral(". Possible ids are 0:"
                                                + Collections.max(this.ruleIdForName.keySet())))
                                        .ofType("IllegalArgumentException");
                            });
                        }).returningInvocationOf("get").on("targetMethod");
                    });
        });
        return result;
    }

    static String typeName(TypeMirror mirror) {
        String result = mirror.toString();
        if (result.startsWith("()")) {
            return result.substring(2);
        }
        return result;
    }

    public String ruleFieldForRuleId(int id) {
        return ruleConstantFieldNameForRuleId.get(id);
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
        return AnnotationUtils.simpleName(parserEntryPointReturnType().toString());
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
        return AnnotationUtils.simpleName(parserClassFqn());
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

    private ParserProxy(Map<Integer, String> ruleIdForName, Map<String, Integer> nameForRuleId, ExecutableElement parserEntryPointMethod, TypeElement parserClassElement, TypeMirror parserClass, Map<String, ExecutableElement> methodsForNames, int entryPointRuleNumber, Map<Integer, String> ruleConstantFieldNameForRuleId, Map<String, TypeMirror> classForRuleName) {
        this.ruleIdForName = ruleIdForName;
        this.nameForRuleId = nameForRuleId;
        this.parserEntryPointMethod = parserEntryPointMethod;
        this.parserClassElement = parserClassElement;
        this.parserClass = parserClass;
        this.methodsForNames = methodsForNames;
        this.entryPointRuleNumber = entryPointRuleNumber;
        this.ruleConstantFieldNameForRuleId = ruleConstantFieldNameForRuleId;
        this.classForRuleName = classForRuleName;
    }

}

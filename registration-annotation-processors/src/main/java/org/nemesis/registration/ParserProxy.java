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
            utils.fail("Could not find entry point method for rule id " + entryPointRuleId + " in " + parserClass);
            return null;
        }
        return new ParserProxy(ruleIdForName, nameForRuleId, parserEntryPointMethod,
                parserClassElement, parserClass, methodsForNames,
                entryPointRuleId, ruleConstantFieldNameForRuleId);
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

    private ParserProxy(Map<Integer, String> ruleIdForName, Map<String, Integer> nameForRuleId, ExecutableElement parserEntryPointMethod, TypeElement parserClassElement, TypeMirror parserClass, Map<String, ExecutableElement> methodsForNames, int entryPointRuleNumber, Map<Integer, String> ruleConstantFieldNameForRuleId) {
        this.ruleIdForName = ruleIdForName;
        this.nameForRuleId = nameForRuleId;
        this.parserEntryPointMethod = parserEntryPointMethod;
        this.parserClassElement = parserClassElement;
        this.parserClass = parserClass;
        this.methodsForNames = methodsForNames;
        this.entryPointRuleNumber = entryPointRuleNumber;
        this.ruleConstantFieldNameForRuleId = ruleConstantFieldNameForRuleId;
    }

}

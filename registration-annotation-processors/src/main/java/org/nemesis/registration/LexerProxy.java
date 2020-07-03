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
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import com.mastfrog.annotation.AnnotationUtils;
import javax.lang.model.element.ExecutableElement;

/**
 *
 * @author Tim Boudreau
 */
class LexerProxy implements LexerInfo {

    private final TypeMirror lexerClass;
    private final TypeElement lexerClassElement;
    private final Map<Integer, String> tokenNameForIndex;
    private final Map<String, Integer> indexForTokenName;
    private final int last;

    private LexerProxy(TypeMirror lexerClass, TypeElement lexerClassElement, Map<Integer, String> tokenNameForIndex, Map<String, Integer> indexForTokenName, int last) {
        this.lexerClass = lexerClass;
        this.lexerClassElement = lexerClassElement;
        this.tokenNameForIndex = tokenNameForIndex;
        this.indexForTokenName = indexForTokenName;
        this.last = last;
    }

    public String implicitLanguageName() {
        String simple = lexerClassSimple();
        if (!"Lexer".equals(simple) && simple.endsWith("Lexer")) {
            int end = simple.length() - "Lexer".length();
            return simple.substring(0, end);
        }
        return simple;
    }

    @Override
    public String tokenFieldReference(int tokenId) {
        String nm = tokenNameForIndex.get(tokenId);
        if (nm == null) {
            throw new IllegalArgumentException("No such token id " + tokenId);
        }
        return lexerClassSimple() + "." + nm;
    }

    @Override
    public String tokenFieldReference(String tokenName) {
        if (!indexForTokenName.containsKey(tokenName)) {
            throw new IllegalArgumentException("No token named '" + tokenName + "'");
        }
        return lexerClassSimple() + "." + tokenName;
    }

    boolean hasInitialStackedModeNumberMethods(AnnotationUtils utils) {
        // Some defunct (?) stuff from the original module for lexer restart
        // info
        ExecutableElement getterMethod = null;
        ExecutableElement setterMethod = null;
        for (Element e : lexerClassElement.getEnclosedElements()) {
            switch (e.getKind()) {
                case METHOD:
                    if (e instanceof ExecutableElement) {
                        ExecutableElement ex = (ExecutableElement) e;
                        if ("getInitialStackedModeNumber".equals(ex.getSimpleName().toString())) {
                            if ("int".equals(ex.getReturnType().toString()) && ex.getParameters().isEmpty()) {
                                getterMethod = ex;
                                if (setterMethod != null) {
                                    break;
                                }
                            }
                        } else if ("setInitialStackedModeNumber".equals(ex.getSimpleName().toString())) {
                            List<? extends VariableElement> params = ex.getParameters();
                            if (params.size() == 1 && "int".equals(params.get(0).asType().toString())) {
                                if ("void".equals(ex.getReturnType().toString())) {
                                    setterMethod = ex;
                                    if (getterMethod != null) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
            }
        }
        return getterMethod != null && setterMethod != null;
    }

    static LexerProxy create(AnnotationMirror mirror, Element target, AnnotationUtils utils) {
        TypeMirror lexerClass = utils.typeForSingleClassAnnotationMember(mirror, "lexer");
        if (mirror == null) {
            utils.fail("Could not locate lexer class on classpath", target);
            return null;
        }
        return create(lexerClass, target, utils);
    }

    static LexerProxy create(TypeMirror lexerClass, Element target, AnnotationUtils utils) {
        TypeElement lexerClassElement = utils.processingEnv().getElementUtils().getTypeElement(lexerClass.toString());
        if (lexerClassElement == null) {
            utils.fail("Could not resolve a TypeElement for " + lexerClass, target);
            return null;
        }
        return create(lexerClassElement, target, utils);
    }

    static LexerProxy create(TypeElement lexerClassElement, Element target, AnnotationUtils utils) {
        Map<Integer, String> tokenNameForIndex = new HashMap<>();
        Map<String, Integer> indexForTokenName = new HashMap<>();
        int last = -2;
        for (Element el : lexerClassElement.getEnclosedElements()) {
            if (el instanceof VariableElement) {
                VariableElement ve = (VariableElement) el;
                String nm = ve.getSimpleName().toString();
                if (nm == null || nm.startsWith("_") || !"int".equals(ve.asType().toString())) {
                    continue;
                }
                if (ve.getConstantValue() != null) {
                    Integer val = (Integer) ve.getConstantValue();
                    if (val < last) {
                        break;
                    }
                    tokenNameForIndex.put(val, nm);
                    indexForTokenName.put(nm, val);
                    last = val;
                }
            }
        }
        tokenNameForIndex.put(-1, "EOF");
        tokenNameForIndex.put(last + 1, "$ERRONEOUS");
        return new LexerProxy(lexerClassElement.asType(),
                lexerClassElement, tokenNameForIndex, indexForTokenName, last);
    }

    public Set<String> tokenNames() {
        return new TreeSet<>(indexForTokenName.keySet());
    }

    public Set<Integer> tokenTypes() {
        return new TreeSet<>(tokenNameForIndex.keySet());
    }

    @Override
    public String tokenName(int type) {
        return tokenNameForIndex.get(type);
    }

    @Override
    public Integer tokenIndex(String name) {
        return indexForTokenName.get(name);
    }

    @Override
    public String lexerClassFqn() {
        return lexerClass.toString();
    }

    public int erroneousTokenId() {
        return last + 1;
    }

    public int maxToken() {
        return last;
    }
    String lcs;

    @Override
    public String lexerClassSimple() {
        return lcs == null ? lcs = AnnotationUtils.simpleName(lexerClassFqn()) : lcs;
    }

    public TypeMirror lexerType() {
        return lexerClass;
    }

    public TypeElement lexerClass() {
        return lexerClassElement;
    }

    public int lastDeclaredTokenType() {
        return last;
    }

    public boolean isSynthetic(int tokenType) {
        return tokenType < 0 || tokenType > last;
    }

    public String toFieldName(String tokenName) {
        return "TOK_" + tokenName.toUpperCase();
    }

    public String toFieldName(int tokenId) {
        String tokenName = tokenName(tokenId);
        return tokenName == null ? null : toFieldName(tokenName);
    }

    public List<Integer> allTypesSorted() {
        List<Integer> result = new ArrayList<>(tokenNameForIndex.keySet());
        Collections.sort(result);
        return result;
    }

    public List<Integer> allTypesSortedByName() {
        List<Integer> result = new ArrayList<>(tokenNameForIndex.keySet());
        Collections.sort(result, (a, b) -> {
            String an = tokenName(a);
            String bn = tokenName(b);
            return an.compareToIgnoreCase(bn);
        });
        return result;
    }

    boolean typeExists(Integer val) {
        if (val == null) {
            throw new IllegalArgumentException("Null value");
        }
        return tokenNameForIndex.containsKey(val);
    }

}

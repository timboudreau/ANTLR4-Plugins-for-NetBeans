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
package org.nemesis.data.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import com.mastfrog.graph.StringGraph;

/**
 *
 * @author Tim Boudreau
 */
class RuleTreeBuilder extends ANTLRv4BaseVisitor<Void> {

    Map<String, Set<String>> ruleReferences = new HashMap<>();
    Map<String, Set<String>> ruleReferencedBy = new HashMap<>();
    String currRule;

    void addEdge(String referencer, String referenced) {
        System.out.println("addEdge " + referencer + " -> " + referenced);
        Set<String> outbound = ruleReferences.get(referencer);
        if (outbound == null) {
            outbound = new HashSet<>(10);
            ruleReferences.put(referencer, outbound);
        }
        outbound.add(referenced);
        Set<String> inbound = ruleReferencedBy.get(referenced);
        if (inbound == null) {
            inbound = new HashSet<>(10);
            ruleReferencedBy.put(referenced, inbound);
        }
        inbound.add(referencer);
    }

    @Override
    public Void visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
        //            System.out.println("VISIT RULE REF " + ctx.getText() + " in " + currRule);
        addEdge(currRule, ctx.getText());
        super.visitParserRuleReference(ctx);
        return null;
    }

    @Override
    public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        ctx.accept(idFinder);
        enterItem(idFinder.rule, () -> {super.visitParserRuleSpec(ctx);});
        return null;
    }

    void enterItem(String item, Runnable run) {
        String old = currRule;
        currRule = item;
        try {
            run.run();
        } finally {
            currRule = old;
        }
    }

    public StringGraph toRuleTree() {
        return new RuleTreeImpl(ruleReferences, ruleReferencedBy);
    }

    static final IdFinder idFinder = new IdFinder();

    static final class IdFinder extends ANTLRv4BaseVisitor<Void> {
        String rule;

        @Override
        public Void visitParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext ctx) {
            rule = ctx.getText();
            return null;
        }
    }

}

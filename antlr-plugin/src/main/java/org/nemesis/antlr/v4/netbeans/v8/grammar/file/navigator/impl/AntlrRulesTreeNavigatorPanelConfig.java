/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.navigator.AntlrNavigatorPanelRegistration;
import org.nemesis.antlr.navigator.Appearance;
import org.nemesis.antlr.navigator.NavigatorPanelConfig;
import org.nemesis.antlr.navigator.SortTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.openide.awt.HtmlRenderer;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
class AntlrRulesTreeNavigatorPanelConfig implements Appearance<NamedSemanticRegion<RuleTypes>>, BiConsumer<Extraction, List<? super NamedSemanticRegion<RuleTypes>>> {

    private final Map<String, Integer> depths = new HashMap<>();
    private static final int PIXELS_PER_DEPTH_INCREMENT = 8;

    @Messages({"ruleTreeName=Rule Tree", "ruleTreeHint=Displays the dependency graph of rules such that each rule occurs once"})
    @AntlrNavigatorPanelRegistration(mimeType = "text/x-g4", order = 100, displayName = "Rule Tree")
    static NavigatorPanelConfig<RuleTypes> config() {
        AntlrRulesTreeNavigatorPanelConfig config = new AntlrRulesTreeNavigatorPanelConfig();
        return NavigatorPanelConfig.<RuleTypes>builder(config::fetch)
                .withAppearance(config)
                .setDisplayName(Bundle.ruleTreeName())
                .setHint(Bundle.ruleTreeHint())
                .build();
    }

    @Override
    public void configureAppearance(HtmlRenderer.Renderer on, NamedSemanticRegion<RuleTypes> region, boolean componentActive, Set<String> scopingDelimiter, SortTypes sort) {
        AntlrGrammarLanguageNavigatorPanelConfig.configureAppearance(on, region, componentActive, SortTypes.NATURAL);
        Integer depth = depths.getOrDefault(region.name(), 0);
        on.setIndent(PIXELS_PER_DEPTH_INCREMENT * depth);
    }

    private void fetch(Extraction extraction, List<? super NamedSemanticRegion<RuleTypes>> into) {
        NamedSemanticRegions<RuleTypes> decls = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        extraction.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES).walk((String rule, int depth) -> {
            NamedSemanticRegion<RuleTypes> decl = decls.regionFor(rule);
            depths.put(rule, depth);
            into.add(decl);
        });
    }

    @Override
    public void accept(Extraction t, List<? super NamedSemanticRegion<RuleTypes>> u) {
        fetch(t, u);
    }
}

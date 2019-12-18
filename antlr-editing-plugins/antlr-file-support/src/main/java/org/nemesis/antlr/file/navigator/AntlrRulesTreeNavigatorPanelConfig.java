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
package org.nemesis.antlr.file.navigator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.navigator.AntlrNavigatorPanelRegistration;
import org.nemesis.antlr.navigator.Appearance;
import org.nemesis.antlr.navigator.NavigatorPanelConfig;
import org.nemesis.antlr.navigator.SortTypes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.swing.html.HtmlRenderer;
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
        AntlrGrammarLanguageNavigatorPanelConfig.configureAppearance(on, region, componentActive, scopingDelimiter, SortTypes.NATURAL);
        Integer depth = depths.getOrDefault(region.name(), 0);
        on.setIndent(PIXELS_PER_DEPTH_INCREMENT * depth);
    }

    private void fetch(Extraction extraction, List<? super NamedSemanticRegion<RuleTypes>> into) {
        if (extraction == null) {
            return;
        }
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

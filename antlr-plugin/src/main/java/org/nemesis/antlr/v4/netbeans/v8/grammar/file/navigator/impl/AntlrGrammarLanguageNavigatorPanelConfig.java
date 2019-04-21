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

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.navigator.AntlrNavigatorPanelRegistration;
import org.nemesis.antlr.navigator.NavigatorPanelConfig;
import org.nemesis.antlr.navigator.SortTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.openide.awt.HtmlRenderer;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrGrammarLanguageNavigatorPanelConfig {

    @Messages({"ruleListName=Antlr Rules", "ruleListHint=Displays all rules defined in an Antlr grammar"})
    @AntlrNavigatorPanelRegistration(mimeType = "text/x-g4", order = 0, displayName = "Antlr Rules")
    static NavigatorPanelConfig<RuleTypes> config() {
        return NavigatorPanelConfig.<RuleTypes>builder(AntlrKeys.RULE_BOUNDS)
                .alsoFetchingWith(AntlrKeys.NAMED_ALTERNATIVES)
                .withAppearance(AntlrGrammarLanguageNavigatorPanelConfig::configureAppearance)
                .popupMenuPopulator(AntlrGrammarLanguageNavigatorPanelConfig::populatePopup)
                .withListModelPopulator(AntlrGrammarLanguageNavigatorPanelConfig::populateListModel)
                .withCentralityKey(AntlrKeys.RULE_NAME_REFERENCES)
                .setDisplayName(Bundle.ruleListName())
                .setHint(Bundle.ruleListHint())
                .sortable()
                .build();
    }

    static final Map<? super RuleTypes, ImageIcon> iconForType = AntlrConstants.iconForTypeMap();
    static final ImageIcon alternativeIcon = AntlrConstants.alternativeIcon();

    private static boolean isShowAlternatives() {
        return NbPreferences.forModule(AntlrGrammarLanguageNavigatorPanelConfig.class).getBoolean("show-alternatives", true);
    }

    private static void setShowAlternatives(boolean val) {
        NbPreferences.forModule(AntlrGrammarLanguageNavigatorPanelConfig.class).putBoolean("show-alternatives", val);
    }

    @Messages("showAlternatives=Show &Alternatives")
    private static void populatePopup(JPopupMenu menu) {
        boolean show = isShowAlternatives();
        JCheckBoxMenuItem item = new JCheckBoxMenuItem();
        item.setSelected(show);
        item.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                setShowAlternatives(!show);
            }
        });
        Mnemonics.setLocalizedText(item, Bundle.showAlternatives());
        menu.add(new JSeparator());
        menu.add(item);
    }

    private static int populateListModel(Extraction extraction, List<NamedSemanticRegion<RuleTypes>> rules, DefaultListModel<NamedSemanticRegion<RuleTypes>> model, NamedSemanticRegion<RuleTypes> oldSelection, SortTypes sort) {
        int newSelectedIndex = -1;

        boolean showAlternatives = isShowAlternatives();
        BitSetHeteroObjectGraph<NamedSemanticRegion<RuleTypes>, NamedSemanticRegion<RuleTypes>, ?, NamedSemanticRegions<RuleTypes>> graph;

        if (showAlternatives) {
            NamedSemanticRegions<RuleTypes> namedAlts = extraction.namedRegions(AntlrKeys.NAMED_ALTERNATIVES);
            NamedSemanticRegions<RuleTypes> ruleBounds = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);

            graph = ruleBounds.crossReference(namedAlts);
        } else {
            graph = null;
        }

        for (int i = 0; i < rules.size(); i++) {
            NamedSemanticRegion<RuleTypes> rule = rules.get(i);
            if (oldSelection != null && rule.name().equals(oldSelection.name())) {
                newSelectedIndex = model.getSize();
            }
            model.addElement(rule);

            if (showAlternatives && graph != null && graph.leftSlice().childCount(rule) > 0) {
                List<NamedSemanticRegion<RuleTypes>> alts = new ArrayList<>(graph.leftSlice().children(rule));
                switch (sort) {
                    case ALPHA:
                    case ALPHA_TYPE:
                        sort.sort(alts, extraction, AntlrKeys.RULE_NAME_REFERENCES);
                        break;
                    case NATURAL:
                        // These will always be natural sort, as originally created
                        break;
                    default:
                        // Eigenvector and pagerank are meaningless here since
                        // these are labels for rules, not rules - they do
                        // not have a ranking score
                        SortTypes.ALPHA.sort(alts, extraction, AntlrKeys.RULE_NAME_REFERENCES);
                }
                for (NamedSemanticRegion<RuleTypes> alt : alts) {
                    model.addElement(alt);
                }
            }
        }
        return newSelectedIndex;
    }

    static void configureAppearance(HtmlRenderer.Renderer renderer, NamedSemanticRegion<RuleTypes> value, boolean active, Set<String> scopingDelimiters, SortTypes sort) {
        String txt = value.name();
        RuleTypes tgt = value.kind();
        switch (tgt) {
            case FRAGMENT:
                txt = "<i>" + txt;
                renderer.setHtml(true);
                break;
            case LEXER:
                renderer.setHtml(false);
                break;
            case PARSER:
                if (value.kind() != RuleTypes.NAMED_ALTERNATIVES) {
                    renderer.setHtml(true);
                    txt = "<b>" + txt;
                }
                break;
        }
        renderer.setText(txt);
        renderer.setParentFocused(active);
        if (value.kind() == RuleTypes.NAMED_ALTERNATIVES) {
            // Subrules are indented for a tree-like display
            renderer.setIcon(alternativeIcon);
            switch(sort) {
                case ALPHA :
                case NATURAL :
                    renderer.setIndent(alternativeIcon.getIconWidth() + 8);
            }
        } else {
            Icon icon = iconForType.get(tgt);
            assert icon != null : "null icon for " + tgt + " in " + iconForType;
            renderer.setIcon(icon);
            renderer.setIndent(5);
        }

        renderer.setIconTextGap(5);
    }
}

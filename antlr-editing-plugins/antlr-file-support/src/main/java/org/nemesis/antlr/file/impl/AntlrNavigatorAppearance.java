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
package org.nemesis.antlr.file.impl;

import java.util.Set;
import javax.swing.Icon;
import static org.nemesis.antlr.common.AntlrConstants.alternativeIcon;
import static org.nemesis.antlr.common.AntlrConstants.iconForTypeMap;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.navigator.Appearance;
import org.nemesis.antlr.navigator.SortTypes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.swing.html.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrNavigatorAppearance implements Appearance<NamedSemanticRegion<RuleTypes>> {

    @Override
    public void configureAppearance(HtmlRenderer.Renderer renderer, NamedSemanticRegion<RuleTypes> value, boolean active, Set<String> scopingDelimiters, SortTypes sort) {
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
            renderer.setIcon(alternativeIcon());
            switch (sort) {
                case ALPHA:
                case NATURAL:
                    renderer.setIndent(alternativeIcon().getIconWidth() + 8);
            }
        } else {
            Icon icon = iconForTypeMap().get(tgt);
            renderer.setIcon(icon);
            renderer.setIndent(5);
        }

        renderer.setIconTextGap(5);
    }
}

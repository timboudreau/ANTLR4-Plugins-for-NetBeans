package org.nemesis.antlr.file.impl;

import java.util.Set;
import javax.swing.Icon;
import static org.nemesis.antlr.common.AntlrConstants.alternativeIcon;
import static org.nemesis.antlr.common.AntlrConstants.iconForTypeMap;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.navigator.Appearance;
import org.nemesis.antlr.navigator.SortTypes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.openide.awt.HtmlRenderer;

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

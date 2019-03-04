package org.nemesis.antlr.navigator;

import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.openide.awt.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
final class NoAppearance implements Appearance<Object> {

    @Override
    public void configureAppearance(HtmlRenderer.Renderer on, Object region, boolean componentActive, SortTypes sort) {
        defaultConfigure(on, region, componentActive);
    }

    static void defaultConfigure(HtmlRenderer.Renderer on, Object region, boolean componentActive) {
        if (region instanceof NamedSemanticRegion<?>) {
            on.setHtml(true);
            on.setText(((NamedSemanticRegion<?>) region).name());
        } else if (region instanceof SemanticRegion<?>) {
            SemanticRegion<?> semRegion = (SemanticRegion<?>) region;
            Object key = semRegion.key();
            if (key == null) {
                on.setHtml(false);
                on.setText("<no-key>");
            } else {
                on.setHtml(true);
                on.setText(semRegion.key().toString());
            }
            int indent = semRegion.nestingDepth() * 8;
            on.setIndent(indent);
        } else {
            on.setText(region == null ? "<no-region>" : region.toString());
        }
        on.setParentFocused(componentActive);

    }

}

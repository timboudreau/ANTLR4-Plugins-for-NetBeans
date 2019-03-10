package org.nemesis.antlr.navigator;

import java.util.Set;
import javax.swing.ImageIcon;
import org.openide.awt.HtmlRenderer;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Tim Boudreau
 */
final class IconAppearance<T> implements Appearance<T> {

    private final DefaultAppearance no = new DefaultAppearance();
    private final ImageIcon icon;

    IconAppearance(String iconBase) {
        icon = ImageUtilities.loadImageIcon(iconBase, false);
    }

    @Override
    public void configureAppearance(HtmlRenderer.Renderer on, T region, boolean componentActive, Set<String> scopingDelimiters, SortTypes sort) {
        no.configureAppearance(on, region, componentActive, scopingDelimiters, sort);
        on.setIcon(icon);
    }

}

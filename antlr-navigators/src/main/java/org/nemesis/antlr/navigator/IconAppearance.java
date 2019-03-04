package org.nemesis.antlr.navigator;

import javax.swing.ImageIcon;
import org.openide.awt.HtmlRenderer;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Tim Boudreau
 */
final class IconAppearance<T> implements Appearance<T> {

    private final NoAppearance no = new NoAppearance();
    private final ImageIcon icon;

    IconAppearance(String iconBase) {
        icon = ImageUtilities.loadImageIcon(iconBase, false);
    }

    @Override
    public void configureAppearance(HtmlRenderer.Renderer on, T region, boolean componentActive, SortTypes sort) {
        no.configureAppearance(on, region, componentActive, sort);
        on.setIcon(icon);
    }

}

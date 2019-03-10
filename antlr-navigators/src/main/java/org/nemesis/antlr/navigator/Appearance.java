package org.nemesis.antlr.navigator;

import java.util.Set;
import org.openide.awt.HtmlRenderer;

/**
 * Sets up the passed HTML renderer with icon, indentation, formatted text when
 * rendering.
 *
 * @param <K>
 */
public interface Appearance<K> {

    void configureAppearance(HtmlRenderer.Renderer on, K region, boolean componentActive, Set<String> scopingDelimiters, SortTypes sort);

    default Appearance<K> and(Appearance<? super K> appearance) {
        return (HtmlRenderer.Renderer on, K region, boolean componentActive, Set<String> scopingDelimiter, SortTypes sort) -> {
            Appearance.this.configureAppearance(on, region, componentActive, scopingDelimiter, sort);
            appearance.configureAppearance(on, region, componentActive, scopingDelimiter, sort);
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> Appearance<T> defaultAppearance() {
        DefaultAppearance no = new DefaultAppearance();
        return (Appearance<T>) no;
    }
}

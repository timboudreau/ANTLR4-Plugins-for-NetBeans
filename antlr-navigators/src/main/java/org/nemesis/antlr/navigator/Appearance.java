/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.navigator;

import org.openide.awt.HtmlRenderer;

/**
 * Sets up the passed HTML renderer with icon, indentation, formatted text
 * when rendering.
 *
 * @param <K>
 */
public interface Appearance<K> {

    void configureAppearance(HtmlRenderer.Renderer on, K region, boolean componentActive, SortTypes sort);

    default Appearance<K> and(Appearance<K> appearance) {
        return (HtmlRenderer.Renderer on, K region, boolean componentActive, SortTypes sort) -> {
            Appearance.this.configureAppearance(on, region, componentActive, sort);
            appearance.configureAppearance(on, region, componentActive, sort);
        };
    }

}

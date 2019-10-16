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

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

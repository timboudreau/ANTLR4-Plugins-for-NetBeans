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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import org.openide.util.NbBundle;

/**
 * Bitmasks in a lightweight coloring attribute as a convenient Java enum.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {"ACTIVE=Active", "BACKGROUND=Background", "FOREGROUND=Foreground", "BOLD=Bold", "ITALIC=Italic"})
public enum AttrTypes {
    ACTIVE(AdhocColoring.MASK_ACTIVE), 
    BACKGROUND(AdhocColoring.MASK_BACKGROUND),
    FOREGROUND(AdhocColoring.MASK_FOREGROUND),
    BOLD(AdhocColoring.MASK_BOLD),
    ITALIC(AdhocColoring.MASK_ITALIC);
    private final int maskValue;

    private AttrTypes(int maskValue) {
        this.maskValue = maskValue;
    }

    public String toString() {
        return NbBundle.getMessage(AttrTypes.class, name());
    }

    int maskValue() {
        return maskValue;
    }

}

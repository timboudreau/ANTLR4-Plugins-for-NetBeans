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
package org.nemesis.antlr.language.formatting.config;

import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public enum ColonHandling {
    @NbBundle.Messages(value = "INLINE=&Inline")
    INLINE,
    @NbBundle.Messages(value = "NEWLINE_BEFORE=&New Line Before")
    NEWLINE_BEFORE,
    @NbBundle.Messages(value = "STANDALONE=On &Separate Line")
    STANDALONE,
    @NbBundle.Messages(value = "NEWLINE_AFTER=Newline &After")
    NEWLINE_AFTER
    ;

    public String toString() {
        return displayName();
    }

    public String displayName() {
        return NbBundle.getMessage(ColonHandling.class, name());
    }
}

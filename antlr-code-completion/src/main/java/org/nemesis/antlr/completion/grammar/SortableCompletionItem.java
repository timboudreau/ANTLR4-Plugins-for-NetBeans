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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.util.strings.Strings;
import org.netbeans.spi.editor.completion.CompletionItem;

/**
 * Local interface to unify several completion item implementations, old and new.
 *
 * @author Tim Boudreau
 */
interface SortableCompletionItem extends CompletionItem, Comparable<SortableCompletionItem> {

    float relativeScore();

    void score(float normalizedScore);

    boolean isInstant();

    void setInstant();

    boolean matchesPrefix(String text);

    String insertionText();

    @Override
    public default int compareTo(SortableCompletionItem o) {
        int asp = getSortPriority();
        int bsp = o.getSortPriority();
        int result = Integer.compare(asp, bsp);
        if (result == 0) {
            result = Strings.compareCharSequences(getSortText(), o.getSortText(), true);
        }
        return result;
    }
}

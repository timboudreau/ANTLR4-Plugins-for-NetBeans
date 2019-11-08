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

import java.util.List;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NamedRegionKey;
import org.openide.cookies.EditorCookie;

final class EditorAndChangeAwareListModel<T> extends ListListModel<T> {

    final EditorCookie cookie;
    final int change;
    final Extraction semantics;

    public EditorAndChangeAwareListModel(List<T> list, EditorCookie cookie, int change, Extraction semantics) {
        super(list);
        this.cookie = cookie;
        this.change = change;
        this.semantics = semantics;
    }

    <T extends Enum<T>> NamedSemanticRegion<T> nameRegionFor(NamedRegionKey<T> key, NamedSemanticRegion<T> orig) {
        Extraction sem = semantics;
        if (sem == null || orig == null) {
            return orig;
        }
        NamedRegionKey<T> namesKey = sem.nameKeyFor(key);
        if (namesKey == key) {
            return orig;
        }
        NamedSemanticRegions<T> nameRegions = sem.namedRegions(namesKey);
        NamedSemanticRegion<T> result = nameRegions.regionFor(orig.name());
        return result == null ? orig : result;
    }
}

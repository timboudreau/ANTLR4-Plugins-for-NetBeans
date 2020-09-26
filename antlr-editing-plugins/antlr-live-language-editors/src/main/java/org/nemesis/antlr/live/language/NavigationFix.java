/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.language;

import java.util.Objects;
import javax.swing.text.Document;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
class NavigationFix implements Fix, Comparable<NavigationFix> {

    private static int fixIndex = Integer.MIN_VALUE;
    private final String name;
    private final Runnable impl;
    private final PositionRange range;
    private final int id = fixIndex++;

    public NavigationFix(String name, PositionRange range, Runnable impl) {
        this.name = name;
        this.impl = impl;
        this.range = range;
    }

    @Override
    public String getText() {
        return name;
    }

    public String toString() {
        return name;
    }

    @Override
    public ChangeInfo implement() throws Exception {
        impl.run();
        Document doc = range.document();
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo != null) {
            return new ChangeInfo(fo, range.startPosition(), range.endPosition());
        }
        return null;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.range);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final NavigationFix other = (NavigationFix) obj;
        return this.range.equals(other.range) && this.name.equals(other.name);
    }

    @Override
    public int compareTo(NavigationFix o) {
        return Integer.compare(id, o.id);
    }

}

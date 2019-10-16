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
package org.nemesis.antlr.fold;

import java.lang.ref.WeakReference;
import java.util.Objects;
import javax.swing.text.Document;

/**
 * A WeakReference to a document which can do identity comparisons based on the
 * document's identity hash code.
 *
 * @author Tim Boudreau
 */
final class DocumentReference extends WeakReference<Document> {

    int idHash;

    public DocumentReference(Document referent) {
        super(referent);
        idHash = System.identityHashCode(referent);
    }

    boolean isAlive() {
        return super.get() != null;
    }

    @Override
    public int hashCode() {
        return idHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof DocumentReference) {
            DocumentReference d = (DocumentReference) obj;
            return idHash == d.idHash && Objects.equals(get(), d.get());
        }
        return false;
    }

}

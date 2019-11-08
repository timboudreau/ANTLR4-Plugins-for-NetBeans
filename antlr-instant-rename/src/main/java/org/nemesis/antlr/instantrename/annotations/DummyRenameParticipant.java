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
package org.nemesis.antlr.instantrename.annotations;

import org.nemesis.antlr.instantrename.RenameParticipant;
import org.nemesis.antlr.instantrename.spi.RenameQueryResult;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.ExtractionKey;

/**
 * This class only exists to provide a default for an annotation parameter
 * on InplaceRename, and must not implement its generic signature.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("rawType")
final class DummyRenameParticipant extends RenameParticipant {

    private DummyRenameParticipant() {
        throw new AssertionError();
    }

    @Override
    protected RenameQueryResult isRenameAllowed(Extraction ext, ExtractionKey key, IndexAddressable.IndexAddressableItem item, IndexAddressable collection, int caretOffset, String identifier) {
        throw new AssertionError();
    }

}

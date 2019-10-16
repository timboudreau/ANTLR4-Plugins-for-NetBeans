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
package org.nemesis.extraction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 * Attributes unknown name references found a source file by (probably) looking
 * up other related source files, parsing them and finding the unknown
 * references in them, and returning an extraction for them.
 *
 * @author Tim Boudreau
 */
public interface UnknownNameReferenceResolver<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>> {

    <X> X resolve(Extraction extraction, UnknownNameReference<T> ref, ResolutionConsumer<R, I, N, T, X> c) throws IOException;

    default <X> Map<UnknownNameReference<T>, X> resolveAll(Extraction extraction,
            SemanticRegions<UnknownNameReference<T>> refs,
            ResolutionConsumer<R, I, N, T, X> c) throws IOException {
        Map<UnknownNameReference<T>, X> result = new HashMap<>();
        for (SemanticRegion<UnknownNameReference<T>> unk : refs) {
            X item = resolve(extraction, unk.key(), c);
            if (item != null) {
                result.put(unk.key(), item);
            }
        }
        return result;
    }

    Class<T> type();
}

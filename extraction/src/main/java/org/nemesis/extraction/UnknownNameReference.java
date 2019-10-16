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
import java.io.Serializable;
import org.nemesis.data.IndexAddressable;
import com.mastfrog.abstractions.Named;
import org.nemesis.data.named.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
public interface UnknownNameReference<T extends Enum<T>> extends Named, IndexAddressable.IndexAddressableItem, Serializable {

    T expectedKind();

    default Class<T> kindType() {
        return expectedKind().getDeclaringClass();
    }

    default <R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>> AttributedForeignNameReference<R, I, N, T>
            resolve(Extraction extraction, UnknownNameReferenceResolver<R, I, N, T> resolver) throws IOException {
        ResolutionConsumer<R, I, N, T, AttributedForeignNameReference<R, I, N, T>> cons
                = (UnknownNameReference<T> unknown, R resolutionSource, I in, N element, Extraction target)
                -> new AttributedForeignNameReference<>(unknown, resolutionSource, in, element, extraction, target);
        return resolver.resolve(extraction, this, cons);
    }
}

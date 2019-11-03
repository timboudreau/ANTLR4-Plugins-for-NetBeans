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

import org.nemesis.data.IndexAddressable;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.key.NamedExtractionKey;

/**
 *
 * @author Tim Boudreau
 */
public interface ResolutionConsumer<R, I extends IndexAddressable.NamedIndexAddressable<N>, N extends NamedSemanticRegion<T>, T extends Enum<T>, X> {

    X resolved(NamedExtractionKey<T> key, UnknownNameReference<T> unknown, R resolutionSource, I in, N element, Extraction target);

}

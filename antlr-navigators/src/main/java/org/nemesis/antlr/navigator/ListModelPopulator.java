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

import java.util.Collection;
import java.util.List;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;

/**
 * Updates a list model and sorts it.
 *
 * @param <K> The enum type
 */
public interface ListModelPopulator<K, I extends IndexAddressable.IndexAddressableItem> {

    /**
     * Populate the list model with whatever objects this panel should find in
     * the extraction.
     *
     * @param extraction The extraction
     * @param model A new, empty model
     * @param oldSelection The selection in the panel at this time
     * @param requestedSort The sort order that should be used
     * @return The index of the old selection (if not null) in the new set of
     * model elements, or -1 if not found
     */
    int populateListModel(Extraction extraction, List<? extends I> fetched, Collection<? super I> model, I oldSelection, SortTypes sort);
}

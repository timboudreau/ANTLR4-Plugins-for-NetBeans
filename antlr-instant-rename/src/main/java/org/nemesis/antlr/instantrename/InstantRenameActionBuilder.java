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
package org.nemesis.antlr.instantrename;

import com.mastfrog.range.IntRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import static org.nemesis.antlr.instantrename.InstantRenameAction.LOG;
import org.nemesis.antlr.instantrename.impl.RenameActionType;
import static org.nemesis.antlr.instantrename.impl.RenameActionType.INPLACE;
import static org.nemesis.antlr.instantrename.impl.RenameActionType.INPLACE_AUGMENTED;
import static org.nemesis.antlr.instantrename.impl.RenameActionType.POST_PROCESS;
import static org.nemesis.antlr.instantrename.impl.RenameActionType.USE_REFACTORING_API;
import org.nemesis.antlr.instantrename.impl.RenameQueryResultTrampoline;
import org.nemesis.antlr.instantrename.spi.RenameQueryResult;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.editor.BaseDocument;

/**
 *
 * @author Tim Boudreau
 */
public final class InstantRenameActionBuilder {

    private final List<InstantRenameProcessorEntry<?, ?, ?, ?>> entries = new ArrayList<>();

    private <T, X extends ExtractionKey<T>> InstantRenameActionBuilder add(
            Entry<T, X, ?, ?> entry) {
        entries.add(entry);
        return this;
    }

    public InstantRenameAction build() {
        return new InstantRenameAction(entries);
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NamedRegionKey<T> key,
            RenameParticipant<T, NamedRegionKey<T>, NamedSemanticRegion<T>, NamedSemanticRegions<T>> participant,
            CharFilter filter) {
        return add(new NamedRegionsEntry<>(key, participant.wrap(filter)));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NamedRegionKey<T> key,
            RenameParticipant<T, NamedRegionKey<T>, NamedSemanticRegion<T>, NamedSemanticRegions<T>> participant) {
        return add(new NamedRegionsEntry<>(key, participant));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NamedRegionKey<T> key,
            CharFilter filter) {
        return add(new NamedRegionsEntry<>(key, RenameParticipant.filterOnly(filter)));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NamedRegionKey<T> key) {
        return add(new NamedRegionsEntry<>(key, RenameParticipant.defaultInstance()));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NameReferenceSetKey<T> key,
            RenameParticipant<T, NameReferenceSetKey<T>, NamedSemanticRegion<T>, NamedRegionReferenceSets<T>> participant) {
        return add(new ReferencesEntry<>(key, participant));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NameReferenceSetKey<T> key,
            RenameParticipant<T, NameReferenceSetKey<T>, NamedSemanticRegion<T>, NamedRegionReferenceSets<T>> participant,
            CharFilter filter) {
        return add(new ReferencesEntry<>(key, participant.wrap(filter)));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NameReferenceSetKey<T> key,
            CharFilter filter) {
        return add(new ReferencesEntry<>(key, RenameParticipant.filterOnly(filter)));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            NameReferenceSetKey<T> key) {
        return add(new ReferencesEntry<>(key, RenameParticipant.defaultInstance()));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            SingletonKey<T> key) {
        return add(new SingletonEntry<>(key, RenameParticipant.defaultInstance()));
    }

    public <T> InstantRenameActionBuilder add(
            SingletonKey<T> key,
            RenameParticipant<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> participant,
            CharFilter filter) {
        return add(new SingletonEntry<>(key, participant.wrap(filter)));
    }

    public <T> InstantRenameActionBuilder add(
            SingletonKey<T> key,
            RenameParticipant<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> participant) {
        return add(new SingletonEntry<>(key, participant));
    }

    public <T extends Enum<T>> InstantRenameActionBuilder add(
            SingletonKey<T> key,
            CharFilter filter) {
        return add(new SingletonEntry<>(key, RenameParticipant.filterOnly(filter)));
    }

    static abstract class Entry<T, X extends ExtractionKey<T>, I extends IndexAddressable.IndexAddressableItem, C extends IndexAddressable<? extends I>>
            implements InstantRenameProcessorEntry<T, X, I, C> {

        final X key;
        final RenameParticipant<T, X, I, C> participant;

        Entry(X key, RenameParticipant<T, X, I, C> participant) {
            this.key = key;
            this.participant = participant;
        }

        @Override
        public X key() {
            return key;
        }

        @Override
        public RenameParticipant<T, X, I, C> participant() {
            return participant;
        }

        public abstract FindItemsResult<T, X, I, C> find(Extraction extraction, BaseDocument document, int caret, String identifier);
    }

    static final class SingletonEntry<T> extends Entry<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> {

        public SingletonEntry(SingletonKey<T> key, RenameParticipant<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> participant) {
            super(key, participant);
        }

        @Override
        public FindItemsResult<T, SingletonKey<T>, SingletonEncounter<T>, SingletonEncounters<T>> find(Extraction extraction, BaseDocument document, int caret, String identifier) {
            SingletonEncounters<T> singletons = extraction.singletons(key);
            SingletonEncounters.SingletonEncounter<T> enc = singletons.at(caret);
            if (enc == null || enc.isEmpty()) {
                return new FindItemsResult<>(null, RenameQueryResultTrampoline.createNothingFoundResult());
            }
            RenameQueryResult res = participant.isRenameAllowed(extraction, key, enc, singletons, caret, identifier);
            RenameActionType type = RenameQueryResultTrampoline.typeOf(res);
            switch (type) {
                case INPLACE:
                case INPLACE_AUGMENTED:
                case POST_PROCESS:
                case USE_REFACTORING_API:
                    return new FindItemsResult<>(Collections.singleton(enc), res);
                default:
                    return new FindItemsResult<>(null, res);
            }
        }
    }

    static final class NamedRegionsEntry<T extends Enum<T>> extends Entry<T, NamedRegionKey<T>, NamedSemanticRegion<T>, NamedSemanticRegions<T>> {

        NamedRegionsEntry(NamedRegionKey<T> key, RenameParticipant participant) {
            super(key, participant);
        }

        @Override
        public FindItemsResult<T, NamedRegionKey<T>, NamedSemanticRegion<T>, NamedSemanticRegions<T>> find(Extraction extraction, BaseDocument document, int caret, String identifier) {
            NamedSemanticRegions<T> regions = extraction.namedRegions(key);
            if (regions == null || regions.isEmpty()) {
                return new FindItemsResult<>(null, RenameQueryResultTrampoline.createNothingFoundResult());
            }
            NamedSemanticRegion<T> region = regions.at(caret);
            if (region == null) {
                return new FindItemsResult<>(null, RenameQueryResultTrampoline.createNothingFoundResult());
            }
            RenameQueryResult res = participant.isRenameAllowed(extraction, key, region, regions, caret, identifier);
            RenameActionType type = RenameQueryResultTrampoline.typeOf(res);
            switch (type) {
                case INPLACE:
                case INPLACE_AUGMENTED:
                case POST_PROCESS:
                case USE_REFACTORING_API:
                    return new FindItemsResult<>(Collections.singleton(region), res);
                default:
                    return new FindItemsResult<>(null, res);
            }
        }

    }

    static final class ReferencesEntry<T extends Enum<T>> extends Entry<T, NameReferenceSetKey<T>, NamedSemanticRegion<T>, NamedRegionReferenceSets<T>> {

        ReferencesEntry(NameReferenceSetKey<T> key, RenameParticipant participant) {
            super(key, participant);
        }

        public FindItemsResult<T, NameReferenceSetKey<T>, NamedSemanticRegion<T>, NamedRegionReferenceSets<T>> find(Extraction extraction, BaseDocument document, int caret, String identifier) {
            NamedRegionReferenceSets<T> refs = extraction.references(key);
            NamedSemanticRegions<T> names = extraction.namedRegions(key.referencing());
            NamedSemanticRegion<T> caretNamedRegion = refs.at(caret);
            NamedSemanticRegion<T> referent = null;
            if (caretNamedRegion == null) {
                caretNamedRegion = referent = names.at(caret);
            }
            if (caretNamedRegion == null) {
                return new FindItemsResult<>(null, RenameQueryResultTrampoline.createNothingFoundResult());
            }
            String ident = caretNamedRegion.name();
            if (referent == null) {
                referent = names.regionFor(ident);
            }
            if (referent == null) {
                return new FindItemsResult<>(null, RenameQueryResultTrampoline.createNothingFoundResult());
            }
            RenameQueryResult res = participant.isRenameAllowed(extraction, key, referent, refs, caret, ident);
            RenameActionType type = RenameQueryResultTrampoline.typeOf(res);
            switch (type) {
                case INPLACE:
                case INPLACE_AUGMENTED:
                case POST_PROCESS:
                case USE_REFACTORING_API:
                    NamedRegionReferenceSet<?> referencesToName = refs.references(ident);
                    LOG.log(Level.FINE, "Inplace rename {0} reparse {1} using {2} in"
                            + " region {3}",
                            new Object[]{
                                ident,
                                extraction.source(),
                                key,
                                caretNamedRegion
                            });
                    Iterable<? extends IntRange> regions = referencesToName;
                    Set<IntRange> all = new HashSet<>();
                    referencesToName.forEach(all::add);
                    all.add(referent);
                    return new FindItemsResult<>(all, res);
                default:
                    return new FindItemsResult<>(null, res);
            }
        }
    }
}

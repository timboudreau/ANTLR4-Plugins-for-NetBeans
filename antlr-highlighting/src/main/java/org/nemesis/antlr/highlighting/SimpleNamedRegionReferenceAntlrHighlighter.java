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
package org.nemesis.antlr.highlighting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import static org.nemesis.antlr.highlighting.SimpleSemanticRegionAntlrHighlighter.coloringForMimeType;
import static org.nemesis.antlr.highlighting.SimpleSemanticRegionAntlrHighlighter.coloringLookup;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleNamedRegionReferenceAntlrHighlighter<T extends Enum<T>> implements AntlrHighlighter {

    private final NameReferenceSetKey<T> key;
    private final Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup;
    private final int cacheSize;
    private boolean highlightReferencesUnderCaret;
    private static final Logger LOG = Logger.getLogger(SimpleNamedRegionAntlrHighlighter.class.getName());

    private static void log(String msg, Object... args) {
        LOG.log(Level.FINER, msg, args);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    SimpleNamedRegionReferenceAntlrHighlighter(NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, AttributeSet> coloringLookup) {
        this.key = key;
        this.coloringLookup = coloringLookup;
        cacheSize = key.type().getEnumConstants().length;
        log("Create {0}", this);
    }

    void highlightReferencesUnderCaret() {
        highlightReferencesUnderCaret = true;
    }

    @Override
    public String toString() {
        return "SimpleNamedRegionReferenceAntlrHighlighter{" + key + ", coloring=" + coloringLookup + '}';
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> fixed(NameReferenceSetKey<T> key, Supplier<AttributeSet> lookup) {
        return new SimpleNamedRegionReferenceAntlrHighlighter<>(key, t -> {
            return lookup.get();
        });
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> fixed(String mimeType, NameReferenceSetKey<T> key, String coloring) {
        return fixed(key, coloringLookup(mimeType, coloring));
    }

    static <T extends Enum<T>> SimpleNamedRegionReferenceAntlrHighlighter<T> create(String mimeType, NameReferenceSetKey<T> key, Function<NamedSemanticRegionReference<T>, String> coloringNameProvider) {
        Function<String, AttributeSet> coloringFinder = coloringForMimeType(mimeType);
        Function<NamedSemanticRegionReference<T>, AttributeSet> xformed = coloringNameProvider.andThen(coloringFinder);
        return new SimpleNamedRegionReferenceAntlrHighlighter<>(key, xformed);
    }

    @Override
    public void refresh(Document doc, Extraction ext, OffsetsBag bag, Integer caret) {
        NamedRegionReferenceSets<T> regions = ext.references(key);
        if (regions == null) {
            LOG.log(Level.FINE, "Null regions for {0}.  Parse cancelled?", key);
            return;
        }
        log("refresh {0} NamedRegionReferenceSets for {1} track originals {2} caret {3}", regions.size(), doc, highlightReferencesUnderCaret, caret);
        if (!regions.isEmpty()) {
            Map<T, AttributeSet> cache = new HashMap<>(cacheSize);
            if (!highlightReferencesUnderCaret) {
                for (NamedSemanticRegionReference<T> region : regions.asIterable()) {
                    T kind = region.kind();
                    AttributeSet coloring = cache.get(kind);
                    if (coloring == null) {
                        coloring = coloringLookup.apply(region);
                        if (coloring != null) {
                            cache.put(kind, coloring);
                        } else {
                            log("no color for {0}", kind);
                        }
                    }
                    if (coloring != null) {
                        bag.addHighlight(region.start(), region.end(), coloring);
                    }
                }
            } else {
                final boolean log = LOG.isLoggable(Level.FINEST);
                // Here we want to find the item under the caret, and highlight
                // all references and any original which does not contain the
                // caret - this is mark occurrences
                if (caret != null) {
                    // The caret may be in the an element of the region
                    // references we were passed
                    NamedSemanticRegion<T> target = regions.at(caret);
                    if (target == null) {
                        // Or it may be in an element of the NamedSemanticRegions
                        // these are references to
                        NamedSemanticRegions<T> originals = regions.originals();
                        if (originals != null) {
                            target = originals.at(caret);
                            if (log && target != null) {
                                LOG.log(Level.FINEST, "Using original {0}", target);
                            }
                        }
                    }
                    // If the target is null, then the caret is not in anything
                    // relevant to us, so exit
                    if (target != null && target.name() != null) {
                        if (log) {
                            LOG.log(Level.FINEST, "Highlight references to {0} ref {1}",
                                    new Object[]{target, target.isReference()});
                        }
                        AttributeSet coloring = null;
                        // Get the set of references just to the name of the target
                        NamedRegionReferenceSet<T> refs = regions.references(target.name());
                        if (refs == null) {
                            return;
                        }
                        // First, find a coloring - we need a reference object
                        // to do the lookup with - we iterate here, but the first
                        // iteration should catch it and break, unless it is
                        // undefined in the coloring scheme
                        for (NamedSemanticRegionReference<T> ref : refs) {
                            if (coloring == null) {
                                coloring = coloringLookup.apply(ref);
                                if (coloring != null) {
                                    break;
                                }
                            }
                        }
                        boolean originalDone = false;
                        if (coloring != null) {
                            // If we didn't find a coloring, then either the
                            // target was an original and it has no references,
                            // or there is no coloring defined for this region
                            // kind
                            for (NamedSemanticRegionReference<T> ref : refs) {
                                // Don't highlight the element the caret is in,
                                // for consistency with other NetBeans
                                // highlighters
                                if (ref.contains(caret)) {
                                    if (log) {
                                        LOG.log(Level.FINEST, "Skip {0}:{1} ref for {2}", new Object[]{target.start(), target.end(), target.name()});
                                    }
                                } else {
                                    // Add the highlight for this reference
                                    bag.addHighlight(ref.start(), ref.end(), coloring);
                                    if (log) {
                                        LOG.log(Level.FINEST, "Add highlight for {0}:{1} for {2}",
                                                new Object[]{ref.start(), ref.end(), ref.name()});
                                    }
                                }
                                // Highlight the original item if necessary - all
                                // references will have the same original, so only
                                // do this once
                                if (!originalDone) {
                                    NamedSemanticRegion<T> orig = ref.referencing();
                                    if (!orig.contains(caret)) {
                                        bag.addHighlight(orig.start(), orig.end(), coloring);
                                        if (log) {
                                            LOG.log(Level.FINEST, "Highlight orig {0}:{1} for {2}",
                                                    new Object[]{orig.start(), orig.end(), orig.name()});
                                        }
                                    }
                                    originalDone = true;
                                }
                            }
                        }
                    } else if (log) {
                        LOG.log(Level.FINEST, "No references to highlight for {0}", key);
                    }
                }
            }
        }
    }
}

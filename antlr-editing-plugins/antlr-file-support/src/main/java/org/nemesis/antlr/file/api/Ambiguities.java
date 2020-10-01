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
package org.nemesis.antlr.file.api;

import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapFactories;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.nemesis.antlr.common.TimedWeakReference;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * Communication interface which allows the Antlr preview to communicate with
 * Antlr highlighters about ambiguities in the grammar, which can only be found
 * via parsing a file using that grammar.
 *
 * @author Tim Boudreau
 */
public final class Ambiguities {

    private static final Map<Path, Ambiguities> ambiguitiesForGrammar = MapFactories.WEAK_VALUE.createMap(16, true);
    private final Set<Runnable> notify = com.mastfrog.util.collections.SetFactories.WEAK_HASH.newSet(3, true);
    private final Path file;
    private final AtomicReference<Collection<? extends AmbiguityRecord>> entriesRef
            = new AtomicReference<>(Collections.emptySet());
    private static Reference<Ambiguities> PENDING;
    private static final AtomicLinkedQueue<Ambiguities> PENDING_NOTIFICATIONS
            = new AtomicLinkedQueue<>();

    private static final int DELAY = 2000;
    private static final RequestProcessor.Task FLUSH_EVENTS
            = RequestProcessor.getDefault().create(() -> {
                List<Ambiguities> all = new LinkedList<>();
                Set<Path> paths = new HashSet<>();
                while (!PENDING_NOTIFICATIONS.isEmpty()) {
                    PENDING_NOTIFICATIONS.drainTo(all);
                    for (Ambiguities ambig : all) {
                        if (!paths.contains(ambig.path())) {
                            ambig.fire();
                            paths.add(ambig.path());
                        }
                    }
                    all.clear();
                }
            });

    private Ambiguities(Path file) {
        this.file = file;
    }

    /**
     * Get a callback when the set of ambiguities is updated; note, it is best
     * not to update the UI synchronously here, but instead to note something
     * and enqueue an update.
     *
     * @param r A runnable to call, which must remain strongly referenced to be
     * called
     * @return A set of ambiguities
     */
    public Ambiguities listen(Runnable r) {
        notify.add(r);
        return this;
    }

    public Path path() {
        return file;
    }

    /**
     * Get a the holder for ambiguity reports about a grammar; may be empty
     * initially, until something is parsed
     *
     * @param grammarPath A grammar file path
     * @return An ambiguity set
     */
    public static Ambiguities forGrammar(Path grammarPath) {
        return ambiguitiesForGrammar.computeIfAbsent(grammarPath, Ambiguities::new);
    }

    /**
     * Get the current set of ambiguities.
     *
     * @return
     */
    public Collection<? extends AmbiguityRecord> get() {
        return entriesRef.get();
    }

    private void fire() {
        for (Runnable r : notify) {
            try {
                r.run();
            } catch (Exception | Error ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private void setEntries(Collection<? extends AmbiguityEntryImpl> entries) {
        Collection<? extends AmbiguityRecord> old = entriesRef.get();
        if (!entries.equals(old)) {
            entriesRef.set(entries);
            PENDING_NOTIFICATIONS.add(this);
            FLUSH_EVENTS.schedule(DELAY);
        }
    }

    /**
     * Report some ambiguities encountered when parsing; if nothing is
     * listening, the passed consumer will not be called back.
     *
     * @param grammarPath A grammar file
     * @param c A consumer which will be called back if something has registered
     * interest in ambiguities
     */
    public static void reportAmbiguities(Path grammarPath, Consumer<AmbiguityConsumer> c) {
//        Ambiguities ambigs = ambiguitiesForGrammar.get(grammarFile);
//        Ambiguities ambigs = forGrammar(grammarFile);
        Ambiguities ambigs = ambiguitiesForGrammar.computeIfAbsent(grammarPath, p -> {
            Ambiguities result = new Ambiguities(p);
            // In the case that an editor is opening but no highlighter has
            // requested an Ambiguities yet, we want to hold a temporary strong
            // reference to the return value;  otherwise they will not talk
            // to the same instance
            PENDING = TimedWeakReference.create(result, 1, TimeUnit.MINUTES);
            return result;
        });
        if (ambigs != null) {
            Update update = new Update();
            c.accept(update);
            if (update.entries != null) {
                ambigs.setEntries(CollectionUtils.immutableSet(update.entries.values()));
            } else {
                ambigs.setEntries(Collections.emptySet());
            }
        }
    }

    private static class Update implements AmbiguityConsumer {

        Map<RuleAltKey, AmbiguityEntryImpl> entries;

        @Override
        public void reportAmbiguity(String inRule, BitSet alternatives, CharSequence inText, int start, int end) {
            if (entries == null) {
                entries = new HashMap<>();
            }
            CharSequence txt;
            if (end > start) {
                txt = inText.subSequence(start, end);
            } else {
                txt = "";
            }
            RuleAltKey rak = new RuleAltKey(inRule, alternatives);
            AmbiguityEntryImpl impl = entries.get(rak);
            if (impl == null) {
                entries.put(rak, new AmbiguityEntryImpl(inRule, txt, alternatives));
            } else {
                impl.add(txt);
            }
        }

        static final class RuleAltKey {

            private final String rule;
            private final BitSet alts;
            private final int hc;

            public RuleAltKey(String rule, BitSet alts) {
                this.rule = rule;
                this.alts = alts;
                hc = (80599 * rule.hashCode()) ^ (5813 * alts.hashCode());
            }

            public boolean equals(Object o) {
                return o == null ? false : o == this ? true
                        : o.getClass() == RuleAltKey.class ? ((RuleAltKey) o).alts.equals(alts)
                        && ((RuleAltKey) o).rule.equals(rule) : false;
            }

            public int hashCode() {
                return hc;
            }
        }

    }

    private static class AmbiguityEntryImpl implements AmbiguityRecord {

        final String inRule;
        final Set<CharSequence> texts = new TreeSet<>(Strings.charSequenceComparator());
        final BitSet alternatives;

        public AmbiguityEntryImpl(String inRule, CharSequence text, BitSet alternatives) {
            this.inRule = inRule;
            texts.add(text);
            this.alternatives = alternatives;
        }

        void add(CharSequence seq) {
            texts.add(seq);
        }

        @Override
        public String toString() {
            return inRule + ":" + alternatives + ":"
                    + Escaper.CONTROL_CHARACTERS.escape(texts.iterator().next());
        }

        @Override
        public String rule() {
            return inRule;
        }

        @Override
        public BitSet alternatives() {
            return alternatives;
        }

        @Override
        public Set<CharSequence> causeText() {
            return texts;
        }

        @Override
        public int hashCode() {
            return (80599 * inRule.hashCode()) ^ (5813 * alternatives.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final AmbiguityEntryImpl other = (AmbiguityEntryImpl) obj;
            return inRule.equals(other.inRule) && alternatives.equals(other.alternatives);
        }
    }
}

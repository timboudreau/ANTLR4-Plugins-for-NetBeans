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

import com.mastfrog.util.collections.MapFactories;
import com.mastfrog.util.strings.Escaper;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private final AtomicReference<Set<? extends AmbiguityRecord>> entriesRef = new AtomicReference<>(Collections.emptySet());

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
    public Set<? extends AmbiguityRecord> get() {
        return entriesRef.get();
    }

    private void setEntries(Set<AmbiguityEntryImpl> entries) {
        Set<? extends AmbiguityRecord> old = entriesRef.get();
        if (!entries.equals(old)) {
            entriesRef.set(entries);
            for (Runnable r : notify) {
                r.run();
            }
        }
    }

    /**
     * Report some ambiguities encountered when parsing; if nothing is
     * listening, the passed consumer will not be called back.
     *
     * @param grammarFile A grammar file
     * @param c A consumer which will be called back if something has registered
     * interest in ambiguities
     */
    public static void reportAmbiguities(Path grammarFile, Consumer<AmbiguityConsumer> c) {
        Ambiguities ambigs = ambiguitiesForGrammar.get(grammarFile);
        if (ambigs != null) {
            Update update = new Update();
            c.accept(update);
            if (update.entries != null) {
                ambigs.setEntries(update.entries);
            } else {
                ambigs.setEntries(Collections.emptySet());
            }
        }
    }

    private static class Update implements AmbiguityConsumer {

        Set<AmbiguityEntryImpl> entries;

        @Override
        public void reportAmbiguity(String inRule, BitSet alternatives, CharSequence inText, int start, int end) {
            if (entries == null) {
                entries = new HashSet<>();
            }
            CharSequence txt;
            if (end > start) {
                txt = inText.subSequence(start, end);
            } else {
                txt = "";
            }
            entries.add(new AmbiguityEntryImpl(inRule, txt, alternatives, start, end));
        }

    }

    private static class AmbiguityEntryImpl implements AmbiguityRecord {

        final String inRule;
        final CharSequence text;
        final BitSet alternatives;
        final int start;
        final int end;

        public AmbiguityEntryImpl(String inRule, CharSequence text, BitSet alternatives, int start, int end) {
            this.inRule = inRule;
            this.text = text;
            this.alternatives = alternatives;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return inRule + ":" + alternatives + ":" + Escaper.CONTROL_CHARACTERS.escape(text);
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
        public CharSequence causeText() {
            return text;
        }

        @Override
        public int hashCode() {
            return 3 + (79 * inRule.hashCode()) + (131 * alternatives.hashCode());
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

        @Override
        public int start() {
            return start;
        }

        @Override
        public int size() {
            return Math.max(0, end - start);
        }

        @Override
        public AmbiguityRecord newRange(int start, int size) {
            return new AmbiguityEntryImpl(this.rule(), this.causeText(), alternatives, start, end);
        }

        @Override
        public AmbiguityRecord newRange(long start, long size) {
            return newRange((int) start, (int) size);
        }
    }
}

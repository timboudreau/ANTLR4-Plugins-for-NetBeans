package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyNamedRegionReferenceSets<K extends Enum<K>> implements NamedRegionReferenceSets<K> {

    @Override
    public NamedRegionReferenceSet<K> references(String name) {
        return new EmptyRS<>();
    }

    @Override
    public NamedSemanticRegionReference<K> itemAt(int pos) {
        return null;
    }

    @Override
    public Set<String> collectNames(Predicate<NamedSemanticRegionReference<K>> pred) {
        return Collections.emptySet();
    }

    @Override
    public Iterator<NamedRegionReferenceSet<K>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public NamedSemanticRegionReference<K> at(int position) {
        return null;
    }

    @Override
    public boolean isChildType(IndexAddressableItem item) {
        return false;
    }

    @Override
    public int indexOf(Object o) {
        return -1;
    }

    @Override
    public NamedSemanticRegionReference<K> forIndex(int index) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    private static class EmptyRS<K extends Enum<K>> implements NamedRegionReferenceSet<K> {

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean contains(int pos) {
            return false;
        }

        @Override
        public String name() {
            return "_";
        }

        @Override
        public NamedSemanticRegionReference<K> at(int pos) {
            return null;
        }

        @Override
        public NamedSemanticRegion<K> original() {
            return null;
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Iterator<NamedSemanticRegionReference<K>> byPositionIterator() {
            return Collections.emptyIterator();
        }

        @Override
        public boolean isChildType(IndexAddressableItem item) {
            return false;
        }

        @Override
        public int indexOf(Object o) {
            return -1;
        }

        @Override
        public NamedSemanticRegionReference<K> forIndex(int index) {
            return null;
        }
    }

}

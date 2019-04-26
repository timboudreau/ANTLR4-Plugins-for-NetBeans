package org.nemesis.range;

/**
 * Integer range subtype which has mutator methods.  Synchronized and
 * unsynchronized implementations are available from static methods on Range.
 *
 * @author Tim Boudreau
 */
public interface MutableIntRange<OI extends MutableIntRange<OI>> extends IntRange<OI>, MutableRange<OI> {

    boolean setStartAndSize(int start, int size);

    @Override
    default boolean setStartAndSizeValues(Number start, Number size) {
        return setStartAndSize(start.intValue(), size.intValue());
    }

    default boolean setStart(int start) {
        assert start >= 0;
        return setStartAndSize(start, size());
    }

    default boolean setSize(int size) {
        assert size >= 0;
        return setStartAndSize(start(), size);
    }

    default void shift(int amount) {
        setStartAndSize(start() + amount, size());
    }

    default void grow(int by) {
        setStartAndSize(start(), size() + by);
    }

    default void shrink(int by) {
        setStartAndSize(start(), size() - by);
    }

    default boolean resizeIfExact(int start, int oldSize, int newSize) {
        if (start() == start && oldSize == size()) {
            return setSize(newSize);
        }
        return false;
    }

}

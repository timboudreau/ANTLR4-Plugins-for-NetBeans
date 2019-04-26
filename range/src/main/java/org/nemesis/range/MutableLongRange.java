package org.nemesis.range;

/**
 * Integer range subtype which has mutator methods. Synchronized and
 * unsynchronized implementations are available from static methods on Range.
 *
 * @author Tim Boudreau
 */
public interface MutableLongRange<OI extends MutableLongRange<OI>> extends LongRange<OI>, MutableRange<OI> {

    boolean setStartAndSize(long start, long size);

    @Override
    default boolean setStartAndSizeValues(Number start, Number size) {
        return setStartAndSize(start.longValue(), size.longValue());
    }

    default boolean setStart(long start) {
        assert start >= 0;
        return setStartAndSize(start, size());
    }

    default boolean setSize(long size) {
        assert size >= 0;
        return setStartAndSize(start(), size);
    }

    default void shift(long amount) {
        setStartAndSize(start() + amount, size());
    }

    default void grow(long by) {
        setStartAndSize(start(), size() + by);
    }

    default void shrink(long by) {
        setStartAndSize(start(), size() - by);
    }

    default boolean resizeIfExact(long start, long oldSize, long newSize) {
        if (start() == start && oldSize == size()) {
            return setSize(newSize);
        }
        return false;
    }

}

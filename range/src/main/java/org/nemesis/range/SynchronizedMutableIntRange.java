package org.nemesis.range;

/**
 *
 * @author Tim Boudreau
 */
final class SynchronizedMutableIntRange extends MutableIntRangeImpl {

    public SynchronizedMutableIntRange(int start, int size) {
        super(start, size);
    }

    @Override
    public synchronized boolean setStartAndSize(int start, int size) {
        return super.setStartAndSize(start, size);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized int start() {
        return super.start();
    }

    @Override
    public MutableIntRangeImpl newRange(int start, int size) {
        return new SynchronizedMutableIntRange(start, size);
    }

    @Override
    public MutableIntRangeImpl newRange(long start, long size) {
        assert start <= Integer.MAX_VALUE;
        assert size <= Integer.MAX_VALUE && size >= 0;
        return new SynchronizedMutableIntRange((int) start, (int) size);
    }

}

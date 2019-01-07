package org.nemesis.jfs.nio;

/**
 *
 * @author Tim Boudreau
 */
interface Range {

    int start();

    default int stop() {
        return start() + size() - 1;
    }

    int size();

    default int end() {
        return start() + size();
    }

    default Range snapshot() {
        int start = start();
        int sz = size();
        return new Range() {
            @Override
            public int start() {
                return start;
            }

            @Override
            public int size() {
                return sz;
            }
        };
    }
}

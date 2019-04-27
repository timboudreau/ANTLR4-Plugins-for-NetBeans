package org.nemesis.indexed;

import java.util.Arrays;
import java.util.Objects;
import org.nemesis.indexed.Indexed;

/**
 *
 * @author Tim Boudreau
 */
final class StringArrayIndexed implements Indexed<String> {

    private final String[] sortedStrings;

    StringArrayIndexed(String[] sortedStrings) {
        this.sortedStrings = sortedStrings;
    }

    @Override
    public int indexOf(Object o) {
        return Arrays.binarySearch(sortedStrings, Objects.toString(o, ""));
    }

    @Override
    public String forIndex(int index) {
        return sortedStrings[index];
    }

    @Override
    public int size() {
        return sortedStrings.length;
    }

}

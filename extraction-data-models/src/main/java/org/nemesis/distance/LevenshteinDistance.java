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
package org.nemesis.distance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public class LevenshteinDistance {

    /**
     * Sort a list of strings by their distance to the passed ones
     * <i>case-insensitively</i>.
     *
     * @param to The target string
     * @param l The list
     */
    public static void sortByDistance(String to, List<String> l) {
        sortByDistance(to, false, l);
    }

    /**
     * Sort a list of strings by their distance to the passed ones.
     *
     * @param to The target string
     * @param caseSensitive Whether or not the distance computation is case
     * sensitive
     * @param l A list of strings
     */
    public static void sortByDistance(String to, boolean caseSensitive, List<String> l) {
        Collections.sort(l, new LevenshteinComparator(to, caseSensitive));
    }

    /**
     * Sort a list of objects <i>case-insensitively</i> by their distance to a
     * target.
     *
     * @param <T> The type
     * @param to The target item
     * @param items The items
     * @param stringifier A function to convert items to strings
     */
    public static <T> void sortByDistance(T to, List<T> items, Function<T, String> stringifier) {
        sortByDistance(to, false, items, stringifier);
    }

    /**
     * Sort a list of objects by their distance to a target.
     *
     * @param <T> The type
     * @param to The target item
     * @param caseSensitive Whether or not the distance computation should be
     * case-sensitive
     * @param items The items
     * @param stringifier A function to convert items to strings
     */
    public static <T> void sortByDistance(T to, boolean caseSensitive, List<T> items, Function<T, String> stringifier) {
        Collections.sort(items, distanceComparator(to, caseSensitive, stringifier));
    }

    /**
     * Get a <i>case-insensitive</i> comparator of strings by their distance to
     * the passed target string.
     *
     * @param to The target string
     * @return A comparator
     */
    public static Comparator<String> distanceComparator(String to) {
        return distanceComparator(to, false);
    }

    /**
     * Get a comparator of strings by their distance to the passed target
     * string.
     *
     * @param to The target string
     * @param caseSensitive Whether or not the distance computation is
     * case-sensitive
     * @return A comparator
     */
    public static Comparator<String> distanceComparator(String to, boolean caseSensitive) {
        return new LevenshteinComparator(to, caseSensitive);
    }

    /**
     * Get a <i>case-insensitive</i> comparator of objects by their distance to
     * the passed target object as converted by the passed stringifier function.
     *
     * @param to The target object
     * @param stringifier The string conversion function
     * @return A comparator
     */
    public static <T> Comparator<T> distanceComparator(T to, Function<T, String> stringifier) {
        return distanceComparator(to, false, stringifier);
    }

    /**
     * Get a comparator of objects by their distance to the passed target object
     * as converted by the passed stringifier function.
     *
     * @param to The target object
     * @param caseSensitive Whether or not the distance computation is
     * case-sensitive
     * @param stringifier The string conversion function
     * @return A comparator
     */
    public static <T> Comparator<T> distanceComparator(T to, boolean caseSensitive, Function<T, String> stringifier) {
        return new AdaptedLevenshteinComparator<>(to, caseSensitive, stringifier);
    }

    /**
     * Collect the top n nearest items by distance to the passed item from the
     * passed list, without altering the original list, computing the distance
     * <i>case insensitively</i>.
     *
     * @param <T> The type
     * @param max The maximum size of the returned list
     * @param to The target item
     * @param items The items to sort
     * @param stringifier A string conversion function
     * @return A new list no larger than max
     */
    public static <T> List<T> topMatches(int max, T to, List<T> items, Function<T, String> stringifier) {
        return topMatches(max, to, false, items, stringifier);
    }

    /**
     * Collect the top n nearest items by distance to the passed item from the
     * passed list, without altering the original list.
     *
     * @param <T> The type
     * @param max The maximum size of the returned list
     * @param to The target item
     * @param caseSensitive Whether or not the distance computation should be
     * case-sensitive
     * @param items The items to sort
     * @param stringifier A string conversion function
     * @return A new list no larger than max
     */
    public static <T> List<T> topMatches(int max, T to, boolean caseSensitive, List<T> items, Function<T, String> stringifier) {
        List<T> result = new ArrayList<>(items);
        sortByDistance(to, caseSensitive, result, stringifier);
        if (result.size() > max) {
            return result.subList(0, max);
        }
        return result;
    }

    /**
     * Collect the top n nearest items by distance to the passed item from the
     * passed list, without altering the original list, computing the distance
     * <i>case insensitively</i>.
     *
     * @param <T> The type
     * @param max The maximum size of the returned list
     * @param to The target item
     * @param items The items to sort
     * @return A new list no larger than max
     */
    public static List<String> topMatches(int max, String to, List<String> items) {
        return topMatches(max, to, false, items);
    }

    /**
     * Collect the top n nearest items by distance to the passed item from the
     * passed list, without altering the original list.
     *
     * @param <T> The type
     * @param max The maximum size of the returned list
     * @param to The target item
     * @param caseSensitive Whether or not the distance computation should be
     * case-sensitive
     * @param items The items to sort
     * @return A new list no larger than max
     */
    public static List<String> topMatches(int max, String to, boolean caseSensitive, List<String> items) {
        List<String> result = new ArrayList<>(items);
        sortByDistance(to, result);
        if (result.size() > max) {
            return result.subList(0, max);
        }
        return result;
    }

    private static final class AdaptedLevenshteinComparator<T> implements Comparator<T> {

        private final T orig;
        private final Function<T, String> stringifier;
        private final boolean caseSensitive;
        LevenshteinComparator comparator;

        public AdaptedLevenshteinComparator(T orig, boolean caseSensitive, Function<T, String> stringifier) {
            this.orig = orig;
            this.caseSensitive = caseSensitive;
            this.stringifier = stringifier;
        }

        @Override
        public int compare(T o1, T o2) {
            if (comparator == null) {
                comparator = new LevenshteinComparator(stringifier.apply(orig), caseSensitive);
            }
            return comparator.compare(stringifier.apply(o1), stringifier.apply(o2));
        }
    }

    private static final class LevenshteinComparator implements Comparator<String> {

        private final String to;
        private final boolean caseSensitive;

        LevenshteinComparator(String to, boolean caseSensitive) {
            this.to = to;
            this.caseSensitive = caseSensitive;
        }

        LevenshteinComparator(String to) {
            this(to, false);
        }

        @Override
        public int compare(String a, String b) {
            int da = levenshteinDistance(to, a, caseSensitive);
            int db = levenshteinDistance(to, b, caseSensitive);
            return Integer.compare(da, db);
        }
    }

    public static int levenshteinDistance(
            String a,
            String b,
            final boolean caseSensitive) {
        if (!caseSensitive) {
            a = a.toLowerCase();
            b = b.toLowerCase();
        }
        int[][] distance = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 1; j <= b.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                distance[i][j] = minimum(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1] + ((a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1));
            }
        }

        return distance[a.length()][b.length()];
    }

    private static int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }
}

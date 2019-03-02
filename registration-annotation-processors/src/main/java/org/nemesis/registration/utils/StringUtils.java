package org.nemesis.registration.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public class StringUtils {

    private static final String DEFAULT_DELIMITER = ", ";

    public static <T> String concatenate(Iterable<T> iter, Function<? super T, String> toString) {
        return concatenate(DEFAULT_DELIMITER, iter, toString);
    }

    public static <T> String concatenate(Iterable<T> iter, String delimiter) {
        return concatenate(delimiter, iter, StringUtils::objToString);
    }

    public static <T> String concatenate(String delimiter, Iterable<T> iter, Function<? super T, String> toString) {
        StringBuilder sb = new StringBuilder();
        concatenate(delimiter, iter, sb, toString);
        return sb.toString();
    }

    public static <T> void concatenate(Iterable<T> iter, StringBuilder into, Function<? super T, String> toString) {
        concatenate(DEFAULT_DELIMITER, iter, into, toString);
    }

    public static <T> void concatenate(String delimiter, Iterable<T> iter, StringBuilder into, Function<? super T, String> toString) {
        for (Iterator<T> it = iter.iterator(); it.hasNext();) {
            into.append(toString.apply(it.next()));
            if (it.hasNext()) {
                into.append(delimiter);
            }
        }
    }

    public static String concatenate(Iterable<?> iter) {
        return concatenate(DEFAULT_DELIMITER, iter);
    }

    public static String concatenate(String delimiter, Iterable<?> iter) {
        StringBuilder sb = new StringBuilder();
        concatenate(delimiter, iter, sb);
        return sb.toString();
    }

    public static void concatenate(String delimiter, Iterable<?> iter, StringBuilder into) {
        concatenate(delimiter, iter, into, StringUtils::objToString);
    }

    private static String objToString(Object o) {
        if (o != null && o.getClass().isArray()) {
            List<Object> l = new ArrayList<>();
            int max = Array.getLength(o);
            for (int i = 0; i < max; i++) {
                l.add(Array.get(o, i));
            }
            StringBuilder sb = new StringBuilder();
            concatenate(", ", l, sb);
            return sb.toString();
        } else if (o instanceof Iterable<?>) {
            Iterable<?> it = (Iterable<?>) o;
            StringBuilder sb = new StringBuilder();
            concatenate(", ", it, sb);
            return sb.toString();
        } else {
            return Objects.toString(o);
        }
    }
}

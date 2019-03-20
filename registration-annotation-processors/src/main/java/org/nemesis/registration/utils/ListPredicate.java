package org.nemesis.registration.utils;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
interface ListPredicate<T> extends Predicate<T>, Consumer<Predicate<? super T>>, NamedPredicate<T>, Iterable<Predicate<? super T>> {

    @Override
    default String name() {
        Iterator<?> it = iterator();
        if (!it.hasNext()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            String name = Named.findName(it.next());
            sb.append(name);
            if (it.hasNext()) {
                sb.append(" && ");
            }
        }
        return sb.toString();
    }

}

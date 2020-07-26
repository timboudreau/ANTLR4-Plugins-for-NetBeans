package org.nemesis.antlr.subscription;

import java.util.Collection;

/**
 *
 * @author Tim Boudreau
 */
interface SubscribersStore<K, C> {

    Collection<? extends C> subscribersTo(K key);

}

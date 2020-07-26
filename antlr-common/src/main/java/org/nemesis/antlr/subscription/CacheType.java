/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.subscription;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Types of map from subscribed-to to set-of-subscribers.
 *
 * @author Tim Boudreau
 */
public enum CacheType {
    /**
     * Use a regular java.util.HashMap.
     */
    EQUALITY,
    /**
     * Use a WeakHashMap.
     */
    WEAK,
    /**
     * Use a ConcurrentHashMap.
     */
    EQUALITY_CONCURRENT,
    /**
     * Use an IdentityHashMap.
     */
    IDENTITY,
    /**
     * Use a binary-search integer map mapping the identity hash code of the
     * object; no reference to the original object is retained. Behavior is
     * similar to that of WeakHashMap, but better performaing, with the caveat
     * that there is no mechanism to garbage collect references to key objects,
     * so if there are likely to be many objects listened to for a short while
     * in the JVM, the map will grow, accumulating stale hash codes unless
     * anything that gets subscribed also gets explicitly unsubscribed.
     */
    IDENTITY_WITHOUT_REFERENCE;

    <T, R> Map<T, R> createMap(int size, boolean threadSafe) {
        Map<T, R> result;
        switch (this) {
            case EQUALITY:
                result = new HashMap<>(size);
                break;
            case EQUALITY_CONCURRENT:
                return new ConcurrentHashMap<>(size);
            case IDENTITY:
                result = new IdentityHashMap<>(size);
                break;
            case WEAK:
                result = new WeakHashMap<>(size);
                break;
            case IDENTITY_WITHOUT_REFERENCE:
            default:
                throw new UnsupportedOperationException();
        }
        return threadSafe ? Collections.synchronizedMap(result) : result;
    }

    boolean isMapBased() {
        return hasKeyReference();
    }

    boolean hasKeyReference() {
        return this != IDENTITY_WITHOUT_REFERENCE;
    }

    boolean stronglyReferencesKeys() {
        switch (this) {
            case EQUALITY:
            case IDENTITY:
                return true;
            default:
                return false;
        }
    }
}

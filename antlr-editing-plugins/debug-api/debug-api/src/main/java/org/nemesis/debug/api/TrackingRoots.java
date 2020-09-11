/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.debug.api;

import com.mastfrog.predicates.Predicates;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 * Allows modules to inject roots into the object reference search.
 *
 * @author Tim Boudreau
 */
public interface TrackingRoots {

    /**
     * Collect root objects to search via fields and references for a queried
     * object.
     *
     * @param nameAndObject Accepts names and objects to search from
     */
    default void collect(BiConsumer<String, Object> nameAndObject) {

    }

    default Predicate<? super Object> shouldIgnorePredicate() {
        return null;
    }

    public static Predicate<? super Object> ignoreTester() {
        List<Predicate<? super Object>> all = new ArrayList<>();
        for (TrackingRoots r : Lookup.getDefault().lookupAll(TrackingRoots.class)) {
            Predicate<? super Object> pred = r.shouldIgnorePredicate();
            if (pred != null) {
                all.add(pred);
            }
        }
        if (all.isEmpty()) {
            return Predicates.alwaysFalse();
        }
        return obj -> {
            for (Predicate<? super Object> p : all) {
                if (p.test(obj)) {
                    return true;
                }
            }
            return false;
        };
    }

    public static void collectAll(BiConsumer<String, Object> consumer) {
        for (TrackingRoots r : Lookup.getDefault().lookupAll(TrackingRoots.class)) {
            try {
                r.collect(consumer);
            } catch (Exception | Error ex) {
                Logger.getLogger(TrackingRoots.class.getName()).log(Level.WARNING, consumer.toString(), ex);
            }
        }
    }
}

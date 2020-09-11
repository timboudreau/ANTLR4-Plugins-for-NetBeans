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
package org.nemesis.jfs.result;

import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public interface ProcessingResult {

    boolean isUsable();

    Optional<Throwable> thrown();

    UpToDateness currentStatus();

    default void rethrow() throws Throwable {
        Optional<Throwable> result = thrown();
        if (result.isPresent()) {
            throw result.get();
        }
    }

    default <T> T getWrapped(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }
}

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

package org.nemesis.antlr.live.execution.impl;

import com.mastfrog.subscription.SubscribableBuilder;
import java.util.function.BiConsumer;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.extraction.Extraction;
import org.openide.filesystems.FileObject;

/**
 * Cache key for collections of subscriptions to different files, where
 * those subscriptions all use the same type (such as EmbeddedAntlrParser)
 * for the thing that gets updated on rebuilds and can be used to run code
 * against a compiled grammar in-memory.
 *
 * @param <T>
 */
class InvocationRunnerLookupKey<T> extends TypedCache.K<SubscribableBuilder.SubscribableContents<Object, FileObject, BiConsumer<Extraction, GrammarRunResult<T>>, AntlrGenerationEvent>> {

    final Class<T> invocationResultType;

    public InvocationRunnerLookupKey(Class<T> invocationResultType) {
        super(SubscribableBuilder.SubscribableContents.class, invocationResultType.getName());
        this.invocationResultType = invocationResultType;
    }

    MapCacheKey<T> mapCacheKey() {
        return new MapCacheKey<>(this);
    }

    Class<T> invocationResultType() {
        return invocationResultType;
    }

    static <T> InvocationRunnerLookupKey<T> cast(TypedCache.K<?> k) {
        if (k instanceof InvocationRunnerLookupKey<?>) {
            return (InvocationRunnerLookupKey<T>) k;
        }
        throw new AssertionError(k + "");
    }

    @Override
    public int hashCode() {
        return invocationResultType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final InvocationRunnerLookupKey<?> other = (InvocationRunnerLookupKey<?>) obj;
        return this.invocationResultType == other.invocationResultType;
    }

    @Override
    public String toString() {
        return "IK<" + invocationResultType.getSimpleName() + ">";
    }

}

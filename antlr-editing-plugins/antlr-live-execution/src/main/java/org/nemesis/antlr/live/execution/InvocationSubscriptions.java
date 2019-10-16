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
package org.nemesis.antlr.live.execution;

import java.util.function.BiConsumer;
import org.nemesis.antlr.compilation.GrammarRunResult;
import org.nemesis.extraction.Extraction;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public final class InvocationSubscriptions<T> {

    private final Class<T> type;

    InvocationSubscriptions(Class<T> type) {
        this.type = type;
    }

    public Runnable subscribe(FileObject file, BiConsumer<Extraction, GrammarRunResult<T>> subscriber) {
        return AntlrRunSubscriptions.instance()._subscribe(file, type, subscriber);
    }
}

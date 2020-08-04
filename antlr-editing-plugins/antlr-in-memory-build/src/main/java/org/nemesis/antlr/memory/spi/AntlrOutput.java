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

package org.nemesis.antlr.memory.spi;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public interface AntlrOutput {

    public static AntlrOutput getDefault() {
        AntlrOutput out = Lookup.getDefault().lookup(AntlrOutput.class);
        return out == null ? DummyOutput.INSTANCE : out;
    }

    /**
     * Get a supplier for the output of the particular type of processing
     * of the passed file, if any; if present and non-null, implementations
     * should guarantee that the output at the time this call is made will
     * be snapshotted and not updated, if stored in reusable storage).
     *
     * @param path The file path
     * @param task The name of the task
     * @return A supplier if there is some output, and null if not
     */
    Supplier<? extends CharSequence> outputFor(Path path, String task);

    default void onUpdated(Consumer<Path> c) {
        // do nothing
    }

}

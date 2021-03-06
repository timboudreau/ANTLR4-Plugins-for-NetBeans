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
package org.nemesis.jfs;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_16;
import javax.tools.JavaFileManager.Location;

/**
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
interface JFSStorageAllocator<T extends JFSBytesStorage> {

    public static final Charset DEFAULT_ENCODING = UTF_16;
    T allocate(JFSStorage storage, Name name, Location location);

    default void onDiscard(JFSBytesStorage obj) {
        // do nothing
    }

    default void destroy() {

    }

    default Charset encoding() {
        return DEFAULT_ENCODING;
    }

    default boolean isDefaultEncoding() {
        return encoding() == DEFAULT_ENCODING;
    }

    default JFSStorageAllocator<?> toReadOnlyAllocator() {
        if (isDefaultEncoding()) {
            return READ_ONLY;
        } else {
            return READ_ONLY.withEncoding(encoding());
        }
    }

    default JFSStorageAllocator<T> withEncoding(Charset encoding) {
        if (encoding == this.encoding()) {
            return this;
        }
        return new JFSStorageAllocator<T>(){
            @Override
            public T allocate(JFSStorage storage, Name name, Location location) {
                return JFSStorageAllocator.this.allocate(storage, name, location);
            }

            @Override
            public Charset encoding() {
                return encoding;
            }
        };
    };

    static JFSStorageAllocator<?> defaultAllocator() {
        boolean useOffHeap = Boolean.getBoolean("jfs.off.heap");
        return useOffHeap ? NioBytesStorageAllocator.allocator() : HEAP;
    }

    public static JFSStorageAllocator<HeapBytesStorageImpl> HEAP
            = (JFSStorage storage, Name name, Location location) -> new HeapBytesStorageImpl(storage);

    public static JFSStorageAllocator<HeapBytesStorageImpl> READ_ONLY
            = (JFSStorage storage, Name name, Location location) -> {
                throw new IllegalStateException("Read only. Cannot allocate for " + name + " in " + location);
            };

}

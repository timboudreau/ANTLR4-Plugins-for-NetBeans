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
package org.nemesis.jfs.nio;

/**
 *
 * @author Tim Boudreau
 */
final class BlockToBytesConverter {

    private final int bytesPerBlock;

    BlockToBytesConverter(int bytesPerBlock) {
        this.bytesPerBlock = bytesPerBlock;
    }

    int blockSize() {
        return bytesPerBlock;
    }

    int blocksToBytes(int blocks) {
        return blocks * bytesPerBlock;
    }

    int bytesToBlocks(int bytes) {
        if (bytes == 0) {
            return 0;
        } else if (bytes < bytesPerBlock) {
            return 1;
        }
        return (bytes / bytesPerBlock) + ((bytes % bytesPerBlock > 0) ? 1 : 0);
    }
}

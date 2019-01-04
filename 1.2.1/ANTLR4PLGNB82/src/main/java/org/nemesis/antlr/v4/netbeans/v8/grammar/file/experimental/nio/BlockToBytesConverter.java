package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

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

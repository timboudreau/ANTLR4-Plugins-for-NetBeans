package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(Parameterized.class)
public class ByteBufferAllocatorTest {

    private final ByteBufferAllocator alloc;
    private final BlockStorageKind kind;

    public ByteBufferAllocatorTest(BlockStorageKind kind) throws IOException {
        this.kind = kind;
        this.alloc = kind.createBufferAllocator(new BlockToBytesConverter(5), 10, Ops.defaultOps());
    }

    @Parameters
    public static Set<BlockStorageKind> params() {
        return EnumSet.allOf(BlockStorageKind.class);
    }

    @Test
    public void testNonOverlappingBackwardMove() throws IOException {
        assertBytes(nums, 50);
        assertBytes(nums2, 90);
        move(50, 15, 0);
        assertBytes(nums, 0);
        assertBytes(nums2, 90);
        move(90, 10, 16);
        assertBytes(nums2, 16);
    }

    @Test
    public void testNonOverlappingForewardMove() throws IOException {
        alloc.ensureSize(200, 100);
        assertBytes(nums, 50);
        assertBytes(nums2, 90);
        move(50, 15, 150);
        assertBytes(nums, 150);
        assertBytes(nums2, 90);
        move(90, 10, 190);
        assertBytes(nums2, 190);
    }

    @Test
    public void testOverlappingBackwardMove() throws IOException {
        assertBytes(nums, 50);
        assertBytes(nums2, 90);
        move(50, 15, 49);
        assertBytes(nums, 49);
        assertBytes(nums2, 90);
        move(90, 10, 89);
        assertBytes(nums2, 89);
        move(89, 10, 87);
        assertBytes(nums2, 87);
        move(87, 10, 78);
        assertBytes(nums2, 78);
        move(78, 10, 68);
        assertBytes(nums2, 68);
    }

    @Test
    public void testOverlappingForewardMove() throws IOException {
        alloc.ensureSize(150, 100);
        assertBytes(nums, 50);
        assertBytes(nums2, 90);
        move(50, 15, 51);
        assertBytes(nums, 51);
        assertBytes(nums2, 90);
        move(90, 10, 91);
        assertBytes(nums2, 91);
        move(91, 10, 93);
        assertBytes(nums2, 93);
        move(93, 10, 102);
        assertBytes(nums2, 102);
        move(102, 10, 112);
        assertBytes(nums2, 112);
    }

    private void move(int start, int len, int newStart) throws IOException {
        alloc.move(start, len, newStart);
    }

    private void assertBytes(byte[] expect, int start) throws IOException {
        alloc.withBufferIO(start, start + expect.length, buf -> {
            byte[] got = new byte[expect.length];
            buf.get(got);
            assertArrayEquals(expect, got);
        });
    }

    private byte[] nums;
    private byte[] nums2;
    private byte[] negs = new byte[]{-1, -1};

    private void writeNegsPreceding(int pos) throws IOException {
        writeBytesPreceding(pos, negs);
    }

    private void writeNegsAfter(int pos, byte[] bytes) throws IOException {
        writeBytes(pos + bytes.length, negs);
    }

    private void writeBytesPreceding(int pos, byte[] nums) throws IOException {
        alloc.withBufferIO(pos - nums.length - 1, pos, buf -> {
            buf.put(nums);
        });
    }

    private void writeBytes(int pos, byte[] nums) throws IOException {
        alloc.withBufferIO(pos, pos + nums.length, buf -> {
            buf.put(nums);
        });
    }

    @Before
    public void writeInitialData() throws IOException {
        alloc.ensureSize(100, 0);
        nums = new byte[15];
        nums2 = new byte[10];
        for (int i = 0; i < nums.length; i++) {
            nums[i] = (byte) (i + 1);
        }
        for (int i = 0; i < nums2.length; i++) {
            nums2[i] = (byte) (nums2.length - i);
        }
        writeBytes(50, nums);
        writeBytes(90, nums2);

//        alloc.withBufferIO(50, 65, buf -> {
//            buf.put(nums);
//        });
//        alloc.withBufferIO(90, 100, buf -> {
//            buf.put(nums2);
//        });
    }

    @After
    public void dispose() throws IOException {
        alloc.close();
    }

}

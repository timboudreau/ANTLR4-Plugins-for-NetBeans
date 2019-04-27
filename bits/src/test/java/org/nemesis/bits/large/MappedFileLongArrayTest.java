package org.nemesis.bits.large;

import org.nemesis.bits.large.MappedFileLongArray;
import org.nemesis.bits.large.JavaLongArray;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class MappedFileLongArrayTest {

    private final Random rnd = new Random(1384013940139L);

    @Test
    public void testSimple() throws Exception {
        MappedFileLongArray arr = new MappedFileLongArray();
        try {
            long[] vals = new long[20];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = rnd.nextLong();
            }
            for (int i = 0; i < vals.length; i++) {
                arr.set(i, vals[i]);
            }
            for (int i = 0; i < vals.length; i++) {
                assertEquals(vals[i], arr.get(i));
            }
        } finally {
            arr.destroy();
        }
    }

    @Test
    public void testFill() throws Exception {
        MappedFileLongArray arr = new MappedFileLongArray();
        try {
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, i);
            }
            for (long i = 0; i < arr.size(); i++) {
                assertEquals(i, arr.get(i));
            }
            long val = Long.MAX_VALUE / 4;
            arr.fill(0, arr.size(), val);
            for (long i = 0; i < arr.size(); i++) {
                assertEquals(i + "", val, arr.get(i));
            }
            long size = arr.size() * 2;
            for (int i = 0; i < size; i++) {
                arr.set(i, i * 2);
            }
            assertEquals(size, arr.size());
            for (int i = 0; i < size; i++) {
                assertEquals(i * 2, arr.get(i));
            }
            arr.close();
            arr = new MappedFileLongArray(arr.file());
            for (int i = 0; i < size; i++) {
                assertEquals(i * 2, arr.get(i));
            }
            for (int i = 1; i < arr.size(); i++) {
                arr.fill(arr.size() - i, i, 23);
                for (int j = 0; j < arr.size(); j++) {
                    if (j < arr.size() - i) {
                        assertEquals("at " + i + "," + j, j * 2, arr.get(j));
                    } else {
                        assertEquals("Mismatch at " + i + "," + j, 23, arr.get(j));
                    }
                }
            }
        } finally {
            arr.destroy();
        }
    }

    @Test
    public void testSimpleJava() throws Exception {
        JavaLongArray arr = new JavaLongArray(20);
        long[] vals = new long[20];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = rnd.nextLong();
        }
        for (int i = 0; i < vals.length; i++) {
            arr.set(i, vals[i]);
        }
        for (int i = 0; i < vals.length; i++) {
            assertEquals(vals[i], arr.get(i));
        }
    }

    @Test
    public void testFillJava() throws Exception {
        JavaLongArray arr = new JavaLongArray(500);
        for (int i = 0; i < arr.size(); i++) {
            arr.set(i, i);
        }
        for (long i = 0; i < arr.size(); i++) {
            assertEquals(i, arr.get(i));
        }
        long val = Long.MAX_VALUE / 4;
        arr.fill(0, arr.size(), val);
        for (long i = 0; i < arr.size(); i++) {
            assertEquals(i + "", val, arr.get(i));
        }
        long size = arr.size() * 2;
        arr.resize(size);
        for (int i = 0; i < size; i++) {
            arr.set(i, i * 2);
        }
        assertEquals(size, arr.size());
        for (int i = 0; i < size; i++) {
            assertEquals(i * 2, arr.get(i));
        }
        for (int i = 1; i < arr.size(); i++) {
            arr.fill(arr.size() - i, i, 23);
            for (int j = 0; j < arr.size(); j++) {
                if (j < arr.size() - i) {
                    assertEquals("at " + i + "," + j, j * 2, arr.get(j));
                } else {
                    assertEquals("Mismatch at " + i + "," + j, 23, arr.get(j));
                }
            }
        }
    }
}

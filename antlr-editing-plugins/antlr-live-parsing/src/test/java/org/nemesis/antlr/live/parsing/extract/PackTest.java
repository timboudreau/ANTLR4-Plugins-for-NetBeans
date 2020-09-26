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
package org.nemesis.antlr.live.parsing.extract;

import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.pack;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackA;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackB;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackC;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackD;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackLeft;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackRight;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.pack3;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.packShort;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpack3Int;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpack3LeftByte;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpack3RightByte;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackShortLeft;
import static org.nemesis.antlr.live.parsing.extract.AntlrProxies.unpackShortRight;

/**
 *
 * @author Tim Boudreau
 */
public class PackTest {

    @Test
    public void testPackShort() {
        int top = (Short.MAX_VALUE * 2) - 2;
        for (int i = 0; i < top; i+=17) {
            int va = i;
            int vb = i+1;
            int val = packShort(va, vb);
            int ua = unpackShortLeft(val);
            int ub = unpackShortRight(val);
            assertEquals(va, ua, "Wrong value at " + i);
            assertEquals(vb, ub, "Wrong value at " + i);
        }
    }

    @Test
    public void testProxyTokenPacked() {
        Random rnd = new Random(31390L);
        int start = 0;
        int currLine = 0;
        for (int x = 1; x < 120; x++) {
            int type = rnd.nextInt(20) + 1;
            if (rnd.nextInt(16) + 1 == 5) {
                currLine++;
            }
            int line = currLine;
            int charPositionInLine = rnd.nextInt(80);
            int channel = rnd.nextInt(5);
            int tokenIndex = x;
            int startIndex = start;
            int length = rnd.nextInt(42) + 1;
            int stopIndex = start + length - 1;
            start += length;
            int trim = rnd.nextInt(length);
            int mode = rnd.nextInt(5);

            String params = x + ". type " + type + " line " + line + " charPos "
                    + charPositionInLine + " channel " + channel + " tokenIndex "
                    + tokenIndex + " startIndex " + startIndex + " length " + length
                    + " stopIndex " + stopIndex + " trim " + trim + " mode " + mode;
            ProxyToken pt = new ProxyToken(type, line, charPositionInLine, channel, tokenIndex, startIndex, stopIndex, trim, mode);

            assertEquals(type, pt.getType(), () -> "Wrong type " + pt.getType() + " - " + params);
            assertEquals(line, pt.getLine(), () -> "Wrong line " + pt.getLine() + " - " + params);
            assertEquals(charPositionInLine, pt.getCharPositionInLine(), () -> "Wrong char position " + pt.getCharPositionInLine()
                    + " - " + params);
            assertEquals(channel, pt.getChannel(), () -> "Wrong channel " + pt.getChannel() + " - " + params);

            assertEquals(tokenIndex, pt.getTokenIndex(), () -> "Wrong token index " + pt.getTokenIndex() + " - " + params);

            assertEquals(startIndex, pt.getStartIndex(), () -> "Wrong start index " + pt.getStartIndex() + " - " + params);

            assertEquals(stopIndex, pt.getStopIndex(), () -> "Wrong stop index " + pt.getStopIndex() + " - " + params);

            assertEquals(trim, pt.trim(), () -> "Wrong trim " + pt.trim() + " - " + params);

            assertEquals(mode, pt.mode(), () -> "Wrong mode " + pt.mode() + " - " + params);

            assertEquals(length, pt.length(), () -> "Wrong length " + pt.length() + " - " + params);

//            assertEquals()
            /*
int type, int line, int charPositionInLine, int channel, int tokenIndex, int startIndex, int stopIndex, int trim, int mode
             */
        }
    }

    @Test
    public void testPack3() {
        int interval = 1903;
        for (int a = Integer.MIN_VALUE + 2; a < Integer.MAX_VALUE - (interval + 1); a += interval) {

            int aa = a;
            int bb = Math.abs(a % 256);
            int cc = Math.abs((a + 1) % 256);

            long val = pack3(a, bb, cc);
            long valA = pack3(a, 0, 0);
            long valB = pack3(0, bb, 0);
            long valC = pack3(0, 0, cc);

            assertNotEquals(0l, val, () -> " Zero at " + aa + " / " + bb + " / " + cc);
            if (bb != 0) {
                assertNotEquals(0l, valB, () -> " Zero for B at " + aa + " / " + bb + " / " + cc);
            }
            if (cc != 0) {
                assertNotEquals(0l, valC, () -> " Zero for C at " + aa + " / " + bb + " / " + cc);
            }

            int ua = unpack3Int(valA);
            int ub = unpack3LeftByte(valB);
            int uc = unpack3RightByte(valC);

            int xa = unpack3Int(val);
            int xb = unpack3LeftByte(val);
            int xc = unpack3RightByte(val);

            assertEquals(aa, ua, () -> "Mismatched iso int value at " + aa + " / " + bb + " / " + cc + " " + split(valA));
            assertEquals(bb, ub, () -> "Mismatched iso left byte value at " + aa + " / " + bb + " / " + cc + " " + split(valB));
            assertEquals(cc, uc, () -> "Mismatched iso left byte value at " + aa + " / " + bb + " / " + cc + " " + split(valC));

            assertEquals(aa, xa, () -> "Mismatched int value at " + aa + " / " + bb + " / " + cc + " " + split(val));
            assertEquals(bb, xb, () -> "Mismatched left byte value at " + aa + " / " + bb + " / " + cc + " " + split(val));
            assertEquals(cc, xc, () -> "Mismatched left byte value at " + aa + " / " + bb + " / " + cc + " " + split(val));

        }
    }

    @Test
    public void testPack2() {
        int interval = 11903;
        for (int i = Integer.MIN_VALUE + 2; i < Integer.MAX_VALUE - (interval + 1); i += interval) {
            int ia = i;
            int ib = i + 1;

            long val = pack(ia, ib);

            long valB = pack(0, ib);
            long valA = pack(ia, 0);

            int ua = unpackLeft(valA);
            int ub = unpackRight(valB);

            int xa = unpackLeft(val);
            int xb = unpackRight(val);

            assertEquals(ia, ua, "Mismatch A iso at " + i);
            assertEquals(ia, xa, "Mismatch A at " + i);

            assertEquals(ib, ub, "Mismatch B iso at " + i);
            assertEquals(ib, xb, "Mismatch B at " + i);
        }
    }

    @Test
    public void testPack4() {
        for (int i = 0; i < (Short.MAX_VALUE * 2); i += 903) {
            int ia = i;
            int ib = i + 1;
            int ic = i + 2;
            int id = i + 3;
            long val = pack(ia, ib, ic, id);
            long valA = pack(ia, 0, 0, 0);
            long valB = pack(0, ib, 0, 0);
            long valC = pack(0, 0, ic, 0);
            long valD = pack(0, 0, 0, id);

            int ua = unpackA(val);
            int ub = unpackB(val);
            int uc = unpackC(val);
            int ud = unpackD(val);

            int xa = unpackA(valA);
            int xb = unpackB(valB);
            int xc = unpackC(valC);
            int xd = unpackD(valD);

//            System.out.println(" A + " + ia + " -> " + xa + " as " + split(valA));
//            System.out.println(" C + " + ia + " -> " + xc + " as " + split(valC));
//            System.out.println(" D + " + ia + " -> " + xd + " as " + split(valD));
            assertEquals(id, xd, () -> "Mismatch iso D at " + ia + " with " + ic + " packed to " + split(valD));
            assertEquals(ic, xc, () -> "Mismatch iso C at " + ia + " with " + ic + " packed to " + split(valC));
            assertEquals(ib, xb, () -> "Mismatch iso B at " + ia + " with " + ib + " packed to " + split(valB));
            assertEquals(ia, xa, () -> "Mismatch iso A at " + ia + " with " + ia + " packed to " + split(valA));

            assertEquals(ia, ua, () -> "Mismatch A at " + ia + " with " + ia + " packed to " + split(val));
            assertEquals(ib, ub, () -> "Mismatch B at " + ia + " with " + ib + " packed to " + split(val));
            assertEquals(ic, uc, () -> "Mismatch C at " + ia + " with " + ic + " packed to " + split(val));
            assertEquals(id, ud, () -> "Mismatch D at " + ia + " with " + id + " packed to " + split(val));

//            System.out.println(i + ". A " + i + " -> " + ua);
//            System.out.println(i + ". B " + (i + 1) + " -> " + ub);
//            System.out.println(i + ". C " + (i + 2) + " -> " + uc);
//            System.out.println(i + ". D " + (i + 3) + " -> " + ud);
        }
    }

    static long MASK = 0x0000_0000_0000_FFFFL;

    static int unpackC2(long value) {
//        value = value & 0x0000_0000_FFFF_0000L;
        System.out.println("UNPACK " + split(value) + " ( " + value + " )");
        long shifted = (value >>> 16);
        System.out.println("SHIFT  " + split(shifted) + " ( " + shifted + " )");
        long masked = shifted & MASK;
        System.out.println("MASK   " + split(MASK) + " ( " + MASK + " )");
        System.out.println("MASKED " + split(masked) + " ( " + masked + " )");
        int result = (int) masked;
        System.out.println("CAST   " + split(result) + " ( " + result + ")");
        return result;
    }

    private static String binaryString(long val) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(Long.toBinaryString(val));
        while (sb.length() < 64) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    private static String split(long val) {
        String res = binaryString(val);
        StringBuilder sb = new StringBuilder();
        int[] vals = new int[8];
        int cursor = 0;
        char pfx = 'a';
        sb.append(pfx).append(": ");
        for (int i = 0; i < res.length(); i++) {
            int bitIx = 15 - (i % 16);
            int or = 1 << bitIx;
            char c = res.charAt(i);
            if ('1' == c) {
                vals[cursor] |= or;
            }
            sb.append(res.charAt(i));
            if ((i + 1) % 16 == 0) {
                sb.append(' ');
                sb.append("(").append(vals[cursor]).append(") ");
                cursor++;
                if (i != res.length() - 1) {
                    sb.append(++pfx).append(": ");
                }
            }
        }
        return sb.toString();
    }
}

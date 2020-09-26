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
package org.nemesis.antlr.live.parsing.extract;

import ignoreme.placeholder.DummyLanguageLexer;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor.CharSequenceCharStream;

/**
 *
 * @author Tim Boudreau
 */
public class CharSequenceCharStreamTest {

    @Test
    @SuppressWarnings("deprecation") // we are testing behavior against the oldest possible implementation
    public void testStreamsBehaveIdentically() {
        org.antlr.v4.runtime.ANTLRInputStream theirs = new org.antlr.v4.runtime.ANTLRInputStream(txt);
        CharSequenceCharStream ours = new CharSequenceCharStream(txt);
        assertEquals(theirs.toString(), ours.toString());

        for (int i = 0; i < txt.length() + 1; i++) {
            char a = (char) theirs.LA(1);
            char b = (char) ours.LA(1);
            assertEquals(a, b, "Mismatch at " + i);
            if (theirs.LA(1) != -1) {
                theirs.consume();
                ours.consume();
            }
            assertEquals(theirs.index(), ours.index());
        }

        for (int i = 1; i < txt.length(); i++) {
            Interval iv = new Interval(0, i);
            String a = theirs.getText(iv);
            String b = ours.getText(iv);
            assertEquals(a, b, "Mismatch for " + iv.a + ":" + iv.b);
        }
    }

    @Test
    @SuppressWarnings("deprecation") // we are testing behavior against the oldest possible implementation
    public void testProxyMaker() throws Throwable {
        String txt = "These are some words.  Sentences, even.";
        CharStream str = new CharSequenceCharStream(txt);
        DummyLanguageLexer lex = new DummyLanguageLexer(str);
        List<ProxyToken> l = new ArrayList<>(30);
        for (int i = 0;; i++) {
            Token t = lex.nextToken();
            ProxyToken pt = new ProxyToken(t.getType(), t.getLine(), t.getCharPositionInLine(),
                    t.getChannel(), i, t.getStartIndex(), t.getStopIndex(), 0);
            l.add(pt);
            if (t.getType() == -1) {
                break;
            }
        }
        str = new org.antlr.v4.runtime.ANTLRInputStream(txt);
        lex = new DummyLanguageLexer(str);
        List<ProxyToken> l2 = new ArrayList<>(30);
        for (int i = 0;; i++) {
            Token t = lex.nextToken();
            ProxyToken pt = new ProxyToken(t.getType(), t.getLine(), t.getCharPositionInLine(),
                    t.getChannel(), i, t.getStartIndex(), t.getStopIndex(), 0);
            l2.add(pt);
            System.out.println(pt.hashCode() + " - " + pt);
            if (t.getType() == -1) {
                break;
            }
        }
        assertEquals(l, l2);
        AntlrProxies.ParseTreeProxy extracted = ParserExtractor.extract(txt, () -> false);
        assertTokensEquals(l, extracted.tokens());
    }

    private void assertTokensEquals(List<ProxyToken> expect, List<ProxyToken> got) {
        int max = Math.min(expect.size(), got.size());
        for (int i = 0; i < max; i++) {
            ProxyToken e = expect.get(i);
            ProxyToken g = got.get(i);
            assertTokensEquals("Token mismatch at " + i, e, g);
        }
        assertEquals(expect.size(), got.size());
    }

    private void assertTokensEquals(String msg, ProxyToken a, ProxyToken b) {
        String m = msg + ": Tokens do not match:\n" + a + "\n" + b;
        assertEquals(a.getType(), b.getType(), () -> "type mismatch; " + m);
        assertEquals(a.getStartIndex(), b.getStartIndex(), () -> "start mismatch; " + m);
        assertEquals(a.getStopIndex(), b.getStopIndex(), () -> "stop mismatch; " + m);
        assertEquals(a.length(), b.length(), () -> "length mismatch; " + m);
        assertEquals(a.getLine(), b.getLine(), () -> "line mismatch; " + m);
        assertEquals(a.getCharPositionInLine(), b.getCharPositionInLine(), () -> "position in line mismatch; " + m);
        assertEquals(a.mode(), b.mode(), () -> "mode mismatch; " + m);
    }

    private static final String txt = "//Copyright 2015 The Rust Project Developers. See the COPYRIGHT\n"
            + "// file at the top-level directory of this distribution and at\n"
            + "// http://rust-lang.org/COPYRIGHT.\n"
            + "//\n"
            + "// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or\n"
            + "// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license\n"
            + "// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your\n"
            + "// option. This file may not be copied, modified, or distributed\n"
            + "// except according to those terms.\n"
            + "//\n"
            + "// compile-flags: -C debug-assertions\n"
            + "//\n"
            + "// Test std::num::Wrapping<T> for {uN, iN, usize, isize}\n"
            + "\n"
            + "use std::num::Wrapping;\n"
            + "use std::ops::{ \n"
            + "    Add, Sub, Mul, Div, Rem, BitXor, BitOr, BitAnd,\n"
            + "    AddAssign, SubAssign, MulAssign, DivAssign, RemAssign, BitXorAssign, BitOrAssign, BitAndAssign,\n"
            + "    Shl, Shr, ShlAssign, ShrAssign\n"
            + "}; \n"
            + "use test::black_box; ";

}

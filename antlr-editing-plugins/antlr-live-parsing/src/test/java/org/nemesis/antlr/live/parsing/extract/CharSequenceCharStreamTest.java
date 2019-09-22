/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.parsing.extract;

import ignoreme.placeholder.DummyLanguageLexer;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ANTLRInputStream;
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
    public void testStreamsBehaveIdentically() {
        ANTLRInputStream theirs = new ANTLRInputStream(txt);
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
    public void testProxyMaker() throws Throwable {
        String txt = "These are some words.  Sentences, even.";
        CharStream str = new CharSequenceCharStream(txt);
        DummyLanguageLexer lex = new DummyLanguageLexer(str);
        List<ProxyToken> l = new ArrayList<>(30);
        for (;;) {
            Token t = lex.nextToken();
            ProxyToken pt = new ProxyToken(t.getText(), t.getType(), t.getLine(), t.getCharPositionInLine(), t.getChannel(), t.getTokenIndex(), t.getStartIndex(), t.getStopIndex());
            l.add(pt);
            if (t.getType() == -1) {
                break;
            }
        }
        str = new ANTLRInputStream(txt);
        lex = new DummyLanguageLexer(str);
        List<ProxyToken> l2 = new ArrayList<>(30);
        for (;;) {
            Token t = lex.nextToken();
            ProxyToken pt = new ProxyToken(t.getText(), t.getType(), t.getLine(), t.getCharPositionInLine(), t.getChannel(), t.getTokenIndex(), t.getStartIndex(), t.getStopIndex());
            l2.add(pt);
            if (t.getType() == -1) {
                break;
            }
        }
        assertEquals(l, l2);
        AntlrProxies.ParseTreeProxy extracted = ParserExtractor.extract(txt);
        assertEquals(l, extracted.tokens());
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

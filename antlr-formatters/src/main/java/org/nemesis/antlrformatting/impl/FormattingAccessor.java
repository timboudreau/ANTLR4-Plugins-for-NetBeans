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
package org.nemesis.antlrformatting.impl;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.nemesis.antlrformatting.api.Criterion;
import org.nemesis.antlrformatting.api.FormattingRules;
import org.nemesis.antlrformatting.api.LexingState;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.netbeans.modules.csl.api.Formatter;

/**
 *
 * @author Tim Boudreau
 */
public abstract class FormattingAccessor {

    public static FormattingAccessor DEFAULT;
    public static Function<AntlrFormatterProvider, Formatter> FMT_CONVERT;

    public static FormattingAccessor getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = LexingState.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FormattingAccessor.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        type = AntlrFormatterProvider.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(FormattingAccessor.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert FMT_CONVERT != null : "The FMT_CONVERT field must be initialized";

        return DEFAULT;
    }

    public abstract TokenStream createCompleteTokenStream(Lexer lexer, String[] modeNames);

//    public abstract String reformat(AntlrFormatterProvider<?,?> provider, int start, int end, String text);
    public abstract String reformat(int start, int end, int indentSize,
            FormattingRules rules, LexingState state, Criterion whitespace,
            Predicate<Token> debug, Lexer lexer, String[] modeNames);

    public Formatter toFormatter(AntlrFormatterProvider prov) {
        return FMT_CONVERT.apply(prov);
    }
}

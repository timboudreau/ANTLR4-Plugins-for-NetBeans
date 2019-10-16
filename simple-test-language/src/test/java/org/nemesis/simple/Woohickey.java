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
package org.nemesis.simple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.antlr.runtime.RecognitionException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerInterpreter;
import org.antlr.v4.runtime.ParserInterpreter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ATNSerializer;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.nemesis.simple.language.SimpleLanguageParser;

/**
 *
 * @author Tim Boudreau
 */
public class Woohickey {

    public static void main(String[] args) throws IOException, RecognitionException {
        doIt(SampleFiles.FORMATTING_TUTORIAL);
    }

    static void doIt(SampleFiles f) throws IOException, RecognitionException {
        ATN atn = SimpleLanguageParser._ATN;
        String[] channelNames = SimpleLanguageLexer.channelNames;
        String[] ruleNames = SimpleLanguageLexer.ruleNames;
        Set<String> modes = new LinkedHashSet<>(Arrays.asList(SimpleLanguageLexer.modeNames));

        Lexer lex = f.lexer();
//        atn = lex.getATN();
//        LexerInterpreter li = lexerInterpreter("minimal", atn, channelNames, SimpleLanguageLexer.VOCABULARY,
//                ruleNames, modes, SampleFiles.ABSURDLY_MINIMAL.charStream());

//        atn = SimpleLanguageParser._ATN;
//        ParserInterpreter pi = createParserInterpreter("minimal", atn, ruleNames, SimpleLanguageLexer.VOCABULARY, new CommonTokenStream(li));
        ParserInterpreter pi = createParserInterpreter("minimal", atn, ruleNames,
                SimpleLanguageLexer.VOCABULARY, new CommonTokenStream(lex));
//        pi.setTrace(true);
        pi.setTrimParseTree(true);

        ParserRuleContext res = pi.parse(0);
        System.out.println("RES " + res);
    }

    static LexerInterpreter lexerInterpreter(String fileName, ATN atn, String[] channelNames, Vocabulary vocab, String[] ruleNames, Set<String> modes, CharStream input) {
//        char[] serializedAtn = ATNSerializer.getSerializedAsChars(atn);
//        ATN deserialized = new ATNDeserializer().deserialize(serializedAtn);
        ATN deserialized = atn;
        List<String> allChannels = new ArrayList<String>();
        allChannels.add("DEFAULT_TOKEN_CHANNEL");
        allChannels.add("HIDDEN");
        allChannels.addAll(Arrays.asList(channelNames));
        return new LexerInterpreter(fileName, vocab, Arrays.asList(ruleNames),
                allChannels, modes, deserialized, input);
    }

    public static ParserInterpreter createParserInterpreter(String fileName, ATN atn, String[] ruleNames, Vocabulary vocab, TokenStream tokenStream) {
        char[] serializedAtn = ATNSerializer.getSerializedAsChars(atn);
        ATN deserialized = new ATNDeserializer().deserialize(serializedAtn);
        return new PI(fileName, vocab,
                Arrays.asList(ruleNames), deserialized, tokenStream);
    }

    interface ParseTranscriber {

        void enterRule(int ruleIndex, int tokenIndex);

        void decision(int decision, int stateNumber, int ruleIndex,
                boolean epsilonOnly, int[] nextTokenWithinRule, int tokenIndex);

        void state(int stateNumber, boolean epsilonOnly, int ruleIndex, int[] nextTokenWithinRule, int tokenIndex);

        void exitRule(int tokenIndex);

        void recurse(int state, int ruleIndex, int precedence, int index);

        public void ruleStop(int ruleIndex, int stateNumber, boolean epsilonOnlyTransitions, int index);
    }

    static class PT implements ParseTranscriber {

        private final TokenStream str;

        public PT(TokenStream str) {
            this.str = str;
        }

        @Override
        public void ruleStop(int ruleIndex, int stateNumber, boolean epsilonOnlyTransitions, int tokenIndex) {

        }

        enum Events {
            ENTER_RULE,
            EXIT_RULE

        }

        @Override
        public void enterRule(int ruleIndex, int tokenIndex) {
        }

        public void decision(int decision, int stateNumber, int ruleIndex,
                boolean epsilonOnly, int[] nextTokenWithinRule, int tokenIndex) {

        }

        @Override
        public void state(int stateNumber, boolean epsilonOnly, int ruleIndex, int[] nextTokenWithinRule, int tokenIndex) {

        }

        @Override
        public void exitRule(int tokenIndex) {
        }

        @Override
        public void recurse(int state, int ruleIndex, int precedence, int index) {
        }
    }

    static class PI extends ParserInterpreter {

        private ParseTranscriber scribe;

        public PI(String grammarFileName, Vocabulary vocabulary, Collection<String> ruleNames, ATN atn, TokenStream input) {
            super(grammarFileName, vocabulary, ruleNames, atn, input);
        }

        private String tok() {
            return this._input.index() + " - " + this._input.get(this._input.index()) + "";
        }

        @Override
        protected void visitRuleStopState(ATNState p) {
            scribe.ruleStop(p.ruleIndex, p.stateNumber, p.epsilonOnlyTransitions, _input.index());
            System.out.println("visitRuleStopState " + SimpleLanguageParser.ruleNames[p.ruleIndex] + " " + tok());
            super.visitRuleStopState(p);
        }

        @Override
        public ParseInfo getParseInfo() {
            return super.getParseInfo(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void exitRule() {
            scribe.exitRule(_input.index());
            super.exitRule(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void enterRule(ParserRuleContext localctx, int state, int ruleIndex) {
            scribe.enterRule(ruleIndex, _input.index());
            System.out.println("ENTER RULE " + SimpleLanguageParser.ruleNames[ruleIndex]);
            super.enterRule(localctx, state, ruleIndex); //To change body of generated methods, choose Tools | Templates.
        }

        static String tokenIntervals(IntervalSet set) {
            if (set == null) {
                return "<none>";
            }
            StringBuilder sb = new StringBuilder();
            for (int i : set.toArray()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(SimpleLanguageLexer.VOCABULARY.getSymbolicName(i));
            }
            return sb.toString();
        }

        static String a2s(Transition[] xit) {
            if (xit == null) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < xit.length; i++) {
                Transition t = xit[i];
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("target ").append(t.target).append(" ");
                sb.append(t.isEpsilon() ? "epsilon " : "non-epsilon ");
                sb.append("serType ").append(t.getSerializationType());
                sb.append(" label ").append(t.label());
            }
            return sb.insert(0, '[').append(']').toString();
        }

        @Override
        protected int visitDecisionState(DecisionState p) {
            Transition t = null;

            scribe.decision(p.decision, p.stateNumber, p.ruleIndex,
                    p.epsilonOnlyTransitions, p.nextTokenWithinRule == null ? new int[0] : p.nextTokenWithinRule.toArray(),
                    _input.index());
            System.out.println("visitDecisionState " + p
                    + " stateNum " + p.stateNumber
                    + " nextTokens [" + tokenIntervals(p.nextTokenWithinRule)
                    + "] decision " + (p.decision < 0 ? p.decision : p.decision)
                    + " rule " + SimpleLanguageParser.ruleNames[p.ruleIndex]
                    + " xit " + a2s(p.getTransitions())
                    + " "
                    + tok());
            int result = super.visitDecisionState(p);
            return result;
        }

        @Override
        protected void visitState(ATNState p) {
            scribe.state(p.stateNumber, p.epsilonOnlyTransitions, p.ruleIndex,
                    p.nextTokenWithinRule == null ? new int[0] : p.nextTokenWithinRule.toArray(), _input.index());
            if (p.nextTokenWithinRule != null) {
                System.out.println("visitState " + p + " nextTokenWithinRule "
                        + tokenIntervals(p.nextTokenWithinRule) + " " + tok());
            }
            super.visitState(p);
        }

        @Override
        public void enterRecursionRule(ParserRuleContext localctx, int state, int ruleIndex, int precedence) {
            scribe.recurse(state, ruleIndex, precedence, _input.index());
            System.out.println("enterRecursionRule " + localctx + " " + state + " " + ruleNames[ruleIndex] + " " + precedence + " " + tok());
            super.enterRecursionRule(localctx, state, ruleIndex, precedence);
        }

        @Override
        public ParserRuleContext parse(int startRuleIndex) {
            System.out.println("parse " + startRuleIndex);
            return super.parse(startRuleIndex);
        }

        @Override
        public IntervalSet getExpectedTokens() {
            IntervalSet result = super.getExpectedTokens();
            System.out.println("getExpectedTokens " + result + " " + tok());
            for (int i : result.toArray()) {
                System.out.println("  exp " + SimpleLanguageLexer.VOCABULARY.getSymbolicName(i));
            }
            return result;
        }

        @Override
        public void setContext(ParserRuleContext ctx) {
            System.out.println("setContext " + ctx);
            super.setContext(ctx);
        }

        @Override
        public void pushNewRecursionContext(ParserRuleContext localctx, int state, int ruleIndex) {
            System.out.println("pushNewRecursionContext " + localctx + " " + state + " " + ruleIndex + " " + tok());
            super.pushNewRecursionContext(localctx, state, ruleIndex);
        }

        @Override
        public boolean sempred(RuleContext _localctx, int ruleIndex, int actionIndex) {
            boolean result = super.sempred(_localctx, ruleIndex, actionIndex);
            System.out.println("sempred " + result + " for " + _localctx + " " + ruleIndex + " " + actionIndex);
            return result;
        }

        @Override
        public void setInterpreter(ParserATNSimulator interpreter) {
            System.out.println("setInterpreter " + interpreter);
            super.setInterpreter(interpreter);
        }
    }
}

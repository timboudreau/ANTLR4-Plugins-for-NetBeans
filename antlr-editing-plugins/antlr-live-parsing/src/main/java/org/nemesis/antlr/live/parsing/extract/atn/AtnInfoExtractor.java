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
package org.nemesis.antlr.live.parsing.extract.atn;

import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntSet;
import ignoreme.placeholder.DummyLanguageLexer;
import ignoreme.placeholder.DummyLanguageParser;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.PredicateTransition;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 *
 * @author Tim Boudreau
 */
public class AtnInfoExtractor {

    private final Parser parser;
    private final Vocabulary vocabulary;
    private final ATN atn;
    private final String[] ruleNames;
    private int tokenStartIndex;
    private final List<Token> tokens = new LinkedList<>();

    public static void extract(String s, int charPosition) {
        CharStream stream = CharStreams.fromString(s);
        DummyLanguageLexer lex = new DummyLanguageLexer(stream);
        CommonTokenStream cts = new CommonTokenStream(lex);
        DummyLanguageParser parser = new DummyLanguageParser(cts);
        ATN atn = parser.getATN();
        Vocabulary vocabulary = parser.getVocabulary();
        String[] ruleNames = parser.getRuleNames();
    }

    AtnInfoExtractor(Parser parser) {
        this.parser = parser;
        this.atn = parser.getATN();
        this.vocabulary = parser.getVocabulary();
        this.ruleNames = parser.getRuleNames();
    }

    public static class PipelineEntry {

        public PipelineEntry(ATNState state, Integer tokenIndex) {
            this.state = state;
            this.tokenIndex = tokenIndex;
        }

        ATNState state;
        Integer tokenIndex;
    }

    interface Transcript {

        void enterRule(int id);

        void enterToken(Token token);

        void transition(Transition transition);
    }

    public void collectCandidates(int caretTokenIndex, ParserRuleContext context) {

        this.tokenStartIndex = context != null && context.start != null ? context.start.getTokenIndex() : 0;
        TokenStream tokenStream = this.parser.getInputStream();

        int currentIndex = tokenStream.index();
        tokenStream.seek(Math.max(0, this.tokenStartIndex));
        int offset = 1;
        while (true) {
            Token token = tokenStream.LT(offset++);
            this.tokens.add(token);
            if (token.getTokenIndex() >= caretTokenIndex || token.getType() == Token.EOF) {
                break;
            }
        }
        tokenStream.seek(Math.max(0, currentIndex));

        IntList callStack = IntList.create(24);
        int startRule = context != null ? context.getRuleIndex() : 0;
        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "\n");

        tokenStream.seek(Math.max(0, currentIndex));

        // now post-process the rule candidates and find the last occurrences
        // of each preferred rule and extract its start and end in the input stream
//        for (int ruleId = 0; ruleId < parser.getRuleNames().length; ruleId++) {
//            final IntMap<IntSet> shortcut = shortcutMap.get(ruleId);
//
//            // select the right-most occurrence
//            final int startToken = shortcut.highestKey();
//            final IntSet endSet = shortcut.get(startToken);
//            final int endToken;
//            if (endSet.isEmpty()) {
//                endToken = tokens.size() - 1;
//            } else {
//                endToken = shortcut.get(startToken).last();
//            }
//
//            final int startOffset = tokens.get(startToken).getStartIndex();
//            final int endOffset;
//            if (tokens.get(endToken).getType() == Token.EOF) {
//                // if last token is EOF, include trailing whitespace
//                endOffset = tokens.get(endToken).getStartIndex();
//            } else {
//                // if last token is not EOF, limit to matching tokens which excludes trailing whitespace
//                endOffset = tokens.get(endToken - 1).getStopIndex() + 1;
//            }
//
//            candidates.rulePositions.put(ruleId, startOffset, endOffset);
//        }
    }

    /**
     * Check if the predicate associated with the given transition evaluates to
     * true.
     */
    private boolean checkPredicate(PredicateTransition transition) {
        return transition.getPredicate().eval(this.parser, ParserRuleContext.EMPTY);
    }

    /**
     * Walks the rule chain upwards to see if that matches any of the preferred
     * rules. If found, that rule is added to the collection candidates and true
     * is returned.
     */
    private boolean translateToRuleIndex(IntList ruleStack) {
        // Loop over the rule stack from highest to lowest rule level. This way we properly handle the higher rule
        // if it contains a lower one that is also a preferred rule.
        for (int i = 0; i < ruleStack.size(); ++i) {
            // Add the rule to our candidates list along with the current rule path,
            // but only if there isn't already an entry like that.
            IntList path = ruleStack.subList(0, i);
            boolean[] addNew = new boolean[]{true};

            final int ix = i;
            this.candidates.rules.forSome((key, list) -> {
                boolean result = true;
                if (key != ruleStack.get(ix) || list.size() != path.size()) {
                    return true;
                }
                if (path.equals(list)) {
                    addNew[0] = false;
                    return false;
                }
                return result;
            });

            if (addNew[0]) {
                this.candidates.rules.put(ruleStack.get(i), path);
            }
            return true;
        }

        return false;
    }

    /**
     * This method follows the given transition and collects all symbols within
     * the same rule that directly follow it without intermediate transitions to
     * other rules and only if there is a single symbol for a transition.
     */
    private IntSet getFollowingTokens(Transition initialTransition) {
        IntSet result = IntSet.create(5);
        LinkedList<ATNState> pipeline = new LinkedList<>();
        pipeline.add(initialTransition.target);

        while (!pipeline.isEmpty()) {
            ATNState state = pipeline.removeLast();

            for (Transition transition : state.getTransitions()) {
                if (transition.getSerializationType() == Transition.ATOM) {
                    if (!transition.isEpsilon()) {
                        List<Integer> list = transition.label().toList();
                        if (list != null && list.size() == 1) {
                            result.add(list.get(0));
                            pipeline.addLast(transition.target);
                        }
                    } else {
                        pipeline.addLast(transition.target);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Entry point for the recursive follow set collection function.
     */
    private LinkedList<FollowSetWithPath> determineFollowSets(ATNState start, ATNState stop) {
        LinkedList<FollowSetWithPath> result = new LinkedList<>();
        Set<ATNState> seen = new HashSet<>();
        IntList ruleStack = IntList.create(5);

        this.collectFollowSets(start, stop, result, seen, ruleStack);

        return result;
    }

    /**
     * Collects possible tokens which could be matched following the given ATN
     * state. This is essentially the same algorithm as used in the LL1Analyzer
     * class, but here we consider predicates also and use no parser rule
     * context.
     */
    private void collectFollowSets(ATNState s, ATNState stopState, LinkedList<FollowSetWithPath> followSets,
            Set<ATNState> seen, IntList ruleStack) {

        if (seen.contains(s)) {
            return;
        }

        seen.add(s);

        if (s.equals(stopState) || s.getStateType() == ATNState.RULE_STOP) {
            FollowSetWithPath set = new FollowSetWithPath();
            set.intervals = IntervalSet.of(Token.EPSILON);
//            set.path = new LinkedList<Integer>(ruleStack);
            set.path = IntList.create(ruleStack);
            followSets.addLast(set);
            return;
        }

        for (Transition transition : s.getTransitions()) {
            if (transition.getSerializationType() == Transition.RULE) {
                RuleTransition ruleTransition = (RuleTransition) transition;
                if (ruleStack.indexOf(ruleTransition.target.ruleIndex) != -1) {
                    continue;
                }
                ruleStack.add(ruleTransition.target.ruleIndex);
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
                ruleStack.removeLast();

            } else if (transition.getSerializationType() == Transition.PREDICATE) {
                if (this.checkPredicate((PredicateTransition) transition)) {
                    this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
                }
            } else if (transition.isEpsilon()) {
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
            } else if (transition.getSerializationType() == Transition.WILDCARD) {
                FollowSetWithPath set = new FollowSetWithPath();
                set.intervals = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType);
                set.path = IntList.create(ruleStack);
                followSets.addLast(set);
            } else {
                IntervalSet label = transition.label();
                if (label != null && label.size() > 0) {
                    if (transition.getSerializationType() == Transition.NOT_SET) {
                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                    }
                    FollowSetWithPath set = new FollowSetWithPath();
                    set.intervals = label;
                    set.path = IntList.create(ruleStack);
                    set.following = IntSet.create(this.getFollowingTokens(transition));
                    followSets.addLast(set);
                }
            }
        }
    }

    /**
     * Walks the ATN for a single rule only. It returns the token stream
     * position for each path that could be matched in this rule. The result can
     * be empty in case we hit only non-epsilon transitions that didn't match
     * the current input or if we hit the caret position.
     */
    private IntSet processRule(ATNState startState, int tokenIndex, IntList callStack, String indentation) {
        // Start with rule specific handling before going into the ATN walk.
        // Check first if we've taken this path with the same input before.
        IntSet result = IntSet.create(5);

        // For rule start states we determine and cache the follow set, which gives us 3 advantages:
        // 1) We can quickly check if a symbol would be matched when we follow that rule. We can so check in advance
        //    and can save us all the intermediate steps if there is no match.
        // 2) We'll have all symbols that are collectable already together when we are at the caret when entering a rule.
        // 3) We get this lookup for free with any 2nd or further visit of the same rule, which often happens
        //    in non trivial grammars, especially with (recursive) expressions and of course when invoking code completion
        //    multiple times.
        callStack.add(startState.ruleIndex);
        int currentSymbol = this.tokens.get(tokenIndex).getType();

        if (tokenIndex >= this.tokens.size() - 1) { // At caret?
            // No need to go deeper when collecting entries and we reach a rule that we want to collect anyway.
            this.translateToRuleIndex(callStack);
        } else {
            // Convert all follow sets to either single symbols or their associated preferred rule and add
            // the result to our candidates list.
            for (FollowSetWithPath set : followSets.sets) {
                IntList fullPath = IntList.create(callStack);
                fullPath.addAll(set.path);
                if (!this.translateToRuleIndex(fullPath)) {
                    for (int symbol : set.intervals.toList()) {
                        if (!this.candidates.tokens.containsKey(symbol) && set.following != null) {
                            this.candidates.tokens.put(symbol, set.following); // Following is empty if there is more than one entry in the set.
                        } else {
                            // More than one following list for the same symbol.
                            if (!this.candidates.tokens.get(symbol).equals(set.following)) { // XXX js uses !=
                                this.candidates.tokens.put(symbol, IntSet.create(5));
                            }
                        }
                    }
                }
            }

            callStack.removeLast();
            return result;
        }else {
            // Process the rule if we either could pass it without consuming anything (epsilon transition)
            // or if the current input symbol will be matched somewhere after this entry point.
            // Otherwise stop here.
            if (followSets.combined != null) {
                if (!followSets.combined.contains(Token.EPSILON) && !followSets.combined.contains(currentSymbol)) {
                    callStack.removeLast();
                    return result;
                }
            }
        }

        // The current state execution pipeline contains all yet-to-be-processed ATN states in this rule.
        // For each such state we store the token index + a list of rules that lead to it.
        LinkedList<PipelineEntry> statePipeline = new LinkedList<>();
        PipelineEntry currentEntry;

        // Bootstrap the pipeline.
        statePipeline.add(new PipelineEntry(startState, tokenIndex));

        while (!statePipeline.isEmpty()) {
            currentEntry = statePipeline.removeLast();

            currentSymbol = this.tokens.get(currentEntry.tokenIndex).getType();

            boolean atCaret = currentEntry.tokenIndex >= this.tokens.size() - 1;

            switch (currentEntry.state.getStateType()) {
                case ATNState.RULE_START: // Happens only for the first state in this rule, not subrules.
                    indentation += "  ";
                    break;

                case ATNState.RULE_STOP: {
                    // Record the token index we are at, to report it to the caller.
                    result.add(currentEntry.tokenIndex);
                    continue;
                }

                default:
                    break;
            }

            Transition[] transitions = currentEntry.state.getTransitions();
            for (Transition transition : transitions) {
                switch (transition.getSerializationType()) {
                    case Transition.RULE: {
                        IntSet endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation);
                        endStatus.forEach((int position) -> {
                            statePipeline.addLast(new PipelineEntry(((RuleTransition) transition).followState, position));
                        });
                        break;
                    }

                    case Transition.PREDICATE: {
                        if (this.checkPredicate((PredicateTransition) transition)) {
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex));
                        }
                        break;
                    }

                    case Transition.WILDCARD: {
                        if (atCaret) {
                            if (!this.translateToRuleIndex(callStack)) {
                                for (Integer token : IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType).toList()) {
                                    this.candidates.tokens.put(token, IntSet.create(5));
                                }
                            }
                        } else {
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                        }
                        break;
                    }

                    default: {
                        if (transition.isEpsilon()) {
                            if (atCaret) {
                                this.translateToRuleIndex(callStack);
                            }
                            // Jump over simple states with a single outgoing epsilon transition.
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex));
                            continue;
                        }

                        IntervalSet set = transition.label();
                        if (set != null && set.size() > 0) {
                            if (transition.getSerializationType() == Transition.NOT_SET) {
                                set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                            }
                            if (atCaret) {
                                if (!this.translateToRuleIndex(callStack)) {
                                    List<Integer> list = set.toList();
                                    boolean addFollowing = list.size() == 1;
                                    for (Integer symbol : list) {
                                        if (addFollowing) {
                                            this.candidates.tokens.put(symbol, this.getFollowingTokens(transition));
                                        } else {
                                            this.candidates.tokens.put(symbol, IntSet.create(5));
                                        }
                                    }
                                }
                            } else {
                                if (set.contains(currentSymbol)) {
                                    statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                                }
                            }
                        }
                    }
                }
            }
        }

        callStack.removeLast();

        return result;
    }
}

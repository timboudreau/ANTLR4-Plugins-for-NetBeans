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
package com.mastfrog.antlr.cc;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.PredicateTransition;
import org.antlr.v4.runtime.atn.RuleStopState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.IntervalSet;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.collections.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.antlr.v4.runtime.misc.IntegerList;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Borrowed from
 * https://raw.githubusercontent.com/mike-lischke/antlr4-c3/master/ports/java/src/main/java/com/vmware/antlr4c3/CodeCompletionCore.java
 * and heavily modified for performance - in a benchmark of 1000 runs on
 * different requests, performance is 3.4x that of the original with a much
 * smaller memory footprint / lower gc pressure.
 *
 * Port of antlr-c3 javascript library to java
 * <p>
 * The c3 engine is able to provide code completion candidates useful for
 * editors with ANTLR generated parsers, independent of the actual
 * language/grammar used for the generation.
 */
public final class CodeCompletionCore {

    private static final Logger logger = Logger.getLogger(CodeCompletionCore.class.getName());

    public static class PipelineEntry {

        public PipelineEntry(ATNState state, int tokenIndex) {
            this.state = state;
            this.tokenIndex = tokenIndex;
        }

        final ATNState state;
        final int tokenIndex;

        @Override
        public String toString() {
            return "PE(" + tokenIndex + " - " + state.ruleIndex + ":" + state.stateNumber + ":" + state.nextTokenWithinRule + ")";
        }
    }

    private static final int ITERATION_LIMIT = 15000;
    private final boolean showDebugOutput = false;
    private final boolean debugOutputWithTransitions = false;
    private boolean showRuleStack = false;

    private final IntPredicate ignoredTokens;
    private final IntPredicate preferredRules;

    private final Parser parser;
    private final ATN atn;
    private final Vocabulary vocabulary;
    private final String[] ruleNames;
    private List<? extends Token> tokens;

    private int tokenStartIndex = 0;
    private int statesProcessed = 0;
    private final Map<String, IntMap<FollowSetsHolder>> cache;

    // A mapping of rule index to token stream position to end token positions.
    // A rule which has been visited before with the same input position will always produce the same output positions.
    private final IntMap<IntMap<IntSet>> shortcutMap = IntMap.create(50, true, () -> {
        return IntMap.create(64, true, () -> IntSet.arrayBased(24));
    });
    private static final String[] atnStateTypeMap = new String[]{
        "invalid",
        "basic",
        "rule start",
        "block start",
        "plus block start",
        "star block start",
        "token start",
        "rule stop",
        "block end",
        "star loop back",
        "star loop entry",
        "plus loop back",
        "loop end"
    };

    private final CandidatesCollection candidates = new CandidatesCollection(); // The collected candidates (rules and tokens).

    // XXX this should be held by the completion provider
    public CodeCompletionCore(Parser parser, IntPredicate preferredRules,
            IntPredicate ignoredTokens,
            Map<String, IntMap<FollowSetsHolder>> followSetsByATN) {
        this.parser = parser;
        this.cache = followSetsByATN;
        candidates.clear();
        this.atn = parser.getATN();
        this.vocabulary = parser.getVocabulary();
        this.ruleNames = parser.getRuleNames();
        if (preferredRules != null) {
            this.preferredRules = preferredRules;
        } else {
            this.preferredRules = ignored -> false;
        }
        if (ignoredTokens != null) {
            this.ignoredTokens = ignoredTokens;
        } else {
            this.ignoredTokens = ignored -> false;
        }
    }

    private static int highestNonEmptyKey(IntMap<IntSet> m) {
        if (m.isEmpty()) {
            return -1;
        } else if (m.size() == 1) {
            if (m.get(m.leastKey()).isEmpty()) {
                return -1;
            } else {
                return m.leastKey();
            }
        }
        int ix = m.greatestKey();
        while (ix >= 0) {
            IntSet is = m.get(ix);
            if (!is.isEmpty()) {
                return ix;
            }
            ix = m.nearestKey(ix - 1, true);
        }
        return -1;
    }

    /**
     * This is the main entry point. The caret token index specifies the token
     * stream index for the token which currently covers the caret (or any other
     * position you want to get code completion candidates for). Optionally you
     * can pass in a parser rule context which limits the ATN walk to only that
     * or called rules. This can significantly speed up the retrieval process
     * but might miss some candidates (if they are outside of the given
     * context).
     */
    public CandidatesCollection collectCandidates(int caretTokenIndex, ParserRuleContext context) {
        return collectCandidates(caretTokenIndex, context, null);
    }

    private List<? extends Token> computeTokenList(int start, int caretTokenIndex, List<? extends Token> orig, TokenStream tokenStream) {
        if (orig != null) {
            if (orig.size() > caretTokenIndex + 1) {
                return orig.subList(0, caretTokenIndex + 1);
            }
        }
        List<Token> toks = new ArrayList<>(caretTokenIndex + 1);
        int negativeIndicesSeen = 0;
        int offset = 1;
        int originalStreamIndex = tokenStream.index();
        if (originalStreamIndex != 0) {
            tokenStream.seek(Math.max(0, this.tokenStartIndex));
        }
        try {
            for (;;) {
                Token token = tokenStream.LT(offset++);
                CCLog.log(this, token);
                toks.add(token);
                if (token.getTokenIndex() >= caretTokenIndex || token.getType() == Token.EOF) {
                    break;
                }
            }
            if (negativeIndicesSeen > 1) {
                throw new IllegalStateException("Saw " + negativeIndicesSeen + " tokens "
                        + "with a token index < 0 - this stream is not composed of "
                        + "CommonToken instances, and completion will not work.");
            }
        } finally {
            if (originalStreamIndex < 0) {
                tokenStream.seek(0);
            } else {
                tokenStream.seek(originalStreamIndex);
            }
        }
        return toks;
    }

    void reset() {
        assert Thread.holdsLock(this);
        candidates.clear();
        this.statesProcessed = 0;
        parser.reset();
        this.shortcutMap.clear();
    }

    public synchronized CandidatesCollection collectCandidates(int caretTokenIndex, ParserRuleContext context, List<? extends Token> tks) {
        reset();
        context = null;
        this.tokenStartIndex = context != null ? context.start.getTokenIndex() : 0;
        TokenStream tokenStream = this.parser.getInputStream();

        int originalTokenStreamPosition = tokenStream.index();
        if (originalTokenStreamPosition != 0) {
            tokenStream.seek(Math.max(0, this.tokenStartIndex));
        }

        this.tokens = computeTokenList(tokenStartIndex, caretTokenIndex, tks, tokenStream);
        assert this.tokens != null : "null tokens";

        CCLog.log(this, "Start", (this.tokens.isEmpty() ? "emptytokens" : this.tokens.get(this.tokens.size() - 1)), " toks ", this.tokens.size());
        IntList callStack = IntList.create(ruleNames.length);
        int startRule = context != null ? context.getRuleIndex() : 0;

        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "\n");

        // now post-process the rule candidates and find the last occurrences
        // of each preferred rule and extract its start and end in the input stream
        for (int ruleId = 0; ruleId < parser.getRuleNames().length; ruleId++) {
            if (!preferredRules.test(ruleId) || !shortcutMap.containsKey(ruleId)) {
                continue;
            }
            final IntMap<IntSet> shortcut = shortcutMap.get(ruleId);
            if (shortcut == null || shortcut.isEmpty()) {
                continue;
            }

            // select the right-most occurrence
            final int startToken = highestNonEmptyKey(shortcut);
            if (startToken == -1) {
                continue;
            }
            final IntSet endSet = shortcut.get(startToken);
            final int endToken;
            if (endSet.isEmpty()) {
                endToken = tokens.size() - 1;
            } else {
                endToken = endSet.last();
            }
            final int startOffset = tokens.get(startToken).getStartIndex();
            final int endOffset;
            if (tokens.get(endToken).getType() == Token.EOF) {
                // if last token is EOF, include trailing whitespace
                endOffset = tokens.get(endToken).getStartIndex();
            } else {
                // if last token is not EOF, limit to matching tokens which excludes trailing whitespace
                endOffset = tokens.get(endToken - 1).getStopIndex() + 1;
            }
            candidates.rulePositions.put(ruleId, startOffset, endOffset);
//            assert candidates.rulePositions.containsKey(ruleId);
//            assert candidates.rulePositions.get(ruleId) != null : "" + ruleId + " in " + candidates.rulePositions.get(ruleId);
//            assert candidates.rulePositions.get(ruleId).contains(startOffset);
//            assert candidates.rulePositions.get(ruleId).contains(endOffset);
        }

        if (logger.isLoggable(Level.FINE)) {
            StringBuilder logMessage = new StringBuilder();

            logMessage.append("States processed: ").append(this.statesProcessed).append("\n");

            logMessage.append("Collected rules:\n");

            candidates.rules.forEach((key, list) -> {
                String rid = this.ruleNames[key];
                logMessage.append("  ").append(rid).append(", path: ");
                list.forEach((int token) -> {
                    logMessage.append(this.ruleNames[token]).append(" ");
                });
                logMessage.append("\n");
            });

            logMessage.append("Collected Tokens:\n");
            candidates.tokens.forEach((key, set) -> {
                logMessage.append("  ").append(this.vocabulary.getDisplayName(key));
                set.forEachInt((int following) -> {
                    logMessage.append(" ").append(this.vocabulary.getDisplayName(following));
                });
                logMessage.append("\n");
            });
            logger.log(Level.FINE, logMessage.toString());
        }
        this.tokens = Collections.emptyList();
        return this.candidates;
    }

    /**
     * Check if the predicate associated with the given transition evaluates to
     * true.
     */
    private boolean checkPredicate(PredicateTransition transition) {
        // XXX shouldn't this use the real parser rule context?
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
        boolean result = false;
        for (int i = 0; i < ruleStack.size(); ++i) {
            // FIXME the change to set the result rather than
            // simply return true at the line result = true results in getting
            // complete results - which is good, we no longer abort at the outermost
            // preferred rule - but also winds up with the paths returned
            // containing multiple copies of the path from the top of the file,
            // e.g.
            // grammarFile modeSpec tokenRuleSpec tokenRuleDefinition lexerRuleBlock lexerRuleAlt grammarFile ...
            int currentRule = ruleStack.get(i);
            if (this.preferredRules.test(currentRule)) {
                // Add the rule to our candidates list along with the current rule path,
                // but only if there isn't already an entry like that.
                IntList path = ruleStack.subList(0, i);
                IntList existingPath = candidates.rules.containsKey(currentRule)
                        ? candidates.rules.get(currentRule) : null;
                if (existingPath == null || !existingPath.equals(path)) {
                    candidates.rules.put(currentRule, path);
//                    assert this.candidates.rules.containsKey(rule) : "absent key " + rule;
//                    assert this.candidates.rules.get(rule) != null : "absent after put: " + rule + " with " + path;
//                    assert this.candidates.rules.get(rule).containsAll(path) : "items for " + rule + " absent after put which should add all: "
//                            + this.candidates.rules.get(rule) + " missing some of " + path;

                    if (showDebugOutput && logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "=====> collected: {0}", this.ruleNames[i]);
                    }
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * This method follows the given transition and collects all symbols within
     * the same rule that directly follow it without intermediate transitions to
     * other rules and only if there is a single symbol for a transition.
     */
    private final Map<Transition, IntList> ftCache = new HashMap<>(128);

    private IntList getFollowingTokens(Transition initialTransition) {
        return ftCache.computeIfAbsent(initialTransition, this::_getFollowingTokens);
    }

    private IntList _getFollowingTokens(Transition initialTransition) {
        IntList result = IntList.create(vocabulary.getMaxTokenType());
        LinkedList<ATNState> pipeline = new LinkedList<>();
        pipeline.add(initialTransition.target);
        while (!pipeline.isEmpty()) {
            ATNState state = pipeline.removeLast();

            for (Transition transition : state.getTransitions()) {
                if (transition.getSerializationType() == Transition.ATOM) {
                    if (!transition.isEpsilon()) {
                        IntList cached = ftCache.get(transition);
                        if (cached != null) {
                            result.addAll(cached);
                            continue;
                        }

                        IntervalSet ivals = transition.label();
                        int first = ivals.get(0);
//                        IntegerList list = transition.label().toIntegerList();
//                        int first = list.get(0);
                        if (ivals.size() == 1 && !ignoredTokens.test(first)) {
                            result.add(first);
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
        IntList ruleStack = IntList.create(Math.max(8, parser.getRuleNames().length / 2));
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
            CCLog.log(this, "seen", s, stopState);
            return;
        }
        CCLog.log(this, "cfs", s, stopState, ruleStack);

        seen.add(s);

        if (s.equals(stopState) || s.getStateType() == ATNState.RULE_STOP) {
            FollowSetWithPath set = new FollowSetWithPath();
            set.intervals = IntervalSet.of(Token.EPSILON);
            set.path = ruleStack.copy();
            followSets.addLast(set);
            CCLog.log(this, "epstop", set.path);
            return;
        }

        IntList ruleStackCopy = null;
        for (Transition transition : s.getTransitions()) {
            if (transition.getSerializationType() == Transition.RULE) {
                RuleTransition ruleTransition = (RuleTransition) transition;
                if (ruleStack.indexOf(ruleTransition.target.ruleIndex) != -1) {
                    continue;
                }
                ruleStack.add(ruleTransition.target.ruleIndex);
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
                assert ruleStack.last() == ruleTransition.target.ruleIndex;
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
                if (ruleStackCopy == null) {
                    ruleStackCopy = ruleStack.copy();
                }
                set.path = ruleStackCopy;
                followSets.addLast(set);
            } else {
                IntervalSet label = transition.label();
                if (label != null && label.size() > 0) {
                    if (transition.getSerializationType() == Transition.NOT_SET) {
                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                    }
                    FollowSetWithPath set = new FollowSetWithPath();
                    set.intervals = label;
                    if (ruleStackCopy == null) {
                        ruleStackCopy = ruleStack.copy();
                    }
                    set.path = ruleStackCopy;
                    set.following = this.getFollowingTokens(transition);
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
        CCLog.log(this, "pr", tokenIndex, ": ", callStack);
        // Start with rule specific handling before going into the ATN walk.

        // Check first if we've taken this path with the same input before.
        IntMap<IntSet> positionMap = this.shortcutMap.get(startState.ruleIndex);

        IntSet result = positionMap.get(tokenIndex);
        if (!result.isEmpty()) {
            if (showDebugOutput) {
                logger.fine("=====> shortcut");
            }
            return result;
        }

        // For rule start states we determine and cache the follow set, which gives us 3 advantages:
        // 1) We can quickly check if a symbol would be matched when we follow that rule. We can so check in advance
        //    and can save us all the intermediate steps if there is no match.
        // 2) We'll have all symbols that are collectable already together when we are at the caret when entering a rule.
        // 3) We get this lookup for free with any 2nd or further visit of the same rule, which often happens
        //    in non trivial grammars, especially with (recursive) expressions and of course when invoking code completion
        //    multiple times.
        IntMap<FollowSetsHolder> setsPerState = cache.computeIfAbsent(this.parser.getClass().getName(),
                nm -> IntMap.create(32));

        FollowSetsHolder followSets = setsPerState.get(startState.stateNumber);
        if (followSets == null) {
            followSets = new FollowSetsHolder();
            setsPerState.put(startState.stateNumber, followSets);
            RuleStopState stop = this.atn.ruleToStopState[startState.ruleIndex];
            followSets.sets = this.determineFollowSets(startState, stop);
            CCLog.log(this, "fs", followSets.sets);
            // Sets are split by path to allow translating them to preferred rules. But for quick hit tests
            // it is also useful to have a set with all symbols combined.
            IntervalSet combined = new IntervalSet();
            for (FollowSetWithPath set : followSets.sets) {
                combined.addAll(set.intervals);
            }
            followSets.combined = combined;
        }

        callStack.add(startState.ruleIndex);
        if (tokenIndex >= this.tokens.size()) {
            return result;
        }
        int currentSymbol = this.tokens.get(tokenIndex).getType();

        if (tokenIndex >= this.tokens.size() - 1) { // At caret?
            if (this.preferredRules.test(startState.ruleIndex)) {
                // No need to go deeper when collecting entries and we reach a rule that we want to collect anyway.
                this.translateToRuleIndex(callStack);
            } else {
                coalesceFollowSets(followSets, callStack);
            }
            callStack.removeLast();
            if (!result.isEmpty()) {
                positionMap.put(tokenIndex, result);
            } else {
                positionMap.remove(tokenIndex);
            }
            return result;

        } else {
            // Process the rule if we either could pass it without consuming anything (epsilon transition)
            // or if the current input symbol will be matched somewhere after this entry point.
            // Otherwise stop here.
            if (followSets.combined != null) {
                if (!followSets.combined.contains(Token.EPSILON) && !followSets.combined.contains(currentSymbol)) {
                    callStack.removeLast();
                    if (!result.isEmpty()) {
                        positionMap.put(tokenIndex, result);
                    } else {
                        positionMap.remove(tokenIndex);
                    }
                    return result;
                }
            }
        }

        // The current state execution pipeline contains all yet-to-be-processed ATN states in this rule.
        // For each such state we store the token index + a list of rules that lead to it.
        LinkedList<PipelineEntry> statePipeline = new LinkedList<>();
        PipelineEntry currentEntry = null;

        // Bootstrap the pipeline.
        statePipeline.add(new PipelineEntry(startState, tokenIndex));

        PipelineEntry lastEntry = null;
        int count = 0;
        while (!statePipeline.isEmpty()) {
            lastEntry = currentEntry;
            currentEntry = statePipeline.removeLast();
            ++this.statesProcessed;

            if (++count > ITERATION_LIMIT) {
                break;
            }

            currentSymbol = this.tokens.get(currentEntry.tokenIndex).getType();

            boolean atCaret = currentEntry.tokenIndex >= this.tokens.size() - 1;
            if (logger.isLoggable(Level.FINE)) {
                printDescription(indentation, currentEntry.state, this.generateBaseDescription(currentEntry.state), currentEntry.tokenIndex);
                if (this.showRuleStack) {
                    printRuleState(callStack);
                }
            }

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
            processTransitions(transitions, currentEntry, callStack, indentation, statePipeline, atCaret, currentSymbol);
        }

        callStack.removeLast();

        // Cache the result, for later lookup to avoid duplicate walks.
        if (!result.isEmpty()) {
            positionMap.put(tokenIndex, result);
        } else {
            positionMap.remove(tokenIndex);
        }

        return result;
    }

    private void processTransitions(Transition[] transitions, PipelineEntry currentEntry, IntList callStack, String indentation, LinkedList<PipelineEntry> statePipeline, boolean atCaret, int currentSymbol) {
        for (Transition transition : transitions) {
            switch (transition.getSerializationType()) {
                case Transition.RULE: {
                    IntSet endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation);
                    endStatus.forEachInt((int position) -> {
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
                            IntegerList il = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType).toIntegerList();
                            for (int i = 0; i < il.size(); i++) {
                                int token = il.get(i);
                                if (!this.ignoredTokens.test(token)) {
                                    this.candidates.tokens.putNewSet(token);
//                                        assert candidates.tokens.containsKeys(token) : "no " + token + " in " + candidates.tokens.keySet();
                                }
                            }
                        }
                    } else {
                        statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                    }
                    break;
                }

                default: {
                    if (transition.isEpsilon()) {
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
                            processCaretToken(callStack, set, transition);
                        } else {
                            if (set.contains(currentSymbol)) {
                                if (showDebugOutput && logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, "=====> consumed: {0}", this.vocabulary.getDisplayName(currentSymbol));
                                }
                                statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                            }
                        }
                    }
                }
            }
        }
    }

    private void processCaretToken(IntList callStack, IntervalSet set, Transition transition) {
        if (!translateToRuleIndex(callStack)) {
            IntegerList list = set.toIntegerList();
//                                    List<Integer> list = set.toList();
            boolean addFollowing = list.size() == 1;
            for (int i = 0; i < list.size(); i++) {
                int symbol = list.get(i);
                if (!this.ignoredTokens.test(symbol)) {
                    if (showDebugOutput && logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "=====> collected: {0}", this.vocabulary.getDisplayName(symbol));
                    }
                    if (addFollowing) {
                        IntList foll = this.getFollowingTokens(transition);
                        this.candidates.tokens.putReplace(symbol, foll);
//                                                assert candidates.tokens.containsKey(symbol);
//                                                assert this.candidates.tokens.getIfPresent(symbol) != null : "" + symbol;
//                                                assert this.candidates.tokens.getIfPresent(symbol).isEmpty() == foll.isEmpty() : "sym " + symbol
//                                                        + " " + foll + " " + this.candidates.tokens.getIfPresent(symbol);
//                                                assert ((this.candidates.tokens.getIfPresent(symbol).isEmpty() && foll.isEmpty())
//                                                        || this.candidates.tokens.getIfPresent(symbol).containsAll(foll)) : " non-match "
//                                                        + this.candidates.tokens.getIfPresent(symbol) + " expected " + foll;
                    } else {
                        this.candidates.tokens.putNewSet(symbol);
//                                                assert this.candidates.tokens.containsKey(symbol) : "no key " + symbol;
//                                                assert this.candidates.tokens.getIfPresent(symbol) != null : "no " + symbol;
//                                                assert this.candidates.tokens.getIfPresent(symbol).isEmpty() : "no " + symbol;
                    }
                } else {
                    logger.log(Level.FINE, "====> collected: Ignoring token: {0}", symbol);
                }
            }
        }
    }

    private void coalesceFollowSets(FollowSetsHolder followSets, IntList callStack) {
        // Convert all follow sets to either single symbols or their associated preferred rule and add
        // the result to our candidates list.
        for (FollowSetWithPath set : followSets.sets) {
            IntList fullPath = callStack.copy();
            fullPath.addAll(set.path);
            if (!this.translateToRuleIndex(fullPath)) {
                // For the fairly common case where we will only process one
                // int, we can avoid several allocations with a small optimization:
                if (set.intervals.size() == 1) {
                    Interval ival = set.intervals.getIntervals().get(0);
                    if (ival.a == ival.b) {
                        handleFollowSet(ival.a, set);
                    } else {
                        handleFollowSet(ival.a, set);
                        handleFollowSet(ival.b, set);
                    }
                } else {
                    List<Interval> intervals = set.intervals.getIntervals();
                    int n = intervals.size();
                    for (int i = 0; i < n; i++) {
                        Interval I = intervals.get(i);
                        int a = I.a;
                        int b = I.b;
                        if (a == b) {
                            handleFollowSet(a, set);
                        } else {
                            for (int symbol = a; symbol <= b; symbol++) {
                                handleFollowSet(symbol, set);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleFollowSet(int symbol, FollowSetWithPath set) {
        if (!this.ignoredTokens.test(symbol)) {
            if (showDebugOutput && logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "=====> collected: {0}", this.vocabulary.getDisplayName(symbol));
            }
            if (!this.candidates.tokens.containsKey(symbol)) {
                if (set.following == null) {
                    set.following = IntList.create(vocabulary.getMaxTokenType());
                }
                this.candidates.tokens.put(symbol, set.following); // Following is empty if there is more than one entry in the set.
//                assert candidates.tokens.containsKey(symbol);
            } else {
                // More than one following list for the same symbol.
                if (!this.candidates.tokens.get(symbol).equals(set.following)) { // XXX js uses !=
                    this.candidates.tokens.putNewSet(symbol);
//                    assert candidates.tokens.containsKey(symbol);
//                    assert candidates.tokens.getIfPresent(symbol).isEmpty();
                }
            }
        } else {
            logger.log(Level.FINE, "====> collection: Ignoring token: {0}", symbol);
        }
    }

    private String generateBaseDescription(ATNState state) {
        String stateValue = (state.stateNumber == ATNState.INVALID_STATE_NUMBER) ? "Invalid" : Integer.toString(state.stateNumber);
        return "[" + stateValue + " " + this.atnStateTypeMap[state.getStateType()] + "] in " + this.ruleNames[state.ruleIndex];
    }

    private void printDescription(String currentIndent, ATNState state, String baseDescription, int tokenIndex) {

        StringBuilder output = new StringBuilder(currentIndent);

        StringBuilder transitionDescription = new StringBuilder();
        if (this.debugOutputWithTransitions && logger.isLoggable(Level.FINER)) {
            for (Transition transition : state.getTransitions()) {
                StringBuilder labels = new StringBuilder();
                List<Integer> symbols = (transition.label() != null) ? transition.label().toList() : new LinkedList<>();
                if (symbols.size() > 2) {
                    // Only print start and end symbols to avoid large lists in debug output.
                    labels.append(this.vocabulary.getDisplayName(symbols.get(0))).append(" .. ")
                            .append(this.vocabulary.getDisplayName(symbols.get(symbols.size() - 1)));
                } else {
                    for (Integer symbol : symbols) {
                        if (labels.length() > 0) {
                            labels.append(", ");
                        }
                        labels.append(this.vocabulary.getDisplayName(symbol));
                    }
                }
                if (labels.length() == 0) {
                    labels.append("ε");
                }
                transitionDescription.
                        append("\n").
                        append(currentIndent).
                        append("\t(").
                        append(labels).
                        append(") [").
                        append(transition.target.stateNumber).
                        append(" ").
                        append(this.atnStateTypeMap[transition.target.getStateType()]).
                        append("] in ").
                        append(this.ruleNames[transition.target.ruleIndex]);
            }

            if (tokenIndex >= this.tokens.size() - 1) {
                output.append("<<").append(this.tokenStartIndex + tokenIndex).append(">> ");
            } else {
                output.append("<").append(this.tokenStartIndex + tokenIndex).append("> ");
            }
            logger.log(Level.FINER, "{0}Current state: {1}{2}", new Object[]{output, baseDescription, transitionDescription});
        }
    }

    private void printRuleState(IntList stack) {
        if (logger.isLoggable(Level.FINEST) && stack.size() > 1) {
            if (stack.isEmpty()) {
                logger.finest("<empty stack>");
            } else {
                StringBuilder sb = new StringBuilder();
                stack.forEach((int rule) -> {
                    if (sb.length() > 0) {
                        sb.append("->");
                    }
                    sb.append(this.ruleNames[rule]);
                });
                logger.finest(sb.toString());
            }
        }
    }
}

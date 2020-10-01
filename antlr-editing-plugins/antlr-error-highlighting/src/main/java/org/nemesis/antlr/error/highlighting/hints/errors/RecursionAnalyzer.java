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
package org.nemesis.antlr.error.highlighting.hints.errors;

import com.mastfrog.antlr.utils.TreeUtils;
import com.mastfrog.function.state.Int;
import com.mastfrog.graph.ObjectPath;
import com.mastfrog.graph.StringGraph;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import static com.mastfrog.util.collections.CollectionUtils.supplierMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.ANTLRv4Parser.EbnfSuffixContext;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;

/**
 * Left-recusion is another case where the error reporting in Antlr is rather
 * anemic - it will tell you *that* some set of rules result in left-recursion,
 * but it will give no indication whatsoever as to by what path or what
 * rule-calls within those paths left-recursion is caused. We solve that here
 * by: 1. Using the rule name and references from the Extraction to create a
 * graph of what calls what that lets us easily (and cheaply!) get a graph of
 * what calls what that can give us a list of all paths through the grammar
 * between any two rules 2. Run some analysis of the grammar (not worth making
 * into an extractor since it is only needed in the relatively rare case of this
 * error) that captures the *order* in which rules are called, and whether or
 * not each one is wildcarded, split up by every top-level "alternative"
 * (non-nested clauses separated by a | character) - a graph gives you paths but
 * not sequence info. 3. For each path the potentially could be causing
 * left-recursion, examine the call sequence of each rule in it in order, and
 * see if it is really left-recursive (meaning it is the first rule called, or
 * the first rule after a series of other rules that can have the wildcard * or
 * *? and so could be absent and have the left-recursing target able to be the
 * leftmost rule matched); if it can't be left-recursive, prune it from the set
 * we're interested in. 4. When done, we have a set of NameReference objects
 * with character offsets in the document, mapped to the set of paths causing
 * left-recursion they are a part of, which we can highlight to show the user
 * *exactly* where the problem is.
 *
 * @author Tim Boudreau
 */
class RecursionAnalyzer {

    private static final Logger LOG = Logger.getLogger(RecursionAnalyzer.class.getName());

    Map<NameReference, Set<ObjectPath<String>>> analyze(GrammarFileContext tree, Extraction ext, Set<String> nms) {
        // Dummy parse, project locked or parsing infrastructure not yet initialized
        if (ext.isPlaceholder() || ext.isDisposed()) {
            return Collections.emptyMap();
        }

        NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
        NamedRegionReferenceSets<RuleTypes> refs = ext.references(AntlrKeys.RULE_NAME_REFERENCES);
        if (names == null || refs == null || names.isEmpty() || refs.isEmpty()) {
            return Collections.emptyMap();
        }
        // The graph can give us all of the paths by which rule B is reachable
        // from rule A
        StringGraph refGraph = ext.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);

        Set<String> namesOfInterest = new HashSet<>(names.size());
        Set<ObjectPath<String>> allPaths = new HashSet<>(names.size());
        namesOfInterest.addAll(nms);
        Map<NamePair, List<ObjectPath<String>>> pathsForNamePair = new HashMap<>(nms.size() * nms.size());
        // Collect all paths that connect any of the set of rule names from the error message
        // through the grammar, and collect all names that occur anywhere in those paths
        for (String name : nms) {
            for (String otherName : nms) {
                // Create a key so we can keep straight which paths we're analyzing for -
                // We may be handed a whole list of rules that indirectly call each other
                NamePair pair = new NamePair(name, otherName);
                List<ObjectPath<String>> pathsForPair = refGraph.pathsBetween(name, otherName);
                allPaths.addAll(pathsForPair);
                List<ObjectPath<String>> realPaths = new ArrayList<>();
                pathsForPair.forEach(onePath -> {
                    if (onePath != null) {
                        realPaths.add(onePath);
                        namesOfInterest.addAll(onePath.contents());
                    }
                });
                pathsForNamePair.put(pair, realPaths);
            }
        }
        // We will collect the order in which rules are called for every rule on
        // every path that goes between rules.  Note this will contain items that
        // do not exist in the reference graph, since the reference graph will not
        // contain imported tokens from other grammar files, only ones defined within
        // the scope of this one
        Map<String, Alts> callOrderings = RuleCallSequencesCollector.collectRuleCallSequences(namesOfInterest, tree);

        // This will be our result set
        Map<NameReference, Set<ObjectPath<String>>> culprits = supplierMap(TreeSet::new);
        // Now that we have both our (unordered) paths, and our rule call sequences,
        // we want to prune any path that could not possibly be left-recursion - any
        // cases where, before coming to the rule we are interested in, the path traverses
        // another rule which is not ours and is not wildcarded so that it could be
        // omitted and therefore still lead to left recursion
        for (Map.Entry<NamePair, List<ObjectPath<String>>> e : pathsForNamePair.entrySet()) {
            for (Iterator<ObjectPath<String>> iter = e.getValue().iterator(); iter.hasNext();) {
                ObjectPath<String> currPath = iter.next();
                if (!allPaths.contains(currPath)) {
                    // already encountered due to a previous run
                    // XXX is it possible for this to happen?
                    iter.remove();
                    allPaths.remove(currPath);
                } else {
                    Map<NameReference, Set<ObjectPath<String>>> nue = supplierMap(TreeSet::new);
                    // Prune the path if it is a real path between the nodes, but not
                    // one involving left-recursion because some rule along the path requires
                    // a zero-or-more-non-wildcarded prefix
                    if (walkPath(currPath, callOrderings, e.getKey(), nue)) {
                        for (Map.Entry<NameReference, Set<ObjectPath<String>>> e1 : nue.entrySet()) {
                            culprits.get(e1.getKey()).addAll(e1.getValue());
                        }
                    } else {
                        allPaths.remove(currPath);
                    }
                }
            }
        }
        return culprits;
    }

    private boolean walkPath(ObjectPath<String> path, Map<String, Alts> callOrderings,
            NamePair lookingFor, Map<NameReference, Set<ObjectPath<String>>> culprits) {
        Int altsAccepted = Int.create();
        if (lookingFor.isSelfPath() && path.size() == 2) {
            // Special case - *self* left-recursion in Antlr is legal; but
            // we do need to do the wildcard test because
            // foo : foo bar baz;
            // is legal, but
            // foo: (foo bar)* baz;
            // is NOT
            Alts alts = callOrderings.get(lookingFor.a);
            if (alts != null) {
                alts.visitAlternativesContaining(lookingFor.a, ai -> {
                    for (NameReference item : ai) {
                        // In this case, if the item is not in a block or is not
                        // flagged as a wildcard, it's either not the same item,
                        // or a case of legal self-left-recursion
                        if (lookingFor.a.equals(item.name) && item.inNestedBlock) {
                            culprits.get(item).add(path);
                            altsAccepted.increment();
                            break;
                        } else if (lookingFor.a.equals(item.name)) {
                            break;
                        } else if (!item.isWildcard()) {
                            LOG.log(Level.FINEST, "Left-Path {0} of {1} interrupted  in\n\t{2}"
                                    + "\n\tbecause {3}({4}:{5}) is not wildcarded",
                                    new Object[]{lookingFor, path, ai, item, item.start, item.end});
                            break;
                        }
                    }
                });
            }
            return altsAccepted.getAsInt() > 0;
        }
        boolean result = true;
        // Now visit each rule of the path, get the sequence of rules that rule
        // invokes;  if the next path element occurs after a reference to another
        // rule that is preceded by a non-zero-or-more-non-wildcarded rule reference.
        //
        // It is only left-recursion if the target rule is either the leftmost element
        // in the next rule. or in the entire chain of rules up to the end of the
        // path, each one is either the first element or preceded only by * or *?
        // elements that might not be present, making the target rule the leftmost
        // in the next rule that processes a token
        for (int i = 0; i < path.size() - 1; i++) {
            String curr = path.get(i);
            String next = path.get(i + 1);
            // Get the set of alternatives ( top-level |-separated clauses) for
            // the current rule
            Alts alts = callOrderings.get(curr);
            // Store the old value to see if anything changed
            int prevAltsAccepted = altsAccepted.getAsInt();
            int allAlts = alts.visitAlternativesContaining(next, ai -> {
                for (NameReference item : ai) {
                    // Found the target rule
                    if (next.equals(item.name)) {
                        // Add the name reference with its offsets to our results
                        culprits.get(item).add(path);
                        altsAccepted.increment();
                        break;
                    } else if (!item.isWildcard()) {
                        // If it's not wildcarded and it's not the target,
                        // then we have eliminated this alternative
                        LOG.log(Level.FINEST, "Left-Path {0} of {1} interrupted  in\n\t{2}"
                                + "\n\tbecause {3}({4}:{5}) is not wildcarded",
                                new Object[]{lookingFor, path, ai, item, item.start, item.end});
                        break;
                    }
                }
            });
            if (allAlts == 0) {
                // Should not happen unless the logic for computing the graph
                // or the alternative lists is broken somehow
                LOG.log(Level.WARNING, "No alternatives containing {0} in a set "
                        + "of alternatives that MUST contain it.  Something is "
                        + "wrong. Path: {1} alternatives {2}",
                        new Object[]{next, path, alts});
            }
            if (prevAltsAccepted == altsAccepted.getAsInt()) {
                LOG.log(Level.FINER, "\t\tDISCARD PATH {0} at {1}",
                        new Object[]{path, next});
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Our graph gets us paths through the grammar, which is great; but to
     * determine if something is an actual instance of left recusion, we need to
     * know if the reference to the rule is either the first atom in an
     * alternative, or the first atom in an alternative preceded only by atoms
     * with an ebnf suffix that could capture nothing, such as "?", "*" or "*?".
     * This visitor captures the sequence of rule references per alternative.
     */
    static final class RuleCallSequencesCollector extends ANTLRv4BaseVisitor<Void> {

        private final Set<String> group;
        private final List<Alts> alts = new ArrayList<>();
        private final Map<String, Alts> altsForName = new HashMap<>();
        private boolean active;
        private AltInfo currentAlt;

        public RuleCallSequencesCollector(Set<String> group) {
            this.group = group;
        }

        static Map<String, Alts> collectRuleCallSequences(Set<String> names, GrammarFileContext tree) {
            RuleCallSequencesCollector collector = new RuleCallSequencesCollector(names);
            tree.accept(collector);
            return collector.altsForName;
        }

        @Override
        public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            if (ctx.parserRuleDeclaration() != null && ctx.parserRuleDeclaration().parserRuleIdentifier() != null) {
                String name = ctx.parserRuleDeclaration().parserRuleIdentifier().getText();
                if (group.contains(name)) {
                    // If it is a rule we care about, create a new set of Alts
                    // to contain the sequence of rules called in each to-level
                    // |-separated alternative
                    active = true;
                    Alts altSet = new Alts(name);
                    alts.add(altSet);
                    altsForName.put(name, altSet);
                    // The super call will collect the contents
                    super.visitParserRuleSpec(ctx);
                    // Then reset the state
                    active = false;
                    return null;
                }
            }
            return super.visitParserRuleSpec(ctx);
        }

        @Override
        public Void visitParserRuleAtom(ANTLRv4Parser.ParserRuleAtomContext ctx) {
            if (active && currentAlt != null && ctx.terminal() != null) {
                // This lets us capture token rule calls that, in a parser grammar
                // will be in other files and therefore not in the graph or the
                // extraction
                String referenced = ctx.terminal().getText();
                ParserRuleElementContext el = TreeUtils.ancestor(ctx, ParserRuleElementContext.class);
                String ebnfString = null;
                if (el != null) {
                    ANTLRv4Parser.EbnfSuffixContext ebnf = el.ebnfSuffix();
                    if (ebnf != null) {
                        ebnfString = ebnf.getText();
                    }
                }
                currentAlt.add(referenced, ebnfString, ctx.start.getStartIndex(),
                        ctx.stop.getStopIndex() + 1, inNestedBlock);
            }
            return super.visitParserRuleAtom(ctx);
        }

        @Override
        public Void visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
            if (active && currentAlt != null && ctx.parserRuleIdentifier() != null) {
                // Collect the name and position of this rule call for use in highlighting
                String referenced = ctx.parserRuleIdentifier().getText();
                ParserRuleElementContext el = TreeUtils.ancestor(ctx, ParserRuleElementContext.class);
                String ebnfString = null;
                if (el != null) {
                    ANTLRv4Parser.EbnfSuffixContext ebnf = el.ebnfSuffix();
                    if (ebnf != null) {
                        ebnfString = ebnf.getText();
                    }
                }
                currentAlt.add(referenced, ebnfString, ctx.start.getStartIndex(),
                        ctx.stop.getStopIndex() + 1, inNestedBlock);
            }
            return super.visitParserRuleReference(ctx);
        }

        private boolean inNestedBlock;

        @Override
        public Void visitBlock(ANTLRv4Parser.BlockContext ctx) {
            if (active && currentAlt != null) {
                int sz = currentAlt.references.size();
                boolean old = inNestedBlock;
                inNestedBlock = true;
                super.visitBlock(ctx);
                inNestedBlock = old;
                if (currentAlt.references.size() > sz) {
                    // This is a tiny cheat - rather than modelling nested block
                    // contents, we simply capture the rules called within it
                    // and apply the EBNF suffix of the block to each one - whether
                    // or not it adds up to a * or *? if the only information
                    // we need
                    if (ctx.getParent() instanceof EbnfContext) {
                        EbnfContext p = (EbnfContext) ctx.getParent();
                        EbnfSuffixContext bsuff = p.ebnfSuffix();
                        if (bsuff != null) {
                            for (int i = sz; i < currentAlt.references.size(); i++) {
                                NameReference ref = currentAlt.references.get(i);
                                if (ref.ebnfString == null || ref.ebnfString.isEmpty() || ref.ebnfString.indexOf('+') >= 0) {
                                    ref.ebnfString = bsuff.getText();
                                }
                            }
                        } else if (p.getParent() instanceof ParserRuleElementContext) {
                            ParserRuleElementContext prc = (ParserRuleElementContext) p.getParent();
                            ANTLRv4Parser.EbnfSuffixContext suff = prc.ebnfSuffix();
                            if (suff != null) {
                                for (int i = sz; i < currentAlt.references.size(); i++) {
                                    NameReference ref = currentAlt.references.get(i);
                                    if (ref.ebnfString == null || ref.ebnfString.isEmpty() || ref.ebnfString.indexOf('+') >= 0) {
                                        ref.ebnfString = suff.getText();
                                    }
                                }
                            }
                        }
                    }
                }
                return null;
            }
            return super.visitBlock(ctx);
        }

        @Override
        public Void visitParserRuleAlternative(ANTLRv4Parser.ParserRuleAlternativeContext ctx) {
            if (active) {
                if (currentAlt != null) {
                    // nested - we only care about top level alternatives - the
                    // rest can get munged into the alts we're creating harmlessly,
                    // since either branch of an OR is a potential left-recursion blocker,
                    // and we will apply the ebnfs from the block to its elements
                    return super.visitParserRuleAlternative(ctx);
                }
                Alts st = alts.get(alts.size() - 1);
                // Create a new branch of this alternative of the current rule
                currentAlt = st.newAlt(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1);
                try {
                    // Everything collected within the closure of the super call
                    // will be added to currentAlt
                    return super.visitParserRuleAlternative(ctx);
                } finally {
                    // Clear the alternative when done
                    currentAlt = null;
                }
            }
            return super.visitParserRuleAlternative(ctx);
        }
    }

    /**
     * Models the set of |-separated top-level alternatives and what they call,
     * for each rule we requested capture of.
     */
    static class Alts implements Iterable<AltInfo> {

        private final String name;
        private final List<AltInfo> alts = new ArrayList<>();

        public Alts(String name) {
            this.name = name;
        }

        @Override
        public Iterator<AltInfo> iterator() {
            return alts.iterator();
        }

        int visitAlternativesContaining(String name, Consumer<AltInfo> c) {
            int result = 0;
            for (AltInfo in : alts) {
                if (in.contains(name)) {
                    c.accept(in);
                    result++;
                }
            }
            return result;
        }

        AltInfo newAlt(int start, int end) {
            AltInfo nue = new AltInfo(alts.size(), start, end);
            alts.add(nue);
            return nue;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append(":");
            alts.forEach(ai -> {
                sb.append("\n  * ").append(ai);
            });
            return sb.toString();
        }

        void visitLeftRecusingReferencesTo(String name, BiConsumer<AltInfo, NameReference> c) {
            alts.forEach(ifo -> {
                if (ifo.contains(name)) {
                    ifo.visitPossiblyLeftRecursingItems(c);
                }
            });
        }
    }

    /**
     * Models the list of rules (parser or token) called by one alternative in
     * one rule.
     */
    static class AltInfo implements Iterable<NameReference> {

        List<NameReference> references = new ArrayList<>();
        private final Set<String> allNames = new HashSet<>();
        List<NameReference> possiblyLeftRecursing;
        boolean leftRecursionNowBlocked;
        final int start;
        final int end;
        private final int index;

        public AltInfo(int index, int start, int end) {
            this.start = start;
            this.end = end;
            this.index = index;
        }

        boolean isEmpty() {
            return references.isEmpty();
        }

        boolean contains(String name) {
            return allNames.contains(name);
        }

        @Override
        public Iterator<NameReference> iterator() {
            return references.iterator();
        }

        IntRange<? extends IntRange<?>> toRange() {
            return Range.ofCoordinates(start, end);
        }

        void add(String s, String ebnfString, int start, int end, boolean inNestedBlock) {
            allNames.add(s);
            NameReference nr = new NameReference(s, ebnfString, start, end, inNestedBlock);
            references.add(nr);
        }

        void visitPossiblyLeftRecursingItems(BiConsumer<AltInfo, NameReference> r) {
            if (possiblyLeftRecursing == null) {
                possiblyLeftRecursing = new ArrayList<>();
                for (NameReference nr : references) {
                    possiblyLeftRecursing.add(nr);
                    if (!nr.isWildcard()) {
                        break;
                    }
                }
            }
            for (NameReference nr : possiblyLeftRecursing) {
                r.accept(this, nr);
            }
        }

        public NameReference leftRecursingReferenceTo(String name) {
            for (NameReference ref : references) {
                if (name.equals(ref.name)) {
                    return ref;
                }
                if (!ref.isWildcard()) {
                    break;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (NameReference a : references) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(a);
            }
            return sb.toString();
        }
    }

    /**
     * Models one rule reference within one alternative of a rule, retaining its
     * offsets within the file.
     */
    static final class NameReference implements IntRange<NameReference> {

        final String name;
        String ebnfString;
        final int start;
        final int end;
        final boolean inNestedBlock;

        NameReference(String name, String ebnfString, int start, int end, boolean inNestedBlock) {
            this.name = name;
            this.ebnfString = ebnfString;
            this.start = start;
            this.end = end;
            this.inNestedBlock = inNestedBlock;
        }

        public boolean isWildcard() {
            if (ebnfString == null) {
                return false;
            } else {
                switch (ebnfString) {
                    case "*":
                    case "*?":
                    case "?":
                        return true;
                    default:
                        return false;
                }
            }
        }

        @Override
        public String toString() {
            return name + (ebnfString == null ? "" : ebnfString)
                    + "(" + start + ":" + end + ")";
        }

        @Override
        public int start() {
            return start;
        }

        @Override
        public int size() {
            return end - start;
        }

        @Override
        public NameReference newRange(int start, int size) {
            return new NameReference(name, ebnfString, start, end, inNestedBlock);
        }

        @Override
        public NameReference newRange(long start, long size) {
            return new NameReference(name, ebnfString, (int) start, (int) (end - start), inNestedBlock);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + Objects.hashCode(this.name);
            hash = 19 * hash + Objects.hashCode(this.ebnfString);
            hash = 19 * hash + this.start;
            hash = 19 * hash + this.end;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameReference other = (NameReference) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.ebnfString, other.ebnfString);
        }
    }

    /**
     * Models start and end points of a path (which may be the same).
     */
    static final class NamePair {

        final String a;
        final String b;

        public NamePair(String a, String b) {
            this.a = a;
            this.b = b;
        }

        public boolean isSelfPath() {
            return a.equals(b);
        }

        @Override
        public String toString() {
            return a + ":" + b;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.a);
            hash = 67 * hash + Objects.hashCode(this.b);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || !(obj instanceof NamePair)) {
                return false;
            }
            NamePair other = (NamePair) obj;
            return a.equals(other.a) && b.equals(other.b);
        }
    }
}

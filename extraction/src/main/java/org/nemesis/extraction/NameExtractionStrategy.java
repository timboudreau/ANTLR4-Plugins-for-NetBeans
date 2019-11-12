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
package org.nemesis.extraction;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.data.Hashable;
import org.nemesis.data.Hashable.Hasher;

/**
 *
 * @author Tim Boudreau
 */
final class NameExtractionStrategy<R extends ParserRuleContext, T extends Enum<T>> implements Hashable {

    final Class<R> type;
    final Function<R, NamedRegionData<T>> extractor;
    final Predicate<RuleNode> ancestorQualifier;
    final T argType;
    final Function<R, List<? extends TerminalNode>> terminalFinder;
    final Predicate<ParseTree> parseTreePredicate;

    NameExtractionStrategy(Class<R> type, Function<R, NamedRegionData<T>> extractor, Predicate<RuleNode> ancestorQualifier, Predicate<ParseTree> parseTreePredicate) {
        this.type = type;
        this.extractor = extractor;
        this.ancestorQualifier = ancestorQualifier;
        this.terminalFinder = null;
        this.argType = null;
        this.parseTreePredicate = parseTreePredicate;
    }

    NameExtractionStrategy(Class<R> type, Predicate<RuleNode> ancestorQualifier, T argType, Function<R, List<? extends TerminalNode>> terminalFinder, Predicate<ParseTree> parseTreePredicate) {
        this.type = type;
        this.extractor = null;
        this.ancestorQualifier = ancestorQualifier;
        this.argType = argType;
        this.terminalFinder = terminalFinder;
        this.parseTreePredicate = parseTreePredicate;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(NameExtractionStrategy.class.getName())
                .append('@').append(System.identityHashCode(this));
        if (argType != null) {
            sb.append(':').append(argType);
        }
        if (ancestorQualifier != null) {
            sb.append("q=").append(ancestorQualifier);
        }
        sb.append("ext=").append(extractor);
        if (terminalFinder != null) {
            sb.append("term=").append(terminalFinder);
        }

        return sb.toString();
    }

    @Override
    public void hashInto(Hasher hasher) {
        hasher.writeString(type.getName());
        hasher.hashObject(extractor);
        if (ancestorQualifier != null) {
            hasher.hashObject(ancestorQualifier);
        }
        if (argType != null) {
            hasher.writeInt(argType.ordinal());
        }
        if (terminalFinder != null) {
            hasher.hashObject(terminalFinder);
        }
    }

    public int find(R node, BiPredicate<NamedRegionData<T>, TerminalNode> cons) {
        if (parseTreePredicate != null && !parseTreePredicate.test(node)) {
            return 0;
        }
        if (extractor != null) {
            boolean result = cons.test(extractor.apply(node), null);
            return result ? 1 : 0;
        } else {
            List<? extends TerminalNode> nds = terminalFinder.apply(node);
            int result = 0;
            if (nds != null) {
                for (TerminalNode tn : nds) {
                    Token tok = tn.getSymbol();
                    if (tok != null) {
                        boolean found = cons.test(new NamedRegionData<>(tn.getText(), argType, tok.getStartIndex(), tok.getStopIndex() + 1), tn);
                        if (found) {
                            result++;
                        }
                    }
                }
            }
            return result;
        }
    }
}

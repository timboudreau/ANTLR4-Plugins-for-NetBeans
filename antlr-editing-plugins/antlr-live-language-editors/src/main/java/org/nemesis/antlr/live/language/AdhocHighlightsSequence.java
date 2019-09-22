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
package org.nemesis.antlr.live.language;

import com.mastfrog.range.Coalescer;
import com.mastfrog.range.DataIntRange;
import com.mastfrog.range.Range;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocHighlightsSequence implements HighlightsSequence {

    private static final Logger LOG = Logger.getLogger(AdhocHighlightsSequence.class.getName());
    private final Iterator<DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>>> iter;
    private DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> current;

    AdhocHighlightsSequence(AdhocColorings colorings, ParseTreeProxy semantics, int length) {
        List<DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>>> ranges = new ArrayList<>(semantics.tokenCount());
        int allRuleCount = 0;
        int ruleCount = 0;
        for (AntlrProxies.ParseTreeElement el : semantics.allTreeElements()) {
            if (el.kind() == AntlrProxies.ParseTreeElementKind.RULE) {
                AntlrProxies.RuleNodeTreeElement rn = (AntlrProxies.RuleNodeTreeElement) el;
                AdhocColoring a = colorings.get(rn.name());
                allRuleCount++;
                if (a != null && a.isActive()) {
                    ruleCount++;
                    int start = rn.startTokenIndex();
                    int end = rn.endTokenIndex();
                    if (start < 0 || end < 0) {
                        continue;
//                        start = 0;
//                        end = Math.min(20, length);
                    } else {
                        AntlrProxies.ProxyToken startToken = semantics.tokens().get(start);
                        AntlrProxies.ProxyToken endToken = end >= semantics.tokens().size()
                                ? semantics.tokens().get(semantics.tokens().size() - 1) : semantics.tokens().get(end);
                        start = startToken.getStartIndex();
                        end = Math.min(length - 1, endToken.getStopIndex() + 1);
                    }
                    if (start > length || end > length) {
                        continue;
                    }
                    if (end <= start) {
                        continue;
                    }
                    DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> range = Range.of(start, end - start, a);
                    ranges.add(range);
                }
            }
        }
        for (AntlrProxies.ProxyToken tok : semantics.tokens()) {
            int tp = tok.getType();
            AntlrProxies.ProxyTokenType type = semantics.tokenTypeForInt(tp);
            AdhocColoring a = colorings.get(AdhocTokenId.categorize(type));
            // We can be highlighting a stale text
            if (tok.getStartIndex() > length || tok.getStopIndex() > length) {
                break;
            }
            if (a != null && a.isActive()) {
                int start = tok.getStartIndex();
                int end = tok.getEndIndex();
                if (end >= length) {
                    end = length - 1;
                }
                if (end <= start) {
                    continue;
                }
                CharSequence txt = tok.text();
                int last = (end - start) - 1;
                while (last > 0) {
                    if (Character.isWhitespace(txt.charAt(last))) {
                        end--;
                        last--;
                    } else {
                        break;
                    }
                }
                DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> range = Range.of(start, end - start, a);
                ranges.add(range);
            }
        }
        ranges = Range.coalesce((List) ranges, (Coalescer) new C());
        iter = ranges.iterator();
        LOG.log(Level.FINER, "rules highlighted: {0} of {1}", new Object[]{ruleCount, allRuleCount});
    }

    @Override
    public boolean moveNext() {
        boolean result = iter.hasNext();
        if (result) {
            current = iter.next();
        } else {
            current = null;
        }
        return result;
    }

    @Override
    public int getStartOffset() {
        return current.start();
    }

    @Override
    public int getEndOffset() {
        return current.end();
    }

    @Override
    public AttributeSet getAttributes() {
        return current.get();
    }

    static final class C implements Coalescer<DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>>> {

        @Override
        public DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> combine(
                DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> a,
                DataIntRange<AttributeSet, ? extends DataIntRange<AttributeSet, ?>> b,
                int start, int size) {
            AttributeSet ca = a.get();
            AttributeSet cb = b.get();
            if (ca.isEqual(cb)) {
                return a.newRange(start, size);
            }
            AttributeSet nue = a.isContainedBy(b) ? AdhocColoring.concatenate(cb, ca)
                    : AdhocColoring.concatenate(ca, cb);
            return Range.of(start, size, nue);
        }
    }
}

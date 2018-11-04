package org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.BlockElement;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrNestingDepthHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, ANTLRv4ParserResult, ANTLRv4SemanticParser> {

    public AntlrNestingDepthHighlighter(Document doc) {
        super(doc, ANTLRv4ParserResult.class, GET_SEMANTICS);
    }

    private List<BlockElement> lastBlocks = new LinkedList<>();

    public void refresh(Document doc, Void argument, ANTLRv4SemanticParser semantics, ANTLRv4ParserResult result) {
        bag.clear();
        List<BlockElement> blocks = new ArrayList<>(semantics.blocks());
        synchronized (this) {
            if (blocks.equals(lastBlocks)) {
                return;
            }
            lastBlocks = blocks;
        }
        // Progressively higher alpha for nested blocks
        BlockElement.rendering(() -> { // temporary cache of colors
            // Since we're deep in the rendering loop, avoid
            // creating a ton of identical AttributeSet instances,
            // one for every block of the same nesting depth
            AttributeSet[] cache = new AttributeSet[20];
            Collections.sort(blocks);
            for (BlockElement block : blocks) {
                int depth = block.nestingDepth();
                if (depth > 1) {
                    AttributeSet set = depth < cache.length
                            ? cache[depth] : null;
                    if (set == null) {
                        set = block.attrs();
                    }
                    if (depth < cache.length) {
                        cache[depth] = set;
                    }
                    bag.addHighlight(block.getStartOffset(), block.getEndOffset(),
                            set);
                }
            }
        });
    }
}

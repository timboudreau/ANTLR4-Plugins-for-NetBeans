package org.nemesis.antlr.live.preview;

import java.awt.Color;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 * Manages converting a particular token into an (optionally HTML-ized) breadcrumb
 * string showing the path through the grammar rules to the grammar root from a
 * particular token; also maintains state in the form of a list of distances from
 * each rule to the target (which is the current caret position), allowing these
 * elements to be highlighted.
 *
 * @author Tim Boudreau
 */
public interface RulePathStringifier {

    public void tokenRulePathString(AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, StringBuilder into, boolean html);

    public Color listBackgroundColorFor(String ruleName);
}

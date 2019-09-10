package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 *
 * @author Tim Boudreau
 */
public class RulePathStringifierImpl implements RulePathStringifier {

    private static final String DELIM = " &gt; ";
    private static final String TOKEN_DELIM = " | ";

    private Map<String, Integer> distances = new HashMap<>();
    private int maxDist = 1;

    public void tokenRulePathString(AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, StringBuilder into, boolean html) {
        AntlrProxies.ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
        if (type != null) {
            if (html) {
                into.append("<b>");
            }
            into.append(type.name());
            if (html) {
                into.append("</b>");
            }
            into.append(TOKEN_DELIM);
        }
        int count = tok.referencedBy().size() - 1;
        maxDist = count + 1;
        distances.clear();
        for (int i = count; i >= 0; i--) { // zeroth will be the token
            AntlrProxies.ParseTreeElement el = tok.referencedBy().get(i);
            if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                boolean sameSpan = ((AntlrProxies.RuleNodeTreeElement) el).isSameSpanAsParent();
                if (i != count) {
                    into.append(DELIM);
                }
                if (sameSpan) {
                    if (html) {
                        into.append("<font color=#888888><i>");
                    }
                    into.append(el.name());
                    if (html) {
                        into.append("</i></font>");
                    }
                } else {
                    into.append(el.name());
                }
                distances.put(el.name(), i);
            } else if (el instanceof AntlrProxies.TerminalNodeTreeElement) {
                if (i != count) {
                    into.append(DELIM);
                }
                if (html) {
                    into.append("<font color='#0000cc'><b>'");
                }
                into.append(el.name());
                if (html) {
                    into.append("'</b></font>");
                }
            } else {
                if (i != count) {
                    into.append(DELIM);
                }
                if (html) {
                    String nm = el.name().replaceAll("<", "&lt;")
                            .replaceAll(">", "&gt;");
                    into.append(nm);
                } else {
                    into.append(el.name());
                }
            }
        }
    }

    public Color listBackgroundColorFor(String ruleName) {
        Integer val = distances.get(ruleName);
        if (val == null) {
            return null;
        }
        return colorForDistance(val);
    }

    private Color relatedToCaretItemHighlightColor() {
        return new Color(255, 196, 80);
    }

    private Color caretItemHighlightColor() {
        return new Color(180, 180, 255);
    }
    private static final int MAX_HIGHLIGHTABLE_DISTANCE = 7;

    private Color colorForDistance(float dist) {
        if (maxDist == 0) {
            return null;
        }
        if (dist == 0) {
            caretItemHighlightColor();
        }
        int mx = Math.min(MAX_HIGHLIGHTABLE_DISTANCE, maxDist);
        if (dist > MAX_HIGHLIGHTABLE_DISTANCE) {
            return null;
        }
        float alpha = Math.max(1f, dist / (float) mx);
        Color hl = relatedToCaretItemHighlightColor();
        return new Color(hl.getRed(), hl.getGreen(), hl.getBlue(),
                (int) (255 * alpha));
    }
}

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
package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JList;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;

/**
 *
 * @author Tim Boudreau
 */
public class RulePathStringifierImpl implements RulePathStringifier {

    private static final String DELIM = " &gt; ";
    private static final String TOKEN_DELIM = " | ";

    private Map<String, Integer> distances = new HashMap<>();
    private int maxDist = 1;

    public void tokenRulePathString(AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, StringBuilder into, boolean html, JComponent colorSource) {
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
        int count = prx.referencesCount(tok) - 1; //tok.referencedBy().size() - 1;
        if (count <= 0) {
            return;
        }
        maxDist = count + 1;
        distances.clear();
        List<ParseTreeElement> els = prx.referencedBy(tok);
        for (int i = count; i >= 0; i--) { // zeroth will be the token
            AntlrProxies.ParseTreeElement el = els.get(i);
            if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                boolean sameSpan = ((AntlrProxies.RuleNodeTreeElement) el).isSameSpanAsParent();
                if (i != count) {
                    into.append(DELIM);
                }
                if (sameSpan) {
                    if (html) {
                        appendDimmedForeground(colorSource, into);
                        into.append("<i>");
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
                    appendAttentionForeground(colorSource, into);
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

    static String dimmedForeground(JComponent comp) {
        StringBuilder sb = new StringBuilder(20);
        appendDimmedForeground(comp, sb);
        return sb.toString();
    }

    static String attentionForeground(JComponent comp) {
        StringBuilder sb = new StringBuilder(20);
        appendAttentionForeground(comp, sb);
        return sb.toString();
    }

    static void appendDimmedForeground(JComponent comp, StringBuilder into) {
        if (isDarkBackground(comp)) {
            foregroundColor(new Color(162, 162, 162));
        } else {
            foregroundColor(new Color(128, 128, 128));
        }
    }

    static void appendAttentionForeground(JComponent comp, StringBuilder into) {
        if (isDarkBackground(comp)) {
            foregroundColor(new Color(192, 192, 240));
        } else {
            foregroundColor(new Color(40, 40, 120));
        }
    }

    public Color listBackgroundColorFor(String ruleName, JList<?> list) {
        Integer val = distances.get(ruleName);
        if (val == null) {
            return null;
        }
        return colorForDistance(val, list);
    }

    static void twoCharHex(int val, StringBuilder into) {
        String s = Integer.toString(val, 16);
        if (s.length() == 1) {
            into.append('0');
        }
        into.append(s);
    }

    static void hexColor(Color color, StringBuilder into) {
        into.append("'#");
        twoCharHex(color.getRed(), into);
        twoCharHex(color.getGreen(), into);
        twoCharHex(color.getBlue(), into);
        into.append('\'');
    }

    static String foregroundColor(Color color) {
        StringBuilder sb = new StringBuilder(20).append("<font color=");
        hexColor(color, sb);
        return sb.append('>').toString();
    }

    static boolean isDarkBackground(JComponent comp) {
        Object cp = comp.getClientProperty("_dark");
        if (cp != null) {
            return Boolean.TRUE.equals(cp);
        }
        Color c = comp.getBackground();
        float[] hsb = new float[3];
        Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
        boolean result = hsb[2] <= 0.5;
        comp.putClientProperty("_dark", result);
        return result;
    }

    private Color relatedToCaretItemHighlightColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return new Color(40, 112, 40);
        }
        return new Color(255, 196, 80);
    }

    private Color caretItemHighlightColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return new Color(30, 180, 30);
        }
        return new Color(180, 180, 255);
    }
    private static final int MAX_HIGHLIGHTABLE_DISTANCE = 7;

    private Color colorForDistance(float dist, JComponent comp) {
        if (maxDist == 0) {
            return null;
        }
        if (dist == 0) {
            caretItemHighlightColor(comp);
        }
        int mx = Math.min(MAX_HIGHLIGHTABLE_DISTANCE, maxDist);
        if (dist > MAX_HIGHLIGHTABLE_DISTANCE) {
            return null;
        }
        float alpha = Math.max(1f, dist / (float) mx);
        Color hl = relatedToCaretItemHighlightColor(comp);
        return new Color(hl.getRed(), hl.getGreen(), hl.getBlue(),
                (int) (255 * alpha));
    }
}

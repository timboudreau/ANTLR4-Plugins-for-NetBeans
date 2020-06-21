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

import com.mastfrog.util.strings.Escaper;
import java.awt.Color;
import java.awt.FontMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.UIManager;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElementKind;
import org.nemesis.swing.cell.TextCell;
import org.nemesis.swing.cell.TextCellLabel;

/**
 *
 * @author Tim Boudreau
 */
public class RulePathStringifierImpl implements RulePathStringifier {

    private static final Color DIM_BRIGHT = new Color(162, 162, 162);
    private static final Color DIM_DARK = new Color(128, 128, 128);
    private static final Color ATTENTION_BRIGHT = new Color(192, 192, 240);
    private static final Color ATTENTION_DARK = new Color(40, 40, 120);
    private static final Color TERMINAL_BRIGHT = new Color(128, 187, 140);
    private static final Color TERMINAL_DARK = new Color(60, 152, 80);
    private static final Color RELATED_TO_CARET_DARK = new Color(40, 112, 40);
    private static final Color RELATED_TO_CARET_BRIGHT = new Color(255, 196, 80);
    private static final Color CARET_ITEM_DARK = new Color(30, 180, 30);
    private static final Color CARET_ITEM_BRIGHT = new Color(180, 180, 255);
    private static final Color RULE_CELL_DARK = new Color(40, 40, 135);
    private static final Color RULE_CELL_LIGHT = new Color(162, 162, 237);
    private static final Color RULE_CELL_DARK_DIM = new Color(10, 10, 80);
    private static final Color RULE_CELL_LIGHT_DIM = new Color(180, 180, 245);
    private static final int MAX_HIGHLIGHTABLE_DISTANCE = 8;
    private static final String DELIM = " &gt; ";
    private static final String TOKEN_DELIM = " | ";
    private final BreadcrumbCellShape shape = new BreadcrumbCellShape();
    private boolean useShape = false;

    private Map<String, Integer> distances = new HashMap<>();
    private int maxDist = 1;

    public void configureTextCell(TextCellLabel lbl, AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, JComponent colorSource) {
        AntlrProxies.ProxyTokenType type = prx.tokenTypeForInt(tok.getType());
        int count = prx.referencesCount(tok) - 1;
        if (count <= 0) {
            return;
        }
        maxDist = count + 1;
        distances.clear();
        List<ParseTreeElement> els = prx.referencedBy(tok);
        lbl.setBackground(colorSource.getBackground());
        lbl.setForeground(colorSource.getForeground());
        Color shad = DIM_DARK;
        FontMetrics fm = colorSource.getFontMetrics(lbl.getFont());
        lbl.useFullTextAsToolTip();

        int pad = (int) Math.ceil(fm.getHeight() / 14);
        if (useShape) {
            lbl.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }

        int gap = fm.charWidth('O') / 2;
        TextCell cell = lbl.cell().bold().withText(type.name()).withForeground(attentionForegroundColor(colorSource))
                .rightMargin(gap).pad(pad);

        boolean ruleElementSeen = false;
        for (int i = count; i >= 0; i--) { // zeroth will be the token
            AntlrProxies.ParseTreeElement el = els.get(i);
            if (i != count) {
                String delim = i == 0 ? "\u00B7" : ">";
                cell.append(delim, (ch) -> {
                    ch.withForeground(shad).leftMargin(gap).rightMargin(gap).scaleFont(0.75);
                });
            }
            if (el instanceof AntlrProxies.RuleNodeTreeElement) {
                boolean sameSpan = ((AntlrProxies.RuleNodeTreeElement) el).isSameSpanAsParent();
                boolean isFirstRule = !ruleElementSeen;
                ruleElementSeen = true;
                cell.append(el.name(), ch -> {
                    if (sameSpan) {
                        ch.withForeground(dimmedForegroundColor(colorSource)).italic();
                        if (useShape) {
                            ch.withBackground(ruleCellDimmedBackgroundColor(colorSource), shape)
                                    .indent(12).rightMargin(12).topMargin(3);
                        }
                    } else {
                        if (useShape) {
                            ch.withBackground(ruleCellBackgroundColor(colorSource), shape)
                                    .indent(12).rightMargin(12).topMargin(3);
                        }
                    }
                    if (isFirstRule) {
                        ch.bold();
                    }
                    if (useShape) {
                        ch.pad(pad).shapeOutlinePainted().bottomMargin(1).topMargin(1);
                    }

                });
                distances.put(el.name(), i);
            } else if (el instanceof AntlrProxies.TerminalNodeTreeElement) {
                cell.append(truncateText(el), ch -> {
                    ch.withForeground(terminalForegroundColor(colorSource));
                });
            } else { // error node
                cell.append(el.name(), ch -> {
                    Color c = UIManager.getColor("nb.errorForeground");
                    if (c == null) {
                        c = Color.RED;
                    }
                    if (el.kind() == ParseTreeElementKind.ERROR) {
                        ch.withForeground(c);
                    }
                });
            }
        }
        lbl.invalidate();
        lbl.revalidate();
        lbl.repaint();
    }

    private String truncateText(AntlrProxies.ParseTreeElement el) {
        String text = Escaper.CONTROL_CHARACTERS.escape(el.name());
        if (text.length() > 16) {
            text = text.substring(0, 16) + "\u2026";
        }
        return "'" + text + "'";
    }

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
        into.append(foregroundColor(dimmedForegroundColor(comp)));
    }

    static void appendAttentionForeground(JComponent comp, StringBuilder into) {
        into.append(foregroundColor(attentionForegroundColor(comp)));
    }

    static Color ruleCellBackgroundColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return RULE_CELL_DARK;
        }
        return RULE_CELL_LIGHT;
    }

    static Color ruleCellDimmedBackgroundColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return RULE_CELL_DARK_DIM;
        }
        return RULE_CELL_LIGHT_DIM;
    }

    static Color dimmedForegroundColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return DIM_BRIGHT;
        }
        return DIM_DARK;
    }

    static Color attentionForegroundColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return ATTENTION_BRIGHT;
        }
        return ATTENTION_DARK;
    }

    static Color terminalForegroundColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return TERMINAL_BRIGHT;
        }
        return TERMINAL_DARK;
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
            return RELATED_TO_CARET_DARK;
        }
        return RELATED_TO_CARET_BRIGHT;
    }

    private Color caretItemHighlightColor(JComponent comp) {
        if (isDarkBackground(comp)) {
            return CARET_ITEM_DARK;
        }
        return CARET_ITEM_BRIGHT;
    }

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
        float alpha = Math.min(1f, dist / (float) mx);
        Color hl = relatedToCaretItemHighlightColor(comp);
        return new Color(hl.getRed(), hl.getGreen(), hl.getBlue(),
                (int) (255 * alpha));
    }
}

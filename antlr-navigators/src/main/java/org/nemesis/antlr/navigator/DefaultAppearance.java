package org.nemesis.antlr.navigator;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedSemanticRegion;
import org.openide.awt.HtmlRenderer;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultAppearance implements Appearance<Object> {

    private static final int DEFAULT_INDENT_PIXELS = 10;

    private int indentPixels = -1;
    private int fontSize = -1;

    private void configureIndent(HtmlRenderer.Renderer on) {
        JComponent comp = (JComponent) on; // naughty, but I wrote it
        Font font = comp.getFont();
        if (font == null) {
            font = UIManager.getFont("controlFont");
        }
        if (font != null) {
            fontSize = font.getSize();
            FontMetrics metrics = comp.getFontMetrics(font);
            indentPixels = metrics.stringWidth("XX");
        }
    }

    private int indent(HtmlRenderer.Renderer on) {
        if (fontSize == -1) {
            configureIndent(on);
        }
        return fontSize == -1 || indentPixels == -1 ? DEFAULT_INDENT_PIXELS : indentPixels;
    }

    @Override
    public void configureAppearance(HtmlRenderer.Renderer on, Object region, boolean componentActive, Set<String> scopingDelimiters, SortTypes sort) {
        defaultConfigure(on, region, componentActive, scopingDelimiters);
    }

    Map<String, Pattern> patternCache = new HashMap<>();

    private Pattern patternFor(String s) {
        Pattern result = patternCache.get(s);
        if (result == null) {
            result = Pattern.compile(s, Pattern.LITERAL);
            patternCache.put(s, result);
        }
        return result;
    }

    private String setText(String name, Set<String> scopingDelimiters, HtmlRenderer.Renderer on) {
        // If a delimiter exists, trim preceding path elements, and indent a
        // pixel multiple of the depth
        if (scopingDelimiters != null && !scopingDelimiters.isEmpty()) {
            for (String scopingDelimiter : scopingDelimiters) {
                if (name.contains(scopingDelimiter)) {
                    Pattern p = patternFor(scopingDelimiter);

                    String[] items = p.split(name);
                    int count = items.length - 1;
                    on.setIndent(indent(on) * count);
                    String result = items[items.length - 1];
                    on.setText(result);
                    return result;
                }
            }
        }
        on.setText(name);
        return name;
    }

    @SuppressWarnings("StringEquality")
    void defaultConfigure(HtmlRenderer.Renderer on, Object region, boolean componentActive, Set<String> scopingDelimiters) {
        if (region instanceof NamedSemanticRegion<?>) {
            NamedSemanticRegion<?> nsr = (NamedSemanticRegion<?>) region;
            on.setHtml(true);
            Enum<?> key = nsr.kind();
            if (key instanceof Icon) {
                on.setIcon((Icon) key);
            }
            String name = nsr.name();
            if (scopingDelimiters == null) {
                on.setText(name);
            } else {
                setText(name, scopingDelimiters, on);
                StringBuilder sb = new StringBuilder();
                sb.append(name);
                if (nsr.kind() != null) {
                    sb.append(" (").append(nsr.kind()).append(")");
                }
                ((JComponent) on).setToolTipText(sb.toString());
            }
        } else if (region instanceof SemanticRegion<?>) {
            SemanticRegion<?> semRegion = (SemanticRegion<?>) region;
            Object key = semRegion.key();
            if (key == null) {
                on.setHtml(false);
                on.setText("<no-key>");
            } else {
                on.setHtml(true);
                on.setText(semRegion.key().toString());
            }
            int indent = semRegion.nestingDepth() * indent(on);
            on.setIndent(indent);
        } else {
            on.setText(region == null ? "<no-region>" : region.toString());
        }
        on.setParentFocused(componentActive);

    }
}

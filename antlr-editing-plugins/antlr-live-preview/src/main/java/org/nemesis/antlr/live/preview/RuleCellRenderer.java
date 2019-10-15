package org.nemesis.antlr.live.preview;

import java.awt.Color;
import java.awt.Component;
import java.util.function.Function;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;

/**
 *
 * @author Tim Boudreau
 */
final class RuleCellRenderer implements ListCellRenderer<String> {

    private final AdhocColorings colorings;
    private final HtmlRendererImpl ren = new HtmlRendererImpl();
    private final Function<String, Color> listBackgroundColorFor;

    public RuleCellRenderer(AdhocColorings colorings, Function<String, Color> listBackgroundColorFor) {
        this.colorings = colorings;
        this.listBackgroundColorFor = listBackgroundColorFor;
    }

    @Override
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = ren.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ren.setHtml(true);
        String prefix = "";
        AdhocColoring col = colorings.get(value);
        if (!col.isActive()) {
            prefix += "<font color=#aaaaaa>";
        }
        if (col.isBackgroundColor()) {
            prefix += "<b>";
        }
        if (col.isItalic()) {
            prefix += "<i>";
        }
        Color back = listBackgroundColorFor.apply(value);
        if (isSelected) {
            ren.setCellBackground(list.getSelectionBackground());
        } else if (back != null) {
            ren.setCellBackground(back);
        }
        ren.setText(prefix.isEmpty() ? value : prefix + value);
        ren.setIndent(5);
        return result;
    }

}

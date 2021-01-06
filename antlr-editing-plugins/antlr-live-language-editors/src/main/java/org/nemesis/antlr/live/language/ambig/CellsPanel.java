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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.colors.RotatingColors;
import com.mastfrog.function.state.Obj;
import com.mastfrog.swing.cell.TextCell;
import com.mastfrog.swing.cell.TextCellLabel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.spi.language.highlighting.EditorAttributesFinder;
import org.netbeans.api.editor.settings.FontColorNames;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Pair;

/**
 *
 * @author Tim Boudreau
 */
public class CellsPanel extends JPanel {

    private final PT target;
    private final List<List<PT>> paths;
    private final JLabel title = new JLabel();
    private final List<Pair<TextCellLabel, TextCellLabel>> connections = new ArrayList<>();
    private final BiConsumer<MouseEvent, PT> onClick;
    private final JPanel innerPanel = new JPanel(new GridBagLayout());

    @NbBundle.Messages(value = {"# {0} - tokenName",
        "# {1} - ruleName",
        "reachableMultiple=<html><b>{0}</b> can be reached multiple ways from <i>{1}</i>:"
    })
    public CellsPanel(String ruleName, PT target, List<List<PT>> paths) {
        this(ruleName, target, paths, null, null);
    }

    public CellsPanel(String ruleName, PT target, List<List<PT>> paths, BiConsumer<MouseEvent, PT> onClick, Function<PT, String> toolTipSupplier) {
        super(new GridBagLayout());
        this.onClick = onClick;
        GridBagConstraints gbc = new GridBagConstraints();
        title.setText(Bundle.reachableMultiple(target, ruleName));
        GridBagConstraints c1 = new GridBagConstraints();
        c1.anchor = GridBagConstraints.FIRST_LINE_START;
        c1.fill = GridBagConstraints.BOTH;
        c1.gridx = 0;
        c1.gridy = 0;
        c1.weightx = 1;
        c1.weighty = 0;
        title.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("controlDkShadow")));
        innerPanel.setBorder(BorderFactory.createEmptyBorder(12, 5, 5, 5));
        add(title, c1);

        c1.gridy++;
        c1.weighty = 1;
        c1.fill = GridBagConstraints.NONE;
        add(innerPanel, c1);
        gbc.gridx = 0;
        gbc.gridy = 0;
        int max = 1;
        for (List<PT> l : paths) {
            max = Math.max(max, l.size());
        }
        gbc.gridwidth = max;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        this.target = target;
        this.paths = paths;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 1;
        gbc.weightx = 0;
        addCellsFor(paths, 0, max, gbc, 0, new RotatingColors(), toolTipSupplier);
    }

    private void postMerge(List<List<PT>> diffs) {
        for (int i = 1; i < diffs.size(); i++) {
            List<PT> prev = diffs.get(i - 1);
            List<PT> curr = diffs.get(i);
            if (curr.size() != prev.size()) {
                int insertPoint = -1;
                for (int j = 0; j < Math.min(prev.size(), curr.size()); j++) {
                    int pix = prev.size() - (j + 1);
                    PT ppt = prev.get(pix);
                    int cix = curr.size() - (j + 1);
                    PT cpt = curr.get(cix);
                    if (cpt != null && Objects.equals(ppt, cpt)) {
                        insertPoint = cix;
                    } else {
//                        break;
                    }
                }
                if (insertPoint >= 0) {
                    curr.add(insertPoint, null);
                }
            }
        }
    }

    private void addCellsFor(List<List<PT>> paths, int start, int max, GridBagConstraints gbc, int depth, Supplier<Color> colors, Function<PT, String> tooltips) {
        int ct = paths.size();
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        Obj<TextCellLabel> firstTailCell = Obj.create();
        PT.commonalities(paths, (List<PT> commonHead, List<List<PT>> differences, List<PT> commonTail) -> {
            postMerge(differences);
            int startY = gbc.gridy;
            gbc.gridx = start;
//            System.out.println("HEAD: " + commonHead);
//            System.out.println("TAIL: " + commonTail);
//            System.out.println("DIFFS SIZE " + differences.size());
            int diffItems = 0;
            boolean containsEmpty = false;
            for (int i = 0; i < differences.size(); i++) {
                diffItems += differences.get(i).size();
                containsEmpty |= differences.get(i).isEmpty();
//                System.out.println("D-" + (i + 1) + ". " + differences.get(i));
            }
            Color col = colors.get();
            if (diffItems == 0 && (commonHead.equals(commonTail))) {
                for (PT pt : commonHead) {
                    JComponent lbl = cellFor(pt, true, col, true, tooltips);
                    innerPanel.add(lbl, gbc);
                    gbc.gridx++;
                }
                return;
            }
            Collections.sort(differences, (a, b) -> {
                return -Integer.compare(a.size(), b.size());
            });

            TextCellLabel lastHeadCell = null;
            for (PT pt : commonHead) {
                JComponent lbl = cellFor(pt, true, col, true, tooltips);
                if (lbl instanceof TextCellLabel) {
                    lastHeadCell = (TextCellLabel) lbl;
                }
                innerPanel.add(lbl, gbc);
                gbc.gridx++;
            }
            List<TextCellLabel> firstDiffCells = new ArrayList<>();
            List<TextCellLabel> lastDiffCells = new ArrayList<>();
            int x = gbc.gridx;
            Map<PT, TextCellLabel> m = new HashMap<>();
            for (int i = 0; i < differences.size(); i++) {
                gbc.gridy = startY + i;
                gbc.gridx = x;
                List<PT> d = differences.get(i);
                TextCellLabel lastLabel = null;
                for (int j = 0; j < d.size(); j++) {
                    gbc.gridx = x + j;
                    if (j < d.size() - 1 && d.get(j+1) == null) {
                        gbc.gridwidth = 2;
                    }
                    Color c = colors.get();
                    PT p = differences.get(i).get(j);
                    if (i > 0) {
                        List<PT> prevList = differences.get(i - 1);
                        int endOff = j;// d.size() - (j + 1);
                        if (endOff < prevList.size() && Objects.equals(prevList.get(endOff), p) && lastLabel != null) {
                            TextCellLabel prev = m.get(p);
                            if (prev != null) {
                                connections.add(Pair.of(lastLabel, prev));
                                gbc.gridwidth = 1;
                                continue;
                            }
                        }
                    }
                    JComponent lbl = cellFor(p, false, c, false, tooltips);
                    if (lbl instanceof TextCellLabel) {
                        TextCellLabel ll = (TextCellLabel) lbl;
                        if (lastLabel != null) {
                            connections.add(Pair.of(lastLabel, ll));
                        }
                        lastLabel = ll;
                        m.put(p, ll);
                    }
                    if (j == 0 && containsEmpty) {
                        gbc.gridy++;
                    }
                    innerPanel.add(lbl, gbc);
                    gbc.gridwidth = 1;
                    if (lbl instanceof TextCellLabel && j == differences.get(i).size() - 1) {
                        lastDiffCells.add((TextCellLabel) lbl);
                    }
                    if (j == 0 && lbl instanceof TextCellLabel) {
                        firstDiffCells.add((TextCellLabel) lbl);
                    }
                }
            }
            if (lastHeadCell != null) {
                for (TextCellLabel lb : firstDiffCells) {
                    connections.add(Pair.of(lastHeadCell, lb));
                }
            }
            TextCellLabel tailCellOne = firstTailCell.get();
            if (tailCellOne == null) {
                for (int i = 0; i < commonTail.size(); i++) {
                    int right = max - i;
                    PT pt = commonTail.get(commonTail.size() - (i + 1));
                    gbc.gridy = startY;
                    gbc.gridx = right;
                    JComponent lbl = cellFor(pt, true, col, false, tooltips);
                    innerPanel.add(lbl, gbc);
                    if (i == commonTail.size() - 1 && lbl instanceof TextCellLabel) {
                        if (tailCellOne == null) {
                            tailCellOne = (TextCellLabel) lbl;
                            firstTailCell.set(tailCellOne);
                        }
                    }
                }
            }
            if (tailCellOne != null) {
                for (TextCellLabel lb : lastDiffCells) {
                    connections.add(Pair.of(lb, tailCellOne));
                }
            }
            if (containsEmpty && tailCellOne != null && lastHeadCell != null) {
                connections.add(Pair.of(lastHeadCell, tailCellOne));
            }
            gbc.gridy = startY + differences.size() + 1;
        });
    }

    private Font font;

    private Font font() {
        if (font == null) {
            EditorAttributesFinder finder = EditorAttributesFinder.forMimeType(ANTLR_MIME_TYPE);
            AttributeSet attrs = finder.apply(FontColorNames.DEFAULT_COLORING);
            String fontName = null;
            int size = 12;
            if (attrs != null) {
                fontName = StyleConstants.getFontFamily(attrs);
                size = StyleConstants.getFontSize(attrs);
                if (size == 12 || size == 13) {
                    Font f = UIManager.getFont("controlFont");
                    if (f != null) {
                        size = f.getSize();
                    }
                }
            }
//            System.out.println("ATTRS " + attrs);
            font = new Font(fontName == null ? "Courier New" : fontName, Font.PLAIN, size);
        }
        return font;
    }

    private void applyFont(TextCell cell) {
        cell.withFont(font());
    }

    @Messages({"# {0} - altNumber", "alternativeNumber=Alternative {0}"})
    private JComponent cellFor(PT pt, boolean common, Color bg, boolean isHead, Function<PT, String> toolTipSupplier) {
        if (pt == null) {
            return new JLabel("");
        }
        TextCell cell = new TextCell(pt.toString()).pad(3).topMargin(3);

        applyFont(cell);

        if (!common) {
            cell.bold();
        }
        if (isHead) {
            cell.italic();
        }
        cell.shapeOutlinePainted().indent(18).rightMargin(13);
        if (bg != null) {
            RectangularShape ell = new SHP();  //new RoundRectangle2D.Float(0, 0, 10, 10, 9, 9);
            cell.withBackground(bg, ell);
        }
        cell.scaleFont(0.875);
        TextCellLabel lbl = new TextCellLabel(cell);
        if (toolTipSupplier != null) {
            lbl.setToolTipText(toolTipSupplier.apply(pt));
        } else if (pt.isRuleTree()) {
            lbl.setToolTipText(Bundle.alternativeNumber(pt.altNumber()));
        }
        if (onClick != null) {
            lbl.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            lbl.addMouseListener(new PTLis(pt));
        }
        return lbl;
    }

    private final class PTLis extends MouseAdapter {

        private final PT pt;

        public PTLis(PT pt) {
            this.pt = pt;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            onClick.accept(e, pt);
        }
    }

    enum HorizRelation {
        BEFORE,
        SAME_COLUMN,
        AFTER;

        static HorizRelation relation(Rectangle a, Rectangle b) {
            if (a.x == b.x) {
                return SAME_COLUMN;
            } else if (a.x < b.x) {
                return BEFORE;
            } else {
                return AFTER;
            }
        }
    }

    enum VertRelation {
        BELOW,
        SAME_ROW,
        ABOVE;

        static VertRelation relation(Rectangle a, Rectangle b) {
            if (a.y == b.y) {
                return SAME_ROW;
            } else if (a.y > b.y) {
                return BELOW;
            } else {
                return ABOVE;
            }
        }
    }

    static final class SHP extends RectangularShape {

        private double x, y, w, h;
        private final double arcOffset;

        SHP() {
            this(10);
        }

        SHP(double arcOffset) {
            this.arcOffset = arcOffset;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public double getWidth() {
            return w;
        }

        @Override
        public double getHeight() {
            return h;
        }

        @Override
        public boolean isEmpty() {
            return w <= 0 || h <= 0;
        }

        @Override
        public void setFrame(double d, double d1, double d2, double d3) {
            x = d;
            y = d1;
            w = d2;
            h = d3 - 5;
        }

        @Override
        public Rectangle2D getBounds2D() {
            return new Rectangle2D.Double(x, y, w, h);
        }

        @Override
        public boolean contains(double x, double y) {
            return getBounds2D().contains(x, y);
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            return getBounds2D().intersects(x, y, w, h);
        }

        @Override
        public boolean contains(double x, double y, double w, double h) {
            return getBounds2D().contains(x, y, w, h);
        }

        @Override
        public PathIterator getPathIterator(AffineTransform at) {
            if (at != null && !at.isIdentity()) {
                double[] pts = new double[]{
                    x, y,
                    x + w, y + h,
                    0, 0,
                    arcOffset, arcOffset
                };
                at.transform(pts, 0, pts, 0, 4);
//                System.out.println("WAS " + x + "," + y + " " + w + "x" + h);
//                System.out.println("xf to " + pts[0] + ", " + pts[1] + " " + (pts[2] - pts[0]) + "x" + (pts[3] - pts[1]) + " arc " + (pts[6] - pts[4]));
                return new PI(pts[0], pts[1], pts[2] - pts[0], pts[3] - pts[1], Math.abs(pts[6] - pts[4]));
            }
            return new PI(x, y, w, h, arcOffset);
        }

        static class PI implements PathIterator {

            private int cursor = 0;
            private final double x, y, w, h, arc;

            public PI(double x, double y, double w, double h, double arc) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.arc = arc;
            }

            @Override
            public int getWindingRule() {
                return PathIterator.WIND_NON_ZERO;
            }

            @Override
            public boolean isDone() {
                // Points:
                // 0 : upper right top line start
                // 1 : upper left angle begin
                // 2 : left angle midpoint
                // 3 : left angle bottom
                // 4 : bottom right bottom line leftmost point
                // 5 : curve to first point through midpoint
                return cursor > 5;
            }

            @Override
            public void next() {
                cursor++;
            }

            @Override
            public int currentSegment(double[] coords) {
                switch (cursor) {
                    case 0:
                        coords[0] = x + arc;
                        coords[1] = y;
                        return PathIterator.SEG_MOVETO;
                    case 1:
                        coords[0] = x + (w - (h / 2));
                        coords[1] = y;
                        return PathIterator.SEG_LINETO;
                    case 2:
                        coords[0] = x + w;
                        coords[1] = y + (h / 2);
                        return PathIterator.SEG_LINETO;
                    case 3:
                        coords[0] = x + (w - (h / 2));
                        coords[1] = y + h;
                        return PathIterator.SEG_LINETO;
                    case 4:
                        coords[0] = x + arc;
                        coords[1] = y + h;
                        return PathIterator.SEG_LINETO;
                    case 5:
                        coords[0] = x;
                        coords[1] = y + (h / 2);
                        coords[2] = x + arc;
                        coords[3] = y;
                        return PathIterator.SEG_QUADTO;
                    default:
                        throw new IndexOutOfBoundsException(cursor);
                }
            }

            @Override
            public int currentSegment(float[] coords) {
                double[] d = new double[coords.length];
                int result = currentSegment(d);
                for (int i = 0; i < coords.length; i++) {
                    coords[i] = (float) d[i];
                }
                return result;
            }
        }
    }

    public void paintChildren(Graphics gg) {
        super.paintChildren(gg);
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5F));
        Color color = UIManager.getColor("textText");
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
        for (Pair<TextCellLabel, TextCellLabel> p : connections) {
            Rectangle abds = p.first().getBounds();
            Rectangle bbds = p.second().getBounds();
            abds = SwingUtilities.convertRectangle(innerPanel, abds, this);
            bbds = SwingUtilities.convertRectangle(innerPanel, bbds, this);

            VertRelation vrel = VertRelation.relation(abds, bbds);

            Path2D.Double path = new Path2D.Double();
            double voff = 4;
            switch (vrel) {
                case SAME_ROW:
                    path.moveTo(abds.x + (abds.width - voff) + 1, abds.y + (abds.height / 2) - voff);
                    path.lineTo(bbds.x + 3, bbds.y + (bbds.height / 2) - voff);
                    break;
                case ABOVE:
                    path.moveTo(abds.x + (abds.width / 2), abds.y + abds.height - 7);
                    double dist = (abds.y + abds.height) - (bbds.y + bbds.height);
                    path.quadTo(abds.x + (abds.width / 2) + 3, bbds.y + bbds.height + (dist / 2),
                            bbds.x + 3, bbds.y + (bbds.height / 2) - voff);
//                    path.curveTo(
//                            abds.x + (abds.width) + 3, bbds.y + bbds.height + (dist),
//                            abds.x + (abds.width / 2) + 3, bbds.y + bbds.height + (dist / 2),
//                            bbds.x + 3, bbds.y + (bbds.height / 2) - voff);
                    break;
                case BELOW:
                    double dist2 = (bbds.y + bbds.height) - (abds.y + (abds.height / 2));
                    if (bbds.x - (abds.width + abds.x) < 10) {
                        path.moveTo(abds.x + (abds.width / 2), abds.y + 2);
                        path.curveTo(
                                abds.x + (abds.width / 2) + 5, bbds.y + bbds.height,
                                //                                abds.x + (abds.width / 2), abds.y + (dist2 / 4),
                                bbds.x - ((bbds.x - abds.x) / 2), bbds.y + (bbds.height / 2),
                                bbds.x + 3, bbds.y + (bbds.height / 2) - voff);
                        break;
                    } else {
                        path.moveTo(abds.x + abds.width, abds.y + (abds.height / 2) - voff);
//                        int of = 20;
                        int of = ((abds.y - bbds.y) / 4);
                        path.curveTo(
                                bbds.x - ((abds.x + abds.width) / 10), abds.y + (abds.height / 2) - voff,
                                bbds.x + of, bbds.y + bbds.height - (dist2),
                                bbds.x + 3, bbds.y + (bbds.height / 2) - voff);
                        break;
                    }
                default:
                    boolean toRight = bbds.x > abds.x + abds.width;
                    boolean above = abds.y < bbds.y;

                    double ax = toRight && !above ? abds.x + abds.width : abds.getCenterX();
                    double ay = above ? abds.y : abds.getCenterY();

                    double bx = toRight ? bbds.x : bbds.getCenterX();
                    double by = above ? bbds.y + bbds.height : bbds.getCenterY();

                    path.moveTo(ax, ay);
                    path.lineTo(bx, by);
            }
            g.draw(path);
        }
    }
}

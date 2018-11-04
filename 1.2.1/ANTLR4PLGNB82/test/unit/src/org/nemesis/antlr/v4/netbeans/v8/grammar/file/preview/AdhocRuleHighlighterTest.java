package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocRuleHighlighter.HL;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocRuleHighlighterTest {

    private ArrayList<HL> hss;

    @Test
    public void testSort() {
        List<HL> h = new ArrayList<>(hss);
        Collections.sort(h);
        for (int i = 0; i < h.size(); i++) {
            // These should wind up sorted by start, then width, such
            // that any item that contains smaller ones comes first
            switch(i) {
                case 0 :
                    assertEquals(20, h.get(i).end);
                    break;
                case 1 :
                    assertEquals(5, h.get(i).end);
                    break;
                case 2 :
                    assertEquals(5, h.get(i).start);
                    assertEquals(10, h.get(i).end);
                    break;
                case 3 :
                    assertEquals(20, h.get(i).start);
                    assertEquals(30, h.get(i).end);
                    break;
                case 4 :
                    assertEquals(20, h.get(i).start);
                    assertEquals(25, h.get(i).end);
                    break;
            }
        }
        List<HL> xformed = AdhocRuleHighlighter.split(h);
        assertEquals(13, xformed.size());
        assertEquals("0:5 - background=java.awt.Color[r=0,g=0,b=0] foreground=java.awt.Color[r=0,g=0,b=255]", xformed.get(0).toString());
        assertEquals("5:10 - background=java.awt.Color[r=0,g=0,b=0] foreground=java.awt.Color[r=255,g=255,b=0]", xformed.get(1).toString());
        assertEquals("10:20 - AdhocColoring{r=0 g=0 b=0, flags:ACTIVE }", xformed.get(2).toString());
        assertEquals("20:25 - background=java.awt.Color[r=255,g=255,b=255] foreground=java.awt.Color[r=0,g=255,b=0]", xformed.get(3).toString());
        assertEquals("25:26 - AdhocColoring{r=255 g=255 b=255, flags:ACTIVE }", xformed.get(4).toString());
        assertEquals("26:27 - background=java.awt.Color[r=255,g=255,b=255] foreground=java.awt.Color[r=255,g=0,b=0] bold=true", xformed.get(5).toString());
        assertEquals("27:30 - AdhocColoring{r=255 g=255 b=255, flags:ACTIVE }", xformed.get(6).toString());
        assertEquals("50:60 - AdhocColoring{r=255 g=200 b=0, flags:ACTIVE BACKGROUND }", xformed.get(7).toString());
        assertEquals("60:65 - AdhocColoring{r=0 g=0 b=255, flags:ACTIVE BACKGROUND BOLD }", xformed.get(8).toString());
        assertEquals("65:70 - foreground=java.awt.Color[r=0,g=0,b=0] italic=true", xformed.get(9).toString());
        assertEquals("100:103 - foreground=java.awt.Color[r=0,g=0,b=0] background=java.awt.Color[r=255,g=255,b=255] italic=true bold=true", xformed.get(10).toString());
        assertEquals("103:105 - foreground=java.awt.Color[r=0,g=0,b=0] background=java.awt.Color[r=255,g=255,b=255] italic=true", xformed.get(11).toString());
        assertEquals("105:110 - foreground=java.awt.Color[r=0,g=0,b=0] background=java.awt.Color[r=255,g=255,b=255]", xformed.get(12).toString());
    }

    @Before
    public void setup() {
        hss = new ArrayList<>();
        hss.add(new HL(20, 30, new AdhocColoring(Color.white, true, true, false, false, false)));
        hss.add(new HL(20, 25, new AdhocColoring(Color.green, true, false, true, false, false)));
        hss.add(new HL(26, 27, new AdhocColoring(Color.red, true, false, true, true, false)));
        hss.add(new HL(0, 20, new AdhocColoring(Color.black, true, true, false, false, false)));
        hss.add(new HL(0, 5, new AdhocColoring(Color.blue, true, false, true, false, false)));
        hss.add(new HL(5, 10, new AdhocColoring(Color.yellow, true, false, true, false, false)));
        hss.add(new HL(50, 60, new AdhocColoring(Color.orange, false, true, true, false, false)));
        hss.add(new HL(60, 70, new AdhocColoring(Color.blue, false, true, true, false, true)));
        hss.add(new HL(65, 70, new AdhocColoring(Color.black, true, false, true, false, true)));

        hss.add(new HL(100, 120, new AdhocColoring(Color.black, true, false, true, false, false)));
        hss.add(new HL(100, 110, new AdhocColoring(Color.white, true, true, false, false, false)));
        hss.add(new HL(100, 105, new AdhocColoring(Color.white, true, false, false, false, true)));
        hss.add(new HL(100, 103, new AdhocColoring(Color.white, true, false, false, true, false)));
    }

}

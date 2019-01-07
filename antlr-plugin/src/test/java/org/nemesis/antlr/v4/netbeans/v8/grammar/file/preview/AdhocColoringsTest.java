package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ColorUtils.editorBackground;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ColorUtils.editorForeground;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui.AdhocColoringPanel;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.LanguageReplaceabilityTest.ADP;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import org.nemesis.antlr.v4.netbeans.v8.project.ParsingTestEnvironment;
import org.nemesis.antlr.v4.netbeans.v8.project.ParsingWithoutFilesTest;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocColoringsTest {

    private String goodTestData;
    private DefaultStyledDocument goodData;
    private String mimeType;

    @Test
    public void testAttributes() throws Throwable {
        AdhocColorings colorings = new AdhocColorings();
        PCL pcl = new PCL();
        AdhocColoring c = colorings.add("foo", Color.yellow, AttrTypes.ACTIVE, AttrTypes.BACKGROUND, AttrTypes.BOLD);
        colorings.addPropertyChangeListener("foo", pcl);

        assertEquals(2, c.getAttributeCount());
        Set<Object> attrs = attrs(c);
        assertEquals(2, attrs.size());
        assertFalse(attrs.contains(StyleConstants.Foreground));
        assertTrue(attrs.contains(StyleConstants.Background));
        assertTrue(attrs.contains(StyleConstants.Bold));

        assertEquals(Color.yellow, c.color());
        assertTrue(c.isBackgroundColor());
        Object attr = c.getAttribute(StyleConstants.Background);
        assertNotNull(attr);
        assertEquals(Color.yellow, attr);

        assertTrue(c.containsAttribute(StyleConstants.Background, Color.yellow));
        assertFalse(c.containsAttribute(StyleConstants.Foreground, Color.yellow));

        assertTrue(colorings.setForeground("foo", true));
        pcl.assertFired("foo");
        assertTrue("Should now be set to foreground", c.isForegroundColor());
        assertFalse("Should not say it is a background color", c.isBackgroundColor());

        attr = c.getAttribute(StyleConstants.Background);
        assertNull(attr);
        attr = c.getAttribute(StyleConstants.Foreground);
        assertNotNull(attr);
        assertEquals(Color.yellow, attr);

        assertFalse(c.containsAttribute(StyleConstants.Background, Color.yellow));
        assertTrue(c.containsAttribute(StyleConstants.Foreground, Color.yellow));

        attrs = attrs(c);
        assertEquals(2, attrs.size());
        assertTrue(attrs.contains(StyleConstants.Foreground));
        assertFalse(attrs.contains(StyleConstants.Background));
        assertFalse(attrs.contains(StyleConstants.Italic));
        assertTrue(attrs.contains(StyleConstants.Bold));
        assertEquals(2, c.getAttributeCount());

        assertTrue(colorings.setFlag("foo", AttrTypes.ITALIC, true));
        pcl.assertFired("foo");
        assertFalse(colorings.setFlag("foo", AttrTypes.ITALIC, true));
        pcl.assertNotFired();

        attrs = attrs(c);
        assertTrue(attrs.contains(StyleConstants.Foreground));
        assertFalse(attrs.contains(StyleConstants.Background));
        assertTrue(attrs.contains(StyleConstants.Italic));
        assertTrue(attrs.contains(StyleConstants.Bold));

        assertEquals(3, c.getAttributeCount());
    }

    private static Set<Object> attrs(AttributeSet s) {
        Set<Object> result = new HashSet<>();
        Enumeration<?> en = s.getAttributeNames();
        while(en.hasMoreElements()) {
            result.add(en.nextElement());
        }
        return result;
    }

    @Test
    public void testColorings() throws IOException {
        AdhocColorings colorings = new AdhocColorings();
        AdhocColoring c = colorings.add("foo", Color.yellow, AttrTypes.ACTIVE, AttrTypes.BACKGROUND, AttrTypes.BOLD);
        System.out.println(c);
        assertNotNull(c);
        assertTrue(c.isActive());
        assertTrue(c.isBold());
        assertTrue(c.isBackgroundColor());
        assertFalse(c.isForegroundColor());
        assertFalse(c.isItalic());
        assertEquals(Color.yellow, c.color());
        assertTrue(colorings.contains("foo"));
        assertSame(c, colorings.get("foo"));

        AdhocColoring c1 = colorings.add("bar", Color.blue, AttrTypes.ACTIVE, AttrTypes.FOREGROUND);
        System.out.println(c1);
        assertNotNull(c1);
        assertTrue(c1.isActive());
        assertFalse(c1.isBold());
        assertFalse(c1.isBackgroundColor());
        assertTrue(c1.isForegroundColor());
        assertFalse(c1.isItalic());
        assertEquals(Color.blue, c1.color());
        assertTrue(colorings.contains("bar"));
        assertSame(c1, colorings.get("bar"));

        String line = c1.toLine();
        System.out.println("LINE: " + line);
        AdhocColoring reconstituted = AdhocColoring.parse(line);
        assertNotNull(reconstituted);
        assertEquals(c1, reconstituted);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        colorings.store(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        AdhocColorings nue = AdhocColorings.load(in);
        System.out.println("WROTE " + new String(out.toByteArray(), UTF_8));
        assertNotNull(nue);
        assertEquals(colorings, nue);

        c1.addFlag(AttrTypes.ITALIC);
        assertTrue(c1.isItalic());
        assertNotEquals(colorings, nue);

        PCL pcl = new PCL();
        colorings.addPropertyChangeListener(pcl);
        colorings.setColor("foo", Color.gray);
        pcl.assertFired("foo");
        colorings.setFlag("bar", AttrTypes.BOLD, true);
        pcl.assertFired("bar");
        colorings.setFlag("bar", AttrTypes.BOLD, true);
        pcl.assertNotFired();
        colorings.setFlag("bar", AttrTypes.BOLD, false);
        pcl.assertFired("bar");
    }

    @Test
    public void testGenerateColors() throws Throwable {
        AdhocColoringsRegistry reg = new AdhocColoringsRegistry();
        AdhocColorings col = reg.get(mimeType);
        assertNotNull(col);
        assertFalse(col.isEmpty());
        System.out.println("GOT COLORS: '" + col + "'");

        ColorUtils utils = new ColorUtils();

        Supplier<Color> bgs = utils.backgroundColorSupplier();
        for (int i = 0; i < 10; i++) {
            System.out.println("BG: " + bgs.get());
        }

        Supplier<Color> fgs = utils.foregroundColorSupplier();
        for (int i = 0; i < 10; i++) {
            System.out.println("FG: " + fgs.get());
        }

        if (false) {
            System.setProperty("swing.aatext", "true");
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
            EventQueue.invokeAndWait(() -> {
                JPanel pnl = new JPanel(new GridLayout(30, 1));
                Color c = editorBackground();
                pnl.setBackground(c);
                JPanel editors = new JPanel(new GridBagLayout());
                GridBagConstraints con = new GridBagConstraints();
                con.gridx = 0;
                con.gridy = 0;
                con.anchor = GridBagConstraints.FIRST_LINE_START;
                con.fill = GridBagConstraints.BOTH;
                con.weightx = 1.0;
                con.weighty = 1.0;
                for (String key : col.keys()) {
                    AdhocColoring a = col.get(key);
                    editors.add(new AdhocColoringPanel(key, col), con);
                    con.gridy++;
                    JLabel lbl = new JLabel(key);
                    lbl.setFont(lbl.getFont().deriveFont(lbl.getFont().getSize2D() + 5F));

                    lbl.setOpaque(true);
                    if (a.isBackgroundColor()) {
                        lbl.setForeground(editorForeground());
                        lbl.setBackground(a.color());
                    } else {
                        lbl.setBackground(editorBackground());
                        lbl.setForeground(a.color());
                    }
                    col.addPropertyChangeListener(key, new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (a.isBackgroundColor()) {
                                lbl.setForeground(editorForeground());
                                lbl.setBackground(a.color());
                            } else {
                                lbl.setBackground(editorBackground());
                                lbl.setForeground(a.color());
                            }
                        }
                    });
                    pnl.add(lbl);
                }
                JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        pnl, new JScrollPane(editors));
                JFrame jf = new JFrame();
                jf.setContentPane(split);
                jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                jf.pack();
                jf.setVisible(true);
            });
            Thread.sleep(160000);
        }
    }

    private ANTLRv4SemanticParser doParse() {
        NBANTLRv4Parser parser = new NBANTLRv4Parser();
        Source src = Source.create(goodData);
        ParsingTestEnvironment.setSourceForParse(src);
        Snapshot sn = src.createSnapshot();
        UserTask ut = new UserTask() {
            @Override
            public void run(ResultIterator ri) throws Exception {
            }
        };
        parser.parse(sn, ut, new ParsingWithoutFilesTest.SME(src));
        NBANTLRv4Parser.ANTLRv4ParserResult result = parser.getResult(ut);
        assertNotNull(result);
        ANTLRv4SemanticParser sem = result.semanticParser();
        assertNotNull(sem);
        return sem;
    }

    static final class PCL implements PropertyChangeListener {

        void assertNotFired() {
            assertNull(last);
        }

        String last;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            last = evt.getPropertyName();
        }

        void assertFired(String name) {
            String l = last;
            last = null;
            assertNotNull(l);
            assertEquals(name, l);
        }

    }

    @Before
    public void setup() throws Throwable {
        ParsingTestEnvironment.init(ADP.class);
        Path baseDir = TestDir.projectBaseDir();
        Path src = baseDir.resolve(Paths.get("src", "main", "resources", AntlrFolders.class.getPackage().getName().replace('.', '/'),
                "antlr-options-preview.g4"));
        assertTrue(src + "", Files.exists(src));
        mimeType = AdhocMimeTypes.mimeTypeForPath(src);
        System.out.println("MIME " + mimeType);

        InputStream in = AntlrFolders.class.getResourceAsStream("antlr-options-preview.g4");
        assertNotNull("antlr-options-preview.g4 not on classpath next to AntlrFolders.class",
                in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtil.copy(in, out);
        goodTestData = new String(out.toByteArray(), UTF_8);
        goodData = new DefaultStyledDocument();
        goodData.insertString(0, goodTestData, null);
        goodData.putProperty("mimeType", "text/x-g4");
    }
}

package org.nemesis.antlr.completion;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.BaseDocument;

/**
 *
 * @author Tim Boudreau
 */
public class DefaultDocumentUpdaterTest {

    private final S s = new S();
    private final DefaultDocumentUpdater<String> updater = new DefaultDocumentUpdater<>(s);

    @Test
    public void testTrailing() throws Exception {
        BaseDocument doc = doc(".Poozle)");
        String trail = DefaultDocumentUpdater.findPresentTrailingSubsequence("woogle.Poozle)", doc, 0);
        assertEquals("woogle", trail);

        doc = doc("");
        trail = DefaultDocumentUpdater.findPresentTrailingSubsequence("woogle.Poozle)", doc, 0);
        assertEquals("woogle.Poozle)", trail);

        doc = doc("wo");
        trail = DefaultDocumentUpdater.findPresentTrailingSubsequence("woogle.Poozle)", doc, 0);
        assertEquals("woogle.Poozle)", trail);
    }

    @Test
    public void testDocuments() throws Exception{
        int[] cp = {0};
        BaseDocument doc = doc("(woogle     ");
        updater.doUpdate("woogle.Poozle", doc, 7, 0, 0, cp);
//        System.out.println("DOC NOW: " + doc.getText(0, doc.getLength()));
        assertDocument("(woogle.Poozle)     ", doc);
        doc = doc("(woogle");
        updater.doUpdate("woogle.Poozle", doc, 7, 0, 0, cp);
//        System.out.println("DOC NOW: " + doc.getText(0, doc.getLength()));
        assertDocument("(woogle.Poozle) ", doc);

        doc = doc("glork");
        updater.doUpdate("woogle.Poozle", doc, 1, 0, 0, cp);
        assertDocument("g (woogle.Poozle) lork", doc);

        doc = doc("");
        updater.doUpdate("woogle.Poozle", doc, 0, 0, 0, cp);
        assertDocument("(woogle.Poozle) ", doc);

        doc = doc("(woogle.Poo");
        updater.doUpdate("woogle.Poozle", doc, 3, 0, 0, cp);
        assertDocument("(woogle.Poozle) ", doc);
        
        doc = doc("(woogle.Poozle) ");
        updater.doUpdate("woogle.Poozle", doc, 3, 0, 0, cp);
        assertDocument("(woogle.Poozle) ", doc);
        assertEquals(doc.getLength(), cp[0]);
    }

    @Test
    public void checkSequence() throws Exception {
        BaseDocument d = doc("Hello world");
        assertDocument("Hello world", d);
        boolean[] found = new boolean[1];
        String wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 9, found);
        assertEquals("ld", wo);
        assertTrue(found[0]);
        wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 10, found);
        assertEquals("d", wo);
        wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 8, found);
        assertEquals("rld", wo);
        wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 7, found);
        assertEquals("orld", wo);
        wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 6, found);
        assertEquals("world", wo);
        wo = DefaultDocumentUpdater.findRemainingSubsequence("world", d, 1, found);
        assertEquals("world", wo);
    }

    @Test
    public void sanityCheck() throws Exception {
        assertEquals("in wookie.foo", s.apply(StringKind.DISPLAY_DIFFERENTIATOR, "wookie.foo.Bar"));
        assertEquals("Bar", s.apply(StringKind.DISPLAY_NAME, "Bar"));
        assertEquals("wookie.foo.Bar", s.apply(StringKind.INSERT_PREFIX, "wookie.foo.Bar"));
        assertEquals("(wookie.foo.Bar)", s.apply(StringKind.TEXT_TO_INSERT, "wookie.foo.Bar"));
        assertNull(s.apply(StringKind.SORT_TEXT, "wookie.foo.Bar"));
    }

    private void assertDocument(String expected, Document doc) throws BadLocationException {
        String txt = doc.getText(0, doc.getLength());
        assertEquals(expected, txt);
    }

    private static BaseDocument doc(String text) throws BadLocationException {
        BaseDocument d = new BaseDocument(false, "text/x-yasl");
        d.insertString(0, text, null);
        return d;
    }

    static class S implements Stringifier<String> {

        @Override
        public String apply(StringKind kind, String text) {
            int ix;
            switch (kind) {
                case DISPLAY_DIFFERENTIATOR:
                    ix = text.lastIndexOf('.');
                    if (ix >= 0) {
                        return "in " + text.substring(0, ix);
                    }
                    return null;
                case INSERT_PREFIX:
                    return text;
                case DISPLAY_NAME:
                    ix = text.lastIndexOf('.');
                    if (ix > 0 && ix < text.length() - 1) {
                        return text.substring(ix + 1);
                    }
                    return text;
                case SORT_TEXT:
                    return null;
                case TEXT_TO_INSERT:
                    return "(" + text + ")";
                default:
                    throw new AssertionError();
            }
        }

    }

}

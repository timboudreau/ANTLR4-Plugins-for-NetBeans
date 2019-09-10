package org.nemesis.antlr.spi.language.fix;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Position;
import org.nemesis.antlr.spi.language.fix.DocumentEditBag.BLE;
import com.mastfrog.function.throwing.ThrowingRunnable;
import org.netbeans.editor.BaseDocument;

/**
 *
 * @author Tim Boudreau
 */
public final class DocumentEditSet {

    private final List<ThrowingRunnable> all = new ArrayList<>(3);
    private final BaseDocument doc;
    private final DocumentEditBag consumer;

    DocumentEditSet(BaseDocument doc, DocumentEditBag consumer) {
        this.doc = doc;
        this.consumer = consumer;
    }

    public final DocumentEditSet delete(int start, int end) throws Exception {
        Position startPos = doc.createPosition(start);
        Position endPos = doc.createPosition(end);
        all.add(() -> {
            consumer.delete(doc, startPos.getOffset(), endPos.getOffset());
        });
        return this;
    }

    public final DocumentEditSet insert(int start, String text) throws Exception {
        Position startPos = doc.createPosition(start);
        all.add(() -> {
            consumer.insert(doc, startPos.getOffset(), text);
        });
        return this;
    }

    public final DocumentEditSet replace(int start, int end, String text) throws Exception {
        Position startPos = doc.createPosition(start);
        Position endPos = doc.createPosition(end);
        all.add(() -> {
            consumer.replace(doc, startPos.getOffset(), endPos.getOffset(), text);
        });
        return this;
    }

    BLE runner() {
        return () -> {
            for (ThrowingRunnable r : all) {
                r.run();
            }
        };
    }
}

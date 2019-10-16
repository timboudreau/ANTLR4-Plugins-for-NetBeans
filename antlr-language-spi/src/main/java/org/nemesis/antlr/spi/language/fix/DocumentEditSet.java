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

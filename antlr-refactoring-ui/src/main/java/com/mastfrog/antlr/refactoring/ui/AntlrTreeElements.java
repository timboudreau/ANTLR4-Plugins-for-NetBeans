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
package com.mastfrog.antlr.refactoring.ui;

import com.mastfrog.range.IntRange;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.io.IOException;
import javax.swing.Icon;
import org.nemesis.extraction.key.ExtractionKey;
import org.netbeans.api.actions.Openable;
import org.netbeans.modules.refactoring.api.RefactoringElement;
import org.netbeans.modules.refactoring.spi.ui.TreeElement;
import org.netbeans.modules.refactoring.spi.ui.TreeElementFactory;
import org.netbeans.modules.refactoring.spi.ui.TreeElementFactoryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.text.NbDocument;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = TreeElementFactoryImplementation.class, position = 45)
public class AntlrTreeElements implements TreeElementFactoryImplementation {

    private static final Lookup.Template<? super ExtractionKey<?>> templ = new Lookup.Template<>(ExtractionKey.class);

    @Override
    public TreeElement getTreeElement(Object o) {
        System.out.println("get tree element " + o + " (" + o.getClass().getName() + ")");
        if (o instanceof RefactoringElement) {
            RefactoringElement el = (RefactoringElement) o;
            // A squirrely way around the fact that you can't look up
            // ExtractionKey<?> only raw ExtractionKey.
            Lookup.Item<? super ExtractionKey<?>> item = el.getLookup().lookupItem(templ);
            if (item != null) {
                // one of ours
                IntRange<?> range = el.getLookup().lookup(IntRange.class);
                if (range != null) {
                    return new TreeElementImpl(el);
                }
            }
        }
        return null;
    }

    @Override
    public void cleanUp() {
    }

    class TreeElementImpl implements TreeElement, Openable {

        private RefactoringElement el;

        TreeElementImpl(RefactoringElement el) {
            this.el = el;
        }

        @Override
        public TreeElement getParent(boolean bln) {
            return TreeElementFactory.getTreeElement(el.getParentFile());
        }

        @Override
        public Icon getIcon() {
            return new I();
        }

        @Override
        public String getText(boolean bln) {
//            return !bln ? el.getText() : el.getDisplayText();
            return el.getDisplayText();
        }

        @Override
        public Object getUserObject() {
            return el;
        }

        @Override
        public void open() {
            try {
                FileObject file = el.getParentFile();
                if (file.isValid()) {
                    DataObject od = DataObject.find(file);
                    PositionBounds pb = el.getPosition();
                    NbDocument.openDocument(od, pb.getBegin().getLine(),
                            pb.getBegin().getColumn(),
                            Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    static final class I implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(new Color(128, 128, 255));
            g.fillRect(x + 2, y + 2, 12, 12);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

    }

}

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
package org.nemesis.antlr.file.refactoring;

import com.mastfrog.range.IntRange;
import java.awt.EventQueue;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
class Usage extends AbstractRefactoringContext implements RefactoringElementImplementation {

    private final FileObject file;
    private final IntRange range;
    private int status = NORMAL;
    private final String of;
    private final String name;
    private boolean enabled = true;

    public Usage(FileObject file, IntRange range, String of, String name) {
        this.file = file;
        this.range = range;
        this.of = of;
        this.name = name;
    }

    @Override
    public String toString() {
        return "Usage(" + name + " at " + range + " of " + of + " in " + file.getNameExt() + ")";
    }

    @Override
    @Messages({
        "# {0} - reference type",
        "# {1} - usage text",
        "# {2} - range start",
        "# {3} - range end",
        "# {4} - file name",
        "usage={0} ''{1}'' at {2}:{3} in {4}"
    })
    public String getText() {
        return Bundle.usage(of, name, range.start(), range.end(), file.getNameExt());
    }

    @Override
    @Messages({
        "# {0} - reference type",
        "# {1} - file name",
        "simple_usage={0} in {1}"
    })
    public String getDisplayText() {
        return Bundle.simple_usage(name, file.getName());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean bln) {
        enabled = bln;
    }

    @Override
    public void performChange() {
        // do nothing
    }

    @Override
    public void undoChange() {
        // do nothing
    }

    @Override
    public Lookup getLookup() {
        return lookupOf(getParentFile());
    }

    @Override
    public FileObject getParentFile() {
        return file;
    }

    @Override
    public PositionBounds getPosition() {
        return createPosition(getParentFile(), range);
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int i) {
        status = i;
    }

    @Override
    public void openInEditor() {
        Lookup lkp = getLookup();
        OpenCookie ck = lkp.lookup(OpenCookie.class);
        if (ck != null) {
            ck.open();
        }
        EventQueue.invokeLater(() -> {
            EditorCookie ec = lkp.lookup(EditorCookie.class);
            JTextComponent[] comps = ec.getOpenedPanes();
            for (JTextComponent comp : comps) {
                TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
                if (tc != null) {
                    tc.requestActive();
                    try {
                        Rectangle a = comp.modelToView(range.start());
                        Rectangle b = comp.modelToView(range.end());
                        a.add(b);
                        comp.setSelectionStart(range.start());
                        comp.setSelectionEnd(range.end());
                        comp.scrollRectToVisible(a);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    break;
                }
            }
        });
    }

    @Override
    public void showPreview() {
        // do nothing
    }
}

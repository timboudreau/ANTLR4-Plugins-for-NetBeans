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
package org.nemesis.antlr.refactoring;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.RangeRelation;
import java.awt.EventQueue;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
class Usage extends AbstractRefactoringContext implements ComparableRefactoringElementImplementation {

    private final FileObject file;
    private final IntRange<? extends IntRange> range;
    private int status = NORMAL;
    private final ExtractionKey<?> key;
    private final String name;
    private boolean enabled = true;
    private Lookup lookup;
    private final Object[] lookupContents;
    private final PositionBounds position;

    Usage(FileObject file, IntRange<? extends IntRange> range, ExtractionKey<?> key, String name, Object... lookupContents) {
        this.file = file;
        this.range = range;
        this.key = key;
        this.name = name;
        this.lookupContents = lookupContents;
        this.position = createPosition(file, range);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof Usage) {
            Usage u = (Usage) o;
            return u.file.equals(file) && u.range.relationTo(range) == RangeRelation.EQUAL;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Usage(" + name + " at " + range + " of " + key + " in " + file.getNameExt() + ")";
    }

    @Override
    @Messages({
        "# {0} - reference type",
        "# {1} - usage text",
        "# {2} - range start",
        "# {3} - range end",
        "# {4} - file name",
        "usage=Original {0} ''{1}'' at {2}:{3} in {4}",
        "# {0} - reference type",
        "# {1} - usage text",
        "# {2} - range start",
        "# {3} - range end",
        "# {4} - file name",
        "reference=Reference to {0} ''{1}'' at {2}:{3} in {4}"
    })
    public String getText() {
        String localizedKeyName = Localizers.displayName(key);
        return isRef()
                ? Bundle.reference(localizedKeyName, name, range.start(), range.end(), file.getNameExt())
                : Bundle.usage(localizedKeyName, name, range.start(), range.end(), file.getNameExt());
    }

    boolean isRef() {
        if (range instanceof NamedSemanticRegionReference<?>) {
            return true;
        }
        if (range instanceof UnknownNameReference<?>) {
            return true;
        }
        if (range instanceof org.nemesis.extraction.AttributedForeignNameReference<?,?,?,?>) {
            return true;
        }
        return false;
    }

    @Override
    @Messages({
        "# {0} - reference type",
        "# {1} - file name",
        "simple_usage={0} in {1}",
        "# {0} - reference type",
        "# {1} - file name",
        "simple_ref={0} in {1}"
    })
    public String getDisplayText() {
        return getText();
//        return isRef()
//                ? Bundle.simple_ref(name, file.getName())
//                : Bundle.simple_usage(name, file.getName());
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
        if (lookup == null) {
            if (lookupContents != null && lookupContents.length > 0) {
                lookup = new ProxyLookup(Lookups.fixed(lookupContents),
                        Lookups.fixed(this, range, name, getParentFile(), position),
                        lookupOf(getParentFile()));
            } else {
                lookup = new ProxyLookup(
                        Lookups.fixed(this, range, name, getParentFile(), position),
                        lookupOf(getParentFile()));
            }
        }
        return lookup;
    }

    @Override
    public FileObject getParentFile() {
        return file;
    }

    @Override
    public PositionBounds getPosition() {
        return position;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int i) {
        status = i;
    }

    private JTextComponent findEditor(Lookup lookup) {
        EditorCookie ck = lookup.lookup(EditorCookie.class);
        if (ck == null) {
            return null;
        }
        JTextComponent comp = NbDocument.findRecentEditorPane(ck);
        if (comp != null) {
            return comp;
        }
        JTextComponent[] comps = ck.getOpenedPanes();
        return comps.length > 0 ? comps[0] : null;
    }

    @Override
    public void openInEditor() {
        Lookup lkp = getLookup();
        OpenCookie ck = lkp.lookup(OpenCookie.class);
        if (ck != null) {
            ck.open();
        } else {
            CloneableEditorSupport supp = lkp.lookup(CloneableEditorSupport.class);
            if (supp != null) {
                supp.edit();
            }
        }
        EventQueue.invokeLater(() -> {
            boolean opened = false;
            JTextComponent comp = findEditor(lkp);
            if (comp != null) {
                TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, comp);
                if (tc != null) {
                    tc.requestActive();
                    try {
                        // It seems unlikely, but we sometimes get a PositionBounds
                        // with the end set to null, and the class does not check it
                        Rectangle a = comp.modelToView(position.getBegin().getOffset());
                        if (a == null) {
                            System.out.println("GOT NULL RECT FOR " + position);
                        }
                        if (position.getEnd() != null) {
                            Rectangle b = comp.modelToView(position.getEnd().getOffset());
                            a.add(b);
                        }
                        comp.setSelectionStart(position.getBegin().getOffset());
                        if (position.getEnd() != null) {
                            comp.setSelectionEnd(position.getEnd().getOffset());
                        }
                        comp.scrollRectToVisible(a);
                        opened = true;
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            if (!opened) {
                logWarn("Could not find an editor to open "
                        + "for {0}", file);
            }
        });
    }

    @Override
    public void showPreview() {
        // do nothing
    }

    @Override
    public int compareTo(RefactoringElementImplementation o) {
        if (o instanceof Usage) {
            Usage u = (Usage) o;
            if (file.equals(u.file)) {
                return this.range.compareTo(u.range);
            }
        }
        return 0;
    }
}

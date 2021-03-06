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
package org.nemesis.antlr.navigator;

import com.mastfrog.abstractions.Named;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.Strings;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.ContentsChecksums;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.localizers.api.Localizers;
import org.openide.awt.HtmlRenderer;
import org.openide.cookies.EditorCookie;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
final class ExtractionTreeNavigatorPanel extends AbstractAntlrTreeNavigatorPanel<ExtractionKey, ActivatedTcPreCheckJTree> {

    private final DefaultTreeModel mdl = new DefaultTreeModel(new DefaultMutableTreeNode());

    @Override
    protected ActivatedTcPreCheckJTree createComponent() {
        ActivatedTcPreCheckJTree tree = new ActivatedTcPreCheckJTree(mdl);
        tree.setCellRenderer(new Ren());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    Object comp = path.getLastPathComponent();
                    if (comp instanceof LazyTreeNode) {
                        LazyTreeNode eq = (LazyTreeNode) comp;
                        int start = -1, end = -1;
                        if (eq.userObject instanceof IndexAddressableItem) {
                            IndexAddressableItem item = (IndexAddressableItem) eq.userObject;
                            start = item.start();
                            end = item.end();
                        } else if (eq.userObject instanceof SingletonEncounter<?>) {
                            SingletonEncounter se = ((SingletonEncounter) eq.userObject);
                            start = se.start();
                            end = se.end();
                        }
                        if (start >= 0 && end >= 0) {
                            Collection<? extends EditorCookie> ecs = editorCookieContext.allInstances();
                            if (!ecs.isEmpty()) {
                                EditorCookie ck = ecs.iterator().next();
                                JEditorPane[] comps = ck.getOpenedPanes();
                                for (JEditorPane pane : comps) {
                                    ExtractionTreeNavigatorPanel.super.moveTo(pane, start, end);
                                }
                            }
                        }
                    }
                }
            }
        });
        return tree;
    }

    @Override
    protected void setNoModel(int forChange) {
        mdl.setRoot(new DefaultMutableTreeNode());
    }

    @Override
    protected void withNewModel(Extraction ext, EditorCookie ck, int forChange) {
        TreePath[] old = list.getSelectionPaths();
        LazyTreeNode newRoot = newRoot2(ext);
        Mutex.EVENT.readAccess(() -> {
            mdl.setRoot(newRoot);
            if (old != null && old.length > 0) {
                List<TreePath> newSel = new ArrayList<>(old.length);
                TreePath rootPath = new TreePath(newRoot);
                for (TreePath pth : old) {
                    if (rootPath.isDescendant(pth)) {
                        newSel.add(pth);
                    } else {
                        while (pth.getParentPath() != null) {
                            pth = pth.getParentPath();
                            if (rootPath.isDescendant(pth)) {
                                newSel.add(pth);
                                break;
                            }
                        }
                    }
                }
                if (!newSel.isEmpty()) {
                    TreePath[] sel = newSel.toArray(new TreePath[newSel.size()]);
                    list.setSelectionPaths(sel);
                }
            }
        });
    }

    @NbBundle.Messages({
        "# {0} - the extraction tokens hash",
        "extraction=Extraction {0}",
        "namedRegions=Named Semantic Regions",
        "semanticRegions=Semantic Regions",
        "singletons=Singleton Regions"
    })
    private LazyTreeNode newRoot2(Extraction ext) {
        return new LazyTreeNode(ext);
    }

    @Override
    public String getDisplayName() {
        return "Current Extraction";
    }

    @Override
    public String getDisplayHint() {
        return "Allows debugging the extraction from the last parse of the "
                + "edited document";
    }

    static class Ren implements TreeCellRenderer {

        private final HtmlRenderer.Renderer r = HtmlRenderer.createRenderer();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component result = r.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            r.setHtml(true);
            if (tree instanceof ComponentIsActiveChecker) {
                r.setParentFocused(((ComponentIsActiveChecker) tree).isActive());
            }
            if (value instanceof String) {
                value = escapeHtml((String) value);
            }
            if (value instanceof LazyTreeNode) {
                if (((LazyTreeNode) value).title != null) {
                    value = ((LazyTreeNode) value).title;
                } else {
                    value = ((LazyTreeNode) value).userObject;
                }
            }
            Icon icon = Localizers.icon(value);
            if (icon.getIconWidth() > 0) {
                r.setIcon(icon);
            } else {
                if (value instanceof NamedSemanticRegion<?>) {
                    icon = Localizers.icon(((NamedSemanticRegion<?>) value).kind());
                } else if (value instanceof SingletonEncounter<?>) {
                    icon = Localizers.icon(((SingletonEncounter<?>) value).get());
                }
                if (icon.getIconWidth() > 0) {
                    r.setIcon(icon);
                }
            }
            if (value instanceof Named) {
                Named n = (Named) value;
                String nm = n.name();
                if (nm != null) {
                    nm = escapeHtml(nm);
                    if (value instanceof NamedSemanticRegion<?>) {
                        NamedSemanticRegion<?> nsr = (NamedSemanticRegion<?>) value;
                        if (nsr.kind() instanceof Supplier<?>) {
                            r.setText("<b>" + nm + "</b> " + value + " <i>"
                                    + ((Supplier<?>) nsr.kind()).get());
                        } else {
                            r.setText("<b>" + nm + "</b> " + value);
                        }
                    } else {
                        r.setText("<b>" + nm + "</b> " + value);
                    }
                }
            } else if (value instanceof ContentsChecksums<?>) {
                r.setText("<b>Duplicates</b> by Checksum");
            } else if (value instanceof DuplicateSet) {
                r.setText(value.toString());
            } else if (value instanceof UnknownNameReference<?>) {
                UnknownNameReference<?> unk = (UnknownNameReference<?>) value;
                r.setText("<b>" + escapeHtml(unk.name()) + "</b> (unattributed reference)");
            } else if (value instanceof ExtractionKey) {
                ExtractionKey k = (ExtractionKey) value;
                String name = Localizers.displayName(k);
                r.setText("<b>" + name + "</b> <i>(" + k.getClass().getSimpleName() + ")</i>");
            } else if (value instanceof Extraction) {
                r.setText("Extraction <i>" + ((Extraction) value).tokensHash());
            } else if (value != null) {
                r.setText(escapeHtml(value.toString()));
            } else if (value instanceof SemanticRegion<?>) {
                SemanticRegion<?> sem = (SemanticRegion<?>) value;
                StringBuilder sb = new StringBuilder();
                sb.append("<b>").append(sem.index()).append("</b> ")
                        .append(sem.start()).append(":").append(sem.end());
                if (sem.key() != null) {
                    sb.append(" <i>").append(escapeHtml(sem.key().toString())).append("</i>");
                }
                r.setText(sb.toString());
            }
            return result;
        }
    }

    private static List<TreeNode> childNodes(LazyTreeNode parent, Object userObject) {
        List<TreeNode> kids = new ArrayList<>(20);
        Extraction ext = parent.extraction();
        if (userObject instanceof Extraction) {
            ext = (Extraction) userObject;
            for (SingletonKey<?> singKey : ext.singletonKeys()) {
                kids.add(new LazyTreeNode(parent, singKey, true));
            }
            for (NamedRegionKey<?> nrk : ext.regionKeys()) {
                kids.add(new LazyTreeNode(parent, nrk, true));
            }
            for (RegionsKey<?> rk : ext.regionsKeys()) {
                kids.add(new LazyTreeNode(parent, rk, true));
            }
            for (NameReferenceSetKey<?> refKey : ext.referenceKeys()) {
                NamedRegionReferenceSets<?> refs = ext.references(refKey);
                if (!refs.isEmpty()) {
                    kids.add(new LazyTreeNode(parent, refKey, true));
                }
                SemanticRegions<?> unks = ext.unknowns(refKey);
                if (!unks.isEmpty()) {
                    kids.add(new LazyTreeNode(parent, unks, true).withTitle("Unknowns: " + refKey));
                }
            }
        } else if (userObject instanceof SemanticRegions<?>) {
            SemanticRegions<?> sems = (SemanticRegions<?>) userObject;
            for (SemanticRegion<?> reg : sems) {
                kids.add(new LazyTreeNode(parent, reg, true));
            }
        } else if (userObject instanceof SemanticRegion<?>) {
            kids.add(new LazyTreeNode(parent, ((SemanticRegion<?>) userObject).key(), true));
        } else if (userObject instanceof UnknownNameReference<?>) {
            UnknownNameReference<?> unk = (UnknownNameReference<?>) userObject;
            kids.add(new LazyTreeNode(parent, unk, false));
        } else if (userObject instanceof ContentsChecksums<?>) {
            ContentsChecksums<?> cc = (ContentsChecksums<?>) userObject;
            if (cc.hasDuplicates()) {
                int[] ix = new int[]{1};
                cc.visitRegionGroups(group -> {
                    kids.add(new LazyTreeNode(parent, new DuplicateSet(ix[0]++, group), !group.isEmpty()));
                });
            }
        } else if (userObject instanceof DuplicateSet) {
            DuplicateSet dups = (DuplicateSet) userObject;
            dups.items.forEach((o) -> {
                kids.add(new LazyTreeNode(parent, o, o instanceof SemanticRegion<?> && ((SemanticRegion<?>) o).hasChildren()));
            });
        } else if (userObject instanceof RegionsKey<?>) {
            RegionsKey<?> rk = (RegionsKey<?>) userObject;
            SemanticRegions<?> semr = ext.regions(rk);
            for (SemanticRegion<?> outermost : semr.outermostElements()) {
                kids.add(new LazyTreeNode(parent, outermost, outermost.hasChildren()));
            }
            ContentsChecksums<?> checksums = ext.checksums(rk);
            if (checksums.hasDuplicates()) {
                kids.add(new LazyTreeNode(parent, checksums, true));
            }
        } else if (userObject instanceof SemanticRegion<?>) {
            SemanticRegion<?> sem = (SemanticRegion<?>) userObject;
            sem.children().forEach((inner) -> {
                kids.add(new LazyTreeNode(parent, inner, inner.hasChildren()));
            });
        } else if (userObject instanceof SingletonKey<?>) {
            SingletonEncounters<?> encs = ext.singletons((SingletonKey<?>) userObject);
            for (SingletonEncounters.SingletonEncounter<?> e : encs) {
                kids.add(new LazyTreeNode(parent, e, false));
            }
        } else if (userObject instanceof NamedRegionKey<?>) {
            NamedRegionKey<?> nrk = (NamedRegionKey<?>) userObject;
            NamedSemanticRegions<?> regs = ext.namedRegions(nrk);
            for (NamedSemanticRegion reg : regs) {
                kids.add(new LazyTreeNode(parent, reg, false));
            }
            ContentsChecksums<?> checksums = ext.checksums(nrk);
            if (checksums.hasDuplicates()) {
                kids.add(new LazyTreeNode(parent, checksums, true));
            }
        } else if (userObject instanceof NameReferenceSetKey<?>) {
            NameReferenceSetKey<?> nrsk = (NameReferenceSetKey<?>) userObject;
            for (NamedRegionReferenceSet<?> r : ext.references(nrsk)) {
                if (!r.isEmpty()) {
                    kids.add(new LazyTreeNode(parent, r, true));
                }
            }
        } else if (userObject instanceof NamedRegionReferenceSet<?>) {
            NamedRegionReferenceSet<?> set = (NamedRegionReferenceSet<?>) userObject;
            for (NamedSemanticRegionReference<?> x : set) {
                kids.add(new LazyTreeNode(parent, x, true));
            }
        } else if (userObject instanceof NamedSemanticRegionReference<?>) {
            NamedSemanticRegionReference<?> ref = (NamedSemanticRegionReference<?>) userObject;
            kids.add(new LazyTreeNode(parent, ref.referencing(), false));
        }
        return kids.isEmpty() ? Collections.emptyList() : kids;
    }

    static final class DuplicateSet {

        final int index;
        final List<? extends IndexAddressableItem> items;

        DuplicateSet(int index, List<? extends IndexAddressableItem> items) {
            this.index = index;
            this.items = items;
        }

        @Override
        public String toString() {
            return "<b>" + index + "</b> - " + items.size()
                    + " duplicate token sequences";
        }
    }

    static final class LazyTreeNode implements TreeNode {

        private List<TreeNode> children = new ArrayList<>();

        private final LazyTreeNode parent;
        private final Object userObject;
        private volatile boolean initialized;
        private final boolean allowsKids;
        private String title;

        LazyTreeNode(Object userObject) {
            this(null, userObject, true);
        }

        public String toString() {
            return title == null ? Objects.toString(userObject) : title;
        }

        public LazyTreeNode withTitle(String title) {
            this.title = title;
            return this;
        }

        private Extraction extraction() {
            if (userObject instanceof Extraction) {
                return (Extraction) userObject;
            }
            if (parent != null) {
                return parent.extraction();
            }
            return null;
        }

        public LazyTreeNode(LazyTreeNode parent, Object userObject, boolean allowsKids) {
            this.parent = parent;
            this.userObject = userObject;
            this.allowsKids = allowsKids;
        }

        public Object userObject() {
            return userObject;
        }

        private void initKids() {
            if (!initialized) {
                if (userObject != null) {
                    synchronized (this) {
                        if (!initialized) {
                            initialized = true;
                            children.addAll(childNodes(this, userObject));
                        }
                    }
                }
            }
        }

        @Override
        public TreeNode getChildAt(int childIndex) {
            initKids();
            return children.get(childIndex);
        }

        @Override
        public int getChildCount() {
            initKids();
            return children.size();
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }

        @Override
        public int getIndex(TreeNode node) {
            if (node instanceof LazyTreeNode) {
                LazyTreeNode lazy = (LazyTreeNode) node;
                lazy.initKids();
                return lazy.children.indexOf(this);
            }
            return -1;
        }

        @Override
        public boolean getAllowsChildren() {
            return allowsKids;
        }

        @Override
        public boolean isLeaf() {
            return !allowsKids;
        }

        @Override
        public Enumeration<? extends TreeNode> children() {
            return CollectionUtils.toEnumeration(children.iterator());
        }
    }

    static String escapeHtml(String s) {
        return Strings.escape(s, ExtractionTreeNavigatorPanel::escape);
    }

    static CharSequence escape(char c) {
        switch (c) {
            case '<':
                return "&lt";
            case '>':
                return "&gt;";
            case '"':
                return "&quot;";
        }
        return Strings.singleChar(c);
    }
}

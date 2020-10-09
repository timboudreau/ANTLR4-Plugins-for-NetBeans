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
package org.nemesis.antlr.live.language.editoractions;

import com.mastfrog.range.Range;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import org.antlr.runtime.CommonToken;
import org.antlr.v4.runtime.misc.Interval;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.language.AdhocEditorKit;
import org.nemesis.antlr.live.language.AdhocLanguageHierarchy;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParser;
import org.nemesis.antlr.live.parsing.EmbeddedAntlrParserResult;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeElement;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.RuleNodeTreeElement;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Extraction;
import org.nemesis.source.api.GrammarSource;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
class GotoSubmenuLazyPopulatorListener implements ItemListener, Runnable {

    private final AtomicLinkedQueue<Action> actions = new AtomicLinkedQueue<>();
    private final JMenu sub;
    private final JMenuItem waitItem;
    private final JTextComponent target;
    private final RequestProcessor.Task task = AdhocEditorKit.popupPool().create(this, false);
    private volatile boolean completed;
    private final String mimeType;

    public GotoSubmenuLazyPopulatorListener(JMenu sub, JMenuItem waitItem, JTextComponent target, String mimeType) {
        this.sub = sub;
        this.waitItem = waitItem;
        this.target = target;
        this.mimeType = mimeType;
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        boolean done = completed;
        if (done) {
            e.getItemSelectable().removeItemListener(this);
        }
        Object[] obs = e.getItemSelectable().getSelectedObjects();
        if (obs != null && obs.length > 0 && !done) {
            task.cancel();
            task.schedule(350);
        } else {
            task.cancel();
        }
    }

    @Override
    public void run() {
        if (!EventQueue.isDispatchThread()) {
            if (Thread.interrupted()) {
                return;
            }
            Segment seg = new Segment();
            target.getDocument().render(() -> {
                try {
                    target.getDocument().getText(0, target.getDocument().getLength(), seg);
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            if (Thread.interrupted()) {
                return;
            }
            if (seg.length() > 0) {
                EmbeddedAntlrParser parser = AdhocLanguageHierarchy.parserFor(mimeType);
                if (Thread.interrupted()) {
                    return;
                }
                try {
                    EmbeddedAntlrParserResult res = parser.parse(seg);
                    if (res != null && res.isUsable()) {
                        if (Thread.interrupted()) {
                            return;
                        }
                        AntlrProxies.ParseTreeProxy prox = res.proxy();
                        if (prox != null && !prox.isUnparsed()) {
                            int caret = target.getCaretPosition();
                            AntlrProxies.ProxyToken tok = prox.tokenAtPosition(caret);
                            if (!prox.isErroneousToken(tok)) {
                                AntlrProxies.ProxyTokenType tokenType = prox.tokenTypeForInt(tok.getType());
                                Path grammarPath = AdhocMimeTypes.grammarFilePathForMimeType(mimeType);
                                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(grammarPath.toFile()));
                                Extraction ext = NbAntlrUtils.extractionFor(fo);
                                if (ext != null && !ext.isPlaceholder()) {
                                    if (Thread.interrupted()) {
                                        return;
                                    }
                                    List<Action> actionsLocal = new ArrayList<>();
                                    if (tokenType.symbolicName != null) {
                                        Action gotoToken = createActionFor(tokenType.symbolicName, ext, null, null);
                                        if (gotoToken != null) {
                                            actionsLocal.add(gotoToken);
                                        }
                                    }
                                    Set<String> seen = new HashSet<>();
                                    List<ParseTreeElement> all = prox.allTreeElements();

                                    ParseTreeElement rangeHolder = null;
                                    for (int i = 0; i < all.size(); i++) {
                                        ParseTreeElement pte = all.get(i);
                                        rangeHolder = i == all.size() - 1 ? null : all.get(i + 1);
                                        switch (pte.kind()) {
                                            case RULE:
                                                if (Thread.interrupted()) {
                                                    return;
                                                }
                                                if (seen.contains(pte.name(prox))) {
                                                    continue;
                                                }
                                                List<AntlrProxies.ProxyToken> toks = prox.tokensForElement(pte);
                                                if (!toks.isEmpty()) {
                                                    AntlrProxies.ProxyToken first = toks.get(0);
                                                    AntlrProxies.ProxyToken last = toks.get(toks.size() - 1);
                                                    int start = first.getStartIndex();
                                                    int end = last.getEndIndex();
                                                    if (caret >= start && caret <= end) {
                                                        String name = pte.name(prox);
                                                        seen.add(name);
                                                        Action act = createActionFor(name, ext, pte, rangeHolder);
                                                        if (act != null) {
                                                            actionsLocal.add(act);
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                    for (Action a : actionsLocal) {
                                        this.actions.add(a);
                                    }
                                }
                            }
                            completed = true;
                            EventQueue.invokeLater(this);
                        }
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        } else {
            if (!sub.isPopupMenuVisible()) {
                completed = false;
                return;
            }
            synchronized (sub.getTreeLock()) {
                boolean hasAny = !actions.isEmpty();
                sub.removeAll();
                if (hasAny) {
                    actions.drain(act -> {
                        sub.add(act);
                    });
                } else {
                    JMenuItem noNav = new JMenuItem(Bundle.noNav());
                    noNav.setEnabled(false);
                    sub.add(noNav);
                }
            }
            // Hack hack hack - menu popups don't expect their
            // contents to change on the fly, and we get 4px tall menu items
            Container parent = sub.getParent();
            if (parent != null) {
                JPopupMenu subPop = sub.getPopupMenu();
                subPop.invalidate();
                subPop.doLayout();
                subPop.revalidate();
                subPop.repaint();
                sub.setPopupMenuVisible(false);
                sub.setPopupMenuVisible(true);
            }
            sub.invalidate();
            sub.revalidate();
            sub.repaint();
        }
    }

    private static Action createActionFor(String name, Extraction ext, ParseTreeElement el, ParseTreeElement container) {
        AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved = ext.resolveName(AntlrKeys.RULE_NAME_REFERENCES, name, true);
        if (resolved != null) {
            Extraction originExt = resolved.attributedTo();
            DataObject dob = originExt.source().lookupOrDefault(DataObject.class, null);
            if (dob != null) {
                CloneableEditorSupport ck = dob.getLookup().lookup(CloneableEditorSupport.class);
                if (ck != null) {
                    PositionBounds pb = null;
                    if (el instanceof RuleNodeTreeElement) {
                        RuleNodeTreeElement re = (RuleNodeTreeElement) el;
                        if (container instanceof RuleNodeTreeElement) {
                            // If we can get the range in the grammar where the target
                            // rule is referenced, then we should select just that token
                            // rather than select the entire rule
                            int state = ((RuleNodeTreeElement) container).invokingState();
                            AntlrGenerationResult genResult = RebuildSubscriptions.recentGenerationResult(dob.getPrimaryFile());
                            if (genResult != null && genResult.isUsable() && genResult.mainGrammar != null) {
                                Interval ival = genResult.mainGrammar.getStateToGrammarRegion(state);
                                if (ival != null) {
                                    CommonToken startToken = (CommonToken) genResult.mainGrammar.originalTokenStream.get(ival.a);
                                    CommonToken stopToken = ival.a == ival.b ? startToken
                                            : (CommonToken) genResult.mainGrammar.originalTokenStream.get(ival.b);
                                    if (resolved.contains(Range.ofCoordinates(startToken.getStartIndex(), stopToken.getStopIndex() + 1))) {
                                        PositionRef start = ck.createPositionRef(startToken.getStartIndex(), Position.Bias.Backward);
                                        PositionRef end = ck.createPositionRef(stopToken.getStopIndex() + 1, Position.Bias.Forward);
                                        pb = new PositionBounds(start, end);
                                    }
                                }
                            }
                        }
                    }
                    if (pb == null) {
                        PositionRef start = ck.createPositionRef(resolved.element().start(), Position.Bias.Backward);
                        PositionRef end = ck.createPositionRef(resolved.element().end(), Position.Bias.Forward);
                        pb = new PositionBounds(start, end);
                    }
                    try {
                        return new GoToRegionInGrammarAction(resolved.element().name(), resolved.element().kind(), originExt.source().name(), pb);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
        return null;
    }

}

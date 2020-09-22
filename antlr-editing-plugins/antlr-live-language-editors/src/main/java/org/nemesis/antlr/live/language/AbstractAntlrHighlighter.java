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
package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 * Base class for highlighters that takes care of most of the boilerplate of
 * listening and re-running as needed.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractAntlrHighlighter implements AdhocHighlighter {

    protected final Logger LOG = Logger.getLogger(getClass().getName());
    protected final AdhocHighlighterManager mgr;
    private final ZOrder zorder;

    public AbstractAntlrHighlighter(AdhocHighlighterManager mgr, ZOrder zorder) {
        this.mgr = mgr;
        this.zorder = zorder;
    }

    public final ZOrder zorder() {
        return zorder;
    }

    public  void addNotify(JTextComponent comp) {

    }

    public void removeNotify(JTextComponent comp) {

    }

    public void onColoringsChanged() {

    }

    public void onNewHighlightingInfo() {

    }

    protected void onEq(Runnable run) {
        if (EventQueue.isDispatchThread()) {
            run.run();
        } else {
            EventQueue.invokeLater(run);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public abstract HighlightsContainer getHighlightsBag();

    public abstract void refresh(HighlightingInfo info);

}

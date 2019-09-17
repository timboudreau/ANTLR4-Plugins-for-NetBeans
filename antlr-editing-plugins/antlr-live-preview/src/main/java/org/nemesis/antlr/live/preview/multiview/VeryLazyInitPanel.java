/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.preview.multiview;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.nemesis.antlr.live.preview.PreviewPanel;
import org.nemesis.antlr.live.preview.Spinner;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class VeryLazyInitPanel extends JPanel implements Consumer<JComponent> {

    private final RequestProcessor threadPool;
    private final Consumer<Consumer<JComponent>> innerComponentFactory;
    private final JComponent loadingLabel = new Spinner();
    private volatile Future<?> fut;
    private Reference<JComponent> oldInner;
    private boolean showing;

    public VeryLazyInitPanel(Consumer<Consumer<JComponent>> innerComponentFactory, RequestProcessor threadPool) {
        super(new BorderLayout());
        this.innerComponentFactory = innerComponentFactory;
        this.threadPool = threadPool;
//        loadingLabel.setEnabled(false);
    }

    @Override
    public void addNotify() {
        super.addNotify();
    }

    private void danceLikeSomebodysLooking() {
        // the irritating incantation after you update the hierarchy
        invalidate();
        revalidate();
        repaint();
    }

    void showing() {
        System.out.println("showing");
        if (showing) {
            System.out.println(" already showing");
            return;
        }
        showing = true;
        JComponent inner = oldInner == null ? null : oldInner.get();
        if (inner != null) {
            System.out.println("   have old component");
            if (inner.getParent() != this) {
                removeAll();
                add(inner, BorderLayout.CENTER);
                System.out.println("      add it");
                danceLikeSomebodysLooking();
            } else {
                System.out.println("      already a child");
            }
        } else {
            if (getComponentCount() == 0) {
                System.out.println("  add the loading label");
                add(loadingLabel, BorderLayout.CENTER);
                danceLikeSomebodysLooking();
            }
            System.out.println("   start background init");
            fut = threadPool.submit(() -> {
                try {
                    System.out.println("     background init running");
                    innerComponentFactory.accept(this);
                } catch (Exception | Error e) {
                    Logger.getLogger(VeryLazyInitPanel.class.getName()).log(
                            Level.INFO, "Failed opening preview", e);
                }
            });
        }
    }

    void hidden() {
        System.out.println("hidden");
        if (!showing) {
            System.out.println(" already hidden");
            return;
        }
        showing = false;
        Future<?> f = fut;
        if (f != null) {
            f.cancel(false);
            if (fut == f) {
                fut = null;
            }
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        hidden();
    }

    @Override
    public void accept(JComponent t
    ) {
        System.out.println("Received component from background: " + t);
        assert EventQueue.isDispatchThread();
        fut = null;
        if (isDisplayable()) {
            oldInner = new WeakReference<>(t);
            if (t == null) {
                removeAll();
                add(loadingLabel, BorderLayout.CENTER);
            } else {
                removeAll();
                add(t, BorderLayout.CENTER);
            }
            danceLikeSomebodysLooking();
            if (t instanceof PreviewPanel) {
                ((PreviewPanel) t).notifyShowing();
            }
        }
    }
}

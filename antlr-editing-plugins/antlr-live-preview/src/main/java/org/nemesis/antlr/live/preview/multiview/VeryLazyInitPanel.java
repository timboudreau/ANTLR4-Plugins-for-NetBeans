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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.nemesis.antlr.live.preview.PreviewPanel;
import org.nemesis.antlr.live.preview.Spinner;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class VeryLazyInitPanel extends JPanel implements Consumer<JComponent> {

    private final RequestProcessor threadPool;
    private final Consumer<Consumer<JComponent>> innerComponentFactory;
    private final JComponent spinner = new Spinner();
    private volatile Future<?> fut;
    private Reference<JComponent> oldInner;
    private boolean showing;
    private final JLabel status = new JLabel();

    public VeryLazyInitPanel(Consumer<Consumer<JComponent>> innerComponentFactory, RequestProcessor threadPool) {
        super(new BorderLayout());
        this.innerComponentFactory = innerComponentFactory;
        this.threadPool = threadPool;
        status.setEnabled(false);
        status.setMinimumSize(new Dimension(100, 40));
        status.setHorizontalTextPosition(SwingConstants.CENTER);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        add(spinner, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    void status(String status) {
        Mutex.EVENT.readAccess(() -> {
            this.status.setText(status);
            this.status.paintImmediately(0, 0, this.status.getWidth(),
                    this.status.getHeight());
        });
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

    @Messages("initializing=Initializing Language Support...")
    void showing() {
        if (showing) {
            return;
        }
        showing = true;
        JComponent inner = oldInner == null ? null : oldInner.get();
        if (inner != null) {
            if (inner.getParent() != this) {
                removeAll();
                add(inner, BorderLayout.CENTER);
                danceLikeSomebodysLooking();
            }
        } else {
            if (getComponentCount() == 0) {
                add(spinner, BorderLayout.CENTER);
                add(status, BorderLayout.SOUTH);
                danceLikeSomebodysLooking();
            }
            status.setText(Bundle.initializing());
            fut = threadPool.submit(() -> {
                try {
                    innerComponentFactory.accept(this);
                } catch (Exception | Error e) {
                    Logger.getLogger(VeryLazyInitPanel.class.getName()).log(
                            Level.INFO, "Failed opening preview", e);
                }
            });
        }
    }

    void hidden() {
        if (!showing) {
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
        status.setText(" ");
        fut = null;
        if (isDisplayable()) {
            oldInner = new WeakReference<>(t);
            if (t == null) {
                removeAll();
                add(spinner, BorderLayout.CENTER);
                add(status, BorderLayout.SOUTH);
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

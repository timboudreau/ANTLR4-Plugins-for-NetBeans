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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class VeryLazyInitPanel extends JPanel implements Consumer<JComponent> {

    private final RequestProcessor threadPool;
    private final Consumer<Consumer<JComponent>> innerComponentFactory;
    private final JLabel loadingLabel = new JLabel("Really Loading...");
    private Future<?> fut;
    private Reference<JComponent> oldInner;

    public VeryLazyInitPanel(Consumer<Consumer<JComponent>> innerComponentFactory, RequestProcessor threadPool) {
        super(new BorderLayout());
        add(loadingLabel, BorderLayout.CENTER);
        this.innerComponentFactory = innerComponentFactory;
        this.threadPool = threadPool;
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVerticalAlignment(SwingConstants.CENTER);
        loadingLabel.setEnabled(false);
    }

    @Override
    public void addNotify() {
        super.addNotify();
    }

    void showing() {
        JComponent inner = oldInner == null ? null : oldInner.get();
        if (inner != null) {
            if (inner.getParent() != this) {
                removeAll();
                add(inner, BorderLayout.CENTER);
            }
        } else {
            if (getComponentCount() == 0) {
                add(loadingLabel, BorderLayout.CENTER);
            }
            fut = threadPool.submit(() -> {
                innerComponentFactory.accept(this);
            });
        }
    }

    void hidden() {
        Future<?> f = fut;
        if (f != null) {
            f.cancel(false);
            fut = null;
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        hidden();
    }

    @Override
    public void accept(JComponent t) {
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
            invalidate();
            revalidate();
            repaint();
        }
    }
}

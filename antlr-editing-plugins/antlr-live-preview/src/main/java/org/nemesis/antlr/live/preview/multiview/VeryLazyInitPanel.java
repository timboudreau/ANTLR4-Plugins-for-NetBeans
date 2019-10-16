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

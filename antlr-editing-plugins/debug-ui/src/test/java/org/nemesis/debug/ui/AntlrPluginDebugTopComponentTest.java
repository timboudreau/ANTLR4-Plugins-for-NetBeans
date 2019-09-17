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
package org.nemesis.debug.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.debug.api.Debug;
import org.netbeans.junit.MockServices;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrPluginDebugTopComponentTest {

    private Thread ticksThread;
    private volatile JFrame frame;

    @Test
    public void testUI() throws Throwable {
        if (true) {
            return;
        }
        Throwable[] thrown = new Throwable[1];
        try {
            CountDownLatch latch = testEq(thrown);
            latch.await(30, TimeUnit.SECONDS);
        } catch (Throwable ex) {
            thrown[0] = ex;
        }
        if (thrown[0] != null) {
            throw thrown[0];
        }
    }

    private CountDownLatch testEq(Throwable[] th) throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        EventQueue.invokeLater(() -> {
            AntlrPluginDebugTopComponent comp = new AntlrPluginDebugTopComponent();
            JFrame frame = frame(comp);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    latch.countDown();
                }

                @Override
                public void windowClosed(WindowEvent e) {
                    latch.countDown();
                }
            });
            try {
                comp.componentOpened();

                doTestUI(comp, frame);
            } catch (InterruptedException ex) {
                th[0] = ex;
            } finally {
//                comp.componentClosed();
//                frame.setVisible(false);
//                frame.dispose();
            }
        });
        return latch;
    }

    private void doTestUI(AntlrPluginDebugTopComponent comp, JFrame frame) throws InterruptedException {

    }

    private JFrame frame(AntlrPluginDebugTopComponent comp) {
        frame = new JFrame("Test it");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(600, 800));
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(comp, BorderLayout.CENTER);
        JPanel bot = new JPanel();
        JButton but = new JButton("Do the thing");
        bot.add(but);
        but.addActionListener(ae -> {
            if (wrapped != null) {
                wrapped.run();
            }
        });
        frame.getContentPane().add(bot, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
        comp.componentOpened();
        comp.setVisible(true);
        return frame;
    }

    Thread ticksThread2;
    @BeforeEach
    public void setup() {
        MockServices.setServices(ContextFactoryImpl.class);
        UIManager.put("nb.errorColor", Color.RED);
        UIManager.put("controlShadow", new Color(200, 200, 200));
        ticksThread = new Thread(this::tickAway, "ticker");
        ticksThread.setPriority(Thread.NORM_PRIORITY - 1);
        ticksThread.start();
        ticksThread2 = new Thread(this::tickAway, "ticks2");
        ticksThread.setPriority(Thread.NORM_PRIORITY - 2);
        ticksThread2.start();
    }

    @AfterEach
    public void tearDown() {
        done = true;
        if (ticksThread != null) {
            ticksThread.interrupt();
        }
        if (frame != null) {
            frame.setVisible(false);
        }
    }

    Runnable wrapme = () -> {
        Debug.message("wrap wrapp", () -> {
            Debug.run("R", "sub-wrapped", () -> {
                Debug.message("Hey there");
            });
            return Thread.currentThread().getName();
        });
    };
    Runnable wrapped;
    void tickAway() {
        for (int i = 0; !Thread.interrupted(); i++) {
            final int index = i;
            Debug.run("A", "outer-" + i, () -> {
                Debug.message("First stop " + index, () -> {
                    return "This is the first thing for " + index;
                });
                Debug.runBoolean("Y", "Something Boolean", () -> {
                    boolean result = index % 2 == 0;
                    if (result) {
                        Debug.success("Woo hoo", () -> "Index is " + index + " which is divisisble by 2");
                    } else {
                        Debug.success("Bummer", () -> "Index is " + index + " which is not divisible by 2");
                    }
                    Debug.message("This has no details " + index);
                    return result;
                });
                try {
                    Debug.runObject("Z", "Some more stuff", () -> {
                        Debug.message("Ain't done yet", "Formatting!  Yay! {0}", index);
                        Debug.run("QQ", "Deeper", () -> "Context with details" + index, () -> {
                            Debug.thrown(new IllegalStateException("" + index));
                        });

                        if (wrapped == null) {
                            wrapped = Debug.wrap(wrapme);
                        }

                        return "This also has a message";
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            if (!pause()) {
                break;
            }
        }
    }

    boolean done;

    boolean pause() {
        if (done) {
            return false;
        }
        try {
            Thread.sleep(1000);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            done = true;
            return false;
        }
    }
}

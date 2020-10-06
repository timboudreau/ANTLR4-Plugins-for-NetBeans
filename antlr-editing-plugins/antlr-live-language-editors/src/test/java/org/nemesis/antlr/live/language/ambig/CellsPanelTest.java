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
package org.nemesis.antlr.live.language.ambig;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

public class CellsPanelTest {

    @Test
    public void testSomeMethod() throws InterruptedException {
        if (true) {
            return;
        }
        // For interactive testing
        UIManager.put("controlFont", new Font("Arial", Font.PLAIN, 24));
        UIManager.put("Label.font", new Font("Arial", Font.PLAIN, 24));

        List<List<PT>> paths = new ArrayList<>();
        paths.add(new ArrayList<>());
        paths.add(new ArrayList<>());
        paths.add(new ArrayList<>());

        for (int i = 0; i < 3; i++) {
            String nm;
            switch (i) {
                case 0:
                    nm = "one";
                    break;
                case 1:
                    nm = "two";
                    break;
                case 2:
                    nm = "three";
                    break;
                default:
                    throw new AssertionError(i);
            }

            for (List<PT> p : paths) {
                p.add(PT.of(nm));
            }
        }
        paths.get(0).add(PT.of("this"));
        paths.get(0).add(PT.of("here"));
        paths.get(0).add(PT.of("thing"));
        paths.get(0).add(PT.of("is"));
        paths.get(0).add(PT.of("whee"));

        paths.get(1).add(PT.of("that"));
        paths.get(1).add(PT.of("other"));
        paths.get(1).add(PT.of("thing"));
        paths.get(1).add(PT.of("is"));
        paths.get(1).add(PT.of("ugly"));

        paths.get(2).add(PT.of("something"));
        paths.get(2).add(PT.of("else"));
        paths.get(2).add(PT.of("here"));
        paths.get(2).add(PT.of("thing"));
        paths.get(2).add(PT.of("is"));
        paths.get(2).add(PT.of("entirely"));

        for (int i = 0; i < 3; i++) {
            String nm;
            switch (i) {
                case 0:
                    nm = "tailOne";
                    break;
                case 1:
                    nm = "tailTwo";
                    break;
                case 2:
                    nm = "tailThree";
                    break;
                default:
                    throw new AssertionError(i);
            }

            for (List<PT> p : paths) {
                p.add(PT.of(nm));
            }
        }
        final CountDownLatch latch = new CountDownLatch(1);

        EventQueue.invokeLater(() -> {
            CellsPanel cells = new CellsPanel("stuff", PT.of("whatevs"), paths);
            JPanel pnl = new JPanel(new BorderLayout());
            pnl.add(new JLabel("And here is the stuff"), BorderLayout.NORTH);
            pnl.add(cells, BorderLayout.CENTER);
            JFrame jf = new JFrame();
            jf.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    latch.countDown();
                }
            });
            jf.setContentPane(pnl);
            jf.pack();
            jf.setVisible(true);
        });
        latch.await(60, TimeUnit.SECONDS);
    }
}

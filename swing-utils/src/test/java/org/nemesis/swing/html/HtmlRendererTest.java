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
package org.nemesis.swing.html;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.lang.reflect.InvocationTargetException;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class HtmlRendererTest {

    @Test
    public void testSomeMethod() throws InterruptedException, InvocationTargetException {
        if (true) {
            return;
        }
        Thread[] t = new Thread[1];
        EventQueue.invokeAndWait(() -> {
            t[0] = Thread.currentThread();
            UIManager.put("controlShadow", new Color(164, 164, 164));
            UIManager.put("wookie", new Color(112, 164, 80));

            JList<String> list = new JList<>();
            list.setFont(list.getFont().deriveFont(24F));
            list.setCellRenderer(new Ren());
            DefaultListModel<String> dfm = new DefaultListModel<>();
            dfm.addElement("<b>Bold</b> <i>italic</i> <s>strikethrough</s>");
            dfm.addElement("<b>Bold <font color='!controlShadow'>Shadow SingleQuote</font></b>");
            dfm.addElement("<b>Bold <font color=\"!controlShadow\">Shadow DoubleQuote</font></b>");
            dfm.addElement("<b>Bold</b> notbold <font color='!controlShadow'>Shadow SingleQuote</font>");
            dfm.addElement("<b>Bold</b> notbold <font color=\"!controlShadow\">Shadow DoubleQuote</font>");
            dfm.addElement("notbold <font color='!controlShadow'>Shadow SingleQuote</font>");
            dfm.addElement("notbold <font color=\"!controlShadow\">Shadow DoubleQuote</font>");
            dfm.addElement("<font color='!controlShadow'>Shadow SingleQuote</font> stuff");
            dfm.addElement("<font color=\"!controlShadow\">Shadow DoubleQuote</font> stuff");
            dfm.addElement("<font color='!controlShadow'>Entity &lt; hi &gt; &amp; &quot;</font> stuff");
            list.setModel(dfm);
            JScrollPane pane = new JScrollPane(list);
            JFrame jf = new JFrame();
            jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            jf.setContentPane(pane);
            jf.pack();
            jf.setVisible(true);
        });
        t[0].join();
    }

    static class Ren implements ListCellRenderer<Object> {

        static final I i = new I();
        HtmlRenderer.Renderer r = HtmlRenderer.createRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = r.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            r.setHtml(true);
            r.setIcon(i);
            r.setIconTextGap(5);
            if (index % 3 == 0) {
                r.setCellBackground(Color.yellow);
            }
            r.setText(value.toString());
            return c;
        }
    }

    static class I implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.ORANGE);
            g.fillRect(3, 3, 13, 13);
            g.setColor(new Color(128, 128, 255));
            g.drawRect(3, 3, 13, 13);
            System.out.println("HT: " + c.getHeight());
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

    }
}

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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.nemesis.debug.api.Emitters;
import org.nemesis.debug.spi.Emitter;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.nemesis.debug.ui//AntlrPluginDebug//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "AntlrPluginDebugTopComponent",
        iconBase = "org/nemesis/debug/ui/alternative.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = true)
@ActionID(category = "Window", id = "org.nemesis.debug.ui.AntlrPluginDebugTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_AntlrPluginDebugAction",
        preferredID = "AntlrPluginDebugTopComponent"
)
@Messages({
    "CTL_AntlrPluginDebugAction=AntlrPluginDebug",
    "CTL_AntlrPluginDebugTopComponent=AntlrPluginDebug Window",
    "HINT_AntlrPluginDebugTopComponent=This is a AntlrPluginDebug window"
})
public final class AntlrPluginDebugTopComponent extends TopComponent implements ListSelectionListener {

    private final DefaultListModel<EmittedItem> mdl = new DefaultListModel<>();
    private final Ren ren = new Ren();

    public AntlrPluginDebugTopComponent() {
        System.setProperty("swing.aatext", "true");
        initComponents();
        setName(Bundle.CTL_AntlrPluginDebugTopComponent());
        setToolTipText(Bundle.HINT_AntlrPluginDebugTopComponent());
        putClientProperty(TopComponent.PROP_KEEP_PREFERRED_SIZE_WHEN_SLIDED_IN, Boolean.TRUE);
        itemsList.setModel(mdl);
        itemsList.setCellRenderer(ren);
        itemsList.setFont(itemsList.getFont().deriveFont(Font.PLAIN));
//        itemsList.addMouseListener(listClicks);
        threadsPanel.setLayout(new MultiRowFlowLayout());
    }

//    MouseListener listClicks = new MouseAdapter() {
//        @Override
//        public void mouseClicked(MouseEvent e) {
//            valueChanged(new ListSelectionEvent(itemsList.getModel(), itemsList.getSelectedIndex(), itemsList.getSelectedIndex()));
//        }
//    };
    @Override
    public void valueChanged(ListSelectionEvent e) {
        int ix = e.getLastIndex();
        if (ix >= 0 && ix < itemsList.getModel().getSize()) {
            EmittedItem item = itemsList.getModel().getElementAt(ix);
            detailArea.setText(item.details());
        }
    }

    static class ContextPredicate implements Predicate<EmittedItem> {

        private final String filter;

        public ContextPredicate(String filter) {
            this.filter = filter;
        }

        @Override
        public boolean test(EmittedItem t) {
            boolean matched = false;
            for (String ctx : t.contexts) {
                matched = ctx.contains(filter);
                if (matched) {
                    break;
                }
            }
            return matched;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        filterLabel = new javax.swing.JLabel();
        filterField = new javax.swing.JTextField();
        applyFilterButton = new javax.swing.JButton();
        clearButton = new javax.swing.JButton();
        threadsPanel = new javax.swing.JPanel();
        enableCheckbox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        itemsList = new SizingList();
        jScrollPane2 = new javax.swing.JScrollPane();
        detailArea = new javax.swing.JTextArea();

        jPanel1.setBackground(javax.swing.UIManager.getDefaults().getColor("controlShadow"));
        jPanel1.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED));

        filterLabel.setLabelFor(filterField);
        org.openide.awt.Mnemonics.setLocalizedText(filterLabel, org.openide.util.NbBundle.getMessage(AntlrPluginDebugTopComponent.class, "AntlrPluginDebugTopComponent.filterLabel.text")); // NOI18N

        filterField.setText(org.openide.util.NbBundle.getMessage(AntlrPluginDebugTopComponent.class, "AntlrPluginDebugTopComponent.filterField.text")); // NOI18N
        filterField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyFilterButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(applyFilterButton, org.openide.util.NbBundle.getMessage(AntlrPluginDebugTopComponent.class, "AntlrPluginDebugTopComponent.applyFilterButton.text")); // NOI18N
        applyFilterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyFilterButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(clearButton, org.openide.util.NbBundle.getMessage(AntlrPluginDebugTopComponent.class, "AntlrPluginDebugTopComponent.clearButton.text")); // NOI18N
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        threadsPanel.setLayout(null);

        enableCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(enableCheckbox, org.openide.util.NbBundle.getMessage(AntlrPluginDebugTopComponent.class, "AntlrPluginDebugTopComponent.enableCheckbox.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(filterLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(filterField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(applyFilterButton))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(clearButton)
                        .addGap(18, 18, 18)
                        .addComponent(enableCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(threadsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterLabel)
                    .addComponent(filterField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(applyFilterButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(clearButton)
                    .addComponent(enableCheckbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(threadsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(76, Short.MAX_VALUE))
        );

        itemsList.setModel(mdl);
        jScrollPane1.setViewportView(itemsList);

        detailArea.setColumns(20);
        detailArea.setRows(5);
        jScrollPane2.setViewportView(detailArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane2)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 788, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 249, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void applyFilterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyFilterButtonActionPerformed
        String filterText = filterField.getText().trim();
        if (filterText.isEmpty()) {
            setItemsModel(mdl);
        } else {
            setItemsModel(new FilterListModel(mdl, new ContextPredicate(filterText)));
        }
    }//GEN-LAST:event_applyFilterButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        mdl.removeAllElements();
        threadsPanel.removeAll();
        threadLabels.clear();
    }//GEN-LAST:event_clearButtonActionPerformed

    private void setItemsModel(ListModel<EmittedItem> items) {
        if (items != itemsList.getModel()) {
            int oldSelectedIndex = itemsList.getSelectedIndex();
            EmittedItem oldSelection = oldSelectedIndex >= 0 ? itemsList.getModel().getElementAt(oldSelectedIndex) : null;
            itemsList.setModel(items);
            int newSelection = -1;
            if (oldSelection != null) {
                for (int i = 0; i < items.getSize(); i++) {
                    EmittedItem nue = items.getElementAt(i);
                    if (nue == oldSelection) {
                        newSelection = i;
                        break;
                    }
                }
            }
            if (newSelection == -1) {
                if (items.getSize() > 0) {
                    if (items.getSize() > newSelection) {
                        newSelection = oldSelectedIndex;
                    } else {
                        newSelection = items.getSize() - 1;
                    }
                }
            }
            if (newSelection >= 0) {
                itemsList.setSelectedIndex(newSelection);
                Rectangle r = itemsList.getCellBounds(newSelection, newSelection);
                itemsList.scrollRectToVisible(r);
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton applyFilterButton;
    private javax.swing.JButton clearButton;
    private javax.swing.JTextArea detailArea;
    private javax.swing.JCheckBox enableCheckbox;
    private javax.swing.JTextField filterField;
    private javax.swing.JLabel filterLabel;
    private javax.swing.JList<EmittedItem> itemsList;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JPanel threadsPanel;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        super.componentOpened();
        Emitters.subscribe(em);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        itemsList.getSelectionModel().addListSelectionListener(this);
        Emitters.subscribe(em);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        Emitters.unsubscribe(em);
        itemsList.getSelectionModel().removeListSelectionListener(this);
    }

    @Override
    public void componentClosed() {
        super.componentClosed();
        Emitters.unsubscribe(em);
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.1");
        p.setProperty("collectionEnabled", Boolean.toString(isCollectionEnabled()));
        p.setProperty("filter", filterField.getText());
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        if ("1.1".equals(version)) {
            enableCheckbox.setSelected("true".equals(p.getProperty("collectionEnabled")));
            String filter = p.getProperty("filter");
            if (filter != null) {
                filterField.setText(filter);
                applyFilterButtonActionPerformed(null);
            }
        }
    }

    final Em em = new Em();

    boolean isCollectionEnabled() {
        return enableCheckbox.isSelected();
    }

    private final Map<Long, JLabel> threadLabels = new HashMap<>();
    private static final BasicStroke STROKE = new BasicStroke(0.9F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    void updateThreadLabels(long threadId, String threadName) {
        JLabel lbl = threadLabels.get(threadId);
        if (lbl == null) {
            lbl = new JLabel();
            lbl.setOpaque(true);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            Color color = ren.colorForThreadId(threadId);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(color.darker()),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            lbl.setBackground(color);
            lbl.setText(threadName);
            lbl.setToolTipText("Thread " + threadId + " - " + threadName);
            threadLabels.put(threadId, lbl);
            threadsPanel.add(lbl);
            threadsPanel.invalidate();
            threadsPanel.revalidate();
            threadsPanel.repaint();
        } else {
            if (!lbl.getText().contains(threadName)) {
                lbl.setText(lbl.getText() + " / " + threadName);
            }
        }
    }

    class Em implements Emitter {

        long outerContextEnteredAt;
        String currentContext;
        String outerContext;
        private final LinkedList<String> contexts = new LinkedList<>();

        @Override
        public void enterContext(int depth, long globalOrder, long timestamp, String threadName, long threadId, boolean reentry, String ownerType, String ownerToString, String action, Supplier<String> details, long creationThreadId, String creationThreadString) {
            if (depth == 0) {
                outerContextEnteredAt = timestamp;
            }
            if (depth == 0) {
                outerContext = action;
            }
            currentContext = action;
            contexts.push(action);
            if (isCollectionEnabled()) {
                updateThreadLabels(threadId, threadName);
                EmittedItem item = new EmittedItem(contexts(), outerContextEnteredAt, false, action, depth, timestamp, globalOrder, details, threadId, threadName);
                mdl.addElement(item);
            }
        }

        private List<String> contexts() {
            return new ArrayList<>(contexts);
        }

        @Override
        public void exitContext(int depth) {
            if (contexts.size() > 0) {
                contexts.pop();
            }
        }

        @Override
        public void thrown(int depth, long globalOrder, long timestamp, String threadName, long threadId, Throwable thrown, Supplier<String> stackTrace) {
            if (isCollectionEnabled()) {
                updateThreadLabels(threadId, threadName);
                mdl.addElement(new EmittedItem(contexts(), outerContextEnteredAt, true, thrown.getClass().getSimpleName(), depth, timestamp, globalOrder, stackTrace, threadId, threadName));
            }
        }

        @Override
        public void message(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, Supplier<String> msg) {
            if (isCollectionEnabled()) {
                updateThreadLabels(threadId, threadName);
                mdl.addElement(new EmittedItem(contexts(), outerContextEnteredAt, false, heading, depth, timestamp, globalOrder, msg, threadId, threadName));
            }
        }

        @Override
        public void successOrFailure(int depth, long globalOrder, long timestamp, String threadName, long threadId, String heading, boolean success, Supplier<String> msg) {
            if (isCollectionEnabled()) {
                updateThreadLabels(threadId, threadName);
                mdl.addElement(new EmittedItem(contexts(), outerContextEnteredAt, !success, heading, depth, timestamp, globalOrder, msg, threadId, threadName));
            }
        }
    }
}

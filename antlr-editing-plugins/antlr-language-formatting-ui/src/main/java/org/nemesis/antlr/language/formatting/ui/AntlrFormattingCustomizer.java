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
package org.nemesis.antlr.language.formatting.ui;

import com.mastfrog.util.streams.Streams;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import static org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig.formatPreviewText;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.netbeans.modules.options.editor.spi.PreferencesCustomizer;
import org.netbeans.modules.options.editor.spi.PreviewProvider;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrFormattingCustomizer implements PreferencesCustomizer, PreviewProvider {

    private WeakReference<JPanel> panelInstance;
    private WeakReference<JEditorPane> previewInstance;
    private final UIModel ui;

    AntlrFormattingCustomizer(AntlrFormatterConfig config) {
        this.ui = UIModel.create(config);
    }

    @Override
    public String getId() {
        return "antlr-language-formatting";
    }

    @Override
    @NbBundle.Messages("antlr_formatting=Antlr Formatting")
    public String getDisplayName() {
        return Bundle.antlr_formatting();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public synchronized JComponent getComponent() {
        JPanel pnl = panelInstance == null ? null : panelInstance.get();
        if (pnl == null) {
            pnl = ui.createFormattingPanel();
            panelInstance = new WeakReference<>(pnl);
        }
        return pnl;
    }

    @Override
    @Messages({
        "accessibleName_preview=Antlr Formatting Preview",
        "accessibleDescription_preview=Preview of the formatting that will be applied with these settings for Antlr grammar files"
    })
    public synchronized JComponent getPreviewComponent() {
        JEditorPane preview = previewInstance == null ? null : previewInstance.get();
        if (preview == null) {
            preview = new JEditorPane();
            previewInstance = new WeakReference<>(preview);
            preview.setEditorKit(CloneableEditorSupport.getEditorKit("text/x-g4"));
            preview.setEditable(false);
            String text = formatPreviewText(ui.config().preferences(), previewText());
            System.out.println("PREVIEW TEXT:\n" + text);
            try {
                preview.getDocument().insertString(0, text, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
//            preview.setText(text);
            preview.getAccessibleContext().setAccessibleName(Bundle.accessibleName_preview()); //NOI18N
            preview.getAccessibleContext().setAccessibleDescription(Bundle.accessibleDescription_preview()); //NOI18N

            PropertyChangeListener pcl = evt -> {
                refreshPreview();
            };
            ui.config().addPropertyChangeListener(WeakListeners.propertyChange(pcl, preview));
            preview.putClientProperty("changes", pcl);
//            preview.putClientProperty("HighlightsLayerIncludes", //NOI18N
//                    "^org<br>.netbeans<br>.modules<br>.editor<br>.lib2<br>.highlighting<br>.SyntaxHighlighting$"); //NOI18N
        }
        return preview;
    }

    @Override
    public void refreshPreview() {
        JEditorPane preview = (JEditorPane) getPreviewComponent();
//        String text = formatPreviewText(ui.config().preferences(), previewText());
//        System.out.println("PREVIEW TEXT:\n" + text);
//        try {
//            preview.getDocument().remove(0, preview.getDocument().getLength());
//            preview.getDocument().insertString(0, text, null);
//        } catch (BadLocationException ex) {
//            Exceptions.printStackTrace(ex);
//        }

//        /*
//        preview.setText();
        BaseDocument doc = (BaseDocument) preview.getDocument();
        String text = previewText();
        doc.runAtomic(() -> {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, text, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
        Reformat reformat = Reformat.get(doc);
        reformat.lock();
        try {
            doc.runAtomic(() -> {
                try {
                    reformat.reformat(0, doc.getLength());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
        } finally {
            reformat.unlock();
        }
    }

    private static String previewText() {
        try (InputStream in = AntlrFormattingCustomizer.class.getResourceAsStream("Sensors-g4.txt")) {
            if (in == null) {
                throw new IOException("Sensors-g4.txt not adjacent to " + AntlrFormattingCustomizer.class.getName());
            }
            return Streams.readUTF8String(in);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return "Could not load sample";
        }
    }

}

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
package org.nemesis.antlr.language.formatting.ui;

import com.mastfrog.util.streams.Streams;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import static org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig.formatPreviewText;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.netbeans.modules.options.editor.spi.PreferencesCustomizer;
import org.netbeans.modules.options.editor.spi.PreviewProvider;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.NbDocument;
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
        StyledDocument doc = (StyledDocument) preview.getDocument();
        String text = previewText();
        NbDocument.runAtomic(doc, () -> {
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
            reformat.reformat(0, doc.getLength());
        } catch (BadLocationException ex) {
            Logger.getLogger(AntlrFormattingCustomizer.class.getName())
                    .log(Level.INFO, "Reformatting preview", ex);
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

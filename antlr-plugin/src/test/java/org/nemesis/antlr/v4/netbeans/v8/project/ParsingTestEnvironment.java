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
package org.nemesis.antlr.v4.netbeans.v8.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.implspi.EnvironmentFactory;
import org.netbeans.modules.parsing.implspi.SchedulerControl;
import org.netbeans.modules.parsing.implspi.SourceControl;
import org.netbeans.modules.parsing.implspi.SourceEnvironment;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.netbeans.spi.editor.document.DocumentFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public final class ParsingTestEnvironment {

    public static void init(Class<?>... additional) {
        List<Class<?>> all = new ArrayList<>(Arrays.asList(EnvironmentFactoryImpl.class, DF.class));
        all.addAll(Arrays.asList(additional));
        MockServices.setServices(all.toArray(new Class<?>[0]));
    }

    public static void setSourceForParse(Source source) {
        EnvironmentFactoryImpl ev = Lookup.getDefault().lookup(EnvironmentFactoryImpl.class);
        ev.source = source;
    }

    public static final class EnvironmentFactoryImpl implements EnvironmentFactory {

        public EnvironmentFactoryImpl() {

        }

        Source source;

        @Override
        public Lookup getContextLookup() {
            return Lookup.getDefault();
        }

        @Override
        public Class<? extends Scheduler> findStandardScheduler(String string) {
            return Sched.class;
        }

        @Override
        public Parser findMimeParser(Lookup lkp, String string) {
            return new NBANTLRv4Parser();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Collection<? extends Scheduler> getSchedulers(Lookup lkp) {
            Scheduler result = new Sched();
            return Collections.singleton(result);
        }

        @Override
        public SourceEnvironment createEnvironment(Source source, SourceControl sc) {
            return new SourceEnvironment(sc) {
                @Override
                public Document readDocument(FileObject fo, boolean bln) throws IOException {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    FileUtil.copy(fo.getInputStream(), out);
                    String data = new String(out.toByteArray(), UTF_8);
                    Document doc = new DefaultStyledDocument();
                    try {
                        doc.insertString(0, data, null);
                    } catch (BadLocationException ex) {
                        throw new AssertionError(ex);
                    }
                    doc.putProperty("mimeType", "text/x-g4");
                    return doc;
                }

                @Override
                public void attachScheduler(SchedulerControl sc, boolean bln) {
                    // do nothing
                    sc.sourceChanged(source);
                }

                @Override
                public void activate() {
                    // do nothing
                }

                @Override
                public boolean isReparseBlocked() {
                    return false;
                }
            };
        }

        @Override
        public <T> T runPriorityIO(Callable<T> clbl) throws Exception {
            T result = clbl.call();
            System.out.println("CB RES " + result);
            return result;
        }
    }

    public static DocumentFactory newDocumentFactory() {
        return new DF("text/x-g4");
    }
    public static DocumentFactory newDocumentFactory(String mimeType) {
        return new DF(mimeType);
    }

    public static DocumentFactory newDocumentFactory(String mimeType, Supplier<Document> supp) {
        return new DF(mimeType, supp);
    }

    public static class DF implements DocumentFactory {

        private final Supplier<Document> supp;
        private final String mimeType;

        public DF() {
            this("text/x-g4");
        }

        public DF(String mimeType, Supplier<Document> supp) {
            this.mimeType = mimeType;
            this.supp = supp;
        }

        public DF(String mimeType) {
            this.supp = DefaultStyledDocument::new;
            this.mimeType = mimeType;
        }

        @Override
        public Document createDocument(String string) {
            Document doc = supp.get();
            try {
                doc.insertString(0, string, null);
            } catch (BadLocationException ex) {
                throw new AssertionError(ex);
            }
            doc.putProperty("mimeType", "text/x-g4");
            return doc;
        }

        @Override
        public Document getDocument(FileObject fo) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = fo.getInputStream()) {
                FileUtil.copy(in, out);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            Document doc = new DefaultStyledDocument();
            try {
                doc.insertString(0, new String(out.toByteArray(), UTF_8), null);
            } catch (BadLocationException ex) {
                throw new AssertionError(ex);
            }
            doc.putProperty("mimeType", mimeType);
            doc.putProperty(Document.StreamDescriptionProperty, fo);
//            doc.putProperty("fo", fo);
            return doc;
        }

        @Override
        public FileObject getFileObject(Document dcmnt) {
            Object fo = dcmnt.getProperty(Document.StreamDescriptionProperty);
            if (fo != null) {
                if (fo instanceof FileObject) {
                    return (FileObject) fo;
                }
                if (fo instanceof DataObject) {
                    return ((DataObject) fo).getPrimaryFile();
                }
            }
            return null;
        }
    }

    public static final class Sched extends Scheduler {

        @Override
        protected SchedulerEvent createSchedulerEvent(SourceModificationEvent sme) {
            return new Ev(sme.getSource());
        }

        static final class Ev extends SchedulerEvent {

            public Ev(Object source) {
                super(source);
            }

        }
    }

}

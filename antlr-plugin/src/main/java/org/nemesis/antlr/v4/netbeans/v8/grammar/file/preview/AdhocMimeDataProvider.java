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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = MimeDataProvider.class, position = Integer.MAX_VALUE - 1)
public class AdhocMimeDataProvider implements MimeDataProvider {

    static final Logger LOG = Logger.getLogger(AdhocMimeDataProvider.class.getName());

    private final LanguageIC lang = new LanguageIC();
    private final FontColorsIC fcic = new FontColorsIC();
    private final Map<String, MimeEntry> lookups = new ConcurrentHashMap<>();

    @Override
    public Lookup getLookup(MimePath mp) {
        MimeEntry entry = lookups.get(mp.getPath());
        return entry == null ? Lookup.EMPTY : entry.getLookup();
    }

    void removeMimeType(String mt) {
        MimeEntry entry = lookups.remove(mt);
        entry.shutdown();
        AdhocLanguageFactory.get().discard(mt);
        AdhocColoringsRegistry.getDefault().remove(mt);
    }

    public Lookup getLookup(String mimeType) {
        MimeEntry entry = lookups.get(mimeType);
        return entry == null ? Lookup.EMPTY : entry.getLookup();
    }

    void addMimeType(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy parseResult) {
        MimeEntry en = new MimeEntry(buildResult, parseResult, lang, fcic);
        LOG.log(Level.FINER, "Add mime entries for {0}", AdhocMimeTypes.loggableMimeType(en.mimeType));
        lookups.put(en.mimeType, en);
    }

    void updateMimeType(GenerateBuildAndRunGrammarResult buildResult, AntlrProxies.ParseTreeProxy lastParseResult) {
        String mime = lastParseResult.mimeType();
        LOG.log(Level.FINER, "Update parsing environment for {0}", AdhocMimeTypes.loggableMimeType(mime));
        AdhocLanguageFactory.get().update(lastParseResult);
        MimeEntry me = lookups.get(mime);
        if (me != null) {
            AdhocParserFactory pf = me.pf;
            if (pf != null) {
                pf.updated(buildResult, lastParseResult);
            }
        }
    }

    static final class DebugLookup extends Lookup {

        private final AbstractLookup lkp;

        DebugLookup(AbstractLookup lkp) {
            this.lkp = lkp;
        }

        @Override
        public <T> T lookup(Class<T> type) {
            System.out.println("LOOKUP TYPE " + type.getName());
            return lkp.lookup(type);
        }

        @Override
        public <T> Result<T> lookup(Template<T> tmplt) {
            System.out.println("LOKUP TMPL " + tmplt.getId() + " " + tmplt.getType().getName());
            return lkp.lookup(tmplt);
        }
    }

    static final class MimeEntry {

        private final InstanceContent content = new InstanceContent();
        private final Lookup lookup;
        private final AdhocEditorKit kit;
        private final AdhocHighlightLayerFactory layers;
        private final LanguageIC cvt;
        private final String mimeType;
        private final AdhocParserFactory pf;
        private final FontColorsIC fcic;
        private final TaskFactory errorHighlighter;

        MimeEntry(GenerateBuildAndRunGrammarResult buildResult,
                AntlrProxies.ParseTreeProxy prox, LanguageIC cvt, FontColorsIC fcic) {
            this.cvt = cvt;
            this.fcic = fcic;
            this.mimeType = prox.mimeType();
            content.add(kit = new AdhocEditorKit(mimeType));
            content.add(mimeType, fcic);
            content.add(mimeType, cvt);
            content.add(pf = new AdhocParserFactory(buildResult, prox));
            content.add(layers = new AdhocHighlightLayerFactory(mimeType));
            content.add(errorHighlighter = AdhocErrorsHighlighter.create());
//            this.lookup = new DebugLookup(new AbstractLookup(content));
            this.lookup = new AbstractLookup(content);
        }

        public Lookup getLookup() {
            return lookup;
        }

        private void shutdown() {
            // Ensure listeners get notified of things
            // going away, rather than just going silent
            content.remove(kit);
            content.remove(mimeType, cvt);
            content.remove(mimeType, fcic);
            content.remove(layers);
            content.remove(pf);
            content.remove(errorHighlighter);
        }
    }

    static final class FontColorsIC implements InstanceContent.Convertor<String, AdhocColorings> {

        @Override
        public AdhocColorings convert(String t) {
            return AdhocColoringsRegistry.getDefault().get(t);
        }

        @Override
        public Class<? extends AdhocColorings> type(String t) {
            return AdhocColorings.class;
        }

        @Override
        public String id(String t) {
            return "adhoc-colorings-" + t;
        }

        @Override
        public String displayName(String t) {
            return id(t);
        }
    }

    static final class LanguageIC implements InstanceContent.Convertor<String, Language<?>> {

        @Override
        public Language<?> convert(String t) {
            return AdhocLanguageFactory.get().language(t);
        }

        @Override
        @SuppressWarnings(value = {"rawtypes", "unchecked"})
        public Class<? extends Language<?>> type(String t) {
            Class c = Language.class;
            return c;
        }

        @Override
        public String id(String t) {
            return t;
        }

        @Override
        public String displayName(String t) {
            return t;
        }
    }
}

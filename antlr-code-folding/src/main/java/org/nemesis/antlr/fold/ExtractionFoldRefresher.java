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
package org.nemesis.antlr.fold;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.misc.utils.Iterables;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
abstract class ExtractionFoldRefresher {

    private static ExtractionFoldRefresher INSTANCE;
    private static final Logger LOG = Logger.getLogger(ExtractionFoldRefresher.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    public static ExtractionFoldRefresher getDefault() {
        if (INSTANCE == null) {
            // This allows us to have synchronous exection for tests
            INSTANCE = Lookup.getDefault().lookup(ExtractionFoldRefresher.class);
            if (INSTANCE == null) {
                INSTANCE = new DefaultExtractionFoldRefresher();
            }
        }
        return INSTANCE;
    }

    private final Map<RegionsKey<?>, Map<DataObject, ExtractionFoldTask<?>>> taskForKeyForFile
            = Iterables.supplierMap(WeakHashMap::new);

    ExtractionFoldTask<?> getExistingTask(RegionsKey<?> key, FileObject file) {
        Map<DataObject, ExtractionFoldTask<?>> map = taskForKeyForFile.get(key);
        if (map == null) {
            return null;
        }
        try {
            DataObject dob = DataObject.find(file);
            return map.get(dob);
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    <T> ExtractionFoldTask<T> getTask(FileObject file, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        try {
            DataObject od = DataObject.find(file);
            synchronized (taskForKeyForFile) {
                Map<DataObject, ExtractionFoldTask<?>> taskForFile = taskForKeyForFile.get(key);
                ExtractionFoldTask<T> task = (ExtractionFoldTask<T>) taskForFile.get(od);
                if (task == null) {
                    LOG.log(Level.FINE, "Create a new ExtractionFoldTask for {0} and {1}",
                            new Object[] { file, key});
                    taskForFile.put(od, task = new ExtractionFoldTask<>(key, converter));
                }
                return task;
            }
        } catch (DataObjectNotFoundException ex) {
            Logger.getLogger(ExtractionElementsFoldManager.class.getName()).log(Level.FINE, null, ex);
//            return new ExtractionFoldTask(AntlrKeys.FOLDABLES, converter);
            return null;
        }
    }

    public abstract <T> void scheduleRefresh(FileObject fo, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter);

    static final class DefaultExtractionFoldRefresher extends ExtractionFoldRefresher {

        final RequestProcessor FOLDS_REFRESH = new RequestProcessor("antlr-folds-refresh", 3);

        private final Map<RegionsKey<?>, Map<FileObject, RequestProcessor.Task>> refreshTaskForKeyForFile
                = Iterables.supplierMap(WeakHashMap::new);

        private <T> RequestProcessor.Task findTask(FileObject fo, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
            Map<FileObject, RequestProcessor.Task> taskForFileObject = refreshTaskForKeyForFile.get(key);
            RequestProcessor.Task task = taskForFileObject.get(fo);
            if (task == null) {
                Refresher<T> refresher = new Refresher<>(fo, this, key, converter);
                task = FOLDS_REFRESH.create(refresher);
                taskForFileObject.put(fo, task);
            }
            return task;
        }

        @Override
        public <T> void scheduleRefresh(FileObject fo, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
            RequestProcessor.Task task = findTask(fo, key, converter);
            if (task != null) {
                task.schedule(100);
            }
        }
    }

    static final class Refresher<T> implements Runnable {

        private final FileObject file;
        private final DefaultExtractionFoldRefresher refresher;
        private final RegionsKey<T> key;
        private final SemanticRegionToFoldConverter<T> converter;

        Refresher(FileObject file, DefaultExtractionFoldRefresher refresher, RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
            this.file = file;
            this.refresher = refresher;
            this.key = key;
            this.converter = converter;
        }

        @Override
        public void run() {
            if (!file.isValid()) {
                return;
            }
            ExtractionFoldTask task = refresher.getTask(file, key, converter);
            if (task != null) {
                task.reparse(file);
            }
        }

        public String toString() {
            return file.getPath();
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 13 * hash + Objects.hashCode(this.file);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Refresher other = (Refresher) obj;
            return Objects.equals(this.file, other.file);
        }
    }
}

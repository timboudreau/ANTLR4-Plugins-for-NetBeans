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

package org.nemesis.antlr.live;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
public class FakeFolderLoader extends DataLoader {

    public FakeFolderLoader() {
        super(DataFolder.class);
    }

    @Override
    protected DataObject handleFindDataObject(FileObject fo, RecognizedFiles rf) throws IOException {
        if (fo.isFolder()) {
            rf.markRecognized(fo);
            return new DataFolder(fo);
        }
        return null;
    }

}

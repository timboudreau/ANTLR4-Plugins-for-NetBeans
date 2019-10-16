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
package org.nemesis.antlr.v4.netbeans.v8;

import java.util.Optional;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=RelativeResolverImplementation.class, path="antlr-languages/relative-resolvers/text/x-g4")
public class AntlrFileObjectRelativeResolver extends RelativeResolverImplementation<FileObject> {

    public AntlrFileObjectRelativeResolver() {
        super(FileObject.class);
    }

    @Override
    public Optional<FileObject> resolve(FileObject relativeTo, String name) {
        return ProjectHelper.resolveRelativeGrammar(relativeTo, name);
    }
}

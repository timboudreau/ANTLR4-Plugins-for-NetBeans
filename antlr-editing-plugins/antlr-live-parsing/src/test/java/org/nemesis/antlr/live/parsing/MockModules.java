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

package org.nemesis.antlr.live.parsing;

import edu.emory.mathcs.backport.java.util.Collections;
import java.util.Set;
import org.openide.modules.Dependency;
import org.openide.modules.ModuleInfo;
import org.openide.modules.Modules;
import org.openide.modules.SpecificationVersion;

/**
 *
 * @author Tim Boudreau
 */
public class MockModules extends Modules {

    @Override
    public ModuleInfo ownerOf(Class<?> clazz) {
        return FakeModuleInfo.INSTANCE;
    }

    @Override
    public ModuleInfo findCodeNameBase(String cnb) {
        return FakeModuleInfo.INSTANCE;
    }

    private static final class FakeModuleInfo extends ModuleInfo {
        private static final FakeModuleInfo INSTANCE = new FakeModuleInfo();

        @Override
        public String getCodeNameBase() {
            return "antlr.editor.plugins.parent";
        }

        @Override
        public int getCodeNameRelease() {
            return 1;
        }

        @Override
        public String getCodeName() {
            return getCodeNameBase();
        }

        @Override
        public SpecificationVersion getSpecificationVersion() {
            return new SpecificationVersion("1.0");
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public Object getAttribute(String string) {
            return null;
        }

        @Override
        public Object getLocalizedAttribute(String string) {
            return null;
        }

        @Override
        public Set<Dependency> getDependencies() {
            return Collections.emptySet();
        }

        @Override
        public boolean owns(Class<?> type) {
            return true;
        }
    }
}

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
package com.mastfrog.antlr.project.helpers.ant;

import java.util.Collections;
import java.util.Set;
import org.netbeans.contrib.yenta.Yenta;

public final class Installer extends Yenta {

    @Override
    public Set<String> friends() {
        /*
        Sigh - this results in:
WARNING [org.netbeans.core.startup.NbEvents]: The extension /home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-suite/target/antlr/ide/modules/ext/jcodings-1.0.18.jar may be multiply loaded by modules: [/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-suite/target/antlr/ide/modules/org-netbeans-libs-bytelist.jar, /home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/antlr-suite/target/antlr/ide/modules/org-netbeans-modules-textmate-lexer.jar]; see: http://www.netbeans.org/download/dev/javadoc/org-openide-modules/org/openide/modules/doc-files/classpath.html#class-path
java.lang.AssertionError: Already had dead JAR: /tmp/org-netbeans-modules-project-ant12760243590029093585.jar
	at org.netbeans.JarClassLoader$JarSource.destroy(JarClassLoader.java:676)
	at org.netbeans.JarClassLoader.destroy(JarClassLoader.java:317)
	at org.netbeans.StandardModule.classLoaderDown(StandardModule.java:556)
	at org.netbeans.ModuleManager.enable(ModuleManager.java:1398)
	at org.netbeans.ModuleManager.enable(ModuleManager.java:1254)
	at org.netbeans.core.startup.ModuleList.installNew(ModuleList.java:315)
	at org.netbeans.core.startup.ModuleList.trigger(ModuleList.java:251)
	at org.netbeans.core.startup.ModuleSystem.restore(ModuleSystem.java:298)
	at org.netbeans.core.startup.Main.getModuleSystem(Main.java:156)
	at org.netbeans.core.startup.Main.getModuleSystem(Main.java:125)
	at org.netbeans.core.startup.Main.start(Main.java:282)
	at org.netbeans.core.startup.TopThreadGroup.run(TopThreadGroup.java:98)
	at java.base/java.lang.Thread.run(Thread.java:832)
         */
//        return Collections.singleton("org.netbeans.modules.java.j2seproject/1");
        return Collections.singleton("org.netbeans.modules.java.api.common");
    }
}

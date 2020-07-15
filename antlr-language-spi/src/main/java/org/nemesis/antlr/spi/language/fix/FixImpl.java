/*
 * Copyright 2020 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language.fix;

import java.util.function.Supplier;
import org.nemesis.editor.edit.Applier;
import org.nemesis.editor.edit.EditBag;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Tim Boudreau
 */
final class FixImpl implements Fix {
    private final Applier applier;
    private final Supplier<? extends CharSequence> description;
    private final EditBag changes;
    private final boolean letEditorInfrastructureSetCaretPosition;

    public FixImpl( Applier applier, Supplier<? extends CharSequence> description, EditBag changes,
            boolean letEditorInfrastructureSetCaretPosition ) {
        this.applier = applier;
        this.description = description;
        this.changes = changes;
        this.letEditorInfrastructureSetCaretPosition = letEditorInfrastructureSetCaretPosition;
    }

    @Override
    public String getText() {
        return description.get().toString();
    }

    @Override
    public ChangeInfo implement() throws Exception {
        // What's going on here:
        //
        // If a fix returns a ChangeInfo, the editor somehow or other tries to
        // set the caret selection to its bounds;  in the case of deletions or
        // multiple discontiguous selections, the results are fairly random, and
        // almost always wrong (a deletion will randomly select some range of
        // characters that would have been in the bounds of the deletion before
        // it occurred - and with multiple deletions, the selection might be related to
        // any random one of them, with no discernable rhyme or reason)
        //
        // editor-utils has reliable, good caret handling and allows us to
        // provide a CaretPositionCalculator to explicitly specify where we want
        // to end up, and with the exception of simple insertions, that is almost
        // always preferable
        ChangeInfo info = new ChangeInfo();
        applier.apply();
        if ( letEditorInfrastructureSetCaretPosition ) {
            FileObject fo = NbEditorUtilities.getFileObject( changes.document() );
            changes.visitChanges( ( kind, start, end ) -> {
                info.add( fo, start, end == null ? start : end );
            } );
        }
        return info;
    }
}

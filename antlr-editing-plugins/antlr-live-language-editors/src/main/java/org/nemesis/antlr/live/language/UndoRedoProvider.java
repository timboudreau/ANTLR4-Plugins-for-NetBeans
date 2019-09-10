package org.nemesis.antlr.live.language;

import org.openide.awt.UndoRedo;

/**
 * Allows AdhocDataObject's DataEditorSupport to expose its undo manager so that
 * the preview multiview element can supply it.
 *
 * @author Tim Boudreau
 */
public interface UndoRedoProvider {

    public UndoRedo get();

}

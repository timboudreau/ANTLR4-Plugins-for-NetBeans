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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.folding;

import java.util.ArrayList;
import java.util.Collection;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;

import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.spi.editor.fold.FoldTypeProvider;

import org.netbeans.api.editor.mimelookup.MimeRegistration;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@MimeRegistration(mimeType = ANTLR_MIME_TYPE, service = FoldTypeProvider.class, position = 667)
public class GrammarFoldTypeProvider implements FoldTypeProvider {
    private final Collection<FoldType> types = new ArrayList<>();

    public GrammarFoldTypeProvider() {
        types.add(GrammarFoldType.COMMENT_FOLD_TYPE);
        types.add(GrammarFoldType.ACTION_FOLD_TYPE);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    // the API really is Class, not Class<?>
    public Collection<? extends FoldType> getValues(@SuppressWarnings({"unchecked", "rawtypes"}) Class type) {
        return type == FoldType.class ? types : null;
    }

    @Override
    public boolean inheritable() {
        return false;
    }
}
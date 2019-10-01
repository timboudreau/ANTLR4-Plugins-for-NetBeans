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
package org.nemesis.antlr.language.formatting.ui;

import org.nemesis.antlr.language.formatting.config.ColonHandling;
import java.util.Objects;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;

/**
 *
 * @author Tim Boudreau
 */
final class ColonHandlingRadioButtonModel extends ConfigButtonModel<ColonHandling> {

    private final ColonHandling what;

    ColonHandlingRadioButtonModel(AntlrFormatterConfig config, ColonHandling what) {
        super(config, AntlrFormatterConfig.KEY_COLON_HANDLING, config::getColonHandling);
        this.what = what;
    }

    @Override
    protected void onChange(ColonHandling oldValue, ColonHandling newValue) {
        boolean shouldSelect = Objects.equals(what, newValue);
        if (shouldSelect != isSelected()) {
            setSelected(shouldSelect);
        }
    }

    @Override
    protected void onAction() {
        if (!isSelected()) {
            config.setColonHandling(what);
        }
    }
}
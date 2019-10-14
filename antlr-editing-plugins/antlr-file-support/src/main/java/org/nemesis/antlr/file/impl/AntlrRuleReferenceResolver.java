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
package org.nemesis.antlr.file.impl;

import java.io.IOException;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ResolutionConsumer;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.extraction.attribution.ImportBasedResolver;
import org.nemesis.extraction.attribution.RegisterableResolver;
import org.nemesis.source.api.GrammarSource;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RegisterableResolver.class, path = "antlr/resolvers/text/x-g4")
public class AntlrRuleReferenceResolver implements RegisterableResolver<RuleTypes> {

    private final ImportBasedResolver<RuleTypes> instance;
    static AntlrRuleReferenceResolver INSTANCE;

    public AntlrRuleReferenceResolver() {
        this.instance = ImportBasedResolver.create(AntlrKeys.RULE_NAMES,
                AntlrKeys.IMPORTS);
        INSTANCE = this;
    }

    static boolean instanceCreated() {
        return INSTANCE != null;
    }

    @Override
    public <X> X resolve(Extraction extraction, UnknownNameReference<RuleTypes> ref, ResolutionConsumer<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes, X> c) throws IOException {
        return instance.resolve(extraction, ref, c);
    }

    @Override
    public Class type() {
        return RuleTypes.class;
    }
}

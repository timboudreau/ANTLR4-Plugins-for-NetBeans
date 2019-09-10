/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.jfs.isolation.pk2;

import org.nemesis.jfs.isolation.RelatedThing;

/**
 *
 * @author Tim Boudreau
 */
public class DoIt {

    private final RelatedThing rl;

    public DoIt(RelatedThing rl) {
        this.rl = rl;
    }

    @Override
    public String toString() {
        return rl.toString();
    }
}

package org.mozilla.javascript.lens;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/** Instruments each new Context as Rhino creates it (e.g. from plain {@code Context.enter()}). */
final class LensContextListener implements ContextFactory.Listener {

    @Override
    public void contextCreated(Context cx) {
        Lens.instrument(cx);
    }

    @Override
    public void contextReleased(Context cx) {
        // nothing to undo
    }
}

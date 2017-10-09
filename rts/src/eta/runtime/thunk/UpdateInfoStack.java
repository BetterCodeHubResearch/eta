package eta.runtime.thunk;

import eta.runtime.stg.Capability;
import eta.runtime.stg.Closure;
import eta.runtime.stg.TSO;

public class UpdateInfoStack {
    private UpdateInfo top;
    private UpdateInfo bottom;

    /* Manages a set of free UpdateInfos to avoid excess allocation */
    private UpdateInfo free;

    public UpdateInfoStack() {}

    public final UpdateInfo push(Thunk updatee) {
        UpdateInfo ui = acquireUpdateInfo(updatee);
        if (bottom == null) {
            pushBottom(ui);
        } else {
            pushMiddle(ui);
        }
        return ui;
    }

    private final void pushMiddle(UpdateInfo ui) {
        ui.prev  = top;
        ui.next  = null;
        top.next = ui;
        top = ui;
    }

    private final void pushBottom(UpdateInfo ui) {
        bottom = top = ui;
        ui.prev = null;
        ui.next = null;
    }

    private final UpdateInfo acquireUpdateInfo(Thunk updatee) {
        UpdateInfo ui;
        if (free != null) {
            ui = grabFreeUpdateInfo(updatee);
        } else {
            ui = new UpdateInfo(updatee);
        }
        return ui;
    }

    private final UpdateInfo grabFreeUpdateInfo(Thunk updatee) {
        UpdateInfo ui = free;
        ui.updatee = updatee;
        free = free.prev;
        return ui;
    }

    public final Thunk pop() {
        UpdateInfo ui = top;
        Thunk res = ui.updatee;
        adjustAfterPop(ui);
        free = ui.reset(free);
        return res;
    }

    private final void adjustAfterPop(UpdateInfo top) {
        top = top.prev;
        if (top == null) {
            bottom = null;
        } else {
            top.next = null;
        }
        this.top = top;
    }

    public final boolean isEmpty() {
        return bottom == null;
    }

    public final UpdateInfo peek() {
        return top;
    }

    public final void clear() {
        top    = null;
        bottom = null;
    }

    public final void raiseExceptionAfter(Capability cap, TSO tso, Closure raise, UpdateInfo ui) {
        top = ui;
        if (ui != null) ui = ui.next;
        else ui = bottom;
        if (top != null) {
            top.next = null;
        } else {
            bottom = null;
        }
        while (ui != null) {
            ui.updatee.updateThunk(cap, tso, raise);
            ui = ui.next;
        }
    }

    public final UpdateInfo markBackwardsFrom(Capability cap, TSO tso) {
        return markBackwardsFrom(cap, tso, null);
    }

    public final UpdateInfo markBackwardsFrom(Capability cap, TSO tso, UpdateInfo ui) {
        if (ui == null) ui = top;
        UpdateInfo suspend = null;
        while (ui != null && !ui.marked) {
            ui.marked = true;
            Thunk bh = ui.updatee;
            do {
                Closure p = bh.indirectee;
                if (p != null) {
                    if (p != tso) {
                        suspend = ui;
                    }
                    break;
                } else {
                    if (bh.tryLock()) {
                        bh.setIndirection(tso);
                        break;
                    } else continue;
                }
            } while (true);
            ui = ui.prev;
        }
        return suspend;
    }
}

package eta.runtime.thunk;

import eta.runtime.stg.StgClosure;
import eta.runtime.stg.StgContext;


public class Ap6Upd extends StgInd {
    public StgClosure p1;
    public StgClosure p2;
    public StgClosure p3;
    public StgClosure p4;
    public StgClosure p5;
    public StgClosure p6;

    public Ap6Upd(final StgClosure p1, final StgClosure p2, final StgClosure p3, final StgClosure p4, final StgClosure p5, final StgClosure p6) {
        super();
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.p4 = p4;
        this.p5 = p5;
        this.p6 = p6;
    }

    @Override
    public StgClosure thunkEnter(StgContext context) {
        return p1.applyPPPPP(context, p2, p3, p4, p5, p6);
    }
}

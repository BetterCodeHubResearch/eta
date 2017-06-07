package eta.runtime.thunk;

import eta.runtime.stg.StgClosure;
import eta.runtime.stg.StgContext;
import eta.runtime.stg.StgConstr;


public class SelectorOUpd extends SelectorUpd {

    public SelectorOUpd(int i, StgClosure p) {
        super(i, p);
    }

    @Override
    public StgClosure selectEnter(StgContext context, StgConstr result) {
        context.O(1, result.getO(index));
        return null;
    }
}

package eta.runtime.io;

import eta.runtime.stg.StgContext;
import eta.runtime.stg.Closure;
import eta.runtime.thunk.Ap2Upd;
import eta.runtime.thunk.SelectorPUpd;
import eta.runtime.RuntimeOptions;

public class IO {

    public static Closure decodeFloat_Int(StgContext context, float f) {
        int bits = Float.floatToRawIntBits(f);
        int s = ((bits >> 31) == 0) ? 1 : -1;
        int e = ((bits >> 23) & 0xff);
        int m = (e == 0) ?
            (bits & 0x7fffff) << 1 :
            (bits & 0x7fffff) | 0x800000;
        context.I(1, s * m);
        context.I(2, e - 150);
        return null;
    }

    public static Closure atomicModifyMutVar(StgContext context, MutVar mv, Closure f) {
        Ap2Upd z = new Ap2Upd(f, null);
        SelectorPUpd y = new SelectorPUpd(1, z);
        SelectorPUpd r = new SelectorPUpd(2, z);
        do {
            Closure x = mv.value;
            z.p2 = x;
            if (!mv.cas(x, y)) {
                continue;
            }
            mv.value = y;
            break;
        } while (true);
        return r;
    }

    public static Closure casMutVar(StgContext context, MutVar mv, Closure old, Closure update) {
        if (mv.cas(old, update)) {
            context.I(1, 0);
            return update;
        } else {
            context.I(1, 1);
            return mv.value;
        }
    }
}

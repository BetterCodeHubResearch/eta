package eta.runtime.stm;

import java.util.Stack;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;

import eta.runtime.stg.TSO;
import eta.runtime.stg.Capability;

import eta.runtime.stg.Closure;
import eta.runtime.stg.StgContext;
import eta.runtime.stg.StackFrame;

public class StgCatchRetryFrame extends StgSTMCatchFrame {
    public boolean runningAltCode;
    public final Closure firstCode;
    public final Closure altCode;

    public StgCatchRetryFrame(final Closure firstCode, final Closure altCode) {
        this(firstCode, altCode, false);
    }

    public StgCatchRetryFrame(final Closure firstCode, final Closure altCode, boolean runningAltCode) {
        this.firstCode = firstCode;
        this.altCode = altCode;
        this.runningAltCode = runningAltCode;
    }

    @Override
    public void stackEnter(StgContext context) {
        Capability cap = context.myCapability;
        TSO tso = context.currentTSO;
        StgTRecHeader trec = tso.trec;
        StgTRecHeader outer = trec.enclosingTrec;
        boolean result = cap.stmCommitNestedTransaction(trec);
        if (result) {
            tso.trec = outer;
        } else {
            StgTRecHeader newTrec = cap.stmStartTransaction(outer);
            tso.trec = newTrec;
            Closure code;
            if (runningAltCode) {
                code = altCode;
            } else {
                code = firstCode;
            }
            tso.sp.add(new StgCatchRetryFrame(firstCode, altCode, runningAltCode));
            code.applyV(context);
        }
    }

    @Override
    public boolean doFindRetry(Capability cap, TSO tso) {
        return false;
    }

    @Override
    public boolean doRetry(Capability cap, TSO tso, StgTRecHeader trec) {
        StgContext context = cap.context;
        StgTRecHeader outer = trec.enclosingTrec;
        cap.stmAbortTransaction(trec);
        cap.stmFreeAbortedTrec(trec);
        if (runningAltCode) {
            tso.trec = outer;
            /* TODO: Ensure stack operations */
            tso.sp.next();
            tso.sp.remove();
            return true;
        } else {
            StgTRecHeader newTrec = cap.stmStartTransaction(outer);
            tso.trec = newTrec;
            runningAltCode = true;
            altCode.applyV(context);
            return false;
        }
    }

    @Override
    public boolean doRaiseExceptionHelper(Capability cap, TSO tso, AtomicReference<Closure> raiseClosure, Closure exception) {
        StgTRecHeader trec = tso.trec;
        StgTRecHeader outer = trec.enclosingTrec;
        cap.stmAbortTransaction(trec);
        cap.stmFreeAbortedTrec(trec);
        tso.trec = outer;
        return true;
    }
}

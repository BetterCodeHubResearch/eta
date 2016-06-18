package ghcvm.runtime.stm;

import java.util.Stack;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.ListIterator;

import ghcvm.runtime.types.StgTSO;
import ghcvm.runtime.types.Capability;
import ghcvm.runtime.closure.StgClosure;
import ghcvm.runtime.closure.StgContext;
import ghcvm.runtime.stackframe.StackFrame;
import ghcvm.runtime.apply.Apply;

public class StgAtomicallyFrame extends StackFrame {
    public final StgClosure code;
    public Queue<StgInvariantCheck> nextInvariants = new ArrayDeque<StgInvariantCheck>();
    public StgClosure result;

    public StgAtomicallyFrame(final StgClosure code) {
        this.code = code;
    }

    public StgAtomicallyFrame(final StgClosure code, Queue<StgInvariantCheck> invariants, StgClosure result) {
        this.code = code;
        nextInvariants = invariants;
        this.result = result;
    }

    @Override
    public void stackEnter(StgContext context) {
        // TODO: Complete
        Capability cap = context.myCapability;
        StgTSO tso = context.currentTSO;
        ListIterator<StackFrame> sp = tso.sp;
        Stack<StgTRecHeader> stack = tso.trec;
        ListIterator<StgTRecHeader> it = stack.listIterator(stack.size());
        StgTRecHeader trec = it.previous();
        StgTRecHeader outer = null;
        StgClosure result = this.result;
        if (it.hasPrevious()) {
            outer = it.previous();
        }
        Queue<StgInvariantCheck> invariants = null;
        if (outer == null) {
            invariants = cap.stmGetInvariantsToCheck(trec);
            result = context.R1;
        } else {
            invariants = nextInvariants;
            StgInvariantCheck check = nextInvariants.peek();
            check.myExecution = trec; // TODO: Should this be trec stack?
            cap.stmAbortTransaction(stack);
            nextInvariants.poll();
            trec = outer;
            stack.pop();
        }

        if (invariants.isEmpty()) {
            boolean valid = cap.stmCommitTransaction(stack);
            if (valid) {
            } else {
            }
        } else {
            trec = cap.stmStartTransaction(trec);
            stack.push(trec);
            StgInvariantCheck q = invariants.peek();
            StgAtomicInvariant invariant = q.invariant;
            context.R1 = invariant.code;
            sp.add(new StgAtomicallyFrame(code, invariants, result));
            Apply.ap_v_fast.enter(context);
        }
    }

}

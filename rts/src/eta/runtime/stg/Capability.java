package eta.runtime.stg;

import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Deque;
import java.util.Stack;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;
import eta.runtime.stg.Task.InCall;
import static eta.runtime.Rts.*;
import static eta.runtime.RuntimeLogging.*;
import static eta.runtime.Rts.ExitCode.*;
import static eta.runtime.RtsScheduler.*;
import static eta.runtime.RtsScheduler.SchedulerState.*;
import static eta.runtime.RtsScheduler.SchedulerStatus.*;
import static eta.runtime.stg.TSO.*;
import static eta.runtime.stg.TSO.WhatNext.*;
import static eta.runtime.stg.TSO.WhyBlocked.*;
import static eta.runtime.stg.StgContext.*;
import static eta.runtime.stg.StgContext.ReturnCode.*;
import static eta.runtime.RtsScheduler.RecentActivity.*;
import static eta.runtime.interpreter.Interpreter.*;
import eta.runtime.*;
import eta.runtime.thunk.*;
import eta.runtime.thread.*;
import eta.runtime.stg.*;
import eta.runtime.exception.*;
import eta.runtime.stm.*;
import eta.runtime.message.*;
import eta.runtime.concurrent.*;
import eta.runtime.parallel.*;
import eta.runtime.apply.*;
import static eta.runtime.stg.StackFrame.MarkFrameResult;
import static eta.runtime.stg.StackFrame.MarkFrameResult.*;

public final class Capability {
    public static List<Capability> capabilities = new ArrayList<Capability>();
    public static Set<Capability> workerCapabilities
        = Collections.newSetFromMap(new ConcurrentHashMap<Capability, Boolean>());
    public static Set<Capability> blockedCapabilities
        = Collections.newSetFromMap(new ConcurrentHashMap<Capability, Boolean>());
    private static ThreadLocal<Capability> myCapability = new ThreadLocal<Capability>();

    public static Capability getLocal(boolean worker) {
        Capability cap = myCapability.get();
        if (cap == null) {
            cap = new Capability(Thread.currentThread(), worker);
            if (worker) {
                workerCapabilities.add(cap);
            } else {
                synchronized (capabilities) {
                    capabilities.add(cap);
                    cap.id = capabilities.size() - 1;
                }
            }
        }
        return cap;
    }

    public static Capability getLocal() {
        return getLocal(false);
    }

    public static int getNumCapabilities() {
        return Runtime.getMaxWorkerCapabilities();
    }

    public static void setNumCapabilities(int n) {
        Runtime.setMaxWorkerCapabilities(n);
    }

    public int id;
    public final boolean worker;
    public WeakReference<Thread> thread;
    public Lock lock                                       = new ReentrantLock();
    public StgContext context                              = new StgContext();
    public Deque<TSO> runQueue                             = new LinkedLinked<TSO>();
    public Deque<Message> inbox                            = new ConcurrentLinkedDeque<Message>();
    public Map<WeakReference<Closure>, WeakPtr> weakPtrMap = new ArrayList<Weak>();
    public ReferenceQueue<Closure> refQueue                = new ReferenceQueue<Closure>();

    public Capability(Thread t, boolean worker) {
        this.thread = new WeakReference<Thread>(t);
        this.worker = worker;
    }

    public static Closure scheduleClosure(Closure p) {
        return getLocal().schedule(new TSO(p));
    }

    public static void interruptAll() {
        for (Capability c: capabilities) {
            c.interrupt();
        }
    }

    public final Closure schedule(TSO tso) {
        if (tso != null) {
            appendToRunQueue(tso);
        }
        Closure result = null;
        TSO     outer  = null;

        do {
            result = null;
            if (context.currentTSO != null) {
                /* Re-entering the RTS, a fresh TSO was generated. */
                outer = context.currentTSO;
            }

            /* TODO: The following still need to be implemented:
               - Deadlock detection. Be able to detect <<loop>>.
            */
            if (emptyRunQueue()) {

                if (worker && workerCapabilitiesSize() > Runtime.getMaxWorkerCapabilities()) {
                    /* Terminate this Worker Capability if we've exceeded the limit
                       of maxWorkerCapabilities. */
                    return null;
                }

                tryStealGlobalRunQueue();
                if (emptyRunQueue()) {
                    activateSpark();
                    if (emptyRunQueue()) {
                        blockedCapabilities.add(this);
                        LockSupport.park();
                        if (Thread.interrupted()) {}
                        continue;
                    }
                }
            }

            TSO t = popRunQueue();
            context.reset(cap, t);

            WhatNext prevWhatNext = t.whatNext;
            switch (prevWhatNext) {
                case ThreadKilled:
                case ThreadComplete:
                    break;
                case ThreadRun:
                    try {
                        result = tso.closure.enter(context);
                    } catch (Exception e) {
                        // TODO: Catch exceptions here?
                        throw e;
                    }
                    break;
                case ThreadInterpret:
                    interpretBCO(cap);
                    break;
                default:
                    barf("{Scheduler} Invalid whatNext field for TSO[%d].", t.id);
            }

            context.currentTSO = null;

            if (outer != null) {
                context.currentTSO = outer;
                outer              = null;
            }

            /* Thread is done executing, awaken the blocked exception queue. */
            awakenBlockedExceptionQueue(t);
            if (emptyRunQueue() && !worker) break;
        } while (true);
        return result;
    }

    public final void migrateThread(TSO tso, Capability to) {
        tso.cap = to;
        tryWakeupThread(tso);
    }

    /* Run Queue */

    public final boolean emptyRunQueue() {
        return runQueue.isEmpty();
    }

    public final int runQueueSize() {
        return runQueue.size();
    }

    public final void appendToRunQueue(TSO tso) {
        runQueue.offerLast(tso);
    }

    public final void pushOnRunQueue(TSO tso) {
        runQueue.offerFirst(tso);
    }

    public final TSO popRunQueue() {
        return runQueue.pollFirst();
    }

    public final TSO peekRunQueue() {
        return runQueue.peekFirst();
    }

    public final void promoteInRunQueue(TSO tso) {
        removeFromRunQueue(tso);
        pushOnRunQueue(tso);
    }

    public final void removeFromRunQueue(TSO tso) {
        runQueue.remove(tso);
    }

    /* Sparks */

    public final void activateSpark() {
        if (Parallel.anySparks()) {
            createSparkThread();
            if (RuntimeOptions.DebugFlag.scheduler) {
                debugBelch("{Scheduler} Creating a Spark TSO[%d].", tso.id);
            }
        }
    }

    public final void createSparkThread() {
        appendToRunQueue(Runtime.createIOThread(Closures.runSparks));
    }

    public final boolean newSpark(Closure p) {
        if (p.getEvaluated() == null) {
            if (sparks.offerFirst(p)) {
                globalSparkStats.created.getAndIncrement();
            } else {
                globalSparkStats.overflowed.getAndIncrement();
            }
        } else {
            globalSparkStats.dud.getAndIncrement();
        }
        return true;
    }

    public final void threadPaused(TSO tso) {
        maybePerformBlockedException(tso);
        UpdateInfo ui = tso.updateInfoStack.markBackwardsFrom(this, tso);
        if (ui != null) {
            suspendComputation(tso, ui);
        }
    }

    public final boolean maybePerformBlockedException(TSO tso) {
        Queue<MessageThrowTo> blockedExceptions = tso.blockedExceptions;
        boolean noBlockedExceptions = blockedExceptions.isEmpty();
        if (tso.whatNext == ThreadComplete) {
            if (noBlockedExceptions) {
                return false;
            } else {
                awakenBlockedExceptionQueue(tso);
                return true;
            }
        }

        if (!noBlockedExceptions &&
            (!tso.hasFlag(TSO_BLOCKEX) ||
             (tso.hasFlag(TSO_INTERRUPTIBLE) && tso.interruptible()))) {
            do {
                MessageThrowTo msg = tso.blockedExceptions.peek();
                if (msg == null) return false;
                msg.lock();
                tso.blockedExceptions.poll();
                if (!msg.isValid()) {
                    msg.unlock();
                    continue;
                }
                TSO source = msg.source;
                msg.done();
                tryWakeupThread(source);
                Exception.throwToSingleThreaded(msg.target, msg.exception);
                return true;
            } while (true);
        }
        return false;
    }

    public final void awakenBlockedExceptionQueue(TSO tso) {
        MessageThrowTo msg;

        while ((msg = tso.blockedExceptions.poll()) != null) {
            msg.lock();
            if (msg.isValid()) {
                TSO source = msg.source;
                msg.done();
                tryWakeupThread(source);
            } else {
                msg.unlock();
            }
        }
    }

    public final void tryWakeupThread(TSO tso) {
        if (tso.cap != cap) {
            sendMessage(tso.cap, new MessageWakeup(tso));
        } else {
            boolean blocked = true;
            switch (tso.whyBlocked) {
                case BlockedOnMVar:
                case BlockedOnMVarRead:
                    /* TODO: fix this */
                    blocked = true;
                    break;
                case BlockedOnMsgThrowTo:
                    MessageThrowTo msg = (MessageThrowTo) tso.blockInfo;
                    if (msg.isValid()) {
                        return;
                    }
                case BlockedOnBlackHole:
                case BlockedOnSTM:
                    blocked = true;
                    break;
                default:
                    return;

            }
            tso.whyBlocked = NotBlocked;
            if (!blocked) {
                appendToRunQueue(tso);
            }
        }
    }

    public final void sendMessage(Capability target, Message msg) {
        target.inbox.offer(msg);
        target.interrupt();
    }

    public final boolean emptyInbox() {
        return inbox.isEmpty();
    }

    public final void interrupt() {
        Thread t = thread.get();
        TSO tso = context.currentTSO;
        if (t != null && (tso == null || !tso.hasFlag(TSO_INTERRUPT_IMMUNE))) {
            t.interrupt();
        }
    }

    public final boolean messageBlackHole(MessageBlackHole msg) {
        Thunk bh = msg.bh;
        do {
            Closure p = bh.indirectee;
            if (p instanceof WhiteHole) {
                return false;
            } else if (p instanceof TSO) {
                TSO owner = (TSO) p;
                if (owner.cap != this) {
                    sendMessage(owner.cap, msg);
                    return true;
                }
                BlockingQueue bq = new BlockingQueue(owner, msg);
                owner.blockingQueues.offer(bq);
                bh.setIndirection(bq);
                return true;
            } else if (p instanceof BlockingQueue) {
                BlockingQueue bq = (BlockingQueue) p;
                assert bq.bh == bh;
                TSO owner = bq.owner;
                assert owner != null;
                if (owner.cap != this) {
                    sendMessage(owner.cap, msg);
                    return true;
                }
                messages.offer(msg);
                return true;
            } else return false;
        } while (true);
    }

    public final void checkBlockingQueues(TSO tso) {
        for (BlockingQueue bq: tso.blockingQueues) {
            Closure p = bq.bh;
            Closure ind = p.indirectee;
            /* TODO: Is this the correct condition? */
            if (ind == null || ind != bq) {
                wakeBlockingQueue(bq);
            }
        }
    }

    public final void wakeBlockingQueue(BlockingQueue blockingQueue) {
        for (MessageBlackHole msg: blockingQueue) {
            if (msg.isValid()) {
                tryWakeupThread(msg.tso);
            }
        }
        blockingQueue.clear();
    }

    public final static Capability getFreeCapability() {
        if (lastFreeCapability.runningTask != null) {
            for (Capability cap: capabilities) {
                if (cap.runningTask == null) {
                    return cap;
                }
            }
        }
        return lastFreeCapability;
    }


    public static void shutdownCapabilities(Task task, boolean safe) {
        for (Capability c: capabilities) {
            c.shutdown(task, safe);
        }
    }

    public TSO tryStealGlobalRunQueue() {
        TSO tso = globalRunQueue.pollLast();
        if (tso != null) {
            Concurrent.globalRunQueueModifiedTime = System.currentTimeMillis();
            migrateThread(tso, this);
        }
    }

    public static void runFinalizers() {
        for (Capability c: Capability.capabilities) {
            c.runAllJavaFinalizers();
        }
    }

    public static void runAllJavaFinalizers() {
        /* TODO: Run finalizers */
    }

    public void checkFinalizers() {
        if (!weakPtrList.isEmpty()) {
            Reference<?> ref;
            while ((ref = refQueue.poll()) != null) {

            }
        }
    }

    public void blockedLoop(boolean actuallyBlocked) {
        TSO tso = context.currentTSO;
        processInbox();

        /* TODO: Replace this check elsewhere. It's to detect loops in STM. */
        // if (tso.trec != null && tso.whyBlocked == NotBlocked) {
        //     if (!tso.trec.validateNestOfTransactions()) {
        //         throwToSingleThreaded(tso, null, true);
        //     }
        // }

        threadPaused(tso);

        /* Run finalizers for WeakPtrs and ByteArrays */
        checkFinalizers();

        /* Spawn worker capabilities if there's work to do */
        manageOrSpawnWorkers();
    }

    public void manageOrSpawnWorkers() {

        /* When we have excess live threads and blocked Capabilities, let's wake
           them up so they can terminate themselves. */
        if ((workerCapabilitiesSize() > Runtime.getMaxWorkerCapabilities()) &&
            !blockedCapabilities.isEmpty()) {
            unblockCapabilities();
        }
            /* Interrupt the blocked capabilities so that they can terminate
               themselves when they unblock. */

        if ((!Concurrent.emptyGlobalRunQueue() || Parallel.anySparks()) &&
            ( System.getCurrentTimeMillis()
            - Concurrent.globalRunQueueModifiedTime
            > Runtime.getMinTSOIdleTime())) {
            if (!blockedCapabilities.isEmpty()) {
                unblockCapabilities();
            } else if (workerCapabilitiesSize() < Runtime.getMaxWorkerCapabilities()) {
                new WorkerThread().start();
            }
        }
    }

    public static void unblockCapabilities() {
        synchronized (blockedCapabilities) {
            if (!blockedCapabilities.isEmpty()) {
                for (Capability c:blockedCapabiliies) {
                    c.interrupt();
                }
                blockedCapabilities.clear();
            }
        }
    }

    public void processInbox() {
        Message msg;
        while ((msg = inbox.poll()) != null) {
            msg.execute(cap);
        }
    }
}

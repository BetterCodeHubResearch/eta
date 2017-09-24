package eta.runtime.apply;

import eta.runtime.stg.Value;
import eta.runtime.stg.Closure;
import eta.runtime.stg.StgContext;
import eta.runtime.stg.AbstractArgumentStack;

import static eta.runtime.RuntimeLogging.barf;

public abstract class Function extends Value {

    public abstract int arity();

    @Override
    public Closure applyV(StgContext context) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyV.");
            return null;
        } else {
            return new PAP(arity - 1, this);
        }
    }

    @Override
    public Closure applyN(StgContext context, int n) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyN.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .add(n)
                              .build());
        }
    }

    @Override
    public Closure applyL(StgContext context, long l) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyL.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .add(l)
                              .build());
        }
    }

    @Override
    public Closure applyF(StgContext context, float f) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyF.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .add(f)
                              .build());
        }
    }

    @Override
    public Closure applyD(StgContext context, double d) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyD.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .add(d)
                              .build());
        }
    }

    @Override
    public Closure applyO(StgContext context, Object o) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for applyO.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .add(o)
                              .build());
        }
    }

    @Override
    public Closure apply1(StgContext context, Closure p) {
        int arity = arity();
        if (arity == 1) {
            barf(this + ": Expected implementation for apply1.");
            return null;
        } else {
            return new PAP(arity - 1, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .addC(p)
                              .build());
        }
    }

    @Override
    public Closure apply1V(StgContext context, Closure p) {
        int arity = arity();
        if (arity == 1) {
            return apply1(context, p).applyV(context);
        } else if (arity == 2) {
            barf(this + ": Expected implementation for apply1V.");
            return null;
        } else {
            return new PAP(arity - 2, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .addC(p)
                              .build());
        }
    }

    @Override
    public Closure apply2(StgContext context, Closure p1, Closure p2) {
        int arity = arity();
        if (arity == 1) {
            return apply1(context, p1).apply1(context, p2);
        } else if (arity == 2) {
            barf(this + ": Expected implementation for apply2.");
            return null;
        } else {
            return new PAP(arity - 2, this,
                              AbstractArgumentStack.Builder
                              .from(null)
                              .addC(p1)
                              .addC(p2)
                              .build());
        }
    }

    @Override
    public Closure apply2V(StgContext context, Closure p1, Closure p2) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply1V(context, p2);
            case 2:
                return apply2(context, p1, p2).applyV(context);
            case 3:
                barf(this + ": Expected implementation for apply2V.");
                return null;
            default:
                return new PAP(arity - 3, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .build());
        }
    }

    @Override
    public Closure apply3(StgContext context, Closure p1, Closure p2, Closure p3) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply2(context, p2, p3);
            case 2:
                return apply2(context, p1, p2).apply1(context, p3);
            case 3:
                barf(this + ": Expected implementation for apply3.");
                return null;
            default:
                return new PAP(arity - 3, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .addC(p3)
                                  .build());
        }
    }

    @Override
    public Closure apply3V(StgContext context, Closure p1, Closure p2, Closure p3) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply2V(context, p2, p3);
            case 2:
                return apply2(context, p1, p2).apply1V(context, p3);
            case 3:
                return apply3(context, p1, p2, p3).applyV(context);
            case 4:
                barf(this + ": Expected implementation for apply3V.");
                return null;
            default:
                return new PAP(arity - 4, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .addC(p3)
                                  .build());
        }
    }

    @Override
    public Closure apply4(StgContext context, Closure p1, Closure p2, Closure p3, Closure p4) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply3(context, p2, p3, p4);
            case 2:
                return apply2(context, p1, p2).apply2(context, p3, p4);
            case 3:
                return apply3(context, p1, p2, p3).apply1(context, p4);
            case 4:
                barf(this + ": Expected implementation for apply4.");
                return null;
            default:
                return new PAP(arity - 4, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .addC(p3)
                                  .addC(p4)
                                  .build());
        }
    }

    @Override
    public Closure apply5(StgContext context, Closure p1, Closure p2, Closure p3, Closure p4, Closure p5) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply4(context, p2, p3, p4, p5);
            case 2:
                return apply2(context, p1, p2).apply3(context, p3, p4, p5);
            case 3:
                return apply3(context, p1, p2, p3).apply2(context, p4, p5);
            case 4:
                return apply4(context, p1, p2, p3, p4).apply1(context, p5);
            case 5:
                barf(this + ": Expected implementation for apply5.");
                return null;
            default:
                return new PAP(arity - 5, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .addC(p3)
                                  .addC(p4)
                                  .addC(p5)
                                  .build());
        }
    }

    @Override
    public Closure apply6(StgContext context, Closure p1, Closure p2, Closure p3, Closure p4, Closure p5, Closure p6) {
        int arity = arity();
        switch (arity) {
            case 1:
                return apply1(context, p1).apply5(context, p2, p3, p4, p5, p6);
            case 2:
                return apply2(context, p1, p2).apply4(context, p3, p4, p5, p6);
            case 3:
                return apply3(context, p1, p2, p3).apply3(context, p4, p5, p6);
            case 4:
                return apply4(context, p1, p2, p3, p4).apply2(context, p5, p6);
            case 5:
                return apply5(context, p1, p2, p3, p4, p5).apply1(context, p6);
            case 6:
                barf(this + ": Expected implementation for apply6.");
                return null;
            default:
                return new PAP(arity - 6, this,
                                  AbstractArgumentStack.Builder
                                  .from(null)
                                  .addC(p1)
                                  .addC(p2)
                                  .addC(p3)
                                  .addC(p4)
                                  .addC(p5)
                                  .addC(p6)
                                  .build());
        }
    }
}

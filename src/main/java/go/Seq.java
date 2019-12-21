package go;

import java.util.logging.Logger;

import android.content.Context;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.logging.Logger;

public class Seq {
    private static Logger log = Logger.getLogger("GoSeq");
    private static final int NULL_REFNUM = 41;
    public static final Ref nullRef = new Ref(41, null);
    private static final GoRefQueue goRefQueue = new GoRefQueue();
    static final RefTracker tracker;

    public static void setContext(Context context) {
        Seq.setContext((Object)context);
    }

    private static native void init();

    public static void touch() {
    }

    private Seq() {
    }

    static native void setContext(Object var0);

    public static void incRefnum(int n) {
        tracker.incRefnum(n);
    }

    public static int incRef(Object object) {
        return tracker.inc(object);
    }

    public static int incGoObjectRef(GoObject goObject) {
        return goObject.incRefnum();
    }

    public static void trackGoRef(int n, GoObject goObject) {
        if (n > 0) {
            throw new RuntimeException("trackGoRef called with Java refnum " + n);
        }
        goRefQueue.track(n, goObject);
    }

    public static Ref getRef(int n) {
        return tracker.get(n);
    }

    public static native void incGoRef(int var0, GoObject var1);

    static native void destroyRef(int var0);

    static void decRef(int n) {
        tracker.dec(n);
    }

    static {
        System.loadLibrary("gojni");
        Seq.init();
        Universe.touch();
        tracker = new RefTracker();
    }

    static final class RefMap {
        private int next = 0;
        private int live = 0;
        private int[] keys = new int[16];
        private Ref[] objs = new Ref[16];

        RefMap() {
        }

        Ref get(int n) {
            int n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
            if (n2 >= 0) {
                return this.objs[n2];
            }
            return null;
        }

        void remove(int n) {
            int n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
            if (n2 >= 0 && this.objs[n2] != null) {
                this.objs[n2] = null;
                --this.live;
            }
        }

        void put(int n, Ref ref) {
            if (ref == null) {
                throw new RuntimeException("put a null ref (with key " + n + ")");
            }
            int n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
            if (n2 >= 0) {
                if (this.objs[n2] == null) {
                    this.objs[n2] = ref;
                    ++this.live;
                }
                if (this.objs[n2] != ref) {
                    throw new RuntimeException("replacing an existing ref (with key " + n + ")");
                }
                return;
            }
            if (this.next >= this.keys.length) {
                this.grow();
                n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
            }
            if ((n2 ^= -1) < this.next) {
                System.arraycopy(this.keys, n2, this.keys, n2 + 1, this.next - n2);
                System.arraycopy(this.objs, n2, this.objs, n2 + 1, this.next - n2);
            }
            this.keys[n2] = n;
            this.objs[n2] = ref;
            ++this.live;
            ++this.next;
        }

        private void grow() {
            int[] arrn;
            Ref[] arrref;
            int n;
            int n2 = 2 * RefMap.roundPow2(this.live);
            if (n2 > this.keys.length) {
                arrn = new int[this.keys.length * 2];
                arrref = new Ref[this.objs.length * 2];
            } else {
                arrn = this.keys;
                arrref = this.objs;
            }
            int n3 = 0;
            for (n = 0; n < this.keys.length; ++n) {
                if (this.objs[n] == null) continue;
                arrn[n3] = this.keys[n];
                arrref[n3] = this.objs[n];
                ++n3;
            }
            for (n = n3; n < arrn.length; ++n) {
                arrn[n] = 0;
                arrref[n] = null;
            }
            this.keys = arrn;
            this.objs = arrref;
            this.next = n3;
            if (this.live != this.next) {
                throw new RuntimeException("bad state: live=" + this.live + ", next=" + this.next);
            }
        }

        private static int roundPow2(int n) {
            int n2;
            for (n2 = 1; n2 < n; n2 *= 2) {
            }
            return n2;
        }
    }

    static class GoRef
            extends PhantomReference<GoObject> {
        final int refnum;

        GoRef(int n, GoObject goObject, GoRefQueue goRefQueue) {
            super(goObject, goRefQueue);
            if (n > 0) {
                throw new RuntimeException("GoRef instantiated with a Java refnum " + n);
            }
            this.refnum = n;
        }
    }

    static class GoRefQueue
            extends ReferenceQueue<GoObject> {
        private final Collection<GoRef> refs = Collections.synchronizedCollection(new HashSet());

        void track(int n, GoObject goObject) {
            this.refs.add(new GoRef(n, goObject, this));
        }

        GoRefQueue() {
            Thread thread = new Thread(new Runnable(){

                @Override
                public void run() {
                    do {
                        try {
                            do {
                                GoRef goRef = (GoRef)GoRefQueue.this.remove();
                                GoRefQueue.this.refs.remove(goRef);
                                Seq.destroyRef(goRef.refnum);
                                goRef.clear();
                            } while (true);
                        }
                        catch (InterruptedException interruptedException) {
                            continue;
                        }
//                        break;
                    } while (true);
                }
            });
            thread.setDaemon(true);
            thread.setName("GoRefQueue Finalizer Thread");
            thread.start();
        }

    }

    static final class RefTracker {
        private static final int REF_OFFSET = 42;
        private int next = 42;
        private final RefMap javaObjs = new RefMap();
        private final IdentityHashMap<Object, Integer> javaRefs = new IdentityHashMap();

        RefTracker() {
        }

        synchronized int inc(Object object) {
            int n;
            Ref ref;
            if (object == null) {
                return 41;
            }
            if (object instanceof Proxy) {
                return ((Proxy)object).incRefnum();
            }
            Integer n2 = this.javaRefs.get(object);
            if (n2 == null) {
                if (this.next == Integer.MAX_VALUE) {
                    throw new RuntimeException("createRef overflow for " + object);
                }
                n2 = this.next++;
                this.javaRefs.put(object, n2);
            }
            if ((ref = this.javaObjs.get(n = n2.intValue())) == null) {
                ref = new Ref(n, object);
                this.javaObjs.put(n, ref);
            }
            ref.inc();
            return n;
        }

        synchronized void incRefnum(int n) {
            Ref ref = this.javaObjs.get(n);
            if (ref == null) {
                throw new RuntimeException("referenced Java object is not found: refnum=" + n);
            }
            ref.inc();
        }

        synchronized void dec(int n) {
            if (n <= 0) {
                log.severe("dec request for Go object " + n);
                return;
            }
            if (n == Seq.nullRef.refnum) {
                return;
            }
            Ref ref = this.javaObjs.get(n);
            if (ref == null) {
                throw new RuntimeException("referenced Java object is not found: refnum=" + n);
            }
            ref.refcnt--;
            if (ref.refcnt <= 0) {
                this.javaObjs.remove(n);
                this.javaRefs.remove(ref.obj);
            }
        }

        synchronized Ref get(int n) {
            if (n < 0) {
                throw new RuntimeException("ref called with Go refnum " + n);
            }
            if (n == 41) {
                return nullRef;
            }
            Ref ref = this.javaObjs.get(n);
            if (ref == null) {
                throw new RuntimeException("unknown java Ref: " + n);
            }
            return ref;
        }
    }

    public static final class Ref {
        public final int refnum;
        private int refcnt;
        public final Object obj;

        Ref(int n, Object object) {
            if (n < 0) {
                throw new RuntimeException("Ref instantiated with a Go refnum " + n);
            }
            this.refnum = n;
            this.refcnt = 0;
            this.obj = object;
        }

        void inc() {
            if (this.refcnt == Integer.MAX_VALUE) {
                throw new RuntimeException("refnum " + this.refnum + " overflow");
            }
            ++this.refcnt;
        }
    }

    public static interface Proxy
            extends GoObject {
    }

    public static interface GoObject {
        public int incRefnum();
    }

}

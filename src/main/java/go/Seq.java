package go;

import java.util.Arrays;
import java.lang.ref.PhantomReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Collection;
import java.lang.ref.ReferenceQueue;
import java.util.IdentityHashMap;
import android.content.Context;
import java.util.logging.Logger;

public class Seq
{
    private static Logger log;
    private static final int NULL_REFNUM = 41;
    public static final Ref nullRef;
    private static final GoRefQueue goRefQueue;
    static final RefTracker tracker;

    public static void setContext(final Context context) {
        setContext((Object)context);
    }

    private static native void init();

    public static void touch() {
    }

    private Seq() {
    }

    static native void setContext(final Object p0);

    public static void incRefnum(final int n) {
        Seq.tracker.incRefnum(n);
    }

    public static int incRef(final Object o) {
        return Seq.tracker.inc(o);
    }

    public static int incGoObjectRef(final GoObject goObject) {
        return goObject.incRefnum();
    }

    public static void trackGoRef(final int i, final GoObject goObject) {
        if (i > 0) {
            throw new RuntimeException("trackGoRef called with Java refnum " + i);
        }
        Seq.goRefQueue.track(i, goObject);
    }

    public static Ref getRef(final int n) {
        return Seq.tracker.get(n);
    }

    public static native void incGoRef(final int p0, final GoObject p1);

    static native void destroyRef(final int p0);

    static void decRef(final int n) {
        Seq.tracker.dec(n);
    }

    static {
        Seq.log = Logger.getLogger("GoSeq");
        nullRef = new Ref(41, null);
        goRefQueue = new GoRefQueue();
        System.loadLibrary("gojni");
        init();
        Universe.touch();
        tracker = new RefTracker();
    }

    public static final class Ref
    {
        public final int refnum;
        private int refcnt;
        public final Object obj;

        Ref(final int n, final Object obj) {
            if (n < 0) {
                throw new RuntimeException("Ref instantiated with a Go refnum " + n);
            }
            this.refnum = n;
            this.refcnt = 0;
            this.obj = obj;
        }

        void inc() {
            if (this.refcnt == Integer.MAX_VALUE) {
                throw new RuntimeException("refnum " + this.refnum + " overflow");
            }
            ++this.refcnt;
        }
    }

    static final class RefTracker
    {
        private static final int REF_OFFSET = 42;
        private int next;
        private final RefMap javaObjs;
        private final IdentityHashMap<Object, Integer> javaRefs;

        RefTracker() {
            this.next = 42;
            this.javaObjs = new RefMap();
            this.javaRefs = new IdentityHashMap<Object, Integer>();
        }

        synchronized int inc(final Object key) {
            if (key == null) {
                return 41;
            }
            if (key instanceof Proxy) {
                return ((Proxy)key).incRefnum();
            }
            Integer value = this.javaRefs.get(key);
            if (value == null) {
                if (this.next == Integer.MAX_VALUE) {
                    throw new RuntimeException("createRef overflow for " + key);
                }
                value = this.next++;
                this.javaRefs.put(key, value);
            }
            final int intValue = value;
            Ref value2 = this.javaObjs.get(intValue);
            if (value2 == null) {
                value2 = new Ref(intValue, key);
                this.javaObjs.put(intValue, value2);
            }
            value2.inc();
            return intValue;
        }

        synchronized void incRefnum(final int i) {
            final Ref value = this.javaObjs.get(i);
            if (value == null) {
                throw new RuntimeException("referenced Java object is not found: refnum=" + i);
            }
            value.inc();
        }

        synchronized void dec(final int n) {
            if (n <= 0) {
                Seq.log.severe("dec request for Go object " + n);
                return;
            }
            if (n == Seq.nullRef.refnum) {
                return;
            }
            final Ref value = this.javaObjs.get(n);
            if (value == null) {
                throw new RuntimeException("referenced Java object is not found: refnum=" + n);
            }
            value.refcnt--;
            if (value.refcnt <= 0) {
                this.javaObjs.remove(n);
                this.javaRefs.remove(value.obj);
            }
        }

        synchronized Ref get(final int n) {
            if (n < 0) {
                throw new RuntimeException("ref called with Go refnum " + n);
            }
            if (n == 41) {
                return Seq.nullRef;
            }
            final Ref value = this.javaObjs.get(n);
            if (value == null) {
                throw new RuntimeException("unknown java Ref: " + n);
            }
            return value;
        }
    }

    static class GoRefQueue extends ReferenceQueue<GoObject>
    {
        private final Collection<GoRef> refs;

        void track(final int n, final GoObject goObject) {
            this.refs.add(new GoRef(n, goObject, this));
        }

        GoRefQueue() {
            this.refs = Collections.synchronizedCollection(new HashSet<GoRef>());
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            while (true) {
                                final GoRef goRef = (GoRef)GoRefQueue.this.remove();
                                GoRefQueue.this.refs.remove(goRef);
                                Seq.destroyRef(goRef.refnum);
                                goRef.clear();
                            }
                        }
                        catch (InterruptedException ex) {
                            continue;
                        }
//                        break;
                    }
                }
            });
            thread.setDaemon(true);
            thread.setName("GoRefQueue Finalizer Thread");
            thread.start();
        }
    }

    static class GoRef extends PhantomReference<GoObject>
    {
        final int refnum;

        GoRef(final int n, final GoObject referent, final GoRefQueue q) {
            super(referent, q);
            if (n > 0) {
                throw new RuntimeException("GoRef instantiated with a Java refnum " + n);
            }
            this.refnum = n;
        }
    }

    static final class RefMap
    {
        private int next;
        private int live;
        private int[] keys;
        private Ref[] objs;

        RefMap() {
            this.next = 0;
            this.live = 0;
            this.keys = new int[16];
            this.objs = new Ref[16];
        }

        Ref get(final int key) {
            final int binarySearch = Arrays.binarySearch(this.keys, 0, this.next, key);
            if (binarySearch >= 0) {
                return this.objs[binarySearch];
            }
            return null;
        }

        void remove(final int key) {
            final int binarySearch = Arrays.binarySearch(this.keys, 0, this.next, key);
            if (binarySearch >= 0 && this.objs[binarySearch] != null) {
                this.objs[binarySearch] = null;
                --this.live;
            }
        }

        void put(final int n, final Ref ref) {
            if (ref == null) {
                throw new RuntimeException("put a null ref (with key " + n + ")");
            }
            int n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
            if (n2 < 0) {
                if (this.next >= this.keys.length) {
                    this.grow();
                    n2 = Arrays.binarySearch(this.keys, 0, this.next, n);
                }
                final int n3 = ~n2;
                if (n3 < this.next) {
                    System.arraycopy(this.keys, n3, this.keys, n3 + 1, this.next - n3);
                    System.arraycopy(this.objs, n3, this.objs, n3 + 1, this.next - n3);
                }
                this.keys[n3] = n;
                this.objs[n3] = ref;
                ++this.live;
                ++this.next;
                return;
            }
            if (this.objs[n2] == null) {
                this.objs[n2] = ref;
                ++this.live;
            }
            if (this.objs[n2] != ref) {
                throw new RuntimeException("replacing an existing ref (with key " + n + ")");
            }
        }

        private void grow() {
            int[] keys;
            Ref[] objs;
            if (2 * roundPow2(this.live) > this.keys.length) {
                keys = new int[this.keys.length * 2];
                objs = new Ref[this.objs.length * 2];
            }
            else {
                keys = this.keys;
                objs = this.objs;
            }
            int next = 0;
            for (int i = 0; i < this.keys.length; ++i) {
                if (this.objs[i] != null) {
                    keys[next] = this.keys[i];
                    objs[next] = this.objs[i];
                    ++next;
                }
            }
            for (int j = next; j < keys.length; ++j) {
                keys[j] = 0;
                objs[j] = null;
            }
            this.keys = keys;
            this.objs = objs;
            this.next = next;
            if (this.live != this.next) {
                throw new RuntimeException("bad state: live=" + this.live + ", next=" + this.next);
            }
        }

        private static int roundPow2(final int n) {
            int i;
            for (i = 1; i < n; i *= 2) {}
            return i;
        }
    }

    public interface Proxy extends GoObject
    {
    }

    public interface GoObject
    {
        int incRefnum();
    }
}

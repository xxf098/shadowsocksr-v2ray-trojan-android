package go;

public abstract class Universe
{
    private Universe() {
    }

    public static void touch() {
    }

    private static native void _init();

    static {
        Seq.touch();
        _init();
    }

    private static final class proxyerror extends Exception implements Seq.Proxy, error
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyerror(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public String getMessage() {
            return this.error();
        }

        @Override
        public native String error();
    }
}
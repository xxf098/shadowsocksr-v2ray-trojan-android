package com.github.shadowsocks.go;

public abstract class Universe {
    private Universe() {
    }

    public static void touch() {
    }

    private static native void _init();

    static {
        Seq.touch();
        Universe._init();
    }

    private static final class proxyerror
            extends Exception
            implements Seq.Proxy,
            Error {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyerror(int n) {
            this.refnum = n;
            Seq.trackGoRef(n, this);
        }

        @Override
        public String getMessage() {
            return this.error();
        }

        @Override
        public native String error();
    }

}

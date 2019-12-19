package com.github.shadowsocks.libv2ray;

public interface V2RayVPNServiceSupportsSet {
    public long onEmitStatus(long var1, String var3);

    public long prepare();

    public long protect(long var1);

    public long sendFd();

    public long setup(String var1);

    public long shutdown();
}

package tun2socks;

public interface VpnService
{
    boolean protect(final long fd);
}
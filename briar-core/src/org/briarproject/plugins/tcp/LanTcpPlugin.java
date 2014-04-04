package org.briarproject.plugins.tcp;

import static java.util.logging.Level.WARNING;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;

class LanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("lan");

	private static final Logger LOG =
			Logger.getLogger(LanTcpPlugin.class.getName());

	LanTcpPlugin(Executor pluginExecutor, DuplexPluginCallback callback,
			int maxFrameLength, long maxLatency, long pollingInterval) {
		super(pluginExecutor, callback, maxFrameLength, maxLatency,
				pollingInterval);
	}

	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<SocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		InetSocketAddress old = parseSocketAddress(p.get("address"),
				p.get("port"));
		// Get a list of the device's network interfaces
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return Collections.emptyList();
		}
		List<SocketAddress> addrs = new LinkedList<SocketAddress>();
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(isAcceptableAddress(a)) {
					// If this is the old address, try to use the same port
					if(old != null && old.getAddress().equals(a))
						addrs.add(0, new InetSocketAddress(a, old.getPort()));
					addrs.add(new InetSocketAddress(a, 0));
				}
			}
		}
		return addrs;
	}

	private boolean isAcceptableAddress(InetAddress a) {
		// Accept link-local and site-local IPv4 addresses
		boolean ipv4 = a instanceof Inet4Address;
		boolean loop = a.isLoopbackAddress();
		boolean link = a.isLinkLocalAddress();
		boolean site = a.isSiteLocalAddress();
		return ipv4 && !loop && (link || site);
	}

	@Override
	protected boolean isConnectable(InetSocketAddress remote) {
		if(remote.getPort() == 0) return false;
		if(!isAcceptableAddress(remote.getAddress())) return false;
		// Try to determine whether the address is on the same LAN as us
		if(socket == null) return true;
		byte[] localIp = socket.getInetAddress().getAddress();
		byte[] remoteIp = remote.getAddress().getAddress();
		return addressesAreOnSameLan(localIp, remoteIp);
	}

	// Package access for testing
	boolean addressesAreOnSameLan(byte[] localIp, byte[] remoteIp) {
		// 10.0.0.0/8
		if(localIp[0] == 10) return remoteIp[0] == 10;
		// 172.16.0.0/12
		if(localIp[0] == (byte) 172 && (localIp[1] & 0xF0) == 16)
			return remoteIp[0] == (byte) 172 && (remoteIp[1] & 0xF0) == 16;
		// 192.168.0.0/16
		if(localIp[0] == (byte) 192 && localIp[1] == (byte) 168)
			return remoteIp[0] == (byte) 192 && remoteIp[1] == (byte) 168;
		// Unrecognised prefix - may be compatible
		return true;
	}
}
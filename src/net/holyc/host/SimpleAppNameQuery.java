package net.holyc.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

class ConnectionCacheItem {
	int uid;
	private Long timestamp;
	
	ConnectionCacheItem(int uid) {
		this.uid = uid;
		this.timestamp = System.currentTimeMillis() / 1000;
	}
	
	boolean expire(int threshold) {
		return System.currentTimeMillis() / 1000 - this.timestamp >= threshold;
	}
}

/**
 * Another class to query app name
 * it doesn't call lsof, instead directly
 * read files such as /proc/net/tcp, tcp6..
 * so it is more efficient 
*/

public class SimpleAppNameQuery {
	public static final String TAG = "SimpleAppNameQuery";
	public static final HashMap<Integer, String> uid2PkgName = new HashMap<Integer, String>();
	public static final ConcurrentHashMap<String, ConnectionCacheItem> connectionCache = 
		new ConcurrentHashMap<String, ConnectionCacheItem>();
	public static Thread monitorThread = null;
	
	/**
	 * Transport ip and port to hex network address
	 * 127.0.0.1:5037 -> 0100007F:13AD
	 * @param ip
	 * @param port
	 * @return null if transportation fails
	 */
	public static String toHexNetworkAddr(String ip, int port) {
		String networkAddr = "";
		try {
			String[] items = ip.split("\\.");
			for (int i = 3; i >= 0; i--) {
				String EightBits = Integer.toHexString(Integer.parseInt(items[i]));
				if (EightBits.length() == 1) EightBits = "0" + EightBits;
				networkAddr += EightBits;
			}
			String strPort = Integer.toHexString(port);
			strPort = "0000".substring(strPort.length()) + strPort;
			networkAddr += ":" + strPort;
			networkAddr = networkAddr.toUpperCase();
		} catch (Exception e) {
			networkAddr = null;
		}
		return networkAddr;
	}
	
	public static String toString(String remoteIP, int remotePort, int localPort) {
		return localPort + "-" + remoteIP + ":" + remotePort;
	}
	
	public static int getUidFromFile(String localAddr, String remoteAddr, String file) {
		ArrayList<String> lines = Utility.readLinesFromFile(file);
		int uid = -1;
		//Log.d(TAG, "query: "+localAddr+"->"+remoteAddr+"::"+file);
		for (String line : lines) {
			try {
				String[] items = line.split(" +");
				if (items[1].equalsIgnoreCase(localAddr) && items[2].equalsIgnoreCase(remoteAddr)) {
					uid = Integer.parseInt(items[7]);
				}
			} catch (Exception e) {
				Log.d(TAG, "Parsing line error: " + e.getMessage());
			}
		}
		return uid;
	}
	/**
	 * get uid info of a connection by looking up
	 * proc/net/tcp (tcp6, udp, udp6)
	 * @param localIP
	 * @param localPort
	 * @param remoteIP
	 * @param remotePort
	 * @return < 0 if not found
	 */
	public static int getConnectionUid(String localIP, int localPort, String remoteIP, int remotePort) {
		String[] ipv4files = {"/proc/net/tcp", "/proc/net/udp"};
		String[] ipv6files = {"/proc/net/tcp6", "/proc/net/udp6"};
		String localAddr = toHexNetworkAddr(localIP, localPort);
		String remoteAddr = toHexNetworkAddr(remoteIP, remotePort);
		int uid = -1;
		for (String file : ipv4files) {
			if ((uid=getUidFromFile(localAddr, remoteAddr, file)) > 0) break;
		}
		if (uid < 0) {
			localAddr = "0000000000000000FFFF0000" + localAddr;
			if (remoteAddr.equals("00000000")) 
				remoteAddr = "000000000000000000000000" + remoteAddr;
			else
				remoteAddr = "0000000000000000FFFF0000" + remoteAddr;
			for (String file : ipv6files) {
				if ((uid=getUidFromFile(localAddr, remoteAddr, file)) > 0) break;
			}
		}
		//try cache
		if (uid < 0) {
			ConnectionCacheItem item = connectionCache.get(toString(remoteIP, remotePort, localPort));
			if (item != null) uid = item.uid;
		}
		return uid;
	}
	
	public static void refreshCache(Context cxt, int uid) {
	  if (SystemService.id2Service.containsKey(uid)) {
		  uid2PkgName.put(uid, SystemService.id2Service.get(uid));
	  } else {
	  	  PackageManager pm = cxt.getPackageManager();
		  List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		  for (int i = 0; i < packages.size(); i++) {
			  ApplicationInfo appInfo = packages.get(i);
			  if (!uid2PkgName.containsKey(appInfo.uid)) {
				  uid2PkgName.put(appInfo.uid, appInfo.processName);
			  }
		  }
	  }
	  if (uid2PkgName.containsKey(uid) == false) {
		uid2PkgName.put(uid, ""+uid);
	  }
	}
	
	public static String getPKGNameFromAddr(Context cxt, String localIP, 
			int localPort, String remoteIP, int remotePort) {
    	if (AppNameQueryEngine.isValidQuery(remoteIP, remotePort, localPort) == false) return null;
    	String knownService = AppNameQueryEngine.queryServiceByPort(remotePort);
    	if (knownService != null) return knownService;
    	
    	int uid = -1;
    	if(connectionCache.contains(toString(remoteIP, remotePort, localPort))){
    		uid = connectionCache.get(toString(remoteIP, remotePort, localPort)).uid;
    	}else{
    		uid = getConnectionUid(localIP, localPort, remoteIP, remotePort);
    		//Log.d(TAG, "uid = " + uid);
    		if (uid < 0) return null;
    		connectionCache.put(toString(remoteIP, remotePort, localPort), new ConnectionCacheItem(uid));
    	}
		if (monitorThread == null || monitorThread.isAlive() == false) {
			monitorThread = new Thread(new CacheMonitor());
			monitorThread.start();
		}
    	if (uid2PkgName.containsKey(uid) == false) {
    		refreshCache(cxt, uid);
    	}
    	return uid2PkgName.get(uid);
	}

}

class CacheMonitor implements Runnable {
	static final String TAG = "CacheMonitor";
	static final int TIMEOUT = 300;
	static final int INTERVAL = 10*1000;

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			for (String k : SimpleAppNameQuery.connectionCache.keySet()) {
				ConnectionCacheItem item = SimpleAppNameQuery.connectionCache.get(k);
				if (item.expire(TIMEOUT) == true) {
					Log.d(TAG, "remove " + k + " from Connectoin Cache");
					SimpleAppNameQuery.connectionCache.remove(k);
				} 
			}
			try {
				Thread.sleep(INTERVAL);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				Thread.currentThread().interrupt();
			}
		}
		
	}
	
}

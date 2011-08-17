package net.holyc.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.format.Time;
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
 * Skype call generates so many flows that slow down holy greatly
 * There is a discipline: most of the flows have the same local port
 * which is the listening port of skype.
 * So we use it to quick flow->appname lookup by cache skype listening port
 * @author leo
 * 
 */
class SkypeInfo {
	public static final String TAG = "SkypeInfo";
	int listenPort;
	String dataDir;
	String pkgName;
	int uid;
	
	SkypeInfo() {
		listenPort = -1;
		dataDir = null;
		pkgName = null;
		uid = -1;
	}
	
	public void getListenPort() {
		if (dataDir == null) return;
		String filePath = dataDir + "/files/shared.xml";
		//Log.d(TAG, "read " + filePath);
		ArrayList<String> lines = Utility.readLinesFromFile(filePath);
		for (String line : lines) {
			if (line.startsWith("<ListeningPort>")) {
				//Log.d(TAG, "find " + line);
				int pos = line.indexOf('<', 15);
				if (pos > 15) {
					String strListenPort =  line.substring(15, pos);
					this.listenPort = Integer.parseInt(strListenPort);
				}
			}
		}
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
	public static final SkypeInfo skypeInfo = new SkypeInfo();
	public static Thread monitorThread = null;
	public static String debugInfo = "";
	
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
		//debugInfo += "query: " + localAddr + "->" + remoteAddr + "::" + file + "\n";
		for (String line : lines) {
			//debugInfo += line + "\n";
			try {
				String[] items = line.split(" +");
				if (items[1].equalsIgnoreCase(localAddr) && items[2].equalsIgnoreCase(remoteAddr)) {
					//debugInfo += "parse " + items[7];
					uid = Integer.parseInt(items[7]);
					//debugInfo += " to " + uid + "\n";
					break;
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
			if ((uid=getUidFromFile(localAddr, remoteAddr, file)) >= 0) break;
		}
		if (uid < 0) {
			localAddr = "0000000000000000FFFF0000" + localAddr;
			if (remoteAddr.equals("00000000")) 
				remoteAddr = "000000000000000000000000" + remoteAddr;
			else
				remoteAddr = "0000000000000000FFFF0000" + remoteAddr;
			for (String file : ipv6files) {
				if ((uid=getUidFromFile(localAddr, remoteAddr, file)) >= 0) break;
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
			  //update skype info
			  if (appInfo.processName.contains("com.skype")) {
				  skypeInfo.dataDir = appInfo.dataDir;
				  skypeInfo.pkgName = appInfo.processName;
				  skypeInfo.uid = appInfo.uid;
				  skypeInfo.getListenPort();
				  //Log.d(TAG, "skype listen port is" + skypeInfo.listenPort);
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
    	//check if it is skype
    	if (localPort == skypeInfo.listenPort) return skypeInfo.pkgName;
    	int uid = -1;
    	String networkAddr = toString(remoteIP, remotePort, localPort);
    	if (connectionCache.contains(networkAddr)) {
    		uid = connectionCache.get(networkAddr).uid;
    	} else {
    		 debugInfo = "";
    		 uid = getConnectionUid(localIP, localPort, remoteIP, remotePort);
     	}
  	    if (uid < 0) return null;
		 connectionCache.put(networkAddr, new ConnectionCacheItem(uid));

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

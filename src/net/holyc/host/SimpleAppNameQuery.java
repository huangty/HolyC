package net.holyc.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Another class to query app name
 * it doesn't call lsof, instead directly
 * read files such as /proc/net/tcp, tcp6..
 * so it is more efficient 
*/

public class SimpleAppNameQuery {
	public static final String TAG = "SimpleAppNameQuery";
	public static final HashMap<Integer, String> uid2PkgName = new HashMap<Integer, String>(); 
	
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
		return uid;
	}
	
	public static void refreshCache(Context cxt) {
  	  final PackageManager pm = cxt.getPackageManager();
	  List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
	  for (int i = 0; i < packages.size(); i++) {
		  ApplicationInfo appInfo = packages.get(i);
		  if (!uid2PkgName.containsKey(appInfo.uid)) {
			  uid2PkgName.put(appInfo.uid, appInfo.processName);
		  }
	  }
	}
	
	public static String getPKGNameFromAddr(Context cxt, String localIP, 
			int localPort, String remoteIP, int remotePort) {
    	if (AppNameQueryEngine.isValidQuery(remoteIP, remotePort, localPort) == false) return null;
    	String knownService = AppNameQueryEngine.queryServiceByPort(remotePort);
    	if (knownService != null) return knownService;
    	int uid = getConnectionUid(localIP, localPort, remoteIP, remotePort);
    	//Log.d(TAG, "uid = " + uid);
    	if (uid < 0) return null;
    	if (uid2PkgName.containsKey(uid) == false) {
    		refreshCache(cxt);
    	}
    	return uid2PkgName.get(uid);
	}

}

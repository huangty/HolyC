package net.holyc.host;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 *
 *
 * @author Yongqiang Liu (yliu78@stanford.edu)
 */
public class Utility {
	
	static final String TAG = "Utility";
	public static final int MSG_REPORT_INTFACE_STATE = 0; 
	public static final ConnectionList Connections = new ConnectionList();
	
	public static ArrayList<String> runRootCommand(String command, boolean returnResult) {
	Process process = null;
        DataOutputStream out = null;
        DataInputStream in = null;
        ArrayList<String> resultLines = null;
        
        try {
        	process = Runtime.getRuntime().exec("su");
            out = new DataOutputStream(process.getOutputStream());
        	out.writeBytes(command + "\n");
            out.writeBytes("exit\n");
            out.flush();
            process.waitFor();
            if (returnResult == true) {
            	in = new DataInputStream(process.getInputStream());
            	resultLines = new ArrayList<String>();
            	String line = null;
            	while ((line = in.readLine()) != null)
            		resultLines.add(line);
            }
         } catch (Exception e) {
                    Log.d("*** DEBUG ***", "Unexpected error - Here is what I know: "+e.getMessage());
         }
         finally {
                    try {
                            if (out != null) out.close();
                            if (in != null) in.close();
                            process.destroy();
                    } catch (Exception e) {
                            // nothing
                    }
         }
         return resultLines;
    }

    public static String getProp(String name) {
	    ArrayList<String> resultLines = runRootCommand("getprop " + name, true);
	    return (resultLines == null) ? null : resultLines.get(0);
	}
	
    public static ArrayList<String> readLinesFromFile(String filename) {
		ArrayList<String> lines = new ArrayList<String>();
		File file = new File(filename);
		if (!file.canRead()) {
			Log.d(TAG, filename + "can not be read");
			return lines;
		}
		BufferedReader buffer = null;
        try {
			buffer = new BufferedReader(new FileReader(file), 8192);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, e.getMessage());
		}
		try {
			String line = null;
			while ((line = buffer.readLine()) != null) {
				lines.add(line.trim());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, e.getMessage());
		}
		finally {
			try {
				buffer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, e.getMessage());
			}
		}
		return lines;
	}
	
    public static Method getMethodFromClass(Object obj, String methodName) {
    	final String TAG = "getMethodFromClass";
        Class<?> whichClass = null;
        try {
            whichClass = Class.forName(obj.getClass().getName());
        } catch (ClassNotFoundException e2) {
            // TODO Auto-generated catch block
            Log.d(TAG, "class not found");
        }
        
        Method method = null;
        try {
            //method = whichClass.getDeclaredMethod(methodName);
            Method[] methods = whichClass.getDeclaredMethods();
        	for (Method m : methods) {
        		//Log.d(TAG, "method: " + m.getName());
        		if (m.getName().contains(methodName)) {
        			method = m;
        		}
        	}
        } catch (SecurityException e2) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "SecurityException for " + methodName);
        } 
        return method;
    }
    
    public static Object runMethodofClass(Object obj, Method method, Object... argv) {
    	Object result = null;
    	if (method == null) return result;
    	method.setAccessible(true);
        try {
			result = method.invoke(obj, argv);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "IllegalArgumentException for " + method.getName());
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "IllegalAccessException for " + method.getName());
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "InvocationTargetException for " + method.getName() 
					+ "; Reason: " + e.getLocalizedMessage());
		}
		return result;
    }
    
    /**
     * get process id from network address, using command lsof
     * 
     * return -1 if fails
     * */
    public static int getPidFromAddr(String remoteIP, int remotePort, int localPort) {
    	int pid = -1;
    	String command = "lsof -n -nn -i ";
    	if (remoteIP != null) command += "@" + remoteIP;
    	if (remotePort > 0) command += ":" + remotePort;
    	command += " | grep -v COMMAND";
    	if (localPort > 0) command += " | grep " + localPort;
    	try {
    		ArrayList<String> resultLines = runRootCommand(command, true);
    		//Log.d(TAG, "result: " + resultLines.get(0));
    		String[] items = resultLines.get(0).split("\t| +");
    		pid = Integer.parseInt(items[1]);
    	} catch (Exception e) {
    		//Log.d(TAG, "can not get pid with command: " + command);
    	}
    	return pid;
    }
    
    /**
     * The following system-based APIs needs a context instance
     * as input parameter.
     * 
     * The context instance can be Activity, Service, Application ...
     */
    public static RunningAppProcessInfo getAppInfoFromPid(int pid, Context cxt) {
    	RunningAppProcessInfo appInfo = null;
    	ActivityManager activityMan;
    	activityMan = (ActivityManager)cxt.getSystemService(Context.ACTIVITY_SERVICE);
    	List<RunningAppProcessInfo> process;
    	process = activityMan.getRunningAppProcesses();
    	for (int i = 0; i < process.size(); i++) {
    		RunningAppProcessInfo proc = process.get(i);
    		if (pid == proc.pid) {
    			appInfo = proc;
    			break;
    		}
    	}
    	return appInfo;
    } 
    
    public static RunningServiceInfo getServiceInfoFromPid(int pid, Context cxt) {
    	RunningServiceInfo servInfo = null;
    	ActivityManager activityMan;
    	activityMan = (ActivityManager)cxt.getSystemService(Context.ACTIVITY_SERVICE);
    	List<RunningServiceInfo> service;
    	service = activityMan.getRunningServices(100);
    	for (int i = 0; i < service.size(); i++) {
    		RunningServiceInfo serv = service.get(i);
    		if (pid == serv.pid) {
    			servInfo = serv;
    			break;
    		}
    	}
    	return servInfo;
    }
    
    /**
     * Look up pkg name by network address. 
     * It uses cache to fast lookup speed. Only when cache misses, 
     * lsof command is triggered.
     * 
     * @param remoteIP
     * @param remotePort
     * @param localPort
     * @param cxt
     * @return
     */
    public static String fastGetPKGNameFromAddr(String remoteIP, int remotePort, int localPort, Context cxt) {
    	if (localPort <= 3 || localPort == 111 || localPort == 137 || 
    			localPort == 67 || localPort == 68 || remotePort <= 3) 
    		return null; //non-tcp or non-udp
    	if (remotePort == 53) return "DNSQuery";
    	if (remoteIP == null || remoteIP.equals("0.0.0.0") == true) return null;
    	Connection conn = null;
    	boolean refreshed = false;
    	while ((conn = Connections.find(remoteIP, remotePort, localPort)) == null &&
    			refreshed == false) {
    		//Log.d(TAG, "Before refreshing");
    		//Connections.showList();
    		Connections.refresh();
    		refreshed = true;
    		//Log.d(TAG, "After refreshing");
    		//Connections.showList();
    	}
    	if (conn == null) return null;
    	if (conn.getPkgName() == null) conn.setPkgName(getPKGNameFromPid(conn.pid, cxt));
    	return conn.getPkgName();
    }
    
    /**
     * Look up pkg name by network address.
     * Each query triggers lsof command
     * 
     * @param remoteIP
     * @param remotePort
     * @param localPort
     * @param cxt
     * @return
     */
    public static String getPKGNameFromAddr(String remoteIP, int remotePort, int localPort, Context cxt) {
    	int pid = getPidFromAddr(remoteIP, remotePort, localPort);
        return getPKGNameFromPid(pid, cxt);
    }
    
    public static String getPKGNameFromPid(int pid, Context cxt) {
    	String pkgName = null;
       	if (pid < 0 || cxt == null) return pkgName;
    	RunningAppProcessInfo appInfo = getAppInfoFromPid(pid, cxt);
    	if (appInfo != null) {
    		pkgName = appInfo.processName;
    	} else {
    		RunningServiceInfo servInfo = getServiceInfoFromPid(pid, cxt);
    		if (servInfo != null) pkgName = servInfo.process;
    	}
    	return pkgName;
    }
    /**
     * Can invoke the test code in any Context. For example,
     * in controlUI activity, you can call test as follows:
     * 
     * 
     */
	public static void commandTest(Context cxt) {
		/*runRootCommand("rmmod openvswitch_mod.ko", false);
		ArrayList<String> lines = runRootCommand("/data/local/bin/busybox ifconfig tiwlan0", true);
		if (lines != null)
			for (String line : lines)
				Log.d(TAG, line);
		Log.d(TAG, "wifi.interface: " + getProp("wifi.interface"));
		Log.d(TAG, "wifi.interfac: " + getProp("wifi.interfac"));
		*/
		Long start = System.currentTimeMillis();
		for (int i = 0; i< 1; i++) {
			testFastCommand(cxt);
		}
		Log.d(TAG, "fast time: " + (System.currentTimeMillis()-start) + "ms");
		start = System.currentTimeMillis();
		for (int i = 0; i< 1; i++) {
			testSlowCommand(cxt);
		}
		Log.d(TAG, "slow time: " + (System.currentTimeMillis()-start) + "ms");

    }
	
	private static void testFastCommand(Context cxt) {
		String pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 59855, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 59855, cxt);
    	Log.d(TAG, "get pkgname2 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 80, 0, cxt);
    	Log.d(TAG, "get pkgname3 : " + pkgName);
		pkgName = fastGetPKGNameFromAddr("74.125.224.76", 1, 59855, cxt);
    	Log.d(TAG, "get pkgname4 : " + pkgName);
	}
	
	private static void testSlowCommand(Context cxt) {
		String pkgName = getPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 80, 49284, cxt);
    	Log.d(TAG, "get pkgname1 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 80, 59855, cxt);
    	Log.d(TAG, "get pkgname2 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 80, 0, cxt);
    	Log.d(TAG, "get pkgname3 : " + pkgName);
		pkgName = getPKGNameFromAddr("74.125.224.76", 1, 59855, cxt);
    	Log.d(TAG, "get pkgname4 : " + pkgName);
	}
}


/**
 * Connection class to keep network address and process ID
 * information of a connection
 * 
 * @author leo
 *
 */
class Connection {
    public String destIP;
    public int destPort;
    public int srcPort;
    public int pid;
    private String pkgName;
    private Long timestamp;
    
    public Connection(String destIP, int destPort, int srcPort, int pid) {
    	this.destIP = destIP;
    	this.destPort = destPort;
    	this.srcPort = srcPort;
    	this.pid = pid;
    	this.pkgName = null;
    	this.timestamp = System.currentTimeMillis()/1000;
    }
    
    public boolean match(String destIP, int destPort, int srcPort) {
    	return (this.destPort == destPort && this.srcPort == srcPort
    			&& this.destIP.equalsIgnoreCase(destIP));
    }
    
    /**
     * judge if a conection information expires according to the 
     * given threhold
     * @param threshold (seconds)
     * @return
     */
    public boolean expire(Long threshold) {
    	return (System.currentTimeMillis()/1000 - this.timestamp >= threshold);
    }
    
    public int getPid() {
    	return pid;
    }
    
    public String getPkgName() {
    	return pkgName;
    }
    
    public void setPkgName(String name) {
    	this.pkgName = name;
    }
    
	public String toString() {
		return this.pid + "::" + "local_ip" + ":" + this.srcPort
		       + "->" + this.destIP + ":" + this.destPort;
	}
} 

/**
 * Wrapper a cache of connections and the operations on it
 * @author leo
 *
 */
class ConnectionList {
	static final String TAG = "ConnectionList";
	ArrayList<Connection> mConnections = null;
	static final Long THRESHOLD = 300L; //300 seconds for expiring

	public ConnectionList() {
	    mConnections = new ArrayList<Connection>();	
	}
	
	/**
	 * Find a connection by given network address.
	 * For efficiency, it filters out the expiring connection when looking up
	 * Since the remove operation of ArrayList is very heavy, we add unexpired
	 * connection to new ArrayList 
	 */
	public synchronized Connection find(String destIP, int destPort, int srcPort) {
		Connection target = null;
		ArrayList<Connection> newConnList = new ArrayList<Connection>();
		for (int i = 0; i < mConnections.size(); i++) {
			Connection conn = mConnections.get(i);
			if (conn.match(destIP, destPort, srcPort) == true)
				target = conn;
			if (conn.expire(THRESHOLD) == false)
				newConnList.add(conn);
		}
		mConnections = newConnList;
		return target;
	}
	
	/**
	 * Find if the connection exists in the cache
	 * @param conn
	 * @return
	 */
	private synchronized boolean find(Connection conn) {
		boolean found = false;
		for (int i = 0; i < mConnections.size(); i++) {
			if (mConnections.get(i).match(conn.destIP, conn.destPort, conn.srcPort)==true) {
				found = true;
				break;
			}
		}
		return found;
	}
	/**
	 * load new connections info into cache by lsof command
	 */
	public synchronized void refresh() {
		String command = "su -c \"lsof -P -n -i | grep -\"";
		try {
			ArrayList<String> results = Utility.runRootCommand(command, true);
			for (int i = 0; i < results.size(); i++) {
				Connection conn = createConnectionByString(results.get(i));
				if (conn != null && find(conn) == false)
				    mConnections.add(conn);
			}
		} catch (Exception e) {
		}
		return;
	}
	
	public void showList() {
		for (int i = 0; i < mConnections.size(); i++) {
			Log.d(TAG, mConnections.get(i).toString());
		}
	}
	
	private Connection createConnectionByString(String s) {
		String[] items = s.split("\t| +");
		int pid = Integer.parseInt(items[1]);
		String dstIP = getDestIP(items[8]);
		int dstPort = getDestPort(items[8]);
		int srcPort = getSrcPort(items[8]);
		return new Connection(dstIP, dstPort, srcPort, pid);
	}
	
	private String getDestIP(String s) {
		int index1 = s.indexOf("->");
		int index2 = s.lastIndexOf(':');
		return (index2 > index1 && index1 >= 0)? s.substring(index1+2, index2):null;
	}
	
	private int getDestPort(String s) {
		int index1 = s.indexOf("->");
		int index2 = s.lastIndexOf(':');
		return (index2 > index1 && index1 >= 0)? Integer.parseInt(s.substring(index2+1)):-1;
	}
	
	private int getSrcPort(String s) {
		int index1 = s.indexOf("->");
		int index2 = s.indexOf(':');
		return (index2 < index1 && index1 >= 0)? Integer.parseInt(s.substring(index2+1, index1)):-1;
	}

}
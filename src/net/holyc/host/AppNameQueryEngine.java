package net.holyc.host;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

/**
 * Query App Name with network connection address It use asynchronous mechanism:
 * Put query request into a queue, and one thread handles the request with lsof
 * command. Once losf command, all the connection infos are stored into a cache.
 * If there is still no answer in the cache, the handler re-enqueue the
 * corresponding request
 * 
 * @author Yongqiang Liu (yliu78@stanford.edu)
 */
public class AppNameQueryEngine {
	static final String TAG = "AppNameQueryEngine";
	/**
	 * Connection info cache
	 */
	public static final ConnectionList Connections = new ConnectionList();

	/**
	 * Query request Queue
	 */	
	public static final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<Request>();
	public static final StringCounterMap requestMap = new StringCounterMap();
	public static Thread handleThread = null;
	
	public static final int MAX_REPLICA = 1;
	
    /**
     * Judge if the request connection is valid to lsof  
     */
    public static boolean isValidQuery(String destIP, int destPort, int srcPort) {
    	boolean valid = true;
    	if (srcPort <= 3 || srcPort == 111 || srcPort == 137 || 
    			srcPort == 67 || srcPort == 68 || destPort <= 3 ||
    			destPort == 53 || destPort == 17500 || destIP == null || 
    			destIP.equals("0.0.0.0") == true) 
    		valid = false;
        return valid;
    }
    
    public static String queryServiceByPort(int remotePort) {
    	String service = null;
    	switch (remotePort) {
    	case 53:
    		service = "DNSQuery";
    		break;
    	case 17500:
    		service = "CrazzyNet.Trojan";
    		break;
    	case 111:
    		service = "SUN.RemoteControl";
    		break;
    	case 137:
    		service = "NETBOIS";
    		break;
    	}
    	return service;
    }
    
    public static String getPKGNameFromAddr(String remoteIP, int remotePort, int localPort) {
    	if (isValidQuery(remoteIP, remotePort, localPort) == false) return null;
    	String knownService = queryServiceByPort(remotePort);
    	if (knownService != null) return knownService;
    	Connection found = Connections.find(remoteIP, remotePort, localPort);
    	if (found == null) return null;
    	if (found.getPkgName() == null) found.setPkgName(Utility.getPKGNameFromPidByCmdLine(found.pid));
    	return found.getPkgName();
    }
    
    public static void sendQueryRequest(String remoteIP, int remotePort, int localPort) {    	
    	if (isValidQuery(remoteIP, remotePort, localPort) == false) return;
    	AppNameRequest request = new AppNameRequest(remoteIP, remotePort, localPort);
    	int replicaNum = requestMap.getCount(request.toString());
    	if ( replicaNum >= MAX_REPLICA) {
    		Log.d(TAG, "drop request " + request.toString());
    		return;
    	}
    	try {
    		//note: the sequence of the two statements can not be changed
    		requestMap.setCount(request.toString(), replicaNum + 1);
			requestQueue.put(request);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "Error of enqeuing request: " + e.getMessage());
		}
		if (AppNameQueryEngine.handleThread == null || 
				AppNameQueryEngine.handleThread.isAlive() == false) {
			AppNameQueryEngine.handleThread = new Thread(new RequestHandler());
			AppNameQueryEngine.handleThread.start();
		}
    }
}

class RequestHandler implements Runnable {
	static final String TAG = "RequestHandler";

	@Override
	public void run() {
		boolean printOut = false;
		while (!Thread.currentThread().isInterrupted()) {
			try {
				int queueSize = AppNameQueryEngine.requestQueue.size();
				Log.d(TAG, "lsof dequeue, queue size = " + queueSize);
				if (queueSize > 50 && printOut == false) {
					printOut = true;
					Iterator<Request> itr = AppNameQueryEngine.requestQueue.iterator();					
					while(itr.hasNext()){
						AppNameRequest req = (AppNameRequest)itr.next();						
						Log.d(TAG, ":"+req.srcPort+"<->"+req.destIP+":"+req.destPort);
					}
				}
				Request req = AppNameQueryEngine.requestQueue.take(); //block on empty queue
				if (req.process() == true) {
					AppNameQueryEngine.requestQueue.put(req);
				} else {
					String key = req.toString();
					int value = AppNameQueryEngine.requestMap.getCount(key) - 1;
					AppNameQueryEngine.requestMap.setCount(key, value);
					//AppNameQueryEngine.requestMap.showMap();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				Log.d(TAG, "Error of dequeuing request: " + e.getMessage());
			} 
		}
		
	}
	
}



/**
 * Base class of request class
 * 
 * @author leo
 * 
 */
abstract class Request {
	/**
	 * Request handle function 
	 * @return true - if the request need to be handled again
	 */
	public abstract boolean process();
}

/**
 * AppNameRequest class
 */
class AppNameRequest extends Request {
	static final String TAG = "AppNameRequest";
	static final int TRYTIMES = 2;
	
	public String destIP;
	public int destPort;
	public int srcPort;
    public int ttl;
    
    AppNameRequest(String destIP, int destPort, int srcPort) {
		this.destIP = destIP;
		this.destPort = destPort;
		this.srcPort = srcPort;
    	ttl = TRYTIMES;
    }
    
    public boolean process() {
    	boolean tryAgain = false;
    	Log.d(TAG, "processing " + this.toString());
    	Connection found = AppNameQueryEngine.Connections.find(destIP, destPort, srcPort);
    	if (found == null) {
    		AppNameQueryEngine.Connections.refresh();
    		//Log.d(TAG, "After refreshing");
    		//AppNameQueryEngine.Connections.showList();
    		if (AppNameQueryEngine.requestMap.getCount(this.toString()) - 1 <= 0) {
        		if (--this.ttl > 0) tryAgain = true;
    		} 
    	} else {
    		if (found.getPkgName() == null) found.setPkgName(Utility.getPKGNameFromPidByCmdLine(found.pid));
    	}
        return tryAgain;
    }
	
	public String toString(){
		 return srcPort + "-" + destIP + ":" + destPort;		
	}
}

/**
 * Connection class to keep network address and process ID information of a
 * connection
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
		this.timestamp = System.currentTimeMillis() / 1000;
	}

	public boolean match(String destIP, int destPort, int srcPort) {
		return (this.destPort == destPort && this.srcPort == srcPort && this.destIP
				.equalsIgnoreCase(destIP));
	}

	/**
	 * judge if a conection information expires according to the given threhold
	 * 
	 * @param threshold
	 *            (seconds)
	 * @return
	 */
	public boolean expire(Long threshold) {
		return (System.currentTimeMillis() / 1000 - this.timestamp >= threshold);
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
		return "" + this.pkgName + "::" + this.pid + "::" + "local_ip" + ":" + this.srcPort + "->"
				+ this.destIP + ":" + this.destPort;
	}
}

/**
 * Wrapper a cache of connections and the operations on it
 * 
 * @author leo
 * 
 */
class ConnectionList {
	static final String TAG = "ConnectionList";
	ArrayList<Connection> mConnections = null;
	static final Long THRESHOLD = 1800L; // seconds for expiring

	public ConnectionList() {
		mConnections = new ArrayList<Connection>();
	}

	/**
	 * Find a connection by given network address. For efficiency, it filters
	 * out the expiring connection when looking up Since the remove operation of
	 * ArrayList is very heavy, we add unexpired connection to new ArrayList
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
	 * 
	 * @param conn
	 * @return
	 */
	private synchronized boolean find(Connection conn) {
		boolean found = false;
		for (int i = 0; i < mConnections.size(); i++) {
			Connection item = mConnections.get(i);
			if (item.match(conn.destIP, conn.destPort,
					conn.srcPort) == true) {
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
		String command = "lsof -P -n -i | grep -";
		try {
			ArrayList<String> results = Utility.runRootCommand(command, true);
			for (int i = 0; i < results.size(); i++) {
				Connection conn = createConnectionByString(results.get(i));
				if (conn != null && find(conn) == false){
					conn.setPkgName(Utility.getPKGNameFromPidByCmdLine(conn.pid));
					mConnections.add(conn);
				}
			}
		} catch (Exception e) {
		}
		return;
	}

	public void showList() {
		for (int i = 0; i < mConnections.size(); i++) {
			Log.d(TAG, mConnections.get(i).toString());
		}
		Log.d(TAG, "===============================================");
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
		return (index2 > index1 && index1 >= 0) ? s.substring(index1 + 2,
				index2) : null;
	}

	private int getDestPort(String s) {
		int index1 = s.indexOf("->");
		int index2 = s.lastIndexOf(':');
		return (index2 > index1 && index1 >= 0) ? Integer.parseInt(s
				.substring(index2 + 1)) : -1;
	}

	private int getSrcPort(String s) {
		int index1 = s.indexOf("->");
		int index2 = s.indexOf(':');
		return (index2 < index1 && index1 >= 0) ? Integer.parseInt(s.substring(
				index2 + 1, index1)) : -1;
	}

}

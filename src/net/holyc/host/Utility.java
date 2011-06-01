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
import java.nio.ByteBuffer;
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
	    return (resultLines.size() == 0) ? null : resultLines.get(0);
	}
	
    public static ArrayList<String> readLinesFromFile(String filename) {
		ArrayList<String> lines = new ArrayList<String>();
		File file = new File(filename);
		if (!file.exists() || !file.canRead()) {
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
    
    public static String getPKGNameFromPidByCmdLine(int pid) {
    	String fileName = "/proc/" + pid + "/cmdline"; 
    	ArrayList<String> lines = readLinesFromFile(fileName);
    	return (lines.size() > 0) ? lines.get(0) : null;
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
		AppNameQueryEngine.sendQueryRequest("74.125.224.106", 80, 36971, cxt);
		AppNameQueryEngine.sendQueryRequest("74.125.224.97", 80, 54955, cxt);
		AppNameQueryEngine.sendQueryRequest("61.135.218.49", 80, 58099, cxt);
		AppNameQueryEngine.sendQueryRequest("72.14.213.139", 80, 38731, cxt);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d(TAG, "query1: " + AppNameQueryEngine.getPKGNameFromAddr("74.125.224.106", 80, 36971, cxt));
		Log.d(TAG, "query2: " + AppNameQueryEngine.getPKGNameFromAddr("74.125.224.97", 80, 54955, cxt));
		Log.d(TAG, "query3: " + AppNameQueryEngine.getPKGNameFromAddr("61.135.218.49", 80, 58099, cxt));	
		Log.d(TAG, "query4: " + AppNameQueryEngine.getPKGNameFromAddr("72.14.213.139", 80, 38731, cxt));	

    }
	public static ByteBuffer getByteBuffer(byte[] data){
		ByteBuffer bb;
		bb = ByteBuffer.allocate(data.length);
		bb.put(data);
		bb.flip();
		return bb;
	}
}

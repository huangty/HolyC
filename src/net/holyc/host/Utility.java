package net.holyc.host;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.holyc.jni.NativeCallWrapper;
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
    	String command = "su -c \"lsof -n -nn -i ";
    	if (remoteIP != null) command += "@" + remoteIP;
    	if (remotePort > 0) command += ":" + remotePort;
    	command += " | grep -v COMMAND";
    	if (localPort > 0) command += " | grep " + localPort;
    	command += "\"";
    	//Log.d(TAG, "command: " + command);
    	String result = NativeCallWrapper.getResultByCommand(command);
    	try {
    		String[] items = result.split("\t| +");
    		pid = Integer.parseInt(items[1]);
    	} catch (Exception e) {
    		Log.d(TAG, "error of getting pid from port");
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
    		if (pid == process.get(i).pid) {
    			appInfo = process.get(i);
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
    		if (pid == service.get(i).pid) {
    			servInfo = service.get(i);
    			break;
    		}
    	}
    	return servInfo;
    }
    
    public static String getPKGNameFromAddr(String remoteIP, int remotePort, int localPort, Context cxt) {
    	String pkgName = null;
    	int pid = getPidFromAddr(remoteIP, remotePort, localPort);
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
    
    private static String getLabelFromPKGName(String PKGName, Context cxt) {
    	PackageManager pk = cxt.getPackageManager();
    	String appLabel = null;
    	if (PKGName == null) return appLabel;
    	try {
    		ApplicationInfo ai = pk.getApplicationInfo(PKGName, 0);
    		appLabel = ai.loadLabel(pk).toString();
    	} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
    		appLabel = PKGName;
			Log.d(TAG, "Label of " + PKGName + "Not Found");
		}
    	return appLabel;
    }
    
    /**
     * Can invoke the test code in any Context. For example,
     * in controlUI activity, you can call test as follows:
     * 
     * 
     */
	public static void commandTest(Context cxt) {
		String pkgName = getPKGNameFromAddr("74.125.224.33", 80, 42015, cxt); //src ip + src port
    	Log.d(TAG, "get pkgname : " + pkgName);
    	Log.d(TAG, "get label : " + getLabelFromPKGName(pkgName, cxt));
    }
}
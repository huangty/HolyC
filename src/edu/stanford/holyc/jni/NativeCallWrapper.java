package edu.stanford.holyc.jni;

import android.os.Handler;
import android.util.Log;


public class NativeCallWrapper{
	static Handler h;
	public NativeCallWrapper(){}
	public NativeCallWrapper(Handler h){
		this.h = h;
	}
	static String TAG="HOLYC:NativeCall";
	
	static {
		try {
		    Log.i(TAG, "Trying to load libExecNativeCmd.so");    
			System.loadLibrary("ExecNativeCmd");
		} catch (UnsatisfiedLinkError ule) {
            Log.i(TAG, "Could not load libExecNativeCmd.so");
        }
      
	}
	//public static native String getProp(String name);
    public static native int runCommand(String command);
}

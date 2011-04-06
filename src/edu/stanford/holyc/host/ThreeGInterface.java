package edu.stanford.holyc.host;

import java.io.File;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import edu.stanford.holyc.jni.NativeCallWrapper;


public class ThreeGInterface extends HostInterface {
	private Context context = null;
	
	public ThreeGInterface(Context context) {
		this.context = context;
	}
	
	public ThreeGInterface() {}
	
	@Override
    public String searchName() {
    	final String[] devices = {"ppp0", "rmnet0", "pdp0"};
    	for (int i = 0; i < devices.length; i++) {
    		File file = new File("/sys/devices/virtual/net/" + devices[i]);
    		if (file.exists()) return devices[i];
    	}
    	return null;
    }
	
	@Override
	public HostInterface searchGateway() {
		ThreeGInterface gateway = new ThreeGInterface();
		String gwIP = NativeCallWrapper.getProp("net.rmnet0.gw");
		gateway.setIP(gwIP);
		if (gwIP != null) {
			gateway.setMac(getMacFromIPByPing(gwIP));
		}
		return gateway;
	}
	
	@Override
	public boolean getInterfaceEnable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Activity.CONNECTIVITY_SERVICE);
        Method m = Utility.getMethodFromClass(cm, "getMobileDataEnabled");
        Object enabled = Utility.runMethodofClass(cm, m);
		return (Boolean) enabled;
	}
	
	@Override
	public void setInterfaceEnable(boolean enable) {
		if (getInterfaceEnable() == enable) return;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Activity.CONNECTIVITY_SERVICE);
        Method m = Utility.getMethodFromClass(cm, "setMobileDataEnabled");
        Utility.runMethodofClass(cm, m, enable);
	}
	
}
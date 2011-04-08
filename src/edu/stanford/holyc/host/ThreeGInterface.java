package edu.stanford.holyc.host;

import java.io.File;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import edu.stanford.holyc.jni.NativeCallWrapper;

/**
*
*
* @author Yongqiang Liu (yliu78@stanford.edu)
*/

public class ThreeGInterface extends HostInterface {
	private Context context = null;
	String TAG = "HOLYC.3GInterface";
	
	public ThreeGInterface(Context context) {
		this.context = context;
	}
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
		HostInterface gateway = new ThreeGInterface(context);
		String devName = getName();
		String prop = "net." + devName +".gw";
		String gwIP = NativeCallWrapper.getProp(prop);
		gateway.setIP(gwIP);
		if (gwIP != null) {
			gateway.setMac(getMacFromIPByArpRequest(gwIP));
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
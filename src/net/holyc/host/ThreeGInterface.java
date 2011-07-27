package net.holyc.host;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

/**
*
*
* @author Yongqiang Liu (yliu78@stanford.edu)
*/

public class ThreeGInterface extends HostInterface {
	private Context context = null;
	private ArrayList<String> routeNets;
	String TAG = "HOLYC.3GInterface";
	
	public ThreeGInterface(Context context) {
		this.context = context;
		routeNets = new ArrayList<String>();
	}
	public void addNet(String net){
		routeNets.add(net);
	}
	public ArrayList<String> getNets(){
		return routeNets;
	}
	public void clearNets(){
		routeNets.clear();
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
		String prop = devName.equalsIgnoreCase("ppp0") ? 
				"net." + devName + ".remote-ip" : "net." + devName + ".gw";
		String gwIP = Utility.getProp(prop);
		Log.d(TAG, "3G gwIP is " + gwIP);
		gateway.setIP(gwIP);
		if (gwIP != null) {
			//gateway.setMac(getMacFromIPByArpRequest(gwIP));
			//gateway.setMac(getMacFromIPByPing(gwIP));
			gateway.setMac("02:50:f3:00:00:00");
		}
		return gateway;
	}
	
	@Override
	public String getIP() {
		String ip = super.getIP();
		
		if (ip == null) {
			String devName = getName();
			String prop = devName.equalsIgnoreCase("ppp0") ? 
					"net." + devName + ".local-ip" : "net." + devName + ".ip";
			ip = Utility.getProp(prop);
		}else if(ip.length() > 15){
			ip = null;
		}
		return ip;
	}
	
	@Override
	public String getMask() {
		String devName = getName();
		String mask = devName.equalsIgnoreCase("ppp0") ? "255.255.255.255" : super.getMask();
		return mask;
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
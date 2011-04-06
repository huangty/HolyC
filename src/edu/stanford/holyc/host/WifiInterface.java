package edu.stanford.holyc.host;


import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import edu.stanford.holyc.jni.NativeCallWrapper;

public class WifiInterface extends HostInterface {
	static final String TAG = "WifiInterface"; 
	private Context context;
	
	public WifiInterface(Context context) {
		this.context = context;
	}
	
	public WifiInterface() {}
	
	@Override	
	public String searchName() {
		return NativeCallWrapper.getProp("wifi.interface");
	}
	
	/**
	 * Provides a method to get IP, which does not use busybox
	 */
	/*@Override
	public String searchIP() {
		if (context == null || getInterfaceEnable() == false) return super.searchIP();
		WifiInfo wifiInf = getWifiInfo();
		return IPv4.fromIPv4Address(wifiInf.getIpAddress());
	}*/
	
	/**
	 * Provides a method to get mac, which does not use busybox
	 */
	@Override
	public String searchMac() {
		if (context == null || getInterfaceEnable() == false) return super.searchMac();
		WifiInfo wifiInf = getWifiInfo();
		return wifiInf.getMacAddress();
	}
	
	@Override
	public HostInterface searchGateway() {
		if (context == null || getInterfaceEnable() == false) return super.searchGateway();
		WifiInterface gateway = new WifiInterface();
		String gwIP = NativeCallWrapper.getProp("dhcp.eth0.gateway");
		gateway.setIP(gwIP);
		if (gwIP != null) {
			String mac = getMacFromIPByPing(gwIP);
			//Log.d(TAG, "get mac is " + mac);
			gateway.setMac(mac);
		}
		return gateway;
	}
	
	
	@Override
	public boolean getInterfaceEnable() {
		WifiManager wifiMan = getWifiManager();
		return wifiMan.isWifiEnabled();
	}
	
	@Override
	public void setInterfaceEnable(boolean enable) {
    	WifiManager wifiMan = getWifiManager();
    	if (wifiMan.isWifiEnabled() != enable) {
    			wifiMan.setWifiEnabled(enable);
    	}
	}
	
	private WifiInfo getWifiInfo() {
		return getWifiManager().getConnectionInfo();
	}
	
	private WifiManager getWifiManager() {
		return (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	}
}
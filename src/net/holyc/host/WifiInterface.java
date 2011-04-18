package net.holyc.host;


import net.beaconcontroller.packet.IPv4;
import net.holyc.jni.NativeCallWrapper;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
*
*
* @author Yongqiang Liu (yliu78@stanford.edu)
*/

public class WifiInterface extends HostInterface {
	static final String TAG = "HOLYC.WifiInterface"; 
	private Context context;
	
	public WifiInterface(Context context) {
		this.context = context;
	}	
	@Override	
	public String searchName() {
		return NativeCallWrapper.getProp("wifi.interface");
	}
	
	/**
	 * Provides a method to get IP, which does not use busybox
	 */
	@Override
	public String searchIP() {
		if (context == null || getInterfaceEnable() == false) return super.searchIP();
		WifiInfo wifiInf = getWifiInfo();
		int ipAddress = wifiInf.getIpAddress();
		
		//return IPv4.fromIPv4Address(wifiInf.getIpAddress());
		return String.format("%d.%d.%d.%d",
				(ipAddress & 0xff),
				(ipAddress >> 8 & 0xff),
				(ipAddress >> 16 & 0xff),
				(ipAddress >> 24 & 0xff));
	}
	
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
		WifiInterface gateway = new WifiInterface(context);
		String prop = "dhcp."+ getName() + ".gateway";
		String gwIP = NativeCallWrapper.getProp(prop);
		gateway.setIP(gwIP);
		if (gwIP != null) {
			//String mac = getMacFromIPByArpRequest(gwIP);
			String mac = getMacFromIPByPing(gwIP);
			Log.d(TAG, "gateway mac is " + mac);
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
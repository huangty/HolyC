package edu.stanford.holyc.host;

import android.util.Log;
import edu.stanford.holyc.jni.NativeCallWrapper;

/**
 * The interface class is a super class, which defines
 * the basic attributes of an interface and provides 
 * API to get/set these attributes.
 * 
 *   @author Leo
 */
public abstract class HostInterface {
	static final String TAG = "HostInterface";
	private String name = null;
	private String IP = null;
	private String mask = null;
	private String mac = null;
	private HostInterface gateway;
	
	public abstract void setInterfaceEnable(boolean enable);;
	
	public abstract boolean getInterfaceEnable();
	
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setIP(String IP) {
		this.IP = IP;
	}
	
	public void setMask(String mask) {
		this.mask = mask;
	}
	
	public void setMac(String mac) {
		this.mac = mac;
	}
	
	public void setGateway(HostInterface gateway) {
		this.gateway = gateway;
	}
	
	public String getName() {
		if (name == null) name = searchName();
		return name;
	}
	
	public String getIP() {
		if (IP == null) IP = searchIP();
		return IP;
	}
	
	public String getMask() {
		if (mask == null) mask = searchMask();
		return mask;
	}
	
	public String getMac() {
		if (mac == null) mac = searchMac();
		return mac;
	}
	
	public HostInterface getGateway() {
		if (gateway == null) gateway = searchGateway();
		return gateway;
	}
	
	/**
	 * It must be overrode by derived class.
	 */
	public String searchName() {
		return null;
	}
	
	/**
	 * the super class provides a set of methods to 
	 * retrieve IP address by using busybox output.
	 * If the device does not have busybox, the derived
	 * class should implement its own search methods 
	 */
	public String searchIP() {
		if (name == null) return null;
		String command = "su -c \" busybox ifconfig " + name + " | grep inet\"";
		String token = "addr:";
		return this.getValueByBusyBox(command, token);
	}
	
	public String searchMask() {
		if (name == null) return null;
		String command = "su -c \" busybox ifconfig " + name + " | grep Mask\"";
		String token = "Mask:";
		return this.getValueByBusyBox(command, token);
	}
	
	public String searchMac() {
		if (name == null) return null;
		String command = "su -c \" busybox ifconfig " + name + " | grep HWaddr\"";
		String token = "HWaddr ";
		return this.getValueByBusyBox(command, token);
	}
	
	public HostInterface searchGateway() {
		return null;
	}
	
	
	public String getMacFromIPByPing(String IP) {
		String mac = null;
		NativeCallWrapper.runCommand("su -c \"busybox ping " + IP + " -c 1 -w 1\"");
		String resLine = NativeCallWrapper.getResultByCommand("su -c \"arp -a -n | grep " + IP + "\"");
		String[] items = resLine.split("\t| +");
		for (int i = 0; i < items.length; i++) {
			//Log.d(TAG, "items[" + i + "]: " + items[i]);
			if (items[i].contains(":") == true) {
				mac = items[i];
				break;
			}
		}
		return mac;
	}
	
	private String getValueByBusyBox(String command, String token) {
		String value = null;
		String resLine = NativeCallWrapper.getResultByCommand(command);
		//Log.d(TAG, "resLine is " + resLine);
		String[] items = resLine.split("  ");
		for (int i = 0; i < items.length; i++) {
			int index = items[i].indexOf(token);
			//Log.d(TAG, "items[" + i + "]: " + items[i] + "; index = " + index);
			if (index >= 0) {
				index += token.length();
				//Log.d(TAG, "target is " + items[i] + "; index = " + index);
				value = items[i].substring(index);
				break;
			} 
		}
		return value;
	}

}

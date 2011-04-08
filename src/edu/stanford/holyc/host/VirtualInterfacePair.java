package edu.stanford.holyc.host;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;
import edu.stanford.holyc.jni.NativeCallWrapper;

/**
* The class to manage each pair of veth
* 
* @author Te-Yuan Huang (huangty@stanford.edu)
*/
class VirtualInterfacePair{
	private Context context = null;
	private VirtualInterface veth0;
	private VirtualInterface veth1;
	private String TAG = "VirtualInterfacePair";
	
	public VirtualInterfacePair() {
		init();
	}
	
	public void init(){
		String result = NativeCallWrapper.getResultByCommand("su -c \"/data/local/bin/busybox ip link | grep veth0\"");
		if(result == ""){ //the veth0/1 pair hasn't been created 
			//NativeCallWrapper.runCommand("su -c \"/data/local/bin/busybox ip link add type veth\"");		
			String[] command = {"su", "-c", "/data/local/bin/busybox ip link add type veth"};
			try {
				Runtime.getRuntime().exec(command).waitFor();
			} catch (Exception e) {			
				e.printStackTrace();
			}	
		}else{
			Log.d(TAG, "the veth0/1 pair is already been created");
		}
		veth0 = new VirtualInterface("veth0");
		veth0.setInterfaceEnable(true);
		veth1 = new VirtualInterface("veth1");
		veth1.setInterfaceEnable(true);
	}
	
	public String getNames(){
		String names = veth0.getName() + " " + veth1.getName();
		return names;
	}
	public String getIPs(){
		String ips =  "veth0: " + veth0.getIP() + " veth1: " + veth1.getIP();
		return ips;
	}
	public String getMacs(){
		String macs =  "veth0: " + veth0.getMac() + " veth1: " + veth1.getMac();
		return macs;
	}
	public void setIP(String dev, String ip, String mask){
		if(dev == "veth1"){
			veth1.setIP(ip, mask);
		}else if (dev == "veth0"){
			veth0.setIP(ip, mask);
		}else{
			Log.d(TAG, "trying to set ip to an non-existing virtual interface");
		}
	}
	
	public VirtualInterface getVeth0(){
		return veth0;
	}
	public VirtualInterface getVeth1(){
		return veth1;
	}	
}
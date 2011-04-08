package edu.stanford.holyc.host;


import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import android.util.Log;
import edu.stanford.holyc.jni.NativeCallWrapper;

/**
 * The class to record all the information about OVS
 * 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 */

class VirtualSwitch{	
	String TAG = "VirtualSwitch";
	HashMap<String, LinkedList<String>> dptable;
	VirtualSwitch(){		
		dptable = new HashMap<String, LinkedList<String>>();
		loadKernelModule();
	}
	public void loadKernelModule(){
		String result = NativeCallWrapper.getResultByCommand("lsmod | grep openvswitch");
		if(result == ""){
			NativeCallWrapper.runCommand("su -c \"insmod /sdcard/openvswitch_mod.ko\"");
		}
	}
	public void addDP(String dp){
		if(!dptable.containsKey(dp)){
			NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-dp "+ dp +"\"");
		}else{ 
			//no need to add datapatch
			return;
		}
		dptable.put(dp, new LinkedList<String>());		
	}
	public void addIF(String dp, String intf){
		if(dptable.containsKey(dp)){
			NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-if "+ dp + " " + intf +"\"");
			dptable.get(dp).add(intf);
		}else{
			Log.e(TAG, "datapatch not exists");
		}
	}
	public String getDP(int index){
		Set<String> dps = dptable.keySet();
		String dpname = "";
		Iterator<String> it = dps.iterator();
		int i = 0;
		while(it.hasNext()){
			if(i== index){
				dpname = it.next();
				break;
			}
			i++;
		}
		return dpname;
	}
	public String getDPs(){
		Set<String> dps = dptable.keySet();
		String dpname = "";
		Iterator<String> it = dps.iterator();
		while(it.hasNext()){
			dpname = dpname + it.next() + " ";
		}
		return dpname;
	}
}
package net.holyc.host;


import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import net.holyc.jni.NativeCallWrapper;

import android.util.Log;

/**
 * The class to record all the information about OVS
 * 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 */

class VirtualSwitch{	
	String TAG = "HOLYC.VirtualSwitch";
	private static HashMap<String, LinkedList<String>> dptable;
	VirtualSwitch(){		
		dptable = new HashMap<String, LinkedList<String>>();
		loadKernelModule();
	}
	public void loadKernelModule(){		
		String result = NativeCallWrapper.getResultByCommand("su -c \"lsmod | grep openvswitch\"");
		
		StringBuffer sb = new StringBuffer("openvswitch");
		if(!result.contains(sb.subSequence(0, sb.length()))){
			NativeCallWrapper.runCommand("su -c \"insmod /data/local/lib/openvswitch_mod.ko\"");
		}else{
			Log.d(TAG, "no need to insmod, since lsmod result = " + result);
		}
	}
	public void addDP(String dp){
		if(!dptable.containsKey(dp)){
			NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-dp "+ dp +"\"");
			Log.d(TAG, "OVS add-dp " + dp);
		}else{ 
			//no need to add datapatch
			Log.d(TAG, "no need to add dp0, since it's already there");
			return;
		}
		dptable.put(dp, new LinkedList<String>());		
	}
	public void addIF(String dp, String intf){
		if(dptable.containsKey(dp)){
			String[] command = {"su", "-c", "/data/local/bin/ovs-dpctl add-if " + dp + " " + intf};
			try {
				Runtime.getRuntime().exec(command).waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
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
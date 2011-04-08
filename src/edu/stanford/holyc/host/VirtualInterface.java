package edu.stanford.holyc.host;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import edu.stanford.holyc.jni.NativeCallWrapper;

/**
* The class to control virtual interface 
*
* @author Te-Yuan Huang (huangty@stanford.edu)
*/

public class VirtualInterface extends HostInterface {
	private boolean enabled = false;
	public VirtualInterface(String name) {		
		setName(name);
	}
			
	@Override
	public String searchName() {
		return getName();
	}

	@Override
	public void setInterfaceEnable(boolean enable) {
		String[] command = null;
		if(enable){
			command = new String[]{"su", "-c", "/data/local/bin/busybox ifconfig " + getName() + " up"};
		}else{
			command = new String[]{"su", "-c", "/data/local/bin/busybox ifconfig " + getName() + " down"};
		}
		try {
			Runtime.getRuntime().exec(command).waitFor();
		} catch (Exception e) {			
			e.printStackTrace();
		}
		enabled = true;
	}

	@Override
	public boolean getInterfaceEnable(){
		return enabled;
	}

	@Override
	public void setIP(String ip){
		String[] command = {"su", "-c", "/data/local/bin/busybox ifconfig " + getName() + " " + ip};
		try {
			Runtime.getRuntime().exec(command).waitFor();
		} catch (Exception e) {			
			e.printStackTrace();
		}
	}

	public void setIP(String ip, String mask){
		String[] command = {"su", "-c", "/data/local/bin/busybox ifconfig " + getName() + " " + ip + " netmask " + mask};
		try {
			Runtime.getRuntime().exec(command).waitFor();
		} catch (Exception e) {			
			e.printStackTrace();
		}
	}	
	
}
package edu.stanford.lal;

import java.util.HashMap;

import net.holyc.host.AppNameQueryEngine;
import net.holyc.host.SimpleAppNameQuery;
import net.holyc.openflow.handler.FlowSwitch;

import org.openflow.protocol.OFMatch;
import org.openflow.util.U16;
import org.openflow.util.U8;

import android.content.Context;
import android.util.Log;

/**
 * Customized L2 learning switch
 * 
 * @author ykk
 */
public class LalFlowSwitch extends FlowSwitch {
	/**
	 * Log name
	 */
	private String TAG = "Lal.FlowSwitch";
	/**
	 * Local port number
	 */
	public static short LOCAL_PORT = 1;

	/**
	 * Hashmap for app name
	 */
	public static HashMap<String, Object> appNames = new HashMap<String, Object>();

	public int sendQuery(OFMatch ofm, Context context) {
		int cookie = 0;
		if (ofm.getNetworkProtocol() == 0x06
				|| ofm.getNetworkProtocol() == 0x11) {
			// it is tcp or udp
			String remoteIP = "";
			int remotePort = 0;
			int localPort = 0;
			String localIP = "";
			
			if (ofm.getInputPort() == LOCAL_PORT) {
				remoteIP = ipToString(ofm.getNetworkDestination());
				remotePort = U16.f(ofm.getTransportDestination());
				localIP = ipToString(ofm.getNetworkSource());
				localPort = U16.f(ofm.getTransportSource());				
			} else {
				remoteIP = ipToString(ofm.getNetworkSource());
				remotePort = U16.f(ofm.getTransportSource());
				localIP = ipToString(ofm.getNetworkDestination());
				localPort = U16.f(ofm.getTransportDestination());
			}
			String appName = SimpleAppNameQuery.getPKGNameFromAddr(context, localIP, localPort, remoteIP, remotePort);
			Log.d(TAG, appName+"::"+localPort+"->"+remoteIP+":"+remotePort);
			//AppNameQueryEngine.sendQueryRequest(remoteIP, remotePort, localPort);					
			if(appName == null){
				cookie = -1;
			}else{
				cookie = appName.hashCode();
				Lal.appNames.put(new Long(cookie).toString(), appName);
			}
			
		}
		return cookie;
	}

	protected static String ipToString(int ip) {
		return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24))) + "."
				+ Integer.toString((ip & 0x00ff0000) >> 16) + "."
				+ Integer.toString((ip & 0x0000ff00) >> 8) + "."
				+ Integer.toString(ip & 0x000000ff);
	}

}

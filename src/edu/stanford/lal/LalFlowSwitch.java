package edu.stanford.lal;

import java.util.HashMap;

import net.holyc.host.AppNameQueryEngine;
import net.holyc.openflow.handler.FlowSwitch;

import org.openflow.protocol.OFMatch;
import org.openflow.util.U16;
import org.openflow.util.U8;

import android.content.Context;

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
			/** @TODO: (1) only send request when it's localIP or multicast IP (224.0.0.0 - 239.255.255.255) 
			 *         (2) keep track of wifi states and 3G states **/
			AppNameQueryEngine.sendQueryRequest(remoteIP, remotePort, localPort);
		}
		return 0;
	}

	protected static String ipToString(int ip) {
		return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24))) + "."
				+ Integer.toString((ip & 0x00ff0000) >> 16) + "."
				+ Integer.toString((ip & 0x0000ff00) >> 8) + "."
				+ Integer.toString(ip & 0x000000ff);
	}

}

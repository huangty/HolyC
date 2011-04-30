package edu.stanford.lal;

import java.util.HashMap;

import net.holyc.HolyCIntent;
import net.holyc.host.Utility;
import net.holyc.openflow.handler.FlowSwitch;

import org.openflow.protocol.OFMatch;
import org.openflow.util.U8;
import org.openflow.util.U16;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Customized L2 learning switch
 *
 * @author ykk
 */
public class LalFlowSwitch
    extends FlowSwitch
{
    /** Log name
     */
    private String TAG = "Lal.FlowSwitch";
    /** Local port number
     */
    public static short LOCAL_PORT = 1;
    /** Hashmap for app name
     */
    public static HashMap<String, Object> appNames = new HashMap<String, Object>();
    
    public int getCookie(OFMatch ofm, Context context)
    {  
	String remoteIP = "";
	int remotePort = 0;
	int localPort = 0;
	if (ofm.getInputPort() == LOCAL_PORT)
	{
	    remoteIP = ipToString(ofm.getNetworkDestination());
	    remotePort = U16.f(ofm.getTransportDestination());
	    localPort = U16.f(ofm.getTransportSource());
	}
	else
	{
	    remoteIP = ipToString(ofm.getNetworkSource());
	    remotePort = U16.f(ofm.getTransportSource());
	    localPort = U16.f(ofm.getTransportDestination());
	}

	//String appname = null;
	String appname = Utility.getPKGNameFromAddr(remoteIP, remotePort, 
						    localPort, context);
	if (appname == null)
	    appname = "System/Unidentified App";
	
	//Broadcast new application name
	if (appNames.get(appname) == null)
	{
	    appNames.put(appname, appname);
	    Intent i = new Intent(HolyCIntent.LalAppFound.action);
	    i.setPackage(context.getPackageName());
	    i.putExtra(HolyCIntent.LalAppFound.str_key, appname);
	    context.sendBroadcast(i);
	}

	return appname.hashCode();
    }

    
    protected static String ipToString(int ip)
    {                                                                                           
        return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24))) + "."
	    + Integer.toString((ip & 0x00ff0000) >> 16) + "."                                        
	    + Integer.toString((ip & 0x0000ff00) >> 8) + "."                                        
	    + Integer.toString(ip & 0x000000ff);                                                     
    }

}

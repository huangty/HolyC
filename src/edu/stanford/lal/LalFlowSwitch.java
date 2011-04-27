package edu.stanford.lal;

import net.holyc.openflow.handler.FlowSwitch;
import org.openflow.protocol.OFMatch;
import org.openflow.util.U8 ;
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
    
    public int getCookie(OFMatch ofm)
    {  
	//Utility.getPKGNameFromAddr(
	String remoteIP = "";
	int remotePort = 0;
	int localPort = 0;
	if (ofm.getInputPort() == LOCAL_PORT)
	{
	    remoteIP = ipToString(ofm.getNetworkDestination());
	    remotePort = ofm.getTransportDestination();
	    localPort = ofm.getTransportSource();
	}
	else
	{
	    remoteIP = ipToString(ofm.getNetworkSource());
	    remotePort = ofm.getTransportSource();
	    localPort = ofm.getTransportDestination();
	}

	String appname = null;
	//String appname = Utility.getPKGNameFromAddr(remoteIP, remotePort, 
	//localPort, context);
	if (appname == null)
	    appname = "System/Unidentified App";

	Log.d(TAG, "Packet in with remote ip "+remoteIP+
	      " and port "+remotePort+" and local port "+localPort);
	
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

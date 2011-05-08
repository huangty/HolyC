package edu.stanford.lal.permission;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;

/** A class to define the format of Permission Response to Dynamic Firewall
* *
* @author huangty
* @date Apr 2011
*/

public class PermissionResponse{
	private OFMatch ofm;
	private boolean allow;
	private OFPacketIn ofpi;
	private int control_socket_channel; 


	public PermissionResponse(OFMatch _ofm, OFPacketIn _ofpi, boolean _allow, int _socket_channel){
		ofm = _ofm;
		ofpi = _ofpi;
		allow = _allow;
		control_socket_channel = _socket_channel; 
	}
	
	public OFMatch getOFMatch(){
		return ofm;
	}
	
	public OFPacketIn getOFPacketIn(){
		return ofpi;
	}
	
	public boolean getResponse(){
		return allow;
	}
	
	public int getSocketChannelNumber(){
		return control_socket_channel;
	}
}
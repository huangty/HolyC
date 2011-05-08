package edu.stanford.lal.permission;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;

/** A class to define the format of Permission Enquiry to Dynamic Firewall
* *
* @author huangty
* @date Apr 2011
*/

public class PermissionInquiry{
	private OFMatch ofm;
	private OFPacketIn ofpi;
	private String app_name;
	private int app_cookie;
	private int control_socket_channel; 
	
	public PermissionInquiry(OFMatch _ofm, OFPacketIn _ofpi, String _appname, int _appcookie, int _socket_channel){
		ofm = _ofm;
		ofpi = _ofpi;
		app_name = _appname;
		app_cookie = _appcookie;
		control_socket_channel = _socket_channel;
	}
	
	public OFMatch getOFMatch(){
		return ofm;
	}
	
	public OFPacketIn getOFPacketIn(){
		return ofpi;
	}
	
	public String getAppName(){
		return app_name;
	}
	
	public int getAppCookie(){
		return app_cookie;
	}
	
	public int getSocketChannelNumber(){
		return control_socket_channel;
	}
}
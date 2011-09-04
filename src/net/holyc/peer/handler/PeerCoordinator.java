package net.holyc.peer.handler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.beaconcontroller.packet.Ethernet;
import net.beaconcontroller.packet.IPv4;
import net.holyc.HolyCIntent;
import net.holyc.controlUI;
import net.holyc.host.EnvInitService;
import net.holyc.host.Utility;
import net.holyc.ofcomm.OFCommService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.OFStatisticsFactory;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.U16;

import com.google.gson.Gson;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class PeerCoordinator extends BroadcastReceiver {
	
	private String TAG = "HOLYC.PeerCoordinator";
	private static Gson gson = new Gson();
	private static List<String> default_rule_match_string = null; 
	@Override
	public void onReceive(Context context, Intent intent) {		
		//OFPacketOut udp_notify_msg = new OFPacketOut();
		
		byte[] ofdata = null;
		int ofport = 0;
		List<String> ip_prevList = new LinkedList<String>();
		List<String> ip_newList = new LinkedList<String>();
		List<String> ip_notifyList = new LinkedList<String>();
		//Log.d(TAG, "got a stat reply");
		if (intent.getAction().equals(HolyCIntent.OFStatsReply_Intent.action)) {
			ofdata = intent.getByteArrayExtra(HolyCIntent.OFStatsReply_Intent.data_key);
			ofport = intent.getIntExtra(HolyCIntent.OFStatsReply_Intent.port_key, -1);
			
			OFStatisticsReply ofsr = new OFStatisticsReply();
			OFStatisticsFactory statisticsFactory = new BasicFactory();
			ofsr.setStatisticsFactory(statisticsFactory);
			ofsr.readFrom(Utility.getByteBuffer(ofdata));
			
			if(ofsr.getStatisticType() == OFStatisticsType.FLOW){
				//Log.d(TAG, "StatisticType is for flows");
				if(default_rule_match_string == null){
					genDefaultOFMatchString();
				}
				
				if(controlUI.interface_just_disabled.equals("") || controlUI.interface_just_enabled.equals("")){
					Log.d(TAG, "WARNING!!! Cannot determine ips");
					return;
				}
				String ip_prev = findIPfromInterface(controlUI.interface_just_disabled);				
				ip_prevList.add(ip_prev);
				String ip_new = findIPfromInterface(controlUI.interface_just_enabled);
				ip_newList.add(ip_new);
				List<OFAction> new_actions = genOFNewAction(controlUI.interface_just_enabled);
				Log.d(TAG, "ip_prev ( "+ controlUI.interface_just_disabled + "): " + ip_prev +"ip_new ("+controlUI.interface_just_enabled+"):" + ip_new);
						
								
				Iterator<OFStatistics> its = ofsr.getStatistics().iterator();							
				while(its.hasNext()){
					OFFlowStatisticsReply ofs = (OFFlowStatisticsReply) its.next();
					OFMatch ofm = ofs.getMatch();					
					List<OFAction> actions = ofs.getActions();
					boolean isDefault = false;
					if(default_rule_match_string.contains(ofm.toString())){
						isDefault = true;
					}else{
						Log.d(TAG, "this match is not default=> " + ofm.toString());
						//the flow rules added by Packet-In
						String src_ip = IPv4.fromIPv4Address(ofm.getNetworkSource());
						String dst_ip = IPv4.fromIPv4Address(ofm.getNetworkDestination());
						
						if(!src_ip.equals("0.0.0.0") && !isMyIP(src_ip)){
							if(!ip_notifyList.contains(src_ip)){
								ip_notifyList.add(src_ip);
							}						
							//From another host -> me, no need to change flow table
							
						}else if(!dst_ip.equals("0.0.0.0") && !isMyIP(dst_ip)){
							if(!ip_notifyList.contains(dst_ip)){
								ip_notifyList.add(dst_ip);
							}
							/*From me -> another host, need to change the action! */
							
							/*if it's single interfaces, then we will need to change ofm source to the next interface ip*/
							if(ip_prev.equals(EnvInitService.vIFs.getVeth1().getIP())){
								ofm.setNetworkSource(IPv4.toIPv4Address(ip_new));
							}
																					
							OFFlowMod offm = new OFFlowMod();
							offm.setActions(new_actions);							 
							offm.setMatch(ofm);
							offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
							offm.setBufferId(-1);
							offm.setCommand(OFFlowMod.OFPFC_MODIFY);
							offm.setIdleTimeout((short) 5);
							offm.setHardTimeout((short) 0);
							offm.setPriority((short) 32768);
							offm.setFlags((short) 1); // Send flow removed							
							offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
									+OFActionDataLayerSource.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH));
							ByteBuffer bb_ofm = ByteBuffer.allocate(offm.getLength());					
							offm.writeTo(bb_ofm);
							
							Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
							poutIntent.setPackage(context.getPackageName());
							poutIntent.putExtra(HolyCIntent.BroadcastOFReply.data_key, bb_ofm.array());
							poutIntent.putExtra(HolyCIntent.BroadcastOFReply.port_key, ofport);
							context.sendBroadcast(poutIntent);
							Log.d(TAG, "send out OF flow mod = " + offm.toString());

						}																														
					}
					
					//Log.d(TAG, "Default:"+ isDefault +" ofmatch = " + ofm.toString());
				}//end of interate all the rules
				//TODO: Send UDP Notification Message
				Iterator<String> itn = ip_notifyList.iterator();
				while(itn.hasNext()){
					String notifiee_ip = itn.next();
					String wifi_ip = "171.64.66.58";
					if(controlUI.interface_just_disabled.equals("wifi")){
						ip_prev = wifi_ip;
					}
					if(controlUI.interface_just_enabled.equals("wifi")){
						ip_new = wifi_ip;
					}
					sendNotifyPacket(notifiee_ip, ip_prev, ip_new);
				}
			}	
		}		
	}//end onReceive	
	private void sendNotifyPacket(String dst_ip, String ip_prev, String ip_new){
		/*OFPacketOut opo = new OFPacketOut();
		opo.setActions(actions);
		opo.setInPort((short)1); //make it from the host
		opo.setActionsLength(U16.t(OFActionOutput.MINIMUM_LENGTH+OFActionDataLayerSource.MINIMUM_LENGTH + 
				OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH) );
		opo.setBufferId(-1);
		int length = OFPacketOut.MINIMUM_LENGTH + opo.getActionsLength();*/
		
		/*Generate Notification Message*/
		String msg = "{\"ip_prev\":[\""+ ip_prev + "\"], \"ip_new\":[\""+ip_new+"\"]}";
		Log.d(TAG, "Send out udp msg = " + msg);
		/*Generate UDP Packet*/
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), InetAddress.getByAddress(IPv4.toIPv4AddressBytes(dst_ip)), 2605);
			socket.send(packet);			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(socket!=null){
			socket.close();
		}
		/*opo.setPacketData(opie.getPacketData());
		length += opie.getPacketData().length;						
		//opo.setLengthU(length);
		opo.setLength(U16.t(length));
		//Log.d(TAG, "Packetout = " + opo.toString());
		ByteBuffer bb_opo = ByteBuffer.allocate(opo.getLength());
		bb_opo.clear();
		opo.writeTo(bb_opo);*/
		
		return;
	}
	private List<OFAction> genOFNewAction(String interface_just_enabled){
		/*Construct new action*/
		OFActionDataLayerSource mod_dl_src = new OFActionDataLayerSource();
		mod_dl_src.setDataLayerAddress(Ethernet.toMACAddress(findMACfromInterface(interface_just_enabled)));			
		//Log.d(TAG, "mod_dl_src = " + mod_dl_src.getDataLayerAddress() + " | " + interface_mac);
		
		OFActionDataLayerDestination mod_dl_dst = new OFActionDataLayerDestination();
		mod_dl_dst.setDataLayerAddress(Ethernet.toMACAddress(findGWfromInterface(interface_just_enabled)));			
		//Log.d(TAG, "mod_dl_dst = " + mod_dl_dst.toString() + " | " + gw_mac);
		
		OFActionNetworkLayerSource mod_nw_src = new OFActionNetworkLayerSource();
		mod_nw_src.setNetworkAddress(IPv4.toIPv4Address(findIPfromInterface(interface_just_enabled)));
		//Log.d(TAG, "mod_nw_src = " + mod_dl_src.toString() + " | " + interface_ip );			
		
		OFActionOutput oao = new OFActionOutput();
		oao.setMaxLength((short) 0);
		oao.setPort(findOVSPortfromInterface(interface_just_enabled));
					
		List<OFAction> new_actions = new ArrayList<OFAction>();
		new_actions.add(mod_dl_src);
		new_actions.add(mod_dl_dst);
		new_actions.add(mod_nw_src);
		new_actions.add(oao);
		return new_actions;
	}
	
	
	private void genDefaultOFMatchString(){
		default_rule_match_string = new LinkedList<String>();
		Iterator<OFMatch> itm = OFCommService.defaultRules.iterator();
		while(itm.hasNext()){
			OFMatch ofm = itm.next();
			default_rule_match_string.add(ofm.toString());
		}
	}
	private String findIPfromInterface(String network_interface ){
		String ip = "0.0.0.0";
		if(network_interface.equals("wifi")){
			ip = EnvInitService.wifiIF.getIP();								
		}else if(network_interface.equals("wimax")){
			ip = EnvInitService.wimaxIF.getIP();			
		}else if(network_interface.equals("3g")){
			if(EnvInitService.threeGIF.isPointToPoint()){
				ip = EnvInitService.threeGIF.veth2.getIP();
			}else{
				ip = EnvInitService.threeGIF.getIP();
			}			
		}
		return ip;
	}
	
	private String findMACfromInterface(String network_interface){
		String mac = "";
		if(network_interface.equals("wifi")){
			mac = EnvInitService.wifiIF.getMac();								
		}else if(network_interface.equals("wimax")){
			mac = EnvInitService.wimaxIF.getMac();			
		}else if(network_interface.equals("3g")){
			if(EnvInitService.threeGIF.isPointToPoint()){
				mac = EnvInitService.threeGIF.veth2.getMac();
			}else{
				mac = EnvInitService.threeGIF.getMac();
			}			
		}
		return mac;
	}
	
	private String findGWfromInterface(String network_interface){
		String gw = "";
		if(network_interface.equals("wifi")){
			gw = EnvInitService.wifiIF.getGateway().getMac();								
		}else if(network_interface.equals("wimax")){
			gw = EnvInitService.wimaxIF.getGateway().getMac();			
		}else if(network_interface.equals("3g")){
			if(EnvInitService.threeGIF.isPointToPoint()){
				gw = EnvInitService.threeGIF.veth2.getGateway().getMac();
			}else{
				gw = EnvInitService.threeGIF.getGateway().getMac();
			}			
		}
		return gw;
	}
	private short findOVSPortfromInterface(String network_interface){
		//TODO: change to check with OVS, should not hard-coded!
		short port = 2;
		if(network_interface.equals("wifi")){
			port = 2;								
		}else if(network_interface.equals("wimax")){
			port = 4;			
		}else if(network_interface.equals("3g")){
			port = 3;			
		}
		return port;
	}
	
	private boolean isMyIP(String ip){
		boolean isMine = false;
		if(ip.equals(EnvInitService.wifiIF.getIP())){
			isMine = true;
		}else if(ip.equals(EnvInitService.wimaxIF.getIP())){
			isMine = true;
		}else if(ip.equals(EnvInitService.threeGIF.getIP())){
			isMine = true;
		}else if(ip.equals(EnvInitService.threeGIF.veth2.getIP())){
			isMine = true;
		}else if(ip.equals(EnvInitService.vIFs.getVeth1().getIP())){
			isMine = true;
		}
		return isMine;
	}
}


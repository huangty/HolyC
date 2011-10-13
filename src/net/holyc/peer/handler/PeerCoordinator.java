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
import net.beaconcontroller.packet.UDP;
import net.beaconcontroller.packet.IPv4;
import net.beaconcontroller.packet.Data;

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
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
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
	private static String wifi_ip = "172.27.75.83";
	private static String middlebox_ip = "171.67.74.239";
	private static String wimax_ip = "66.87.119.85";

	@Override
	public void onReceive(Context context, Intent intent) {		
		//OFPacketOut udp_notify_msg = new OFPacketOut();
		
		byte[] ofdata = null;
		int ofport = 0;
		List<String> ip_prevList = new LinkedList<String>();
		List<String> ip_newList = new LinkedList<String>();
		List<String> ip_notifyList = new LinkedList<String>();
		String action_string = "";
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
				
				if(controlUI.interface_just_disabled.equals("")){
					Log.d(TAG, "WARNING!!! Cannot determine ips");
					return;
				}
				String ip_prev = findIPfromInterface(controlUI.interface_just_disabled);				
				ip_prevList.add(ip_prev);
				String ip_new = "";
				List<OFAction> new_actions = null;
				ByteBuffer bb = null;
				int bb_length = 0;
				
				if(!controlUI.interface_just_enabled.equals("")){
					ip_new = findIPfromInterface(controlUI.interface_just_enabled);
					ip_newList.add(ip_new);
				}
				
				if(!controlUI.interface_just_enabled.equals("") && !controlUI.checkbox_fwdMB.isChecked() && !controlUI.checkbox_notifyMB.isChecked()){ 
					//if an new interface is just enabled, and no need to forward to middle box
					action_string = "fwdTraffic_server";					
					new_actions = genOFNewAction(controlUI.interface_just_enabled);
				}else if (!controlUI.interface_just_enabled.equals("") && !controlUI.checkbox_fwdMB.isChecked() && controlUI.checkbox_notifyMB.isChecked()){
					//need to notify middle box
					action_string = "notify_middlebox_server";
				}else if (!controlUI.interface_just_enabled.equals("") && controlUI.checkbox_fwdMB.isChecked()){
					//need to forward to a middle box
					action_string = "fwdTraffic_middlebox";
					new_actions = genOFNewAction(controlUI.interface_just_enabled);
				}else if(controlUI.interface_just_enabled.equals("") && !controlUI.interface_just_disabled.equals("")){
					//the disable is a trigger to send UDP traffic
					action_string = "notify_server";					
				}else{
					action_string = "NOT SUPPORT ACTION, PLEASE CHECK!!!";
				}
				Log.d(TAG, "action = " + action_string);
				
				Iterator<OFStatistics> its = ofsr.getStatistics().iterator();							
				while(its.hasNext()){
					OFFlowStatisticsReply ofs = (OFFlowStatisticsReply) its.next();
					OFMatch ofm = ofs.getMatch();
					List<OFAction> actions = ofs.getActions();

					if(!default_rule_match_string.contains(ofm.toString())){					
						Log.d(TAG, "this match is not default=> " + ofm.toString());
						//the flow rules added by Packet-In
						String src_ip = IPv4.fromIPv4Address(ofm.getNetworkSource());
						String dst_ip = IPv4.fromIPv4Address(ofm.getNetworkDestination());
						
						if(!src_ip.equals("0.0.0.0") && !isMyIP(src_ip)){
							if(!ip_notifyList.contains(src_ip)){
								ip_notifyList.add(src_ip);
							}
							//for each flow I have to make some changes
							if(action_string.equals("fwdTraffic_server") || action_string.equals("fwdTraffic_middlebox") ){
								//From another host -> me, need to update the rule
								OFFlowMod offm = new OFFlowMod();
								
								if(action_string == "fwdTraffic_server"){
									//the traffic will now come from a new port
									ofm.setInputPort(findOVSPortfromInterface(controlUI.interface_just_enabled));									
									
									offm.setActions(actions);							 
									offm.setMatch(ofm);
									offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
									offm.setBufferId(-1);
									offm.setCommand(OFFlowMod.OFPFC_ADD);
									offm.setIdleTimeout((short) 5);
									offm.setHardTimeout((short) 0);
									offm.setPriority((short) 32768);
									offm.setFlags((short) 1); // Send flow removed	
									
									offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
											+ OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH));
								}else if(action_string == "fwdTraffic_middlebox"){
									
									int serverip = ofm.getNetworkSource();
									ofm.setNetworkSource(IPv4.toIPv4Address(middlebox_ip));
									//ofm.setWildcards(ofm.getWildcards())
								
									OFActionNetworkLayerSource mod_nw_src = new OFActionNetworkLayerSource();
									mod_nw_src.setNetworkAddress(serverip);
																			
									List<OFAction> middlebox_actions = new LinkedList(); 
																				
									middlebox_actions.add(mod_nw_src);
									
									Iterator<OFAction> it = actions.iterator();
									while(it.hasNext()){
										middlebox_actions.add(it.next());
									}
									
									offm.setActions(middlebox_actions);							 
									offm.setMatch(ofm);
									offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
									offm.setBufferId(-1);
									offm.setCommand(OFFlowMod.OFPFC_ADD);
									offm.setIdleTimeout((short) 5);
									offm.setHardTimeout((short) 0);
									offm.setPriority((short) 32768);
									offm.setFlags((short) 1); // Send flow removed	
									
									offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
											+ OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH + OFActionNetworkLayerDestination.MINIMUM_LENGTH));
								}
								ByteBuffer bb_ofm = ByteBuffer.allocate(offm.getLength());					
								offm.writeTo(bb_ofm);
								
								ByteBuffer bb_tmp = ByteBuffer.allocate(bb_length + offm.getLength());
								bb_tmp.clear();
								if(bb != null){
									bb_tmp.put(bb.array());									
								}
								bb_tmp.put(bb_ofm.array());
								bb_tmp.flip();
								bb = bb_tmp;
								bb_length = bb_length + offm.getLength();
							}
														
						}else if(!dst_ip.equals("0.0.0.0") && !isMyIP(dst_ip)){
							if(!ip_notifyList.contains(dst_ip)){
								ip_notifyList.add(dst_ip);
							}

							if(action_string.equals("fwdTraffic_server") || action_string.equals("fwdTraffic_middlebox") ){
								/*From me -> another host, need to change the action! */														
								/*if it's single interfaces, then we will need to change ofm source to the next interface ip*/
								OFFlowMod offm = new OFFlowMod();
								ByteBuffer bb_ofm = null;
								ByteBuffer bb_tmp = null;
								if(action_string.equals("fwdTraffic_server")){
									if(ip_prev.equals(EnvInitService.vIFs.getVeth1().getIP())){									
										ofm.setNetworkSource(IPv4.toIPv4Address(ip_new));
									}
																																
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
									bb_ofm = ByteBuffer.allocate(offm.getLength());					
									offm.writeTo(bb_ofm);
									bb_tmp = ByteBuffer.allocate(bb_length + offm.getLength());
									bb_length = bb_length + offm.getLength();
								}else if(action_string.equals("fwdTraffic_middlebox")){
									List<OFAction> middlebox_actions = new LinkedList(); 
										
									
									OFActionNetworkLayerDestination mod_nw_dst = new OFActionNetworkLayerDestination();
									mod_nw_dst.setNetworkAddress(IPv4.toIPv4Address(middlebox_ip));
									
									middlebox_actions.add(mod_nw_dst);
									
									Iterator<OFAction> it = new_actions.iterator();
									while(it.hasNext()){
										middlebox_actions.add(it.next());
									}
									
									offm.setActions(middlebox_actions);							 
									offm.setMatch(ofm);
									offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
									offm.setBufferId(-1);
									offm.setCommand(OFFlowMod.OFPFC_ADD);
									offm.setIdleTimeout((short) 5);
									offm.setHardTimeout((short) 0);
									offm.setPriority((short) 32768);
									offm.setFlags((short) 1); // Send flow removed							
									offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
											+OFActionDataLayerSource.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH
											+OFActionNetworkLayerSource.MINIMUM_LENGTH + OFActionNetworkLayerDestination.MINIMUM_LENGTH));
									
									OFFlowMod offm_delete = new OFFlowMod();																						 
									offm_delete.setMatch(ofm);		
									offm_delete.setActions(null);
									offm_delete.setOutPort((short) OFPort.OFPP_NONE.getValue());
									offm_delete.setBufferId(-1);
									offm_delete.setCommand(OFFlowMod.OFPFC_DELETE);
									offm_delete.setIdleTimeout((short) 5);
									offm_delete.setHardTimeout((short) 0);
									offm_delete.setPriority((short) 32768);
									offm_delete.setFlags((short) 1); // Send flow removed							
									offm_delete.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
									
									
									//bb_ofm = ByteBuffer.allocate(offm_delete.getLength());
									
									ByteBuffer bb_ofm_add = ByteBuffer.allocate(offm.getLength());
									offm.writeTo(bb_ofm_add);									
									ByteBuffer bb_ofm_delete = ByteBuffer.allocate(offm_delete.getLength());
									offm_delete.writeTo(bb_ofm_delete);
									
									bb_ofm = ByteBuffer.allocate(offm.getLength() + offm_delete.getLength());
									bb_tmp = ByteBuffer.allocate(bb_length + offm.getLength() + offm_delete.getLength());
									bb_length = bb_length + offm.getLength() + offm_delete.getLength();
									
									bb_ofm.clear();
									bb_ofm.put(bb_ofm_delete.array());
									bb_ofm.put(bb_ofm_add.array());
									bb_ofm.flip();
									
									
								}
								
								//Log.d(TAG, "send out OF flow mod = " + offm.toString());																
								
								
								//bb_tmp = ByteBuffer.allocate(bb_length + offm.getLength());
								bb_tmp.clear();
								if(bb != null){
									bb_tmp.put(bb.array());									
								}
								if(bb_ofm != null){
									bb_tmp.put(bb_ofm.array());
								}
								bb_tmp.flip();
								bb = bb_tmp;								
							}//if qualified action
						}																														
					}
					
					//Log.d(TAG, "Default:"+ isDefault +" ofmatch = " + ofm.toString());
				}//end of interate all the rules
				
				//TODO: Send UDP Notification Message to Server
				if(action_string.equals("notify_server")){
					Iterator<String> itn = ip_notifyList.iterator();
					while(itn.hasNext()){
						String notifiee_ip = itn.next();
						if(controlUI.interface_just_disabled.equals("wifi")){
							ip_prev = wifi_ip; //need to hardcode the wifi, since it's behind the NAT
							ip_newList.add(findIPfromInterface("wimax"));						
						}else if(controlUI.interface_just_disabled.equals("wimax")){
							//no need to change ip_prev
							ip_prev = wimax_ip;
							ip_newList.add(wifi_ip);
						}											
						sendNotifyPacket(notifiee_ip, ip_prev, ip_newList);
					}
					Log.d(TAG, "Sending out Notification to Servers");
				}else if(action_string.equals("notify_middlebox_server")){
					Iterator<String> itn = ip_notifyList.iterator();
					String mobile_ip = ip_new;
					String server_ip;
					List<String> middlebox_ipList = new LinkedList();					
					middlebox_ipList.add(middlebox_ip);
					
					String first_ip = ip_prev;
					while(itn.hasNext()){
						server_ip = itn.next();
						if(controlUI.interface_just_enabled.equals("wifi")){
							mobile_ip = wifi_ip; //need to hardcode the wifi, since it's behind the NAT
													
						}else if(controlUI.interface_just_enabled.equals("wimax")){
							mobile_ip = wimax_ip;//ip_new;
						}	
						
						if(controlUI.interface_just_disabled.equals("wifi")){
							first_ip = wifi_ip; //need to hardcode the wifi, since it's behind the NAT												
						}else if(controlUI.interface_just_disabled.equals("wimax")){							
							//first_ip = findIPfromInterface(controlUI.interface_just_disabled);
							first_ip = wimax_ip;
						}
						//send message to Server telling him that instead of sending packets to me, send to middlebox 
						sendNotifyPacket(server_ip, first_ip, middlebox_ipList);
						
						//send message to MiddleBox telling him that forward traffic from the server to me, and reverse versa. 
						sendMiddleBoxNotifyPacket(middlebox_ip, mobile_ip, server_ip);
					}
				}
				
				if(bb!=null){
					Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
					poutIntent.setPackage(context.getPackageName());
					poutIntent.putExtra(HolyCIntent.BroadcastOFReply.data_key, bb.array());
					poutIntent.putExtra(HolyCIntent.BroadcastOFReply.port_key, ofport);
					context.sendBroadcast(poutIntent);
				}
				
			}	
		}		
	}//end onReceive	
	
	private void sendMiddleBoxNotifyPacket(String middlebox_ip, String mobile_ip, String server_ip){
		String msg = "{\"server_ip\":[\""+ server_ip + "\"], \"mobile_ip\":[\"" + mobile_ip + "\"]}" ;		
		Log.d(TAG, "Send middlebox udp msg = " + msg);
	
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), InetAddress.getByAddress(IPv4.toIPv4AddressBytes(middlebox_ip)), 2605);			
			socket.send(packet);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(socket!=null){
			socket.close();
		}
		
		return;
	}
	
	private void sendNotifyPacket(String dst_ip, String ip_prev, List<String> ip_newList){
		String msg = "{\"ip_prev\":[\""+ ip_prev + "\"], \"ip_new\":[";
		int list_count = 0;
		Iterator<String> its = ip_newList.iterator();
		while(its.hasNext()){
			String ip_new = its.next();
			if(list_count == 0){
				msg = msg + "\""+ip_new+"\"";
			}else{
				msg = msg + ",\""+ip_new+"\"";
			}
			list_count++;
		}
		msg = msg + "]}";
		Log.d(TAG, "Send out udp msg = " + msg);
		/*OFPacketOut opo = new OFPacketOut();
		opo.setActions(actions);
		opo.setInPort((short)1); //make it from the host
		opo.setActionsLength(U16.t(OFActionOutput.MINIMUM_LENGTH+OFActionDataLayerSource.MINIMUM_LENGTH + 
				OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH) );
		opo.setBufferId(-1);
		int length = OFPacketOut.MINIMUM_LENGTH + opo.getActionsLength();
		
		//Generate Notification Message
		
		UDP udp_packet = new UDP();
		udp_packet.setDestinationPort((short) 2605);
		udp_packet.setSourcePort((short) 12345);
		udp_packet.setChecksum((short) 0);
		Data payload = new Data();
		payload.setData(msg.getBytes());
		udp_packet.setPayload(payload);
		IPv4 ip_packet = new IPv4();
		ip_packet.setDestinationAddress(dst_ip);
		ip_packet.setSourceAddress(EnvInitService.vIFs.getVeth1().getIP());
		ip_packet.setChecksum(checksum);
		ip_packet.setPayload(udp_packet);*/
		
		
		/*opo.setPacketData(opie.getPacketData());
		length += opie.getPacketData().length;						
		//opo.setLengthU(length);
		opo.setLength(U16.t(length));
		//Log.d(TAG, "Packetout = " + opo.toString());
		ByteBuffer bb_opo = ByteBuffer.allocate(opo.getLength());
		bb_opo.clear();
		opo.writeTo(bb_opo);*/		
		
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


package net.holyc.openflow.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;

import android.content.Intent;
import android.util.Log;

import net.beaconcontroller.packet.ARP;
import net.beaconcontroller.packet.Ethernet;
import net.beaconcontroller.packet.IPv4;
import net.holyc.HolyCIntent;
import net.holyc.controlUI;
import net.holyc.host.EnvInitService;

public class OFMultipleInterfaceRoundRobin extends FlowSwitch {
	private String TAG = "HOLYC.OFMultipleInterfacePolicy";
	private static short round_robin_out_port = 2;
	public static int wifi_flow_count = 0;
	public static int wimax_flow_count = 0;
	public static int threeg_flow_count = 0;
	private static Random rnd_generator = new Random();
	
	
	
	public ByteBuffer getResponse(Short out, OFPacketIn opie, OFMatch ofm, long cookie) {
				
		ByteBuffer bb = null;
		
		/*ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
				& ~(63 << OFMatch.OFPFW_NW_DST_SHIFT) & ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT)
				& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_DST & ~OFMatch.OFPFW_DL_SRC);*/
		//ofm.setWildcards(ofm.getWildcards() | OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC);
				
		if (out != null) {
			// Form flow_mod
			OFActionOutput oao = new OFActionOutput();
			oao.setMaxLength((short) 0);
			oao.setPort(out.shortValue());
			OFFlowMod offm = new OFFlowMod();
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(oao);
			offm.setActions(actions);
			offm.setMatch(ofm);
			offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
			offm.setBufferId(opie.getBufferId());
			offm.setCommand(OFFlowMod.OFPFC_ADD);
			offm.setIdleTimeout((short) 5);
			offm.setHardTimeout((short) 0);
			offm.setPriority((short) 32768);
			offm.setFlags((short) 1); // Send flow removed
			offm.setCookie(cookie);
			offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH
					+ OFActionOutput.MINIMUM_LENGTH));
			bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(bb);
			// Log.d(TAG, "Buffer ID: " + opie.getBufferId());
			if (opie.getBufferId() == -1) {
				// if the switch doesn't buffer the packet
				OFPacketOut opo = new OFPacketOut();
				opo.setActions(actions);
				opo.setInPort(opie.getInPort());
				opo.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
				opo.setBufferId(opie.getBufferId());
				opo.setPacketData(opie.getPacketData());
				int length = OFPacketOut.MINIMUM_LENGTH
						+ opo.getActionsLengthU() + opie.getPacketData().length;
				// opo.setLengthU(length);
				// ByteBuffer bb_ofout = ByteBuffer.allocate(opo.getLengthU());
				opo.setLength(U16.t(length));
				ByteBuffer bb_ofout = ByteBuffer.allocate(opo.getLengthU());
				bb_ofout.clear();
				opo.writeTo(bb_ofout);

				ByteBuffer bb_out = ByteBuffer.allocate(opo.getLengthU()
						+ offm.getLength());
				bb_out.clear();
				bb_out.put(bb.array());
				bb_out.put(bb_ofout.array());
				bb_out.flip();
				bb = bb_out;
			}
		} else {
			if(ofm.getInputPort() != (short) 1){ //if coming from interfaces, then has to go to the host
				round_robin_out_port = 1;
				return null; //by pass this situation for now, too many noises in wifi
			}
			//for packets don't know the destination, round-robin to select destination		
			//selection WIFI:WIMAX = 1:1
			/*if( ofm.getTransportDestination() == (short) 80
					&& EnvInitService.wifiIF!=null && EnvInitService.wimaxIF!=null && EnvInitService.mobile_included == false){ //http/DNS request
				if(EnvInitService.wifi_included && (wifi_flow_count == 0 || (wimax_flow_count != 0 && wifi_flow_count <= wimax_flow_count * 1))){				
					Log.d(TAG, "allocate one HTTP flow to wifi!");
					if(EnvInitService.wifi_included && EnvInitService.wimax_included){
						wifi_flow_count += 1;
					}
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}else if(EnvInitService.wimax_included){
					Log.d(TAG, "allocate one HTTP flow to wimax!");
					if(EnvInitService.wifi_included && EnvInitService.wimax_included){
						wimax_flow_count +=1;
					}
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName());
				}else{
					Log.d(TAG, "default: allocate one HTTP flow to wifi!");
					if(EnvInitService.wifi_included && EnvInitService.wimax_included){
						wifi_flow_count += 1;
					}
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}
				if(EnvInitService.wifi_included && EnvInitService.wimax_included){
					Log.d(TAG, "flow count: wifi = " + wifi_flow_count + " wimax = " + wimax_flow_count);
				}
			}else*/ 
			if( (ofm.getTransportDestination() == (short) 80 || ofm.getNetworkProtocol() == 0x11)  //http or udp
					&& EnvInitService.wifiIF!=null && EnvInitService.wimaxIF!=null){ //http/DNS request
				//WIFI:WIMAX:3G = 1:1:1
				if(EnvInitService.wifi_included && (wifi_flow_count == 0 || 
						((!EnvInitService.wimax_included || wifi_flow_count <= wimax_flow_count * 1) && 
						 (!EnvInitService.mobile_included || wifi_flow_count <= threeg_flow_count *1)) )){				
					Log.d(TAG, "allocate one HTTP flow to wifi!");
					wifi_flow_count += 1;					
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}else if(EnvInitService.wimax_included && (wimax_flow_count ==0 || 
						((!EnvInitService.wifi_included || wimax_flow_count <= wifi_flow_count * 1 ) && 
						 (!EnvInitService.mobile_included || wimax_flow_count <= threeg_flow_count * 1)) )){
					Log.d(TAG, "allocate one HTTP flow to wimax!");					
					wimax_flow_count +=1;					
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName());
				}else if(EnvInitService.mobile_included && (threeg_flow_count ==0 || 
						((!EnvInitService.wifi_included || threeg_flow_count <= wifi_flow_count * 1 ) && 
						 (!EnvInitService.wimax_included || threeg_flow_count <= wimax_flow_count * 1)) )){
					Log.d(TAG, "allocate one HTTP flow to 3g!");
					threeg_flow_count += 1;					
					if(EnvInitService.threeGIF.isPointToPoint()){
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.veth2.getName());
					}else{
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName());
					}
				}else{
					if(controlUI.interface_just_disabled.equals("wifi")){
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
					}else if(controlUI.interface_just_disabled.equals("wimax")){
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName());
					}else{
						Log.d(TAG, "default: allocate one HTTP flow to wifi!");									
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
					}
				}				
				Log.d(TAG, "flow count: wifi = " + wifi_flow_count + " wimax = " + wimax_flow_count + " 3g = " + threeg_flow_count);				
			}else{
				//WIFI only or WIMAX only 
				if(EnvInitService.wifiIF!=null){
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}else if(EnvInitService.wimaxIF!=null){
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName());
				}else{
					//3G only 
					if(EnvInitService.threeGIF.isPointToPoint()){
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.veth2.getName());
					}else{
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName());
					}
					
				}				
			}
			/*{						
				//round robin wifi<->wimax<->3g
				if(EnvInitService.wifi_included && round_robin_out_port == (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName())){
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName());
				}else if(EnvInitService.wimax_included && round_robin_out_port == (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName())){				
					if(EnvInitService.threeGIF.isPointToPoint()){
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.veth2.getName());					
					}else{
						round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName());
					}
				}else if(EnvInitService.mobile_included){
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}else{
					round_robin_out_port = (short)EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName());
				}
			}*/
			
			
			if(EnvInitService.ovs == null){
				Log.d(TAG, "the environment is still setting up, sorry, no forward!");
				return null;
			}
			Log.d(TAG, "Doing Round Robin with Output Port = " + round_robin_out_port);
			int num_of_interface = EnvInitService.ovs.dptable.get("dp0").size() - 1; //the virtual interface doesn't count;
			
			// Round-robin-ly send the unknown packets to output port 			
			byte[] interface_mac = null;
			byte[] gw_mac = null;
			int interface_ip = 0;
			if(EnvInitService.wifiIF!= null && round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName())){
				Log.d(TAG, "round_robin to wifi!");				
				interface_mac = Ethernet.toMACAddress(EnvInitService.wifiIF.getMac());				
				interface_ip = IPv4.toIPv4Address(EnvInitService.wifiIF.getIP());
				gw_mac = Ethernet.toMACAddress(EnvInitService.wifiIF.getGateway().getMac());
			}else if(EnvInitService.threeGIF!=null && round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName()) ||
					round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.veth2.getName()) ){
				Log.d(TAG, "round_robin to 3G!");
				if(EnvInitService.threeGIF.isPointToPoint()){
					interface_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.veth2.getMac());
					interface_ip = IPv4.toIPv4Address(EnvInitService.threeGIF.veth2.getIP());
					gw_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.getGateway().getMac());
				}else{
					interface_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.getMac());
					interface_ip = IPv4.toIPv4Address(EnvInitService.threeGIF.getIP());
					gw_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.getGateway().getMac());
				}
			}else if(EnvInitService.wimaxIF!=null && round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName())){
				Log.d(TAG, "round_robin to wimax!");				
				interface_mac = Ethernet.toMACAddress(EnvInitService.wimaxIF.getMac());
				interface_ip = IPv4.toIPv4Address(EnvInitService.wimaxIF.getIP());
				gw_mac = Ethernet.toMACAddress(EnvInitService.wimaxIF.getGateway().getMac());
			}else{
				Log.d(TAG, "WARNING: no ip/mac for the sending interface");
			}
			OFActionDataLayerSource mod_dl_src = new OFActionDataLayerSource();
			mod_dl_src.setDataLayerAddress(interface_mac);			
			//Log.d(TAG, "mod_dl_src = " + mod_dl_src.getDataLayerAddress() + " | " + interface_mac);
			
			OFActionDataLayerDestination mod_dl_dst = new OFActionDataLayerDestination();
			mod_dl_dst.setDataLayerAddress(gw_mac);			
			//Log.d(TAG, "mod_dl_dst = " + mod_dl_dst.toString() + " | " + gw_mac);
			
			OFActionNetworkLayerSource mod_nw_src = new OFActionNetworkLayerSource();
			mod_nw_src.setNetworkAddress(interface_ip);
			//Log.d(TAG, "mod_nw_src = " + mod_dl_src.toString() + " | " + interface_ip );			
			
			OFActionOutput oao = new OFActionOutput();
			oao.setMaxLength((short) 0);
			oao.setPort(round_robin_out_port);
						
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(mod_dl_src);
			actions.add(mod_dl_dst);
			actions.add(mod_nw_src);
			actions.add(oao);						
			
			
			//Also insert Flow-Mod rule like the below
			// /data/local/bin/ovs-ofctl add-flow dp0 in_port=1,idle_timeout=0,hard_timeout=0,actions=mod_dl_src:$MAC_MOBILE,mod_nw_src:$IP_MOBILE,mod_dl_dst:$MAC_MOBILE_GW,output:3
			OFFlowMod offm = new OFFlowMod();
			offm.setActions(actions);
			offm.setMatch(ofm);
			offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
			offm.setBufferId(opie.getBufferId());
			offm.setCommand(OFFlowMod.OFPFC_ADD);
			offm.setIdleTimeout((short) 5);
			offm.setHardTimeout((short) 0);
			offm.setPriority((short) 32768);
			offm.setFlags((short) 1); // Send flow removed
			offm.setCookie(cookie);
			offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
					+OFActionDataLayerSource.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH));
			ByteBuffer bb_ofm = ByteBuffer.allocate(offm.getLength());					
			offm.writeTo(bb_ofm);
			
			ByteBuffer bb_rev_ofm = null;
			OFFlowMod rev_offm = null;
			if(ofm.getInputPort() == (short) 1){
				OFActionDataLayerDestination mod_dl_dst_to_veth = new OFActionDataLayerDestination();
				mod_dl_dst_to_veth.setDataLayerAddress(Ethernet.toMACAddress(EnvInitService.vIFs.getVeth1().getMac()));
				OFActionNetworkLayerDestination mod_nw_dst_to_veth = new OFActionNetworkLayerDestination();
				mod_nw_dst_to_veth.setNetworkAddress(IPv4.toIPv4Address(EnvInitService.vIFs.getVeth1().getIP()));
				OFActionOutput oao_to_veth = new OFActionOutput();
				oao_to_veth.setMaxLength((short) 0);
				oao_to_veth.setPort((short) 1); //to veth
				
				List<OFAction> rev_actions = new ArrayList<OFAction>();			
				rev_actions.add(mod_dl_dst_to_veth);			
				rev_actions.add(mod_nw_dst_to_veth);			
				rev_actions.add(oao_to_veth);
				
				OFMatch rev_ofm = new OFMatch();
				rev_ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_TYPE);
				rev_ofm.setDataLayerType(ofm.getDataLayerType());
				if(ofm.getDataLayerType() == 0x0800 ){ //ip
					//rev_ofm.setNetworkDestination(ofm.getNetworkSource());
					rev_ofm.setNetworkSource(ofm.getNetworkDestination());
					rev_ofm.setWildcards(rev_ofm.getWildcards() &  ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT));
					if (ofm.getNetworkProtocol() == 0x06 || ofm.getNetworkProtocol() == 0x11) { //tcp or udp
						rev_ofm.setNetworkProtocol(ofm.getNetworkProtocol());
						rev_ofm.setTransportDestination(ofm.getTransportSource());
						rev_ofm.setTransportSource(ofm.getTransportDestination());
						rev_ofm.setWildcards(rev_ofm.getWildcards() & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_SRC & ~OFMatch.OFPFW_TP_DST);
					}
				}			
							
				rev_ofm.setInputPort((short) round_robin_out_port);
				
				//reverse route
				rev_offm = new OFFlowMod();
				rev_offm.setActions(rev_actions);
				rev_offm.setMatch(rev_ofm);
				rev_offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
				rev_offm.setBufferId(-1);
				rev_offm.setCommand(OFFlowMod.OFPFC_ADD);
				rev_offm.setIdleTimeout((short) 5);
				rev_offm.setHardTimeout((short) 0);
				rev_offm.setPriority((short) 32768);
				rev_offm.setFlags((short) 1); // Send flow removed
				rev_offm.setCookie(cookie);
				rev_offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH 
						 + OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerDestination.MINIMUM_LENGTH));
				bb_rev_ofm = ByteBuffer.allocate(rev_offm.getLength());					
				rev_offm.writeTo(bb_rev_ofm);						
			}
			
			if(opie.getBufferId() == -1){
				OFPacketOut opo = new OFPacketOut();
				opo.setActions(actions);
				opo.setInPort(opie.getInPort());
				opo.setActionsLength(U16.t(OFActionOutput.MINIMUM_LENGTH+OFActionDataLayerSource.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH +OFActionNetworkLayerSource.MINIMUM_LENGTH) );
				opo.setBufferId(opie.getBufferId());
				int length = OFPacketOut.MINIMUM_LENGTH + opo.getActionsLength();			
				opo.setPacketData(opie.getPacketData());
				length += opie.getPacketData().length;						
				//opo.setLengthU(length);
				opo.setLength(U16.t(length));
				//Log.d(TAG, "Packetout = " + opo.toString());
				ByteBuffer bb_opo = ByteBuffer.allocate(opo.getLength());
				bb_opo.clear();
				opo.writeTo(bb_opo);
				if(rev_offm != null){
					bb = ByteBuffer.allocate(opo.getLengthU() + offm.getLength() + rev_offm.getLength());
				}else{
					bb = ByteBuffer.allocate(opo.getLengthU() + offm.getLength());
				}
				bb.clear();
				bb.put(bb_ofm.array());
				if(rev_offm!=null){
					bb.put(bb_rev_ofm.array());
				}
				bb.put(bb_opo.array());
				bb.flip();
			}else{
				if(rev_offm != null){
					bb = ByteBuffer.allocate(offm.getLengthU() + rev_offm.getLength());
				}else{
					bb = ByteBuffer.allocate(offm.getLengthU());
				}
				bb.clear();
				bb.put(bb_ofm.array());
				if(rev_offm != null){
					bb.put(bb_rev_ofm.array());
				}
				bb.flip();
			}
												
		}
		
		return bb;
	}
	
	public ByteBuffer getARPResponse(Short out, OFPacketIn opie, OFMatch ofm, long cookie) {
		ByteBuffer bb = null;
    	Log.d(TAG, "Receive an ARP");

		byte[] arp_packet = opie.getPacketData();
    	if(arp_packet[21] != 0x01){ //not arp request
    		return getResponse(out, opie, ofm, cookie);
    	}
    	Log.d(TAG, "This is an ARP Request ");
		//Handle arp request explicitly
		//arp from the virtual interface
    	byte[] packet_serialized = null;
	    if (ofm.getInputPort()== EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.vIFs.getVeth0().getName())){	    		    		    	
	    	Log.d(TAG, "Receive an ARP Request from virtual interface: " + ofm.toString());
	    	Log.d(TAG, "Forge an ARP Reply now ... ");

        	byte[] mac_veth = ofm.getDataLayerSource();
        	HexString.toHexString(mac_veth);
        	Log.d(TAG, "veth's MAC = " + HexString.toHexString(mac_veth));
        	int ip_veth = ofm.getNetworkSource();
        	Log.d(TAG, "veth's IP = " + IPv4.fromIPv4Address(ip_veth));
        	//int ip_ans = IPv4.toIPv4Address(EnvInitService.vethGW.getIP());
        	int ip_ans = ofm.getNetworkDestination();
        	Log.d(TAG, "Ask for IP = " + IPv4.fromIPv4Address(ip_ans));
        	byte[] mac_ans = Ethernet.toMACAddress("00:11:22:33:44:55");
        	Log.d(TAG, "synthesized MAC = " + HexString.toHexString(mac_ans));
        	
        	packet_serialized = genARPReply(mac_ans, ip_ans, mac_veth, ip_veth);
        	OFPacketOut ofp_out = new OFPacketOut()
				.setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(ofm.getInputPort())}))
				.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
				.setBufferId(-1)
				.setInPort(OFPort.OFPP_NONE)	                			
				.setPacketData(packet_serialized);
        	int length = OFPacketOut.MINIMUM_LENGTH+ ofp_out.getActionsLengthU() + ofp_out.getPacketData().length;
        	Log.d(TAG, "arp packet length = " + length);
        	//ofp_out.setLengthU(OFPacketOut.MINIMUM_LENGTH + ofp_out.getActionsLengthU() + packet_serialized.length);
        	ofp_out.setLength(U16.t(length));
        	bb = ByteBuffer.allocate(ofp_out.getLength());
        	ofp_out.writeTo(bb);
	    }else if (ofm.getInputPort()== EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName())){
	    	//not sure if we need to handle the arp request comes from wifi 
	    	Log.d(TAG, "got arp request from wifi interface");
	    	Log.d(TAG, "reply arp request for wifi");
	    	byte[] mac_wifi = Ethernet.toMACAddress(EnvInitService.wifiIF.getMac());
	    	int ip_wifi = ofm.getNetworkDestination();
	    	byte[] mac_dst = ofm.getDataLayerSource();
	    	int ip_dst = ofm.getNetworkSource();
	    	
	    	packet_serialized = genARPReply(mac_wifi, ip_wifi, mac_dst, ip_dst);
	    	OFPacketOut ofp_out = new OFPacketOut()
			.setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(ofm.getInputPort())}))
			.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
			.setBufferId(-1)
			.setInPort(OFPort.OFPP_NONE)	                			
			.setPacketData(packet_serialized);
	    	int length = OFPacketOut.MINIMUM_LENGTH+ ofp_out.getActionsLengthU() + ofp_out.getPacketData().length;
	    	ofp_out.setLength(U16.t(length));
        	bb = ByteBuffer.allocate(ofp_out.getLength());
        	ofp_out.writeTo(bb);
        	
	    }else if (ofm.getInputPort()== EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName())){
	    	Log.d(TAG, "got arp request from 3G interface");
	    }else if (ofm.getInputPort()== EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName())){
	    	Log.d(TAG, "got arp request from wimax interface");
	    }
	    
	    if(bb == null){
	    	Log.d(TAG, "WARNING: no action is generated");
	    }
	    return bb;	    
	}
	public byte[] genARPReply(byte[] mac_ans, int ip_ans, byte[] mac_dst, int ip_dst){
			ARP arpReply = new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setOpCode(ARP.OP_REPLY)						
				.setProtocolType(ARP.PROTO_TYPE_IP)								
				.setHardwareAddressLength((byte)0x06)
				.setProtocolAddressLength((byte)0x04)
				.setSenderHardwareAddress(mac_ans)
				.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(IPv4.fromIPv4Address(ip_ans)))
				.setTargetHardwareAddress(mac_dst)
				.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(IPv4.fromIPv4Address(ip_dst)));
			Log.d(TAG, "generated ARP reply : " + arpReply.toString());	
			Ethernet out_packet = new Ethernet()
						.setDestinationMACAddress(mac_dst)
						.setSourceMACAddress(mac_ans)
						.setEtherType(Ethernet.TYPE_ARP);
			out_packet.setPayload(arpReply);
			return out_packet.serialize();
	}
}

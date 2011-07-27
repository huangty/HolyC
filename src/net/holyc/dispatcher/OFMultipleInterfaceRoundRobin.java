package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import net.holyc.host.EnvInitService;
import net.holyc.openflow.handler.FlowSwitch;

public class OFMultipleInterfaceRoundRobin extends FlowSwitch {
	private String TAG = "HOLYC.OFMultipleInterfacePolicy";
	private static short round_robin_out_port = 2; //starts from wifi
	
	public ByteBuffer getResponse(Short out, OFPacketIn opie, OFMatch ofm, long cookie) {
		
		ByteBuffer bb = null;
				
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
			//for packets don't know the destination, round-robin to select destination
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
			if(round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName())){
				Log.d(TAG, "round_robin to wifi!");				
				interface_mac = Ethernet.toMACAddress(EnvInitService.wifiIF.getMac());				
				interface_ip = IPv4.toIPv4Address(EnvInitService.wifiIF.getIP());
				gw_mac = Ethernet.toMACAddress(EnvInitService.wifiIF.getGateway().getMac());
			}else if(round_robin_out_port == (short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName())){
				Log.d(TAG, "round_robin to mobile!");
				interface_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.getMac());
				interface_ip = IPv4.toIPv4Address(EnvInitService.threeGIF.getIP());
				gw_mac = Ethernet.toMACAddress(EnvInitService.threeGIF.getGateway().getMac());
			}else{
				Log.d(TAG, "WARNING: no ip/mac for the sending interface");
			}
			OFActionDataLayerSource mod_dl_src = new OFActionDataLayerSource();
			mod_dl_src.setDataLayerAddress(interface_mac);			
			Log.d(TAG, "mod_dl_src = " + mod_dl_src.toString() + " | " + interface_mac);
			
			OFActionDataLayerDestination mod_dl_dst = new OFActionDataLayerDestination();
			mod_dl_dst.setDataLayerAddress(gw_mac);			
			Log.d(TAG, "mod_dl_dst = " + mod_dl_dst.toString() + " | " + gw_mac);
			
			OFActionNetworkLayerSource mod_nw_src = new OFActionNetworkLayerSource();
			mod_nw_src.setNetworkAddress(interface_ip);
			Log.d(TAG, "mod_nw_src = " + mod_dl_src.toString() + " | " + interface_ip);			
			
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
			
			bb = bb_ofm;
			
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
				Log.d(TAG, "Packetout = " + opo.toString());
				ByteBuffer bb_opo = ByteBuffer.allocate(opo.getLength());
				bb_opo.clear();
				opo.writeTo(bb_opo);
				bb = ByteBuffer.allocate(opo.getLengthU() + offm.getLength());			
				bb.clear();
				bb.put(bb_ofm.array());
				bb.put(bb_opo.array());
				bb.flip();
			}
						
			
			if(round_robin_out_port == 2){
				round_robin_out_port = 3;
			}else{
				round_robin_out_port = 2;
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
	    }else if (ofm.getInputPort()== EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName())){
	    	Log.d(TAG, "got arp request from 3G interface");
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

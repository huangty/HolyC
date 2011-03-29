package edu.stanford.holyc.ofcomm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.beaconcontroller.packet.ARP;
import net.beaconcontroller.packet.Ethernet;
import net.beaconcontroller.packet.IPv4;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFeaturesRequest;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;

import android.util.Log;

public class OFMessageHandler implements Runnable{

	private List msgQueue = new LinkedList();
	  
	public void processData(OFCommService server, SocketChannel socket, byte[] data, int count) {
	    byte[] dataCopy = new byte[count];
	    System.arraycopy(data, 0, dataCopy, 0, count);
	    synchronized(msgQueue) {
	    	msgQueue.add(new OFMessageEvent(server, socket, dataCopy));
	    	msgQueue.notify();
	    }
	}
	
	public void run() {
		OFMessageEvent msgEvent;	    
	    while(true) {
	    	// Wait for data to become available
	    	synchronized(msgQueue) {
	    		while(msgQueue.isEmpty()) {
	    			try {
	    				msgQueue.wait();
	    			} catch (InterruptedException e) {
	    			}
	    		}
	    		msgEvent = (OFMessageEvent) msgQueue.remove(0);
	    	}	      
	    	Log.d("AVSC", "Received msg with size = "+msgEvent.data.length);
	    	OFMessage ofm = new OFMessage();
	    	Log.d("AVSC", "contnet of data = " + msgEvent.bb.toString());
	    	ofm.readFrom(msgEvent.bb);
	    	
	    	if(ofm.getType() == OFType.HELLO){
	    		Log.d("AVSC", "Received OFPT_HELLO");
	    		msgEvent.server.sendReportToUI("Received OFPT_HELLO");
	    		OFHello ofh = new OFHello();
				ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
				ofh.writeTo(bb);
				msgEvent.server.send(msgEvent.socket, bb.array());	
				OFFeaturesRequest offr = new OFFeaturesRequest();
				bb = ByteBuffer.allocate(offr.getLength());
				offr.writeTo(bb);
				msgEvent.server.send(msgEvent.socket, bb.array());
				msgEvent.server.sendReportToUI("Switch Connected");
	    	}else if(ofm.getType() == OFType.ECHO_REQUEST){
	    		Log.d("AVSC", "Received OFPT_ECHO_REQUEST");
	    		//msgEvent.server.sendReportToUI("Received OFPT_ECHO_REQUEST");
	    		OFEchoReply reply = new OFEchoReply();
				ByteBuffer bb = ByteBuffer.allocate(reply.getLength());
				reply.writeTo(bb);
				msgEvent.server.send(msgEvent.socket, bb.array());
				msgEvent.server.insertFixRule(msgEvent.socket);
	    	}else if(ofm.getType() == OFType.PACKET_IN){
	    		Log.d("AVSC", "Received PACKET_IN");
	    		msgEvent.server.sendReportToUI("Received PACKET_IN");
	    		OFPacketIn ofp_in = new OFPacketIn();
	    		ofp_in.readFrom(ByteBuffer.wrap(msgEvent.data));
	    		
	            OFMatch match = new OFMatch();
	            match.loadFromPacket(ofp_in.getPacketData(), ofp_in.getInPort());
	            if ( match.getDataLayerType() == (short) 0x0806 && match.getInputPort()==1){ //arp from the virtual interface
	            	Log.d("AVSC", "Receive an ARP packet: " + match.toString());
	            	msgEvent.server.sendReportToUI("ARP packet + match = " + match.toString());
	            	
	            	byte[] mac_veth = match.getDataLayerSource();
	            	HexString.toHexString(mac_veth);
	            	Log.d("AVSC", "veth's MAC = " + HexString.toHexString(mac_veth));
	            	int ip_veth = match.getNetworkSource();
	            	Log.d("AVSC", "veth's IP = " + IPv4.fromIPv4Address(ip_veth));
	            	int ip_ans = match.getNetworkDestination();
	            	Log.d("AVSC", "Ask for IP = " + IPv4.fromIPv4Address(ip_ans));
	            	byte[] mac_ans = Ethernet.toMACAddress("00:11:22:33:44:55");
	            	Log.d("AVSC", "synthesized MAC = " + HexString.toHexString(mac_ans));
	            		            		            	
	            	ARP arpReply = new ARP()
	            				.setHardwareType(ARP.HW_TYPE_ETHERNET)
								.setOpCode(ARP.OP_REPLY)
								.setHardwareType(ARP.HW_TYPE_ETHERNET)
								.setProtocolType(ARP.PROTO_TYPE_IP)								
								.setHardwareAddressLength((byte)0x06)
								.setProtocolAddressLength((byte)0x04)
								.setSenderHardwareAddress(mac_ans)
								.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(IPv4.fromIPv4Address(ip_ans)))
								.setTargetHardwareAddress(mac_veth)
								.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(IPv4.fromIPv4Address(ip_veth)));
	            	Ethernet out_packet = new Ethernet()
	            							.setDestinationMACAddress(mac_veth)
	            							.setSourceMACAddress(mac_ans)
	            							.setEtherType(Ethernet.TYPE_ARP);
	            	out_packet.setPayload(arpReply);
								
	            	byte[] packet_serialized = out_packet.serialize();
	                OFPacketOut ofp_out = new OFPacketOut()
	                			.setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort((short)1)}))
	                			.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
	                			.setBufferId(-1)
	                			.setInPort(OFPort.OFPP_NONE)	                			
	                			.setPacketData(packet_serialized);
	                ofp_out.setLengthU(OFPacketOut.MINIMUM_LENGTH + ofp_out.getActionsLengthU() + packet_serialized.length);
	                ByteBuffer bb = ByteBuffer.allocate(ofp_out.getLength());
	        		bb.clear();
	        		ofp_out.writeTo(bb);
	        		bb.flip();
	        		msgEvent.server.send(msgEvent.socket, bb.array());
					msgEvent.server.sendReportToUI("Sent Synthesized ARP Reply: " + arpReply.toString());
					Log.d("AVSC", "Generate an ARP reply:" + arpReply.toString());
	            }else if(match.getDataLayerType() == (short) 0x0806 && match.getInputPort()==2){
	            	//msgEvent.server.sendReportToUI("Sent Synthesized ARP Reply: " + arpReply.toString());
	            }
	    		
	    		msgEvent.server.sendReportToUI(ofp_in.toString());
	    	}else if(ofm.getType() == OFType.FEATURES_REPLY){
	    		Log.d("AVSC", "Received Switch Feature Reply");
	    		msgEvent.server.sendReportToUI("Received Switch Feature Reply");
	    		OFFeaturesReply offr = new OFFeaturesReply();	    		
	    		offr.readFrom(ByteBuffer.wrap(msgEvent.data));	    			    		
	    		msgEvent.server.sendReportToUI("Switch("+offr.getDatapathId()+"): port size = "+ offr.getPorts().size());
	    		msgEvent.server.switchData.put(offr.getDatapathId(), offr);
	    	}else{	    		
	    		msgEvent.server.sendReportToUI("Received OF Message type = " + ofm.getType().toString());
	    	}
	    }
	}
}

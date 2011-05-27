package net.holyc.dispatcher;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;


public class OFReplyEvent{
	int scn; //socketChannelNumber
	byte[] data;	
	public OFReplyEvent(int sc, byte[] _data){
		this.scn = sc;
		this.data = _data;
	}
	public byte[] getData(){		
		return data;
	}
	public int getSocketChannelNumber(){
		return scn;
	}
	public OFMessage getOFMessage(){
		OFMessage ofm = new OFMessage();
		ByteBuffer bb = ByteBuffer.allocate(data.length);
		bb.put(data);
		bb.flip();
		ofm.readFrom(bb);
		return ofm;
	}	
	public OFFlowMod getOFFlowMod(){
		OFFlowMod offm = new OFFlowMod();
		BasicFactory bf = new BasicFactory();
		offm.setActionFactory(bf.getActionFactory());
		ByteBuffer bb = ByteBuffer.allocate(data.length);
		bb.put(data);
		bb.flip();
		offm.readFrom(bb);
		return offm;
	}	
}
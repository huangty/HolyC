package net.holyc.dispatcher;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFMessage;


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
}
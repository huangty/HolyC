package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;

public class OFEvent{
	int scn; //SocketChannelNumber
	OFMessage ofm;	
	public OFEvent(int sc, byte[] _data){
		this.scn = sc;
		ofm = new OFMessage();
		ofm.readFrom(getByteBuffer(_data));
	}
	private ByteBuffer getByteBuffer(byte[] data){
		ByteBuffer bb;
		bb = ByteBuffer.allocate(data.length);
		bb.put(data);
		bb.flip();
		return bb;
	}
	public OFMessage getOFMessage(){
		return ofm;
	}
	public int getSocketChannelNumber(){
		return scn;
	}
}
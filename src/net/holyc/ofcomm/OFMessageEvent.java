package net.holyc.ofcomm;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class OFMessageEvent {
	public OFCommService server;
	public SocketChannel socket;
	public byte[] data;
	public ByteBuffer bb;
	
	OFMessageEvent(OFCommService server, SocketChannel socket, byte[] data) {
		this.server = server;
		this.socket = socket;
		this.data = data;
		bb = ByteBuffer.allocate(data.length);
		bb.put(data);
		bb.flip();
	}
}

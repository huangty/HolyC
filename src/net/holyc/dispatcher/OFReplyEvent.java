package net.holyc.dispatcher;


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
}
package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;

/** Class for OpenFlow message event
 *
 * @author huangty
 * @author ykk
 */
public class OFEvent{
    /**Socket Channel Number
     */
    protected int scn;
    /** Reference to OpenFlow message
     */
    protected OFMessage ofm;
    /** Byte array
     */
    protected byte[] byteArray;

    
    /** Empty constructor
     */
    public OFEvent()
    { }

    /** Constructor
     *
     * @param ofevent OpenFlow message event to clone
     */
    public OFEvent(OFEvent ofevent)
    {
	this.scn = ofevent.getSocketChannelNumber();
	byteArray = ofevent.getByteArray().clone();
	ofm = new OFMessage();
	ofm.readFrom(getByteBuffer(byteArray));
    }

    /** Constructor
     *
     * @param sc socket channel number
     * @param _data packet
     */
    public OFEvent(int sc, byte[] _data){
	this.scn = sc;
	byteArray = _data.clone();
	ofm = new OFMessage();
	ofm.readFrom(getByteBuffer(_data));
    }

    /** Function to return byte array with entire message
     *
     * @return byte array
     */
    public byte[] getByteArray()
    {
	return byteArray;
    }

    /** Function to convert byte array to ByteBuffer
     * 
     * @param data byte array with data
     * @return ByteBuffer with message
     */
    protected ByteBuffer getByteBuffer(byte[] data){
	ByteBuffer bb;
	bb = ByteBuffer.allocate(data.length);
	bb.put(data);
	bb.flip();
	return bb;
    }

    /** Return reference to OpenFlow message
     *
     * @return OpenFlow message
     */
    public OFMessage getOFMessage(){
	return ofm;
    }

    /** Return socket channel number
     *
     * @return socket channel number
     */
    public int getSocketChannelNumber(){
	return scn;
    }
}

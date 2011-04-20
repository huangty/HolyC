package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;

import android.util.Log;

import net.holyc.dispatcher.OFEvent;

/** Class for OpenFlow packet in event
 *
 * @author ykk
 */
public class OFPacketInEvent
    extends OFEvent
{
    /** Constructor
     *
     * @param ofevent OpenFlow message event to clone
     */
    public OFPacketInEvent(OFEvent ofevent)
    {
	this.scn = ofevent.getSocketChannelNumber();
	byteArray = ofevent.getByteArray().clone();
	ofm = new OFPacketIn();
	((OFPacketIn) ofm).readFrom(getByteBuffer(byteArray));
    }

    /** Return reference to OpenFlow message
     */
    public OFPacketIn getOFPacketIn()
    {
	return (OFPacketIn) ofm;
    }
}

package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFFlowRemoved;

import net.holyc.dispatcher.OFEvent;

/** Class for OpenFlow flow removed event
 *
 * @author ykk
 */
public class OFFlowRemovedEvent
    extends OFEvent
{
    /** OpenFlow flow removed
     */
    OFFlowRemoved ofm;

    /** Constructor
     *
     * @param ofevent OpenFlow message event to clone
     */
    public OFFlowRemovedEvent(OFEvent ofevent)
    {
	this.scn = ofevent.getSocketChannelNumber();
	byteArray = ofevent.getByteArray().clone();
	ofm = new OFFlowRemoved();
	((OFFlowRemoved) ofm).readFrom(getByteBuffer(byteArray));
    }

    /** Return reference to OpenFlow message
     */
    public OFFlowRemoved getOFFlowRemoved()
    {
	return (OFFlowRemoved) ofm;
    }
}

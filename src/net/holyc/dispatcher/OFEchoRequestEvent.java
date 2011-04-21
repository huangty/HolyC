package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFEchoRequest;

import net.holyc.dispatcher.OFEvent;

/** Class for OpenFlow echo request event
 *
 * @author ykk
 */
public class OFEchoRequestEvent
    extends OFEvent
{
    /** Constructor
     *
     * @param ofevent OpenFlow message event to clone
     */
    public OFEchoRequestEvent(OFEvent ofevent)
    {
	super(ofevent);
    }

    /** Return reference to OpenFlow message
     */
    public OFEchoRequest getOFHello()
    {
	return (OFEchoRequest) ofm;
    }
}

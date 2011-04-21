package net.holyc.dispatcher;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFHello;

import net.holyc.dispatcher.OFEvent;

/** Class for OpenFlow hello event
 *
 * @author ykk
 */
public class OFHelloEvent
    extends OFEvent
{
    /** Constructor
     *
     * @param ofevent OpenFlow message event to clone
     */
    public OFHelloEvent(OFEvent ofevent)
    {
	super(ofevent);
    }

    /** Return reference to OpenFlow message
     */
    public OFHello getOFHello()
    {
	return (OFHello) ofm;
    }
}

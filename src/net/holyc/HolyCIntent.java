package net.holyc;

/** Static class to list all the intent action used in HolyC.
* 
* Rant: This is nasty but necessary.
*
* @author huangty
* @author ykk
* @date Apr 2011
*/

public class HolyCIntent{
    /** Intent to deliver arbitrary OpenFlow message
     *
     * @see OFEvent
     */
    public class BroadcastOFEvent{
	public static final String action = "holyc.intent.broadcast.OFEVENT";
	public static final String str_key = "OFEVENT";
	
    }

    /** Intent to deliver OpenFlow packet in event
     *
     * @see OFPacketInEvent
     */
    public class OFPacketIn_Intent{
	public static final String action = "holyc.intent.broadcast.PACKETIN";
	public static final String str_key = "OFPKTIN";	
    }

    /** Intent to send arbitrary OpenFlow message
     */
    public class BroadcastOFReply{
	public static final String action = "holyc.intent.broadcast.OFREPLYEVENT";
	public static final String str_key = "OF_REPLY_EVENT";
    }
}

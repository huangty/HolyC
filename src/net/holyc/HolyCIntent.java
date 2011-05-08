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

    /** Intent to deliver OpenFlow hello event
     *
     * @see OFHelloEvent
     */
    public class OFHello_Intent{
	public static final String action = "holyc.intent.broadcast.HELLO";
	public static final String str_key = "OFHELLO";	
    }

    /** Intent to deliver OpenFlow echo request event
     *
     * @see OFEchoRequestEvent
     */
    public class OFEchoRequest_Intent{
	public static final String action = "holyc.intent.broadcast.ECHOREQ";
	public static final String str_key = "OFECHOREQUEST";	
    }

    /** Intent to deliver OpenFlow echo request event
    *
    * @see OFErrorEvent
    */
   public class OFError_Intent{
	   public static final String action = "holyc.intent.broadcast.ERROR";
	   public static final String str_key = "OFERROR";	
   }

    /** Intent to deliver OpenFlow packet in event
     *
     * @see OFPacketInEvent
     */
    public class OFPacketIn_Intent{
	public static final String action = "holyc.intent.broadcast.PACKETIN";
	public static final String str_key = "OFPKTIN";	
    }

    /** Intent to deliver OpenFlow flow removed event
     *
     * @see OFFlowRemovedEvent
     */
    public class OFFlowRemoved_Intent{
	public static final String action = "holyc.intent.broadcast.FLOWREMOVED";
	public static final String str_key = "OFFLOWRM";	
    }

    /** Intent to send/reply to arbitrary OpenFlow message
     */
    public class BroadcastOFReply{
	public static final String action = "holyc.intent.broadcast.OFREPLYEVENT";
	public static final String str_key = "OF_REPLY_EVENT";
    }

    /** Intent Lal use to expose new applications
     */
    public class LalAppFound{
	public static final String action = "holyc.intent.broadcast.LALAPPFOUND";
	public static final String str_key = "LAL_APP_NAME";	
    }
    
    /** Intent Lal use to request permission from applications
     */
    public class LalPermInquiry{
    	public static final String action = "holyc.intent.broadcast.LALPERMINQUIRY";
    	public static final String str_key = "LAL_PERM_INQUIRY";	
    }
    
    /** Intent Lal use to receive permission response from applications
     */
    public class LalPermResponse{
    	public static final String action = "holyc.intent.broadcast.LALPERMRESPONSE";
    	public static final String str_key = "LAL_PERM_RESPONSE";	
    }
}

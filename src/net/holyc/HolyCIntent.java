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
	public static final String data_key = "OFEVENT_DATA";
	public static final String port_key = "OFEVENT_PORT";
	
    }

    /** Intent to deliver OpenFlow hello event
     *
     * @see OFHelloEvent
     */
    public class OFHello_Intent{
	public static final String action = "holyc.intent.broadcast.HELLO";
	public static final String str_key = "OFHELLO";	
	public static final String data_key = "OFHELLO_DATA";
	public static final String port_key = "OFHELLO_PORT";
    }

    /** Intent to deliver OpenFlow echo request event
     *
     * @see OFEchoRequestEvent
     */
    public class OFEchoRequest_Intent{
	public static final String action = "holyc.intent.broadcast.ECHOREQ";
	public static final String str_key = "OFECHOREQUEST";
	public static final String data_key = "OFECHOREQUEST_DATA";
	public static final String port_key = "OFECHOREQUEST_PORT";
    }

    /** Intent to deliver OpenFlow echo request event
    *
    * @see OFErrorEvent
    */
   public class OFError_Intent{
	   public static final String action = "holyc.intent.broadcast.ERROR";
	   public static final String str_key = "OFERROR";	
	   public static final String data_key = "OFERROR_DATA";
	   public static final String port_key = "OFERROR_PORT";
   }

    /** Intent to deliver OpenFlow packet in event
     *
     * @see OFPacketInEvent
     */
    public class OFPacketIn_Intent{
	public static final String action = "holyc.intent.broadcast.PACKETIN";
	public static final String str_key = "OFPKTIN";	
	public static final String data_key = "OFPKTIN_DATA";
	public static final String port_key = "OFPKTIN_PORT";
    }

    /** Intent to deliver OpenFlow flow removed event
     *
     * @see OFFlowRemovedEvent
     */
    public class OFFlowRemoved_Intent{
	public static final String action = "holyc.intent.broadcast.FLOWREMOVED";
	public static final String str_key = "OFFLOWRM";	
	public static final String data_key = "OFFLOWRM_DATA";
	public static final String port_key = "OFFLOWRM_PORT";
    }

    /** Intent to send/reply to arbitrary OpenFlow message
     */
    public class BroadcastOFReply{
	public static final String action = "holyc.intent.broadcast.OFREPLYEVENT";
	public static final String str_key = "OF_REPLY_EVENT";
	public static final String data_key = "OF_REPLY_EVENT_DATA";
	public static final String port_key = "OF_REPLY_EVENT_PORT";
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

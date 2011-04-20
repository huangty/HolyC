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
    public class BroadcastOFEvent{
	public static final String action = "holyc.intent.broadcast.OFEVENT";
	//public static final String bundle_key = "MSG_OFCOMM_EVENT";
	public static final String str_key = "OFEVENT";
	
    }

    public class BroadcastOFReply{
	public static final String action = "holyc.intent.broadcast.OFREPLYEVENT";
	//public static final String bundle_key = "OF_REPLY_EVENT";
	public static final String str_key = "OF_REPLY_EVENT";
    }
}
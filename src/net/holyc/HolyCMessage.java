package net.holyc;

/** Static class to list all the message type used in HolyC.
 *
 * HolyC will use message type from 16,384 to 32,767 (INT_MAX for 16 bit).
 * 
 * Rant: This is nasty but necessary.
 *
 * @author ykk
 * @author huangty
 * @date Apr 2011
 */
public class HolyCMessage
{
    /** Maximum message type number an application is allowed to use.
     * HolyC will use a number greater than this for its message.
     */
    public static final int MAX_MSG_TYPE = 16383;

    public class REGISTER_CLIENT
    {
	public static final int type = MAX_MSG_TYPE+1;
    }

    public class UNREGISTER_CLIENT
    {
	public static final int type = MAX_MSG_TYPE+2;
    }

    public class DISPATCH_REPORT
    {
	public static final int type = MAX_MSG_TYPE+3;
	public static final String str_key = "MSG_DISPATCH_REPORT";
    }

    public class UIREPORT_UPDATE
    {
	public static final int type = MAX_MSG_TYPE+4;
	public static final String str_key = "MSG_UIREPORT_UPDATE";
    }

    public class NEW_EVENT
    {
	public static final int type = MAX_MSG_TYPE+5;
    }

    public class OFCOMM_EVENT
    {
	public static final int type = MAX_MSG_TYPE+6;
	public static final String str_key = "OFEVENT";
    }

    public class OFREPLY_EVENT
    {
	public static final int type = MAX_MSG_TYPE+7;
	public static final String bundle_key = "OF_REPLY_EVENT";
	public static final String str_key = "OF_REPLY_EVENT";
    }

}
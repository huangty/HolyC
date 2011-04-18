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

    /**
     * @see DispatcheService
     */
    public class REGISTER_CLIENT
    {
	public static final int type = MAX_MSG_TYPE+1;
    }

    /**
     * @see DispatcheService
     */
    public class UNREGISTER_CLIENT
    {
	public static final int type = MAX_MSG_TYPE+2;
    }

    /**
     * @see DispatcheService
     */
    public class DISPATCH_REPORT
    {
	public static final int type = MAX_MSG_TYPE+3;
	public static final String str_key = "MSG_DISPATCH_REPORT";
    }

    /**
     * @see DispatcheService
     */
    public class UIREPORT_UPDATE
    {
	public static final int type = MAX_MSG_TYPE+4;
	public static final String str_key = "MSG_UIREPORT_UPDATE";
    }

    /**
     * @see DispatcheService
     */
    public class NEW_EVENT
    {
	public static final int type = MAX_MSG_TYPE+5;
    }

    /**
     * @see DispatcheService
     */
    public class OFCOMM_EVENT
    {
	public static final int type = MAX_MSG_TYPE+6;
	public static final String str_key = "OFEVENT";
    }

    /**
     * @see DispatcheService
     */
    public class OFREPLY_EVENT
    {
	public static final int type = MAX_MSG_TYPE+7;
	public static final String bundle_key = "OF_REPLY_EVENT";
	public static final String str_key = "OF_REPLY_EVENT";
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_REGISTER
    {
	public static final int type = MAX_MSG_TYPE+8;
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_UNREGISTER
    {
	public static final int type = MAX_MSG_TYPE+9;
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_START
    {
	public static final int type = MAX_MSG_TYPE+10;
    }


}
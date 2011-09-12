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
    public class DISPATCH_STOP
    {
	public static final int type = MAX_MSG_TYPE+4;
	public static final String str_key = "MSG_DISPATCH_STOP_REASON";
    }

    /**
     * @see DispatcheService
     */
    public class UIREPORT_UPDATE
    {
	public static final int type = MAX_MSG_TYPE+5;
	public static final String str_key = "MSG_UIREPORT_UPDATE";
    }

    /**
     * @see DispatcheService
     */
    public class NEW_EVENT
    {
	public static final int type = MAX_MSG_TYPE+6;
    }

    /**
     * @see DispatcheService
     */
    public class OFCOMM_EVENT
    {
	public static final int type = MAX_MSG_TYPE+7;
	public static final String str_key = "OFEVENT";
	public static final String data_key = "OFDATA";
	public static final String port_key = "OFPORT";
    }

    /**
     * @see DispatcheService
     */
    public class OFREPLY_EVENT
    {
	public static final int type = MAX_MSG_TYPE+8;
	public static final String str_key = "OFREPLY_EVENT";
	public static final String data_key = "OFREPLY_DATA";
	public static final String port_key = "OFREPLY_PORT";
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_REGISTER
    {
	public static final int type = MAX_MSG_TYPE+9;
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_UNREGISTER
    {
	public static final int type = MAX_MSG_TYPE+10;
    }

    /**
     * @see EnvInitService
     */
    public class ENV_INIT_START
    {
	public static final int type = MAX_MSG_TYPE+11;
    }

    /**
     * @see MonitorService
     */
    public class MONITOR_REGISTER
    {
	public static final int type = MAX_MSG_TYPE+12;
    }

    /**
     * @see MonitorService
     */
    public class MONITOR_UNREGISTER
    {
	public static final int type = MAX_MSG_TYPE+13;
    }

    /**
     * @see MonitorService
     */
    public class MONITOR_START
    {
	public static final int type = MAX_MSG_TYPE+14;
    }

    /**
     * @see statusUI
     */
    public class STATUSUI_REPORT_UPDATE
    {
	public static final int type = MAX_MSG_TYPE+15;
	public static final String str_key = "MSG_REPORT_UPDATE";
    }

    /**
     * @see OFCommService
     */
    public class OFCOMM_REGISTER
    {
	public static final int type = MAX_MSG_TYPE+16;
    }

    /**
     * @see OFCommService
     */
    public class OFCOMM_UNREGISTER
    {
	public static final int type = MAX_MSG_TYPE+17;
    }

    /**
     * @see OFCommService
     */
    public class OFCOMM_SET_VALUE
    {
	public static final int type = MAX_MSG_TYPE+18;
    }

    /**
     * @see OFCommService
     */
    public class OFCOMM_START_OPENFLOWD
    {
	public static final int type = MAX_MSG_TYPE+19;
    }
    
    /**
     * @see EnvInitService
     */
    public class ENV_INIT_FINISH
    {
	public static final int type = MAX_MSG_TYPE+20;
    }
    
    /**
     * @see DispatchService
     */
    public class OFCOMM_SEND_REQUEST_UI
    {
    	public static final int type = MAX_MSG_TYPE+21;
    	public static final String data_key = "OF_REQUEST";
    }
    
    public class SENSORHINT_START
    {
    	public static final int type = MAX_MSG_TYPE+22;
    	//public static final String data_key = "SENSORHINT_START";
    }
    
    public class SENSORHINT_STOP
    {
    	public static final int type = MAX_MSG_TYPE+23;
    	//public static final String data_key = "SENSORHINT_STOP";
    }
    public class SENSORHINT_REGISTER
    {
    	public static final int type = MAX_MSG_TYPE+25;    	
    }
    public class SENSORHINT_UNREGISTER
    {
    	public static final int type = MAX_MSG_TYPE+26;    	
    }
}
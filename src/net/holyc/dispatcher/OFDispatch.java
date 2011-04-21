package net.holyc.dispatcher;

import com.google.gson.Gson;

import org.openflow.protocol.OFType;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFEvent;

/** Class to dispatch OpenFlow message.
 * 
 * @author ykk
 * @date Apr 2011
 */
public class OFDispatch
    extends BroadcastReceiver
{
    /** Log name
     */
    private static String TAG = "HOLYC.OFDispatch";
    /** Reference to GSON
     */
    Gson gson = new Gson();
    
    /** Class to store output from parsing
     */
    private class Result
    {
	public String action;
	public String key;
	public String string;
    }

    @Override 
	public void onReceive(Context context, Intent intent)
    {
	if(intent.getAction().equals(HolyCIntent.BroadcastOFEvent.action)){
	    String ofe_json = intent.getStringExtra(HolyCIntent.BroadcastOFEvent.str_key);
	    OFEvent ofe = gson.fromJson(ofe_json, OFEvent.class);
	    
	    Result r = null;
	    switch (ofe.getOFMessage().getType())
	    {
	    case HELLO:
		r.action = HolyCIntent.OFHello_Intent.action;
		r.key = HolyCIntent.OFHello_Intent.str_key;
		OFHelloEvent ohe = new OFHelloEvent(ofe);
		r.string = gson.toJson(ohe, OFHelloEvent.class);
		break;

	    case ECHO_REQUEST:
		r.action = HolyCIntent.OFEchoRequest_Intent.action;
		r.key = HolyCIntent.OFEchoRequest_Intent.str_key;
		OFEchoRequestEvent oere = new OFEchoRequestEvent(ofe);
		r.string = gson.toJson(oere, OFEchoRequestEvent.class);
		break;

	    case PACKET_IN:
		r = new Result();
		r.action = HolyCIntent.OFPacketIn_Intent.action;
		r.key = HolyCIntent.OFPacketIn_Intent.str_key;
		OFPacketInEvent opie = new OFPacketInEvent(ofe);
		r.string = gson.toJson(opie, OFPacketInEvent.class);
		break;
	    }

	    //Send out generated Intent
	    if (r != null)
	    {
		Intent outIntent = new Intent(r.action);
		outIntent.setPackage(context.getPackageName());
		outIntent.putExtra(r.key, r.string);
		Log.d(TAG, "Receive and broadcasting "+r.action);
		context.sendBroadcast(outIntent);
	    }

	}
    }
}


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
		Log.d(TAG, "Hello received");
		break;

	    case PACKET_IN:
		r = new Result();
		r.action = HolyCIntent.OFPacketIn_Intent.action;
		r.key = HolyCIntent.OFPacketIn_Intent.str_key;
		OFPacketInEvent opie = new OFPacketInEvent(ofe);
		r.string = gson.toJson(opie, OFPacketInEvent.class);
		Log.d(TAG, "Receive and broadcasting packet in");
		break;
	    }

	    if (r != null)
	    {
		Intent outIntent = new Intent(r.action);
		outIntent.setPackage(context.getPackageName());
		outIntent.putExtra(r.key, r.string);
		context.sendBroadcast(outIntent);
	    }

	}
    }
}


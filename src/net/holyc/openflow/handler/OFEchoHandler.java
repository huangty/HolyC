package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import com.google.gson.Gson;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFEchoRequestEvent;
import net.holyc.dispatcher.OFReplyEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * The class to handle OpenFlow echo Messages 
 *
 * @author Te-Yuan Huang (huangty@stanford.edu)
 * @author ykk
 */

public class OFEchoHandler extends BroadcastReceiver {
    private String TAG = "HOLYC.OFEchoHandler";
    Gson gson = new Gson();

    @Override    
	public void onReceive(Context context, Intent intent) {
	if(intent.getAction().equals(HolyCIntent.OFEchoRequest_Intent.action)){
	    OFEchoRequestEvent oere = 
		gson.fromJson(intent.getStringExtra(HolyCIntent.OFEchoRequest_Intent.str_key),
			      OFEchoRequestEvent.class);
	    Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
	    poutIntent.setPackage(context.getPackageName());
	    poutIntent.putExtra(HolyCIntent.BroadcastOFReply.str_key, doProcessOFEvent(oere));
	    context.sendBroadcast(poutIntent);
	}
    }
    
    String doProcessOFEvent(OFEchoRequestEvent ofe){
    	OFMessage ofm = ofe.getOFMessage();
	Log.d(TAG, "Received OFPT_ECHO_REQUEST");
	OFEchoReply reply = new OFEchoReply();
	ByteBuffer bb = ByteBuffer.allocate(reply.getLength());
	reply.writeTo(bb);
	OFReplyEvent ofpoe = new OFReplyEvent(ofe.getSocketChannelNumber(), bb.array());
	Log.d(TAG, "Generate PacketOutEvent (OFECHOReply) with socket channel index = " + 
	      ofpoe.getSocketChannelNumber());
	String ofout = gson.toJson(ofpoe, OFReplyEvent.class);
	return ofout;
    }
};

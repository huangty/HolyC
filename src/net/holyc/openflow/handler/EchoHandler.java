package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFEchoRequestEvent;
import net.holyc.dispatcher.OFReplyEvent;
import net.holyc.host.Utility;
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

public class EchoHandler extends BroadcastReceiver {
    private String TAG = "HOLYC.OFEchoHandler";
    
    private byte[] ofdata;
    private int port;

    @Override    
	public void onReceive(Context context, Intent intent) {
	if(intent.getAction().equals(HolyCIntent.OFEchoRequest_Intent.action)){
		ofdata = intent.getByteArrayExtra(HolyCIntent.OFEchoRequest_Intent.data_key);
		port = intent.getIntExtra(HolyCIntent.OFEchoRequest_Intent.port_key, -1);
		Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
		poutIntent.setPackage(context.getPackageName());
		poutIntent.putExtra(HolyCIntent.BroadcastOFReply.data_key, doProcessOFEvent(ofdata));
		poutIntent.putExtra(HolyCIntent.BroadcastOFReply.port_key, port);	    
	    context.sendBroadcast(poutIntent);
	}
    }
    
    byte[] doProcessOFEvent(byte[] ofdata){
    	OFMessage ofm = new OFMessage();
    	ofm.readFrom(Utility.getByteBuffer(ofdata));
    	Log.d(TAG, "Received OFPT_ECHO_REQUEST");
    	OFEchoReply reply = new OFEchoReply();
    	reply.setXid(ofm.getXid());
    	ByteBuffer bb = ByteBuffer.allocate(reply.getLength());
    	reply.writeTo(bb);
    	return bb.array();    	
    }
};

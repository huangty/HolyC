package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import com.google.gson.Gson;

import net.holyc.R;
import net.holyc.controlUI;
import net.holyc.dispatcher.DispatchService;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFReplyEvent;
import net.holyc.host.EnvInitService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
* The class to handle OFHello Messages 
*
* @author Te-Yuan Huang (huangty@stanford.edu)
*/

public class OFHelloEchoHandler extends BroadcastReceiver {
	private String TAG = "HOLYC.OFHelloEchoHandler";
	private boolean mDispatchIsBound;
	Messenger mDispatchService = null;
	OFEvent received_ofe = null;
    @Override    
    public void onReceive(Context context, Intent intent) {
		if(intent.getAction().equals(DispatchService.OFEVENT_UPDATE)){
			Gson gson = new Gson();
			Bundle bundle = intent.getBundleExtra("MSG_OFCOMM_EVENT");		
			received_ofe = gson.fromJson(bundle.getString("OFEVENT"), OFEvent.class);
			Log.d(TAG, "receive from OFEvent broadcast with OFMessage:" + received_ofe.getOFMessage().toString() + "with socket channel index = " + received_ofe.getSocketChannelNumber());
			Intent poutIntent = new Intent(DispatchService.OF_REPLY_EVENT);
			poutIntent.setPackage(context.getPackageName());
			Bundle ofout = doProcessOFEvent(received_ofe);
			if(ofout != null){
				poutIntent.putExtra("OF_REPLY_EVENT", ofout);
				context.sendBroadcast(poutIntent);
			}					
		}
    }
    
    Bundle doProcessOFEvent(OFEvent ofe){
    	Gson gson = new Gson();
    	OFMessage ofm = ofe.getOFMessage();
    	if(ofm.getType() == OFType.HELLO){
    		Log.d(TAG, "Received OFPT_HELLO");
    		OFHello ofh = new OFHello();
			ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
			ofh.writeTo(bb);
			OFReplyEvent ofpoe = new OFReplyEvent(ofe.getSocketChannelNumber(), bb.array());
			Log.d(TAG, "Generate OFReply Event (OFHello) with socket channel index = " + ofpoe.getSocketChannelNumber());
			Bundle bundle = new Bundle();
			bundle.putString("OF_REPLY_EVENT", gson.toJson(ofpoe, OFReplyEvent.class));
	    	return bundle;
    	}else if(ofm.getType() == OFType.ECHO_REQUEST){
    		Log.d(TAG, "Received OFPT_ECHO_REQUEST");
    		OFEchoReply reply = new OFEchoReply();
			ByteBuffer bb = ByteBuffer.allocate(reply.getLength());
			reply.writeTo(bb);
			OFReplyEvent ofpoe = new OFReplyEvent(ofe.getSocketChannelNumber(), bb.array());
			Log.d(TAG, "Generate PacketOutEvent (OFECHOReply) with socket channel index = " + ofpoe.getSocketChannelNumber());
			Bundle bundle = new Bundle();
			bundle.putString("OF_REPLY_EVENT", gson.toJson(ofpoe, OFReplyEvent.class));
	    	return bundle;
    	}			
    	return null;
    }
};



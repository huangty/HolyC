package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import com.google.gson.Gson;

import net.holyc.R;
import net.holyc.controlUI;
import net.holyc.dispatcher.DispatchService;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFPacketOutEvent;
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
			Log.d(TAG, "receive from OFEvent broadcast with OFMessage:" + received_ofe.getOFMessage().toString());
			Intent poutIntent = new Intent(DispatchService.OF_PACKETOUT_EVENT);
			poutIntent.setPackage(context.getPackageName());
			Bundle ofout = doProcessOFEvent(received_ofe);
			poutIntent.putExtra("OF_PACKETOUT", ofout);	
			context.sendBroadcast(poutIntent);
					
		}
    }
    
    Bundle doProcessOFEvent(OFEvent ofe){
    	Gson gson = new Gson();
    	OFMessage ofm = ofe.getOFMessage();
		Bundle bundle = new Bundle();
    	if(ofm.getType() == OFType.HELLO){
    		Log.d(TAG, "Received OFPT_HELLO");
    		OFHello ofh = new OFHello();
			ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
			ofh.writeTo(bb);
			OFPacketOutEvent ofpoe = new OFPacketOutEvent(ofe.getSocketChannelNumber(), bb.array());
			bundle.putString("OF_PACKETOUT", gson.toJson(ofpoe, OFPacketOutEvent.class));
    	}
    	return bundle;
    }
};



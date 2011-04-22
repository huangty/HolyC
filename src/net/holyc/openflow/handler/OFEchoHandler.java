package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import com.google.gson.Gson;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFReplyEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * The class to handle OFHello Messages
 * 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 */

public class OFEchoHandler extends BroadcastReceiver {
	private String TAG = "HOLYC.OFEchoHandler";
	OFEvent received_ofe = null;
	Gson gson = new Gson();

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(HolyCIntent.BroadcastOFEvent.action)) {
			String ofe_json = intent
					.getStringExtra(HolyCIntent.BroadcastOFEvent.str_key);
			received_ofe = gson.fromJson(ofe_json, OFEvent.class);
			Log.d(TAG,
					"receive from OFEvent broadcast with OFMessage:"
							+ received_ofe.getOFMessage().toString()
							+ "with socket channel index = "
							+ received_ofe.getSocketChannelNumber());
			Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
			poutIntent.setPackage(context.getPackageName());
			String ofout = doProcessOFEvent(received_ofe);
			if (ofout != null) {
				poutIntent
						.putExtra(HolyCIntent.BroadcastOFReply.str_key, ofout);
				context.sendBroadcast(poutIntent);
			}
		}
	}

	String doProcessOFEvent(OFEvent ofe) {
		OFMessage ofm = ofe.getOFMessage();
		if (ofm.getType() == OFType.ECHO_REQUEST) {
			Log.d(TAG, "Received OFPT_ECHO_REQUEST");
			OFEchoReply reply = new OFEchoReply();
			reply.setXid(ofm.getXid());
			ByteBuffer bb = ByteBuffer.allocate(reply.getLength());
			reply.writeTo(bb);
			OFReplyEvent ofpoe = new OFReplyEvent(ofe.getSocketChannelNumber(),
					bb.array());
			Log.d(TAG,
					"Generate PacketOutEvent (OFECHOReply) with socket channel index = "
							+ ofpoe.getSocketChannelNumber());
			String ofout = gson.toJson(ofpoe, OFReplyEvent.class);
			return ofout;
		}
		return null;
	}
};

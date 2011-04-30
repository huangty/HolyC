package net.holyc.openflow.handler;

import java.nio.ByteBuffer;

import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import com.google.gson.Gson;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFEchoRequestEvent;
import net.holyc.dispatcher.OFErrorEvent;
import net.holyc.dispatcher.OFReplyEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * The class to handle OpenFlow Error Messages
 * 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 */

public class ErrorHandler extends BroadcastReceiver {
	private String TAG = "HOLYC.OFErrorHandler";
	Gson gson = new Gson();

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(HolyCIntent.OFError_Intent.action)) {
			OFErrorEvent oere = gson.fromJson(
					intent.getStringExtra(HolyCIntent.OFError_Intent.str_key),
					OFErrorEvent.class);
			Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
			poutIntent.setPackage(context.getPackageName());
			poutIntent.putExtra(HolyCIntent.BroadcastOFReply.str_key,
					doProcessOFError(oere));
			context.sendBroadcast(poutIntent);
		}
	}

	String doProcessOFError(OFErrorEvent ofe) {
		OFMessage ofm = ofe.getOFMessage();
		Log.d(TAG, "Received OFPT_ERROR: " + ofe.getOFError().toString());
		return null;
	}
};

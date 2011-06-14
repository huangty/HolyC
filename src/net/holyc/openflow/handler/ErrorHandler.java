package net.holyc.openflow.handler;


import org.openflow.protocol.OFError;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFErrorEvent;
import net.holyc.host.Utility;
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

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(HolyCIntent.OFError_Intent.action)) {
			byte[] ofdata = intent.getByteArrayExtra(HolyCIntent.OFError_Intent.data_key);			
			OFError ope = new OFError();
			ope.readFrom(Utility.getByteBuffer(ofdata));
			doProcessOFError(ope);
		}
	}

	String doProcessOFError(OFError ofe) {		
		Log.d(TAG, "Received OFPT_ERROR: " + ofe.toString());
		return null;
	}
};

package net.holyc.dispatcher;

import java.nio.ByteBuffer;

import com.google.gson.Gson;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFError;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.holyc.HolyCIntent;
import net.holyc.HolyCMessage;
import net.holyc.dispatcher.OFEvent;
import net.holyc.host.Utility;

/**
 * Class to dispatch OpenFlow message.
 * 
 * And for now print the errors!
 * 
 * @author ykk
 * @date Apr 2011
 */
public class OFDispatch extends BroadcastReceiver {
	/**
	 * Log name
	 */
	private static String TAG = "HOLYC.OFDispatch";
	/**
	 * Reference to GSON
	 */
	Gson gson = new Gson();

	/**
	 * Class to store output from parsing
	 */
	private class Result {
		public String action;
		public String key;
		//public String string;
		public String port_key;
		//public int port;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(HolyCIntent.BroadcastOFEvent.action)) {
			/** gson test**/
			/*String ofe_json = intent
					.getStringExtra(HolyCIntent.BroadcastOFEvent.str_key);
			OFEvent ofe = gson.fromJson(ofe_json, OFEvent.class);
			OFMessage ofm = ofe.getOFMessage(); */
			byte[] ofdata = intent.getByteArrayExtra(HolyCIntent.BroadcastOFEvent.data_key);
			int port = intent.getIntExtra(HolyCIntent.BroadcastOFEvent.port_key, -1);
			if(port == -1){
				Log.d(TAG, "port number is wrong!!!!");
			}
			OFMessage ofm = new OFMessage();
			
			ofm.readFrom(Utility.getByteBuffer(ofdata));
			/** end of gson test**/
			
			Result r = null;			
			if(ofm.getType() == null ){
				Log.e(TAG, "GET AN OFMessage with EMPTY OFTYPE! OFMessage = " + ofm.toString());
				return;
			}
			switch (ofm.getType()) {
			//case HELLO:
				//r = new Result();
				//r.action = HolyCIntent.OFHello_Intent.action;
				/** gson test**/
				//r.key = HolyCIntent.OFHello_Intent.str_key;
				//OFHelloEvent ohe = new OFHelloEvent(ofe);				
				//r.string = gson.toJson(ohe, OFHelloEvent.class);
				//r.key = HolyCIntent.OFHello_Intent.data_key;
				//r.port_key = HolyCIntent.OFHello_Intent.port_key;
				/** end of gson test**/
				//break;

			case ECHO_REQUEST:
				r = new Result();
				r.action = HolyCIntent.OFEchoRequest_Intent.action;
				/** gson test**/
				//r.key = HolyCIntent.OFEchoRequest_Intent.str_key;
				//OFEchoRequestEvent oere = new OFEchoRequestEvent(ofe);				
				//r.string = gson.toJson(oere, OFEchoRequestEvent.class);
				r.key = HolyCIntent.OFEchoRequest_Intent.data_key;
				r.port_key = HolyCIntent.OFEchoRequest_Intent.port_key;
				/** end of gson test**/
				break;

			case PACKET_IN:
				r = new Result();
				r.action = HolyCIntent.OFPacketIn_Intent.action;
				/** gson test**/
				//r.key = HolyCIntent.OFPacketIn_Intent.str_key;
				//OFPacketInEvent opie = new OFPacketInEvent(ofe);								
				//r.string = gson.toJson(opie, OFPacketInEvent.class);
				r.key = HolyCIntent.OFPacketIn_Intent.data_key;
				r.port_key = HolyCIntent.OFPacketIn_Intent.port_key;
				/** end of gson test**/
				break;

			case FLOW_REMOVED:
				r = new Result();
				r.action = HolyCIntent.OFFlowRemoved_Intent.action;
				/** gson test**/
				//r.key = HolyCIntent.OFFlowRemoved_Intent.str_key;
				//OFFlowRemovedEvent ofre = new OFFlowRemovedEvent(ofe);								
				//r.string = gson.toJson(ofre, OFFlowRemovedEvent.class);
				r.key = HolyCIntent.OFFlowRemoved_Intent.data_key;
				r.port_key = HolyCIntent.OFFlowRemoved_Intent.port_key;
				/** end of gson test**/				
				break;

			case ERROR:
				/** gson test**/
				//OFErrorEvent ore = new OFErrorEvent(ofe);
				//OFError oe = ore.getOFError();
				/** end of gson test**/
				OFError oe = new OFError();
				oe.readFrom(Utility.getByteBuffer(ofdata));
				Log.d(TAG, "Error!!! Error type " + oe.getErrorType()+
				      " code " + oe.getErrorCode());
				break;
			}

			// Send out generated Intent
			if (r != null) {
				Intent outIntent = new Intent(r.action);
				outIntent.setPackage(context.getPackageName());
				outIntent.putExtra(r.key, ofdata);		
				outIntent.putExtra(r.port_key, port);
				context.sendBroadcast(outIntent);
			}
		}
	}
}

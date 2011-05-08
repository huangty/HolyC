package edu.stanford.lal;

import java.nio.ByteBuffer;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFPacketInEvent;
import net.holyc.dispatcher.OFReplyEvent;
import net.holyc.host.Utility;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.util.HexString;
import org.openflow.util.U16;

import com.google.gson.Gson;

import edu.stanford.lal.permission.PermissionEnquiry;
import edu.stanford.lal.permission.PermissionResponse;

import android.content.Context;
import android.content.Intent;

public class LalPermBasedFlowSwitch extends LalFlowSwitch {

	Gson gson = new Gson();
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(HolyCIntent.OFPacketIn_Intent.action)) {
			OFPacketInEvent opi = gson.fromJson(intent
					.getStringExtra(HolyCIntent.OFPacketIn_Intent.str_key),
					OFPacketInEvent.class);
			OFPacketIn opie = opi.getOFPacketIn();

			// Record port and entry
			OFMatch ofm = new OFMatch();
			ofm.loadFromPacket(opie.getPacketData(), opie.getInPort());
			hostPort.put(HexString.toHexString(ofm.getDataLayerSource()),
					new Short(ofm.getInputPort()));

			// Send to Adam and ask for permission 
			int app_cookie = getCookie(ofm, context);
			String app_name = getAppName(ofm, context);
			PermissionEnquiry pe = new PermissionEnquiry(ofm, opie, app_name, app_cookie, opi.getSocketChannelNumber());
			Intent reqIntent = new Intent(HolyCIntent.LalPermRequest.action);
			reqIntent.setPackage(context.getPackageName());
			reqIntent.putExtra(HolyCIntent.LalPermRequest.str_key, gson.toJson(pe, PermissionEnquiry.class));
			context.sendBroadcast(reqIntent);
			
			
			
		}else if(intent.getAction().equals(HolyCIntent.LalPermResponse.action)){
			//get Adam's response
			String response_json = intent.getStringExtra(HolyCIntent.LalPermResponse.str_key);
			PermissionResponse pr = gson.fromJson(response_json, PermissionResponse.class);
			OFMatch ofm = pr.getOFMatch();
			OFPacketIn opie = pr.getOFPacketIn();
			
			// Find outport if any
			Short out;
			if (HexString.toHexString(ofm.getDataLayerDestination()).equals(
					"ff:ff:ff:ff:ff:ff"))
				out = new Short(OFPort.OFPP_FLOOD.getValue());
			else
				out = hostPort.get(HexString.toHexString(ofm
						.getDataLayerDestination()));			
			// Send response
			Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
			poutIntent.setPackage(context.getPackageName());
			ByteBuffer bb = getResponse(out, opie, ofm, context);
			OFReplyEvent ofpoe = new OFReplyEvent(pr.getSocketChannelNumber(),
					bb.array());
			poutIntent.putExtra(HolyCIntent.BroadcastOFReply.str_key,
					gson.toJson(ofpoe, OFReplyEvent.class));
			context.sendBroadcast(poutIntent);
		}
	}
	public String getAppName(OFMatch ofm, Context context) {
		String remoteIP = "";
		int remotePort = 0;
		int localPort = 0;
		if (ofm.getInputPort() == LOCAL_PORT) {
			remoteIP = ipToString(ofm.getNetworkDestination());
			remotePort = U16.f(ofm.getTransportDestination());
			localPort = U16.f(ofm.getTransportSource());
		} else {
			remoteIP = ipToString(ofm.getNetworkSource());
			remotePort = U16.f(ofm.getTransportSource());
			localPort = U16.f(ofm.getTransportDestination());
		}

		// String appname = null;
		String appname = Utility.getPKGNameFromAddr(remoteIP, remotePort,
				localPort, context);
		if (appname == null)
			appname = "System/Unidentified App";		
		return appname;
	}
}
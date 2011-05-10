package edu.stanford.lal;


import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;

import com.google.gson.Gson;

import edu.stanford.lal.permission.PermissionInquiry;
import edu.stanford.lal.permission.PermissionResponse;
import net.holyc.HolyCIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;


public class LalPermTester extends Service implements Runnable {
	Gson gson = new Gson();
	String TAG = "HOLYC.LalPermTester";
	IntentFilter mIntentFilter;
	Thread mBroadcastThread; 
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(HolyCIntent.LalPermResponse.action);
		registerReceiver(mPermitReplyReceiver, mIntentFilter);
		startBroadcast();
		Log.d(TAG, "Tester got Created");
	}

	public void startBroadcast(){
		mBroadcastThread = new Thread(this);
		mBroadcastThread.start();
	}
	public void stopBroadcast(){		
		if (mBroadcastThread != null) {			
			mBroadcastThread.interrupt();
			mBroadcastThread = null;
		}
	}
	@Override
	public void onDestroy(){
		super.onDestroy();
		unregisterReceiver(mPermitReplyReceiver);
		stopBroadcast();
	}
	
	BroadcastReceiver mPermitReplyReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(HolyCIntent.LalPermResponse.action)){
				//get Adam's response
				String response_json = intent.getStringExtra(HolyCIntent.LalPermResponse.str_key);
				PermissionResponse pr = gson.fromJson(response_json, PermissionResponse.class);
				Log.d(TAG, pr.toString());
			}
		}
	};


	@Override
	public void run() {
		// TODO Auto-generated method stub
		int app_cookie = 12345;
		String app_name = "PermTester";
		OFMatch ofm = new OFMatch();
        // set ofm
		ofm.setNetworkProtocol((byte) 4);
        ofm.setWildcards(~OFMatch.OFPFW_NW_SRC_MASK);
        ofm.setNetworkSource(0x01010203);
        ofm.setNetworkDestination(0x02030101);
        ofm.setDataLayerDestination("00:17:42:EF:CD:01");
        ofm.setDataLayerSource("00:17:42:EF:CD:02");
        ofm.setInputPort((short)1);
        ofm.setTransportSource((short) 80);
        ofm.setTransportDestination((short) 6633);

        OFPacketIn opie = new OFPacketIn();        
        
		while(true){
			try {
				
				PermissionInquiry pi = new PermissionInquiry(ofm, opie, app_name, app_cookie, 12345);
				Intent reqIntent = new Intent(HolyCIntent.LalPermInquiry.action);
				reqIntent.setPackage(getPackageName());
				reqIntent.putExtra(HolyCIntent.LalPermInquiry.str_key, gson.toJson(pi, PermissionInquiry.class));
				sendBroadcast(reqIntent);
				Log.d(TAG, "send out Permission Inquiry: " + pi.toString());
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				break;
			}
		}
	}
}
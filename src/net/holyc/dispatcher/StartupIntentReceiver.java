package net.holyc.dispatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartupIntentReceiver extends BroadcastReceiver{

	private String TAG = "HOLYC.StartupIntentReceiver"; 
	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
        	Log.d(TAG, "boot completed and starting HolyC service");
        	Intent s=new Intent(context, DispatchService.class);  
        	context.startService(s);  
        }        
	}
}
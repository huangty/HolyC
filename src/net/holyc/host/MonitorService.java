package net.holyc.host;

import java.util.ArrayList;

import net.holyc.HolyCMessage;
import net.holyc.statusUI;
import net.holyc.host.EnvInitService.IncomingHandler;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Monitoring Service to check the following:
 * 1. The Routing Table (see if the 3G is added as default again) => how to retrieve routing table? popen?
 * 2. The status of interfaces ? 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 *
 */

public class MonitorService extends Service implements Runnable{
	String TAG = "HOLYC.Monitor";
	/** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
       
    /** Handler of incoming messages from clients (Activities). */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
	        case HolyCMessage.MONITOR_REGISTER.type:
                    mClients.add(msg.replyTo);
                    break;
                case HolyCMessage.MONITOR_UNREGISTER.type:
                    mClients.remove(msg.replyTo);
                    break;                                   
                case HolyCMessage.MONITOR_START.type:
                	sendReportToUI("Initiating the Monitor Service");
                	startMonitorThread();
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    }    
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    /** Send Message Back To statusUI */
    public void sendReportToUI(String str){
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.STATUSUI_REPORT_UPDATE.type);
            	Bundle data = new Bundle();
            	data.putString(HolyCMessage.STATUSUI_REPORT_UPDATE.str_key, 
			       str+"\n -------------------------------");
            	msg.setData(data);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
	@Override
	public IBinder onBind(Intent arg0) {		
		return mMessenger.getBinder();
	}
	
	public void startMonitorThread(){
		new Thread(this).start();
    	sendReportToUI("Start Monitor Daemon");  		
	}
	public void run() {
		/**
		 * @TODO start a thread to constantly monitor the network status of the host 
		 * */

		//check the status every 30 seconds?
		try {
			Thread.sleep(30);
		} catch (InterruptedException e) {
		}
	}
}
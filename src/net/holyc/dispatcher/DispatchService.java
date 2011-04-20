package net.holyc.dispatcher;

import java.util.ArrayList;

import com.google.gson.Gson;

import org.openflow.protocol.OFMessage;

import net.holyc.HolyCIntent;
import net.holyc.HolyCMessage;
import net.holyc.R;
import net.holyc.controlUI;
import net.holyc.statusUI;
import net.holyc.host.EnvInitService;
import net.holyc.ofcomm.OFCommService;
import net.holyc.ofcomm.OFMessageEvent;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;


public class DispatchService extends Service implements Runnable{
    private NotificationManager mNM;
    private String TAG = "HolyC.DispatcherService";
    private int bind_port = 6633;
    /** TODO: link this to UI */
    private boolean wifi_included = true;
    private boolean mobile_included = false;
    private static DispatchService sInstance = null;
    public static boolean isRunning = false;    
    public static DispatchService getInstance() { return sInstance; }
    private volatile Thread dispatchThread = null;
    private ArrayList<OFEvent> eventQueue = new ArrayList<OFEvent>();

    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    /** Messenger for communicating with service. */
    Messenger mOFService = null;
    Messenger mEnvService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsOFBound;
    boolean mIsEnvBound;
    Gson gson = new Gson();
    IntentFilter mIntentFilter;
    /* Service binding */
    BroadcastReceiver mOFReplyReceiver = new BroadcastReceiver() {
	    @Override
		public void onReceive(Context context, Intent intent) {
        	Log.d(TAG, "receive OFReply broadcast");
        	//just acting as a relay from OFHandler to OFComm
		String json = intent.getStringExtra(HolyCIntent.BroadcastOFReply.str_key);
		Message msg = Message.obtain(null, HolyCMessage.OFREPLY_EVENT.type);
		Bundle bundle = new Bundle();
		bundle.putString(HolyCMessage.OFREPLY_EVENT.str_key, json);
    	msg.setData(bundle);
    	
    	/** for debug */
		OFReplyEvent ofpoe = gson.fromJson(json, OFReplyEvent.class);
        Log.d(TAG, "receive broadcast in json = " + json);	
		Log.d(TAG, "receive OFEvent message = " + ofpoe.getOFMessage().toString() + 
		      "socket number = " + ofpoe.getSocketChannelNumber());
		
		try {
		    mOFService.send(msg);
		} catch (RemoteException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}
	    }
	};
    
    
    /** Handler of incoming messages from (Activities). */
    class IncomingHandler extends Handler {
        @Override
	    public void handleMessage(Message msg) {
            switch (msg.what) {
	    case HolyCMessage.REGISTER_CLIENT.type:
		mClients.add(msg.replyTo);
                    break;
	    case HolyCMessage.UNREGISTER_CLIENT.type:
		mClients.remove(msg.replyTo);
		break;
	    case HolyCMessage.UIREPORT_UPDATE.type:
		sendReportToControlUI(msg.getData().getString(HolyCMessage.UIREPORT_UPDATE.str_key));
		break;
	    case HolyCMessage.OFCOMM_EVENT.type:
		OFEvent de = (OFEvent) 
		    gson.fromJson(msg.getData().getString(HolyCMessage.OFCOMM_EVENT.str_key), 
				  OFEvent.class);                	
		synchronized(eventQueue){
		    eventQueue.add(de);
		    eventQueue.notify();
		    Log.d(TAG, "OF event enqueued, queue size = " + eventQueue.size());
		}
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
    
    /** Send Message Back To controlUI */
    public void sendReportToControlUI(String str){
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.DISPATCH_REPORT.type);
            	Bundle data = new Bundle();
            	data.putString(HolyCMessage.DISPATCH_REPORT.str_key, str);
            	msg.setData(data);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
    
    public int numBinder(){
    	return mClients.size();
    }
    
    @Override
	public IBinder onBind(Intent arg0) {		
	return mMessenger.getBinder();
    }
    
    
    @Override
	public void onCreate() {
    	isRunning = true;
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        sInstance = this;
        startForeground(0, null);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(HolyCIntent.BroadcastOFReply.action);
        registerReceiver(mOFReplyReceiver, mIntentFilter);
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        startDispatchThread();
        doBindServices();
    }
    
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    
    @Override
	public void onDestroy() {
    	super.onDestroy();
    	isRunning = false;
        // Cancel the persistent notification.
        mNM.cancel(R.string.dispatcher_started);
        stopDispatchThread();
        doUnBindServices();
        unregisterReceiver(mOFReplyReceiver);
        // Tell the user we stopped.
        Toast.makeText(this, "DispatchService Distroyed", Toast.LENGTH_SHORT).show();
    }
    
    public void startSelf() {
        startService(new Intent(this, DispatchService.class));
    }

    public synchronized void startDispatchThread(){
    	if(dispatchThread == null){
	    dispatchThread = new Thread(this);
	    dispatchThread.start();
    	}
    }
    
    public synchronized void stopDispatchThread(){
	if(dispatchThread != null){
    	    Thread moribund = dispatchThread;
    	    dispatchThread = null;
    	    moribund.interrupt();
	}
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        Log.d(TAG, "show notifications ");
        CharSequence text = getText(R.string.dispatcher_started);
	
        // Set the icon, scrolling text and timestamp for notification
        Notification notification = new Notification(R.drawable.icon, text, 
						     System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, 
								new Intent(this, controlUI.class), 
								0);
	
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.dispatcher_started),
					text, contentIntent);
	
        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.dispatcher_started, notification);
    }
    
    private void doBindServices(){
        doBindOFService();        
        doBindEnvService();        
    }
    
    private void doUnBindServices(){
    	doUnbindOFService();
    	doUnbindEnvService();
    }
    /** Bind with OFCommService **/
    void doBindOFService() {		
	Intent intent = new Intent(this, OFCommService.class);
	Bundle bundle = new Bundle();
		bundle.putInt("BIND_PORT", bind_port);
		intent.putExtras(bundle);
		bindService(intent, mOFConnection, Context.BIND_AUTO_CREATE);
		mIsOFBound = true;
		
    }
    void doUnbindOFService() {
	if (mIsOFBound) {
	    // Unregister ourselves from the service, since we unbind it.
	    if (mOFService != null) {
	            try {
	                Message msg = Message.obtain(null,
						     HolyCMessage.OFCOMM_UNREGISTER.type);
	                msg.replyTo = mMessenger;
	                mOFService.send(msg);
	            } catch (RemoteException e) {
	            }
	    }
	    // Detach our existing connection.
	    unbindService(mOFConnection);
	    mIsOFBound = false;
	}
    }
    private ServiceConnection mOFConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        mOFService = new Messenger(service);
	        Log.d(TAG, "OFComm Service Attached");
		
	        try {
		    // send the mMessenger to the Service and register itself
	            Message msg = Message.obtain(null,
						 HolyCMessage.OFCOMM_REGISTER.type);
	            msg.replyTo = mMessenger;
	            mOFService.send(msg);
	            
	            // send the bind port number to the service
	            msg = Message.obtain(null,
					 HolyCMessage.OFCOMM_START_OPENFLOWD.type, bind_port, 0);  
	            mOFService.send(msg);	            
	        } catch (RemoteException e) {
	        }
	    }
	    public void onServiceDisconnected(ComponentName className) {
	        mOFService = null;
	    }
	};
    /** Bind with EnvInitService **/
    void doBindEnvService(){
	Log.d(TAG, "Bind EnvInit Service");
	Intent intent = new Intent(this, EnvInitService.class);
		Bundle bundle = new Bundle();
		bundle.putBoolean("WIFI_INCLUDED", wifi_included);
		bundle.putBoolean("3G_INCLUDED", mobile_included);
		intent.putExtras(bundle);
		bindService(intent, mEnvConnection, Context.BIND_AUTO_CREATE);
		mIsEnvBound = true;	    
    }
	void doUnbindEnvService(){
	    if (mIsEnvBound) {
	        // Unregister ourselves from the service, since we unbind it.
	        if (mEnvService != null) {
	            try {
	                Message msg = Message.obtain(null,
						     HolyCMessage.ENV_INIT_UNREGISTER.type);
	                msg.replyTo = mMessenger;
	                mEnvService.send(msg);
	            } catch (RemoteException e) {
	            }
	        }
	        // Detach our existing connection.
	        unbindService(mEnvConnection);
	        mIsEnvBound = false;
	    }
	}
    private ServiceConnection mEnvConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        mEnvService = new Messenger(service);
	        Log.d(TAG, "EnvInit Service Attached");
		
	        try {
		    // send the mMessenger to the Service and register itself
	            Message msg = Message.obtain(null, HolyCMessage.ENV_INIT_REGISTER.type);
	            msg.replyTo = mMessenger;
	            mEnvService.send(msg);
	            
	            // send the bind port number to the service
	            int arg1 = 1;
	            int arg2 = 1;
	            if(wifi_included == false) arg1 = 0;
	            if(mobile_included == false) arg2 = 0;
	            msg = Message.obtain(null, HolyCMessage.ENV_INIT_START.type, arg1, arg2);
	            mEnvService.send(msg);	            
	        } catch (RemoteException e) {
	        }
		
	    }
	    public void onServiceDisconnected(ComponentName className) {
	        mEnvService = null;
	    }
	};
    
    /** Retrieve event from the eventQueue, and broadcast to OFEvent Handlers */
    @Override
	public void run() {
		OFEvent msgEvent;
		while(Thread.currentThread() == dispatchThread ){
		    synchronized(eventQueue) {
				while(eventQueue.isEmpty()) {
				    try {
					eventQueue.wait();
				    } catch (InterruptedException e) {
				    }
			    }
				msgEvent = (OFEvent) eventQueue.remove(0);
				Intent broadcastIntent = new Intent(HolyCIntent.BroadcastOFEvent.action);
				broadcastIntent.setPackage(getPackageName());
				String ofe_json = gson.toJson(msgEvent, OFEvent.class);
				broadcastIntent.putExtra(HolyCIntent.BroadcastOFEvent.str_key, ofe_json);			
				this.sendBroadcast(broadcastIntent);	
		    }
		}
    }	    
}
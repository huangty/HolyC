package edu.stanford.holyc;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import edu.stanford.holyc.host.EnvInitService;
import edu.stanford.holyc.jni.NativeCallWrapper;
import edu.stanford.holyc.ofcomm.OFCommService;

/**
 * The Status Report UI of the Controller
 * This Activity will be called from the entryUI
 * 
 * @author Te-Yuan Huang (huangty@stanford.edu)
 *
 */

public class statusUI extends Activity {
	private StringBuffer mBuffer = new StringBuffer();
	private TextView tview_report;
	private ScrollView sview_report;
	private int bind_port;
	private boolean wifi_included;
	private boolean mobile_included;
	private final static int REPORT_RECEIVED = 1;
	public static final int MSG_REPORT_UPDATE = 4;
	private String TAG="HOLYC";
	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	        switch (msg.what) {
	            case OFCommService.MSG_SET_VALUE:
	            	mBuffer.append("Received from service: "+ msg.arg1+"\n");
	                break;	
	            case MSG_REPORT_UPDATE:
	            	mBuffer.append(msg.getData().getString("MSG_REPORT_UPDATE")+"\n");
	            	break;
	            case REPORT_RECEIVED:
					mBuffer.append(msg.getData().getString("REPORT")+"\n");
	                break;
	            default:
	                super.handleMessage(msg);
	        }
	        doRedraw();
	    }
	}
	
	/** Handler for messages passed to show on the screen */
	Handler mHandler = new IncomingHandler();
	/** Messenger for communicating with service. */
	Messenger mOFService = null;
	Messenger mEnvService = null;
	Messenger mMonitorService = null;
	/** Flag indicating whether we have called bind on the service. */
	boolean mIsOFBound;
	boolean mIsEnvBound;
	boolean mIsMonitorBound;
	/** Target we publish for clients to send messages to IncomingHandler. */
	final Messenger mMessenger = new Messenger(mHandler);

	/**
	 * Class for interacting with the statusUI of the service (ofcmm).
	 * Using IDL to communicate between the activity and the service. 
	 * Called when the communication is established. 
	 */
	private ServiceConnection mOFConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        mOFService = new Messenger(service);
	        Log.d(TAG, "OFComm Service Attached");

	        try {
	        	// send the mMessenger to the Service and register itself
	            Message msg = Message.obtain(null,
	            		OFCommService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger;
	            mOFService.send(msg);
	            
	            // send the bind port number to the service
	            msg = Message.obtain(null,
	            		OFCommService.MSG_START_OPENFLOWD, bind_port, 0);	            
	            mOFService.send(msg);	            
	        } catch (RemoteException e) {
	        }

	        Toast.makeText(statusUI.this, R.string.openflow_channel_started, Toast.LENGTH_SHORT).show();
	    }
	    public void onServiceDisconnected(ComponentName className) {
	        mOFService = null;
	        reportToUI("OFComm Service Disconnected");
	        Toast.makeText(statusUI.this, R.string.openflow_channel_stopped, Toast.LENGTH_SHORT).show();
	    }
	};
	
	private ServiceConnection mEnvConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        mEnvService = new Messenger(service);
	        Log.d(TAG, "EnvInit Service Attached");

	        try {
	        	// send the mMessenger to the Service and register itself
	            Message msg = Message.obtain(null, EnvInitService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger;
	            mEnvService.send(msg);
	            
	            // send the bind port number to the service
	            int arg1 = 1;
	            int arg2 = 1;
	            if(wifi_included == false) arg1 = 0;
	            if(mobile_included == false) arg2 = 0;
	            msg = Message.obtain(null, EnvInitService.MSG_START_ENVINIT, arg1, arg2);	            
	            mEnvService.send(msg);	            
	        } catch (RemoteException e) {
	        }

	        Toast.makeText(statusUI.this, R.string.envinit_started, Toast.LENGTH_SHORT).show();
	    }
	    public void onServiceDisconnected(ComponentName className) {
	        mEnvService = null;
	        reportToUI("OFComm Service Disconnected");
	        Toast.makeText(statusUI.this, R.string.envinit_stopped, Toast.LENGTH_SHORT).show();
	    }
	};
	
	void reportToUI(String str){
		Message msg = Message.obtain(null, REPORT_RECEIVED);
	    Bundle data = new Bundle();
     	data.putString("REPORT", str);
     	msg.setData(data);            
        mHandler.sendMessage(msg);
	}
	/** Bind with OFCommService **/
	void doBindOFService() {		
		Intent intent = new Intent(statusUI.this, OFCommService.class);
		Bundle bundle = new Bundle();
		bundle.putInt("BIND_PORT", bind_port);
		intent.putExtras(bundle);
	    bindService(intent, mOFConnection, Context.BIND_AUTO_CREATE);
	    mIsOFBound = true;
	    
	}
	void doUnbindOFService() {
	    if (mIsOFBound) {
	    	reportToUI("Unbinding ofcomm service ... ");
	        // Unregister ourselves from the service, since we unbind it.
	        if (mOFService != null) {
	            try {
	                Message msg = Message.obtain(null,
	                		OFCommService.MSG_UNREGISTER_CLIENT);
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
	
	/** Bind with EnvInitService **/
	void doBindEnvService(){
		Log.d(TAG, "Bind EnvInit Service");
		Intent intent = new Intent(statusUI.this, EnvInitService.class);
		Bundle bundle = new Bundle();
		bundle.putBoolean("WIFI_INCLUDED", wifi_included);
		bundle.putBoolean("3G_INCLUDED", mobile_included);
		intent.putExtras(bundle);
	    bindService(intent, mEnvConnection, Context.BIND_AUTO_CREATE);
	    mIsEnvBound = true;	    
	}
	void doUnbindEnvService(){
		if (mIsEnvBound) {
			reportToUI("Unbinding EnvInit service ... ");
	        // Unregister ourselves from the service, since we unbind it.
	        if (mEnvService != null) {
	            try {
	                Message msg = Message.obtain(null,
	                		EnvInitService.MSG_UNREGISTER_CLIENT);
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
	/** Bind with Monitor Service */
	void doBindMonitorService(){
		
	}
	void doUnbindMonitorService(){
		
	}
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status);
        findViews();
        getBundleValues();
        doBindOFService();        
        doBindEnvService();        
    }
    private void findViews(){
    	tview_report = (TextView)findViewById(R.id.report);
    	sview_report = (ScrollView)findViewById(R.id.txt_scrollview);
    	
    }
    private void doRedraw(){
    	tview_report.setText(mBuffer.toString());
    	sview_report.scrollTo(0, tview_report.getHeight());
    	
    }
    private void getBundleValues(){
    	Bundle bundle = this.getIntent().getExtras();
    	bind_port = bundle.getInt("BIND_PORT");
    	wifi_included = bundle.getBoolean("WIFI_INCLUDED");
    	mobile_included = bundle.getBoolean("3G_INCLUDED");
    }
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    doUnbindOFService();
	    doUnbindEnvService();
	}

    
}

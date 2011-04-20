package net.holyc;

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.holyc.HolyCMessage;
import net.holyc.dispatcher.DispatchService;

public class controlUI extends Activity{
	private String TAG = "HolyC.controUI";
	private String dispatchServiceName;
	private StringBuffer mBuffer = new StringBuffer();
	private ToggleButton button_starter;
	private ToggleButton button_binder;	
	private TextView tview_dispatcher_report;
	private ScrollView sview_dispatcher_report;
	boolean mDispatchIsBound = false;
	Messenger mDispatchService = null;
	/** Handler for messages passed to show on the screen */
	Handler mHandler = new IncomingHandler();
	/** Target we publish for clients to send messages to IncomingHandler. */
	final Messenger mMessenger = new Messenger(mHandler);

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dispatchServiceName = this.getPackageName()+".dispatcher.DispatchService";
        setContentView(R.layout.control);
        findViews();
        setListeners();        
    }
    @Override
    public void onDestroy(){
    	super.onDestroy();
    	doUnbindDispatchService();
    }
    private void findViews(){
    	button_starter = (ToggleButton)findViewById(R.id.dispatcher_starter_button);
    	button_binder = (ToggleButton)findViewById(R.id.dispatcher_binder_button);    	
    	tview_dispatcher_report = (TextView)findViewById(R.id.dispatch_report_txtview);
    	sview_dispatcher_report = (ScrollView) findViewById(R.id.dispatch_scrollview);
    	button_binder.setTextOff(getText(R.string.dispatcher_unbinded));
    	button_binder.setTextOn(getText(R.string.dispatcher_binded));
    	button_starter.setTextOff(getText(R.string.dispatcher_stopped));
    	button_starter.setTextOn(getText(R.string.dispatcher_started));
    	if( mDispatchIsBound ){
    		button_binder.setChecked(true);
    	}else{
    		button_binder.setChecked(false);
    	}
    	
    	if( isDispatchServiceRunning() == false){ 
    		//no existing service is running
    		button_starter.setChecked(false);
    	}else{
    		button_starter.setChecked(true);
    	}
    }
    
    private void setListeners(){
    	button_starter.setOnClickListener(startDispatchService);
    	button_binder.setOnClickListener(bindDispatchService);
    }
    private boolean isDispatchServiceRunning(){
    	boolean isRunning = false;
    	clearTextView();    	
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<RunningServiceInfo> runningServices = activityManager.getRunningServices(50);

        for (int i = 0; i < runningServices.size(); i++) {
        	RunningServiceInfo runningServiceInfo = runningServices.get(i);
        	Log.d(TAG, "running service:" +runningServiceInfo.service.getClassName());
        	if(runningServiceInfo.service.getClassName().equals(dispatchServiceName)){
        		isRunning = true;
        		Log.d(TAG, "Dispatcher is running");
        		break;
        	}
        }
    	if( isRunning == false ){
    		mBuffer.append("Dispatch Service is NOT runnig ... \n");
    	}else{
    		mBuffer.append("Dispatch Service is running ... \n");
    	}    	
    	doRedraw();
    	return isRunning;
    }
    
    private Button.OnClickListener bindDispatchService = new Button.OnClickListener(){
    	public void onClick(View v){
    		if(((ToggleButton)v).isChecked()){    		
    			doBindDispatchService();    		
    		}else{
    			doUnbindDispatchService();
    		}    		
    	}
    };
    
    private Button.OnClickListener startDispatchService = new Button.OnClickListener(){
    	public void onClick(View v){
    		if(((ToggleButton)v).isChecked()){   
    			doStartDispatchService();
    		}else{
    			doStopDispatchService();
    		}    		
    	}
    };
    private ComponentName doStartDispatchService(){
    	ComponentName c = startService(new Intent(this, DispatchService.class));
    	button_starter.setChecked(true);
    	clearTextView(); 
    	mBuffer.append("Dispatch Service is running ... \n");
    	doRedraw();
    	return c;
    }
    private void doStopDispatchService(){
    	stopService(new Intent(this, DispatchService.class));
    	button_starter.setChecked(false);
    	clearTextView(); 
    	mBuffer.append("Dispatch Service is NOT running ... \n");
    	doRedraw();
    }
    /** Bind with DispatchService **/
	void doBindDispatchService() {		
		Intent intent = new Intent(controlUI.this, DispatchService.class);
		if(isDispatchServiceRunning() ){
			//if the service is not running
			doStartDispatchService();
		}
	    bindService(intent, mDispatchConnection, 0);
	    mDispatchIsBound = true;
        button_binder.setChecked(true);
	    
	}
	void doUnbindDispatchService() {
	    if (mDispatchIsBound) {
	        // Unregister ourselves from the service, since we unbind it.
	        if (mDispatchService != null) {
	            try {
	                Message msg = Message.obtain(null,
	                		HolyCMessage.UNREGISTER_CLIENT.type);
	                msg.replyTo = mMessenger;
	                mDispatchService.send(msg);
	            } catch (RemoteException e) {}
	        }
	        // Detach our existing connection.
	        unbindService(mDispatchConnection);
	        mDispatchIsBound = false;
	        clearTextView();
	        button_binder.setChecked(false);
	    }
	}

    private void doRedraw(){
    	tview_dispatcher_report.setText(mBuffer.toString());
    	sview_dispatcher_report.scrollTo(0, tview_dispatcher_report.getHeight());    	
    }
    private void clearTextView(){
    	mBuffer.delete(0, mBuffer.length());
    	doRedraw();
    }

    /**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	        switch (msg.what) {
	            case HolyCMessage.DISPATCH_REPORT.type:
					mBuffer.append(msg.getData().getString(HolyCMessage.DISPATCH_REPORT.str_key)+"\n");
	                break;
	            default:
	                super.handleMessage(msg);
	        }
	        doRedraw();
	    }
	}
	
	private ServiceConnection mDispatchConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	    	//get the Messenger of the service
	    	mDispatchService = new Messenger(service);
	        Log.d(TAG, "Dispatch Service Attached");
	        try {
	        	//Send the mMessenger to the service and Register itself to the service
	            Message msg = Message.obtain(null, HolyCMessage.REGISTER_CLIENT.type);
	            msg.replyTo = mMessenger;
	            mDispatchService.send(msg);
	        } catch (RemoteException e) {}
	        Toast.makeText(controlUI.this, R.string.dispatcher_binded, Toast.LENGTH_SHORT).show();
	    }
	    public void onServiceDisconnected(ComponentName className) {
	    	mDispatchService = null;
	        Toast.makeText(controlUI.this, R.string.dispatcher_unbinded, Toast.LENGTH_SHORT).show();
	    }
	};
}

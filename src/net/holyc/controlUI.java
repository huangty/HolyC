package net.holyc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.U16;

import net.holyc.dispatcher.DispatchService;
import net.holyc.openflow.handler.OFMultipleInterfaceRoundRobin;
import net.holyc.sensors.handler.SensorHintService;
import net.holyc.host.EnvInitService;
import net.holyc.host.Utility;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class controlUI extends Activity{
	private String TAG = "HOLYC.controUI";
	private String dispatchServiceName;
	private StringBuffer mBuffer = new StringBuffer();
	private ToggleButton button_starter;
	private ToggleButton button_binder;	
	public static CheckBox checkbox_wifi;
	public static CheckBox checkbox_wimax;
	public static CheckBox checkbox_3g;
	public static String interface_just_disabled ="";
	public static String interface_just_enabled = "";
	public static CheckBox checkbox_notifyMB;
	public static CheckBox checkbox_fwdMB;
	public static CheckBox checkbox_sensor;
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
        
        //Log.d(TAG, "start Testeer");
        //startService(new Intent(this, LalPermTester.class));
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
    	checkbox_wifi = (CheckBox) findViewById(R.id.wifi_cb);        	
    	checkbox_wimax = (CheckBox) findViewById(R.id.wimax_cb);    	
    	checkbox_3g = (CheckBox) findViewById(R.id.threeg_cb);
    	checkbox_notifyMB = (CheckBox) findViewById(R.id.msg_mb_cb);
    	checkbox_fwdMB = (CheckBox) findViewById(R.id.fwd_mb_cb);	
    	checkbox_sensor = (CheckBox) findViewById(R.id.sensor_cb);

    	checkbox_wifi.setChecked(false);
    	checkbox_wimax.setChecked(false);
    	checkbox_3g.setChecked(false);
    	checkbox_notifyMB.setChecked(false);
    	checkbox_fwdMB.setChecked(false);
    	checkbox_sensor.setChecked(false);
    	
    	/*will be enabled after the environment is setup*/
    	checkbox_wifi.setClickable(false);
    	checkbox_wimax.setClickable(false);
    	checkbox_3g.setClickable(false);
    	checkbox_notifyMB.setClickable(false);
    	checkbox_fwdMB.setClickable(false);
    	checkbox_sensor.setClickable(false);
    	
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
    	checkbox_wifi.setOnCheckedChangeListener(interfaceListener);
    	checkbox_wifi.setTag(new String("wifi"));
    	checkbox_wimax.setOnCheckedChangeListener(interfaceListener);
    	checkbox_wimax.setTag(new String("wimax"));
    	checkbox_3g.setOnCheckedChangeListener(interfaceListener);
    	checkbox_3g.setTag(new String("3g"));
    	
    	checkbox_fwdMB.setOnCheckedChangeListener(middleboxListener);
    	checkbox_fwdMB.setTag("fwdTraffic");    	
    	checkbox_notifyMB.setOnCheckedChangeListener(middleboxListener);
        checkbox_notifyMB.setTag("notify");
        
        checkbox_sensor.setOnCheckedChangeListener(sensorboxListener);
        checkbox_sensor.setTag("sensorService");
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
    private CompoundButton.OnCheckedChangeListener interfaceListener = new CompoundButton.OnCheckedChangeListener(){
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {			
			String netinf = (String) buttonView.getTag();
			if(netinf.equals("wifi")){
				EnvInitService.wifi_included = isChecked;
				checkbox_wifi.setChecked(isChecked);
				Log.d(TAG, "wifi is " + isChecked);				
			}else if(netinf.equals("wimax")){
				EnvInitService.wimax_included = isChecked;
				checkbox_wimax.setChecked(isChecked);
				Log.d(TAG, "wimax is " + isChecked);
			}else if(netinf.equals("3g")){
				EnvInitService.mobile_included = isChecked;
				checkbox_3g.setChecked(isChecked);
			}				
			OFMultipleInterfaceRoundRobin.wifi_flow_count=0;
			OFMultipleInterfaceRoundRobin.wimax_flow_count=0;
			OFMultipleInterfaceRoundRobin.threeg_flow_count=0;
			
			if(isChecked){
				sendOFStatReq();
				controlUI.interface_just_enabled =  netinf;
				
			}else{
				sendOFStatReq();
				controlUI.interface_just_disabled = netinf;
				controlUI.interface_just_enabled =  "";
			}
			Log.d(TAG, "INSIDE interface Listener AND send OFStatRequest");
			//Log.d(TAG, "ALL FLOW COUNT GOES BACK TO ZERO!!! and send OFStatRequest");
						
			
		}    	
    };
    private CompoundButton.OnCheckedChangeListener middleboxListener = new CompoundButton.OnCheckedChangeListener(){
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {			
			
			String action = (String) buttonView.getTag();
			if(isChecked){				
				if(action.equals("notify")){
					sendOFStatReq();					
					checkbox_notifyMB.setChecked(isChecked);
					Log.d(TAG, "going to notify the middle box and the server");				
				}else if(action.equals("fwdTraffic")){
					sendOFStatReq();
					checkbox_fwdMB.setChecked(isChecked);
					Log.d(TAG, "going to forward the traffic to middle box " + isChecked);
				}			
			}else{
				if(action.equals("notify")){
					checkbox_notifyMB.setChecked(isChecked);
				}else if(action.equals("fwdTraffic")){
					checkbox_fwdMB.setChecked(isChecked);
				}
			}																
			Log.d(TAG, "send OFStatRequest");											
		}    	
    };
    
    private CompoundButton.OnCheckedChangeListener sensorboxListener = new CompoundButton.OnCheckedChangeListener(){
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {			
			
			String tag = (String) buttonView.getTag();
			if(tag.equals("sensorService")){
				if(isChecked){				
					doStartSensorHintService();
					Log.d(TAG, "start sensor service");
				}else{
					doStopSensorHintService();
					Log.d(TAG, "stop sensor service");
				}
			}																													
		}    	
    };
    
    private Button.OnClickListener bindDispatchService = new Button.OnClickListener(){
    	public void onClick(View v){
    		if(((ToggleButton)v).isChecked()){    		
    			doBindDispatchService(true);    		
    		}else{
    			doUnbindDispatchService();
    		}    		
    	}
    };
    
    private Button.OnClickListener startDispatchService = new Button.OnClickListener(){
    	public void onClick(View v){
    		//Utility.commandTest(controlUI.this);
    		if(((ToggleButton)v).isChecked()){   
    			doStartDispatchService();
    		}else{
    			doStopDispatchService();
    		}
    	}
    };
    public void sendOFStatReq(){
    	if(!mDispatchIsBound){							
			Log.d(TAG, "Turn on the binding, since we need to send some messages to the ovs");				
			doBindDispatchService(true);
		}
		Message msg = Message.obtain(null, HolyCMessage.OFCOMM_SEND_REQUEST_UI.type);
        msg.replyTo = mMessenger;
        OFStatisticsRequest ofsr = new OFStatisticsRequest();
		ofsr.setStatisticType(OFStatisticsType.FLOW);
				
		OFFlowStatisticsRequest offsr = new OFFlowStatisticsRequest();
        offsr.setTableId((byte)0xff);        
        OFMatch ofm = new OFMatch();
        ofm.setWildcards(OFMatch.OFPFW_ALL);
        offsr.setMatch(ofm);        
        offsr.setOutPort(OFPort.OFPP_NONE.getValue());
        
        List<OFStatistics> statistics = new ArrayList<OFStatistics>();
        statistics.add(offsr);
        ofsr.setStatistics(statistics);
        ofsr.setLengthU(U16.t(OFStatisticsRequest.MINIMUM_LENGTH + offsr.getLength()));
        
		ByteBuffer bb = ByteBuffer.allocate(ofsr.getLengthU());
		ofsr.writeTo(bb);
		Bundle data = new Bundle();
		data.putByteArray(HolyCMessage.OFCOMM_SEND_REQUEST_UI.data_key, bb.array());
		msg.setData(data);
		sendMessageToDispatchService(msg);
    }
    
    public void sendMessageToDispatchService(Message msg){
    	if(mDispatchService != null){
	    	try {
	    		mDispatchService.send(msg);
	    	}catch(RemoteException e) {}
    	}else{
    		doBindDispatchService(true);
    	}
    }
    private ComponentName doStartDispatchService(){
    	ComponentName c = startService(new Intent(this, DispatchService.class));
    	button_starter.setChecked(true);
    	clearTextView(); 
    	mBuffer.append("Dispatch Service is running ... \n");
    	doRedraw();
    	doBindDispatchService(false);

    	return c;
    }
    private void doStopDispatchService(){
    	stopService(new Intent(this, DispatchService.class));
    	button_starter.setChecked(false);
    	clearTextView(); 
    	mBuffer.append("Dispatch Service is NOT running ... \n");
    	doRedraw();
    	doUnbindDispatchService();
    }
    /** Bind with DispatchService **/
	void doBindDispatchService(boolean check) {		
		Intent intent = new Intent(controlUI.this, DispatchService.class);
		if(check && isDispatchServiceRunning() == false ){
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

	private void doStartSensorHintService(){
    	/*ComponentName c = startService(new Intent(this, SensorHintService.class));	    		    	
    	mBuffer.append("SensorHintService is running ... \n");
    	doRedraw();	    	

    	return c;*/
		
		if(!mDispatchIsBound){							
			Log.d(TAG, "Turn on the binding, since we need to send some messages to dispatch service");				
			doBindDispatchService(true);
		}
		Message msg = Message.obtain(null, HolyCMessage.SENSORHINT_START.type);
        msg.replyTo = mMessenger;
        
		sendMessageToDispatchService(msg);
		
    }
    private void doStopSensorHintService(){
    	/*stopService(new Intent(this, SensorHintService.class));	    		    
    	mBuffer.append("SensorHintService is NOT running ... \n");
    	doRedraw();*/	    	
    	if(!mDispatchIsBound){							
			Log.d(TAG, "Turn on the binding, since we need to send some messages to dispatch service");				
			doBindDispatchService(true);
		}
		Message msg = Message.obtain(null, HolyCMessage.SENSORHINT_STOP.type);
        msg.replyTo = mMessenger;
        
		sendMessageToDispatchService(msg);
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
					//Log.d(TAG, "get report from dispatch service: " + msg.getData().getString(HolyCMessage.DISPATCH_REPORT.str_key));
					doRedraw();
	                break;
	            default:
	                super.handleMessage(msg);
	        }
	        
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

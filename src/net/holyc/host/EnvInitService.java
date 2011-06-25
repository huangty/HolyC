package net.holyc.host;

import java.util.ArrayList;

import net.holyc.HolyCMessage;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * To Initiate Environment Setup, including
 * 1. Get Environment Variables
 *    veth0:  mac
 *    rmnet0: mac, ip
 *    wifi: mac, ip
 *    gateway(wifi, 3g): mac, ip
 * 2. OVS setups
 * 3. initiate openflowd
 * 4. Routing table setup
 *
 *
 * @author Te-Yuan Huang (huangty@stanford.edu)
 * @author Yongqiang Liu (yliu78@stanford.edu)
 */


public class EnvInitService extends Service {//implements Runnable{

	String TAG = "HOLYC.EnvInit";	
	
	/** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    public static boolean wifi_included;
	public static boolean mobile_included;
	private boolean isMultipleInterface;
	private Thread monitorThread = null;
	/** The interfaces in the host*/
	private VirtualInterfacePair vIFs = null;
	public static ThreeGInterface threeGIF = null;
	public static WifiInterface wifiIF = null;
	private HostInterface wifiGW = null;
	private HostInterface threeGGW= null;
	private VirtualSwitch ovs = null;
	private ConnectivityManager mConnectivityManager = null; 
    private NetworkInfo wifiInfo = null;
    private NetworkInfo mobileInfo = null;
    private boolean isOVSsetup = false;
    private boolean isOpenflowdSetup = false;
    
	
    /** Handler of incoming messages from clients (Activities). */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HolyCMessage.ENV_INIT_REGISTER.type:
                    mClients.add(msg.replyTo);
                    break;
                case HolyCMessage.ENV_INIT_UNREGISTER.type:
                    mClients.remove(msg.replyTo);
                    break;                                   
                case HolyCMessage.ENV_INIT_START.type:
            		wifi_included = true;
            		mobile_included = true;
                	if(msg.arg1 == 0){
                		wifi_included = false;
                	}                	
                	if(msg.arg2 == 0){
                		mobile_included = false;
                	}                	
                	isMultipleInterface = wifi_included & mobile_included;
                	//sendReportToUI("Initiating the environment with WiFi: " + wifi_included + " and 3G: " + mobile_included);
                	Log.d(TAG, "Initiating the environment with WiFi: " + wifi_included + " and 3G: " + mobile_included);
                	doEnvInit();
                	
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    }    
    
    /**
     * Broadcast Receiver to receive status changes for WiFi
     */
    private BroadcastReceiver mNetworkInfoReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			   NetworkInfo networkInfo = (NetworkInfo) arg1.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			   
			   if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
				   wifiInfo = networkInfo;
				   Log.d(TAG, "Broadcast Receiver WiFi: " + wifiInfo.toString());
				   if(wifiInfo.isConnected()){ //connected to a different ap
					   sendReportToUI("WiFi: a new connection");
					   //doEnvCleanup();
					   doEnvInit();
				   }else if (wifiInfo.isConnectedOrConnecting()){ //is connecting
					   sendReportToUI("WiFi: is connecting ... ");
				   }else{ //currently disconnected
					   sendReportToUI("WiFi: disconnected from the network");
					   doEnvCleanup();					   
				   }
			   }else if(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
				   mobileInfo = networkInfo;
				   Log.d(TAG, "delete 3G route " + threeGIF.getName());
				   //Utility.runRootCommand("ip route del dev "+ threeGIF.getName(), false);
				   Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ threeGIF.getName(), false);
				   //TODO: for ppp0, need to reset configure environment
			   }else{
				   Log.d(TAG, "Broadcast Receiver: " + networkInfo.toString());
			   }
		}    	
    };
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    /** Send Message Back To UI */
    public void sendReportToUI(String str){
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.UIREPORT_UPDATE.type);
            	Bundle data = new Bundle();
            	data.putString(HolyCMessage.UIREPORT_UPDATE.str_key, str+"\n -------------------------------");
            	msg.setData(data);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
    
    
    
    public void doEnvInit(){
    	threeGIF = new ThreeGInterface(this);
    	wifiIF = new WifiInterface(this);
    	mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	//WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);    	
    	
        wifiInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mobileInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        // print info
        Log.d(TAG, "Init : WiFi Info: " + wifiInfo.toString());
        Log.d(TAG, "Init : 3G Info:" + mobileInfo.toString());
        Log.d(TAG, "interfaces in the system " + android.os.Build.DEVICE);

        if(wifiInfo.isConnected()){
        	wifi_included = true;
        }else{
        	wifi_included = false;
        }
    	
        if(mobileInfo.isConnected()){
        	mobile_included = true;        
        }else{
        	mobile_included = false;
        }
        
        if(wifi_included == false && mobile_included == false){
        	//stopController();
        	Log.d(TAG, "NO connection available");
        	sendReportToUI("NO connection available, abort!");
        	return;
        }
        
    	if(wifi_included){
    		doWiFiInit();
    	}
    	if(mobile_included){
    		doMobileInit();
    	}
    	doVethInit();
    	if(isOVSsetup == false){
    		doOVSInit();
    		isOVSsetup = true;
    	}
    	if(isOpenflowdSetup == false){
    		doOpenflowdInit();
    		isOpenflowdSetup = true;
    	}
    	doRoutingInit();
    	//startMonitorThread();    	
    	sendReportToUI("Setup the Environment successfully");
    	/**
    	 * TODO: Notify the statusUI that environment initiation is finished, time to start the Monitor service
    	 */
    }
    
    public void doWiFiInit(){
    	wifiIF.setInterfaceEnable(wifi_included);
    	wifiGW = wifiIF.getGateway();
    	
    	/** debug messages*/
    	Log.d(TAG, "wifi Name is " + wifiIF.getName());
    	Log.d(TAG, "wifi IP is " + wifiIF.getIP());
    	Log.d(TAG, "wifi mac is " + wifiIF.getMac());
    	//sendReportToUI("wifi Name is " + wifiIF.getName());
    	//sendReportToUI("wifi IP is " + wifiIF.getIP());
    	//sendReportToUI("wifi mac is " + wifiIF.getMac());
    	if(wifiGW != null){
    		Log.d(TAG, "wifi gw IP is " + wifiGW.getIP());
    		Log.d(TAG, "wifi gw mac is " + wifiGW.getMac());    	
    		//sendReportToUI("wifi gw IP is " + wifiGW.getIP());
    		//sendReportToUI("wifi gw mac is " + wifiGW.getMac());
    	}
    }
    
    public void doMobileInit(){
    	/** Need to compile with cyanogenmod to make this work*/
    	//threeGIF.setInterfaceEnable(mobile_included);
    	threeGGW = threeGIF.getGateway();
    	
    	/** debug messages*/
    	Log.d(TAG, "3G name is " + threeGIF.getName());
    	Log.d(TAG, "3G IP is " + threeGIF.getIP());
    	Log.d(TAG, "3G Mask is " + threeGIF.getMask());
    	Log.d(TAG, "3G Mac is " + threeGIF.getMac());
    	Log.d(TAG, "3G GW IP is " + threeGGW.getIP());
    	Log.d(TAG, "3G GW Mac is " + threeGGW.getMac());
    	//sendReportToUI("3G name is " + threeGIF.getName());
    	//sendReportToUI("3G IP is " + threeGIF.getIP());
    	//sendReportToUI("3G Mask is " + threeGIF.getMask());
    	//sendReportToUI("3G Mac is " + threeGIF.getMac());
    	//sendReportToUI("3G GW IP is " + threeGGW.getIP());
    	//sendReportToUI("3G GW Mac is " + threeGGW.getMac());
    }
    
    public void doVethInit(){    	
    	/** 1. ip link add type veth (init)
    	 *  2. set the IP address and mask  */
    	
    	vIFs = new VirtualInterfacePair();    
    	if(isMultipleInterface){ 
    		//need to set IP to veth1 only when it's multiple interfaces
	    	if( !wifiIF.getIP().startsWith("192.168") && !threeGIF.getIP().startsWith("192.168")){
	    		vIFs.setIP("veth1", "192.168.0.2", "255.255.255.0");
	    	}else if(!wifiIF.getIP().startsWith("10.38") && !threeGIF.getIP().startsWith("10.38")){
	    		vIFs.setIP("veth1", "10.38.0.2", "255.255.255.0");
	    	}else{
	    		Log.e(TAG, "Couldn't find an IP to initilize virtual interface");
	    	}
    	}else{
    		//otherwise, just set veth1' mac/ip address as wifi or 3G
    		if(wifi_included){
    			vIFs.getVeth1().setIP(wifiIF.getIP(), wifiIF.getMask());
    			vIFs.getVeth1().setMac(wifiIF.getMac());    			
    			wifiIF.removeIP();
    		}else if(mobile_included){
    			vIFs.getVeth1().setIP(threeGIF.getIP(), threeGIF.getMask());
    			vIFs.getVeth1().setMac(threeGIF.getMac());
    			if (threeGIF.getName().equals("rmnet0")) {
    				threeGIF.removeIP();
    			}
    		}else{
    			Log.e(TAG, "no interface is enabled");
    		}
    	}
    	
    	/** debug messages*/
    	Log.d(TAG, "veth name is " + vIFs.getNames());
    	Log.d(TAG, "veth IP is " + vIFs.getIPs());
    	Log.d(TAG, "veth Mac is " + vIFs.getMacs());    	
    	//sendReportToUI("veth name is " + vIFs.getNames());
    	//sendReportToUI("veth IP is " + vIFs.getIPs());
    	//sendReportToUI("veth Mac is " + vIFs.getMacs());
    }
    
    /**
     * @TODO: device name should be variables
     */
    public void doOVSInit(){
    	/**
    	 * 1. init: load openflowvswitch_mod.ko
    	 * 2. add datapath
    	 * 3. add interfaces     	
    	 * */
    	ovs = new VirtualSwitch();
    	ovs.addDP("dp0");
    	ovs.addIF("dp0", vIFs.getVeth0().getName());
    	if(wifi_included){
    		ovs.addIF("dp0", wifiIF.getName());
    	}
    	if(mobile_included){
    		if (threeGIF.getName().equals("ppp0")) {
    			ArrayList<String> resList = Utility.runRootCommand("/data/local/bin/busybox ip link | grep veth2", true);
    			if (resList.size() == 0) {
    				Utility.runRootCommand("/data/local/bin/busybox ip link add type veth", false);
    				Utility.runRootCommand("/data/local/bin/busybox ifconfig veth2 up", false);
    				Utility.runRootCommand("/data/local/bin/busybox ifconfig veth3 up", false);
    				ovs.addIF("dp0", "veth2");
    			}
    		} else {
    			ovs.addIF("dp0", threeGIF.getName());	
    		}
    	}
    	/** debug messages*/
    	//sendReportToUI("Setup OVS");
    	Log.d(TAG, "OVS setuped");
    }         
        
    public void doOpenflowdInit(){    	    	
    	Utility.runRootCommand("/data/local/bin/ovs-openflowd "+ ovs.getDP(0) +" tcp:127.0.0.1 " + 
    			"--out-of-band --monitor --detach --log-file=/sdcard/Android/data/net.holyc/files/ovs.log", false);
    	Log.d(TAG, "Openflowd is running in detached mode");
    	/**
    	 * @TODO: How to retrieve the output of openflowd? (do we want to have it in logcat?)
    	 */
    	/** debug messages*/
    	//sendReportToUI("Please Setup Openflowd By Hand, go to adb shell; /data/local/bin/ovs-openflowd dp0 tcp:127.0.0.1 --out-of-band");
    	//sendReportToUI("Openflowd is running in detached mode");
    }
       
    public void doRoutingInit(){
    	/** remove other default route */		
		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ wifiIF.getName(), false);
		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ threeGIF.getName(), false);

    	if(isMultipleInterface){
    		Utility.runRootCommand("/data/local/bin/busybox route add default dev " + vIFs.getVeth1().getName(), false);
    	}else{ //single interface
    		if(wifi_included){
        		Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
        		Log.d(TAG, "Add default gw:" + "route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName());
    		}else if(mobile_included){
    			if (threeGIF.getName().equals("ppp0")) {
    				Utility.runRootCommand("/data/local/bin/busybox route add -net " 
    						+ threeGGW.getIP() + " netmask 255.255.255.255 veth1", false);
    				Utility.runRootCommand("/data/local/bin/busybox route del -net " 
    						+ threeGGW.getIP() + " netmask 255.255.255.255 ppp0", false);
    				Utility.runRootCommand("/data/local/bin/busybox route add default dev veth1", false);
    				Utility.runRootCommand("/data/local/bin/busybox route del default dev ppp0", false);
    			} else {
    				Utility.runRootCommand("/data/local/bin/busybox route add default gw " + threeGGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
    			}
    		}
    	}
    	Log.d(TAG, "Routing setuped");
    }
    
    public void doEnvCleanup(){    	
    	Utility.runRootCommand("killall -9 ovs-openflowd", false);
    	if(wifiIF != null){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + wifiIF.getName(), false);
    	}
    	if(vIFs != null){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + vIFs.getVeth0().getName(), false);
    	}
		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-dp dp0", false);
		Utility.runRootCommand("rmmod openvswitch_mod", false);		
		Utility.runRootCommand("/data/local/bin/busybox ip link del veth0", false);
		Utility.runRootCommand("/data/local/bin/busybox ip link del veth2", false);
		isOVSsetup = false;
		isOpenflowdSetup = false;
		sendReportToUI("Clean up the environment");
		//stopMonitorThread();
    }
	@Override
	public IBinder onBind(Intent arg0) {
		this.registerReceiver(this.mNetworkInfoReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		return mMessenger.getBinder();		
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		doEnvCleanup();
		Log.d(TAG, "cleanup the environment ");		
		unregisterReceiver(this.mNetworkInfoReceiver);
	}
	public void startMonitorThread(){
		//monitorThread = new Thread(this);  
		//monitorThread.start();
	}
	public void stopMonitorThread(){
   	 /*if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }*/
   }    
	/*public void run() {
		//check the status every 30 seconds?
		try {
			while(true){
				Thread.sleep(30000);				
				if(threeGIF!=null){									
					//Utility.runRootCommand("ip route del dev "+ threeGIF.getName(), false);
					//Log.d(TAG, "delete 3G route " + threeGIF.getName());
				}
			}
		} catch (InterruptedException e) {
		}
	}*/
}
package net.holyc.host;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;

import net.beaconcontroller.packet.IPv4;
import net.holyc.HolyCMessage;
import net.holyc.controlUI;
import net.holyc.ofcomm.OFCommService;
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
	boolean DEBUG = true;
	/** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    public static boolean wifi_included = true;
	public static boolean mobile_included = true;
	public static boolean wimax_included = true;
	public static boolean initializedByDispather = false;
	public static boolean isMultipleInterface = true;
	/** The interfaces in the host*/
	public static VirtualInterfacePair vIFs = null;
	public static ThreeGInterface threeGIF = null;
	public static WifiInterface wifiIF = null;
	public static WimaxInterface wimaxIF = null;
	public static VirtualSwitch ovs_beforeSwitch = null;
	public static VirtualSwitch ovs = null;
	public static HostInterface vethGW = null;
	private HostInterface wifiGW = null;
	private HostInterface threeGGW= null;
	private HostInterface wimaxGW = null;
	private ConnectivityManager mConnectivityManager = null; 
    private NetworkInfo wifiInfo = null;
    private NetworkInfo mobileInfo = null;
    private NetworkInfo wimaxInfo = null;
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
                    doEnvCleanup();
                	initializedByDispather = false;                	
                    break;                                   
                case HolyCMessage.ENV_INIT_START.type:
                	if(ovs != null){
                		doEnvCleanup();
                	}
                	doEnvInit();
                	initializedByDispather = true;
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
			   if(initializedByDispather == false){ 
				   //since the dispatch service has not start the EnvService yet, wait!
				   Log.d(TAG, "got broadcast intent but should not initialize");
				   return;
			   }
			   if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
				   wifiInfo = networkInfo;
				   Log.d(TAG, "Broadcast Receiver WiFi: " + wifiInfo.toString());
				   if(wifiInfo.isConnected()){ //connected to a different ap
					   sendReportToUI("WiFi: a new connection");
					   Log.d(TAG, "WiFi connected, setting up environment");
					   doEnvInit();					   
				   }else if(wifiInfo.isConnectedOrConnecting()){
					   //do nothing
				   }else{ //currently disconnected
					   sendReportToUI("WiFi: disconnected from the network");
					   //doWiFiCleanup();
					   /*set these as false, so that when the next time we do EnvInit, they would reset the rule and sort out OVS interfaces*/
					   isOpenflowdSetup = false;
					   isOVSsetup = false;					   				
				   }
			   }else if(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
				   mobileInfo = networkInfo;				   	  
				   Log.d(TAG, "Broadcast Receiver 3G: " + mobileInfo.toString());
				   //doEnvInit();
				   if(mobileInfo.isConnected()){
					   //Log.d(TAG, "3G is connected");
					   sendReportToUI("3G: connected");
					   doEnvInit();					   	   					   
				   }else if(mobileInfo.isConnectedOrConnecting()){
					   //do nothing
				   }else{ 
					   Log.d(TAG, "3G is disconnected");
					   sendReportToUI("3G: failing over ...");
					   doMobileCleanup();
					   /*set these as false, so that when the next time we do EnvInit, they would reset the rule and sort out OVS interfaces*/
					   isOpenflowdSetup = false;
					   isOVSsetup = false;
				   }
				   //TODO: for ppp0, need to reset configure environment
			   }else if(networkInfo.getType() == ConnectivityManager.TYPE_WIMAX){
				   wimaxInfo = networkInfo;
				   Log.d(TAG, "Broadcast Receiver WiMAX: " + networkInfo.toString());
				   if(wimaxInfo.isConnected()){
					   sendReportToUI("WiMAX: connected");
					   doEnvInit();
				   }else{
					   Log.d(TAG, "WiMAX is disconnected");
					   sendReportToUI("WIMAX: got disconnected ...");
					   doWimaxCleanup();
					   /*set these as false, so that when the next time we do EnvInit, they would reset the rule and sort out OVS interfaces*/
					   isOpenflowdSetup = false;
					   isOVSsetup = false;
				   }
				   
			   }else{
				   Log.d(TAG, "Broadcast Receiver ELSE: " + networkInfo.toString());
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
    
    public void sendEnvFinishNotification(){
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.ENV_INIT_FINISH.type);            	
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
    
    public void doEnvInit(){
    	Log.d(TAG, "Start initialize Environment for the host with ips = " + Utility.getLocalIpAddress());
    	mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	//WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);    	
    	
        wifiInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mobileInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        wimaxInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);
        // print info
        //Log.d(TAG, "Init : WiFi Info: " + wifiInfo.toString());
        //Log.d(TAG, "Init : 3G Info:" + mobileInfo.toString());
        //Log.d(TAG, "interfaces in the system " + android.os.Build.DEVICE);

        if(wifiInfo.isConnected()){
        	wifi_included = true;
        	if(wifiIF == null){
        		wifiIF = new WifiInterface(this);
        	}else{
        		WifiInterface newWifi = new WifiInterface(this);
        		if(newWifi.getIP() != null){
        			wifiIF = newWifi;
        		}
        	}        	
        }else{
        	wifi_included = false;
        }
    	
        if(mobileInfo.isConnected()){
        	mobile_included = true;        
        	if(threeGIF == null){
        		threeGIF = new ThreeGInterface(this);
        	}else{
        		ThreeGInterface new3g = new ThreeGInterface(this);
        		if(new3g.getIP() != null){
        			threeGIF = new3g;
        		}
        	}
        }else{
        	mobile_included = false;
        }
        
        if(wimaxInfo.isConnected()){
        	wimax_included = true;
        	if(wimaxIF == null){
        		wimaxIF = new WimaxInterface(this);
        	}else{
        		WimaxInterface newWimax = new WimaxInterface(this);
        		if(newWimax.getIP() != null){
        			wimaxIF = newWimax;
        		}
        	}
        }else{
        	wimax_included = false;
        }
        
        controlUI.checkbox_wifi.setClickable(wifi_included);
        controlUI.checkbox_wifi.setChecked(wifi_included);
        controlUI.checkbox_wimax.setClickable(wimax_included);
        controlUI.checkbox_wimax.setChecked(wimax_included);
        controlUI.checkbox_3g.setClickable(mobile_included);
        controlUI.checkbox_3g.setChecked(mobile_included);
        controlUI.checkbox_fwdMB.setClickable(true);
        controlUI.checkbox_notifyMB.setClickable(true);
        controlUI.checkbox_fwdMB.setChecked(false);
        controlUI.checkbox_notifyMB.setChecked(false);
        controlUI.checkbox_sensor.setClickable(true);
        controlUI.checkbox_sensor.setChecked(true);
        
        Log.d(TAG, "wifi: " + wifi_included + ", 3g: " + mobile_included + ", wimax: " + wimax_included);
        
        if(isMultipleInterface == false){
        	if(wifi_included == true){
        		mobile_included = false;
        		wimax_included = false;
        	}
        }
        
        if(wifi_included == false && mobile_included == false && wimax_included == false){
        	//stopController();
        	Log.d(TAG, "NO connection available");
        	sendReportToUI("NO connection available, abort!");
        	return;
        }else if(wifi_included == true && mobile_included == false && wimax_included == false){
        	Log.d(TAG, "Using WiFi");
        	sendReportToUI("Using WiFi");
        }else if(wifi_included == false && mobile_included == true && wimax_included == false){
        	Log.d(TAG, "Using 3G");
        	sendReportToUI("Using 3G");
        }else if(wifi_included == true && mobile_included == true && wimax_included == false){
        	Log.d(TAG, "Using Both WiFi and 3G");
        	sendReportToUI("Using Both WiFi and 3G");
        }else if(wifi_included == false && mobile_included == false && wimax_included == true){        	
        	Log.d(TAG, "Using WiMAX");
        	sendReportToUI("Using WiMAX");
        	return;
        }else if(wifi_included == true && mobile_included == false && wimax_included == true){
        	Log.d(TAG, "Using both WiFi and WiMAX");
        	sendReportToUI("Using both WiFi and WiMAX");
        }else if(wifi_included == false && mobile_included == true && wimax_included == true){
        	Log.d(TAG, "Using both 3G and WiMAX");
        	sendReportToUI("Using both 3G and WiMAX");
        }else if(wifi_included == true && mobile_included == true && wimax_included == true){
        	Log.d(TAG, "Using WiFi, 3G and WiMAX");
        	sendReportToUI("Using WiFi, 3G and WiMAX");
        }        	                      
        
    	if(wifi_included){
    		doWiFiInit();
    	}
    	if(mobile_included){
    		doMobileInit();    		
    	}
    	if(wimax_included){
    		doWimaxInit();
    	}
    	
    	if((wifi_included && wifiIF.getIP() == null) || (mobile_included && threeGIF.getIP() == null) || (wimax_included && wimaxIF.getIP() == null)){    		
    		Log.d(TAG, "NO IP on interface");
        	sendReportToUI("NO IP on interface, abort!");
        	return;
    	}
    	
    	doVethInit();
    	//doOVSInit();
    	//doOpenflowdInit();
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
    	Log.d(TAG, "Tell Dispatch service that Environment is finished setup");
    	sendEnvFinishNotification();
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
    	//Utility.runRootCommand("/data/local/bin/busybox ifconfig " + wifiIF.getName() + " 0.0.0.0", false);
    }
    
    public void doMobileInit(){
    	/** Need to compile with cyanogenmod to make this work*/
    	//threeGIF.setInterfaceEnable(mobile_included);
    	threeGGW = threeGIF.getGateway();
    	
    	if(threeGIF.isPointToPoint()){
    		ArrayList<String> resList = Utility.runRootCommand("/data/local/bin/busybox ip link | /data/local/bin/busybox grep veth2", true);
			while (resList.size() == 0) {
				Utility.runRootCommand("/data/local/bin/busybox ip link add type veth", false);
				resList = Utility.runRootCommand("/data/local/bin/busybox ip link | /data/local/bin/busybox grep veth2", true);								
			}			
			threeGIF.veth2 = new VirtualInterface("veth2");
			threeGIF.veth2.setInterfaceEnable(true);
			threeGIF.veth3 = new VirtualInterface("veth3");
			threeGIF.veth3.setInterfaceEnable(true);
			threeGIF.setMac(threeGIF.veth2.getMac());
			threeGIF.veth2.setIP(threeGIF.getIP(), threeGIF.getMask());
			threeGIF.veth2.setMask(threeGIF.getMask());
			//threeGIF.setIP("0.0.0.0");
			
			Utility.runRootCommand("/data/local/bin/busybox ifconfig " + threeGIF.veth2.getName() + " 0.0.0.0", false);
			/*/data/local/bin/busybox ip route del dev rmnet0
			/data/local/bin/busybox route del -net 21.217.22.0 netmask 255.255.255.0 rmnet0
			/data/local/bin/busybox ifconfig veth2 21.217.22.107 netmask 255.255.255.0
			/data/local/bin/busybox route del -net 21.217.22.0 netmask 255.255.255.0 veth2
			/data/local/bin/busybox route add default dev veth2
			/data/local/bin/wrapper rmnet0 veth3 21.217.22.107		
			 */
				    	
    	}    	
    	
    	/** debug messages*/
    	if(threeGIF.isPointToPoint()){
    		Log.d(TAG, "3G wrapper name is " + threeGIF.veth2.getName());
	    	Log.d(TAG, "3G wrapper IP is " + threeGIF.veth2.getIP());    
	    	Log.d(TAG, "3G wrapper Mask is " + threeGIF.veth2.getMask());
	    	Log.d(TAG, "3G wrapper Mac is " + threeGIF.veth2.getMac());
    	}else{
    		Log.d(TAG, "3G IP is " + threeGIF.getIP());
    		Log.d(TAG, "3G Mac is " + threeGIF.getMac());
    	}
    	Log.d(TAG, "3G name is " + threeGIF.getName());    	    
    	Log.d(TAG, "3G Mask is " + threeGIF.getMask());    	
    	Log.d(TAG, "3G GW IP is " + threeGGW.getIP());
    	Log.d(TAG, "3G GW Mac is " + threeGGW.getMac());
    	
    	Utility.runRootCommand("/data/local/bin/busybox ifconfig " + threeGIF.getName() + " 0.0.0.0", false);
    	//sendReportToUI("3G name is " + threeGIF.getName());
    	//sendReportToUI("3G IP is " + threeGIF.getIP());
    	//sendReportToUI("3G Mask is " + threeGIF.getMask());
    	//sendReportToUI("3G Mac is " + threeGIF.getMac());
    	//sendReportToUI("3G GW IP is " + threeGGW.getIP());
    	//sendReportToUI("3G GW Mac is " + threeGGW.getMac());
    }
    public void doWimaxInit(){
    	/** Need to compile with cyanogenmod to make this work*/
    	//threeGIF.setInterfaceEnable(mobile_included);
    	wimaxGW = wimaxIF.getGateway();    	
    	/** debug messages*/
    	Log.d(TAG, "WiMAX name is " + wimaxIF.getName());
    	Log.d(TAG, "WiMAX IP is " + wimaxIF.getIP());
    	Log.d(TAG, "WiMAX Mask is " + wimaxIF.getMask());
    	Log.d(TAG, "WiMAX Mac is " + wimaxIF.getMac());
    	Log.d(TAG, "WiMAX GW IP is " + wimaxGW.getIP());
    	Log.d(TAG, "WiMAX GW Mac is " + wimaxGW.getMac());    
    	
    	//Utility.runRootCommand("/data/local/bin/busybox ifconfig " + wimaxIF.getName() + " 0.0.0.0", false);
    }
    public void doVethInit(){    	
    	/** 1. ip link add type veth (init)
    	 *  2. set the IP address and mask  */
    	
    	vIFs = new VirtualInterfacePair();    
    	
    	if(isMultipleInterface && wifi_included && mobile_included && wimax_included){ 
    		//need to set IP to veth1 only when it's multiple interfaces
	    	if( !wifiIF.getIP().startsWith("192.168") && !threeGIF.getIP().startsWith("192.168") && !wimaxIF.getIP().startsWith("192.168")){
	    		vIFs.setIP("veth1", "192.168.0.2", "255.255.255.0");
	    		//vethGW.setIP("192.168.0.1");
	    	}else if(!wifiIF.getIP().startsWith("10.38") && !threeGIF.getIP().startsWith("10.38") && !wimaxIF.getIP().startsWith("10.38")){
	    		vIFs.setIP("veth1", "10.38.0.2", "255.255.255.0");
	    		//vethGW.setIP("10.38.0.1");
	    	}else{
	    		Log.e(TAG, "Couldn't find an IP to initilize virtual interface");
	    	}
    	}else{
    		//otherwise, just set veth1' mac/ip address as wifi or 3G
    		if(wifi_included){
    			vIFs.getVeth1().setIP(wifiIF.getIP(), wifiIF.getMask());
    			vIFs.getVeth1().setMac(wifiIF.getMac());    			
    			wifiIF.removeIP();
    			vethGW = wifiIF.getGateway();
    		}else if(mobile_included){
    			vIFs.getVeth1().setIP(threeGIF.getIP(), threeGIF.getMask());
    			vIFs.getVeth1().setMac(threeGIF.getMac());
    			vethGW = threeGIF.getGateway();
    			/*if (threeGIF.getName().equals("rmnet0")) {
    				threeGIF.removeIP();
    			}*/
    		}else if(wimax_included){
    			vIFs.getVeth1().setIP(wimaxIF.getIP(), wimaxIF.getMask());
    			vIFs.getVeth1().setMac(wimaxIF.getMac());
    			vethGW = wimaxIF.getGateway();
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
    	ovs.addIF("dp0", "dp0");
    	ovs.addIF("dp0", vIFs.getVeth0().getName());
    	if(wifi_included){
    		ovs.addIF("dp0", wifiIF.getName());
    	}
    	if(mobile_included){
    		if (threeGIF.isPointToPoint()){    			
    			ovs.addIF("dp0", "veth2");    			
    		} else {
    			ovs.addIF("dp0", threeGIF.getName());	
    		}
    	}
    	if(wimax_included){
    		ovs.addIF("dp0", wimaxIF.getName());
    	}
    	/** debug messages*/
    	//sendReportToUI("Setup OVS");
    	if(DEBUG){
	    	Iterator<String> it = ovs.dptable.get("dp0").iterator();
	    	while(it.hasNext()){
	    		String name = it.next();
	    		int port = ovs.dptable.get("dp0").indexOf(name);
	    		Log.d(TAG, "port #" + port + ": " + name);
	    	}
    	}
    	Log.d(TAG, "OVS setuped");
    }         
        
    public void doOpenflowdInit(){    	    	
    	Utility.runRootCommand("killall -9 ovs-openflowd", false);    			
    	Utility.runRootCommand("/data/local/bin/ovs-openflowd "+ ovs.getDP(0) +" tcp:127.0.0.1 " + 
    			"--out-of-band --monitor --detach --log-file=/sdcard/Android/data/net.holyc/files/ovs.log", false);
    	Log.d(TAG, "Openflowd is running in detached mode");    	
    }
       
    public void doRoutingInit(){
    	/** remove other default route */
    	if(wifi_included){
    		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ wifiIF.getName(), false);
    		ArrayList<String> routeNet = Utility.runRootCommand("/data/local/bin/busybox route -n | /data/local/bin/busybox grep "+ wifiIF.getName() +" | /data/local/bin/busybox awk '{print $1}'", true);
			Iterator<String> it = routeNet.iterator();        				
			while(it.hasNext()){        					
				String net = it.next();								
				Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + wifiIF.getMask() + " dev " + wifiIF.getName(), false);				
			}
    	}
    	if(mobile_included){
    		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ threeGIF.getName(), false);
    		if(threeGIF.isPointToPoint()){
    			Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ threeGIF.veth2.getName(), false);
    		}
    		ArrayList<String> routeNet = Utility.runRootCommand("/data/local/bin/busybox route -n | /data/local/bin/busybox grep "+ threeGIF.getName() +" | /data/local/bin/busybox awk '{print $1}'", true);
			Iterator<String> it = routeNet.iterator();        				
			while(it.hasNext()){        					
				String net = it.next();
				Log.d(TAG, "deleting network:" + "/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName());
				threeGIF.addNet(net);
				Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName(), false);
				if (threeGIF.isPointToPoint()) {
					Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.veth2.getName(), false);
				}
			}
    	}
    	if(wimax_included){
    		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ wimaxIF.getName(), false);
    	}
    	
    	if(isMultipleInterface){
    		Utility.runRootCommand("/data/local/bin/busybox route add default dev " + vIFs.getVeth1().getName(), false);    		
    	}else{ //single interface
    		if(wifi_included){
        		Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
        		Log.d(TAG, "Add default gw:" + "route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName());
    		}else if(mobile_included){
    			if (threeGIF.isPointToPoint()) {
    				Utility.runRootCommand("/data/local/bin/busybox route add -net " 
    						+ threeGGW.getIP() + " netmask 255.255.255.255 "+ vIFs.getVeth1().getName(), false);
    				Utility.runRootCommand("/data/local/bin/busybox route add default dev veth1", false);
    				Utility.runRootCommand("/data/local/bin/busybox route del default dev ppp0", false);
    				if(threeGIF.getMask().equals("255.255.255.255")){
    					Utility.runRootCommand("/data/local/bin/busybox route del -net " 
    							+ threeGGW.getIP() + " netmask 255.255.255.255 " + threeGIF.getName() , false);
    				}else{
    					ArrayList<String> routeNet = Utility.runRootCommand("/data/local/bin/busybox route -n | /data/local/bin/busybox grep "+ threeGIF.getName() +" | /data/local/bin/busybox awk '{print $1}'", true);
        				Iterator<String> it = routeNet.iterator();        				
        				while(it.hasNext()){        					
        					String net = it.next();
        					Log.d(TAG, "deleting network:" + "/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName());
        					threeGIF.addNet(net);
        					Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName(), false);
        					Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.veth2.getName(), false);
        				}
    				}
    			} else {
    				//Utility.runRootCommand("/data/local/bin/busybox route add -net " + threeGIF.getIP() + " netmask " + threeGIF.getMask() + " " + vIFs.getVeth1().getName(), false);
    				Utility.runRootCommand("/data/local/bin/busybox route add default gw " + threeGGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
    				ArrayList<String> routeNet = Utility.runRootCommand("/data/local/bin/busybox route -n | /data/local/bin/busybox grep "+ threeGIF.getName() +" | /data/local/bin/busybox awk '{print $1}'", true);
    				Iterator<String> it = routeNet.iterator();
    				while(it.hasNext()){
    					String net = it.next();
    					threeGIF.addNet(net);
    					Utility.runRootCommand("/data/local/bin/busybox route del -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName(), false);
    				}
    				String dns1 = Utility.getProp("net."+threeGGW.getName()+".dns1");
    				String dns2 = Utility.getProp("net."+threeGGW.getName()+".dns2");
    				Utility.runRootCommand("/data/local/bin/busybox route del -net " + dns1 + " netmask 255.255.255.255 dev " + threeGIF.getName(), false);
    				Utility.runRootCommand("/data/local/bin/busybox route del -net " + dns2 + " netmask 255.255.255.255 dev " + threeGIF.getName(), false);
    			}
    		}else if(wimax_included){
    			//TODO, enable single wimax interface usage
    		}
    	}
    	Log.d(TAG, "Routing setuped");
    }
    public void uninstallRulesWithIP(String IP){
    	int host_ip = IPv4.toIPv4Address(IP);
    	OFActionOutput arp_action = new OFActionOutput()
		.setPort(OFPort.OFPP_CONTROLLER.getValue())
		.setMaxLength((short)65535);  
		//remove rules for forwarding arp destinated to the host IP 
		OFMatch arp_match = new OFMatch()        
				.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE & ~(63 << OFMatch.OFPFW_NW_DST_SHIFT))    	    				
				.setNetworkDestination(host_ip)
				.setDataLayerType((short)0x0806);//arp    	    	
		      	
		OFFlowMod arp_fm = new OFFlowMod();
		
		arp_fm.setBufferId(-1)
				.setIdleTimeout((short) 0)
				.setHardTimeout((short) 0)
				.setOutPort((short) OFPort.OFPP_NONE.getValue())		    	    	
				.setCommand(OFFlowMod.OFPFC_DELETE)
				.setMatch(arp_match)            
				.setActions(Collections.singletonList((OFAction)arp_action))
				.setPriority(OFCommService.MID_PRIORITY)
				.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH));
		
		ByteBuffer arp_bb = ByteBuffer.allocate(arp_fm.getLength());
		arp_fm.writeTo(arp_bb);		
		//sendOFPacket(socket, arp_bb.array());
    }
    public void doWiFiCleanup(){
    	Log.d(TAG, "make wifi out of ovs setup .... ");
    	if(wifi_included){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + wifiIF.getName(), false);    		
    	}
    	/* give the ip back to the original interfaces*/
    	if(wifi_included){
			if(wifiInfo != null && wifiInfo.isConnected()){
				Utility.runRootCommand("/data/local/bin/busybox ifconfig " + wifiIF.getName() + " " + wifiIF.getIP() + " netmask " + wifiIF.getMask(), false);
				Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wifiGW.getIP()+ " " + wifiIF.getName(), false);
			}
		}    	
    	//TODO: clear flow rules relates to wifi 
    	//uninstallRulesWithIP(wifiIF.getIP());
    	wifiIF = null;
    	wifi_included = false;
    }
    public void doMobileCleanup(){
    	Log.d(TAG, "make mobile out of ovs setup .... ");
    	if(mobile_included){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + threeGIF.getName(), false);
    	}
    	/* give the ip back to the original interfaces*/
    	if(mobile_included){
			if(mobileInfo != null && mobileInfo.isConnected()){
				Log.d(TAG, "add routing back into host routing table");
				Utility.runRootCommand("/data/local/bin/busybox ifconfig " + threeGIF.getName() + " " + threeGIF.getIP() + " netmask " + threeGIF.getMask(), false);			
				ArrayList<String> routeNets = threeGIF.getNets();
				Iterator<String> it = routeNets.iterator();
				while(it.hasNext()){
					String net = it.next();
					Utility.runRootCommand("/data/local/bin/busybox route add -net " + net + " netmask " + threeGIF.getMask() + " dev " + threeGIF.getName(), false);
				}
				threeGIF.clearNets();
				Utility.runRootCommand("/data/local/bin/busybox route add default gw " + threeGIF.getGateway().getIP()+ " " + threeGIF.getName(), false);
			}
		}
    	//TODO: clear flow rules relates to mobile interface
    	threeGIF = null;
    	mobile_included = false;
    }
    public void doWimaxCleanup(){
    	Log.d(TAG, "make wimax out of ovs setup .... ");
    	if(wimax_included){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + wimaxIF.getName(), false);
    	}
    	/* give the ip back to the original interfaces*/
    	if(wimax_included){
			if(wimaxInfo != null && wimaxInfo.isConnected()){
				Log.d(TAG, "add routing back into host routing table");
				Utility.runRootCommand("/data/local/bin/busybox ifconfig " + wimaxIF.getName() + " " + wimaxIF.getIP() + " netmask " + wimaxIF.getMask(), false);							
				Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wimaxIF.getGateway().getIP()+ " " + wimaxIF.getName(), false);
			}
		}
    	//TODO: clear flow rules relates to mobile interface
    	wimaxIF = null;
    	wimax_included = false;
    }
    public void doEnvCleanup(){    
    	Log.d(TAG, "start cleaning the environment .... ");
    	Utility.runRootCommand("/data/local/bin/busybox killall -9 ovs-openflowd", false);
    	doWiFiCleanup();
    	doMobileCleanup();    	
    	doWimaxCleanup();
    	if(vIFs != null){
    		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + vIFs.getVeth0().getName(), false);
    	}
		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-dp dp0", false);		
		Utility.runRootCommand("rmmod openvswitch_mod", false);		
		Utility.runRootCommand("/data/local/bin/busybox ip link del veth0", false);
		if(mobile_included && threeGIF!= null && threeGIF.getName().equals("ppp0")){
			Utility.runRootCommand("/data/local/bin/busybox ip link del veth2", false);
		}			
		Iterator<Socket> it = OFCommService.socketMap.values().iterator();
		while(it.hasNext()){
			Socket socket = it.next();		
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
		OFCommService.socketMap.clear();
		OFCommService.procDelayTable.clear();
		ovs_beforeSwitch = ovs;
		ovs = null;
		isOVSsetup = false;
		isOpenflowdSetup = false;
		Log.d(TAG, "finished environment cleanup");
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
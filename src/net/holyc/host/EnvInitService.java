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
    public static boolean wifi_included;
	public static boolean mobile_included;
	public static boolean initializedByDispather = false;
	public static boolean isMultipleInterface = true;
	/** The interfaces in the host*/
	public static VirtualInterfacePair vIFs = null;
	public static ThreeGInterface threeGIF = null;
	public static WifiInterface wifiIF = null;
	public static VirtualSwitch ovs_beforeSwitch = null;
	public static VirtualSwitch ovs = null;
	public static HostInterface vethGW = null;
	private HostInterface wifiGW = null;
	private HostInterface threeGGW= null;
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
                	initializedByDispather = false;
                    break;                                   
                case HolyCMessage.ENV_INIT_START.type:
            		wifi_included = true;
            		mobile_included = true;
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
					   doWiFiCleanup();
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
    
    
    
    public void doEnvInit(){
    	Log.d(TAG, "Start initialize Environment for the host with ips = " + Utility.getLocalIpAddress());
    	mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	//WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);    	
    	
        wifiInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mobileInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

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
        
        Log.d(TAG, "wifi: " + wifi_included + ", 3g: " + mobile_included);
        
        if(isMultipleInterface == false){
        	if(wifi_included == true){
        		mobile_included = false;
        	}
        }
        
        if(wifi_included == false && mobile_included == false){
        	//stopController();
        	Log.d(TAG, "NO connection available");
        	sendReportToUI("NO connection available, abort!");
        	return;
        }else if(wifi_included == true && mobile_included == false){
        	Log.d(TAG, "Using WiFi");
        	sendReportToUI("Using WiFi");
        }else if(wifi_included == false && mobile_included == true){
        	Log.d(TAG, "Using Mobile Data Plan");
        	sendReportToUI("Using Mobile Data Plan");
        }else{
        	Log.d(TAG, "Using Both WiFi and Mobile Data Plan");
        	sendReportToUI("Using Both WiFi and Mobile Data Plan");
        }
        
       
        
    	if(wifi_included){
    		doWiFiInit();
    	}
    	if(mobile_included){
    		doMobileInit();    		
    	}
    	
    	if((wifi_included && wifiIF.getIP() == null) || (mobile_included && threeGIF.getIP() == null) ){
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
    	
    	if(isMultipleInterface && wifi_included && mobile_included){ 
    		//need to set IP to veth1 only when it's multiple interfaces
	    	if( !wifiIF.getIP().startsWith("192.168") && !threeGIF.getIP().startsWith("192.168")){
	    		vIFs.setIP("veth1", "192.168.0.2", "255.255.255.0");
	    		//vethGW.setIP("192.168.0.1");
	    	}else if(!wifiIF.getIP().startsWith("10.38") && !threeGIF.getIP().startsWith("10.38")){
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
    	}
    	if(mobile_included){
    		Utility.runRootCommand("/data/local/bin/busybox ip route del dev "+ threeGIF.getName(), false);
    	}

    	if(isMultipleInterface){
    		Utility.runRootCommand("/data/local/bin/busybox route add default dev " + vIFs.getVeth1().getName(), false);
    	}else{ //single interface
    		if(wifi_included){
        		Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
        		Log.d(TAG, "Add default gw:" + "route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName());
    		}else if(mobile_included){
    			if (threeGIF.getName().equals("ppp0")) {
    				Utility.runRootCommand("/data/local/bin/busybox route add -net " 
    						+ threeGGW.getIP() + " netmask 255.255.255.255 "+ vIFs.getVeth1().getName(), false);
    				Utility.runRootCommand("/data/local/bin/busybox route add default dev veth1", false);
    				Utility.runRootCommand("/data/local/bin/busybox route del default dev ppp0", false);
    				Utility.runRootCommand("/data/local/bin/busybox route del -net " 
    						+ threeGGW.getIP() + " netmask 255.255.255.255 ppp0", false);
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
    
    public void doEnvCleanup(){    
    	Log.d(TAG, "start cleaning the environment .... ");
    	Utility.runRootCommand("/data/local/bin/busybox killall -9 ovs-openflowd", false);
    	doWiFiCleanup();
    	doMobileCleanup();    	
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
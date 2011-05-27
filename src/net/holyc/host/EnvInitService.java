package net.holyc.host;

import java.util.ArrayList;

import net.holyc.HolyCMessage;
import android.app.Service;
import android.content.Intent;
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


public class EnvInitService extends Service implements Runnable{

	String TAG = "HOLYC.EnvInit";	
	
	/** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    private boolean wifi_included;
	private boolean mobile_included;
	private boolean isMultipleInterface;
	private Thread monitorThread = null;
	/** The interfaces in the host*/
	private VirtualInterfacePair vIFs = null;
	private ThreeGInterface threeGIF = null;
	private WifiInterface wifiIF = null;
	private HostInterface wifiGW = null;
	private HostInterface threeGGW= null;
	private VirtualSwitch ovs = null;
        
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
                	sendReportToUI("Initiating the environment with WiFi: " + wifi_included + " and 3G: " + mobile_included);
                	Log.d(TAG, "Initiating the environment with WiFi: " + wifi_included + " and 3G: " + mobile_included);
                	doEnvInit();
                	
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

    	if(wifi_included){
    		doWiFiInit();
    	}
    	if(mobile_included){
    		doMobileInit();
    	}
    	doVethInit();
    	doOVSInit();
    	doOpenflowdInit();
    	doRoutingInit();
    	startMonitorThread();
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
    	Log.d(TAG, "wifi gw IP is " + wifiGW.getIP());
    	Log.d(TAG, "wifi gw mac is " + wifiGW.getMac());
    	sendReportToUI("wifi Name is " + wifiIF.getName());
    	sendReportToUI("wifi IP is " + wifiIF.getIP());
    	sendReportToUI("wifi mac is " + wifiIF.getMac());
    	sendReportToUI("wifi gw IP is " + wifiGW.getIP());
    	sendReportToUI("wifi gw mac is " + wifiGW.getMac());
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
    	sendReportToUI("3G name is " + threeGIF.getName());
    	sendReportToUI("3G IP is " + threeGIF.getIP());
    	sendReportToUI("3G Mask is " + threeGIF.getMask());
    	sendReportToUI("3G Mac is " + threeGIF.getMac());
    	sendReportToUI("3G GW IP is " + threeGGW.getIP());
    	sendReportToUI("3G GW Mac is " + threeGGW.getMac());
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
    			threeGIF.removeIP();
    		}else{
    			Log.e(TAG, "no interface is enabled");
    		}
    	}
    	
    	/** debug messages*/
    	Log.d(TAG, "veth name is " + vIFs.getNames());
    	Log.d(TAG, "veth IP is " + vIFs.getIPs());
    	Log.d(TAG, "veth Mac is " + vIFs.getMacs());    	
    	sendReportToUI("veth name is " + vIFs.getNames());
    	sendReportToUI("veth IP is " + vIFs.getIPs());
    	sendReportToUI("veth Mac is " + vIFs.getMacs());
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
    		ovs.addIF("dp0", threeGIF.getName());
    	}
    	/** debug messages*/
    	sendReportToUI("Setup OVS");
    	Log.d(TAG, "OVS setuped");
    }         
        
    public void doOpenflowdInit(){    	    	
    	Utility.runRootCommand("/data/local/bin/ovs-openflowd "+ ovs.getDP(0) +" tcp:127.0.0.1 " + 
    			"--out-of-band --monitor --detach --log-file=/data/local/var/ovs.log", false);
    	Log.d(TAG, "Openflowd is running in detached mode");
    	/**
    	 * @TODO: How to retrieve the output of openflowd? (do we want to have it in logcat?)
    	 */
    	/** debug messages*/
    	//sendReportToUI("Please Setup Openflowd By Hand, go to adb shell; /data/local/bin/ovs-openflowd dp0 tcp:127.0.0.1 --out-of-band");
    	sendReportToUI("Openflowd is running in detached mode");
    }
       
    public void doRoutingInit(){
    	/** remove other default route */		
		Utility.runRootCommand("ip route del dev "+ wifiIF.getName(), false);
		Utility.runRootCommand("ip route del dev "+ threeGIF.getName(), false);

    	if(isMultipleInterface){
    		Utility.runRootCommand("/data/local/bin/busybox route add default dev " + vIFs.getVeth1().getName(), false);
    	}else{ //single interface
    		if(wifi_included){
        		Utility.runRootCommand("/data/local/bin/busybox route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
        		Log.d(TAG, "Add default gw:" + "route add default gw " + wifiGW.getIP()+ " " + vIFs.getVeth1().getName());
    		}else if(mobile_included){
        		Utility.runRootCommand("/data/local/bin/busybox route add default gw " + threeGGW.getIP()+ " " + vIFs.getVeth1().getName(), false);
    		}
    	}
    	Log.d(TAG, "Routing setuped");
    }
	@Override
	public IBinder onBind(Intent arg0) {
		return mMessenger.getBinder();
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		Utility.runRootCommand("killall ovs-openflowd", false);
		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + wifiIF.getName(), false);
		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-if dp0 " + vIFs.getVeth0().getName(), false);
		Utility.runRootCommand("/data/local/bin/ovs-dpctl del-dp dp0", false);
		Utility.runRootCommand("rmmod openvswitch_mod", false);
		Utility.runRootCommand("/data/local/bin/busybox ip link del veth0", false);
		Log.d(TAG, "cleanup the environment ");
		stopMonitorThread();
	}
	public void startMonitorThread(){
		monitorThread = new Thread(this);  
		monitorThread.start();
	}
	public void stopMonitorThread(){
   	 if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
   }    
	public void run() {
		//check the status every 30 seconds?
		try {
			while(true){
				Thread.sleep(30000);				
				if(threeGIF!=null){									
					Utility.runRootCommand("ip route del dev "+ threeGIF.getName(), false);
					Log.d(TAG, "delete 3G route " + threeGIF.getName());
				}
			}
		} catch (InterruptedException e) {
		}
	}
}
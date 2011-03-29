package edu.stanford.holyc.host;

import java.util.ArrayList;

import net.beaconcontroller.packet.IPv4;

import edu.stanford.holyc.statusUI;
import edu.stanford.holyc.jni.NativeCallWrapper;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
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
 * 2. OVS setups
 * 3. initiate openflowd
 * 4. Routing table setup
 *
 * @author Te-Yuan Huang (huangty@stanford.edu)
 *
 */

public class EnvInitService extends Service{

	String TAG = "HOLYC.EnvInit";
	
	/** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	/** Message Types Between statusUI Activity and This Service */
	public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_START_ENVINIT = 3;
	private boolean wifi_included;
	private boolean mobile_included;
	private String wifiMACaddr = "";
	private String wifiIPaddr = "";
        
    /** Handler of incoming messages from clients (Activities). */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;                                   
                case MSG_START_ENVINIT:
            		wifi_included = true;
            		mobile_included = true;
                	if(msg.arg1 == 0){
                		wifi_included = false;
                	}                	
                	if(msg.arg2 == 0){
                		mobile_included = false;
                	}                	
                	sendReportToUI("Initiating the environment with WiFi: " + wifi_included + " and 3G: " + mobile_included);
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
            	Message msg = Message.obtain(null, statusUI.MSG_REPORT_UPDATE);
            	Bundle data = new Bundle();
            	data.putString("MSG_REPORT_UPDATE", str+"\n -------------------------------");
            	msg.setData(data);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
  
    public void doEnvInit(){
    	doVethInit();
    	doWiFiInit();
    	doMobileInit();
    	doOVSInit();
    	doOpenflowdInit();
    	doRoutingInit();
    }
    public void doRoutingInit(){
    	NativeCallWrapper.runCommand("su -c \"ip route del dev eth0\"");
    	NativeCallWrapper.runCommand("su -c \"ip route del dev rmnet0\"");
    	String cmd = "su -c \"/data/local/bin/busybox ifconfig veth1 "+ HostNetworkState.vethIP+" netmask "+ HostNetworkState.vethIPMask + "\"";
    	NativeCallWrapper.runCommand(cmd);
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/busybox route add default dev veth1\"");    	
    }
    public void doOpenflowdInit(){    	
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-openflowd dp0 tcp:127.0.0.1 --out-of-band --detach\"");
    }
    public void doOVSInit(){
    	NativeCallWrapper.runCommand("su -c \"insmod /sdcard/openvswitch_mod.ko\"");
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-dp dp0\"");
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-if dp0 veth0\"");
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-if dp0 eth0\"");
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/ovs-dpctl add-if dp0 rmnet0\"");
    }
    public void doVethInit(){
    	/**
    	 * /data/local/bin/busybox ip link add type veth
    	 * MAC_VETH=`/data/local/bin/busybox ifconfig veth1 | awk 'NR==1{ print $5}'`
    	 * ifconfig veth1 192.168.0.2 netmask 255.255.255.0
    	 * IP_VETH=192.168.0.2
    	 * IP_VETH_MASK=255.255.255.0
    	 * */    	
    	//1. to create veth0 and veth1
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/busybox ip link add type veth\"");
    	//2. Setup the address of veth1
    	NativeCallWrapper.runCommand("su -c \"/data/local/bin/busybox ifconfig veth1 192.168.0.2 netmask 255.255.255.0\"");
    	//2. Get MAC address

    }
    public void doMobileInit(){
    	/**
    	 * MAC_MOBILE=`/data/local/bin/busybox ifconfig rmnet0 | awk 'NR==1{ print $5}'`
    	 * IP_MOBILE_GW=`getprop net.rmnet0.gw`
    	 * MAC_MOBILE_GW=`/data/local/bin/busybox arping -I rmnet0 -c 1 $IP_MOBILE_GW | awk 'NR==2{print$5}' |  sed 's/\[//g' | sed 's/\]//g'`
    	 * IP_MOBILE=`ifconfig rmnet0 | awk '{print $3}'`
    	 */
    	
    }
    public void doWiFiInit(){
    	/**
    	 * MAC_WIFI=`/data/local/bin/busybox ifconfig eth0 | awk 'NR==1{ print $5}'`
    	 * IP_WIFI=`ifconfig eth0 | awk '{print $3}'`
    	 * IP_WIFI_GW=`busybox route -n | grep eth0 | grep UG | awk '{print $2}'`
    	 * MAC_WIFI_GW=`/data/local/bin/busybox arping -I eth0 -c 1 $IP_WIFI_GW | awk 'NR==2{print$5}' |  sed 's/\[//g' | sed 's/\]//g'`
    	 * 
    	 * */
    	WifiManager wifiMan = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
    	if(wifi_included && !wifiMan.isWifiEnabled()){    		
    		wifiMan.setWifiEnabled(true);    	
    	}
    	WifiInfo wifiInf = wifiMan.getConnectionInfo();
    	wifiMACaddr = wifiInf.getMacAddress();
    	int wifiIP = wifiInf.getIpAddress();
    	wifiIPaddr = IPv4.fromIPv4Address(wifiIP);
    	
    }
	@Override
	public IBinder onBind(Intent arg0) {
		return mMessenger.getBinder();
	}
	
}
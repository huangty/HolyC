package net.holyc.ofcomm;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.holyc.HolyCMessage;
import net.holyc.R;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFReplyEvent;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFHello;

import com.google.gson.Gson;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
 * The Thread Create and Maintain Connections to Openflowd
 *
 * @author Te-Yuan Huang (huangty@stanford.edu)
 *
 */

public class OFCommService extends Service implements Runnable{
	int bind_port = 6633;
    ServerSocketChannel ctlServer = null; 
	Selector selector = null;
	//the function of OFMessageHandler is replaced by DispatchService
	//OFMessageHandler ofm_handler = new OFMessageHandler();
	String TAG = "HOLYC.OFCOMM";

	// A list of PendingChange instances
	private List<NIOChangeRequest> pendingChanges = new LinkedList<NIOChangeRequest>();

	// Maps a SocketChannel to a list of ByteBuffer instances
	private Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<SocketChannel, List<ByteBuffer>>();	
	public Map<Long, OFFeaturesReply> switchData = new HashMap<Long, OFFeaturesReply>();
	private ArrayList<SocketChannel> openedSocketChannels = new ArrayList<SocketChannel>();
	/** For showing and hiding our notification. */
    NotificationManager mNM;
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    
    /** Holds last value set by a client. */
    int mValue = 0;

    final int BUFFER_SIZE = 8192;
    
    Gson gson = new Gson();
    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HolyCMessage.OFCOMM_REGISTER.type:
                    mClients.add(msg.replyTo);
                    break;
	        case HolyCMessage.OFCOMM_UNREGISTER.type:
                    mClients.remove(msg.replyTo);
                    break;                    
                case HolyCMessage.OFCOMM_SET_VALUE.type:
                    mValue = msg.arg1;
                    for (int i=mClients.size()-1; i>=0; i--) {
                        try {
                            mClients.get(i).send(Message.obtain(null,
								HolyCMessage.OFCOMM_SET_VALUE.type, 
								mValue, 0));
                        } catch (RemoteException e) {
                            mClients.remove(i);
                        }
                    }
                    break;
                case HolyCMessage.OFCOMM_START_OPENFLOWD.type:
                	bind_port = msg.arg1;
                	sendReportToUI("Bind on port: " + bind_port);
                	Log.d(TAG, "Send msg on bind: " + bind_port);
                	startOpenflowD();
                	break;
                case HolyCMessage.OFREPLY_EVENT.type:
                	String json = msg.getData().getString(HolyCMessage.OFREPLY_EVENT.str_key);
                	Log.d(TAG, "serialized json = " + json);               	
                	OFReplyEvent ofpoe =  gson.fromJson(json, OFReplyEvent.class);
                	// TODO: send back to openflowd based on socketChannelNumber
                	int scn = ofpoe.getSocketChannelNumber();
                	Log.d(TAG, "Send OFReply through socket channel #"+scn);
                	if(openedSocketChannels.isEmpty()){
                		Log.e(TAG, "there is no SocketChannel left");
                	}else{
                		SocketChannel sc = openedSocketChannels.get(scn);
                		if(sc != null){
                    		send(sc, ofpoe.getData());
                    	}
                		
                		/** for debug */
                		sendReportToUI("Send OFReply packet = " + ofpoe.getOFMessage().toString());
                    	Log.d(TAG, "Send OFReply packet = "+ ofpoe.getOFMessage().toString());
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
    
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.
        showNotification();
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(R.string.openflow_channel_started);
        //close server socket before leaving the service
        try{
	        if(ctlServer != null && ctlServer.isOpen()){
				ctlServer.socket().close();				
				ctlServer.close();
	        }
        }catch(IOException e){        	
        }
        // Tell the user we stopped.
        Toast.makeText(this, R.string.openflow_channel_stopped, Toast.LENGTH_SHORT).show();
        
    }
    public void sendReportToUI(String str){
    	//Log.d("AVSC", "size of clients = " + mClients.size() );
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.UIREPORT_UPDATE.type);
            	Bundle data = new Bundle();
            	data.putString(HolyCMessage.UIREPORT_UPDATE.str_key, 
			       str+"\n -------------------------------");
            	msg.setData(data);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
    public void sendOFEventToDispatchService(int sc_index, byte[] OFdata){
    	Gson gson = new Gson();
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.OFCOMM_EVENT.type);
            	OFEvent ofe = new OFEvent(sc_index, OFdata);
            	sendReportToUI("Recevie OFMessage: " + ofe.getOFMessage().toString());
            	Bundle data = new Bundle();            	
            	data.putString(HolyCMessage.OFCOMM_EVENT.str_key, 
			       gson.toJson(ofe, OFEvent.class));
            	msg.setData(data);
                mClients.get(i).send(msg);    	
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
    
    public void startOpenflowD(){    	
    	new Thread(this).start();
    	sendReportToUI("Start Controller Daemon");    	
    }
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        /*CharSequence text = getText(R.string.openflow_channel_started);

        // Set the icon, scrolling text and timestamp for notification
        Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, statusUI.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.openflow_channel_started),
                       text, contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.openflow_channel_started, notification);*/
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

	@Override
	/** This service is also a thread, which running a server to listen to the message from the swtch*/
	public void run() {
		try {
        	ctlServer = ServerSocketChannel.open();
    		ctlServer.configureBlocking(false);
    		ctlServer.socket().setReuseAddress(true);
    		ctlServer.socket().bind(new InetSocketAddress(bind_port));
    		selector = Selector.open();
	        ctlServer.register(selector, SelectionKey.OP_ACCEPT);	        
	        //new Thread(ofm_handler).start();
	    	Log.d(TAG, "Started the Controller TCP server, listening on Port " + bind_port); 
		} catch (IOException e) {
			// TODO Auto-generated catch block			
			e.printStackTrace();
			
		}
		
    	Log.d(TAG,"starting controller on a seperated thread");
    	ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    	try{
    		while (!Thread.interrupted()) {
    			
    			synchronized (this.pendingChanges) {
					Iterator<NIOChangeRequest> changes = this.pendingChanges.iterator();
					while (changes.hasNext()) {
						NIOChangeRequest change = (NIOChangeRequest) changes.next();
						switch (change.type) {
						case NIOChangeRequest.CHANGEOPS:
							SelectionKey key = change.socket.keyFor(this.selector);
							key.interestOps(change.ops);
						}
					}
					this.pendingChanges.clear();
				}
    			selector.select();
    			Set<SelectionKey> selectedKeys = selector.selectedKeys();
    			Iterator<SelectionKey> it = selectedKeys.iterator();
    			while(it.hasNext()){
    				SelectionKey key = (SelectionKey) it.next();
    				it.remove();
    				if( ! key.isValid() ){
    					continue;
    				}else if( key.isAcceptable() ){
    					//handle new connection
    			    	Log.d(TAG,"got a new connection");
    					ServerSocketChannel scc = (ServerSocketChannel) key.channel();
    					SocketChannel sc = scc.accept();
    					sc.configureBlocking(false);
    					sc.register(selector, SelectionKey.OP_READ);
    					sendReportToUI("Accpet New Connection");
    					Log.d(TAG, "accept new connection");
    					/** Immediately send a OFHello back */
    					OFHello ofh = new OFHello();
    					ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
    					ofh.writeTo(bb);
    					send(sc, bb.array());
    				}else if(key.isReadable()){
    					//handle message from switch/remote host
    					read(key, readBuffer);   					    					
    				}else if(key.isWritable()){
    					write(key);
    				}
    			}    			
    		}
    	}catch (IOException e) {
			e.printStackTrace();
		}
	}	  
	private void read(SelectionKey key, ByteBuffer readBuffer) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		readBuffer.clear();
		int numRead = -1;				
		try {
			numRead = sc.read(readBuffer);
		} catch (IOException e) {
			key.cancel();
			sc.close();
			if(openedSocketChannels.contains(sc)){
				int scn = openedSocketChannels.indexOf(sc);
				openedSocketChannels.remove(sc);
				Log.d(TAG, "socket channel #" + scn + " removed , socketChannel size = " + openedSocketChannels.size() );				
			}
			return;
		}
		if (numRead == -1) {
			//If the read is unsuccessful
			key.channel().close();
			key.cancel();
			if(openedSocketChannels.contains(sc)){
				int scn = openedSocketChannels.indexOf(sc);
				openedSocketChannels.remove(sc);
				Log.d(TAG, "socket channel #" + scn + " removed , socketChannel size = " + openedSocketChannels.size() );				
			}
			return;
		}else{
			//If the read is successful
			if(!openedSocketChannels.contains(sc)){
				openedSocketChannels.add(sc);				
				Log.d(TAG, "socket channel added, socketChannel size = " + openedSocketChannels.size() );				
			}
			int scn = openedSocketChannels.indexOf(sc);
			//Hand the data off to OFMessage Handler	
			sendOFEventToDispatchService(scn, readBuffer.array());
	    	Log.d(TAG, "read OF packet through socket channel #"+scn);
		}
		//this.ofm_handler.processData(this, sc, readBuffer.array(), numRead);
	}
	private void write(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		synchronized (this.pendingData) {
			List<ByteBuffer> queue = (List<ByteBuffer>) this.pendingData.get(socketChannel);
			
			// Write until there's not more data ...
			while (!queue.isEmpty()) {
				ByteBuffer buf = queue.get(0);
				socketChannel.write(buf);
				if (buf.remaining() > 0) {
					// ... or the socket's buffer fills up
					break;
				}
				queue.remove(0);
			}

			if (queue.isEmpty()) {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}
	
	public void send(SocketChannel socket, byte[] data) {
		synchronized (this.pendingChanges) {
			// Indicate we want the interest ops set changed
			this.pendingChanges.add(new NIOChangeRequest(socket, NIOChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

			// And queue the data we want written
			synchronized (this.pendingData) {
				List<ByteBuffer> queue = (List<ByteBuffer>) this.pendingData.get(socket);
				if (queue == null) {
					queue = new ArrayList<ByteBuffer>();
					this.pendingData.put(socket, queue);
				}			
				queue.add(ByteBuffer.wrap(data));
			}
		}
		// Finally, wake up our selecting thread so it can make the required changes
		this.selector.wakeup();
		
	}
	
	public void insertFixRule(SocketChannel socket){		
		/*sendReportToUI("Insert Fix Rule");
		OFMessageFactory messageFactory = new BasicFactory();
		OFActionFactory actionFactory = new BasicFactory();*/
		
		/*Assumption: 
		 * 
		 * port 1: veth0
		 * port 2: eth0
		 * port 3: rmnet0
		 * 
		 * */
		
		/*
		 * Insert the following rules:
		 * ovs-ofctl add-flow dp0 in_port=1,arp,priority=60000,idle_timeout=0,hard_timeout=0,actions=controller
		 * ovs-ofctl add-flow dp0 in_port=1,tcp,priority=60000,idle_timeout=0,hard_timeout=0,actions=mod_dl_src:$MAC_WIFI,mod_nw_src:$IP_WIFI,mod_dl_dst:$MAC_WIFI_GW,output:2
		 * ovs-ofctl add-flow dp0 in_port=2,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
		 * ovs-ofctl add-flow dp0 in_port=1,idle_timeout=0,hard_timeout=0,actions=mod_dl_src:$MAC_MOBILE,mod_nw_src:$IP_MOBILE,mod_dl_dst:$MAC_MOBILE_GW,output:3
		 * ovs-ofctl add-flow dp0 in_port=3,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1		 
		 * */
		
		/* Forward all the arp from port (1) to controller 
		 * 
		 * ovs-ofctl add-flow dp0 in_port=1,arp,priority=60000,idle_timeout=0,hard_timeout=0,actions=controller
		 * */
		
		/*OFMatch arp_match = new OFMatch()        
        					.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_TYPE)
        					.setInputPort((short)1)
        					.setDataLayerType((short)0x0806);//arp
        Log.d("AVSC", arp_match.toString());
        
        OFActionOutput arp_action = new OFActionOutput()
        						.setPort(OFPort.OFPP_CONTROLLER.getValue());
        OFFlowMod arp_fm = (OFFlowMod) messageFactory
		        .getMessage(OFType.FLOW_MOD);
       
	    arp_fm.setBufferId(-1)
		    .setIdleTimeout((short) 0)
		    .setHardTimeout((short) 0)
		    .setOutPort((short) OFPort.OFPP_NONE.getValue())
		    .setCommand(OFFlowMod.OFPFC_ADD)
		    .setMatch(arp_match)            
		    .setActions(Collections.singletonList((OFAction)arp_action))
		    .setPriority((short)60000)
		    .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH));
	   		
		ByteBuffer arp_bb = ByteBuffer.allocate(arp_fm.getLength());
		arp_bb.clear();
		arp_fm.writeTo(arp_bb);
		arp_bb.flip();
		send(socket, arp_bb.array());
		sendReportToUI("Set Flow Rule arp->controller: " + arp_fm.toString());
		Log.d("AVSC", arp_fm.toString());*/
        
		/* 1->2
		 * 
 		 * ovs-ofctl add-flow dp0 in_port=1,tcp,priority=60000,idle_timeout=0,hard_timeout=0,
 		 * 								actions=mod_dl_src:$MAC_WIFI,mod_nw_src:$IP_WIFI,mod_dl_dst:$MAC_WIFI_GW,output:2
		 * */
		
		/*OFMatch match1to2 = new OFMatch()        
        						.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT)
        						.setInputPort((short)1);
        Log.d("AVSC", match1to2.toString());
        
        //get $MAC_WIFI and $IP_WIFI
        
        
        // build action
        OFActionOutput action = new OFActionOutput()
            					.setPort((short)2)
            					
        // build flow mod
        OFFlowMod fm = (OFFlowMod) messageFactory
                .getMessage(OFType.FLOW_MOD);
        fm.setBufferId(U32.t(-1))
            .setIdleTimeout((short) 0)
            .setHardTimeout((short) 0)
            .setOutPort((short) OFPort.OFPP_NONE.getValue())
            .setCommand(OFFlowMod.OFPFC_ADD)
            .setMatch(match)            
            .setActions(Collections.singletonList((OFAction)action))
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH));
        //fm.getMatch().setInputPort((short)1); 
		ByteBuffer bb = ByteBuffer.allocate(fm.getLength());
		bb.clear();
		fm.writeTo(bb);
		bb.flip();
		send(socket, bb.array());
		sendReportToUI("Send Flow Messgage 1->2: " + fm.toString());
		Log.d("AVSC", fm.toString());*/
		/*2->1*/
		/*OFMatch match2 = new OFMatch();
		
        match2.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT);
        match2.setInputPort((short)2);
        Log.d("AVSC", match2.toString());
        // build action
        OFAction action2 = new OFActionOutput()
            .setPort((short)1);
        // build flow mod
        OFFlowMod fm2 = (OFFlowMod) messageFactory
                .getMessage(OFType.FLOW_MOD);
        fm2.setBufferId(U32.t(-1))
            .setIdleTimeout((short) 0)
            .setHardTimeout((short) 0)
            .setOutPort((short) OFPort.OFPP_NONE.getValue())
            .setCommand(OFFlowMod.OFPFC_ADD)
            .setMatch(match2)
            .setActions(Collections.singletonList((OFAction)action2))
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH));        
		ByteBuffer bb2 = ByteBuffer.allocate(fm2.getLength());
		bb2.clear();
		fm2.writeTo(bb2);
		bb2.flip();
		send(socket, bb2.array());
		sendReportToUI("Send Flow Messgage 2->1: " + fm2.toString());
		Log.d("AVSC", fm2.toString());		*/
	}

}
package net.holyc.ofcomm;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.holyc.HolyCMessage;
import net.holyc.R;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFReplyEvent;
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

public class OFCommService extends Service{
	int bind_port = 6633;
    ServerSocketChannel ctlServer = null; 
	Selector selector = null;
	String TAG = "HOLYC.OFCOMM";
	private Map<Integer, Socket> socketMap = new HashMap<Integer, Socket>();
    AcceptThread mAcceptThread = null;
	/** For showing and hiding our notification. */
    NotificationManager mNM;
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();    
    
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
                case HolyCMessage.OFCOMM_START_OPENFLOWD.type:
                	bind_port = msg.arg1;
                	sendReportToUI("Bind on port: " + bind_port);
                	Log.d(TAG, "Send msg on bind: " + bind_port);
                	startOpenflowController();
                	break;
                case HolyCMessage.OFREPLY_EVENT.type:
                	String json = msg.getData().getString(HolyCMessage.OFREPLY_EVENT.str_key);
                	Log.d(TAG, "serialized json = " + json);               	
                	OFReplyEvent ofpoe =  gson.fromJson(json, OFReplyEvent.class);
                	int scn = ofpoe.getSocketChannelNumber();                	
                	Log.d(TAG, "Send OFReply through socket channel with Remote Port "+scn);
                	if(!socketMap.containsKey(new Integer(scn))){
                		Log.e(TAG, "there is no SocketChannel left");
                	}else{
                		Socket socket = socketMap.get(new Integer(scn)); 
                		if(socket != null){
                			sendOFPacket(socket, ofpoe.getData());
                    	}                		
                		/** for debug */
                		//sendReportToUI("Send OFReply packet = " + ofpoe.getOFMessage().toString());
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
        
        stopOpenflowController();
        // Tell the user we stopped.
        Toast.makeText(this, R.string.openflow_channel_stopped, Toast.LENGTH_SHORT).show();
        
    }
    public void sendReportToUI(String str){
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
    public void sendOFEventToDispatchService(Integer remotePort, byte[] ofdata){
    	Gson gson = new Gson();
    	for (int i=mClients.size()-1; i>=0; i--) {
            try {
            	Message msg = Message.obtain(null, HolyCMessage.OFCOMM_EVENT.type);
            	OFEvent ofe = new OFEvent(remotePort.intValue(), ofdata);
            	//sendReportToUI("Recevie OFMessage: " + ofe.getOFMessage().toString());
            	Log.d(TAG, "Recevie OFMessage: " + ofe.getOFMessage().toString());
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
    
    public void startOpenflowController(){    	
        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    	sendReportToUI("Start Controller Daemon");    	
    }
    public void stopOpenflowController(){
    	 if (mAcceptThread != null) {
             mAcceptThread.close();
             mAcceptThread = null;
         }
    }    
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }     
    
    private void sendOFPacket(Socket socket, byte[] data ){
    	try {
			OutputStream out = socket.getOutputStream();
			out.write(data);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private class AcceptThread extends Thread {
        // The local server socket
        private final ServerSocket mmServerSocket;

        public AcceptThread() {
            ServerSocket tmp = null;
            try {
                tmp = new ServerSocket(bind_port);
                tmp.setReuseAddress(true);                
            } catch (IOException e) {
                System.err.println("Could not open server socket");
                e.printStackTrace(System.err);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            setName("HolycAccpetThread");            
            while (true) {
            	Socket socket = null;
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    Log.d(TAG, "waiting for openflow client ...");
                    socket = mmServerSocket.accept();
                    Log.d(TAG, "Client connected!");
                    
                } catch (SocketException e) {
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket == null) {
                    break;
                }
                
                //immediately send an OFHello back
        		OFHello ofh = new OFHello();
        		ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
        		ofh.writeTo(bb);
        		sendOFPacket(socket, bb.array());
                
        		Integer remotePort = new Integer(socket.getPort());
                socketMap.put(remotePort, socket);
                ConnectedThread conThread = new ConnectedThread(remotePort, socket);
                conThread.start();
            }
            Log.d(TAG, "END mAcceptThread");
        }
        public void close() {
            Log.d(TAG, "close " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }
    
    private class ConnectedThread extends Thread {
        private final Socket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final int BUFFER_LENGTH = 2048;
        private final Integer mRemotePort;

        public ConnectedThread(Integer remotePort, Socket socket) {
        	mRemotePort = new Integer(remotePort);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                mmSocket.setTcpNoDelay(true);
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[BUFFER_LENGTH];
            int bytes;

            if (mmInStream == null || mmOutStream == null)
                return;

            // Receive until client closes connection, indicated by -1
            try{
	            while (( bytes = mmInStream.read(buffer)) != -1) {
	            	byte[] OFdata = new byte[bytes];
	            	System.arraycopy(buffer, 0, OFdata, 0, bytes);
	            	// TODO: based on header to retrieve correct packet length 
	            	sendOFEventToDispatchService(mRemotePort, OFdata);
	            }
            }catch (Exception e) {
                Log.e(TAG, "Error reading connection header", e);
            }
            close();
        }

        public void close() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

}



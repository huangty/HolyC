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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.beaconcontroller.packet.Ethernet;
import net.beaconcontroller.packet.IPv4;
import net.holyc.HolyCMessage;
import net.holyc.R;
import net.holyc.dispatcher.OFEvent;
import net.holyc.dispatcher.OFPacketInEvent;
import net.holyc.dispatcher.OFReplyEvent;
import net.holyc.host.EnvInitService;
import net.holyc.host.Utility;

import org.openflow.protocol.OFFeaturesRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFGetConfigRequest;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.OFActionFactory;
import org.openflow.protocol.factory.OFMessageFactory;
import org.openflow.util.U16;

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

public class OFCommService extends Service {
	int bind_port = 6633;
	ServerSocketChannel ctlServer = null;
	Selector selector = null;
	String TAG = "HOLYC.OFCOMM";
	public static Map<Integer, Socket> socketMap = new HashMap<Integer, Socket>();
	AcceptThread mAcceptThread = null;
	/** For showing and hiding our notification. */
	NotificationManager mNM;
	/** Keeps track of all current registered clients. */
	ArrayList<Messenger> mClients = new ArrayList<Messenger>();

	/** for debug **/
	public static HashMap<Integer, Delay> procDelayTable = new HashMap<Integer, Delay>();

	/** Openflow Flow Rule Priority */
	public static final short HIGH_PRIORITY = (short) 65535;
	public static final short MID_PRIORITY = (short) 32767;
	public static final short LOW_PRIORITY = (short) 0;

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
				// sendReportToUI("Bind on port: " + bind_port);
				Log.d(TAG, "Send msg on bind: " + bind_port);
				startOpenflowController();
				break;
			case HolyCMessage.OFREPLY_EVENT.type:
				byte[] ofdata = msg.getData().getByteArray(
						HolyCMessage.OFREPLY_EVENT.data_key);
				int scn = msg.getData().getInt(
						HolyCMessage.OFREPLY_EVENT.port_key);
				/** for debug **/
				OFMessage ofm = new OFMessage();
				ofm.readFrom(Utility.getByteBuffer(ofdata));
				if (ofm.getType() == OFType.FLOW_MOD) {
					OFFlowMod offm = new OFFlowMod();
					BasicFactory bf = new BasicFactory();
					offm.setActionFactory(bf.getActionFactory());
					ByteBuffer bb = ByteBuffer.allocate(ofdata.length);
					bb.put(ofdata);
					bb.flip();
					offm.readFrom(bb);
					if (offm.getBufferId() != -1
							&& procDelayTable.containsKey(offm.getBufferId())) {
						Delay d = procDelayTable.get(offm.getBufferId());
						Log.d(TAG, "buffer ID:" + offm.getBufferId()
								+ " delay = " + d.getDelay(new Date()));
						procDelayTable.remove(offm.getBufferId());
					}
				}
				// Log.d(TAG,
				// "Send OFReply through socket channel with Remote Port "+scn);
				if (!socketMap.containsKey(new Integer(scn))) {
					Log.e(TAG, "there is no SocketChannel left");
				} else {
					Socket socket = socketMap.get(new Integer(scn));
					if (socket != null) {
						// sendOFPacket(socket, ofpoe.getData());
						sendOFPacket(socket, ofdata);
					} else {
						socketMap.remove(new Integer(scn));
					}
					/** for debug */
					// sendReportToUI("Send OFReply packet = " +
					// ofpoe.getOFMessage().toString());
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
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		startForeground(0, null);
	}

	@Override
	public void onDestroy() {
		// Cancel the persistent notification.
		mNM.cancel(R.string.openflow_channel_started);
		// close server socket before leaving the service
		try {
			if (ctlServer != null && ctlServer.isOpen()) {
				ctlServer.socket().close();
				ctlServer.close();
			}
		} catch (IOException e) {
		}

		stopOpenflowController();
		// Tell the user we stopped.
		Toast.makeText(this, R.string.openflow_channel_stopped,
				Toast.LENGTH_SHORT).show();

	}

	public void sendReportToUI(String str) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Message msg = Message.obtain(null,
						HolyCMessage.UIREPORT_UPDATE.type);
				Bundle data = new Bundle();
				data.putString(HolyCMessage.UIREPORT_UPDATE.str_key, str
						+ "\n -------------------------------");
				msg.setData(data);
				mClients.get(i).send(msg);
			} catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	public void sendOFEventToDispatchService(Integer remotePort, byte[] ofdata) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Message msg = Message.obtain(null,
						HolyCMessage.OFCOMM_EVENT.type);
				/** for debug **/
				OFEvent ofe = new OFEvent(remotePort.intValue(), ofdata);
				if (ofe.getOFMessage().getType() == OFType.PACKET_IN) {
					Delay d = new Delay(ofe.getOFMessage(), new Date());
					OFPacketInEvent opie = new OFPacketInEvent(ofe);
					int bufid = opie.getOFPacketIn().getBufferId();
					if (bufid != -1) {
						procDelayTable.put(bufid, d);
					}
				}
				// sendReportToUI("Recevie OFMessage: " +
				// ofe.getOFMessage().toString());
				// Log.d(TAG, "Recevie OFMessage: " +
				// ofe.getOFMessage().toString());
				// Log.d(TAG, "OFMessage length = " +
				// ofe.getOFMessage().getLength() + "  ofdata length = " +
				// ofdata.length);
				Bundle data = new Bundle();
				data.putByteArray(HolyCMessage.OFCOMM_EVENT.data_key, ofdata);
				data.putInt(HolyCMessage.OFCOMM_EVENT.port_key,
						remotePort.intValue());
				msg.setData(data);
				mClients.get(i).send(msg);

			} catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	public void startOpenflowController() {
		mAcceptThread = new AcceptThread();
		mAcceptThread.start();
		// sendReportToUI("Start Controller Daemon");
		Log.d(TAG, "Start Controller Daemon");
	}

	public void stopOpenflowController() {
		if (mAcceptThread != null) {
			mAcceptThread.close();
			mAcceptThread = null;
		}
	}

	/**
	 * When binding to the service, we return an interface to our messenger for
	 * sending messages to the service.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	private void sendOFPacket(Socket socket, byte[] data) {
		if (socket == null) {
			return;
		}
		try {
			OutputStream out = socket.getOutputStream();
			out.write(data);
			out.flush();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Integer remotePort = new Integer(socket.getPort());
			socketMap.remove(remotePort);
			socket = null;
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
					// Log.d(TAG, "waiting for openflow client ...");
					socket = mmServerSocket.accept();
					socket.setTcpNoDelay(true);
					Log.d(TAG, "Openflow client connected.");

				} catch (SocketException e) {
				} catch (IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket == null) {
					break;
				}

				// immediately send an OFHello back
				OFHello ofh = new OFHello();
				ByteBuffer bb = ByteBuffer.allocate(ofh.getLength());
				ofh.writeTo(bb);
				sendOFPacket(socket, bb.array());
				
				//immediately send an OF feature request
				OFFeaturesRequest offr = new OFFeaturesRequest();
				ByteBuffer bbfr = ByteBuffer.allocate(offr.getLength());
				offr.writeTo(bbfr);
				sendOFPacket(socket, bbfr.array());
				
				OFGetConfigRequest ofgcr = new OFGetConfigRequest();
				ByteBuffer bbgcr = ByteBuffer.allocate(ofgcr.getLength());
				ofgcr.writeTo(bbgcr);
				sendOFPacket(socket, bbgcr.array());
				
				// insert default rules
				insertDefaultRule(socket);

				Integer remotePort = new Integer(socket.getPort());
				socketMap.put(remotePort, socket);
				ConnectedThread conThread = new ConnectedThread(remotePort,
						socket);
				conThread.start();
			}
			Log.d(TAG, "END mAcceptThread");
		}

		public void dropUnwantedUdp(short port_src, short port_dst,
				short priority, Socket socket) {
			ByteBuffer bb;
			OFFlowMod offm = new OFFlowMod();
			OFMatch ofm = new OFMatch();
			if (port_src != -1 && port_dst != -1) {
				ofm.setTransportDestination(port_dst);
				ofm.setTransportSource(port_src);
				ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
						& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST
						& ~OFMatch.OFPFW_TP_SRC);
			} else if (port_src != -1) {
				ofm.setTransportSource(port_src);
				ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
						& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_SRC);
			} else if (port_dst != -1) {
				ofm.setTransportDestination(port_dst);
				ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
						& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST);
			}
			ofm.setNetworkProtocol((byte) 0x11); // udp
			ofm.setDataLayerType((short) 0x0800); // ip

			offm.setMatch(ofm);
			offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
			offm.setBufferId(-1);
			offm.setCommand(OFFlowMod.OFPFC_ADD);
			offm.setIdleTimeout((short) 0);
			offm.setHardTimeout((short) 0); // make the flow entry permenent
			offm.setFlags((short) 1); // Send flow removed
			offm.setPriority(priority);
			offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH)); // +OFActionOutput.MINIMUM_LENGTH));
			bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(bb);
			sendOFPacket(socket, bb.array());
		}

		public void dropAll(short dl_type, byte nw_proto, short priority,
				Socket socket) {
			OFFlowMod offm = new OFFlowMod();
			OFMatch ofm = new OFMatch();
			ofm.setDataLayerType(dl_type);
			if (dl_type == 0x0806) { // not need to match on network protocol
										// for arp
				ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE);
			} else {
				ofm.setNetworkProtocol(nw_proto);
				ofm.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
						& ~OFMatch.OFPFW_NW_PROTO);
			}
			offm.setMatch(ofm);
			offm.setOutPort((short) OFPort.OFPP_NONE.getValue());
			offm.setBufferId(-1);
			offm.setCommand(OFFlowMod.OFPFC_ADD);
			offm.setIdleTimeout((short) 0);
			offm.setHardTimeout((short) 0); // make the flow entry permenent
			offm.setFlags((short) 1); // Send flow removed
			offm.setPriority(priority);
			offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH)); // +OFActionOutput.MINIMUM_LENGTH));
			ByteBuffer bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(bb);
			sendOFPacket(socket, bb.array());
		}

		public void arpFwdToController(int host_ip, short priority,
				Socket socket) {
			OFActionOutput arp_action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// arp destinated to the host
			OFMatch arp_match = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_DST_SHIFT))
					.setNetworkDestination(host_ip)
					.setDataLayerType((short) 0x0806);// arp

			OFFlowMod arp_fm = new OFFlowMod();

			arp_fm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(arp_match)
					.setActions(
							Collections.singletonList((OFAction) arp_action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer arp_bb = ByteBuffer.allocate(arp_fm.getLength());
			arp_fm.writeTo(arp_bb);
			sendOFPacket(socket, arp_bb.array());

			// arp originated from the host
			arp_match = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT))
					.setNetworkSource(host_ip).setDataLayerType((short) 0x0806);// arp
			arp_fm.setMatch(arp_match);
			arp_bb = ByteBuffer.allocate(arp_fm.getLength());
			arp_fm.writeTo(arp_bb);
			sendOFPacket(socket, arp_bb.array());
		}
		
		public void fwdArpVethToController(short priority, Socket socket) {
			OFActionOutput arp_action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// arp destinated to the host
			OFMatch arp_match = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~OFMatch.OFPFW_IN_PORT )
					.setInputPort((short) 1) //from virtual interface
					.setDataLayerType((short) 0x0806);// arp

			OFFlowMod arp_fm = new OFFlowMod();

			arp_fm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(arp_match)
					.setActions(
							Collections.singletonList((OFAction) arp_action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer arp_bb = ByteBuffer.allocate(arp_fm.getLength());
			arp_fm.writeTo(arp_bb);
			sendOFPacket(socket, arp_bb.array());			
		}
		
		public void tcpFwdToController(int host_ip, short priority,
				Socket socket) {
			OFActionOutput action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// tcp destinated to the host
			OFMatch tcp_match_dst = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_DST_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkDestination(host_ip)
					.setNetworkProtocol((byte) 0x06) // tcp
					.setDataLayerType((short) 0x0800); // ip

			OFFlowMod offm = new OFFlowMod();

			offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(tcp_match_dst)
					.setActions(Collections.singletonList((OFAction) action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer tcp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(tcp_bb);
			sendOFPacket(socket, tcp_bb.array());

			// tcp originated from the host
			OFMatch tcp_match_src = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkSource(host_ip).setNetworkProtocol((byte) 0x06) // tcp
					.setDataLayerType((short) 0x0800); // ip

			offm.setMatch(tcp_match_src);
			tcp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(tcp_bb);
			sendOFPacket(socket, tcp_bb.array());
		}

		public void udpFwdToController(int host_ip, short priority,
				Socket socket) {
			OFActionOutput action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// udp destinated to the host
			OFMatch udp_match_dst = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_DST_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkDestination(host_ip)
					.setNetworkProtocol((byte) 0x11) // udp
					.setDataLayerType((short) 0x0800); // ip

			OFFlowMod offm = new OFFlowMod();

			offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(udp_match_dst)
					.setActions(Collections.singletonList((OFAction) action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer udp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(udp_bb);
			sendOFPacket(socket, udp_bb.array());

			// udp originated from the host
			OFMatch udp_match_src = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkSource(host_ip).setNetworkProtocol((byte) 0x11) // udp
					.setDataLayerType((short) 0x0800); // ip

			offm.setMatch(udp_match_src);
			udp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(udp_bb);
			sendOFPacket(socket, udp_bb.array());

			// broadcast udp
			OFMatch udp_match_broadcast = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~OFMatch.OFPFW_DL_DST
									& ~OFMatch.OFPFW_NW_PROTO)
					.setDataLayerDestination("ff:ff:ff:ff:ff:ff") // broadcast
					.setNetworkProtocol((byte) 0x11) // udp
					.setDataLayerType((short) 0x0800); // ip

			offm.setMatch(udp_match_broadcast);
			udp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(udp_bb);
			sendOFPacket(socket, udp_bb.array());
		}

		public void icmpFwdToController(int host_ip, short priority,
				Socket socket) {
			OFActionOutput action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// icmp destinated to the host
			OFMatch icmp_match_dst = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_DST_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkDestination(host_ip)
					.setNetworkProtocol((byte) 0x01) // icmp
					.setDataLayerType((short) 0x0800); // ip

			OFFlowMod offm = new OFFlowMod();

			offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(icmp_match_dst)
					.setActions(Collections.singletonList((OFAction) action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer icmp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(icmp_bb);
			sendOFPacket(socket, icmp_bb.array());

			// udp originated from the host
			OFMatch udp_match_src = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~(63 << OFMatch.OFPFW_NW_SRC_SHIFT)
									& ~OFMatch.OFPFW_NW_PROTO)
					.setNetworkSource(host_ip).setNetworkProtocol((byte) 0x01) // icmp
					.setDataLayerType((short) 0x0800); // ip

			offm.setMatch(udp_match_src);
			icmp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(icmp_bb);
			sendOFPacket(socket, icmp_bb.array());

		}

		public void hostTrafficFwdToController(short priority, Socket socket) {
			OFActionOutput action = new OFActionOutput().setPort(
					OFPort.OFPP_CONTROLLER.getValue()).setMaxLength(
					(short) 65535);
			// udp sent from the host
			OFMatch udp_match_dst = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
									& ~OFMatch.OFPFW_NW_PROTO
									& ~OFMatch.OFPFW_IN_PORT)
					.setInputPort((short) 1) // from the host
					.setNetworkProtocol((byte) 0x11) // udp
					.setDataLayerType((short) 0x0800); // ip

			OFFlowMod offm = new OFFlowMod();

			offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(udp_match_dst)
					.setActions(Collections.singletonList((OFAction) action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer udp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(udp_bb);
			sendOFPacket(socket, udp_bb.array());
			
			//tcp sent from the host 
			OFMatch tcp_match_dst = new OFMatch()
					.setWildcards(
							OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
							& ~OFMatch.OFPFW_NW_PROTO
							& ~OFMatch.OFPFW_IN_PORT)
					.setInputPort((short) 1) // from the host
					.setNetworkProtocol((byte) 0x06) // tcp
					.setDataLayerType((short) 0x0800); // ip
			
			OFFlowMod tcp_offm = new OFFlowMod();

			tcp_offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(tcp_match_dst)
					.setActions(Collections.singletonList((OFAction) action))
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH));

			ByteBuffer tcp_bb = ByteBuffer.allocate(offm.getLength());
			tcp_offm.writeTo(tcp_bb);
			sendOFPacket(socket, tcp_bb.array());
		}

		public void fwdPktToVeth(short inport, short priority, Socket socket){
			OFActionOutput output_action = new OFActionOutput()
					.setPort((short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.vIFs.getVeth0().getName()))					
					.setMaxLength((short) 65535);
			
			byte[] mac_veth = Ethernet.toMACAddress(EnvInitService.vIFs.getVeth1().getMac());
			OFActionDataLayerDestination mod_dl_dst = new OFActionDataLayerDestination();
			mod_dl_dst.setDataLayerAddress(mac_veth);
			
			OFActionNetworkLayerDestination mod_nw_dst = new OFActionNetworkLayerDestination();
			mod_nw_dst.setNetworkAddress(IPv4.toIPv4Address(EnvInitService.vIFs.getVeth1().getIP()));
			
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(mod_dl_dst);
			actions.add(mod_nw_dst);
			actions.add(output_action);
						
			OFMatch ofm = new OFMatch()
					.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_IN_PORT)
					.setInputPort((short) inport);					

			OFFlowMod offm = new OFFlowMod();

			offm.setBufferId(-1)
					.setIdleTimeout((short) 0)
					.setHardTimeout((short) 0)
					.setOutPort((short) OFPort.OFPP_NONE.getValue())
					.setCommand(OFFlowMod.OFPFC_ADD)
					.setMatch(ofm)
					.setActions(actions)
					.setPriority(priority)
					.setLength(
							U16.t(OFFlowMod.MINIMUM_LENGTH
									+ OFActionOutput.MINIMUM_LENGTH+OFActionDataLayerDestination.MINIMUM_LENGTH
									+OFActionNetworkLayerDestination.MINIMUM_LENGTH));

			ByteBuffer udp_bb = ByteBuffer.allocate(offm.getLength());
			offm.writeTo(udp_bb);
			sendOFPacket(socket, udp_bb.array());
		}
		
		public void insertDefaultRule(Socket socket) {
			/**
			 * drop malicious traffic udp srcPort 138 and dstPort 138 udp
			 * dstPort 137 udp srcPort 17500 and dstPort 17500
			 * **/

			dropUnwantedUdp((short) 138, (short) 138, HIGH_PRIORITY, socket); // netbios
			dropUnwantedUdp((short) -1, (short) 137, HIGH_PRIORITY, socket); // netbios
			dropUnwantedUdp((short) -1, (short) 111, HIGH_PRIORITY, socket); // sun-remote
																				// control
			dropUnwantedUdp((short) 17500, (short) 17500, HIGH_PRIORITY, socket); // trajan
																					// horse
			dropUnwantedUdp((short) 631, (short) 631, HIGH_PRIORITY, socket); // Internet
																				// Printing
																				// Protocol

			OFMessageFactory messageFactory = new BasicFactory();
			OFActionFactory actionFactory = new BasicFactory();

			
			//hostTrafficFwdToController((short) (MID_PRIORITY + 2), socket);
			
			// drop all other dhcp discovery
			dropUnwantedUdp((short) 68, (short) 67, (short) (MID_PRIORITY + 1),
					socket); // dhcp discovery from others

			if(EnvInitService.isMultipleInterface){
				fwdArpVethToController( (short)(MID_PRIORITY+1), socket);
				//data/local/bin/ovs-ofctl add-flow dp0 in_port=2,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
				/*if(EnvInitService.wifi_included){
					fwdPktToVeth((short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wifiIF.getName()), (short)(MID_PRIORITY+1), socket);
				}
				//data/local/bin/ovs-ofctl add-flow dp0 in_port=3,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
				if(EnvInitService.mobile_included){
					if(EnvInitService.threeGIF.isPointToPoint()){
						fwdPktToVeth((short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.veth2.getName()), (short)(MID_PRIORITY+1), socket);
					}else{
						fwdPktToVeth((short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.threeGIF.getName()), (short)(MID_PRIORITY+1), socket);
					}
				}
				//data/local/bin/ovs-ofctl add-flow dp0 in_port=4,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
				if(EnvInitService.wimax_included){
					fwdPktToVeth((short) EnvInitService.ovs.dptable.get("dp0").indexOf(EnvInitService.wimaxIF.getName()), (short)(MID_PRIORITY+1), socket);
				}*/
				/* TODO:
				 /data/local/bin/ovs-ofctl add-flow dp0 in_port=1,tcp,priority=60000,idle_timeout=0,hard_timeout=0,actions=mod_dl_src:$MAC_WIFI,mod_nw_src:$IP_WIFI,mod_dl_dst:$MAC_WIFI_GW,output:2
				 /data/local/bin/ovs-ofctl add-flow dp0 in_port=1,idle_timeout=0,hard_timeout=0,actions=mod_dl_src:$MAC_MOBILE,mod_nw_src:$IP_MOBILE,mod_dl_dst:$MAC_MOBILE_GW,output:3
 				 /data/local/bin/ovs-ofctl add-flow dp0 in_port=2,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
				 /data/local/bin/ovs-ofctl add-flow dp0 in_port=3,idle_timeout=0,hard_timeout=0,actions=mod_dl_dst:$MAC_VETH,mod_nw_dst:$IP_VETH,output:1
			    */				
			}
			
			
			/*List<String> myIPs = Utility.getDeviceIPs();
			Log.d(TAG, "the host has " + myIPs.size() + " ips");
			Iterator<String> it = myIPs.iterator();
			while (it.hasNext()) { // for each IP
				String ipS = it.next();
				// Log.d(TAG, "ip #: " + ipS);
				int ip = IPv4.toIPv4Address(ipS);
				Log.d(TAG, "install rule for ip #: " + IPv4.fromIPv4Address(ip));
				// forward related arp to controller
				arpFwdToController(ip, MID_PRIORITY, socket);
				// forward related tcp to controller
				tcpFwdToController(ip, MID_PRIORITY, socket);
				// forward related udp to controller
				udpFwdToController(ip, MID_PRIORITY, socket);
				// forward related icmp to controller
				icmpFwdToController(ip, MID_PRIORITY, socket);
			}*/
			
			

			// drop all the unrelated traffic (lowest priority)
			dropAll((short) 0x0806, (byte) 0x00, LOW_PRIORITY, socket); // arp
			//dropAll((short) 0x0800, (byte) 0x06, LOW_PRIORITY, socket); // ip, tcp
			//dropAll((short) 0x0800, (byte) 0x11, LOW_PRIORITY, socket); // ip, udp
			//dropAll((short) 0x0800, (byte) 0x01, LOW_PRIORITY, socket); // ip, icmp
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
			byte[] leftOverData = new byte[0];
			// Log.d(TAG, "leftOverData size = " + leftOverData.length);
			try {
				while ((bytes = mmInStream.read(buffer)) != -1) {
					byte[] ofdata = new byte[bytes + leftOverData.length];
					// copy leftOverData to the beginning of OFdata if there is
					// any
					if (leftOverData.length > 0) {
						System.arraycopy(leftOverData, 0, ofdata, 0,
								leftOverData.length);
						System.arraycopy(buffer, 0, ofdata,
								leftOverData.length, bytes);
						leftOverData = new byte[0];
					} else {
						System.arraycopy(buffer, 0, ofdata, 0, bytes);
					}
					while (ofdata.length > 0) {
						// for each message, get the packet length, which is the
						// 3rd and 4th bytes in the OF Header
						ByteBuffer bb = ByteBuffer.allocate(2);
						bb.put(ofdata[2]);
						bb.put(ofdata[3]);
						bb.flip();
						short length = bb.getShort();
						if (ofdata.length >= length) {
							byte[] ofmessage = new byte[length];
							System.arraycopy(ofdata, 0, ofmessage, 0, length);
							// send data up to Dispatch Service
							sendOFEventToDispatchService(mRemotePort, ofmessage);
							int leftOverLen = (ofdata.length - length);
							byte[] temp = new byte[leftOverLen];
							System.arraycopy(ofdata, length, temp, 0,
									leftOverLen);
							ofdata = temp;
						} else {
							leftOverData = new byte[ofdata.length];
							System.arraycopy(ofdata, 0, leftOverData, 0,
									ofdata.length);
							ofdata = new byte[0];
							// Log.d(TAG, "there are left over, with size = " +
							// leftOverData.length);
						}
					}
					// Log.d(TAG,
					// "Finish retrieve data from buffer, read one more time");
				}
			} catch (Exception e) {
				Log.e(TAG, "Error reading for client connection", e);
			}
			close();
		}

		public void close() {
			try {
				socketMap.remove(mRemotePort);
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

}

class Delay {
	private OFMessage ofm;
	private Date time_pktin;

	public Delay(OFMessage _ofm, Date time) {
		this.ofm = _ofm;
		time_pktin = time;
	}

	public long getDelay(Date now) {
		return now.getTime() - time_pktin.getTime();
	}

	public String toString() {
		return ofm.toString() + " startTime: " + time_pktin.toLocaleString();
	}
}

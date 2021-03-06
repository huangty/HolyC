package net.holyc.openflow.handler;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;

import net.holyc.HolyCIntent;
import net.holyc.host.Utility;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** A L2 flow-based learning switch
 *
 * @author ykk
 */

public class FlowSwitch
    extends BroadcastReceiver
{
    /** Log name
     */
    private String TAG = "HOLYC.FlowSwitch";

    /** MAC address and port association
     */
    public static HashMap<String, Short> hostPort = new HashMap<String, Short>();
   
    @Override    
	public void onReceive(Context context, Intent intent) 
    {
	if(intent.getAction().equals(HolyCIntent.OFPacketIn_Intent.action)){		
		byte[] ofdata = intent.getByteArrayExtra(HolyCIntent.OFPacketIn_Intent.data_key);
		int port = intent.getIntExtra(HolyCIntent.OFPacketIn_Intent.port_key, -1);
		OFPacketIn opie = new OFPacketIn();
		opie.readFrom(Utility.getByteBuffer(ofdata));
	    
	    //Record port and entry
	    OFMatch ofm = new OFMatch();
	    ofm.loadFromPacket(opie.getPacketData(), opie.getInPort());
	    hostPort.put(HexString.toHexString(ofm.getDataLayerSource()), 
			 new Short(ofm.getInputPort()));

	    //Find outport if any
	    Short out;
	    if (HexString.toHexString(ofm.getDataLayerDestination()).equals("ff:ff:ff:ff:ff:ff"))
		out = new Short(OFPort.OFPP_FLOOD.getValue());
	    else
		out = hostPort.get(HexString.toHexString(ofm.getDataLayerDestination()));

	    //Send Query  
	    long cookie = sendQuery(ofm, context);
	    //Send response
	    
	    Intent poutIntent = new Intent(HolyCIntent.BroadcastOFReply.action);
	    poutIntent.setPackage(context.getPackageName());
	    ByteBuffer bb = getResponse(out, opie, ofm, cookie);	    
	    poutIntent.putExtra(HolyCIntent.BroadcastOFReply.data_key, bb.array());
	    poutIntent.putExtra(HolyCIntent.BroadcastOFReply.port_key, port);	    
	    context.sendBroadcast(poutIntent);
	}
    }    


    public ByteBuffer getResponse(Short out, OFPacketIn opie, OFMatch ofm, long cookie )
    {
	OFActionOutput oao = new OFActionOutput();	    
	oao.setMaxLength((short) 0);     
	List<OFAction> actions = new ArrayList<OFAction>();
	ByteBuffer bb;
	/** to handle DHCP specially, always broadcast a dhcp packet**/
	
	if (out != null)
	{		
	    //Form flow_mod
	    oao.setPort(out.shortValue());
	    OFFlowMod offm = new OFFlowMod();
	    actions.add(oao);
	    offm.setActions(actions);
	    offm.setMatch(ofm);
	    offm.setOutPort((short) OFPort.OFPP_NONE.getValue());                              
	    offm.setBufferId(opie.getBufferId());
	    offm.setCommand(OFFlowMod.OFPFC_ADD);
	    offm.setIdleTimeout((short) 5);
	    offm.setHardTimeout((short) 0);
	    offm.setPriority((short) 32768);
	    offm.setFlags((short) 1); //Send flow removed
	    offm.setCookie(cookie);	    
	    offm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH));	    
	    bb = ByteBuffer.allocate(offm.getLength());
	    offm.writeTo(bb);
	    //Log.d(TAG, "Buffer ID: " + opie.getBufferId());
	    if(opie.getBufferId() == -1){ 
	    	//if the switch doesn't buffer the packet
	    	OFPacketOut opo = new OFPacketOut();		   
		    opo.setActions(actions);
		    opo.setInPort(opie.getInPort());
		    opo.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		    opo.setBufferId(opie.getBufferId());
		    opo.setPacketData(opie.getPacketData());
		    int length = OFPacketOut.MINIMUM_LENGTH + opo.getActionsLengthU() + opie.getPacketData().length;		
		    //opo.setLengthU(length);
		    //ByteBuffer bb_ofout = ByteBuffer.allocate(opo.getLengthU());
		    opo.setLength(U16.t(length));
		    ByteBuffer bb_ofout = ByteBuffer.allocate(opo.getLengthU());
		    bb_ofout.clear();		    
		    opo.writeTo(bb_ofout);
		    
		    ByteBuffer bb_out = ByteBuffer.allocate(opo.getLengthU() + offm.getLength());
		    bb_out.clear();
		    bb_out.put(bb.array());
		    bb_out.put(bb_ofout.array());
		    bb_out.flip();
		    bb = bb_out;
	    }	    
	}
	else
	{
		//Flood packet
	    oao.setPort(OFPort.OFPP_FLOOD.getValue()); //Flood port
	    OFPacketOut opo = new OFPacketOut();
	    actions.add(oao);
	    opo.setActions(actions);
	    opo.setInPort(opie.getInPort());
	    opo.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
	    opo.setBufferId(opie.getBufferId());
	    int length = OFPacketOut.MINIMUM_LENGTH + opo.getActionsLengthU();
	    if (opie.getBufferId()==-1)
		opo.setPacketData(opie.getPacketData());
	    length += opie.getPacketData().length;		
	    //opo.setLengthU(length);
	    opo.setLength(U16.t(length));
	    bb = ByteBuffer.allocate(length);
	    opo.writeTo(bb);
	}
	return bb;
    }

    public int sendQuery(OFMatch ofm, Context context)
    {
	return 0;
    }
        
}

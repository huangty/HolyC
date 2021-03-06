package org.sqlite.helper;

import android.content.ContentValues;

import org.openflow.protocol.*;
import org.openflow.util.*;

/** Helper class for OpenFlow related columns
 *
 * @author ykk
 * @date Apr 2011
 */
public class OpenFlow
{
    /** Array of names for ofp_match
     */
    public static String[] OFMATCH_NAMES;
    static 
    {
	OFMATCH_NAMES = new String[] { "Wildcard",
				       "Input_Port",
				       "DL_Source",
				       "DL_Destination",
				       "DL_VLAN",
				       "DL_VLAN_PRORITY",
				       "DL_Type",
				       "NW_Source",
				       "NW_Destination",
				       "NW_Protocol",
				       "NW_TOS",
				       "TP_Source",
				       "TP_Destination" };
    }

    /** Array of names for ofp_flow_removed
     * minus ofp_match
     */
    public static String[] OFFLOWREMOVED_NAMES;
    static 
    {
	OFFLOWREMOVED_NAMES = new String[] { "Cookie",
				       "Priority",
				       "Reason",
				       "Duration",
				       "Idle_Timeout",
				       "Packet_Count",
				       "Byte_Count" };
    }

    /** Add value to ContentValues
     *
     * Uses new OFMatch is match is null in OFFlowRemoved.
     * Reason is -1 if reason is null (a corner case).
     * Stores duration as real number.
     *
     * @param cv contentvalues to add to
     * @param ofr OpenFlow flow removed
     */
    public static void addOFFlowRemoved2CV(ContentValues cv, OFFlowRemoved ofr)
    {
	OFMatch ofm = ofr.getMatch();
	if (ofm == null)
	    ofm = new OFMatch();
	addOFMatch2CV(cv, ofm);

	cv.put(OFFLOWREMOVED_NAMES[0], ofr.getCookie());
	cv.put(OFFLOWREMOVED_NAMES[1], U16.f(ofr.getPriority()));
	OFFlowRemoved.OFFlowRemovedReason ofrr = ofr.getReason();
	if (ofrr == null)
	    cv.put(OFFLOWREMOVED_NAMES[2], -1);
	else
	    cv.put(OFFLOWREMOVED_NAMES[2], ofrr.ordinal());
	if (ofrr == OFFlowRemoved.OFFlowRemovedReason.OFPRR_IDLE_TIMEOUT)
	    cv.put(OFFLOWREMOVED_NAMES[3], (double) (U32.f(ofr.getDurationSeconds())-
						     U16.f(ofr.getIdleTimeout())+ 
						     (U32.f(ofr.getDurationNanoseconds())/
						      1e9)));
	else
	    cv.put(OFFLOWREMOVED_NAMES[3], (double) (U32.f(ofr.getDurationSeconds())+ 
						     (U32.f(ofr.getDurationNanoseconds())/
						      1e9)));
	cv.put(OFFLOWREMOVED_NAMES[4], U16.f(ofr.getIdleTimeout()));
	cv.put(OFFLOWREMOVED_NAMES[5], ofr.getPacketCount());
	cv.put(OFFLOWREMOVED_NAMES[6], ofr.getByteCount());
    }
    
    /** Add value to ContentValues
     *
     * @param cv contentvalues to add to
     * @param ofm OpenFlow match
     */
    public static void addOFMatch2CV(ContentValues cv, OFMatch ofm)
    {
	cv.put(OFMATCH_NAMES[0], U32.f(ofm.getWildcards()));
	cv.put(OFMATCH_NAMES[1], U16.f(ofm.getInputPort()));
	cv.put(OFMATCH_NAMES[2], HexString.toHexString(ofm.getDataLayerSource()));
	cv.put(OFMATCH_NAMES[3], HexString.toHexString(ofm.getDataLayerDestination()));
	cv.put(OFMATCH_NAMES[4], U16.f(ofm.getDataLayerVirtualLan()));
	cv.put(OFMATCH_NAMES[5], U8.f(ofm.getDataLayerVirtualLanPriorityCodePoint()));
	cv.put(OFMATCH_NAMES[6], U16.f(ofm.getDataLayerType()));
	cv.put(OFMATCH_NAMES[7], U32.f(ofm.getNetworkSource()));
	cv.put(OFMATCH_NAMES[8], U32.f(ofm.getNetworkDestination()));
	cv.put(OFMATCH_NAMES[9], U8.f(ofm.getNetworkProtocol()));
	cv.put(OFMATCH_NAMES[10], U8.f(ofm.getNetworkTypeOfService()));
	cv.put(OFMATCH_NAMES[11], U16.f(ofm.getTransportSource()));
	cv.put(OFMATCH_NAMES[12], U16.f(ofm.getTransportDestination()));
    }

    /** Add ofp_match fields to database
     *
     * @param tab table to put ofp_match into
     */
    public static void addOFMatch2Table(SQLiteTable tab)
    {
	for (int i = 0; i < OFMATCH_NAMES.length; i++)
	    if (OFMATCH_NAMES[i] == "DL_Source" || 
		OFMATCH_NAMES[i] == "DL_Destination")
		tab.addColumn(OFMATCH_NAMES[i], SQLiteTable.DataType.TEXT);
	    else
		tab.addColumn(OFMATCH_NAMES[i], SQLiteTable.DataType.INTEGER);
    }

    /** Add ofp_flow_removed fields to database
     *
     * @param tab table to put ofp_match into
     */
    public static void addOFFlowRemoved2Table(SQLiteTable tab)
    {
	addOFMatch2Table(tab);
	for (int i = 0; i < OFFLOWREMOVED_NAMES.length; i++)
	    if (OFFLOWREMOVED_NAMES[i] == "Duration")
		tab.addColumn(OFFLOWREMOVED_NAMES[i], SQLiteTable.DataType.REAL);
	    else
		tab.addColumn(OFFLOWREMOVED_NAMES[i], SQLiteTable.DataType.INTEGER);
    }


}
package net.holyc.lal;

import com.google.gson.Gson;

import org.openflow.protocol.OFFlowRemoved;

import org.sqlite.helper.OpenFlow;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFFlowRemovedEvent;

import edu.stanford.lal.Database;

/** Lal
 * 
 * @author ykk
 * @dare Apr 2011
 */
public class Lal
    extends BroadcastReceiver
{
    /** Log name
     */
    private static String TAG = "HOLYC.Lal";
    /**
     * Reference to GSON
     */
    Gson gson = new Gson();
    
    /** Reference to database
     */
    public Database db = null;
    /** Table name
     */
    public static final String TABLE_NAME = "LAL_FLOW_TABLE";

    /** Initialize database
     */
    void initialize(Context context)
    {
	db = new Database(context);
    }

    /** Close database
     */
    protected void finalize()
    {
	db.close();
    }


    @Override
	public void onReceive(Context context, Intent intent) 
    {
	if (db == null)
	    initialize(context);
	
	if (intent.getAction().equals(HolyCIntent.OFFlowRemoved_Intent.action))
	{
	    String ofre_json = intent
		.getStringExtra(HolyCIntent.OFFlowRemoved_Intent.str_key);
	    OFFlowRemovedEvent ofre = gson.fromJson(ofre_json, 
						    OFFlowRemovedEvent.class);
	    ContentValues cv = new ContentValues();
	    cv.put("App", "Something"); //Need to put real application name
	    OpenFlow.addOFFlowRemoved2CV(cv, ofre.getOFFlowRemoved());
	    db.insert(TABLE_NAME, cv);
	}
    }

}
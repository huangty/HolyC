package edu.stanford.lal;

import com.google.gson.Gson;

import org.openflow.protocol.OFFlowRemoved;

import org.sqlite.helper.OpenFlow;
import org.sqlite.helper.CursorHelper;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteCursor;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Handler;
import android.util.Log;

import net.holyc.HolyCIntent;
import net.holyc.dispatcher.OFFlowRemovedEvent;
import net.holyc.host.Utility;
import edu.stanford.lal.Database;
import edu.stanford.lal.LalMessage;

import java.util.HashMap;
import java.util.Vector;

/** Lal
 * 
 * @author ykk
 * @dare Apr 2011
 */
public class Lal
    extends Service
{
    /** Log name
     */
    private static String TAG = "HOLYC.Lal";
    /** Reference to GSON
     */
    Gson gson = new Gson();
    /** LalMessage reference
     */
    LalMessage lmsg = new LalMessage();
    /** Reference to database
     */
    public Database db = null;
    /**
     * Local port number
     */
    public static short LOCAL_PORT = 1;
    /** Table name
     */
    public static final String TABLE_NAME = "LAL_FLOW_TABLE";
    /** Hashmap for app name
     */
    public static HashMap<String, String> appNames = new HashMap<String, String>();

    /** Broadcast receiver
     */
    BroadcastReceiver bReceiver = new BroadcastReceiver() 
    {
	@Override 
	    public void onReceive(Context context, Intent intent) 
	{
	    if (intent.getAction().equals(HolyCIntent.OFFlowRemoved_Intent.action))
	    {
		//Flow removed event (to be recorded)
		String ofre_json = intent
		    .getStringExtra(HolyCIntent.OFFlowRemoved_Intent.str_key);
		OFFlowRemovedEvent ofre = gson.fromJson(ofre_json, 
							OFFlowRemovedEvent.class);
		OFMatch ofm = ofre.getMatch();

		//Get App Name
		String app_name = null;
		if(ofm.getNetworkProtocol() == 0x06 || 
		   ofm.getNetworkProtocol() == 0x11)
		{
		    //it is tcp or udp
		    String remoteIP = "";
		    int remotePort = 0;
		    int localPort = 0;
		    
		    if (ofm.getInputPort() == LOCAL_PORT) {
			remoteIP = ipToString(ofm.getNetworkDestination());
			remotePort = U16.f(ofm.getTransportDestination());
			localPort = U16.f(ofm.getTransportSource());
		    } else {
			remoteIP = ipToString(ofm.getNetworkSource());
			remotePort = U16.f(ofm.getTransportSource());
			localPort = U16.f(ofm.getTransportDestination());
		    }
		    app_name = Utility.getPKGNameFromAddr(remoteIP, remotePort,
							  localPort, context);
		}
		if (app_name == null)
		    app_name = "Unknown";
		
		//Insert data into database
		ContentValues cv = new ContentValues();
		cv.put("App", app_name);
		cv.put("Time_Received", 
		       ((double) System.currentTimeMillis())/(1000.0));
		OpenFlow.addOFFlowRemoved2CV(cv, ofre.getOFFlowRemoved());
		db.insert(TABLE_NAME, cv);
	    }
	    else if (intent.getAction().equals(HolyCIntent.LalAppFound.action))
	    {
		//Application name notified
		String app_name = intent.getStringExtra(HolyCIntent.LalAppFound.str_key);
		Long h = new Long(app_name.hashCode());
		appNames.put(h.toString(), app_name);
		Log.d(TAG, "New application: "+app_name+" with hash "+h.toString());
	    }
	    else if (intent.getAction().equals(LalMessage.Query.action))
	    {
		//Query request
	    Log.d(TAG, "receive query from app");	
		String q_json = intent.getStringExtra(LalMessage.Query.str_key);
		LalMessage.LalQuery query = gson.fromJson(q_json, LalMessage.LalQuery.class);
		SQLiteCursor c = (SQLiteCursor) db.db.query(query.distinct,
							    TABLE_NAME,
							    query.columns,
							    query.selection,
							    query.selectionArgs,
							    query.groupBy,
							    query.having,
							    query.orderBy,
							    query.limit);
		//Read result
		Vector r = new Vector();
		c.moveToFirst();
		while (!c.isAfterLast())
		{
		    r.add(CursorHelper.getRow(c));
		    c.moveToNext();
		}
		c.close();

		//Create result
		LalMessage.LalResult result = lmsg.new LalResult();
		result.columns = query.columns;
		result.results = r;
		String result_str = gson.toJson(result, LalMessage.LalResult.class);

		//Return result
		Intent i = new Intent(LalMessage.Result.action);
		i.setPackage(context.getPackageName());
		i.putExtra(LalMessage.Result.str_key, result_str);
		sendBroadcast(i);
	    }
	}
    };
	
    @Override
	public IBinder onBind(Intent intent)
    {
	return null;
    }

    @Override
	public void onCreate()
    {
	super.onCreate();
	Log.d(TAG, "Starting Lal...");

	IntentFilter mIntentFilter = new IntentFilter();
	mIntentFilter.addAction(HolyCIntent.OFFlowRemoved_Intent.action);
	registerReceiver(bReceiver, mIntentFilter);

	IntentFilter lIntentFilter = new IntentFilter();
	lIntentFilter.addAction(HolyCIntent.LalAppFound.action);
	registerReceiver(bReceiver, lIntentFilter);

	IntentFilter qIntentFilter = new IntentFilter();
	qIntentFilter.addAction(LalMessage.Query.action);
	registerReceiver(bReceiver, qIntentFilter);

	db = new Database(getApplicationContext());
    }

    @Override
	public void onDestroy()
    {
	db.close();

	unregisterReceiver(bReceiver);

	super.onDestroy();
	Log.d(TAG, "Stopping Lal...");
    }

}

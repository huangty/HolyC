package edu.stanford.lal;

import com.google.gson.Gson;

import org.openflow.protocol.OFFlowRemoved;

import org.sqlite.helper.OpenFlow;

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

import edu.stanford.lal.Database;
import edu.stanford.lal.LalMessage;

import java.util.HashMap;


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

		//Get App Name
		String app_name = appNames.get((new Long(ofre.getOFFlowRemoved().getCookie())).
					       toString());
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
		c.moveToFirst();
		Log.d(TAG, c.getCount()+" rows");
		while (true)
		{
		    String[] names = c.getColumnNames();
		    for (int i = 0; i < names.length; i++)
			Log.d(TAG, names[i]);

		    if (c.isLast())
			break;
			c.moveToNext();
		}
		c.close();
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
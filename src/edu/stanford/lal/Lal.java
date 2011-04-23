package net.holyc.lal;

import com.google.gson.Gson;

import org.openflow.protocol.OFFlowRemoved;

import org.sqlite.helper.OpenFlow;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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

    /** Broadcast receiver
     */
    private final BroadcastReceiver bReceiver = new BroadcastReceiver() 
    {
	@Override 
	    public void onReceive(Context context, Intent intent) 
	{
	    if (intent.getAction().equals(HolyCIntent.OFFlowRemoved_Intent.action))
	    {
		String ofre_json = intent
		    .getStringExtra(HolyCIntent.OFFlowRemoved_Intent.str_key);
		OFFlowRemovedEvent ofre = gson.fromJson(ofre_json, 
							OFFlowRemovedEvent.class);
		ContentValues cv = new ContentValues();
		cv.put("App", "Something"); //Need to put real application name
		cv.put("Time_Received", 
		       ((double) System.currentTimeMillis())/(1000.0));
		OpenFlow.addOFFlowRemoved2CV(cv, ofre.getOFFlowRemoved());
		db.insert(TABLE_NAME, cv);
	    }
	}
    };
	
    /** Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler 
    {
        @Override
	    public void handleMessage(Message msg) 
	{
            switch (msg.what) 
	    {
	    case LalMessage.LalQuery.what:
		LalMessage.LalQuery query = (LalMessage.LalQuery) msg.obj;
		SQLiteCursor c = (SQLiteCursor) db.db.query(query.distinct,
							    TABLE_NAME,
							    query.columns,
							    query.selection,
							    query.selectionArgs,
							    query.groupBy,
							    query.having,
							    query.orderBy,
							    query.limit);
		break;
	    default:
		super.handleMessage(msg);
            }
        }
    }

    /** Target for clients to send messages.
     */
    public final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    @Override
	public IBinder onBind(Intent intent) 
    {
	return mMessenger.getBinder();
    }

    @Override
	public void onCreate()
    {
	super.onCreate();
	Log.d(TAG, "Starting Lal...");

	db = new Database(getApplicationContext());

	/////////////////////////////
	/*SQLiteCursor c = (SQLiteCursor) db.db.query("TIA_FLOW",
						    null, null, null, 
						    null, null, null);
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
	c.close();*/
    }

    @Override
	public void onDestroy()
    {
	db.close();
	super.onDestroy();
	Log.d(TAG, "Stopping Lal...");
    }

}
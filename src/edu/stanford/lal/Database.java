package edu.stanford.lal;

import android.content.Context;
import android.util.Log;

import edu.stanford.lal.Lal;

import org.sqlite.helper.*;

/** Database class for TIA's SQLite database
 *
 *  @author ykk
 *  @date Apr 2011
 */
public class Database
    extends org.sqlite.helper.Database
{
    /** Default filename
     */
    public static final String filename = "Lal.sqlite";

    /** Debug name
     */
    private static final String TAG = "LalDatabase";


    public Database(Context context)
    {
	super(context, filename);
    }

    public void createTables()
    {
	SQLiteTable tab = new SQLiteTable(Lal.TABLE_NAME);
	tab.addColumn("App", SQLiteTable.DataType.TEXT);
	tab.addColumn("Time_Received", SQLiteTable.DataType.REAL);
	tab.addColumn("Interface", SQLiteTable.DataType.TEXT);
	OpenFlow.addOFFlowRemoved2Table(tab);
	addTable(tab);
    }
}


package edu.stanford.lal;

/** Class to contain query for Lal
 *
 * @author ykk
 * @date Apr 2011
 */
public class LalMessage
{
    public class LalQuery
    {
	/** Message number
	 */
	public static final int what = 1;
	/** Return distinct values
	 */
	public boolean distinct = false;
	/** Columns to return
	 */
	public String[] columns = null;
	/** The SQL WHERE clause
	 */
	public String selection = null;
	/** May include ?s in selection, 
	 * which will be replaced by the values from selectionArgs
	 */
	public String[] selectionArgs = null;
	/** Group results by
	 */
	public String groupBy = null;
	/** A filter declare which row groups to include in the cursor
	 */
	public String having = null;
	/** Order result with
	 */
	public String orderBy = null;
	/** Limit result to size
	 */
	public String limit = null;
    }
}

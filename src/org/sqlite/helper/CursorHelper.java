package org.sqlite.helper;

import android.database.sqlite.SQLiteCursor;

import java.util.Vector;

/** Class to help with SQLiteCursor
 * @author ykk
 */
public class CursorHelper
{
    /** Return appropriate object for column
     *
     * @return String for string;
     *         Long for long/integer;
     *         Double for float/double;
     *         else null
     */
    public static Object getObject(SQLiteCursor c, int columnIndex)
    {
	if (c.isNull(columnIndex))
	    return null;
	if (c.isString(columnIndex))
	    return c.getString(columnIndex);
	if (c.isFloat(columnIndex))
	    return new Double(c.getDouble(columnIndex));
	if (c.isLong(columnIndex))
	    return new Long(c.getLong(columnIndex));
	
	return null;
    }
    
    /** Return vector with appropriate objects for a row
     */
    public static Vector getRow(SQLiteCursor c)
    {
	Vector v = new Vector();
	int count = c.getColumnCount();
	for (int i = 0; i < count; i++)
	    v.add(CursorHelper.getObject(c,i));
	
	return v;
    }
    
				   
}
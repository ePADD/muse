/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse.email;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** bunch of utils for manipulating date ranges and splitting them
 up into intervals based on exchanges with contacts etc. */
public class CalendarUtil {
    public static Log log = LogFactory.getLog(CalendarUtil.class);

    private static String[] monthStrings = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
	//private static String[] monthStrings = new DateFormatSymbols().getMonths();
	private final static int nMonthPerYear = monthStrings.length;

	public static String getDisplayMonth(int month)
	{
		return monthStrings[month];
	}

	public static String getDisplayMonth(Calendar c)
	{
		// we don't want to be dependent on Calendar.getDisplayName() which is in java 1.6 only
		// return c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) + " " + c.get(Calendar.YEAR);
		// could also use a simpledataformat here.
		int month = c.get(Calendar.MONTH); // month is 0-based
		return monthStrings[month];
	}

	public static String getDisplayMonth(Date d)
	{
		Calendar c = new GregorianCalendar();
		c.setTime(d);
		return getDisplayMonth(c);
	}

	public static int getDiffInMonths(Date firstDate, Date lastDate)
	{
		Calendar cFirst = new GregorianCalendar(); cFirst.setTime(firstDate);
		Calendar cLast = new GregorianCalendar(); cLast.setTime(lastDate);
		int cFirst_year = cFirst.get(Calendar.YEAR);
		int cFirst_month = cFirst.get(Calendar.MONTH);
		int cLast_year = cLast.get(Calendar.YEAR);
		int cLast_month = cLast.get(Calendar.MONTH);
		return (cLast_year - cFirst_year) * (cLast.getMaximum(Calendar.MONTH)-cLast.getMinimum(Calendar.MONTH)+1) + (cLast_month - cFirst_month);
	}

	public static int[] getNextMonth(int year, int month)
	{
		month++;
		year += month/nMonthPerYear;
		month %= nMonthPerYear;
		int[] result = new int[]{ year, month };
		return result;
	}

	/** divides the given time range into intervals */
	public static List<Date> divideIntoIntervals(Date start, Date end, int nIntervals)
	{
		List<Date> result = new ArrayList<Date>();

		long startMillis = start.getTime();
		long endMillis = end.getTime();
		Util.ASSERT (endMillis >= startMillis);

		long gap = (endMillis - startMillis)/nIntervals;

		result.add(start);
		for (int i = 1; i < nIntervals; i++)
		{
			long millis = (startMillis + i * gap);
			result.add(new Date(millis));
		}
		result.add(end);

		return result;
	}

	public static Calendar startOfMonth(Date d)
	{
		Calendar c = new GregorianCalendar();
		c.setTime(d);
		int yy = c.get(Calendar.YEAR);
		int mm = c.get(Calendar.MONTH);
		// get new date with day of month set to 1
		c = new GregorianCalendar(yy, mm, 1);
		return c;
	}

	/** returns a list of monthly intervals for the first of each month
	 * the first entry in the list is the 1st of the month before start
	 * and the last entry is the 1st of the month after the end.
	 */
	public static List<Date> divideIntoMonthlyIntervals(Date start, Date end)
	{
		List<Date> result = new ArrayList<Date>();

		// result will always have at least 2 entries
		Calendar c = startOfMonth(start);
		result.add(c.getTime());
		do {
			int mm = c.get(Calendar.MONTH);
			int yy = c.get(Calendar.YEAR);
			mm++;
			// months are 0-based (jan = 0)
			if (mm >= 12)
			{
				mm = 0;
				yy++;
			}
			c = new GregorianCalendar(yy, mm, 1);
			result.add(c.getTime());
		} while (c.getTime().before(end)); // stop when we are beyond end

		return result;
	}

    public static int[] computeHistogram(List<Date> dates, List<Date> intervals) { return computeHistogram(dates, intervals, false); }

    /** intervals must be sorted, start from before the earliest date, and end after the latest date */
	public static int[] computeHistogram(List<Date> dates, List<Date> intervals, boolean ignoreInvalidDates)
	{
		if (intervals == null || intervals.size() == 0)
			return new int[0];

		int nIntervals = intervals.size()-1;
		int[] counts = new int[nIntervals];

		if (ignoreInvalidDates) {
			int count = 0;
			List<Date> newDates = new ArrayList<Date>();
			for (Date d : dates)
				if (!EmailFetcherThread.INVALID_DATE.equals(d))
					newDates.add(d);
				else
					count++;
			dates = newDates;
			if (count > 0)
				log.info (count + " invalid date(s) ignored");
		}

		if (dates.size() == 0)
			return counts;

		Collections.sort(dates);

		Date firstDate = dates.get(0);
		Date lastDate = dates.get(dates.size()-1);
		// intervals are assumed to be already sorted
		if (firstDate.before (intervals.get(0)))
			throw new RuntimeException("INTERNAL ERROR: invalid dates, first date before intervals start, aborting histogram computation");
		if (lastDate.after(intervals.get(intervals.size()-1)))
			throw new RuntimeException("INTERNAL ERROR: invalid dates, last date after intervals end, aborting histogram computation");

		int currentInterval = 0;
		int thisIntervalCount = 0; // running count which we are accumulating into the current interval
		// no need to track currentIntervalStart explicitly
		Date currentIntervalEnd = intervals.get(currentInterval+1);

		// we'll run down the sorted dates, counting dates in each interval
		for (Date currentDate: dates)
		{
			if (currentDate.after (currentIntervalEnd))
			{
				// we're done with current interval, commit its count
				counts[currentInterval] = thisIntervalCount;

				// find the next interval, skip over till current Date is before the interval end
				do {
					currentInterval++;
					currentIntervalEnd = intervals.get(currentInterval+1);
				} while (currentDate.after(currentIntervalEnd));
				// now current date is before the current interval end, so we have the new interval reflected in currentIntervalEnd
				// count currentDate in this interval
				thisIntervalCount = 1;
			}
			else
				thisIntervalCount++; // still not reached end of interval
		}
		// the last interval's end was not exceeded, so set up its count here
		counts[currentInterval] = thisIntervalCount;

		return counts;
	}

	/** computes activity chart as HTML */
	public static String computeActivityChartHTML(double[] normalizedHistogram)
	{
		StringBuilder sb = new StringBuilder();
		for (double d: normalizedHistogram)
		{
			int colorVal;
			if (d < 0.000001)
				colorVal = 0;
			else
			{
				colorVal = 64 + (int) (d * 192);
				if (colorVal > 255)
					colorVal = 255; // saturate in case of corner cases
			}

			String colorString = Integer.toHexString(colorVal);
			if (colorString.length() == 1)
				colorString = "0" + colorString; // add leading 0, otherwise stylesheet is not valid

			colorString += colorString;
			colorString += "00";
			// colorString += "0000"; // assuming colorString is going into red
			// print a dummy space with the appropriate background color
			sb.append ("<x style=\"background-color:#" + colorString + "\">&nbsp;</x>");  // inactive unless above a miniscule percentage
		}
		return sb.toString();
	}

	public static String formatDateForDisplay(Date d)
	{
		if (d == null)
			return "<No Date>";
	    SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy");
	    return formatter.format(d);
	}

	/** returns date object for the first day of the given month. m is 0 based */
	public static Date convertYYMMToDate(int y, int m, boolean beginning_of_day)
	{
		return convertYYMMDDToDate(y, m, 1, beginning_of_day);
	}

    public static Date convertYYMMDDToDate(int y, int m, int d, boolean beginning_of_day)
    {
        // if m is out of range, its equiv to 0
        if (m < 0 || m > 11)
            m = 0;
        if (d<0 || d>30)
            d = 0;
        GregorianCalendar c = new GregorianCalendar();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, m);
        c.set(Calendar.DATE, d);
        if (beginning_of_day)
        {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
        }
        else
        {
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
        }
        return c.getTime();
    }
	
	/** convert a pair of <yy, mm> specs to a date range. startM, endM are 0-based. if startM is < 0, considered as 0 and if endM is < 0, considered as 11. 
	 * no handling of time zone. */
    public static Pair<Date, Date> getDateRange(int startY, int startM, int endY, int endM){
        return getDateRange(startY, startM, 1, endY, endM, 1);
    }

    public static Pair<Date, Date> getDateRange(int startY, int startM, int startD, int endY, int endM, int endD)
    {
        if (startM < 0)
            startM = 0;
        if (endM < 0)
            endM = 11;

        Date startDate = convertYYMMDDToDate(startY, startM, startD, true);

        // get date for one month beyond endY, endM, and adjust date back by 1 ms
        endM++;
        if (endM >= 12)
        {
            endY++;
            endM = 0;
        }
        Date beyond_end = convertYYMMDDToDate(endY, endM, endD, true);
        Date endDate = new Date(beyond_end.getTime()-1001L);

        return new Pair<Date, Date>(startDate, endDate);
    }

	/** the quarter beginning just before this d */
	public static Date quarterBeginning(Date d)
	{
		Calendar c = new GregorianCalendar();		
		c.setTime(d);
		
		int m = c.get(Calendar.MONTH);
		int qrtrBegin = 3 * (m/3);
		c.set(Calendar.MONTH, qrtrBegin);
		c.set(Calendar.DAY_OF_MONTH, 1);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTime();
	}
	
	public static void main (String args[])
	{
		// tests for get date range
		Pair<Date, Date> p = getDateRange(2010, 10, 2011, 5);
		System.out.println(p.getFirst() + " - " + p.getSecond());		
		p = getDateRange(2010, 10, 2011, -1);
		System.out.println(p.getFirst() + " - " + p.getSecond());		
		p = getDateRange(2010, -1, 2011, -1);
		System.out.println(p.getFirst() + " - " + p.getSecond());		
		p = getDateRange(2010, -1, 2010, -1);
		System.out.println(p.getFirst() + " - " + p.getSecond());

		Date d = new Date();
		for (int i = 0; i < 100; i++)
		{
			d = quarterBeginning(d);
			System.out.println (formatDateForDisplay(d));
			d.setTime(d.getTime() - 1L);
		}
	}
}

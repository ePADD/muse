package edu.stanford.muse.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

public class Screener {
	public static Log log = LogFactory.getLog(Screener.class);
	
	/** returns a map of name -> count, where count is the # of time quanta (each of size quantum, going backwards in time from endTime), in which the name appears in docs. */
	public static Map<String, Integer> nameFrequenciesOverTime(List<EmailDocument> docs, long endTime, long quantum, boolean originalContentOnly)
	{
		long currentEndTime = endTime;
		
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		Set<String> namesInThisQuantum = new LinkedHashSet<String>();
		for (EmailDocument ed: docs)
		{
			if (ed.date.getTime() < (currentEndTime - quantum))
			{
				for (String name: namesInThisQuantum)
				{
					name = name.toLowerCase();
					Integer I = result.get(name);
					if (I == null)
						result.put(name, 1);
					else
						result.put(name, I+1);
				}
				namesInThisQuantum.clear();
			}
		}
		return result;
	}

	/** returns whether a user passes screening or not, along with the histogram. to pass screening there must be at least minMessagesPerInterval
	 * in each of minIntervals intervals going backwards from the current time. */
	public static Pair<List<Integer>, Boolean> screen(Collection<EmailDocument> docs, int nIntervals, int minMessagesPerInterval, long intervalMillis)
	{
		List<Date> dates = new ArrayList<Date>();
		for (EmailDocument ed: docs)
			dates.add(ed.date);

		List<Integer> datesHistogram = EmailUtils.histogram(dates, new Date().getTime(), intervalMillis);

		// dates histogram goes backwards in time, so its [#messages in last interval, #messages in interval before that, .... ]

		boolean success = true;
		// if we don't have enough intervals, bail out
		if (datesHistogram.size() < nIntervals) {
			success = false;
		}
		else {	
			// check the dates histogram up to nIntervals (don't care beyond that)
			int count = 0;
			for (Integer I : datesHistogram)
			{
				if (I < minMessagesPerInterval) {
					success = false;
					break;
				}
				if (++count >= nIntervals)
					break;
			}
		}
		
		// log the dates histogram for debugging
		StringBuilder sb = new StringBuilder("SCREENING " + (success ? "PASS" : "FAIL") + ": Dates histogram for " + docs.size() + " messages: ");
		for (Integer I: datesHistogram)
			sb.append (I + " ");
		log.info (sb.toString());

		return new Pair<List<Integer>, Boolean>(datesHistogram, success);
	}
}

package edu.stanford.muse.ie;

import java.util.*;

/**
 * Contains the syn sets for known classes, may contain syns in canonicalised
 * forms.
 * handle possible uncanicalisations required
 */
public class KnownClasses {
	public Map<String, String[]>	syns	= new HashMap<>();
	public Map<String, String>		colors	= new HashMap<>();
	//Note: keep these names in sync with the DBpedia types.
	//Book|WrittenWork|Work, Disease, University|EducationalInstitution|Organisation|Agent, Museum|Building|ArchitecturalStructure|Place, Film|Work, Award, Company|Organisation|Agent
	public static String			BOOK	= "book", UNIV = "university", MUSICAL_ARTIST = "musical_artist", MUSEUM = "museum", HOTEL = "hotel", COMPANY = "company", MOVIE = "film", GOVT = "govt", AWARD = "award", PEOPLE = "people", PLACE = "place", DISEASE = "disease";

	public KnownClasses() {
		syns.put(BOOK, new String[] { "library", "book", "volume", "record book", "novel", "published", "rule book", "al-Qur'an", "bible", "christian bible", "book", "good book", "author", "ebook", "reading", "poem", "poetry" });
		syns.put(UNIV, new String[] { "academic", "college", "degree", "institution", "university", "graduate", "school", "department", "library", "student", "dept", "professor", "teacher", "scholar", "scholarship" });

		syns.put(MUSICAL_ARTIST, new String[] { "music", "drummer", "player", "jazz", "artist", "concert", "performances", "record" });

		syns.put(MUSEUM, new String[] { "museum", "archive", "library", "art", "historical", "history", "gallery" });

		syns.put(HOTEL, new String[] { "hotel", "food", "suite", "check-in", "check in", "dinner", "lunch", "breakfast", "coffee", "visit", "gather", "reserve", "room", "reservation" });

		syns.put(COMPANY, new String[] { "credit card", "creditcard", "visa", "payment", "seller", "market", "customer", "shipping", "builder", "contract", "publish", "logo", "incorporated", "trademark", "product", "airlines" });

		syns.put(MOVIE, new String[] { "film", "movie", "motion picture", "screening", "actor", "actress", "director", "play", "theater", "screen", "stars" });
		//This set looks so bad, no idea if it works.
		syns.put(GOVT, new String[] { "union", "government", "administration", "agency", "agencies", "association", "public", "volunteer" });
		syns.put(AWARD, new String[] { "prize", "award", "congratulations", "winning", "nomination", "nominate", "won" });
		syns.put(PEOPLE, null);
		//dont use this
		syns.put(PLACE, new String[] { "trip", "ride", "tour", "station", "street", "visit", "travel", "meeting", " at " });
		syns.put(DISEASE, new String[] { "sick", "abnormal", "ill", "unhealthy", "disease", "epidemic", "pandemic", "endemic", "fever", "vomit", "syndrome" });

		String[] types = new String[] { KnownClasses.BOOK, KnownClasses.UNIV, KnownClasses.MUSEUM, KnownClasses.COMPANY, KnownClasses.AWARD, KnownClasses.MOVIE, KnownClasses.DISEASE };
		String[] cs = new String[] { "green", "red", "deepskyblue", "orange", "violet", "fuchsia", "burlywood" };
		for (int i = 0; i < types.length; i++)
			colors.put(types[i], cs[i]);
	}
}

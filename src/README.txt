
EmailUtils now has a feature called bannedWordsInPeopleNames.

	// set of words that occur in non-real names.
	// names are tokenized and converted to lower case before comparing,
	// if any of the tokens matches any of these words, we assume its not a real time.
	// hopefully there is no real person with a last name like postmaster...
	// while normalizing the name we check if name has banned words - e.g. ben shneiderman's email has different people with the "name" (IPM Return requested)
	
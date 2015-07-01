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
package edu.stanford.muse.index;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.util.Util;

/** handles sentiment analysis */
public class Sentiments {
	
	static Log log = LogFactory.getLog(Sentiments.class);
	private static String DEFAULT_LEXICON_FILENAME = "default";
	private static String LEXICON_SUFFIX = ".lexicon";
	
	// default lexicon, will be overriden by LEXICON_FILENAME if it exists
	public static String[][] emotionsData1 =
	{
	    {"congratulations", "congratulations|congrats|congratulate|congratulated|congratulating|felicitate|felicitation"},
	    {"superlative", "brilliant|super|superb|beautiful|exquisite|astonishing|amazing|amazement|mindblowing|astounding|outstanding|astonish|astonished|fantastic|fabulous|electrifying|electrified|adulation|excellent|terrific|genius|priceless|rave|ravishing|gush|awesome|awe-inspiring|preeminent|pre-eminent|standing ovation|gorgeous|marvel|marvellous|marvelous|stupendous|stunning"},
	    {"gratitude", "grateful|gratitude|thankful|generous|heartwarming|heart-warming|\"heart warming\""}, // no thanks!
	    {"pride", "pride|proud|brag|bragging|bragged"},
/*	    {"happy", "happy|joy|happiness"}, */
	    {"joy", "joy|joyous|happiness|euphoria|high spirits|lovely|wonderful|gleeful|glad|\"thank god\"|\"thank goodness\"|\"good news\"|rhapsodic|ecstasy|enraptured|ecstatic|jubilation|festivity|celebration|celebrate|\"cloud nine\"|\"walking on air\"|rapture|blissful|thrill|excited"},
		{"nice", "honey"},
	//    {"like", "like|liking|likes"},
	   // {"hope", "hope|hopeful"},
	    {"surprise", "surprise|surprised|surprising|unusual"},
	    {"pos-surprise", "remarkable|remarkably|omg"}, // peculiar|strange|
	    {"wow", "wow"},
	    {"humor", "joke|funny|slapstick|comedy|humor|humour|haha|hahaha|\"ha ha\"|lol"},
	    {"milestones", "milestones|centennial|anniversary|birthday|graduation|convocation|commencement"},
	    {"memories", "memories|remember|memoir|memoirs|memorable|unforgettable|reminiscence|remembrance"},
	    {"confidential", "confide|confided|confidential|secret|secrecy|secretly|secrets|\"don't tell\"|\"keep it to yourself\""},
	    {"life event", "marriage|marry|married|divorce|divorced|bereaved|bereavement|death|memorium|memoriam|obituary|obit|memorial|funeral|died|dying|fatal|\"pass away\"|born|birth"},
//	    {"interest", "interest|interesting"},
	    {"family", "mom|dad|mother|father|husband|wife|hubby|brother|sister|cousin|uncle|aunt|grandfather|grandmother|grandpa|grandma|granny|son|daughter|stepson|stepdaughter|family|kin"},
//	    {"wish", "wish|wishes|wished|wishing|wish for"},
	    {"religion", "god|religion|church|almighty|bible|christian|jewish|hindu|muslim|islamic|mosque|temple|cathedral|catholic|episcopal|prayer|pray|religious|reverent|reverend|moksha|dharma|karma|kismat|fatwa|fatwah|bar mitzvah|bat mitzvah|piety|protestant|clergy|clergyman|allah|gospel|monk|monastery|shia|sunni|sabbath|ramadan|psalm|pope|priest|passover|mormon|pagan|baptist|baptism|buddhist|buddha|chapel|chaplain|amish|amen|afterlife|pastor|pews"},
	//    {"curious", "curious|curiosity|inquisitive|nosy|snoop|snooping"},
	    {"festivals", "festival|Christmas|\"New year\"|thanksgiving|halloween|diwali|carnival|hannukah|oktoberfest|easter"},
	    {"love", "beloved|valentine|romance|romantic|sweetheart|sweetie|sweetiepie|darling"}, // removing "love" because monica has too much of it...
	    {"vacations", "holiday|vacation"},
	    {"disappointment", "disappoint|disappointed|disappoints|disappointment|disappointing|discontent|bummer|bummed|disgust|disgusted|misery|miserable|bitter|bitterness|backfire"},
	    {"anxiety", "anxious|anxiety"},
	    {"emotion", "emotion|emotional|tears|teary|wrench|heartbroken|heartbreak"},
	//    {"sorry", "sorry|remorse|regret|regrets|apology|apologize|apologies"},
		{"sorry", "remorse|regret|regrets|apology|apologize|apologies"},
	    {"pity", "pity"},
	    {"worry", "worry|worried|worries|worrying|anxiety|anxious|panic|apprehensive|apprehension"},
	    {"embarassment", "embarass|embarassment|embarassed"},
	    {"medical", "sick|ill|illness|sickness|medical|surgery|endoscopy|hospital|unwell|headache|depression|depressed|injure|hurt|injury|injured|injurious|doctor|surgeon|surgical|clinic|lethal|arthritis|suicide|laceration|trauma|traumatic|ptsd|schizophrenia|schizophrenic|disfigure|disfigured|disfiguring|radiation|medicine|wound|foetus|fetus|fetal|injure|disease|infection|vomit|puke|puking|seasick|carsick|nausea|nauseous|nauseating|nauseated|pallid|wan|miscarriage|\"heart attack\"|postpartum|hemorrhage|fracture|casualty|concussion|cancer|biopsy|malignant|leukemia|malignancy|pancreas|pancreatic|gastric|abdominal|menaloma|thyroid|cervical|cardiovascular|colorectal|ovarian|gastrointestinal|lung|lungs|thoracic|oesophagus|alzheimer|parkinson|pediatrician|orthopedic|cardiologist|urologist|oncologist|gynecologist|dermatologist|neurologist|anesthesiologist|anesthetist|geriatric|\"spinal cord\""}, // to add: "not well"
	    {"contempt", "contempt|contemptuous"},
	    {"destructive", "violence|destroy|disturbed|disturbing|disturbance|disturbances|ravage|bomb|explode|explosion|devastate|devastation"},
	    {"hate", "hate|loathe"},
	    {"jealousy", "jealous"},
	    {"dislike", "trouble|problem|lame|negative|terrible|dislike|yucky|unsavory|unsavoury|wrongful|vendetta|patronizing|crass|vulgar|sordid|devious|delusion|megalomania|neurotic|saddle|jackass|bozo|corrupt|worthless|plagiarize|henpecked|domineering|blasphemy|senile|reprehensible|\"ill will\"|unctuous|sloppy|obnoxious|noxious|noisome|supercilious|overbearing|torment|haughty|seedy|wishy-washy|vain|vainglorious|quixotic|insidious|\"hell to pay\"|vapid|stupid|idiot|moron|swollen-headed|conceited|feckless|sadistic|\"can of worms\"|sophistry"},
	    {"despair", "suicide|suicides|desperate|desperation|frustrate|frustrated|frustrating"},
	    {"fear", "afraid|fear|nervous|petrified|petrify|petrifying|horror|horrified|nightmare|nightmarish"},
	    {"racy", "sex|sexy|erotic|erection|copulate|copulation|intercourse|horny|outercourse|aroused|boner|butt|condom|titillate|lechery|virginity|cunt|vagina|panties|chastity|lascivious|sexcapade|sexual|kinky|bondage|coitus|carnal|blowjob|\"blow job\"|cunnilingus|genital|nipple|tits|foreplay|lovemaking|extramarital|orgiastic|salacious|aphrodisiac|prurient|lustful|risque|sex-starved|fornicate|sodomy|ejaculate|masturbate|adultery|incest|fondling|necking|petting|penis|semen|fetish|voyeur|slut|nude|nudity|nyphomaniac|porn|pubic|titties|whore|pornographic|raunchy"},
	    {"emergency", "emergency|accident|critical|flashpoint|catastrophe|crisis|panic"},
	    {"shock", "shock|shocking|shocks|thunderstruck|stupefied|flabbergasted|dumbstruck|dumbfounded"},
	    {"expletives", "goddamn|goddam|gaddamit|damn|fuck|f*ck|fucking|fucked|fucks|fucker|sonofabitch|wtf"},
	    {"sadness", "cry|crying|sad|sadly|saddened|sadness|bleak|ouch|gloomy|crappy|crushed|distress|suffocate|lonely|rejected|pain|pained|sorrow|morose|agony|anguish|heartbreak|heartbroken|dismay|dismayed|sympathy|condolence|condolences"},
	    {"conflict", "fault|mend fences|contrite|contrition|struggle"},
	    {"anger", "angry|anger|exasperate|angered|complain|complained|\"freak out\"|arrogant|bully|abuse|furious|infuriate|hackles|embitter|vitriol|vituperative|excoriate|rage|umbrage|outrage|madden|defame|slander|travesty|enrage|enraged|irate|wrath|livid|offense|indignant|indignation|tantrum|exasperate|exasperation|harass|harassment|torment|tormented|displeased|irritate|irritating|annoyed|annoying|hostile|hostility|hostilities|wreak|wreaking|\"pissed off\"|bitch|bitching|bitchy|rude|churlish"},
	    {"guilt", "guilt|guilty|pang|criminal|rape|accuse|incriminating|culpable|prison|imprison|fraud|bogus|heinous|villainous"},
	    {"shame", "shame|ashamed|shameful|shamefaced|humiliate|humiliated|humiliation|dishonour|dishonor|ignominy|opprobrium|odium|obloquy|sheepish|mortified|mortification"},
	    {"grief", "grief|grieving|grieved|tragedy|aggrieved|anguish|suffer|suffering|sepulchral|mourn|mourning|condolence|bereave|sympathy|sympathies|heartbroken|heart-broken|\"last rites\""},
	  /*  {"wonder", "wonder"}, */
	    /* big categories in sentiwordnet: food terms! mythological terms! lots of places!
	     * famous people. botanical terms. medical terms!
	     * currency, financial terms? chemical terms.
	     * historical battles? animals?
	     *
	     * */
	};

	// some of the above categories are further combined into broad categories like pos and neg
	//static String[] neutralEmo = {"emotion", "festivals", "family", "religion", "curious", "health", "racy", "life event", "interest", "wish"}; // suppressing memories for now
	static String[] posEmo = {"wow", "gratitude", "pride", "joy", "nice", "pos-surprise", "humor"}; // suppressing like for now
	static String[] negEmo = {"grief", "shame", "disappointment", "emotion", "anxiety", "sorry", "pity", "worry", "embarassment", "contempt", "destructive", "hate", "jealousy", "dislike", "despair", "fear", "shock", "sadness", "conflict", "guilt"};
//	public static Map<String, String> captionToQueryMap = new LinkedHashMap<String, String>();

	/** sets up caption to query map. for each caption (category), there is a query that will be run on the indexer */
	/*
	public static void loadLexicon(String dir, String lexicon)
	{
		lexicon = sanitizeLexiconName(lexicon);

		captionToQueryMap.clear();
		try {
			String filename = dir + File.separator + lexicon + LEXICON_SUFFIX;
			LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream (filename), "UTF-8"));
			
			while (true)
			{
				String line = lnr.readLine();
				if (line == null)
					break;
				line = line.trim();
				if (line.startsWith("#"))
					continue;
				StringTokenizer st = new StringTokenizer(line, ":");
				if (st.countTokens() < 2)
					continue;
				String caption = st.nextToken().trim();
				String query = st.nextToken().trim();
				captionToQueryMap.put(caption, query);
			}
		} catch (IOException ioe)
		{
			log.info("No custom sentiment lexicon found, setting up default lexicon");
			//setupDefaultLexicon(); // fallback lexicon
		}
	}
	*/
	/*
	private static void setupDefaultLexicon()
	{
		captionToQueryMap.clear();

		// use default
		for (String[] emotion: emotionsData)
//		for (String[] emotion: LIWCTable.LIWCData_orig)
		{
			String caption = emotion[0];
			String query = emotion[1];
			// no need to perform stemming on the query since we will do it during query lookup
			captionToQueryMap.put(caption, query);
		}

		{
			String query = "";
			for (String sentiment: negEmo)
			{
				query += "|" + captionToQueryMap.get(sentiment);
				captionToQueryMap.remove(sentiment);
			}
			query = query.substring(1); // remove leading |
			captionToQueryMap.put ("Negative", query);
		}
		{
			String query = "";
			for (String sentiment: posEmo)
			{
				query += "|" + captionToQueryMap.get(sentiment);
				captionToQueryMap.remove(sentiment);
			}
			query = query.substring(1);
			captionToQueryMap.put ("Positive", query);
		}
	}
	*/
	
	/** update lexicon. only keys with non-empty values are considered.
	 * @throws FileNotFoundException 
	 * @throws UnsupportedEncodingException */
	/*
	public static void saveLexicon (Map<String, String> captionMap, String saveToDir, String name, Map<String, String[]> map) throws UnsupportedEncodingException, FileNotFoundException
	{
		name = sanitizeLexiconName(name);
		captionMap.clear();
		for (String key: map.keySet())
		{
			String values[] = map.get(key);			
			if (values == null || values.length == 0)
				continue;
			String value = values[0];
			if (value == null)
				continue;
			value = value.trim();
			if (!Util.nullOrEmpty(value))
				captionMap.put(key, value);
		}
		
		try {
			if (saveToDir != null)
			{
				String filename = saveToDir + File.separator + name + LEXICON_SUFFIX;
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filename), "UTF-8"));
				for (String caption: captionMap.keySet())
					pw.println (caption + ":" + captionToQueryMap.get(caption));
				pw.close();
				log.info(captionMap.size() + " sentiment categories saved in " + filename);
			}
		} catch (Exception e) { Util.print_exception(e, log); }
	}
*/

	/*
	public static Map<Integer, Integer>[] debugEmotions (Indexer indexer, List<Document> docs)
	{
		Map<Integer, Integer>[] map = new LinkedHashMap[emotionsData.length];

		Map<Document, Integer> docNums = new LinkedHashMap<Document, Integer>();
		for (int i = 0; i < docs.size(); i++)
			docNums.put(docs.get(i), i);

		for (int i = 0; i < emotionsData.length; i++)
		{
			map[i] = new LinkedHashMap<Integer, Integer>();
 			String query = emotionsData[i][1];

			StringTokenizer st = new StringTokenizer(query, "|");
			while (st.hasMoreTokens())
			{
				String word = st.nextToken();
				Set<Document> ds = indexer.docsWithPhrase(word, -1);
				for (Document d: ds)
				{
					Integer docNum = docNums.get(d);
					Integer I = map[i].get(docNum);
					if (I == null)
						map[i].put(docNum, 1);
					else
						map[i].put(docNum, I+1);
				}
			}
		}
		return map;
	}
	*/
}

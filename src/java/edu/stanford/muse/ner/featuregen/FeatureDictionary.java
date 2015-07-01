package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.ner.NER;

import libsvm.svm_parameter;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probable improvements in train file generation are:
 * 1. Use proper relevant stopwords for extracting entities for each type. (for example why would entity contain "in"? take stop-word stats from Dbpedia)
 * 2. recognise words that truly belong to the Org name and place name. For example in "The New York Times", dont consider "New" to be part of the org name, but just as a place name. Two questions: Will this really improve the situation?, Does this worse the results? because we are not considering the inherent ambiguity affiliated with that word.
 * 3. Get all the names associated with the social network of the group communicating and use it annotate sents.
 * 4. Better classification of People and orgs. In Robert Creeley's archive names like: Penn state, Some Internet solutions are also names from AB
 *
 * As the name suggests, feature dictionary keeps track of all the features generated during training.
 * During prediction/recognition step, features should be generated through this class, this emits a feature vector representation of the phrase
 * Behaviour of generating feature vector
 * Nominal: generates value based on proportion of times the nominal value appeared in iType to total frquency
 * Boolean: Passes the value of the feature generator as is
 * Numeric: Passes the value of the feature generator as is
 * */
public class FeatureDictionary implements Serializable {
	/**
	 * 
	 */
	private static final long							serialVersionUID	= 1L;
	//triple contains freqs with person, org and location types.
	//Set<Map<String, String>>			gazettes			= new HashSet<Map<String, String>>();
	//dim -> instance -> #positive type, #negative type
	//patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
	public Map<String, Map<String, Pair<Integer, Integer>>>	features			= new LinkedHashMap<String, Map<String, Pair<Integer, Integer>>>();
	//contains number of times a CIC pattern is seen (once per doc), also considers quoted text which may reflect wrong count
	//This can get quite depending on the archive and is not a scalable solution
	//TODO: Explore indexing of this data-structure
	public Map<String,Integer> counts = new HashMap<String,Integer>();
	//maximum number of times a CIC pattern repeated
	public Integer maxCount = 0;

	public static String								PERSON				= "Person", ORGANISATION = "Organisation", PLACE = "Place";
	public String									    iType;
	static Log											log					= LogFactory.getLog(FeatureDictionary.class);
	public static Map<String, String[]>					aTypes				= new LinkedHashMap<String, String[]>();
	public FeatureGenerator[]                           featureGens         = null;
	public Map<String, EntityContext> entityContexts = new LinkedHashMap<String, EntityContext>();
	public static Map<String,String[]> startMarkersForType = new LinkedHashMap<String, String[]>();
	public static Map<String,String[]> endMarkersForType = new LinkedHashMap<String, String[]>();
	public static Map<String, String[]> ignoreTypes = new LinkedHashMap<String, String[]>();
	public static String MARKERS_PATT = "^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.|[Pp]rof\\.)\\W+";

	//feature types
	public static short NOMINAL = 0, BOOLEAN = 1, NUMERIC = 2, OTHER = 3;

	static{
		//the extra '|' is appended so as not to match junk.
		//matches both Person and PersonFunction in dbpedia types.
		aTypes.put(FeatureDictionary.PERSON, new String[] { "Person" });
		aTypes.put(FeatureDictionary.PLACE, new String[] { "Place" });
		aTypes.put(FeatureDictionary.ORGANISATION, new String[] { "Organisation", "PeriodicalLiterature|WrittenWork|Work"});

		startMarkersForType.put(FeatureDictionary.PERSON, new String[]{"dear","hi","hello","mr","mr.","mrs","mrs.","miss","sir","madam", "dr.","prof","dr","prof.","dearest","governor","gov."});
		endMarkersForType.put(FeatureDictionary.PERSON, new String[]{"jr","sr"});
		startMarkersForType.put(FeatureDictionary.PLACE,new String[]{"new"});
		endMarkersForType.put(FeatureDictionary.PLACE, new String[]{"shire","city","state","bay","beach","building", "hall"});
		startMarkersForType.put(FeatureDictionary.ORGANISATION, new String[]{"the", "national", "univ","univ.","university","school"});
		endMarkersForType.put(FeatureDictionary.ORGANISATION, new String[]{"inc.","inc","school","university","univ","studio","center","service","service","institution","institute","press","foundation", "project", "org","company","club","industry","factory"});

		/**
		 * Something funny happening here, Creeley has a lot of press related orgs and many press are annotated with non-org type,
		 * fox example periodicals, magazine etc. If these types are not ignored, then word proportion score for "*Press" is very low and is unrecognised
		 * leading to a drop of recall from 0.6 to 0.53*/
		//these types may contain tokens from this type
		ignoreTypes.put(FeatureDictionary.ORGANISATION, new String[]{
//				"Film|Work",
//				"Building|ArchitecturalStructure|Place",
//				"AdministrativeRegion|PopulatedPlace|Place",
//				"Book|WrittenWork|Work",
//				"TelevisionShow|Work","AutomobileEngine|Device",
//				"VideoGame|Software|Work",
//              "Software|Work",
//				"TelevisionEpisode|Work","ProtectedArea|Place",
//				//"Newspaper|PeriodicalLiterature|WrittenWork|Work","AcademicJournal|PeriodicalLiterature|WrittenWork|Work",
//				"Weapon|Device",
//				//"Magazine|PeriodicalLiterature|WrittenWork|Work",
//				"Website|Work","Award",
//				"Hotel|Building|ArchitecturalStructure|Place","MusicFestival|Event",
//				"Theatre|Building|ArchitecturalStructure|Place","Device",
//				"Library|EducationalInstitution|Organisation|Building|ArchitecturalStructure|Place","Restaurant|Building|ArchitecturalStructure|Place",
				"Band|Organisation", "SoccerClub|SportsTeam|Organisation", "TelevisionEpisode|Work",
				"SoccerClubSeason|SportsTeamSeason|Organisation", "Album|MusicalWork|Work",
				"SportsTeamMember|OrganisationMember|Person"
		});
		ignoreTypes.put(FeatureDictionary.PLACE, new String[]{
				"Election|Event", "MilitaryUnit|Organisation", "Ship|MeanOfTransportation", "OlympicResult",
				"SportsTeamMember|OrganisationMember|Person", "TelevisionShow|Work", "Book|WrittenWork|Work",
				"Film|Work", "Album|MusicalWork|Work", "Band|Organisation", "SoccerClub|SportsTeam|Organisation",
				"TelevisionEpisode|Work", "SoccerClubSeason|SportsTeamSeason|Organisation", "Album|MusicalWork|Work",
				"Ship|MeanOfTransportation","Newspaper|PeriodicalLiterature|WrittenWork|Work", "Single|MusicalWork|Work",
				"FilmFestival|Event",
				"SportsTeamMember|OrganisationMember|Person",
				"SoccerClub|SportsTeam|Organisation"
		});
		ignoreTypes.put(FeatureDictionary.PERSON, new String[]{

		});
	}

	public static class EntityContext implements Serializable{
		//people co-occurred with and docids appeared in
		public Map<String, Integer> people;
		public Set<String> docIds;
		public String name, type;
		public EntityContext(String name, String type){
			this.name = name;
			this.type = type;
			people = new LinkedHashMap<String, Integer>();
			docIds = new HashSet<String>();
		}

		public EntityContext(String name, String type, Document doc, Archive archive) {
			this.name = name;
			this.type = type;
			people = new LinkedHashMap<String, Integer>();
			docIds = new HashSet<String>();

			Set<String> ownerNames = archive.ownerNames;
			EmailDocument ed = (EmailDocument) doc;
			List<String> names = ed.getAllNames();
			Set<String> nonown = new HashSet<String>();
			if (names != null)
				for (String n : names)
					if (!ownerNames.contains(n))
						nonown.add(n);

			String docId = doc.getUniqueId();
			add(docId, nonown);
		}

		public void add(String docId, Set<String> names){
			docIds.add(docId);
			for(String name: names) {
				if (!people.containsKey(name))
					people.put(name, 0);
				people.put(name, people.get(name)+1);
			}
		}

		public List<EntityContext> getClosestNHits(Collection<EntityContext> contexts, int N) {
			Map<EntityContext, Double> scontexts = new LinkedHashMap<EntityContext, Double>();
			String wc = FeatureGeneratorUtil.tokenFeature(name);
			boolean acronym = false, dictWord = false;
			if ("ac".equals(wc))
				acronym = true;
			if (!acronym)
				if (DictUtils.commonDictWords5000.contains(name.toLowerCase()))
					dictWord = true;

			//dont expand stuff like office, all, national, the etc.
			if (!dictWord) {
				for (EntityContext context : contexts) {
					if (acronym) {
						String acr = edu.stanford.muse.ie.Util.getAcronym(context.name);
						if (acr == null || !acr.equals(this.name))
							continue;
					} else {
						if(!(context.name.equals(this.name) || context.name.startsWith(this.name+" ") || context.name.endsWith(" "+this.name) || context.name.contains(" "+this.name+" ")))
//							if (!context.name.contains(this.name))
							continue;
					}
					double num = 0;
					//its a candidate, score based on context
					if (context.docIds != null && this.docIds != null)
						for (String cdocid : context.docIds)
							if (docIds.contains(cdocid))
								num++;
					if (context.people != null && this.people != null)
						for (String p : context.people.keySet())
							if (this.people.containsKey(p))
								num++;
					scontexts.put(context, num);
				}
			}
			List<Pair<EntityContext,Double>> sscontexts = Util.sortMapByValue(scontexts);
			List<EntityContext> ret = new ArrayList<EntityContext>();
			int i=0;
			for(Pair<EntityContext,Double> p: sscontexts)
				if(i++<N)
					ret.add(p.getFirst());
			return ret;
		}
	}

	public FeatureDictionary(String iType, FeatureGenerator[] featureGens) {
		this.iType = iType;
		this.featureGens = featureGens;
	}
	/**
	 * addressbook should be specially handled and dbpedia gazette is required.
	 * and make sure the addressbook is cleaned see cleanAB method
	 */
	public FeatureDictionary(Map<String, String> abNames, Map<String, String> dbpedia, FeatureGenerator[] featureGens, String type) {
		this.iType = type;
		this.featureGens = featureGens;

		Set<Map<String, String>> gazettes = new HashSet<Map<String, String>>();
		//adress book contains junk, dont want to rely on it now, also trying to filter addressbook names based on this frequency generated over AB and DBpedia does not make sense
		gazettes.add(abNames);
		gazettes.add(dbpedia);

		long start_time = System.currentTimeMillis();
		long timeToComputeFeatures = 0, timeOther = 0;
		long tms = System.currentTimeMillis();
		log.info("Analysing gazettes");
		//Pattern endClean = Pattern.compile("^\\W+|\\W+$");
		int g = 0, nume = 0;
		Set<String> ignoreTypeSet = new HashSet<String>();
		if(ignoreTypes.get(iType)!=null)
			for(String it: ignoreTypes.get(iType)) {
				ignoreTypeSet.add(it);
			}
		Set<String> ignoredTypes = new HashSet<String>();
		for (Map<String, String> gazette : gazettes) {
			final int gs = gazette.size();
			int gi = 0;
			for (String str : gazette.keySet()) {
				timeOther += System.currentTimeMillis() - tms;
				tms = System.currentTimeMillis();

				//if is a single word name and in dictionary, ignore.
				if(!str.contains(" ") && DictUtils.fullDictWords.contains(str.toLowerCase()))
					continue;

				String entityType = gazette.get(str);
				if(ignoreTypeSet.contains(entityType)) {
					ignoredTypes.add(entityType);
					continue;
				}

				for(FeatureGenerator fg: featureGens) {
					if(!fg.getContextDependence()) {
						add(fg.createFeatures(str, null, null, iType), entityType);
					}
				}
				timeToComputeFeatures += System.currentTimeMillis() - tms;
				tms = System.currentTimeMillis();

				if ((++gi) % 10000 == 0) {
					log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
					log.info("Time spent in computing features: " + timeToComputeFeatures + " total time spent: " + (timeOther + timeToComputeFeatures));
				}
				nume++;
			}

			log.info("Done analysing gazette: " + (g++));
		}
		log.info("Trained over "+nume+" entities in "+gazettes.size());
		log.info("Ignored: "+ignoredTypes.size());

		int gi = 0;
		for(Map<String,String> gazette: gazettes) {
			log.info("Gazettes: " + gi + " size: " + gazette.size());
			gi++;
		}

		log.info("Done ananlysing gazettes in: " + (System.currentTimeMillis() - start_time));
    }

	public FeatureVector getVector(String cname){
		Map<String,List<String>> features = FeatureGenerator.generateFeatures(cname,null,null,iType,featureGens);
		return new FeatureVector(this, featureGens, features);
	}

	/**
	 * Adds the context of a match to the dictionary
	 * @param match recognised entity name
	 * @param type DBpedia type or some other*/
	public EntityContext considerDocFor(String match, String type, Document doc, Archive archive){
		//strip any Mr, Mrs. stuff
		Pair<String,Boolean> p = WordFeature.checkAndStrip(match, FeatureDictionary.startMarkersForType.get(FeatureDictionary.PERSON),true, true);
		match = p.getFirst();
		Set<String> ownerNames = archive.ownerNames;
		EmailDocument ed = (EmailDocument)doc;
		List<String> names = ed.getAllNames();
		Set<String> nonown = new HashSet<String>();
		if(names!=null)
			for(String name: names)
				if(!ownerNames.contains(name))
					nonown.add(name);

		String docId = doc.getUniqueId();
		if (!this.entityContexts.containsKey(match))
			this.entityContexts.put(match, new EntityContext(match, type));

		this.entityContexts.get(match).add(docId, nonown);
		return this.entityContexts.get(match);
	}

	//dictionary should not be build anywhere else other tahn in this file
	private void add(Map<String,List<String>> wfeatures, String type) {
		//System.err.println("Adding type: "+type);
		for (String dim : wfeatures.keySet()) {
			if (!features.containsKey(dim))
				features.put(dim, new LinkedHashMap<String, Pair<Integer, Integer>>());
			Map<String, Pair<Integer, Integer>> hm = features.get(dim);
			if (wfeatures.get(dim) != null)
				for (String val : wfeatures.get(dim)) {
					if (!hm.containsKey(val))
						hm.put(val, new Pair<Integer, Integer>(0, 0));
					Pair<Integer, Integer> p = hm.get(val);
					if (type.contains(iType))
						p.first++;
					p.second++;
					hm.put(val, p);
				}
			features.put(dim, hm);
		}
	}

	public double getMaxpfreq(String name){
		String[] words = name.split("\\s+");
		double p = 0;
		for(String word: words) {
			double pw = 0;
			try{
				Pair<Integer,Integer> freqs = features.get("word").get("word");
				pw = (double)freqs.first/freqs.second;
			}catch(Exception e){
				;
			}
			p = Math.max(pw, p);
		}
		return p;
	}

	/**
	 * returns the value of the dim in the features generated*/
	public Double getFeatureValue(String name, String dim){
		FeatureVector wfv = getVector(name);
		Double[] fv = wfv.fv;
		Integer idx = wfv.featureIndices.get(dim);
		if(idx == null || idx>fv.length) {
			NER.log.warn("No proper index found for dim " + dim + ", " + idx + " for " + name);
			return 0.0;
		}
		return fv[idx];
	}

	//clean address book names based on gazette freqs on dbpedia.
	static Map<String,Pair<String,Double>> scoreAB(Map<String,String> abNames, Map<String,String> dbpedia){
		long start_time = System.currentTimeMillis();
		long timeToComputeFeatures = 0, timeOther = 0;
		long tms = System.currentTimeMillis();
		log.info("Analysing gazettes");
		System.err.println("Analysing gazettes");
		Pattern endClean = Pattern.compile("^\\W+|\\W+$");
		FeatureDictionary wfs = new FeatureDictionary(FeatureDictionary.PERSON, new FeatureGenerator[]{new WordFeature()});
		int gi = 0, gs = dbpedia.size();
		for (String str : dbpedia.keySet()) {
			timeOther += System.currentTimeMillis() - tms;
			tms = System.currentTimeMillis();

			//the type supplied to WordFeatures should not matter, atleast for filtering
			wfs.add(new WordFeature().createFeatures(str, FeatureDictionary.PERSON), dbpedia.get(str));

			if ((++gi) % 10000 == 0) {
				log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs)+"%");
				log.info("Time spent in computing features: " + timeToComputeFeatures + " total time spent: " + (timeOther + timeToComputeFeatures));
			}
		}

		Map<String,Pair<String,Double>> scoredAB = new LinkedHashMap<String, Pair<String,Double>>();
		for(String name: abNames.keySet()) {
			double val = wfs.getFeatureValue(name, "words");
			scoredAB.put(name,new Pair<String,Double>(abNames.get(name), val));
		}
		return scoredAB;
	}

//	public Map<String,String> getCleanAB(){
//		return getCleanAB(0.8);
//	}
//
//	public Map<String,String> getCleanAB(double thresh){
//		if(scoredAddressBook == null) {
//			log.warn("This Feature dictionary of type: " + iType + " does not have score addressbook, did you properly initialise the object?");
//			System.err.println("This Feature dictionary of type: " + iType + " does not have score addressbook, did you properly initialise the object?");
//			return null;
//		}
//
//		Map<String,String> cleanAB = new LinkedHashMap<String, String>();
//		for(String entry: scoredAddressBook.keySet()){
//			Pair<String,Double> p = scoredAddressBook.get(entry);
//			if(p.second>0.8)
//				cleanAB.put(entry, p.first);
//		}
//		return cleanAB;
//	}

	public static Map<String,String> cleanAB(Map<String,String> abNames, Map<String,String> dbpedia){
		if(abNames == null)
			return abNames;

		if(dbpedia == null || dbpedia.size()<1)
			dbpedia = EmailUtils.readDBpedia();

		Map<String,Pair<String,Double>> scoredAddressBook = scoreAB( abNames, dbpedia);

		Map<String,String> cleanAB = new LinkedHashMap<String, String>();
		for(String entry: scoredAddressBook.keySet()){
			Pair<String,Double> p = scoredAddressBook.get(entry);
			if(p.second>0.8)
				cleanAB.put(entry, p.first);
		}
		return cleanAB;
	}

	@Override
	public String toString() {
		String res = "";
		for (String dim : features.keySet()) {
			res += "Dim:" + dim + "--";
			for (String val : features.get(dim).keySet())
				res += val + ":::" + features.get(dim).get(val) + "---";
			res += "\n";
		}
		return res;
	}

	//TODO: get a better location for this method
	public static svm_parameter getDefaultSVMParam() {
		svm_parameter param = new svm_parameter();
		// default values
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.RBF;
		param.degree = 3;
		param.gamma = 0; // 1/num_features
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = 1;
		param.eps = 1e-3;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 1;
		param.nr_weight = 0;
		param.weight_label = new int[0];
		param.weight = new double[0];
		return param;
	}

	public static void main(String[] args) {
		String content = "\"If they were to wrest control away from Robert Bertholf then whoever manages Jess' affairs now and, after his death, the beneficiaries of his estate (and I don't know who they would be) would have control of Robert's work...\".";
		String[] a = new String[] { "Robert", "Robert Bertholf" };
		String str = "(";
		for (int i = 0; i < a.length; i++) {
			str += a[i];
			if (i < (a.length - 1))
				str += "|";
		}
		str += ")";
		Pattern p = Pattern.compile(str);
		Matcher m = p.matcher(content);
		StringBuffer sb = new StringBuffer();
		while (m.find())
			m.appendReplacement(sb, " <START:person> " + m.group() + " <END> ");
		m.appendTail(sb);
		content = sb.toString();
		System.err.println(content);
	}
}

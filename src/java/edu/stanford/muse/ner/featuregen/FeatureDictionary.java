package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.webapp.SimpleSessions;
import libsvm.svm_parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Probable improvements in train file generation are:
 * 1. Use proper relevant stop words for extracting entities for each type. (for example why would entity contain "in"? take stop-word stats from Dbpedia)
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
 * TODO: Explore indexing of this data-structure
 * */
public class FeatureDictionary implements Serializable {
	private static final long							serialVersionUID	= 1L;
	//dimension -> instance -> entity type of interest -> #positive type, #negative type
	//patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
	public Map<String, Map<String, Map<String, Pair<Integer, Integer>>>> features = new LinkedHashMap<String, Map<String, Map<String, Pair<Integer, Integer>>>>();
	//contains number of times a CIC pattern is seen (once per doc), also considers quoted text which may reflect wrong count
	//This can get quite depending on the archive and is not a scalable solution

    //this data-structure is only used for Segmentation which itself is not employed anywhere
    public Map<String,Integer> counts = new LinkedHashMap<String,Integer>();

	public static String								PERSON				= "Person", ORGANISATION = "Organisation", PLACE = "Place";
    public static String[]                              allTypes            = new String[]{PERSON, ORGANISATION, PLACE};
	static Log											log					= LogFactory.getLog(FeatureDictionary.class);
	public static Map<String, String[]>					aTypes				= new LinkedHashMap<String, String[]>();
	public FeatureGenerator[]                           featureGens         = null;
	public static Map<String,String[]>                  startMarkersForType = new LinkedHashMap<String, String[]>();
	public static Map<String,String[]>                  endMarkersForType   = new LinkedHashMap<String, String[]>();
	public static List<String> ignoreTypes = new ArrayList<String>();
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
        //dont see why these ignoreTypes have to be type (i.e. person, org, loc) specific
		ignoreTypes = Arrays.asList(
				"Election|Event", "MilitaryUnit|Organisation", "Ship|MeanOfTransportation", "OlympicResult",
				"SportsTeamMember|OrganisationMember|Person", "TelevisionShow|Work", "Book|WrittenWork|Work",
				"Film|Work", "Album|MusicalWork|Work", "Band|Organisation", "SoccerClub|SportsTeam|Organisation",
				"TelevisionEpisode|Work", "SoccerClubSeason|SportsTeamSeason|Organisation", "Album|MusicalWork|Work",
				"Ship|MeanOfTransportation","Newspaper|PeriodicalLiterature|WrittenWork|Work", "Single|MusicalWork|Work",
				"FilmFestival|Event",
				"SportsTeamMember|OrganisationMember|Person",
				"SoccerClub|SportsTeam|Organisation"
		);
	}

	public FeatureDictionary(FeatureGenerator[] featureGens) {
		this.featureGens = featureGens;
	}
	/**
	 * address book should be specially handled and DBpedia gazette is required.
	 * and make sure the address book is cleaned see cleanAB method
	 */
	public FeatureDictionary(Map<String, String> gazettes, FeatureGenerator[] featureGens) {
        this.featureGens = featureGens;

		long start_time = System.currentTimeMillis();
		long timeToComputeFeatures = 0, timeOther = 0;
		long tms;
		log.info("Analysing gazettes");

        int g = 0, nume = 0;
	    final int gs = gazettes.size();
        int gi = 0;
        for (String str : gazettes.keySet()) {
            tms = System.currentTimeMillis();

            //if is a single word name and in dictionary, ignore.
            if(!str.contains(" ") && DictUtils.fullDictWords.contains(str.toLowerCase()))
                continue;

            String entityType = gazettes.get(str);
            if(ignoreTypes.contains(entityType)) {
                continue;
            }

            for(FeatureGenerator fg: featureGens) {
                if(!fg.getContextDependence()) {
                    for(String iType: allTypes)
                       add(fg.createFeatures(str, null, null, iType), entityType, iType);
                }
            }
            timeToComputeFeatures += System.currentTimeMillis() - tms;

            if ((++gi) % 10000 == 0) {
                log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                log.info("Time spent in computing features: " + timeToComputeFeatures);
            }
            nume++;
        }

		log.info("Considered "+nume+" entities in "+gazettes.size()+" total entities");
		log.info("Done analysing gazettes in: " + (System.currentTimeMillis() - start_time));
    }

	public FeatureVector getVector(String cname, String iType){
		Map<String,List<String>> features = FeatureGenerator.generateFeatures(cname,null,null,iType,featureGens);
		return new FeatureVector(this, iType, featureGens, features);
	}

	//dictionary should not be built anywhere without this method
	private void add(Map<String,List<String>> wfeatures, String type, String iType) {
		for (String dim : wfeatures.keySet()) {
			if (!features.containsKey(dim))
				features.put(dim, new LinkedHashMap<String, Map<String, Pair<Integer, Integer>>>());
			Map<String, Map<String,Pair<Integer, Integer>>> hm = features.get(dim);
			if (wfeatures.get(dim) != null)
				for (String val : wfeatures.get(dim)) {
					if (!hm.containsKey(val)) {
                        hm.put(val, new LinkedHashMap<String, Pair<Integer, Integer>>());
                        for (String at: allTypes)
                            hm.get(val).put(at, new Pair<Integer,Integer>(0,0));
                    }
					Pair<Integer, Integer> p = hm.get(val).get(iType);
					if (type.contains(iType))
						p.first++;
					p.second++;
					hm.get(val).put(iType, p);
				}
			features.put(dim, hm);
		}
	}

	public double getMaxpfreq(String name, String iType){
		String[] words = name.split("\\s+");
		double p = 0;
		for(String word: words) {
			double pw = 0;
			try{
				Pair<Integer,Integer> freqs = features.get("word").get(word).get(iType);
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
	public Double getFeatureValue(String name, String dim, String iType){
		FeatureVector wfv = getVector(name, iType);
		Double[] fv = wfv.fv;
		Integer idx = wfv.featureIndices.get(dim);
		if(idx == null || idx>fv.length) {
			//log.warn("No proper index found for dim " + dim + ", " + idx + " for " + name);
			return 0.0;
		}
		return fv[idx];
	}

	//clean address book names based on gazette freqs on dbpedia.
	static Map<String,Pair<String,Double>> scoreAB(Map<String,String> abNames, Map<String,String> dbpedia){
		long timeToComputeFeatures = 0, timeOther = 0;
		long tms = System.currentTimeMillis();
        String iType = FeatureDictionary.PERSON;
		log.info("Analysing gazettes");
		FeatureDictionary wfs = new FeatureDictionary(new FeatureGenerator[]{new WordSurfaceFeature()});
		int gi = 0, gs = dbpedia.size();
		for (String str : dbpedia.keySet()) {
			timeOther += System.currentTimeMillis() - tms;
			tms = System.currentTimeMillis();

			//the type supplied to WordFeatures should not matter, at least for filtering
			wfs.add(new WordSurfaceFeature().createFeatures(str, iType), dbpedia.get(str), iType);
            timeToComputeFeatures += System.currentTimeMillis()-tms;
			if ((++gi) % 10000 == 0) {
				log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs)+"%");
				log.info("Time spent in computing features: " + timeToComputeFeatures + " total time spent: " + (timeOther + timeToComputeFeatures));
			}
		}

		Map<String,Pair<String,Double>> scoredAB = new LinkedHashMap<String, Pair<String,Double>>();
		for(String name: abNames.keySet()) {
			double val = wfs.getFeatureValue(name, "words", iType);
			scoredAB.put(name,new Pair<String,Double>(abNames.get(name), val));
		}
		return scoredAB;
	}

	public static Map<String,String> cleanAB(Map<String,String> abNames, Map<String,String> dbpedia){
		if(abNames == null)
			return abNames;

		if(dbpedia == null || dbpedia.size()<1)
			dbpedia = EmailUtils.readDBpedia();

		Map<String,Pair<String,Double>> scoredAddressBook = scoreAB( abNames, dbpedia);

		Map<String,String> cleanAB = new LinkedHashMap<String, String>();
		for(String entry: scoredAddressBook.keySet()){
			Pair<String,Double> p = scoredAddressBook.get(entry);
			if(p.second>0.5)
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
        try {
            String userDir = "/Users/vihari/epadd-appraisal/user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            Map<String,String> abNames = EmailUtils.getNames(archive.addressBook.allContacts());
            Map<String, String> dbpedia = EmailUtils.readDBpedia();
            Map<String, String> gazzs = new LinkedHashMap<>();
            gazzs.putAll(abNames);
            gazzs.putAll(dbpedia);
            System.err.println("AB: "+abNames.size()+"\nDBpedia: "+dbpedia.size()+"\nAll:"+ gazzs.size());
            FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature()};
            FeatureDictionary dictionary = new FeatureDictionary(gazzs, fgs);
            Map<String, Map<String, Map<String, Pair<Integer, Integer>>>> features = dictionary.features;
            for(String str1: features.keySet())
                for(String str2: features.get(str1).keySet()) {
                    for (String str3 : features.get(str1).get(str2).keySet()) {
                        System.err.print(str1 + " : " + str2 + " : " + str3 + " : " + features.get(str1).get(str2).get(str3) + ", ");
                    }
                    System.err.println();
                }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}

package edu.stanford.muse.ner;

import edu.stanford.muse.Config;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.featuregen.*;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.SVMModel;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.ner.train.NERTrainer;
import edu.stanford.muse.ner.train.SVMModelTrainer;
import edu.stanford.muse.ner.util.ArchiveContent;
import edu.stanford.muse.util.*;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;

/**
 * This is the only class dependent on ePADD in this package and has all the interfacing functionality*/
public class NER implements StatusProvider {
	public static Log		    log					= LogFactory.getLog(NER.class);
    //names and names_original should include all the names in the title
	public static String		EPER				= "en_person", ELOC = "en_loc", EORG = "en_org", NAMES_ORIGINAL = "en_names_original";
    public static String		EPER_TITLE			= "en_person_title", ELOC_TITLE = "en_loc_title", EORG_TITLE = "en_org_title";
    public static String		NAMES_OFFSETS		= "en_names_offsets", TITLE_NAMES_OFFSETS = "en_names_offsets_title";

	String						status;
	double						pctComplete			= 0;
	boolean						cancelled			= false;
	Archive						archive				= null;
	//in seconds
	long						time				= -1, eta = -1;
	static FieldType			ft;
	int[]						pcts				= new int[] { 16, 32, 50, 100 };
    StatusProvider statusProvider =  null;

	public static class NERStats {
		//non-repeating number of instances of each type
		public Map<Short, Integer>		counts;
		//total number of entities of each type recognised
		public Map<Short, Integer>		rcounts;
		public Map<Short, Set<String>>	all;

		NERStats() {
			counts = new LinkedHashMap<>();
			rcounts = new LinkedHashMap<>();
			all = new LinkedHashMap<>();
		}

		//a map of entity-type key and value list of entities
		public void update(Map<Short, List<String>> map) {
			for (Short type : map.keySet()) {
				if (!rcounts.containsKey(type))
					rcounts.put(type, 0);
				rcounts.put(type, rcounts.get(type) + map.get(type).size());

				if (!all.containsKey(type))
					all.put(type, new HashSet<String>());
				if (map.get(type) != null)
					for (String str : map.get(type))
						all.get(type).add(str);

				counts.put(type, all.get(type).size());
			}
		}

		@Override
		public String toString() {
			String str = "";
			for (Short t : counts.keySet())
				str += "Type: " + t + ":" + counts.get(t) + "\n";
			return str;
		}
	}

	public static class NEROptions {
		public boolean addressbook = true, dbpedia = true, segmentation = true, latentgroupexpansion = false;
		public String prefix = "";
		public String wfsName = "WordFeatures.ser", modelName = "svm.model";
		public String evaluatorName = "ePADD NER complete";
		public String dumpFldr = null;

		public NEROptions setAddressBook(boolean val) {
			this.addressbook = val;
			return this;
		}

		public NEROptions setDBpedia(boolean val) {
			this.dbpedia = val;
			return this;
		}

		public NEROptions setSegmentation(boolean val) {
			this.segmentation = val;
			return this;
		}

		public NEROptions setPrefix(String prefix){
			this.prefix = prefix;
			return this;
		}

		public NEROptions setName(String name){
			this.evaluatorName = name;
			return this;
		}

		public NEROptions setDumpFldr(String name){
			this.dumpFldr = name;
			return this;
		}
	}

	public NERStats	stats;

	static {
		ft = new FieldType();
		ft.setStored(true);
		ft.setIndexed(true);
		ft.freeze();
	}

	public NER(Archive archive) {
		this.archive = archive;
		time = 0;
		eta = 10 * 60;
		stats = new NERStats();
	}

	private static void storeNameOffsets(org.apache.lucene.document.Document doc, boolean body, List<Triple<String, Integer, Integer>> offsets)
	{
        String fieldName = null;
        if(body)
            fieldName = NAMES_OFFSETS;
        else
            fieldName = TITLE_NAMES_OFFSETS;
		FieldType storeOnly_ft = new FieldType();
		storeOnly_ft.setStored(true);
		storeOnly_ft.freeze();
		try {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bs);
			oos.writeObject(offsets);
			oos.close();
			bs.close();
			doc.removeField(fieldName);
			doc.add(new Field(fieldName, bs.toByteArray(), storeOnly_ft));
		} catch (IOException e) {
			log.warn("Failed to serialize field: "+fieldName);
			e.printStackTrace();
		}
	}

    //This method could be a little slower
    public static List<Triple<String, Integer, Integer>> getNameOffsets(Document doc, Archive archive, boolean body) throws IOException{
        if (archive == null || doc == null) {
            Util.aggressiveWarn("Archive/doc is null to retrieve offsets", -1);
            return null;
        }
        org.apache.lucene.document.Document ldoc = archive.getDoc(doc);
        return getNameOffsets(ldoc, body);
    }

    public static List<Triple<String, Integer, Integer>> getNameOffsets(org.apache.lucene.document.Document doc, boolean body) {
        String fieldName = null;
        if(body)
            fieldName = NAMES_OFFSETS;
        else
            fieldName = TITLE_NAMES_OFFSETS;
		BytesRef bytesRef = doc.getBinaryValue(fieldName);
		if (bytesRef == null)
			return null;
		byte[] data = bytesRef.bytes;
		if (data == null)
			return null;

		ByteArrayInputStream bs = new ByteArrayInputStream(data);
		List<Triple<String, Integer, Integer>> result;
		ObjectInputStream ois;
		try {
			ois = new ObjectInputStream(bs);
			result = (List<Triple<String, Integer, Integer>>) ois.readObject();
		} catch (Exception e) {
			log.warn("Failed to deserialize field: "+fieldName);
			e.printStackTrace();
			result = new ArrayList<Triple<String, Integer, Integer>>();
		}

		return result;
	}

    public NERModel loadModel() throws IOException{
        String MODEL_DIR = archive.baseDir + File.separator + Config.MODELS_FOLDER;
        SVMModel model = SVMModel.loadModel(new File(MODEL_DIR+File.separator+SVMModel.modelFileName));
        return model;
    }

    public static NERModel trainArchiveIndependentModel(Object params){
        SVMModelTrainer.TrainingParam tparams = (SVMModelTrainer.TrainingParam)params;
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        Tokenizer tokenizer = new CICTokenizer();
        FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature()};
        SVMModelTrainer trainer = new SVMModelTrainer();
        List<Short> types = Arrays.asList(FeatureDictionary.PERSON, FeatureDictionary.PLACE, FeatureDictionary.ORGANISATION);
        List<String[]> aTypes = Arrays.asList(
                FeatureDictionary.aTypes.get(FeatureDictionary.PERSON),
                FeatureDictionary.aTypes.get(FeatureDictionary.PLACE),
                FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION)
        );
        NERModel model = trainer.trainArchiveIndependent(dbpedia, types, aTypes, fgs, tokenizer, params);
        return model;
    }

    public NERModel trainModel() {
        NERTrainer trainer = new SVMModelTrainer();
        status = "Loading DBpedia and address book";
        Map<String,String> addressbook =  EmailUtils.getNames(archive.addressBook.allContacts());
        pctComplete = 2;
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        pctComplete = 10;
        addressbook = FeatureDictionary.cleanAB(addressbook, dbpedia);
        Tokenizer tokenizer = new CICTokenizer();
        FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature()};
        List<Short> types = Arrays.asList(FeatureDictionary.PERSON, FeatureDictionary.PLACE, FeatureDictionary.ORGANISATION);
        List<String[]> aTypes = Arrays.asList(
                FeatureDictionary.aTypes.get(FeatureDictionary.PERSON),
                FeatureDictionary.aTypes.get(FeatureDictionary.PLACE),
                FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION)
        );
        String CACHE_DIR = archive.baseDir + File.separator + Config.CACHE_FOLDER;
        String MODEL_DIR = archive.baseDir + File.separator + Config.MODELS_FOLDER;
        SVMModelTrainer.TrainingParam tparams = SVMModelTrainer.TrainingParam.initialize(CACHE_DIR, MODEL_DIR);
        ArchiveContent archiveContent = new ArchiveContent() {
            @Override
            public int getSize() {
                return archive.getAllDocs().size();
            }

            @Override
            public String getContent(int i) {
                Document doc = archive.getAllDocs().get(i);
                return archive.getContents(doc, true);
            }
        };
        statusProvider = (SVMModelTrainer)trainer;
        NERModel nerModel = trainer.train(archiveContent, dbpedia, addressbook,types, aTypes, fgs, tokenizer, tparams);
        return nerModel;
    }

	//TODO: Consider using Atomic reader for accessing the index, if it improves performance
	//main method trains the model, recognizes the entities and updates the doc.
	public void trainAndRecognise() throws CancelledException, IOException {
		time = 0;
		archive.openForRead();
		archive.setupForWrite();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

        String modelFile = archive.baseDir + File.separator + "models" + File.separator + SVMModel.modelFileName;
		List<Document> docs = archive.getAllDocs();
		NERModel nerModel = SVMModel.loadModel(new File(modelFile));
        if(nerModel == null) {
            status = "Did not find ner model in " + modelFile;
            status = "Building model";
            nerModel = trainModel();
        }

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		int di = 0, ds = docs.size();
		int ps = 0, ls = 0, os = 0;

		long totalTime = 0, updateTime = 0, recTime = 0, duTime = 0, snoTime = 0;
		for (Document doc : docs) {
			long st1 = System.currentTimeMillis();
			long st = System.currentTimeMillis();
			org.apache.lucene.document.Document ldoc = archive.getDoc(doc);
			//pass the lucene doc instead of muse doc, else a major performance penalty
			//do not recognise names in original content and content separately
			//Its possible to improve the performance further by using linear kernel
			// instead of RBF kernel and classifier instead of a regression model
			// (the confidence scores of regression model can be useful in segmentation)
            //TODO: @bug -> should also recognise names in the subject
			String originalContent = archive.getContents(ldoc, true);
			String content = archive.getContents(ldoc, false);
            String title = archive.getTitle(ldoc);
			//original content is substring of content;
            Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = nerModel.find(content);
            Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsetsTitle = nerModel.find(title);
			recTime += System.currentTimeMillis() - st;
			st = System.currentTimeMillis();

			Map<Short, List<String>> map = mapAndOffsets.first;
            Map<Short, List<String>> mapTitle = mapAndOffsetsTitle.first;
			stats.update(map);
            stats.update(mapTitle);
			updateTime += System.currentTimeMillis() - st;
			st = System.currentTimeMillis();

			//!!!!!!SEVERE!!!!!!!!!!
			//TODO: an entity name is stored in NAMES, NAMES_ORIGINAL, nameoffsets, and one or more of EPER, ELOC, EORG fields, that is a lot of redundancy
			//!!!!!!SEVERE!!!!!!!!!!
			storeNameOffsets(ldoc, true, mapAndOffsets.second);
            storeNameOffsets(ldoc, false, mapAndOffsetsTitle.second);
			List<String> persons = map.get(FeatureDictionary.PERSON);
			List<String> locs = map.get(FeatureDictionary.PLACE);
			List<String> orgs = map.get(FeatureDictionary.ORGANISATION);
            List<String> personsTitle = mapTitle.get(FeatureDictionary.PERSON);
            List<String> locsTitle = mapTitle.get(FeatureDictionary.PLACE);
            List<String> orgsTitle = mapTitle.get(FeatureDictionary.ORGANISATION);
			ps += persons.size() + personsTitle.size();
			ls += locs.size() + locsTitle.size();
			os += orgs.size() + orgsTitle.size();
			snoTime += System.currentTimeMillis() - st;
			st = System.currentTimeMillis();

		    ldoc.removeField(EPER);	ldoc.removeField(EPER_TITLE);
            ldoc.removeField(ELOC); ldoc.removeField(ELOC_TITLE);
            ldoc.removeField(EORG); ldoc.removeField(EORG_TITLE);

			ldoc.add(new StoredField(EPER, Util.join(persons, Indexer.NAMES_FIELD_DELIMITER)));
			ldoc.add(new StoredField(ELOC, Util.join(locs, Indexer.NAMES_FIELD_DELIMITER)));
			ldoc.add(new StoredField(EORG, Util.join(orgs, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EPER_TITLE, Util.join(personsTitle, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(ELOC_TITLE, Util.join(locsTitle, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EORG_TITLE, Util.join(orgsTitle, Indexer.NAMES_FIELD_DELIMITER)));

			List<String> names_original = new ArrayList<String>(), names = new ArrayList<String>();
			if(persons!=null)
				names.addAll(persons);
			if(locs!=null)
				names.addAll(locs);
			if(orgs!=null)
				names.addAll(orgs);
			int ocs = originalContent.length();
			List<Triple<String,Integer,Integer>> offsets = mapAndOffsets.getSecond();
			for (int oi=0;oi<offsets.size();oi++) {
				Triple<String,Integer,Integer> offset = offsets.get(oi);
				String name = offset.getFirst();
				if(offset  == null) {
					log.warn("No offset found for: "+name);
					break;
				}
				if(offset.getSecond() < ocs )
					names_original.add(name);
			}

			ldoc.add(new StoredField(NAMES_ORIGINAL, Util.join(names_original, Indexer.NAMES_FIELD_DELIMITER)));
			//log.info("Found: "+names.size()+" total names and "+names_original.size()+" in original");

            //TODO: Sometimes, updating can lead to deleted docs and keeping these deleted docs can bring down the search performance
			//Makes me think building a new index could be faster
			archive.updateDocument(ldoc);
			duTime += System.currentTimeMillis() - st;
			di++;

			totalTime += System.currentTimeMillis() - st1;
			pctComplete = 30 + ((double)di/(double)ds) * 70;
			double ems = (double) (totalTime * (ds-di)) / (double) (di*1000);
			status = "Recognized entities in " + Util.commatize(di) + " of " + Util.commatize(ds) + " emails ";
			//Util.approximateTimeLeft((long)ems/1000);
			eta = (long)ems;

			if(di%100 == 0)
                log.info(status);
			time += System.currentTimeMillis() - st;

			if (cancelled) {
				status = "Cancelling...";
				throw new CancelledException();
			}
		}

		log.info("Trained and recognised entities in " + di + " docs in " + totalTime + "ms" + "\nPerson: " + ps + "\nOrgs:" + os + "\nLocs:" + ls);
		archive.close();
		//prepare to read again.
		archive.openForRead();
	}

	//arrange offsets such that the end offsets are in increasing order and if there are any overlapping offsets, the bigger of them should appear first
	//makes sure the redaction is proper.
	public static void arrangeOffsets(List<Triple<String,Integer,Integer>> offsets) {
		Collections.sort(offsets, new Comparator<Triple<String, Integer, Integer>>() {
			@Override
			public int compare(Triple<String, Integer, Integer> t1, Triple<String, Integer, Integer> t2) {
				if (t1.getSecond() != t2.getSecond())
                    return t1.getSecond()-t2.getSecond();
                else
                    return t2.getThird()-t1.getThird();
			}
		});
	}

    //retains only filtered entities
	public static String retainOnlyNames(String text, org.apache.lucene.document.Document doc) {
        List<Triple<String,Integer, Integer>> offsets = edu.stanford.muse.ner.NER.getNameOffsets(doc, true);
        if (offsets == null) {
		    //mask the whole content
            offsets = new ArrayList<Triple<String, Integer, Integer>>();
            log.warn("Retain only names method received null offset, redacting the entire text");
		}

		int len = text.length();
        offsets.add(new Triple<String, Integer, Integer>(null, len, len)); // sentinel
        StringBuilder result = new StringBuilder();
		int prev_name_end_pos = 0; // pos of first char after previous name

		//make sure the offsets are in order, i.e. the end offsets are in increasing order
		arrangeOffsets(offsets);
        List<String> people = Archive.getEntitiesInLuceneDoc(doc, NER.EPER, true);
        List<String> orgs = Archive.getEntitiesInLuceneDoc(doc, NER.EORG, true);
        List<String> places = Archive.getEntitiesInLuceneDoc(doc, NER.ELOC, true);
        Set<String> allEntities = new LinkedHashSet<>();
        if (people!=null)
            allEntities.addAll(people);
        if (orgs!=null)
            allEntities.addAll(orgs);
        if (places!=null)
            allEntities.addAll(places);

        for (Triple<String, Integer, Integer> t : offsets) {
            String entity = t.first;
            if(!allEntities.contains(entity))
                continue;

          	int begin_pos = t.second();
			int end_pos = t.third();
			if (begin_pos > len || end_pos > len) {
				// TODO: this is unclean. Happens because we concat body & title together when we previously generated these offsets but now we only have body.
				begin_pos = end_pos = len;
			}

			if (prev_name_end_pos > begin_pos) // something strange, but possible - the same string can be recognized as multiple named entity types
				continue;
			String filler = text.substring(prev_name_end_pos, begin_pos);
			//filler = filler.replaceAll("\\w", "."); // CRITICAL: \w only matches (redacts) english language
			filler = filler.replaceAll("[^\\p{Punct}\\s]", ".");
			result.append(filler);
			result.append(text.substring(begin_pos, end_pos));
			prev_name_end_pos = end_pos;
		}

		return result.toString();
	}

	@Override
	public String getStatusMessage() {
		if(statusProvider!=null)
            return statusProvider.getStatusMessage();

        return JSONUtils.getStatusJSON(status, (int) pctComplete, time, eta);
	}

	@Override
	public void cancel() {
        cancelled = true;
        if(statusProvider!=null)
            statusProvider.cancel();
	}

	@Override
	public boolean isCancelled() {
		return cancelled || statusProvider.isCancelled();
	}

	public static void main1(String[] args) {
		try {
			String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            NER ner = new NER(archive);
            System.err.println("Loading model...");
            long start = System.currentTimeMillis();
            NERModel model = ner.trainModel();
            System.err.println("Trained model in: " + (System.currentTimeMillis() - start));
            System.err.println("Done loading model");
            String[] pers = new String[]{"Senator Jim Scott", "Rep. Bill Andrews"};
            String[] locs = new String[]{"Florida", "Plantation"};
            String[] orgs = new String[]{"Broward Republican Executive Committee", "National Education Association"};
            String text = "First I would like to tell you who I am. I am a lifelong Republican and have served on the Broward Republican Executive Committee since 1991. I have followed education issues in Florida since I moved here in 1973. All four of my children went to public schools here in Plantation. I continued to study education issues when I worked for Senator Jim Scott for six years, and more recently as I worked for Rep. Bill Andrews for the past eight years.\n" +
                    "On the amendment, I would like to join any effort to get it repealed. Second, if the amendment is going to be implemented, I believe that decisions about how money is spent should be taken out of the hands of the school boards. I know the trend has been to provide more local control, however, there has been little or no accountability for school boards that fritter away money on consultants, shoddy construction work, and promoting the agenda of the National Education Association and the local teachers’ unions. Third, while the teachers’ union is publicly making “nice” with you and other Republican legislators, they continue to undermine education reform measures, and because school board members rely heavily on the unions to get elected and re-elected, they pretty much call the shots on local policies. ";
            Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> ret = model.find(text);
            boolean testPass = true;
            for(Short type: ret.getFirst().keySet()) {
                System.err.print("Type: " + type);
                for (String str : ret.getFirst().get(type))
                    System.err.print(":::" + str + ":::");
                System.err.println();
            }
        } catch (Exception e) {
			e.printStackTrace();
		}
	}

    public static void main(String[] args) {
//        String cacheDir = "/Users/vihari/sandbox/cache";
//        String mwl = "/Users/vihari/sandbox/SVMModel-full.ser";
//        SVMModelTrainer.TrainingParam params = SVMModelTrainer.TrainingParam.initialize(cacheDir, mwl);
//        NERModel model = trainArchiveIndependentModel(params);
//        String text = "Hi, My name is Vihari. I work with Amuse Labs. I code in Macbook Pro, a product of Apple";
//        Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> ret = model.find(text);
//        for(Short type: ret.getFirst().keySet()) {
//            System.err.print("Type: " + type);
//            for (String str : ret.getFirst().get(type))
//                System.err.print(":::" + str + ":::");
//            System.err.println();
//        }
//        try {
//            String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user-jeb";
//            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
//        } catch(Exception e){
//            e.printStackTrace();
//        }
        String end_date = "10/02/1995";
        String[] ss =  end_date.split("/");
        System.err.println(ss.length);
    }
}

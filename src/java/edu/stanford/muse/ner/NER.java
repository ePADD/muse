package edu.stanford.muse.ner;

import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.featuregen.*;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.*;
import edu.stanford.muse.webapp.SimpleSessions;
import libsvm.*;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;

public class NER implements StatusProvider {

	private static final long	serialVersionUID	= 1L;
	public String[]				customModelTypes	= new String[] { FeatureDictionary.PERSON, FeatureDictionary.PLACE, FeatureDictionary.ORGANISATION };
	public svm_model[]			customModels		= new svm_model[] { null, null, null };
	//wordfeatures is required to assign vector to any name for svm prediction.
	public FeatureDictionary[]		wfss				= new FeatureDictionary[] { null, null, null };
	public static Log					log					= LogFactory.getLog(NER.class);
    //names and names_original should include all the names in the title
	public static String		EPER				= "en_person", ELOC = "en_loc", EORG = "en_org", NAMES_ORIGINAL = "en_names_original";
    public static String		EPER_TITLE				= "en_person_title", ELOC_TITLE = "en_loc_title", EORG_TITLE = "en_org_title";
    public static String		NAMES_OFFSETS		= "en_names_offsets", TITLE_NAMES_OFFSETS = "en_names_offsets_title";

	FileWriter					fdw					= null;
	String						status				= "";
	double						pctComplete			= 0;
	boolean						cancelled			= false;
	Archive						archive				= null;
	//in seconds
	long						time				= -1, eta = -1;
	static FieldType			ft;
	int[]						pcts				= new int[] { 16, 32, 50, 100 };
	public static final int		MIN_NAME_LENGTH		= 3, MAX_NAME_LENGTH = 100;

	public static class NERStats {
		//non-repeating number of instamces of each type
		public Map<String, Integer>		counts;
		//total number of entities of each type recognised
		public Map<String, Integer>		rcounts;
		public Map<String, Set<String>>	all;

		NERStats() {
			counts = new LinkedHashMap<String, Integer>();
			rcounts = new LinkedHashMap<String, Integer>();
			all = new LinkedHashMap<String, Set<String>>();
		}

		//a map of entity-type key and value list of entities
		public void update(Map<String, List<String>> map) {
			for (String type : map.keySet()) {
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
			for (String t : counts.keySet())
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
        org.apache.lucene.document.Document ldoc = archive.indexer.getDoc(doc);
        return getNameOffsets(ldoc, body);
    }

    public static List<Triple<String, Integer, Integer>> getNameOffsets(org.apache.lucene.document.Document doc, boolean body)
    {
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

	static <T1, T2, T3> List<T1> getFirsts(List<Triple<T1, T2, T3>> list) {
		List<T1> ret = new ArrayList<T1>();
		if (list == null)
			return ret;
		for (Triple<T1, T2, T3> t : list)
			ret.add(t.first);
		return ret;
	}

	//TODO: Consider using Atomic reader for accessing the index, if it improves performance
	//main method trains the model, recognizes the entities and updates the doc.
	public void trainAndRecognise() throws CancelledException, IOException {
		time = 0;
		Indexer li = archive.indexer;
		li.setupForRead();
		li.setupForWrite();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		List<Document> docs = archive.getAllDocs();
		int i = 0;
		for (String modelType : customModelTypes) {
			long st = System.currentTimeMillis();
			status = "Building model for " + modelType + " names";
			eta = 3*(3-i)*60;
			loadModel(modelType);
			status = "Done building model for " + modelType + " names";
			time += System.currentTimeMillis() - st;
			log.info("Loaded modeltype: " + modelType + " in " + (System.currentTimeMillis() - st));
			pctComplete = 3 * (++i);
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
			org.apache.lucene.document.Document ldoc = li.getDoc(doc);
			//pass the lucene doc instead of muse doc, else a major performance penalty
			//do not recognise names in original content and content separately
			//Its possible to improve the performance further by using linear kernel
			// instead of RBF kernel and classifier instead of a regression model
			// (the confidence scores of regression model can be useful in segmentation)
            //TODO: @bug -> should also recognise names in the subject
			String originalContent = li.getContents(ldoc, true);
			String content = li.getContents(ldoc, false);
            String title = li.getTitle(ldoc);
			//original content is substring of content;
            Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = getEntities(content);
            Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsetsTitle = getEntities(title);
			recTime += System.currentTimeMillis() - st;
			st = System.currentTimeMillis();

			Map<String, List<String>> map = mapAndOffsets.first;
            Map<String, List<String>> mapTitle = mapAndOffsetsTitle.first;
			stats.update(map);
            stats.update(map);
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
//            if(personsTitle!=null) {
//                names.addAll(personsTitle);
//                names_original.addAll(personsTitle);
//            }
//            if(locsTitle!=null) {
//                names.addAll(locsTitle);
//                names_original.addAll(locsTitle);
//            }
//            if(orgsTitle!=null) {
//                names.addAll(orgsTitle);
//                names_original.addAll(orgsTitle);
//            }

//			ldoc.add(new StoredField(NAMES, Util.join(names, Indexer.NAMES_FIELD_DELIMITER)));
			ldoc.add(new StoredField(NAMES_ORIGINAL, Util.join(names_original, Indexer.NAMES_FIELD_DELIMITER)));
			//log.info("Found: "+names.size()+" total names and "+names_original.size()+" in original");

            //TODO: Sometimes, updating can lead to deleted docs and keeping these deleted docs can bring down the search performance
			//Makes me think building a new index could be faster
			li.updateDocument(ldoc);
			duTime += System.currentTimeMillis() - st;
			di++;

			totalTime += System.currentTimeMillis() - st1;
			pctComplete = 10 + ((double)di/(double)ds) * 90;
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
		li.close();
		//prepare to read again.
		li.setupForRead();
	}

	void cleanModels(Archive archive) {
		String BASE_DIR = archive.baseDir;
		BASE_DIR += File.separator + "models";
		for (String type : customModelTypes) {
			String path = BASE_DIR + File.separator + type + "_" + edu.stanford.muse.Config.NER_MODEL_FILE;
			String wfsPath = BASE_DIR + File.separator + type + "_" + edu.stanford.muse.Config.WORD_FEATURES;
			try {
				File f = new File(path);
				f.delete();
				f = new File(wfsPath);
				f.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public Pair<svm_model, FeatureDictionary> loadModel(String type) throws CancelledException, IOException{
		return loadModel(type,null);
	}

	/**@path modelFileNames model file path followed by feature dictionary path*/
	public Pair<svm_model, FeatureDictionary> loadModel(String type, NEROptions options) throws CancelledException, IOException {
		String BASE_DIR = archive.baseDir + File.separator + "models";
		File bdir = new File(BASE_DIR);
		if (!bdir.exists())
			bdir.mkdir();

		String NER_MODEL_FILE, WORD_FEATURES_FILE;
		if(options==null) {
			NER_MODEL_FILE = edu.stanford.muse.Config.NER_MODEL_FILE;
			WORD_FEATURES_FILE = edu.stanford.muse.Config.WORD_FEATURES;
		}else{
			NER_MODEL_FILE = options.prefix+options.modelName;
			WORD_FEATURES_FILE = options.prefix+options.wfsName;
		}

		String path = BASE_DIR + File.separator + type + "_" + NER_MODEL_FILE;
		String wfsPath = BASE_DIR + File.separator + type + "_" + WORD_FEATURES_FILE;
//		System.err.println("Loading dictionary from: "+wfsPath);
//		System.err.println("Loading model file from: "+path);

		long start_time = -1;
		int customModelIndex = -1;
		for (int i = 0; i < customModelTypes.length; i++)
			if (type.equals(customModelTypes[i])) {
				customModelIndex = i;
				break;
			}

		if (customModelIndex == -1) {
			//System.err.println("Unknown type: " + type + " returning null model");
			log.error("Unknown type: " + type + " returning null model");
			return new Pair<svm_model, FeatureDictionary>(null, null);
		}

		if (customModels[customModelIndex] == null && new File(path).exists())
			//try loading.
			try {
				start_time = System.currentTimeMillis();
				customModels[customModelIndex] = svm.svm_load_model(new BufferedReader(new FileReader(path)));
				log.info("Loaded " + type + " NER model from " + path + " and the loaded file is: " + customModels[customModelIndex] + ". Loading time: " + (System.currentTimeMillis() - start_time));
			} catch (Exception e) {
				log.info("Exception while loading " + type + " NER model from " + path, e);
			}
		if (wfss[customModelIndex] == null && new File(wfsPath).exists()) {
			start_time = System.currentTimeMillis();
			log.info("Loading WordFeatures from: " + wfsPath);
			ObjectInputStream ois = null;
			try {
				ois = new ObjectInputStream(new FileInputStream(wfsPath));
				wfss[customModelIndex] = (FeatureDictionary) ois.readObject();
				ois.close();
			} catch (Exception e) {
				e.printStackTrace();
				log.info("Exception while trying to load from: " + wfsPath, e);
			}
			log.info("Loaded WordFeatures from:" + wfsPath + " in " + (System.currentTimeMillis() - start_time) + "ms");
		}

        if (wfss[customModelIndex] == null || customModels[customModelIndex] == null) {
            //train, if doesn't exist.
            start_time = System.currentTimeMillis();
            log.info("Did not find NER model at: " + path + "\nStarting to train model");
            status = "Did not find model for " + type + " names... Kicking off training";
            Pair<svm_model, FeatureDictionary> p = trainCustomModel(archive, path, wfsPath, type, options);
            customModels[customModelIndex] = p.first;
            wfss[customModelIndex] = p.second;
            log.info("Done training of " + type + " NER model in " + (System.currentTimeMillis() - start_time) + " ms");
        }

		return new Pair<svm_model, FeatureDictionary>(customModels[customModelIndex], wfss[customModelIndex]);
	}

//    public Map<StringPair<svm_model, FeatureDictionary> checkAndLoadModel(String type){
//
//    }

	public Pair<svm_model, FeatureDictionary> trainCustomModel(Archive archive, String modelFileName, String wfsPath, String iType) throws CancelledException, CorruptIndexException, LockObtainFailedException, IOException {
		return trainCustomModel(archive, modelFileName, wfsPath, iType, null);
	}

	//make sure the path supplied exists.
	public Pair<svm_model, FeatureDictionary> trainCustomModel(Archive archive, String modelFileName, String wfsPath, String iType, NEROptions options) throws CancelledException, CorruptIndexException, LockObtainFailedException, IOException {
		if (archive == null) {
			return null;
			// no need of loading the archive at this point...
//			String aFile = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
//			archive = SimpleSessions.readArchiveIfPresent(aFile);
//			log.warn("Supplied	 archive is null, trying to load from: " + aFile);
		}
		String CACHE_DIR = archive.baseDir + File.separator + "cache";
		Indexer indexer = archive.indexer;
		List<Document> docs = archive.getAllDocs();
		List<Contact> contacts = archive.addressBook.allContacts();
		long st = System.currentTimeMillis();

		status = "Reading names from DBpedia";
		Map<String, String> dbpedia = new LinkedHashMap<String, String>();
		log.info ("Memory usage before reading instance types file: " + Util.getMemoryStats());
		if(options == null || (options!=null && options.dbpedia))
			dbpedia = EmailUtils.readDBpedia();
		long dbpediaReadTimeMillis = System.currentTimeMillis() - st;
		log.info ("Read names from dbpedia types file in " + dbpediaReadTimeMillis + " ms\nMemory usage is now: " + Util.getMemoryStats());

		time += dbpediaReadTimeMillis;
		status = "Reading names from your addressbook";

		st = System.currentTimeMillis();
		Map<String, String> abNames = new LinkedHashMap<String, String>();
		if(options == null || (options!=null && options.addressbook)) {
			abNames = EmailUtils.getNames(contacts);
			NER.log.info("Cleaning address book of size: " + abNames.size());
			abNames = FeatureDictionary.cleanAB(abNames, dbpedia);
			NER.log.info("Address book size after sanitization: " + abNames.size());
		}
		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		time += System.currentTimeMillis() - st;
		st = System.currentTimeMillis();
		status = "Identifying " + iType.toLowerCase() + "s...";
		log.info("Generating features");
		FeatureGenerator[] fgs = new FeatureGenerator[]{new WordFeature()};
		//Now generate feature vectors for the names.
		FeatureDictionary wfs = new FeatureDictionary(abNames, dbpedia, fgs,iType);
		status = "Done generating features";
		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		time += System.currentTimeMillis() - st;
		st = System.currentTimeMillis();

		status = "Finding known names...";
		List<Triple<String, FeatureVector, Integer>> fvs = new ArrayList<Triple<String, FeatureVector, Integer>>();
		//all the cand names collected from the archive.
		Map<String, String> hits = new HashMap<String, String>();
		Set<String> considered = new HashSet<String>();
		int numdbpedia = 0;
		String[] aType = wfs.aTypes.get(iType);
		String[] ignoreTypes = wfs.ignoreTypes.get(iType);
		int di = 0, ds = docs.size();
		outer:
		for (Document doc : docs) {
			String content = indexer.getContents(doc, false);
            //log.info(doc.getUniqueId());
			List<Triple<String, Integer, Integer>> cands = Tokenizer.getNamesFromPattern(content, iType.equals(FeatureDictionary.PERSON));

			for (Triple<String,Integer,Integer> cand : cands) {
				String n = cand.getFirst();
				Pair<String, Boolean> cleanname = WordFeature.checkAndStrip(n,FeatureDictionary.startMarkersForType.get(FeatureDictionary.PERSON),true,true);
				n = cleanname.getFirst();

				if (considered.contains(n))
					continue;

				if (dbpedia.containsKey(n) || abNames.containsKey(n)) {
					String type = dbpedia.get(n);
					if(type == null)
						type = abNames.get(n);
					int label = -1;

					//if is a single word and is dictionary word, forget about this
					if(!n.contains(" ") && DictUtils.fullDictWords.contains(n.toLowerCase()))
						continue;

					//dont add to training
					for(String it: ignoreTypes)
						if(type.equals(it))
							continue outer;

					for (String at : aType)
						if (type.endsWith(at))
							label = 1;

					fvs.add(new Triple<String, FeatureVector, Integer>(cand.getFirst(), wfs.getVector(cand.getFirst()), label));
					wfs.considerDocFor(n, type, doc, archive);

					hits.put(n, type);
					numdbpedia++;
				}
				else
					hits.put(n, "notype");
				considered.add(n);
			}
			if((++di)%1000 == 0)
				NER.log.info("Analysed " + di + "/" + ds + " to find known instances");
		}

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}
		int numC = 0;
		if(iType.contains(FeatureDictionary.PERSON))
			for (String cname : abNames.keySet()) {
				numC++;
				NER.log.info("Adding from addressbook: " + cname);
				fvs.add(new Triple<String, FeatureVector, Integer>(cname, wfs.getVector(cname), 1));

				//for corporate and locations, we cannot use addressbook for positive examples. hence low accuracy
				//The stats after dumping addressbook and before respectively are:
				//Accuracy:0.6170212765957447, Recall:0.675
				//Accuracy:0.59, Recall:0.75
				//				else if (!iType.equals("Person") && (fv[10] < 0.5))
				//					fvs.add(new Triple<String, WordFeatureVector, Boolean>(cname, wfv, false));

			}

		//try to equalize number of vectors of each class.
		int x = 0;
		log.info("Adding some dummy names to balance the addressbook");
		for (String h : hits.keySet()) {
			if (x > numC)
				break;
			String type = hits.get(h);
			if ((type != null && !type.equals("notype")))
				continue;

			String[] tokens = h.split("\\s+");
			int num = 0;
			for (String t : tokens)
				if (DictUtils.fullDictWords.contains(t.toLowerCase()) || Indexer.MUSE_STOP_WORDS_SET.contains(t.toLowerCase()))
					num++;
			double maxp = -1;
			maxp = wfs.getMaxpfreq(h);

			//add to training sequence only if all the words are dictionary words or the max chance of having a person name is less than 0.25.
			//maxp is set to -1 if it cannot find any of the tokens in the gazette list
			boolean nonName = false;
			//works for only person names
			if (num == tokens.length) {
				nonName = true;
				if (maxp > 0.3)
					nonName = false;
			}

			if (maxp < 0.2 && maxp > 0)
				nonName = true;
			if (nonName) {
				fvs.add(new Triple<String, FeatureVector, Integer>(h, wfs.getVector(h), -1));
				log.warn("Adding: " + h + ", number-of-words-in-dictionary/total-number-of-words: (" + num + "/" + tokens.length + "), maximum chance of being a name: " + maxp);
				x++;
			}
		}
		log.info("Wrote dbpedia #" + numdbpedia + ", abNames #" + numC + ", to balance: " + x);

		File cdir = new File(CACHE_DIR);
		if (!cdir.exists())
			cdir.mkdir();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		FileWriter fw1 = null, fw2 = null;
		try {
			fw1 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_fvs.train"));
			fw2 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_names.train"));
		}catch(Exception e){
			log.warn(e);
		}
		svm_problem prob = new svm_problem();
		//svm_node of this line and target
		log.info("Number of feature vectors for training: " + fvs.size());
		prob.l = fvs.size();
		prob.x = new svm_node[prob.l][];
		prob.y = new double[prob.l];
		//for gamma computation.
		int max_dim = -1;
		int i = 0;
		for (Triple<String, FeatureVector, Integer> fv : fvs) {
			prob.x[i] = fv.second.getSVMNode();
			//target
			prob.y[i] = fv.third;
			if(fw1 != null && fw2 != null) {
				fw1.write(fv.third + " " + fv.second.toVector() + "\n");
				fw2.write(fv.first + "  " + fv.third + " " + fv.second + "\n");
			}
			max_dim = Math.max(max_dim, fv.second.NUM_DIM);
			i++;
		}
		svm_parameter param = FeatureDictionary.getDefaultSVMParam();
		param.gamma = 1.0 / max_dim;
		param.probability = 1;
		param.shrinking = 0;

		time += System.currentTimeMillis() - st;
		st = System.currentTimeMillis();
		status = "Learning "+iType+" model...";
		log.info("Training "+iType+" NER model");
		svm_print_interface svm_print_logger = new svm_print_interface() {
			public void print(String s)
			{
				NER.log.warn(s);
			}
		};
		svm.svm_set_print_string_function(svm_print_logger);

		svm_model svmModel = svm.svm_train(prob, param);
		try {
			svm.svm_save_model(modelFileName, svmModel);
			log.info("Trained and wrote NER model to: " + modelFileName);
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(wfsPath));
			oos.writeObject(wfs);
			oos.close();
			log.info("Number of wfs counts: " + wfs.counts.size());
			log.info("Wrote WordFeatures object to: " + wfsPath);
		} catch (Exception e) {
			log.info("Exception while writing WordFeatures object", e);
		}

		if(fw1 != null && fw2 != null) {
			fw1.close();
			fw2.close();
		}
		log.info("Wrote training file to : " + new File(CACHE_DIR + File.separator + iType + ".train").getAbsolutePath());
		status = "Done learning";
		return new Pair<svm_model, FeatureDictionary>(svmModel, wfs);
	}

	/**
	 * @returns map and offset, map with key the type of entity and offsets contain the string, start offset and end offset
	 * @throws IOException
	 * @throws CancelledException
	 * @throws LockObtainFailedException
	 * @throws CorruptIndexException
	 * @returns names recognised by the custom trained SVM model
	 */
	//type in the arguement is one of WordFeatures.{PERSON,ORGANIZATION and LOCATION}
	public Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>> getEntities(String content) throws CancelledException, IOException {
//		if (fdw == null) {
//			try {
//				fdw = new FileWriter(new File(archive.baseDir + File.separator + "cache" + File.separator + "features.dump"));
//			} catch (Exception e) {
//				;
//			}
//		}

		List<Triple<String, Integer, Integer>> names = new ArrayList<Triple<String, Integer, Integer>>();
		List<Double> scores = new ArrayList<Double>();
		long start_time = System.currentTimeMillis();
		String[] types = new String[] { FeatureDictionary.PERSON, FeatureDictionary.PLACE, FeatureDictionary.ORGANISATION };
		FeatureGenerator[] fgs = new FeatureGenerator[]{new WordFeature()};

		Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
		for (String type : types) {
			List<String> entities = new ArrayList<String>();
			Pair<svm_model, FeatureDictionary> preStuff = loadModel(type);
			svm_model svmModel = preStuff.first;
			FeatureDictionary wfs = preStuff.second;

			List<Triple<String, Integer, Integer>> candNames = Tokenizer.getNamesFromPattern(content, type.equals(FeatureDictionary.PERSON));
			for (Triple<String, Integer, Integer> cand : candNames) {

				String name = cand.first;
				String tc = FeatureGeneratorUtil.tokenFeature(name);
				if (name == null || tc.equals("ac"))
					continue;
				name = name.replaceAll("^\\W+|\\W+$", "");
				//trailing apostrophe
				//this could be a good signal for name(occasionally could also be org). The training data (Address book) doesn't contain such pattern, hence probably have to hard code it and I dont want to.
				name = name.replaceAll("'s$", "");
				//stuff b4 colon like subject:, from: ...
				name = name.replaceAll("\\w+:\\W+", "");
				name = name.replaceAll("^\\W+|\\W+$", "");

				FeatureVector wfv = wfs.getVector(name);
				svm_node[] sx = wfv.getSVMNode();
				double[] probs = new double[2];
				double v = svm.svm_predict_probability(svmModel, sx, probs);
				if (v > 0) {
					//clean before passing for annotation.
					name = name.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam)\\W+", "");
					if (DictUtils.tabooNames.contains(name.toLowerCase()) || DictUtils.hasOnlyCommonDictWords(name.toLowerCase())) {
						if(log.isDebugEnabled())
							log.debug("Skipping entity: " + name);
						//System.err.println("Skipping entity: " + name);
						continue;
					}

					if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) // drop it
						continue;

					names.add(cand);
					entities.add(name);
					scores.add(probs[0]);
					if(log.isDebugEnabled())
						log.debug("Found entity name: " + name + ", type: " + type);
				}
				//TODO: Should comment out these writes in the final version as the dump can get too big and also brings down the performance
//				if (fdw != null) {
//					try {
//						fdw.write("name:" + name + ", Type: " + type + ", pred: " + v + ", " + wfv + "\n");
//						fdw.flush();
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
//				}
			}
			map.put(type, entities);
		}
//		log.info("Recognised " + names.size() + " names from message of size: " + content.length() + " in " + (System.currentTimeMillis() - start_time) + "ms");
		return new Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>>(map, names);
	}

//	List<Triple<String, Integer, Integer>> parseAndGetOffsets(String text) throws CancelledException, IOException {
//		Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffset = getEntities(text);
//		return mapAndOffset.second;
//	}

	//arrange offsets such that the end offsets are in increasing order and if there are any overlapping offsets, the bigger of them should appear first
	//makes sure the redaction is proper.
	public static void arrangeOffsets(List<Triple<String,Integer,Integer>> offsets){
		Collections.sort(offsets, new Comparator<Triple<String, Integer, Integer>>() {
			@Override
			public int compare(Triple<String, Integer, Integer> t1, Triple<String, Integer, Integer> t2) {
				if (t1.getSecond() != t2.getSecond())
                    return t1.getSecond()-t2.getSecond();
                else
                    return t2.getThird()-t1.getThird();
			}
		});
		//return offsets;
	}

	public static String retainOnlyNames(String text, List<Triple<String, Integer, Integer>> offsets) {
		if (offsets == null) {
		    //mask the whole content
            offsets = new ArrayList<Triple<String,Integer,Integer>>();
            //Util.aggressiveWarn("Retain Only Names received null offset, returning", -1);
			//return text;
		}

		int len = text.length();
        offsets.add(new Triple<String, Integer, Integer>(null, len, len)); // sentinel
        StringBuilder result = new StringBuilder();
		int prev_name_end_pos = 0; // pos of first char after previous name

		//make sure the offsets are in order, i.e. the end offsets are in increasing order
		arrangeOffsets(offsets);
        for (Triple<String, Integer, Integer> t : offsets)
		{
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
		return JSONUtils.getStatusJSON(status, (int) pctComplete, time, eta);
	}

	@Override
	public void cancel() {
		cancelled = true;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	public static void main(String[] args) {
		try {
			String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
            userDir = "/Users/vihari/epadd-discovery/ePADD archive of Robert Creeley-Discovery/";
			edu.stanford.muse.webapp.ModeConfig.mode = edu.stanford.muse.webapp.ModeConfig.Mode.DISCOVERY;
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
	        Indexer li = archive.indexer;
//            System.err.println(li.directory);
            int i=0;
            Set<EmailDocument> docs = li.luceneLookupDocs("Jim");
            for(Document doc: docs) {
                System.err.println(li.getDoc(doc));
                if(i++>=0)
                    break;
            }
//            String str = "Subject: Hello this is me";
//            List<Triple<String,Integer, Integer>> offsets = null;
//            System.err.println(NER.retainOnlyNames(str, offsets));
//			for(org.apache.lucene.document.Document doc: li.getAttachmentDocs()) {
//                System.err.println(doc.get("fileName") + ", content: " + (doc.get("body") == null ? 0 : doc.get("body").length()));
//            }
//            Pair<String,String> p = li.getContentsOfAttachment("Resume Maria.doc");
//            System.err.println("Content: "+p.first+", status: "+p.second);
//			NER ner = new NER(archive);
////			for(String mt: ner.customModelTypes)
//			//ner.loadModel(FeatureDictionary.PERSON);
////			System.err.println("Loading models ...");
////			Pair<svm_model,FeatureDictionary> p = ner.loadModel(FeatureDictionary.PERSON);
////			System.err.println("Done loading models");
////			Set<Map<String,String>> gazettes = new HashSet<Map<String,String>>();
////			Map<String,String> dbpedia = EmailUtils.readDBpedia();
////			List<Contact> contacts = archive.addressBook.allContacts();
////			Map<String,String> abnames = EmailUtils.getNames(contacts);
////			gazettes.add(dbpedia);
////			gazettes.add(abnames);
////			double[] fs = SegmentFeatures.genarateFeatures("Vihari", p.second, p.first);
////			FeatureDictionary wfs = p.second;
//			//ner.trainAndRecognise();
//			String dIds[] = new String[] { "/home/dev/epadd-data/Bush 01 January 2003/Top of Outlook data file.mbox-896" };
//			Indexer li = (Indexer) archive.indexer;
//			//			FileWriter fw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "sandbox" + File.separator + "some.html"));
//			//
//			for (String dId : dIds) {
//				Document doc = (Document) li.docForId(dId);
//				String content = li.getContents(doc, false);
//				String rc = ner.retainOnlyNames(content);//, li.getDoc(doc));
//				//String rc = NER.retainOnlyNames(content, Tokenizer.getNamesFromPattern(content, false));
//				String[] rlines = rc.split("\\n");
//				String[] lines = content.split("\\n");
//				for(int i=0;i<rlines.length;i++)
//					System.err.println(rlines[i]+"\n"+lines[i]);
//
//				//System.err.println("redacted: " + rc);
//				//System.err.println("Original content: "+content);
////				for (Triple<String, Integer, Integer> t : Tokenizer.getNamesFromPattern(content, false))
////					System.err.println(t.first + ", " + content.substring(t.getSecond(), t.getThird()));
////					rc = rc.replaceAll("\\n", "\n<br>");
////							fw.write(rc + "<br><br>----------------------<br><br>");
////							fw.write(content.replaceAll("\\n", "\n<br>"));
//			}
			//			fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

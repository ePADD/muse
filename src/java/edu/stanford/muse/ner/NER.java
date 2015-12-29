package edu.stanford.muse.ner;

import edu.stanford.muse.Config;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.featuregen.*;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.SVMModel;
import edu.stanford.muse.ner.model.SequenceModel;
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
 * This is the only class dependent on ePADD in this package and has all the interfacing functionality
 *
 * TODO: trainAndrecognise and train methods should take proper options argument and there should be a class that represents training options*/
public class NER implements StatusProvider {
	public static Log		    log					= LogFactory.getLog(NER.class);
    //names and names_original should include all the names in the title
	public static String		EPER				= "en_person", ELOC = "en_loc", EORG = "en_org", NAMES_ORIGINAL = "en_names_original";
    public static String		EPER_TITLE			= "en_person_title", ELOC_TITLE = "en_loc_title", EORG_TITLE = "en_org_title";
    public static String		NAMES_OFFSETS		= "en_names_offsets", TITLE_NAMES_OFFSETS = "en_names_offsets_title";
    public static String        FINE_ENTITIES = "en_fine_entities", TITLE_FINE_ENTITIES = "en_fine_entities_title";

	String						status;
	double						pctComplete			= 0;
	boolean						cancelled			= false;
	Archive						archive				= null;
    SequenceModel               nerModel;
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
		public void update(Map<Short, Map<String,Double>> allTypes) {
            Map<Short, List<String>> mergedTypes = SequenceModel.mergeTypes(allTypes);
			for (Short type : allTypes.keySet()) {
                //dont add type stats twice
                if(mergedTypes.containsKey(type))
                    continue;
				if (!rcounts.containsKey(type))
					rcounts.put(type, 0);
				rcounts.put(type, rcounts.get(type) + allTypes.get(type).size());

				if (!all.containsKey(type))
					all.put(type, new HashSet<String>());
				if (allTypes.get(type) != null)
					for (String str : allTypes.get(type).keySet())
						all.get(type).add(str);

				counts.put(type, all.get(type).size());
			}

            for (Short type : mergedTypes.keySet()) {
                if (!rcounts.containsKey(type))
                    rcounts.put(type, 0);
                rcounts.put(type, rcounts.get(type) + mergedTypes.get(type).size());

                if (!all.containsKey(type))
                    all.put(type, new HashSet<String>());
                if (mergedTypes.get(type) != null)
                    for (String str : mergedTypes.get(type))
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
		public boolean addressbook = true, dbpedia = true, segmentation = true;
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

	public NER(Archive archive, SequenceModel nerModel) {
		this.archive = archive;
        this.nerModel = nerModel;
		time = 0;
		eta = 10 * 60;
		stats = new NERStats();
	}

	private static void storeSerialized(org.apache.lucene.document.Document doc, String fieldName, Object obj)
	{
        FieldType storeOnly_ft = new FieldType();
		storeOnly_ft.setStored(true);
		storeOnly_ft.freeze();
		try {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bs);
			oos.writeObject(obj);
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
        String fieldName;
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
			result = new ArrayList<>();
		}

		return result;
	}

    public static Map<Short,Map<String,Double>> getEntities(Document doc, boolean body, Archive archive) {
        try {
            org.apache.lucene.document.Document ldoc = archive.getDoc(doc);
            return getEntities(ldoc, body);
        } catch(IOException e){
            Util.print_exception("!!Exception while accessing named entities in the doc", e, log);
            return null;
        }
    }

    public static Map<Short,Map<String,Double>> getEntities(org.apache.lucene.document.Document doc, boolean body) {
        String fieldName;
        if(body)
            fieldName = FINE_ENTITIES;
        else
            fieldName = TITLE_FINE_ENTITIES;
        BytesRef bytesRef = doc.getBinaryValue(fieldName);
        if (bytesRef == null)
            return null;
        byte[] data = bytesRef.bytes;
        if (data == null)
            return null;

        ByteArrayInputStream bs = new ByteArrayInputStream(data);
        Map<Short,Map<String,Double>> result;
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(bs);
            result = (Map<Short,Map<String,Double>>) ois.readObject();
        } catch (Exception e) {
            log.warn("Failed to deserialize field: "+fieldName);
            e.printStackTrace();
            result = new LinkedHashMap<>();
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
        List<Short> types = Arrays.asList(
                //FeatureDictionary.PERSON,
                //FeatureDictionary.PLACE,
                FeatureDictionary.ORGANISATION);
        List<String[]> aTypes = Arrays.asList(
                FeatureDictionary.aTypes.get(FeatureDictionary.PERSON),
                FeatureDictionary.aTypes.get(FeatureDictionary.PLACE),
                FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION)
        );
        NERModel model = trainer.trainArchiveIndependent(dbpedia, types, fgs, tokenizer, params);
        return model;
    }

    public NERModel trainModel(boolean dumpModel) {
        NERTrainer trainer = new SVMModelTrainer();
        status = "Loading DBpedia and address book";
        Map<String,String> addressbook =  EmailUtils.getNames(archive.addressBook.allContacts());
        pctComplete = 2;
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        pctComplete = 10;
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
        SVMModelTrainer.TrainingParam tparams = SVMModelTrainer.TrainingParam.initialize(CACHE_DIR, MODEL_DIR, dumpModel);
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

    /**
     * This method is used to get entities in unoriginal content in a message, for example the quoted text.
     * We process all the email content in a thread at once, to reduce the computation.
     * @arg content - text to be processed for entities
     * @arg eMap - a super set of all the possible entities that can appear in content
     * @return entities found in the content in the structure used by NERModel interface
     */
    public static Pair<Map<Short, Map<String, Double>>, List<Triple<String, Integer, Integer>>> getEntitiesInDoc(String content, Map<Short,Map<String,Double>> eMap){
        CICTokenizer tokenizer = SequenceModel.tokenizer;
        List<Triple<String,Integer,Integer>> offsets = tokenizer.tokenize(content, false), offDoc = new ArrayList<>();
        Map<Short,Map<String,Double>> eDoc = new LinkedHashMap<>();
        Map<String,Short> entities = new LinkedHashMap<>();
        if(eMap == null)
            return new Pair<>(eDoc, offDoc);

        for(Short t: eMap.keySet())
            for(String e: eMap.get(t).keySet())
                entities.put(e,t);

        for(Triple<String,Integer,Integer> off: offsets){
            String bestMatch = null;
            int bl = -1;
            for(String e: entities.keySet()) {
                if (e.length()>bl && off.getFirst().contains(e)) {
                    bestMatch = e;
                    bl = e.length();
                }
            }
            if(bestMatch == null || bl == -1)
                continue;
            Short type = entities.get(bestMatch);
            if(!eDoc.containsKey(type))
                eDoc.put(type, new LinkedHashMap<String, Double>());

            off.second = off.second+off.first.indexOf(bestMatch);
            off.third = off.second+bestMatch.length();
            off.first = bestMatch;
            eDoc.get(type).put(bestMatch,eMap.get(type).get(bestMatch));
            offDoc.add(off);
        }
        return new Pair<>(eDoc, offDoc);
    }

	//TODO: Consider using Atomic reader for accessing the index, if it improves performance
	//main method trains the model, recognizes the entities and updates the doc.
	public void recongniseArchive() throws CancelledException, IOException {
		time = 0;
		archive.openForRead();
		archive.setupForWrite();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

        int maxThrdId = archive.assignThreadIds();
        //List<Document> docs = archive.getAllDocs();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		int di = 0, ds = archive.getAllDocs().size();
		int ps = 0, ls = 0, os = 0;

		long totalTime = 0, updateTime = 0, recTime = 0, duTime = 0, snoTime = 0;
		for(int tid = 0;tid<maxThrdId;tid++) {
            Collection<Document> thread = archive.docsWithThreadId(tid);
            //the title will be the same for all the docs in the thread, should not invoke NER on them individually
            String title = null;
            Map<Short, Map<String, Double>> allE = new LinkedHashMap<>();
            for (Document doc : thread) {
                EmailDocument ed = (EmailDocument) doc;
                org.apache.lucene.document.Document ldoc = archive.getDoc(doc);
                if (title != null)
                    title = archive.getTitle(ldoc);
                String content = archive.getContents(ldoc, true);
                Pair<Map<Short, Map<String, Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = nerModel.find(content);
                Map<Short, Map<String, Double>> entities = mapAndOffsets.first;
                for (Short type : entities.keySet()) {
                    if (allE.containsKey(type))
                        allE.put(type, new LinkedHashMap<String, Double>());
                    allE.get(type).putAll(entities.get(type));
                }
            }

            Pair<Map<Short, Map<String, Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsetsTitle = nerModel.find(title);
            Map<Short, List<String>> mapTitle = SequenceModel.mergeTypes(mapAndOffsetsTitle.first);
            //now iterate over the docs in the thread and update them
            for (Document doc : thread) {
                long st1 = System.currentTimeMillis();
                long st = System.currentTimeMillis();
                org.apache.lucene.document.Document ldoc = archive.getDoc(doc);
                //pass the lucene doc instead of muse doc, else a major performance penalty
                //do not recognise names in original content and content separately
                String originalContent = archive.getContents(ldoc, true);
                String content = archive.getContents(ldoc, false);
                //original content is substring of content;
                Pair<Map<Short, Map<String, Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = getEntitiesInDoc(content, allE);
                recTime += System.currentTimeMillis() - st;
                st = System.currentTimeMillis();

                Map<Short, List<String>> map = SequenceModel.mergeTypes(mapAndOffsets.first);
                stats.update(mapAndOffsets.first);
                stats.update(mapAndOffsetsTitle.first);
                updateTime += System.currentTimeMillis() - st;
                st = System.currentTimeMillis();

                //!!!!!!SEVERE!!!!!!!!!!
                //TODO: an entity name is stored in NAMES, NAMES_ORIGINAL, nameoffsets, and one or more of EPER, ELOC, EORG fields, that is a lot of redundancy
                //!!!!!!SEVERE!!!!!!!!!!
                storeSerialized(ldoc, NAMES_OFFSETS, mapAndOffsets.second);
                storeSerialized(ldoc, TITLE_NAMES_OFFSETS, mapAndOffsetsTitle.second);
                storeSerialized(ldoc, FINE_ENTITIES, mapAndOffsets.getFirst());
                storeSerialized(ldoc, TITLE_FINE_ENTITIES, mapAndOffsets.getSecond());

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

                ldoc.removeField(EPER);
                ldoc.removeField(EPER_TITLE);
                ldoc.removeField(ELOC);
                ldoc.removeField(ELOC_TITLE);
                ldoc.removeField(EORG);
                ldoc.removeField(EORG_TITLE);

                ldoc.add(new StoredField(EPER, Util.join(persons, Indexer.NAMES_FIELD_DELIMITER)));
                ldoc.add(new StoredField(ELOC, Util.join(locs, Indexer.NAMES_FIELD_DELIMITER)));
                ldoc.add(new StoredField(EORG, Util.join(orgs, Indexer.NAMES_FIELD_DELIMITER)));
                ldoc.add(new StoredField(EPER_TITLE, Util.join(personsTitle, Indexer.NAMES_FIELD_DELIMITER)));
                ldoc.add(new StoredField(ELOC_TITLE, Util.join(locsTitle, Indexer.NAMES_FIELD_DELIMITER)));
                ldoc.add(new StoredField(EORG_TITLE, Util.join(orgsTitle, Indexer.NAMES_FIELD_DELIMITER)));

                List<String> names_original = new ArrayList<String>(), names = new ArrayList<String>();
                if (persons != null)
                    names.addAll(persons);
                if (locs != null)
                    names.addAll(locs);
                if (orgs != null)
                    names.addAll(orgs);
                int ocs = originalContent.length();
                List<Triple<String, Integer, Integer>> offsets = mapAndOffsets.getSecond();
                for (int oi = 0; oi < offsets.size(); oi++) {
                    Triple<String, Integer, Integer> offset = offsets.get(oi);
                    String name = offset.getFirst();
                    if (offset == null) {
                        log.warn("No offset found for: " + name);
                        break;
                    }
                    if (offset.getSecond() < ocs)
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
                pctComplete = 30 + ((double) di / (double) ds) * 70;
                double ems = (double) (totalTime * (ds - di)) / (double) (di * 1000);
                status = "Recognized entities in " + Util.commatize(di) + " of " + Util.commatize(ds) + " emails ";
                //Util.approximateTimeLeft((long)ems/1000);
                eta = (long) ems;

                if (di % 100 == 0)
                    log.info(status);
                time += System.currentTimeMillis() - st;

                if (cancelled) {
                    status = "Cancelling...";
                    throw new CancelledException();
                }
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

//	public static void main1(String[] args) {
//		try {
//			String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
//            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
//            NER ner = new NER(archive, null);
//            System.err.println("Loading model...");
//            long start = System.currentTimeMillis();
//            NERModel model = ner.trainModel(false);
//            System.err.println("Trained model in: " + (System.currentTimeMillis() - start));
//            System.err.println("Done loading model");
//            String[] pers = new String[]{"Senator Jim Scott", "Rep. Bill Andrews"};
//            String[] locs = new String[]{"Florida", "Plantation"};
//            String[] orgs = new String[]{"Broward Republican Executive Committee", "National Education Association"};
//            String text = "First I would like to tell you who I am. I am a lifelong Republican and have served on the Broward Republican Executive Committee since 1991. I have followed education issues in Florida since I moved here in 1973. All four of my children went to public schools here in Plantation. I continued to study education issues when I worked for Senator Jim Scott for six years, and more recently as I worked for Rep. Bill Andrews for the past eight years.\n" +
//                    "On the amendment, I would like to join any effort to get it repealed. Second, if the amendment is going to be implemented, I believe that decisions about how money is spent should be taken out of the hands of the school boards. I know the trend has been to provide more local control, however, there has been little or no accountability for school boards that fritter away money on consultants, shoddy construction work, and promoting the agenda of the National Education Association and the local teachers’ unions. Third, while the teachers’ union is publicly making “nice” with you and other Republican legislators, they continue to undermine education reform measures, and because school board members rely heavily on the unions to get elected and re-elected, they pretty much call the shots on local policies. ";
//            Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> ret = model.find(text);
//            boolean testPass = true;
//            for(Short type: ret.getFirst().keySet()) {
//                System.err.print("Type: " + type);
//                for (String str : ret.getFirst().get(type))
//                    System.err.print(":::" + str + ":::");
//                System.err.println();
//            }
//        } catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

    public static void main(String[] args) {
        try {
            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user-creeley";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
//            String modelFile = archive.baseDir + File.separator + "models" + File.separator + SVMModel.modelFileName;
            String cacheDir = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"cache";
            String mwl = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator;
            SVMModelTrainer.TrainingParam tparam = SVMModelTrainer.TrainingParam.initialize(cacheDir, mwl, true);
            String modelFile = mwl + SVMModel.modelFileName;
            NERModel nerModel = SVMModel.loadModel(new File(modelFile));
            //NERModel nerModel = trainArchiveIndependentModel(tparam);
            String content = "\n" +
                    "\n" +
                    "    Import\n" +
                    "    Browse\n" +
                    "    Search\n" +
                    "    Export\n" +
                    "\n" +
                    "Robert Creeley (May 21, 1926 â\u0080\u0093 March 30, 2005) was an American poet and author of more than sixty books. He is usually associated with the Black Mountain poets, though his verse aesthetic diverged from that school's. He was close with Charles Olson, Robert Duncan, Allen Ginsberg, John Wieners and Ed Dorn. He served as the Samuel P. Capen Professor of Poetry and the Humanities at State University of New York at Buffalo. In 1991, he joined colleagues Susan Howe, Charles Bernstein, Raymond Federman, Robert Bertholf, and Dennis Tedlock in founding the Poetics Program at Buffalo. Creeley lived in Waldoboro, Maine, Buffalo, New York, and Providence, Rhode Island, where he taught at Brown University. He was a recipient of the Lannan Foundation Lifetime Achievement Award.\n" +
                    "\n" +
                    "Contents\n" +
                    "\n" +
                    "1 Life\n" +
                    "2 Work\n" +
                    "3 Bibliography\n" +
                    "4 Film Appearances\n" +
                    "5 Notes\n" +
                    "6 Research resources\n" +
                    "7 External links\n" +
                    "\n" +
                    "Life\n" +
                    "\n" +
                    "Creeley was born in Arlington, Massachusetts and grew up in Acton. He was raised by his mother with one sister, Helen. At the age of four, he lost his left eye. He attended the Holderness School in New Hampshire. He entered Harvard University in 1943, but left to serve in the American Field Service in Burma and India in 1944â\u0080\u00931945. He returned to Harvard in 1946, but eventually took his BA from Black Mountain College in 1955, teaching some courses there as well. When Black Mountain closed in 1957, Creeley moved to San Francisco, where he met Jack Kerouac and Allen Ginsberg. He later met and befriended Jackson Pollock in the Cedar Tavern in New York City.\n" +
                    "\n" +
                    "\"In a quiet moment I hear Bob pause where I never would have expected it. Such resolve. Such heart. And an ear to reckon with. No truly further American poem without his.\"\n" +
                    "\n" +
                    "Clark Coolidge[1]\n" +
                    "\n" +
                    "He was a chicken farmer briefly before becoming a teacher. The farm was in Littleton, New Hampshire. He was 23. The story goes that he wrote to Cid Corman whose radio show he heard on the farm, and Corman had him read on the show, which is how Charles Olson first heard of Creeley.[2]\n" +
                    "\n" +
                    "From 1951 to 1955, Creeley and his wife, Ann, lived with their three children on the Spanish island of Mallorca. They went there at the encouragement of their friends, British writer Martin Seymour-Smith and his wife, Janet. There they started Divers Press and published works by Paul Blackburn, Robert Duncan, Charles Olson, and others. Creeley wrote about half of his published prose while living on the island, including a short-story collection, The Gold Diggers, and a novel, The Island. He said that Martin and Janet Seymour-Smith are represented by Artie and Marge in the novel.[3] During 1954 and 1955, Creeley traveled back and forth between Mallorca and his teaching position at Black Mountain College. He also saw to the printing of some issues of Origin and Black Mountain Review on Mallorca, because the printing costs were significantly lower there.\n" +
                    "\n" +
                    "An MA from the University of New Mexico followed in 1960. He began his academic career by teaching at the prestigious Albuquerque Academy starting in around 1958 until about 1960 or 1961. In 1957, he met Bobbie Louise Hawkins; they lived together, common law marriage, until 1975. They had two children, Sarah and Katherine. He dedicated his book For Love to Bobbie. Creeley read at the 1963 Vancouver Poetry Festival and at the 1965 Berkeley Poetry Conference.[4] Afterward, he wandered about a bit before settling into the English faculty of â\u0080\u009CBlack Mountain IIâ\u0080\u009D at the University at Buffalo in 1967. He would stay at this post until 2003, when he received a post at Brown University. From 1990 to 2003, he lived with his family in Black Rock, in a converted firehouse at the corner of Amherst and East Streets. At the time of his death, he was in residence with the Lannan Foundation in Marfa, Texas.\n" +
                    "Robert Creeley and Allan Graham, during the taping of \"Add-Verse\", 2004, photo by Gloria Graham\n" +
                    "\n" +
                    "Creeley first received fame in 1962 from his poetry collection For Love. He would go on to win the Bollingen Prize, among others, and to hold the position of New York State Poet Laureate from 1989 until 1991.[5] He was elected a Fellow of the American Academy of Arts and Sciences in 2003.[6]\n" +
                    "\n" +
                    "In 1968, he signed the â\u0080\u009CWriters and Editors War Tax Protestâ\u0080\u009D pledge, vowing to refuse tax payments in protest against the Vietnam War.[7]\n" +
                    "\n" +
                    "In his later years he was an advocate of, and a mentor to, many younger poets, as well as to others outside of the poetry world. He went to great lengths to be supportive to many people regardless of any poetic affiliation. Being responsive appeared to be essential to his personal ethics, and he seemed to take this responsibility extremely seriously, in both his life and his craft. In his later years, when he became well-known, he would go to lengths to make strangers, who approached him as a well-known author, feel comfortable. In his last years, he used the Internet to keep in touch with many younger poets and friends.\n" +
                    "\n" +
                    "Robert Creeley died at sunrise on March 30, 2005, in Odessa, Texas of complications from pneumonia. His death resulted in an outpouring of grief and appreciation from many in the poetry world. He is buried in Cambridge, Massachusetts.\n" +
                    "Work\n" +
                    "\n" +
                    "According to Arthur L. Ford in his book Robert Creeley (1978, p. 25), \"Creeley has long been aware that he is part of a definable tradition in the American poetry of this century, so long as 'tradition' is thought of in general terms and so long as it recognizes crucial distinctions among its members. The tradition most visible to the general public has been the Eliot-Stevens tradition supported by the intellectual probings of the New Critics in the 1940s and early 1950s. Parallel to that tradition has been the tradition Creeley identifies with, the Pound-Olson-Zukofsky-Black Mountain tradition, what M. L. Rosenthal [in his book The New Poets: American and British Poetry Since World War II (1967)] calls 'The Projectivist Movement'.\" This \"movement\" Rosenthal derives from Olson's essay on \"Projective Verse\".\n" +
                    "\n" +
                    "Le Fou, Creeley's first book, was published in 1952, and since then, according to his publisher, barely a year passed without a new collection of poems. The 1983 entry, titled Mirrors, had some tendencies toward concrete imagery. It was hard for many readers and critics to immediately understand Creeley's reputation as an innovative poet, for his innovations were often very subtle; even harder for some to imagine that his work lived up to the Black Mountain tenetâ\u0080\u0094which he articulated to Charles Olson in their correspondence, and which Olson popularized in his essay \"Projective Verse,\"â\u0080\u0094that \"form is never more than an extension of content,\" for his poems were often written in couplet, triplet, and quatrain stanzas that break into and out of rhyme as happenstance appears to dictate. An example is \"The Hero,\" from Collected Poems, also published in 1982 and covering the span of years from 1945 to 1975.\n" +
                    "\n" +
                    "\"The Hero\" is written in variable isoverbal (\"word-count\") prosody; the number of words per line varies from three to seven, but the norm is four to six. Another technique to be found in this piece is variable rhymeâ\u0080\u0094there is no set rhyme scheme, but some of the lines rhyme and the poem concludes with a rhymed couplet. All of the stanzas are quatrains, as in the first two:\n" +
                    "\n" +
                    "THE HERO\n" +
                    "\n" +
                    "Each voice which was asked\n" +
                    "spoke its words, and heard\n" +
                    "more than that, the fair question,\n" +
                    "the onerous burden of the asking.\n" +
                    "And so the hero, the\n" +
                    "hero! stepped that gracefully\n" +
                    "into his redemption, losing\n" +
                    "or gaining life thereby.\n" +
                    "\n" +
                    "Despite these obviously formal elements various critics continue to insist that Creeley wrote in \"free verse\", but most of his forms were strict enough so that it is a question whether it can even be maintained that he wrote in forms of prose. This particular poem is without doubt verse-mode, not prose-mode. M. L. Rosenthal in his The New Poets quoted Creeley's \"'preoccupation with a personal rhythm in the sense that the discovery of an external equivalent of the speaking self is felt to be the true object of poetry,'\" and went on to say that this speaking self serves both as the center of the poem's universe and the private life of the poet. \"Despite his mask of humble, confused comedian, loving and lovable, he therefore stands in his own work's way, too seldom letting his poems free themselves of his blocking presence\" (p. 148). When he used imagery, Creeley could be interesting and effective on the sensory level.\n" +
                    "\n" +
                    "In an essay titled \"Poetry: Schools of Dissidents,\" the academic poet Daniel Hoffman wrote, in The Harvard Guide to Contemporary American Writing which he edited, that as he grew older, Creeley's work tended to become increasingly fragmentary in nature, even the titles subsequent to For Love: Poems 1950-1960 hinting at the fragmentation of experience in Creeley's work: Words, Pieces, A Day Book. In Hoffman's opinion, \"Creeley has never included ideas, or commitments to social issues, in the repertoire of his work; his stripped-down poems have been, as it were, a proving of Pound's belief in 'technique as the test of a man's sincerity.'\" (p. 533)\n" +
                    "\n" +
                    "Jazz bassist Steve Swallow released the albums Home (ECM, 1979) and So There (ECM, 2005) featuring poems by Creeley put to music.\n" +
                    "\n" +
                    "Early work by Creeley appeared in the avant garde little magazine Nomad at the beginning of the 1960s. Posthumous publications of Creeley's work have included the second volume of his Collected Poems, which was published in 2006, and The Selected Letters of Robert Creeley edited by Rod Smith, Kaplan Harris and Peter Baker, published in 2014 by the University of California Press.\n" +
                    "\n";

//            content = "Faculty and Graduate Students";
//            Pair<Map<Short,List<String>>,List<Triple<String,Integer,Integer>>> mapAndOffsets = nerModel.find(content);
//            Map<Short, List<String>> map = mapAndOffsets.getFirst();
//
//            for(Short k: map.keySet()){
//                List<String> names = map.get(k);
//                for(String n: names){
//                    System.err.println("<" +k+":"+n+">");
//                }
//            }
//            List<Document> docs = archive.getAllDocs();
//            FileWriter fw = new FileWriter(cacheDir+File.separator+"orgs.txt");
//            for(Document doc: docs){
//                String dc = archive.getContents(doc, true);
//                mapAndOffsets = nerModel.find(dc);
//                map = mapAndOffsets.getFirst();
//                fw.write(doc.getUniqueId());
//                for(Short k: map.keySet()){
//                    List<String> names = map.get(k);
//                    for(String n: names){
//                        fw.write(n + "\n");
//                    }
//                }
//            }
            //fw.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

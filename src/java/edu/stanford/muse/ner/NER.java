package edu.stanford.muse.ner;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.featuregen.*;
import edu.stanford.muse.ner.model.SequenceModel;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.*;
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
    NERModel               nerModel;
	//in seconds
	long						time				= -1, eta = -1;
	static FieldType			ft;
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
					all.put(type, new HashSet<>());
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
                    all.put(type, new HashSet<>());
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

	public NERStats	stats;

	static {
		ft = new FieldType();
		ft.setStored(true);
		ft.setIndexed(true);
		ft.freeze();
	}

	public NER(Archive archive, NERModel nerModel) {
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

    /**
     * This method should be used to get entities in unoriginal content in a message, for example the quoted text.
     * We process all the email content in a thread at once, to reduce the computation.
     * @arg content - text to be processed for entities
     * @arg eMap - a super set of all the possible entities that can appear in content
     * @return entities found in the content in the structure used by NERModel interface
     */
    public static Pair<Map<Short, Map<String, Double>>, List<Triple<String, Integer, Integer>>> getEntitiesInDoc(String content, Map<Short,Map<String,Double>> eMap){
        CICTokenizer tokenizer = SequenceModel.tokenizer;
        List<Triple<String,Integer,Integer>> offsets = tokenizer.tokenize(content), offDoc = new ArrayList<>();
        Map<Short,Map<String,Double>> eDoc = new LinkedHashMap<>();
        Map<String,Short> entities = new LinkedHashMap<>();
        if(eMap == null)
            return new Pair<>(eDoc, offDoc);

        for(Short t: eMap.keySet())
            for(String e: eMap.get(t).keySet())
                entities.put(e,t);

        for(Short type: FeatureDictionary.allTypes)
            eDoc.put(type, new LinkedHashMap<>());

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

            off.second = off.second+off.first.indexOf(bestMatch);
            off.third = off.second+bestMatch.length();
            off.first = bestMatch;
            eDoc.get(type).put(bestMatch,eMap.get(type).get(bestMatch));
            offDoc.add(off);
        }
        return new Pair<>(eDoc, offDoc);
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

    //main method trains the model, recognizes the entities and updates the doc.
	public void recognizeArchive() throws CancelledException, IOException {
		time = 0;
		archive.openForRead();
		archive.setupForWrite();

		if (cancelled) {
			status = "Cancelling...";
			throw new CancelledException();
		}

		List<Document> docs = archive.getAllDocs();

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
         	String originalContent = archive.getContents(ldoc, true);
			String content = archive.getContents(ldoc, false);
            String title = archive.getTitle(ldoc);
			//original content is substring of content;
            Pair<Map<Short, Map<String,Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = nerModel.find(content);
            Pair<Map<Short, Map<String,Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsetsTitle = nerModel.find(title);
			recTime += System.currentTimeMillis() - st;
			st = System.currentTimeMillis();

			Map<Short, List<String>> map = SequenceModel.mergeTypes(mapAndOffsets.first);
            Map<Short, List<String>> mapTitle = SequenceModel.mergeTypes(mapAndOffsetsTitle.first);
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

		    ldoc.removeField(EPER);	ldoc.removeField(EPER_TITLE);
            ldoc.removeField(ELOC); ldoc.removeField(ELOC_TITLE);
            ldoc.removeField(EORG); ldoc.removeField(EORG_TITLE);

			ldoc.add(new StoredField(EPER, Util.join(persons, Indexer.NAMES_FIELD_DELIMITER)));
			ldoc.add(new StoredField(ELOC, Util.join(locs, Indexer.NAMES_FIELD_DELIMITER)));
			ldoc.add(new StoredField(EORG, Util.join(orgs, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EPER_TITLE, Util.join(personsTitle, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(ELOC_TITLE, Util.join(locsTitle, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EORG_TITLE, Util.join(orgsTitle, Indexer.NAMES_FIELD_DELIMITER)));

			List<String> names_original = new ArrayList<>(), names = new ArrayList<>();
            names.addAll(persons);
            names.addAll(locs);
            names.addAll(orgs);
			int ocs = originalContent.length();
			List<Triple<String,Integer,Integer>> offsets = mapAndOffsets.getSecond();
            for (Triple<String, Integer, Integer> offset : offsets) {
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
		Collections.sort(offsets, (t1, t2) -> {
            if (t1.getSecond() != t2.getSecond())
                return t1.getSecond()-t2.getSecond();
            else
                return t2.getThird()-t1.getThird();
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
}

package edu.stanford.muse.ie;

import edu.stanford.muse.Config;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains pre-processing computations for assign-authorities.jsp
 * and entity resolutions in the browse page (with expandname.jsp).
 * Map<String, EntityFeature> features; This is the crucial object that contains
 * the co-occurring entities and (co-)occurring email addresses. It can consume
 * lot of space if not capped properly.
 * 
 * Canceling the process may leave the object in inconsistent state, please
 * check ifcancelled before using the object
 * 
 * This should be the flow to use this class:
 * 1. get instance from load(Archive), feed it to statusprovider.
 * 2. Irrespective of whether the class is already initialized, call
 * initialize(Archive)
 * 3. call checkFeaturesIndex(Archive) finally
 * 4. Call isCancelled to check if the object is properly initiated,
 *
 * The size of this features index created by this class generally as big as emails index,
 * that is odd, TODO: try to bring down the size of the features index
 */
public class InternalAuthorityAssigner implements StatusProvider, Serializable {
	private static final long		serialVersionUID	= 1L;

	private static Log				log					= LogFactory.getLog(InternalAuthorityAssigner.class);

	public Map<Short, Entities>		entitiesData		= new HashMap<>();

	// frequency of a contact. ContactId->frequency
	public Map<Integer, Integer>	contactFreq;

	//contains status at any moment.
	String							status;
	double							pctComplete			= 0.0;
	//control is set to false, when the entityfeature is doing the job and this class does not have the control.
	EntityFeature					control				= null;
	//set cancel to true if wish to cancel the op, cancel will be checked or used in during repetitive or heavy tasks 
	boolean							cancel				= false;

	public static InternalAuthorityAssigner load(Archive archive) {
		InternalAuthorityAssigner aa;
		String AUTHORITY_ASSIGNER_FILE = archive.baseDir + File.separator + Config.AUTHORITY_ASSIGNER_FILENAME;
		if (new File(AUTHORITY_ASSIGNER_FILE).exists()) {
			log.info("Reading InternalAuthorityAssigner object from file" + AUTHORITY_ASSIGNER_FILE);

			ObjectInputStream ois;
			try {
				ois = new ObjectInputStream(new FileInputStream(AUTHORITY_ASSIGNER_FILE));
				aa = (InternalAuthorityAssigner) ois.readObject();
				if (ois != null)
					ois.close();
				String stats = "";
				stats += "#People: " + aa.entitiesData.get(EntityFeature.PERSON).pairs.size() + "\n";
				stats += "#Correspondents: " + aa.entitiesData.get(EntityFeature.CORRESPONDENT).pairs.size() + "\n";
				stats += "#Places: " + aa.entitiesData.get(EntityFeature.PLACE).pairs.size() + "\n";
				stats += "#Orgs: " + aa.entitiesData.get(EntityFeature.ORG).pairs.size();
				log.info("Loaded entities from file: " + AUTHORITY_ASSIGNER_FILE + "\n" + stats);
				return aa;
			} catch (Exception e) {
				e.printStackTrace();
				log.warn("Exception while reading: " + AUTHORITY_ASSIGNER_FILE + " file, constructing the object...", e);
				return null;
			}
		} else {
			log.warn("!!!!!No " + AUTHORITY_ASSIGNER_FILE + " file found, returning null object...!!!!");
			return null;
		}
	}

	/**
	 * Need to have this empty constructor so that status will have an object to
	 * poll for
	 */
	public InternalAuthorityAssigner() {
	}

	/** Initializes the object */
	public void initialize(Archive archive) {
		contactFreq = new HashMap<>();
		Collection<EmailDocument> docs = (Collection) archive.getAllDocs();
		long start_time = System.currentTimeMillis();
		AddressBook ab = archive.addressBook;

		short[] types = new short[] { EntityFeature.PERSON, EntityFeature.CORRESPONDENT, EntityFeature.ORG, EntityFeature.PLACE };
		for (short t : types) {
			if (!entitiesData.containsKey(t))
				entitiesData.put(t, new Entities());
		}

		if (cancel)
			return;

		Set<String> acronyms = new HashSet<>();

		/*
		 * There are two iterations on all docs, because the entities and their
		 * expansions can occur in any order, first collect all acronyms and
		 * then
		 * analyze every entity that comes in the way.
		 */
		//get all acronyms
		int di = 0;
        Tokenizer tokenizer = new CICTokenizer();
		for (EmailDocument ed : docs) {
			List<String> addrs = ed.getAllAddrs();
			for (String addr : addrs) {
				Contact c = ab.lookupByEmail(addr);
				if (c != null) {
					int cid = ab.getContactId(c);
					if (!contactFreq.containsKey(cid))
						contactFreq.put(cid, 0);
					contactFreq.put(cid, contactFreq.get(cid) + 1);
				}
			}
			String id = ed.getUniqueId();
			String content = archive.getContents(ed, false);
			try {
				//TODO: trying to get acronyms this way is a hack and inefficient
				//Initialise a special reg exp for this task
				Set<String> pnames = tokenizer.tokenizeWithoutOffsets(content);
				if (pnames != null)
					for (String name : pnames) {
						String tc = FeatureGeneratorUtil.tokenFeature(name);
						//we want only all capital letters
						if (tc.equals("ac") && name.length() > 2)
							acronyms.add(name);
					}
			} catch (Exception e) {
				e.printStackTrace();
			}

			status = "Collected acronyms and contacts in " + di + "/" + docs.size();
			pctComplete = ((double) di * 50) / docs.size();
			di++;
			if (cancel)
				return;
		}

		List<Contact> contacts = ab.allContacts();
		for (int i = 0; i < contacts.size(); i++) {
			//Set<String> names = contacts.get(i).names;
			int cid = ab.getContactId(contacts.get(i));
			Entities d = entitiesData.get(EntityFeature.CORRESPONDENT);

			if (!contactFreq.containsKey(cid)) {
				//These contacts generally doesn't have any email addresses associated with them. 
				System.err.println("Contact freq doesn't contain the contact: " + contacts.get(i).emails);
				continue;
			}

			int freq = contactFreq.get(cid);
			String dn = contacts.get(i).pickBestName();
			if (dn == null)
				continue;
			dn = dn.replaceAll("^\\s+|\\s+$", "");
			if (dn.contains(" ")) {
				d.pairs.add(new Pair<String, Integer>(dn, freq));

				String canonicalEntity = IndexUtils.canonicalizeEntity(dn);
				if (canonicalEntity == null)
					continue;
				if (d.canonicalToOriginal.get(canonicalEntity) == null)
					d.canonicalToOriginal.put(canonicalEntity, dn);

				d.counts.put(canonicalEntity, freq);
			}
			if (cancel)
				return;
		}

		di = 0;
		for (EmailDocument ed : docs) {
            List<String> people = Arrays.asList(NER.getCoarseEntities(ed, FeatureDictionary.PERSON, true, archive)).stream().map(s->s.text).collect(Collectors.toList()),
                    places = Arrays.asList(NER.getCoarseEntities(ed, FeatureDictionary.PLACE, true, archive)).stream().map(s->s.text).collect(Collectors.toList()),
                    orgs = Arrays.asList(NER.getCoarseEntities(ed, FeatureDictionary.ORGANISATION, true, archive)).stream().map(s->s.text).collect(Collectors.toList());

			// note that entities could have repetitions.
			// so we create a *set* of entities, but after canonicalization.
			// canonical to original just uses an arbitrary (first) occurrence
			// of the entity
			List<List<String>> allEntities = Arrays.asList(people, orgs, places);
			short[] allTypes = new short[]{EntityFeature.PERSON, EntityFeature.ORG, EntityFeature.PLACE};
			for(int ei=0;ei<allEntities.size();ei++) {
				List<String> entities = allEntities.get(ei);
				Short et = allTypes[ei];
				for (String e : entities) {
					if (e != null && (et != EntityFeature.PERSON ||e.contains(" "))) {
						Entities d = entitiesData.get(et);
						String canonicalEntity = IndexUtils.canonicalizeEntity(e);
						if (canonicalEntity == null)
							continue;
						if (d.canonicalToOriginal.get(canonicalEntity) == null)
							d.canonicalToOriginal.put(canonicalEntity, e);

						Integer I = d.counts.get(canonicalEntity);
						d.counts.put(canonicalEntity, (I == null) ? 1 : I + 1);
					}
				}
			}

			status = "Collected entity stats in " + (di) + "/" + docs.size() + " emails";
			pctComplete = 50 + (((double) di * 50) / (double) docs.size());
			if (cancel)
				return;
			di++;
		}

		log.info("Time taken to iterate over emails to pre-process entities: " + (System.currentTimeMillis() - start_time));

		// ok, so counts now has the c-entity -> counts map, sort it by
		// descending count
		start_time = System.currentTimeMillis();
		for (Entities d : entitiesData.values())
			d.pairs = edu.stanford.muse.util.Util.sortMapByValue(d.counts);

		log.info("Time taken for sorting entities: " + (System.currentTimeMillis() - start_time));

		String AUTHORITY_ASSIGNER_FILE = archive.baseDir + File.separator + Config.AUTHORITY_ASSIGNER_FILENAME;
		log.info("Writing InternalAuthorityAssigner object to file" + AUTHORITY_ASSIGNER_FILE);
		ObjectOutputStream oos ;
		try {
			String stats = "";
			stats += "#People: " + this.entitiesData.get(EntityFeature.PERSON).pairs.size() + "\n";
			stats += "#Correspondents: " + this.entitiesData.get(EntityFeature.CORRESPONDENT).pairs.size() + "\n";
			stats += "#Places: " + this.entitiesData.get(EntityFeature.PLACE).pairs.size() + "\n";
			stats += "#Orgs: " + this.entitiesData.get(EntityFeature.ORG).pairs.size();
			log.info("Writing entities from file: " + AUTHORITY_ASSIGNER_FILE + "\n" + stats);
			oos = new ObjectOutputStream(new FileOutputStream(AUTHORITY_ASSIGNER_FILE));
			oos.writeObject(this);
			oos.close();
		} catch (Exception e) {
			e.printStackTrace();
			log.info("Exception while writing this object to: " + AUTHORITY_ASSIGNER_FILE + " file, neglecting the error and continuing");
		}
	}

	/** force- force creation of new index */
	public boolean checkFeaturesIndex(Archive archive, boolean force) {
		control = new EntityFeature();
		//check features index.
		boolean ret = control.checkIndex(archive, force);
		control = null;
		return ret;
	}

	@Override
	public String getStatusMessage() {
		if (control == null)
			return JSONUtils.getStatusJSON(status, (int) pctComplete, 0, 0);
		else
			return control.getStatusMessage();
	}

	@Override
	public void cancel() {
		if (control != null)
			control.cancel();
		cancel = true;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}
}

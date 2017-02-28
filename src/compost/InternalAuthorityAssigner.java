package edu.stanford.muse.ie;

import edu.stanford.muse.Config;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.model.NEType;
import edu.stanford.muse.ner.tokenize.CICTokenizer;
import edu.stanford.muse.ner.tokenize.Tokenizer;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Span;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains pre-processing computations for assign-authorities.jsp
 * and entity resolutions in the browse page (with expandname.jsp).
 * Map<String, EntityFeature> mixtures; This is the crucial object that contains
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
 * The size of this mixtures index created by this class generally as big as emails index,
 * that is odd, TODO: try to bring down the size of the mixtures index
 */
public class InternalAuthorityAssigner implements StatusProvider, Serializable {
	private static final long		serialVersionUID	= 1L;

	private static Log				log					= LogFactory.getLog(InternalAuthorityAssigner.class);

	public Map<Short, Entities>		entitiesData		= new HashMap<>();

	//contains status at any moment.
	String							status;
	double							pctComplete			= 0.0;
	//control is set to false, when the entityfeature is doing the job and this class does not have the control.
	EntityFeature					control				= null;
	//set cancel to true if wish to cancel the op, cancel will be checked or used in during repetitive or heavy tasks 
	boolean							cancel				= false;

    /** load this object from a file, if its already available */
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
				stats += "#People: " + aa.entitiesData.get(EntityFeature.PERSON).size() + "\n";
				stats += "#Correspondents: " + aa.entitiesData.get(EntityFeature.CORRESPONDENT).size() + "\n";
				stats += "#Places: " + aa.entitiesData.get(EntityFeature.PLACE).size() + "\n";
				stats += "#Orgs: " + aa.entitiesData.get(EntityFeature.ORG).size();
				log.info("Loaded entities from file: " + AUTHORITY_ASSIGNER_FILE + "\n" + stats);
				return aa;
			} catch (Exception e) {
				edu.stanford.muse.util.Util.print_exception ("Exception while reading: " + AUTHORITY_ASSIGNER_FILE + " file, constructing the object...", e, log);
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

	private void setupCorrespondents(Collection<EmailDocument> docs, AddressBook ab) {
        // compute entitySet for correspondents, by setting up maps of centity -> display name
        {
            Map<Integer, Integer> contactIdToFreq = new LinkedHashMap<>(); // compute contact id -> Freq
            for (EmailDocument ed : docs) {
                List<String> addrs = ed.getAllAddrs();
                for (String addr : addrs) {
                    Contact c = ab.lookupByEmail(addr);
                    if (c != null) {
                        int cid = ab.getContactId(c);
                        if (!contactIdToFreq.containsKey(cid))
                            contactIdToFreq.put(cid, 0);
                        contactIdToFreq.put(cid, contactIdToFreq.get(cid) + 1);
                    }
                }
            }

            List<Contact> contacts = ab.allContacts();
            Entities entitySet = entitiesData.get(EntityFeature.CORRESPONDENT);
            for (int i = 0; i < contacts.size(); i++) {
                //Set<String> names = contacts.get(i).names;
                int cid = ab.getContactId(contacts.get(i));

                if (!contactIdToFreq.containsKey(cid)) {
                    //These contacts generally doesn't have any email addresses associated with them.
                    log.warn("Contact freq doesn't contain the contact: " + contacts.get(i).emails);
                    continue;
                }

                int freq = contactIdToFreq.get(cid);
                String displayName = contacts.get(i).pickBestName();
                if (displayName == null)
                    continue;
                displayName = displayName.replaceAll("^\\s+|\\s+$", "");
                if (displayName.contains(" ")) {
                    entitySet.add(displayName, freq);

                    String canonicalEntity = IndexUtils.canonicalizeEntity(displayName);
                    if (canonicalEntity == null)
                        continue;
                    if (entitySet.get(canonicalEntity) == null)
                        entitySet.put(canonicalEntity, displayName);
                }
                if (cancel)
                    return;
            }
        }
    }

    private Set<String> setupAcronyms(Archive archive) {
        Set<String> acronyms = new LinkedHashSet<>();

        pctComplete = 0;
        int i = 0;
        Collection<edu.stanford.muse.index.Document> docs = archive.getAllDocs();
        Tokenizer tokenizer = new CICTokenizer(); // this is being used only to generate acronyms... duh!
        for (edu.stanford.muse.index.Document d : docs) {
            String content = archive.getContents(d, false);
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
                edu.stanford.muse.util.Util.print_exception("Error trying to get acronyms: ", e, log);
            }

            status = "Collected acronyms in " + i + "/" + docs.size() + " messages";
            pctComplete = ((double) i * 100) / docs.size();
            i++;
            if (cancel)
                return null;
        }

        return acronyms;
    }

    /** Initializes the object */
	public void initialize(Archive archive) {
        Collection<EmailDocument> docs = (Collection) archive.getAllDocs();
        AddressBook ab = archive.addressBook;
        long start_time = System.currentTimeMillis();

        // create empty data structs for each type
		{
			short[] types = new short[]{EntityFeature.PERSON, EntityFeature.CORRESPONDENT, EntityFeature.ORG, EntityFeature.PLACE};
			for (short t : types) {
				if (!entitiesData.containsKey(t))
					entitiesData.put(t, new Entities());
			}
		}

		if (cancel)
			return;

		Set<String> acronyms = new LinkedHashSet<>();

		/*
		 * There are two iterations on all docs, because the entities and their
		 * expansions can occur in any order, first collect all acronyms and
		 * then
		 * analyze every entity that comes in the way.
		 */
		//get all acronyms
		// acronyms = setupAcronyms(archive);
		setupCorrespondents(docs, ab);

        int di = 0;
		for (EmailDocument ed : docs) {
			List<String> people, orgs, places;
            try {
                Span[] names = archive.getAllNamesInDoc(ed, true);
                people = Arrays.asList(names).stream()
                        .filter(n -> n.type == NEType.Type.PERSON.getCode())
                        .map(n -> n.text).collect(Collectors.toList());
                places = Arrays.asList(names).stream()
                        .filter(n-> n.type == NEType.Type.PLACE.getCode())
                        .map(n -> n.text).collect(Collectors.toList());
                orgs = Arrays.asList(names).stream()
                        .filter(n-> n.type == NEType.Type.ORGANISATION.getCode())
                        .map(n -> n.text).collect(Collectors.toList());
            } catch (IOException ioe) {
                edu.stanford.muse.util.Util.print_exception ("Error trying to get acronyms and contact ids: ", ioe, log);
                continue;
            }

			//List<String> expansions = new ArrayList<String>();
			// String content = archive.getContents(ed, false);
			//For an acronym, expand it to one of entities to only of the types: place, org or people
			//else it adds noise and can increase the size of the dump uncontrollably
			//			try {
			//				//generally, org are the one with acronyms; hence get non-person like names
			//				Set<String> names = CICTokenizer.tokenizeWithoutOffsets(content, false);
			//				if (names != null)
			//					for (String name : names) {
			//						String acronym = "";
			//						//make acronym out of name
			//						String[] words = name.split("\\W+");
			//						if (words.length > 2) {
			//							for (String word : words)
			//								if (!stopWordsSet.contains(word))
			//									acronym += word.substring(0, 1);
			//							if (acronyms.contains(acronym)) {
			//								//log.info("Adding phrase:" + name + " with acronym: " + acronym);
			//								//TODO: avoid regexps, instead itearte over the string
			//								name = name.replaceAll("^\\W+|\\W+$", "");
			//								expansions.add(name);
			//							}
			//						}
			//					}
			//			} catch (Exception e) {
			//				e.printStackTrace();
			//			}

			// note that entities could have repetitions.
			// so we create a *set* of entities, but after canonicalization.
			// canonical to original just uses an arbitrary (first) occurrence
			// of the entity
			List<List<String>> allEntities = Arrays.asList(people, orgs, places);
			short[] allTypes = new short[]{EntityFeature.PERSON, EntityFeature.ORG, EntityFeature.PLACE};
			for(int ei = 0; ei < allEntities.size(); ei++) {
				List<String> entities = allEntities.get(ei);
				Short et = allTypes[ei];
				for (String e : entities) {
					if (e != null && (et != EntityFeature.PERSON || e.contains(" "))) {
						Entities d = entitiesData.get(et);
						String canonicalEntity = IndexUtils.canonicalizeEntity(e);
						if (canonicalEntity == null)
							continue;
						if (d.get(canonicalEntity) == null)
							d.put(canonicalEntity, e);

						Integer I = d.getCount(canonicalEntity);
						d.putCount(canonicalEntity, (I == null) ? 1 : I + 1);
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
		    d.sort();

		log.info("Time taken for sorting entities: " + (System.currentTimeMillis() - start_time));

		String AUTHORITY_ASSIGNER_FILE = archive.baseDir + File.separator + Config.AUTHORITY_ASSIGNER_FILENAME;
		log.info("Writing InternalAuthorityAssigner object to file" + AUTHORITY_ASSIGNER_FILE);
		ObjectOutputStream oos ;
		try {
			String stats = "";
			stats += "#People: " + this.entitiesData.get(EntityFeature.PERSON).size() + "\n";
			stats += "#Correspondents: " + this.entitiesData.get(EntityFeature.CORRESPONDENT).size() + "\n";
			stats += "#Places: " + this.entitiesData.get(EntityFeature.PLACE).size() + "\n";
			stats += "#Orgs: " + this.entitiesData.get(EntityFeature.ORG).size();
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
		//check mixtures index.
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

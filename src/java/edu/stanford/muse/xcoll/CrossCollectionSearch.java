package edu.stanford.muse.xcoll;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.ie.AuthorityMapper;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.ModeConfig;
import edu.stanford.muse.webapp.Sessions;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.*;

/**
 * Created by hangal on 6/29/17.
 * This is a class that keeps track of all entities in multiple archives and provides an interface to search through them at a token level.
 */
public class CrossCollectionSearch {
    public static Log log = LogFactory.getLog(CrossCollectionSearch.class);

    public static List<Archive.ProcessingMetadata> archiveMetadatas = new ArrayList<>(); // metadata's for the archives. the position number in this list is what is used in the EntityInfo

    private static Multimap<String, EntityInfo> cTokenToInfos;

    // this has to be thought through fully -- which version of canonicalize to use?
    private static String canonicalize (String s) {
        if (s == null)
            return null;
        else
            return s.toLowerCase();
    }

    synchronized public static void initialize() {
        if (cTokenToInfos != null)
            return;

        if (ModeConfig.isDiscoveryMode())
            initialize(Config.REPO_DIR_DISCOVERY);
        else if (ModeConfig.isProcessingMode())
            initialize(Config.REPO_DIR_PROCESSING);
    }

    /** initializes lookup structures (entity infos and ctokenToInfos) for cross collection search
     * reads all archives available in the base dir.
     **/
    synchronized private static void initialize(String baseDir) {

        // this is created only once in one run. if it has already been created, reuse it.
        // in the future, this may be read from a serialized file, etc.
        cTokenToInfos = LinkedHashMultimap.create();

        File[] files = new File(baseDir).listFiles();

        if (files == null) {
            log.warn ("Trying to initialize cross collection search from an invalid directory: " + baseDir);
            return;
        }

        int archiveNum = 0;
        for (File f: files) {
            if (!f.isDirectory())
                continue;

            try {
                String archiveFile = f.getAbsolutePath() + File.separator + Archive.SESSIONS_SUBDIR + File.separator + "default" + Sessions.SESSION_SUFFIX;
                if (!new File(archiveFile).exists())
                    continue;

                Archive archive = SimpleSessions.readArchiveIfPresent(f.getAbsolutePath());
                if (archive == null) {
                    log.warn ("failed to read archive from " + f.getAbsolutePath());
                    continue;
                }

                log.info ("Loaded archive from " + f.getAbsolutePath());

                Archive.ProcessingMetadata pm = SimpleSessions.readProcessingMetadata(f.getAbsolutePath() + File.separator + Archive.SESSIONS_SUBDIR, "default");
                archiveMetadatas.add (pm);
                log.info ("Loaded archive metadata from " + f.getAbsolutePath());

                // process all docs in this archive to set up centityToInfo map
                Map<String, EntityInfo> centityToInfo = new LinkedHashMap<>();
                {
                    for (Document d : archive.getAllDocs()) {
                        EmailDocument ed = (EmailDocument) d;

                        for (String entity : archive.getEntitiesInDoc(ed)) {
                            String centity = canonicalize(entity);
                            EntityInfo ei = centityToInfo.get(centity);
                            if (ei == null) {
                                ei = new EntityInfo();
                                ei.archiveNum = archiveNum;
                                ei.displayName = entity;
                                centityToInfo.put(centity, ei);
                            }

                            if (ei.firstDate == null || ei.firstDate.after(ed.date)) {
                                ei.firstDate = ed.date;
                            }
                            if (ei.lastDate == null || ei.lastDate.before(ed.date)) {
                                ei.lastDate = ed.date;
                            }
                            ei.count++;
                        }
                    }
                    // now process authorities
                    {
                        AuthorityMapper authorityMapper = archive.getAuthorityMapper();
                        // ...


                    }
                }

                log.info ("Archive # " + archiveNum + " read " + centityToInfo.size() + " entities");

                // now set up this map as a token map
                for (EntityInfo ei: centityToInfo.values()) {
                    String entity = ei.displayName;
                    String centity = canonicalize(entity);
                    Set<String> ctokens = new LinkedHashSet<>(Util.tokenize(centity)); // consider a set of tokens because we don't want repeats
                    for (String ctoken: ctokens)
                        cTokenToInfos.put (ctoken, ei);
                }
            } catch (Exception e) {
                Util.print_exception ("Error loading archive in directory " + f.getAbsolutePath(), e, log);
            }
            archiveNum++;
        }
    }


    private static Collection<EntityInfo> getInfosFor (String entity) {
        initialize();
        Set<EntityInfo> result = new LinkedHashSet<>();

        // tokenize entity and look up all infos that contain any of its tokens
        // todo: make this handle variants

        String centity = canonicalize(entity);
        List<String> ctokens = Util.tokenize(centity);

        for (String ctoken: ctokens) {
            Collection<EntityInfo> infos = cTokenToInfos.get(ctoken);
            if (infos != null)
                for (EntityInfo info: infos) {
                    String cDisplayName = canonicalize(info.displayName);
                    if (cDisplayName != null && cDisplayName.contains (centity)) // only if centity is contained in entirety in cDisplayName do we add this info to result
                        result.add (info);
                }
        }

        return result;
    }

    /* returns archiveNum -> {String -> Info, String -> Info, ...}
    *  get all entity infos that contain entity. result only includes infos that contain entity in its entirety.
     * e.g. entity="Mary" would include both { Mary Lou Retton -> Info, Mary Ann Spinner -> Info, etc.}
     * but entity="Mary Lou" would include only  { Mary Lou Retton -> Info }
     * note that only full words are matched, i.e.
     * entity="Mary" will not return a name like MaryLou in any case.
     **/

    public static Multimap<Integer, EntityInfo> search (String entity) {

        // first get all infos that match the entity
        Collection<EntityInfo> infos = getInfosFor(entity);

        // break them down by archiveNum
        Multimap<Integer, EntityInfo> archiveNumToInfos = LinkedHashMultimap.create();
        for (EntityInfo info: infos)
            archiveNumToInfos.put (info.archiveNum, info);

        return archiveNumToInfos;
    }
}

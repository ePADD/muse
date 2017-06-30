package edu.stanford.muse.xcoll;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.Sessions;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by hangal on 6/29/17.
 */
public class CrossCollectionSearch {
    public static Log log = LogFactory.getLog(CrossCollectionSearch.class);

    public static List<Archive.ProcessingMetadata> archiveMetadatas = new ArrayList<>();

    private static Multimap<String, EntityInfo> centityToInfos;

    // this has to be thought through fully -- which version of canonicalize to use?
    private static String canonicalize (String s) {
        if (s == null)
            return null;
        else
            return s.toLowerCase();
    }

    synchronized public static void initialize(String baseDir) {
        if (centityToInfos != null)
            return;
        centityToInfos = LinkedHashMultimap.create();
        File[] files = new File(baseDir).listFiles();

        for (File f: files) {
            if (!f.isDirectory())
                continue;

            String archiveFile = f.getAbsolutePath() + File.separator + Archive.SESSIONS_SUBDIR + File.separator + "default" + Sessions.SESSION_SUFFIX;

            if (!new File(archiveFile).exists())
                continue;

            int archiveNum = 0;
            try {
                Archive archive = SimpleSessions.readArchiveIfPresent(f.getAbsolutePath());
                if (archive == null) {
                    log.warn ("failed to read archive from " + f.getAbsolutePath());
                    continue;
                }

                Archive.ProcessingMetadata pm = SimpleSessions.readProcessingMetadata(f.getAbsolutePath() + File.separator + Archive.SESSIONS_SUBDIR, "default");

                archiveMetadatas.add (pm);

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
                                centityToInfo.put(centity, ei);
                                ei.archiveNum = archiveNum;
                                ei.displayName = entity;
                            }

                            if (ei.firstDate == null || ei.firstDate.before(ed.date)) {
                                ei.firstDate = ed.date;
                            }
                            if (ei.lastDate == null || ei.lastDate.after(ed.date)) {
                                ei.lastDate = ed.date;
                            }
                            ei.count++;
                        }
                    }
                    // now process authorities
                    {


                    }
                }

                log.info ("Archive # " + archiveNum + " read " + centityToInfo.size() + " entities");

                for (EntityInfo ei: centityToInfo.values()) {
                    String entity = ei.displayName;
                    String centity = canonicalize(entity);
                    centityToInfos.put (centity, ei);
                }
            } catch (Exception e) {
                Util.print_exception ("Error loading archive in directory " + f.getAbsolutePath(), e, log);
            }
            archiveNum++;
        }
    }

    private static Collection<EntityInfo> getInfosFor (String entity) {
        initialize(Config.REPO_DIR_PROCESSING);
        String centity = canonicalize(entity);
        Collection<EntityInfo> infos = centityToInfos.get(centity);
        return infos;
    }

    public static Multimap<Integer, EntityInfo> search (String entity) {
        Collection<EntityInfo> infos = getInfosFor(entity);
        Multimap<Integer, EntityInfo> result = LinkedHashMultimap.create();

        for (EntityInfo info: infos)
            result.put (info.archiveNum, info);
        return result;
    }
}

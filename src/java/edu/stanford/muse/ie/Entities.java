package edu.stanford.muse.ie;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.*;

//

/**
 * Contains entities of specific type like: person, organisation and place along
 * with supplementary data
 */
public class Entities implements Serializable, StatusProvider {
	private static Log			log					= LogFactory.getLog(FASTSearcher.class);

	private static final long	serialVersionUID	= 1L;

	public static class Info {
		Boolean	test;
		String	type, database;

		/**
		 * t - the type of entities i.e. Correspondents, people etc.
		 * db - Database for lookup, only fast and freebase are supported.
		 */
		public Info(String t, String db, boolean test) {
			this.type = t;
			this.database = db;
			this.test = test;
		}
	}

	//consult features index for entity features. this class contains very sparse representation of all entities mentioned in the email.
	//pairs -> list of <name, frequency> pairs.
	//canonicaltooriginal -> map from canonical name to original name.
	//counts -> map from canonical name to frequency. 
	//yes, this looks stupid.
	public List<Pair<String, Integer>>	pairs				= null;
	public Map<String, String>			canonicalToOriginal	= null;
	public Map<String, Integer>			counts;

	boolean								cancel				= false;
	String								status;
	double								pctComplete;
    //used to keep track of the html ids that are rendered in the html table on assign auth page
    Set<String>                         ids = new HashSet<String>();
    Random                              rand                = new Random();

	public Entities() {
		pairs = new ArrayList<Pair<String, Integer>>();
		canonicalToOriginal = new LinkedHashMap<String, String>();
		counts = new LinkedHashMap<String, Integer>();
	}

    public static class Score implements Comparable<Score>{
        public double score;
        public String scoredOn;
        public Score(double score, String scoredOn){
            this.score = score;
            this.scoredOn = scoredOn;
        }
        public Score(){}

        @Override
        public int compareTo(Score c) {
            if(c == null)
                return 1;
            if(c.score < score)
                return 1;
            else if (c.score > score)
                return -1;
            else return 0;
        }
    }

    public static List<Pair<FASTRecord, Score>> getFASTRecordsFor(String entity, FASTRecord.FASTDB et, Boolean sort, Archive archive) {
        String cname = IndexUtils.canonicalizeEntity(entity);
        Map<FASTRecord, Score> sr = new LinkedHashMap<FASTRecord, Score>();
        Set<FASTRecord> records = FASTSearcher.getMatches(cname, et);
        //get context and score
        EntityFeature eft = EntityFeature.getExactMatches(cname, archive);
        if(eft == null) {
            if(sort)
                log.warn("Cannot sort as no context found for: "+cname);
            sort = false;
        }

        for(FASTRecord record: records) {
            String dbpedia = null;
            Pair<String,String> sources = record.getAllSources();
            if(sources == null)
                continue;
            String dbn = sources.first, dbi = sources.second;
            List<String> dbs = Arrays.asList(dbn.split(":::"));
            String[] dbIds = dbi.split(":::");
            int idx = dbs.indexOf("dbpedia");
            if(idx>=0 && idx<=dbIds.length)
                dbpedia = "http://dbpedia.org/resource/"+dbIds[idx];

            if(!sort || dbpedia == null) {
                sr.put(record, new Score(0.0, null));
                continue;
            }

            Set<String> context = eft.cooccuringEntities.keySet();
            String[] t = new String[context.size()];
            int i=0;
            for(String c: context)
                t[i++] = c;

            Pair<String,Double> score = edu.stanford.muse.ie.Util.scoreWikiPage(dbpedia, t);
            sr.put(record, new Score(score.getSecond(), score.getFirst()));
        }
        System.err.println("sz b4 sorting"+ sr.size());
        return Util.sortMapByValue(sr);
    }

    public static List<Pair<FreebaseType, Score>> getFreebaseRecordsFor(String entity, FreebaseType.Type et, Boolean sort, Archive archive) {
        List<FreebaseType> fts = FreebaseSearcher.search(entity, et).result;
        Map<FreebaseType, Score> scores = new LinkedHashMap<FreebaseType, Score>();
        System.err.println("Num FB records: "+fts.size()+" for "+entity);
        for(FreebaseType ft: fts){
            if(!sort){
                scores.put(ft, new Score(0.0, null));
                continue;
            }
            String mid = ft.mid;
            String dbpedia = FreebaseSearcher.getWikiPage(mid);
            List<Pair<String,String>> aliases = FreebaseSearcher.getAliases(mid);
            if(dbpedia == null) {
                scores.put(ft, new Score(0.0, null));
                continue;
            }

            //heuristic score
            boolean exact = false;
            if(aliases != null)
                for(Pair<String,String> p: aliases)
                    if(p.getFirst().equals(entity))
                        exact = true;

            double s2 = 0;
            if(exact) s2 = 1;
            ft.dbpedia = dbpedia;
            log.info("DBpedia for "+mid+" is "+dbpedia);

            String cname = IndexUtils.canonicalizeEntity(entity);
            //get context and score
            EntityFeature eft = EntityFeature.getExactMatches(cname, archive);
            if(eft == null) {
                scores.put(ft, new Score(0.0, null));
                continue;
            }
            Set<String> context = eft.cooccuringEntities.keySet();
            String[] t = new String[context.size()];
            int i=0;
            for(String c: context)
                t[i++] = c;

            Pair<String,Double> score = edu.stanford.muse.ie.Util.scoreWikiPage(dbpedia, t);
            double s1 = score.getSecond();
            scores.put(ft, new Score(s2*10+s1, score.getFirst()));
        }
        return Util.sortMapByValue(scores);
    }

	//TODO: this method is very slow because it searches many names in FASTDB to see if they exist and proceeds further only if it exists.
	//Load FAST data into memory nad make it faster
	//TODO: this method should also take sort as parameter and should sort teh records if this variable is set
	public String getHtmlFor(int beginIdx, int endIdx, Info info, Archive archive) {
		String type = info.type;
		String db = info.database;
        //check if the context feature is available
        boolean enableDAButton = EntityFeature.indexExists(archive);

		Boolean test = false;
		Entities entitiesData = this;
		JSONArray table = new JSONArray();

		log.info("Number of confirmed authorities: " + AuthorisedAuthorities.getAuthorisedAuthorities(archive).size());

		List<Integer> indices = new ArrayList<Integer>();
		Set<Integer> considered = new HashSet<Integer>();
		int maxIndex = 0;
		if (test) {
			for (int i = 0; i < pairs.size(); i++) {
				maxIndex += pairs.get(i).second;
				indices.add(maxIndex);
			}
		}

		int entitynum = 0;
		if (beginIdx < 0)
			beginIdx = 0;

		long st;
		long contextCollectionTime = 0, resolutionTime = 0, whileLoopTime = 0;
		long totalTime = System.currentTimeMillis();

		int iter = beginIdx - 1, maxEntries = Math.min(endIdx, pairs.size()) - Math.max(0, beginIdx);
		while (true) {
			if (entitynum >= maxEntries)
				break;
			iter++;
			//for (int iter = Math.max(0, beginIdx); iter < Math.min(endIdx, pairs.size()); iter++) {
			if (iter >= pairs.size())
				break;

			pctComplete = (double) (entitynum * 100) / (double) maxEntries;
			status = "Scanned " + iter + " entities and found " + entitynum + "/" + maxEntries + " hits";

			st = System.currentTimeMillis();
			Pair<String, Integer> p;
			if (!test)
				p = pairs.get(iter);
			else {
				while (true) {
					//For sampling based on the distribution of frequency
					int k = (int) (Math.random() * maxIndex);
					int j = 0;
					for (j = 0; j < indices.size(); j++)
						if (indices.get(j) > k)
							break;
					if (j < pairs.size() && j >= 0)
						p = pairs.get(j);
					else
						continue;
					if (!considered.contains(j)) {
						considered.add(j);
						break;
					}
				}
			}

			Set<String> entities = new LinkedHashSet<String>();
			entities.add(p.getFirst());
			Set<String> cnames = new HashSet<String>();
			cnames.add(IndexUtils.canonicalizeEntity(p.getFirst()));

			boolean authorityResolved = false;
			// there are 2 ways a name can be resolved -- either its already in cnameToDefiniteFastID, or we have to lookup cnameToFASTIds
			Set<FASTPerson> setPerson = null;

			Set<FASTCorporate> setOrg = null;
			Set<FASTGeographic> setPlaces = null;
			//freebase places.
			Set<FreebaseApi> placesSetFB = null, orgSetFB = null;

			Authority authority = null;
			Map<String, Authority> cnameToDefiniteID = AuthorisedAuthorities.getAuthorisedAuthorities(archive);
			if (cnameToDefiniteID != null)
				for (String cname : cnames) {
					authority = cnameToDefiniteID.get(cname);
					if (authority != null) {
						break;
					}
				}

			whileLoopTime += (System.currentTimeMillis() - st);

			for (String entity : entities) {
				st = System.currentTimeMillis();
				Set<FASTRecord> fts;
				if (type.equals("person") || type.equals("correspondent")) {
					//System.err.println("Searching for: " + entity);
					fts = FASTSearcher.getMatches(entity, FASTRecord.FASTDB.PERSON);
					if (fts == null) {
						log.info("No matches for: " + entity);
						//System.err.println("No matches for: " + entity);
						continue;
					} 

					for (FASTRecord ft : fts) {
						if (!(ft instanceof FASTPerson)) {
							log.info("Lookup for term:" + entity + ", gave a different instance of FASTType.");
							continue;
						}
						FASTPerson fp = (FASTPerson) ft;
						if (fp != null) {
							if (setPerson == null)
								setPerson = new LinkedHashSet<FASTPerson>();
							setPerson.add(fp); // this entity is unresolved, there could be multiple hits
						}
					}
				}
				else if (type.equals("org")) {
					if (db != null && db.equalsIgnoreCase("fast")) {
						fts = FASTSearcher.getMatches(entity, FASTRecord.FASTDB.CORPORATE);
						if (fts == null) {
							log.info("No matches for: " + entity);
							continue;
						}
						for (FASTRecord ft : fts) {
							if (!(ft instanceof FASTCorporate)) {
								log.info("Lookup for term:" + entity + ", gave a different instance of FASTType.");
								continue;
							}
							FASTCorporate fp = (FASTCorporate) ft;
							if (fp != null) {
								if (setOrg == null)
									setOrg = new LinkedHashSet<FASTCorporate>();
								log.info("Adding to setorg: " + fp.names.size());
								setOrg.add(fp);
							}
						}
					} else {
						String oEntity = entitiesData.canonicalToOriginal.get(entity);
						if (oEntity != null) {
							FreebaseApi fa = FreebaseSearcher.search(oEntity, FreebaseType.Type.Organization, 10);
							//FreebaseApi fa2 = FreebaseSearcher.search(entity, FreebaseType.Type.Organization,10);
							//if(fa!=null&&fa2!=null&&fa2.result!=null&&fa.result!=null)
							//fa.result.addAll(fa2.result);
							if (orgSetFB == null)
								orgSetFB = new LinkedHashSet<FreebaseApi>();
							if (fa != null)
								orgSetFB.add(fa);
							else
								log.info("Result for:" + entity + " is null.");
						} else {
							log.info("No original entity found for: " + entity);
						}
					}
				} else if (type.equals("places")) {
					if (db != null && db.equalsIgnoreCase("fast")) {
						fts = FASTSearcher.getMatches(entity, FASTRecord.FASTDB.GEOGRAPHIC);
						if (fts == null) {
							log.info("No matches for: " + entity);
							continue;
						}
						for (FASTRecord ft : fts) {
							if (!(ft instanceof FASTGeographic)) {
								log.info("Lookup for term:" + entity + ", gave a different instance of FASTType.");
								continue;
							}
							FASTGeographic fp = (FASTGeographic) ft;
							if (fp != null) {
								if (setPlaces == null)
									setPlaces = new LinkedHashSet<FASTGeographic>();
								setPlaces.add(fp); // this entity is unresolved, there could be multiple hits
							}
						}
					} else {
						String oEntity = entitiesData.canonicalToOriginal.get(entity);
						if (oEntity != null) {
							FreebaseApi fa = FreebaseSearcher.search(oEntity, FreebaseType.Type.Location);
							if (placesSetFB == null)
								placesSetFB = new LinkedHashSet<FreebaseApi>();
							if (fa != null)
								placesSetFB.add(fa);
							else
								log.info("Result for:" + entity + " is null.");
						} else {
							log.info("No original entity found for: " + entity);
						}
					}
				}
			}
			resolutionTime += (System.currentTimeMillis() - st);

			if (authority == null && setPerson == null && setOrg == null && setPlaces == null && placesSetFB == null && orgSetFB == null) {
				log.info("No hits in FAST/Freebase for " + p.first + "... skipping");
				continue; // no hits, just skip.
			}

			JSONObject obj = new JSONObject();
			// now print it out
			JSONArray classes = new JSONArray();
			JSONArray contexts = new JSONArray();
			JSONArray values = new JSONArray();

			//html += "<tr><td class=\"search name\">";
			classes.put("search name");
			if (entitiesData.canonicalToOriginal.containsKey(p.getFirst()))
				values.put(Util.escapeHTML(entitiesData.canonicalToOriginal.get(p.getFirst())));
			else
				values.put(Util.escapeHTML(p.getFirst()));
			contexts.put("");
			//html += "</td>";

			classes.put("");
			if (entitiesData.counts.containsKey(p.getFirst())) {
				//html += "<td>" + entitiesData.counts.get(p.getFirst()) + "</td>";
				values.put(entitiesData.counts.get(p.getFirst()));
			}
			else {
				//html += "<td>" + p.getSecond() + "</td>";
				values.put(p.getSecond());
			}
			contexts.put("");

			StringBuilder contextSB = new StringBuilder();

			st = System.currentTimeMillis();
			boolean featuresExist = EntityFeature.indexExists(archive);
			for (String entity : entities) {
				String normalisedName = entity;//entitiesData.canonicalToOriginal.get(entity);	
				EntityFeature ef = null;
				if (featuresExist) {
					try {
						log.info("Searching for:" + normalisedName);
						ef = EntityFeature.getExactMatches(normalisedName, archive);
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
				}
				if (ef != null) {
					Set<String> incontext = ef.cooccuringEntities.keySet();
					incontext.remove(normalisedName);
					int ii = 0;
					for (String str : incontext) {
						contextSB.append(str);
						if (ii < incontext.size() - 1)
							contextSB.append(",");
						ii++;
					}
					if (incontext.size() > 0)
						log.info("Found context for: " + normalisedName);
					else
						log.info("Did not find context for: " + normalisedName);
				}
				else {
					log.info("No context info for: " + normalisedName);
				}
			}
			//data-context attribute is required to evaluate dbpedia pages and to resort them.
			//html += "<td data-context='" + Util.escapeHTML(contextSB.toString()) + "'>";

			contexts.put(Util.escapeHTML(contextSB.toString()));
			//boolean serialNums = (setPerson.size() > 1);
			//authorityresolved check is unnecessary
			String html = "";
			if (authority == null) {
                //looks bad: repeating code for FAST types.
                if (!Util.nullOrEmpty(setPerson)) {
                    // if the authority is resolved, check the checkbox
                    for (FASTPerson fp : setPerson) {
                        try {
                            Pair<String, String> links = fp.getAllSources();
                            html += "<div class='record'><input data-ids='" + links.second + "' data-dbTypes='" + links.first + "' type='checkbox' " + (authorityResolved ? "checked" : "") + "> " + fp.toHTMLString() + " <br/></div>";
                        } catch (Exception e) {
                            Util.print_exception("Exception while fetching sources for: " + fp.toHTMLString(), e, log);
                        }
                    }
                } else if (!Util.nullOrEmpty(setOrg)) {
                    // if the authority is resolved, check the checkbox
                    for (FASTCorporate fp : setOrg) {
                        Pair<String, String> links = fp.getAllSources();
                        html += "<div class='record'><input data-ids='" + links.second + "' data-dbTypes='" + links.first + "' type='checkbox' " + (authorityResolved ? "checked" : "") + "> " + fp.toHTMLString() + " <br/></div>";
                    }
                } else if (!Util.nullOrEmpty(setPlaces)) {
                    for (FASTGeographic fp : setPlaces) {
                        Pair<String, String> links = fp.getAllSources();
                        html += "<div class='record'><input data-ids='" + links.second + "' data-dbTypes='" + links.first + "' type='checkbox' " + (authorityResolved ? "checked" : "") + "> " + fp.toHTMLString() + " <br/></div>";
                    }
                } else if (!Util.nullOrEmpty(placesSetFB) || !Util.nullOrEmpty(orgSetFB)) {
                    Set<FreebaseApi> set = null;
                    if (!Util.nullOrEmpty(placesSetFB))
                        set = placesSetFB;
                    else
                        set = orgSetFB;
                    for (FreebaseApi fa : set) {
                        if (fa == null) {
                            continue;
                        }
                        List<FreebaseType> fts = new ArrayList<FreebaseType>();
                        if (fa.result != null)
                            fts.addAll(fa.result);
                        for (FreebaseType ft : fts) {
                            //contains only the freebase id.
                            html += "<div class='record'><input data-ids='" + ft.mid + "' data-dbTypes='freebase' type='checkbox' " + (authorityResolved ? "checked" : "") + "> " + ft.toHtmlString() + " <br/></div>";
                        }
                    }
                }
            } else {
				html += authority.toHTMLString();
			}
			contextCollectionTime += (System.currentTimeMillis() - st);

            String tab = "&nbsp&nbsp&nbsp&nbsp";

            //random id for this row
            String randNum = null;
            if(rand == null)
                rand = new Random();
            //there may no be any repeatability here, not at the scale we are dealing with; but cannot take a chance
            if(ids == null)
                ids = new HashSet<String>();
            while(randNum == null || ids.contains(randNum))
                randNum = rand.nextInt()+"";
            ids.add(randNum);

            if(enableDAButton) {
                html += "<span style='cursor:pointer' id='" + randNum + "' onclick='sort(" + randNum + ")' class='sort'><i class='fa fa-sort-amount-desc'></i></span>" + tab;
            }
			html += "<span class='manual' id='manual_" + randNum + "'>";
			html += "<a class='popupManual' id='inline_" + randNum + "' title='' href='#manualassign' onclick='localStorage.setItem(\"entitynum\",\"" + randNum + "\");'>";
			html += "<i class='manual-authority fa fa-plus' id='manual_" + randNum + "'/></i></a></span>\n";
			//show it whenever this row is busy
            html += tab+"<img style='height:15px;display:none' class='loading' src='images/spinner.gif'/>";
			values.put(html);
			classes.put("");

			//html += "</td>";
			if (test) {
				//	html += "<td>Position of the correct match: <input placeholder='1-10' type=\"text\" size=4></input></td>";
				contexts.put("");
				values.put("Position of the correct match: <input placeholder='1-10' type=\"text\" size=4></input>");
				classes.put("");
			}
			obj.put("contexts", contexts);
			obj.put("classes", classes);
			obj.put("values", values);
			table.put(obj);
			//html += "</tr>";
			entitynum++;
		}
		JSONObject obj2 = new JSONObject();
		obj2.put("total", pairs.size());
		//use this endIndex as the beginIndex for the next step.
		obj2.put("endIndex", iter + 1);
		obj2.put("data", table);
		totalTime = System.currentTimeMillis() - totalTime;
		log.info("Total time: " + totalTime + "\nTime to collect record hits: " + whileLoopTime + "\nResolution time: " + resolutionTime + "\nContext Collection Time: " + contextCollectionTime);
		return obj2.toString();
	}

	@Override
	public String getStatusMessage() {
		return JSONUtils.getStatusJSON(status, (int) pctComplete, 0, 0);
	}

	@Override
	public void cancel() {
		cancel = true;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	public static void main(String[] args) {
		try {
			String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
			Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
			InternalAuthorityAssigner authorities = new InternalAuthorityAssigner();
			authorities.initialize(archive);

			Entities ent = authorities.entitiesData.get(EntityFeature.PERSON);
			Info info = new Info("person", "fast", false);
			String html = ent.getHtmlFor(0, 1, info, archive);
			System.err.println(html);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

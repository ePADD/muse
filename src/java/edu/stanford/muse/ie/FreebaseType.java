package edu.stanford.muse.ie;

import edu.stanford.muse.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class FreebaseType {
	//TODO merge this with FastRecord and rename FASTRecord to Record.
	//Note: Dont use the type value except for location.
	//TODO: fix it.
	static public enum Type {
		All("all"),
		Music("/music"),
		Books("/book"),
		Media("/media_common"),
		People("/people"),
		Film("/film"),
		TV("/tv"),
		Location("/location"),
		Business("/business"),
		Fictional_Universes("/fictional_universe"),
		Organization("/organization"),
		Biology("/biology"),
		Sports("/sports"),
		Awards("/award"),
		Education("/education"),
		Time("/time"),
		Government("/government"),
		Soccer("/soccer"),
		Architecture("/architecture"),
		Medicine("/medicine"),
		Video_Games("/cvg"),
		Projects("/projects"),
		Physical_Geography("/geography"),
		Visual_Art("/visual_art"),
		Olympics("/olympics"),
		Internet("/internet"),
		Military("/military"),
		Theater("/theater"),
		Transportation("/transportation"),
		Influence("/influence"),
		Protected_Places("/protected_sites"),
		Periodicals("/periodicals"),
		Broadcast("/broadcast"),
		Food_Drink("/food"),
		Royalty_and_Nobility("/royalty"),
		Travel("/travel"),
		Astronomy("/astronomy"),
		Aviation("/aviation"),
		Boats("/boats"),
		American_football("/american_football"),
		Baseball("/baseball"),
		Event("/event"),
		Law("/law"),
		Computers("/computer"),
		Library("/library"),
		Religion("/religion"),
		Chemistry("/chemistry"),
		Cricket("/cricket"),
		Basketball("/basketball"),
		Symbols("/symbols"),
		Comics("/comic_books"),
		Language("/language"),
		Ice_Hockey("/ice_hockey"),
		Automotive("/automotive"),
		Exhibitions("/exhibitions"),
		Martial_Arts("/martial_arts"),
		Opera("/opera"),
		Games("/games"),
		Boxing("/boxing"),
		Rail("/rail"),
		Tennis("/tennis"),
		Zoos_and_Aquariums("/zoos"),
		Amusement_Parks("/amusement_parks"),
		Spaceflight("/spaceflight"),
		Celebrities("/celebrities"),
		Hobbies_and_Interests("/interests"),
		Meteorology("/meteorology"),
		Conferences_and_Conventions("/conferences"),
		Digicams("/digicams"),
		Fashion_Clothing_and_Textiles("/fashion"),
		Engineering("/engineering"),
		Radio("/radio"),
		Measurement_Unit("/measurement_unit"),
		Skiing("/skiing"),
		Bicycles("/bicycles"),
		Geology("/geology"),
		Comedy("/comedy"),
		Physics("/physics");
		public final String	txt;

		Type(String str) {
			this.txt = str;
		}
	}

	public static class Notable {
		public String	name, id;

		public Notable() {
		}
	}

	public String	name;
	public Double	confidence; //score;
	public String	mid, dbpedia;
	public String	lang;

	public Pair<String, String> getAllSources() {
		String sourceNames = "", sourceIds = "";
		String sep = Authority.sep;
        sourceNames += Authority.types[Authority.FREEBASE];
		sourceIds += mid;
        if(dbpedia != null) {
            sourceNames += sep+"dbpedia";
            String id = dbpedia;
            int idx = dbpedia.lastIndexOf('/');
            if(idx>=0 && idx<(dbpedia.length()-1))
                id = dbpedia.substring(idx+1);
            sourceIds += sep+id;
        }
		return new Pair<String, String>(sourceNames, sourceIds);
	}

	public String toHtmlString() {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append("<br/><div class=\"ids\">");

		String fb_link = "https://www.freebase.com/" + mid;
		// assemble HTML for all external links into this
		List<String> links = new ArrayList<String>();
        if (mid != null)
			links.add("<a target=\"_blank\" href=\"" + fb_link + "\">Freebase:" + mid + "</a> ");
        if(dbpedia !=null)
            links.add("<a target=\"_blank\" href=\""+dbpedia+"\">DBpedia:" + dbpedia + "</a>");

		for (int i = 0; i < links.size(); i++)
		{
			sb.append(links.get(i));
		}
		sb.append("</div>");
		return sb.toString();
	}
}

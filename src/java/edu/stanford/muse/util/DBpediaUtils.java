package edu.stanford.muse.util;

import edu.stanford.muse.Config;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by vihari on 09/04/17.
 */
public class DBpediaUtils {
    public static List<String> ignoreDBpediaTypes = new ArrayList<>();

    static {
        //Consider this, the type dist. of person-like types with the stop word _of_ is
        //10005 Person|Agent
        //4765 BritishRoyalty|Royalty|Person|Agent
        //2628 Noble|Person|Agent
        //1150 Saint|Cleric|Person|Agent
        //669 Monarch|Person|Agent
        //668 OfficeHolder|Person|Agent
        //627 ChristianBishop|Cleric|Person|Agent
        //525 MilitaryPerson|Person|Agent
        //249 SportsTeamMember|OrganisationMember|Person|Agent
        //247 SoapCharacter|FictionalCharacter|Person|Agent
        //158 FictionalCharacter|Person|Agent
        //114 Pope|Cleric|Person|Agent
        ignoreDBpediaTypes = Arrays.asList(
                "RecordLabel|Company|Organisation",
                "Band|Organisation",
                "Band|Group|Organisation",
                //Tokyo appears in 94 Album|MusicalWork|Work, 58 Film|Work, 57 City|Settlement|PopulatedPlace|Place
                //London appears in 192 Album|MusicalWork|Work, 123 Settlement|PopulatedPlace|Place
                //Pair in 130 Film|Work, 109 Album|MusicalWork|Work
                //Can you believe this?!
                "Film|Work",
                //This type is too noisy and contain titles like
                //Cincinatti Kids, FA_Youth_Cup_Finals, The Strongest (and other such team names)
                "OrganisationMember|Person",
                "PersonFunction",
                "GivenName",
                "Royalty|Person",
                //the following type has entities like "Cox_Broadcasting_Corp._v._Cohn", that may assign wrong type to tokens like corp., co., ltd.
                "SupremeCourtOfTheUnitedStatesCase|LegalCase|Case|UnitOfWork",
                //should be careful about Agent type, though it contains personal names it can also contain many non-personal entities
                "ComicsCharacter|FictionalCharacter|Person",
                "MusicalWork|Work","Sport", "Film|Work", "Band|Group|Organisation", "Food",
                "EthnicGroup","RadioStation|Broadcaster|Organisation", "MeanOfTransportation", "TelevisionShow|Work",
                "Play|WrittenWork|Work","Language", "Book|WrittenWork|Work","Genre|TopicalConcept", "InformationAppliance|Device",
                "SportsTeam|Organisation", "Eukaryote|Species","Software|Work", "TelevisionEpisode|Work", "Comic|WrittenWork|Work",
                "Mayor", "Website|Work", "Cartoon|Work",
                "LawFirm|Company|Organisation"
        );
    }

    //just cleans up trailing numbers in the string
    private static String cleanRoad(String title) {
        String[] words = title.split(" ");
        String lw = words[words.length - 1];
        String ct = "";
        boolean hasNumber = false;
        for (Character c : lw.toCharArray())
            if (c >= '0' && c <= '9') {
                hasNumber = true;
                break;
            }
        if (words.length == 1 || !hasNumber)
            ct = title;
        else {
            for (int i = 0; i < words.length - 1; i++) {
                ct += words[i];
                if (i < words.length - 2)
                    ct += " ";
            }
        }
        return ct;
    }

    /**
     * We put phrases through some filters in order to avoid very noisy types
     * These are the checks
     * 1. Remove stuff in the brackets to get rid of disambiguation stuff
     * 2. If the type is road, then we clean up trailing numbers
     * 3. If the type is settlement then the title is written as "Berkeley,_California" which actually mean Berkeley_(California); so cleaning these too
     * 4. We ignore certain noisy types. see ignoreDBpediaTypes
     * 5. Ignores any single word names
     * 6. If the type is person like but the phrase contains either "and" or "of", we filter this out.
     * returns either the cleaned phrase or null if the phrase cannot be cleaned.
     */
    public static String filterTitle(String phrase, String type) {
        int cbi = phrase.indexOf(" (");
        if (cbi >= 0)
            phrase = phrase.substring(0, cbi);

        if (type.equals("Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"))
            phrase = cleanRoad(phrase);

        //in places there are things like: Shaikh_Ibrahim,_Iraq
        int idx;
        if (type.endsWith("Settlement|PopulatedPlace|Place") && (idx = phrase.indexOf(", ")) >= 0)
            phrase = phrase.substring(0, idx);

        boolean allowed = true;
        for (String it : ignoreDBpediaTypes)
            if (type.contains(it)) {
                allowed = false;
                break;
            }
        if (!allowed)
            return null;

        //Do not consider single word names for training, the model has to be more complex than it is right now to handle these
        if (!phrase.contains(" "))
            return null;

        if ((type.endsWith("Person") || type.equals("Agent")) && (phrase.contains(" and ") || phrase.contains(" of ") || phrase.contains(" on ") || phrase.contains(" in ")))
            return null;
        return phrase;
    }

    public static Map<String, String> readDBpedia(double p, String typesFile) {
        if (EmailUtils.dbpedia != null) {
            if (p == 1)
                return EmailUtils.dbpedia;
            else
                return new Util.CaseInsensitiveMap<>(EmailUtils.sample(EmailUtils.dbpedia, p));
        }
        if (typesFile == null)
            typesFile = Config.DBPEDIA_INSTANCE_FILE;
        //dbpedia = new LinkedHashMap<>();
        //we want to be able to access elements in the map in a case-sensitive manner, this is a way to do that.
        EmailUtils.dbpedia = new Util.CaseInsensitiveMap<>();
        int d = 0, numPersons = 0, lines = 0;
        try {
            InputStream is = Config.getResourceAsStream(typesFile);
            if (is == null) {
                EmailUtils.log.warn("DBpedia file resource could not be read!!");
                return EmailUtils.dbpedia;
            }

//true argument for BZip2CompressorInputStream so as to load the whole file content into memory
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new BZip2CompressorInputStream(is, true), "UTF-8"));
            while (true) {
                String line = lnr.readLine();
                if (line == null)
                    break;
                if (lines++ % 1000000 == 0)
                    EmailUtils.log.info("Processed " + lines + " lines of approx. 3.02M in " + typesFile);

                if (line.contains("GivenName"))
                    continue;

                String[] words = line.split("\\s+");
                String r = words[0];

                /**
                 * The types file contains lines like this:
                 * National_Bureau_of_Asian_Research Organisation|Agent
                 * National_Bureau_of_Asian_Research__1 PersonFunction
                 * National_Bureau_of_Asian_Research__2 PersonFunction
                 * Which leads to classifying "National_Bureau_of_Asian_Research" as PersonFunction and not Org.
                 */
                if (r.contains("__")) {
                    d++;
                    continue;
                }
//if it still contains this, is a bad title.
                if (r.equals("") || r.contains("__")) {
                    d++;
                    continue;
                }
                String type = words[1];
                //Royalty names, though tagged person are very weird, contains roman characters and suffixes like of_Poland e.t.c.
                if (type.equals("PersonFunction") || type.equals("Royalty|Person|Agent"))
                    continue;
                //in places there are things like: Shaikh_Ibrahim,_Iraq
                if (type.endsWith("Settlement|PopulatedPlace|Place"))
                    r = r.replaceAll(",_.*", "");

                //its very dangerous to remove things inside brackets as that may lead to terms like
                //University_(Metrorail_Station) MetroStation|Place e.t.c.
                //so keep them, or just skip this entry all together
                //We are not considering single word tokens any way, so its OK to remove things inside the brackets
                //removing stuff in brackets may cause trouble when blind matching entities
                //r = r.replaceAll("_\\(.*?\\)", "");
                String title = r.replaceAll("_", " ");

                String badSuffix = "|Agent";
                if (type.endsWith(badSuffix) && type.length() > badSuffix.length())
                    type = type.substring(0, type.length() - badSuffix.length());
                if (type.endsWith("|Person"))
                    numPersons++;
                type = type.intern(); // type strings are repeated very often, so intern

                if (type.equals("Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place")) {
                    //System.err.print("Cleaned: "+title);
                    title = cleanRoad(title);
                    //System.err.println(" to "+title);
                }
                EmailUtils.dbpedia.put(title, type);
            }
            lnr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        EmailUtils.log.info("Read " + EmailUtils.dbpedia.size() + " names from DBpedia, " + numPersons + " people name. dropped: " + d);

        return new Util.CaseInsensitiveMap<>(EmailUtils.sample(EmailUtils.dbpedia, p));
    }

    public static Map<String, String> readDBpedia() {
        return readDBpedia(1.0, null);
    }
}

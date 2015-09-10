<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="java.io.BufferedReader" %>
<%@ page import="java.io.FileReader" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.File" %>
<%@ page import="edu.stanford.muse.index.IndexUtils" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.SimpleSessions" %><%
    class Some {
        Pair<String, Short> parseLine(String line) {
            line = line.trim();
            String[] fields = line.split(" ::: ");
            if (fields.length < 2) {
                System.err.println("Improper tagging in line: " + line);
                return null;
            }
            Short type = null;
            if ("p".equals(fields[1]))
                type = FeatureDictionary.PERSON;
            else if ("o".equals(fields[1]))
                type = FeatureDictionary.ORGANISATION;
            else if ("l".equals(fields[1]))
                type = FeatureDictionary.PLACE;
            else {
                System.err.println("Unknown tag: " + type + " in " + line);
                return null;
            }

            String cname = IndexUtils.stripExtraSpaces(fields[0]);
            cname = cname.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.|[Pp]rof\\.)\\W+", "");
            return new Pair<String, Short>(cname, type);
        }
    }

    Set<String> docIds = new LinkedHashSet<String>();
    String line = null;
    String dataFldr = System.getProperty("user.home")+File.separator+"epadd-data"+File.separator+"ner-benchmarks"+File.separator+"benchmark-Robert Creeley";
    BufferedReader br = new BufferedReader(new FileReader(dataFldr+File.separator+"docIds.txt"));

    Map<String, Short> bNames = new LinkedHashMap<String, Short>();
    while ((line = br.readLine()) != null) {
        String docId = line.trim();
        try {
            BufferedReader brr = new BufferedReader(new FileReader(new File(dataFldr + File.separator + "docs" +
                    File.separator + docId + ".txt")));
            String eline = null;
            boolean checked = true;
            while ((eline = brr.readLine()) != null) {
                if (eline.startsWith("#"))
                    continue;
                if (!checked)
                    continue;

                Pair<String, Short> entry = new Some().parseLine(eline);
                if (entry != null)
                    bNames.put(entry.getFirst(), entry.getSecond());
            }
            if (checked)
                docIds.add(line);
        }catch(Exception e){
            System.err.println("File: "+dataFldr + File.separator + "docs" + File.separator +docId + ".txt"+" not found");
            e.printStackTrace();
        };
    }
    br.close();

    try {
        //also read missing.txt
        br = new BufferedReader(new FileReader(new File(dataFldr + File.separator + "docs" +
                File.separator+ "missing.txt")));
        line = null;
        while ((line = br.readLine()) != null) {
            Pair<String, Short> entry = new Some().parseLine(line);
            if (entry != null)
                bNames.put(entry.getFirst(), entry.getSecond());
        }
    }catch(Exception e){
        System.err.println("NO missing file?");
        e.printStackTrace();
    }

    System.err.println("Benchmark contains "+docIds.size()+" ids");
    int numPerson = 0, numOrg = 0, numLoc = 0;
    for(String rec: bNames.keySet()) {
        if (FeatureDictionary.PERSON.equals(bNames.get(rec)))
            numPerson++;
        else if(FeatureDictionary.PLACE.equals(bNames.get(rec)))
            numLoc ++;
        else if(FeatureDictionary.ORGANISATION.equals(bNames.get(rec)))
            numOrg++;
    }
    System.err.println("**************\n" +
            "Found "+numPerson+" person entities\n"+
            "Found "+numLoc+" location entities\n"+
            "Found "+numOrg+" org entities");

    String baseDir = System.getProperty("user.home")+File.separator+;
    Archive archive = SimpleSessions.readArchiveIfPresent();
    for(String docId: docIds){
        String content = archive.getContents(doc, true);
        Map<String,Double> some = nerModel.find(content);
        for(String s: some.keySet())
            all.put(s, some.get(s));
        if(i++%1000 == 0)
            out.println("Done: "+i+"/"+docs.size()+"<br>");
    }
    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
    for(Pair<String,Double> p: sall)
        out.println(p.getFirst()+" ::: "+p.getSecond()+"<br>");
%>
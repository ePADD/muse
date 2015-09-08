<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.SimpleSessions" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.model.SVMModel" %>
<%@ page import="java.util.Set" %>
<%@ page import="edu.stanford.muse.index.IndexUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="com.sun.org.apache.xalan.internal.utils.FeatureManager" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %><%
    Archive archive = JSPHelper.getArchive(request.getSession());
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
    String modelFile = mwl + SequenceModel.modelFileName;
    SequenceModel nerModel = (SequenceModel)session.getAttribute("ner");
    if(nerModel == null) {
        System.err.println("Loading model...");
        try {
            nerModel = SequenceModel.loadModel(new File(modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nerModel == null)
            nerModel = SequenceModel.train();
        session.setAttribute("ner", nerModel);
    }

    if (nerModel.fdw == null) {
        try {
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    List<Document> docs = archive.getAllDocs();
    Map<String,Double> all = new LinkedHashMap<>();
    int i=0;
    for(Document doc: docs){
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
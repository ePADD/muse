<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.muse.ner.NEREvaluator" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureUtils" %>
<%@ page import="edu.stanford.nlp.ie.AbstractSequenceClassifier" %>
<%@ page import="edu.stanford.nlp.ie.crf.CRFClassifier" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%
    //Short type = FeatureDictionary.;
    String stype = "PERSON";//"ORGANIZATION";//"PERSON";//"LOCATION";
    String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
    AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);

    NEREvaluator evaluator = new NEREvaluator(10000);
    //List<String> contents = evaluator.getSentences();
    Archive archive = JSPHelper.getArchive(request.getSession());
    List<Document> docs = archive.getAllDocs();
    List<String> contents = new ArrayList<>();
    int i=0;
    contents.toArray(new String[contents.size()]);
    for(Document doc: docs){
        if(i++>510)
            break;
        contents.add(archive.getContents(doc, true));
    }
    Set<String> borgs = new LinkedHashSet<>();//evaluator.bNames.get(type);
    Set<String> orgs = new LinkedHashSet<>();
    int di = 0;
    for (String content: contents) {
        List<List<CoreLabel>> labels = classifier.classify(content);
        for (List<CoreLabel> sentence : labels) {
            String str = "";
            for (CoreLabel word : sentence) {
                String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
                String w = word.word();
                if(!ann.equals(stype)) {
                    if(!str.equals(""))
                        orgs.add(str);
                    str = "";
                }
                else{
                    if(!str.equals(""))
                        str += " "+w;
                    else
                        str = w;
                }
            }
        }
    }

    Set<String> found = new LinkedHashSet<>();
    for(String str: orgs) {
        String color = "";
        if(borgs.contains(str)) {
            color = "red";
            found.add(str);
        }
        out.println("<span style='color:"+color+"'>" + str + "</span><br>");
    }
    out.println("===================<br><br>Missing<br>");
    for(String bo: borgs){
        if(!found.contains(bo))
            out.println(bo+"<br>");
    }
    out.println("#found: "+orgs.size());
    double p = (double)found.size()/orgs.size();
    double r = (double)found.size()/borgs.size();
    double f = 2*p*r/(p+r);
    out.println("Precision: "+p+"<br>");
    out.println("Recall: "+r+"<br>");
    out.println("F1: "+f+"<br>");
%>
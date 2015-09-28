<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.muse.ner.NEREvaluator" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.nlp.ie.AbstractSequenceClassifier" %>
<%@ page import="edu.stanford.nlp.ie.crf.CRFClassifier" %>
<%
    Short type = FeatureDictionary.PLACE;
    String stype = "LOCATION"; // "ORGANIZATION", "PERSON"
    String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
    AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);

    NEREvaluator evaluator = new NEREvaluator(10000);
    List<String> contents = evaluator.getSentences();
    Set<String> borgs = evaluator.bNames.get(type);
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
    double p = (double)found.size()/orgs.size();
    double r = (double)found.size()/borgs.size();
    double f = 2*p*r/(p+r);
    out.println("Precision: "+p+"<br>");
    out.println("Recall: "+r+"<br>");
    out.println("F1: "+f+"<br>");
%>
<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="edu.stanford.muse.util.EmailUtils" %>
<%@ page import="edu.emory.mathcs.backport.java.util.Arrays" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.Triple" %><%
    class Some{
        public String getNotation(String str, Map<String,Map<Short, Pair<Double, Double>>> words){
            List<String> sws = Arrays.asList(new String[]{"for","to","a","the", "an", "and"});
            String[] patt = FeatureDictionary.getPatts(str);
            String pattStr = "";
            String[] tokens = str.split("\\s+");
            int pi = 0;
            for(String p: patt) {
                pi++;
                String w = tokens[pi-1];
                if (!words.containsKey(p)) {
                    pattStr += "0,0,0,:::";
                    continue;
                }

                if (sws.contains(w.toLowerCase()))
                    pattStr += w.toLowerCase();
                else {
                    Map<Short, Pair<Double, Double>> pm = words.get(p);
                    for (Short at : FeatureDictionary.allTypes) {
                        Pair<Double, Double> pair = pm.get(at);
                        if (pair.second == 0) {
                            pattStr += "NULL";
                        }
                        double d = pair.getFirst() / pair.getSecond();
                        pattStr += (int) (d * 10) + ",";
                    }
                }
                pattStr+=":::";
            }
            return pattStr;
        }
    }
    Archive archive = JSPHelper.getArchive(request.getSession());
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
    File f = new File(mwl);
    if(!f.exists())
        f.mkdir();
    f = new File(mwl + "cache");
    if(!f.exists())
        f.mkdir();

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

    try {
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }
//    if(nerModel.dictionary.newWords == null)
//        nerModel.dictionary.computeNewWords();

    String content = "Good Afternoon,\n" +
            "Please share this information with all of your staff who are on the state\n" +
            "payroll. Civil Service will be sending out a letter to employees in February\n" +
            ", which will be too late for the informational seminars being held in\n" +
            "Buffalo in January. Thank you.\n" +
            "\n" +
            "Please be advised that New York State is offering a new long-term care\n" +
            "insurance program for state employees at group rates through MedAmerica. The\n" +
            "program is called the New York State Public Employee and Retiree Long-Term\n" +
            "Care Insurance Plan (NYPERL). This program is available for active employees\n" +
            "and their extended family members, and retirees of New York State.\n" +
            "Long-term care is different from the type of care you would receive in a\n" +
            "hospital. It is not traditional medical care. It is extended care you would\n" +
            "receive at home, in an assisted living or nursing facility, adult day care\n" +
            "or hospice. Long-term care is assistance you need if you are unable to carry\n" +
            "out the basic activities of everyday living. The need could arise from an\n" +
            "accident or a debilitating illness. Or it could simply be the natural result\n" +
            "of aging.\n" +
            "Eligibility\n" +
            "To be eligible to enroll you must meet the eligibility requirements to be\n" +
            "enrolled in health insurance through the New York State Health Insurance\n" +
            "Program.\n" +
            "Open Enrollment Period -No Medical Examination Period Necessary\n" +
            "This one time open enrollment period begins now until May 31, 2002. If you\n" +
            "enroll during the open enrollment period, you are guaranteed enrollment\n" +
            "without a medical examination. Enrollment after May 31, 2002 will require\n" +
            "medical underwriting.\n" +
            "New employees will have an open enrollment for 60 days from date of hire to\n" +
            "enroll without a medical examination.\n" +
            "Extended family members must complete a medical profile and coverage in\n" +
            "NYPERL is determined by MedAmerica.\n" +
            "Payment Method\n" +
            "Payroll deduction will be available effective April 1, 2002. If you enroll\n" +
            "in the program prior to that date, you should choose direct bill, bank\n" +
            "account draft or credit card, and then contact MedAmerica to change to\n" +
            "payroll deduction effective April 1, 2002 if you would prefer payroll\n" +
            "deduction. Please be advised that the payment is not tax-deferred.\n" +
            "Information Seminars\n" +
            "If you would like to attend an informational seminar, you must make a\n" +
            "reservation by calling 1-866-474-5824 or visiting their website at\n" +
            "www.nyperl.net, click on \"Seminar Schedule,\" at least 72 hours prior to the\n" +
            "seminar. They last about one hour. Please advise your supervisor of your\n" +
            "attendance, and if it is during your normal work schedule, you must charge\n" +
            "your absence to vacation, personal or holiday leave.\n" +
            "Please bring a photo ID with you to the seminar, as it may be required for\n" +
            "admittance.\n" +
            "Informational sessions will be held in Buffalo as follows:\n" +
            "January 30, 2002: University at Buffalo, Center for Tomorrow, Service Center\n" +
            "Road, North Campus\n" +
            "February 19, 2002: Mahoney State office Building, 65 Court Street, Hearing\n" +
            "Room Part 1\n" +
            "February 20, 2002: Mahoney State office Building, 65 Court Street, Hearing\n" +
            "Room Part 1\n" +
            "Questions about NYPERL, Information, Rates and Applications\n" +
            "For further information and an enrollment packet, please call MedAmerica\n" +
            "directly at 1-866-474-5824 toll free. Their representatives have the\n" +
            "expertise to assist you with questions and help you complete the enrollment\n" +
            "form. Or you can visit their web site at www.nyperl.net\n" +
            "< http://www.nyperl.net> for information and complete an application online.\n" +
            "Please note that on the application form, at the bottom, you are requested\n" +
            "to complete the following information: \"Agency Name\" which is \"SUNY at\n" +
            "Buffalo\" and the \"Agency 5-digit Code\" which is \"28030\". The form is\n" +
            "returned directly to MedAmerica.\n" +
            "Thank you.\n" +
            "\n" +
            "\n" +
            "Liz\n" +
            "Elizabeth Dundon\n" +
            "Manager, Benefits Administration/Time and Attendance\n" +
            "120 Crofts Hall\n" +
            "SUNY at Buffalo\n" +
            "Buffalo, NY 14260\n" +
            "phone: 716-645-5000 ext.1266\n" +
            "fax: 716-645-3830\n" +
            "ldundon@business.buffalo.edu\n" +
            "web address: www.business.buffalo.edu/hrs/\n" +
            "\n" +
            "List Notes: This is a listserv of all UB employees. This list will be used\n" +
            "intermittently to notify you of issues relevant to your employment at the\n" +
            "University. If you did not receive this message directly, i.e., it was\n" +
            "forwarded to you by a fellow employee, and you would like to be included\n" +
            "on this list please be sure to update/register your email address with the\n" +
            "University Online Directory (LDAP). The membership of this listserv is\n" +
            "periodically updated from employee email addresses listed in LDAP. To\n" +
            "access LDAP please go to: http://ldap.buffalo.edu/query.html Search for\n" +
            "your own listing then follow the directions to update your record.\n" +
            "\n" +
            "If you feel you have received this email in error please send a message to:\n" +
            "Chris Salem at ";
    Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> mapsandoffsets = nerModel.find(content);
    for(Short type: mapsandoffsets.first.keySet())
        out.println(type + " : "+mapsandoffsets.first.get(type)+"<br>");
   // System.err.println(nerModel.find(content, FeatureDictionary.PLACE).keySet());
    nerModel.fdw.close();
    Set<String> set = new LinkedHashSet<>();
    set.add("Y");
    System.err.println(nerModel.dictionary.features.get("foundation").get(FeatureDictionary.ORGANISATION).getLikelihood(set));
    //List<Document> docs = archive.getAllDocs();
//    Map<String,Double> all = new LinkedHashMap<String, Double>();
//    int i=0;
//
//    List<Document> docs = archive.getAllDocs();
//    for(Document doc: docs){
//        String content = archive.getContents(doc, true);
//        Map<String,Double> some = nerModel.find(content, FeatureDictionary.PERSON);
//        for(String s: some.keySet())
//            all.put(s, some.get(s));
//        if(i++%1000 == 0)
//            out.println("Done: "+i+"/"+docs.size()+"<br>");
//    }
//
//    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();
//
//    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
//    for(Pair<String,Double> p: sall) {
//        //String not = new Some().getNotation(p.getFirst(), words);
//        //Pair<Integer, Integer> pair = patts.get(not);
//        out.println(p.getFirst() + " ::: " + p.getSecond()+ "<br>");
//    }
%>
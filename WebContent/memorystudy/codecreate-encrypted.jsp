<%@page import="com.sun.net.httpserver.HttpServer"%>
<%@page import="com.sun.corba.se.spi.orb.StringPair"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="stylesheet" href="css/memoryjsp.css" type="text/css" />
<script src="../js/jquery/jquery.js"></script>
<script src="../js/muse.js"></script>
<link rel="icon" href="images/stanford-favicon.gif">
<title>Finished Signup!</title>
</head>
<body>
<h1 class="title">Approved</h1>
<div class="box">
<!-- Generate code, append to link. Output with a thank you message. -->
<%
File file = new File (System.getProperty("user.home") + File.separator + "codes.txt");

String codesinusepath = System.getProperty("user.home") + File.separator + "codesinuse.txt";
FileEncryptor.decryptFile(codesinusepath, "stanfordmemorystudy");
File codesinuse = new File(System.getProperty("user.home") + File.separator + "codesinuse.txt.decrypted.txt");

File useridfile = new File(System.getProperty("user.home") + File.separator + "userid.txt");

BufferedReader reader = new BufferedReader(new FileReader(file));
BufferedReader codesinusereader = new BufferedReader(new FileReader(codesinuse));
BufferedReader useridreader = new BufferedReader(new FileReader(useridfile));


List<String> codesList = new ArrayList<String>();

List<String> codesinuseList = new ArrayList<String>();
List<String> associatedemailList = new ArrayList<String>();
List<String> genderList = new ArrayList<String>();
List<String> ageList = new ArrayList<String>();
List<String> educationList = new ArrayList<String>();
List<String> professionList = new ArrayList<String>();
List<String> ethnicityList = new ArrayList<String>();
List<String> useridlist = new ArrayList<String>();
String emailid = (String) session.getAttribute("emailid");

String line = null;
while ((line = reader.readLine()) != null)
	codesList.add(line);

String line1 = null;
while ((line1 = codesinusereader.readLine()) != null){
	String[] tokens = line1.split(" ");
	codesinuseList.add(tokens[0]);
	associatedemailList.add(tokens[1]);
	genderList.add(tokens[2]);
	ageList.add(tokens[3]);
	educationList.add(tokens[4]);
	professionList.add(tokens[5]);
	ethnicityList.add(tokens[6]);
	useridlist.add(tokens[7]);
}
codesinusereader.close();

BufferedWriter codeinusewriter = new BufferedWriter(new FileWriter(codesinuse));
for (int j=0; j < codesinuseList.size(); j++){
	String codetowrite = codesinuseList.get(j);
	String emailtowrite = associatedemailList.get(j);
	String gender = genderList.get(j);
	String age = ageList.get(j);
	String education = educationList.get(j);
	String profession = professionList.get(j);
	String ethnicity = ethnicityList.get(j);
	String useridtowrite = useridlist.get(j);
	codeinusewriter.write(codetowrite + " " + emailtowrite+ " " + gender+ " " + age + " " + education + " " + profession + " " + ethnicity + " " + useridtowrite+"\n");
}

String userid = useridreader.readLine();
useridreader.close();

int useridnum = Integer.parseInt(userid);
useridnum = useridnum+1;
String useridtofile = Integer.toString(useridnum);
BufferedWriter useridwriter = new BufferedWriter(new FileWriter(useridfile));
useridwriter.write(useridtofile);
useridwriter.close();

String passkey = codesList.get(0);
String gendernew = (String) session.getAttribute("gender");
String agenew = (String) session.getAttribute("age");
String educationnew = (String) session.getAttribute("education");
String professionnew = (String) session.getAttribute("profession");
String ethnicitynew = (String) session.getAttribute("ethnicity");
//add more user information here.

codeinusewriter.append(passkey+ " "+ emailid + " "+ gendernew+ " " + agenew + " " + educationnew + " " + professionnew + " " + ethnicitynew + " " +userid+ "\n");
codeinusewriter.close();
FileEncryptor.encryptFile(codesinusepath, "stanfordmemorystudy");

codesList.remove(0);

//rewrite codes to the textfile.
BufferedWriter writer = new BufferedWriter(new FileWriter(file));
for (int i=0; i < codesList.size(); i++){
	String code = codesList.get(i);
	writer.write(code + "\n");
}

out.println("Your code is: " + passkey);
reader.close();
writer.close();
%>
<br>
<br>
<br>
You are eligible! Please save this code so that you can take the test. Thank you for signing up!
<br>
<script>
var full = location.href;
var path = full.substring(0, (full.lastIndexOf("/")+1))
path = path + "loginlanding.html";
</script>
Please go to <a name = "continuelink" id="continuelink" href="./loginlanding.html"><script>document.write(path)</script></a> to start the study.
<br>
Other instructions go here:
</div>
</body>
</html>
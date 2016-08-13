<!DOCTYPE html>
<html>
<meta charset="utf-8">
<head>

    <script src="../js/jquery/jquery.js"></script>
    <script src="../js/muse.js"></script>
    <link rel="stylesheet" href="css/memory.css" />
    <link rel="stylesheet" href="../css/fonts.css" type="text/css"/>

    <link rel="icon" href="../images/ashoka-favicon.gif">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>CELL</title>
</head>
<body>
    <div class="box">
        <jsp:include page="header.jspf"/>

        <p>
            This page helps you set up or extend the software underlying CELL to run cognitive tests based on life-logging.
Our software is open access, as is the anonymized data generated by our experiments. It is based on the Muse program that was developed originally at Stanford University.  The setup requires marginal expertise in software, but no software development is needed unless you need to modify the source code. The Java development kit version 8 or above should be pre-installed. 
        </p>

        <p>
 1. Download <a href="http://cs.ashoka.edu.in/cell/muse.war">this war</a> file and deploy it in a Java EE server such as Tomcat version 8 or above.
<p>
2. Run the web server with the following JVM options: -Dencpw=<your password> -Dmuse.mode.server=1 -Xmx2g -Dnobrowseropen=1
<p>
For example, if using Tomcat, set the environment variable JAVA_OPTS to this value, using something like the following: <br/>
export JAVA_OPTS=-Dencpw=PW -Dmuse.mode.server=1 -Xmx2g -Dnobrowseropen=1
<p>
PW is a highly guarded password you provide, using which all results are encrypted.

<p>

3. Set up &lt;home directory&gt;/codes.txt with 1 random (and non-repeating) 7 digit alphanumeric code per line. For example:<br/>
024014x<br/>
054343h<br/>
032947g<br/>

Each test user who passes screening will be issued one of these codes, and this file will be updated as codes are used up.
<p>

4. The results for each individual user will be stored in <home directory>/results, using anonymous ids. For example, after a few users have taken the test, it might look like:
<p>

103/  108/  114/  117/  120/  124/  127/  131/  134/  138/  142/  145/  151/  154/  157/  160/  164/  171/  174/  codes.txt<br/>
104/  110/  115/  118/  121/  125/  129/  132/  135/  140/  143/  147/  152/  155/  158/  161/  166/  172/  175/  <unk>/<br/>
105/  113/  116/  119/  122/  126/  130/  133/  136/  141/  144/  150/  153/  156/  159/  162/  167/  173/  178/  users<br/>

       <p>
5. The users file has user demographic data (anonymized) and each user's directory has a file called results.final with the relevant numbers. All files are encrypted and need to be decrypted with:<p>

jar xvf muse.war muse.jar<br/>
<br/>
java -cp muse.jar -Dencpw=PW edu.stanford.muse.util.CryptoUtils users<br/>
java -cp muse.jar -Dencpw=PW edu.stanford.muse.util.CryptoUtils */results.final<br/>
(where PW is the password provided in step 2).

<hr style="color:rgba(0,0,0,0.2);background-color:rgba(0,0,0,0.2);"/>

If you need to modify the software itself to generate other types of tests based on email or life-logs in general, the source code is available on Github under a permissive Apache open source license. Muse has a lot of infrastructure to ingest and analyze text-based personal digital archives.  Here are the steps to follow to build Muse from source:<p>

1. git clone http://github.com/ePADD/muse.git <br/>
2. cd muse <br/>
3. mvn -f pom-common.xml <br/>
4. mvn <br/>

This will generate a target/muse-1.0.0.snapshot.war which is the same as muse.war above, and can be deployed in a Java EE server.

<p>
You can start from the JSP files in WebContent/memorystudy, which contain the screens shown by CELL.
<p>

In case you have questions or need help, please contact Sudheendra Hangal at hangal@ashoka.edu.in or Abhilasha Kumar at abhilasha.kumar@ashoka.edu.in

</body>
</html>

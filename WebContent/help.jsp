<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<!DOCTYPE html>
<html>
<head><title>Muse Help</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png"/>
</head>
<body class="fixed-width1"> 
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<jsp:include page="header.jsp"/>

<div class="help">
<div style="margin-left:10%;margin-right:10%">
<br/>

For an overview of the goals and ideas behind Muse, please see 
<a href="http://mobisocial.stanford.edu/news/2011/10/muse-reviving-memories-with-email-archives/">this blog post</a> or our
 <a href="http://mobisocial.stanford.edu/muse/muse-papers.html">technical papers page</a>.
<p>
Muse help is provided as a FAQ, divided into the following sections: <br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#email">Using Muse</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#lens">Browsing Lens</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#formats">Formats and Accounts</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#email">Using Muse</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#requirements">Starting and Stopping Muse</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#privacy">Security and privacy</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#algorithms">Muse algorithms</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#problems">Troubleshooting</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#developers">For Developers</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#about">About</a><br/>

<a name="email"></a>
<h2>Using Muse</h2>
<p>
<span class="question">
What can I do with Muse?
</span>
<p>
<span class="answer">
Muse lets you look back and reflect on your past by allowing you to quickly browse a large email archive and 
helps you spot possibly interesting messages and patterns in the archive. For example:<br/>
	<br/> &bull; <b>The sentiment graph</b> displays messages across time that may reflect interesting emotions and feelings. 
	Sentiments are inferred by matching messages with a pre-built lexicon. (<a href="#sentiments">More details</a>)
	<p>	<a name="groups"></a>
	&bull; Communication graphs with <b>automatically inferred groups</b> reflect how you have communicated over time with various groups in your email network. 
		If you need to tune the set of inferred groups, see the <a href="#groups-editor">Refine Groups</a> option. 
	</p><p> &bull; The <b>calendar view</b> displays important terms in your email per month, as identified by statistical analysis. Click on any term to launch a new tab
	with messages containing that term. The terms are color coded according to the group with which they are most closely associated.
	</p><p> &bull; The <b>attachment wall</b> displays all your image attachments. Drag to pan, Scroll to zoom, and click to select a picture. 
	When a picture is selected, you can use the breakout link at the top right of the image to launch a message view containing all messages associated with that attachment. 
	Non-image attachments are simply listed below the attachment wall, and can be saved or opened using the platform's native application.
</p>
<p>
Once these cues have pointed you to a collection of messages, you can explore them in a <b>message view</b>.
You can rapidly skim all the messages in a view using a <b>jog dial</b> interface. (<a href="#message-view">More details</a>)

<p>
To <b>save your current session</b>, click on "save session" from the summary page. 
When you run Muse subsequently, you can directly reach the state of the saved session by selecting it from the login page,
without having to wait very long. (Note: Muse versions before v0.9.8 used a different session format, which is not compatible with previous versions.)
</p>

<a name="sentiments"></a>
<span class="question">How does sentiment analysis work?</span>
</p>
<p>
Muse has a sentiment lexicon containing categories of words or phrases that may reflect potentially interesting events and emotions in personal communications.
When you click on a layer in the graph, all the messages with
words matching that sentiment in the lexicon are displayed in a message view below the graph. Words that are related to the
current sentiment are highlighted. The words for each sentiment category are also displayed if you hover above the name
of the sentiment in the facets panel. Note that a single message may match multiple categories. 
</p>
<p>
By clicking on "Edit Lexicon", you can add, remove or refine categories. 
If your edits are generally applicable, please submit them back to Stanford so we can include them by default
in future revisions of Muse.
</p>
<p>
<a name="facets-panel"></a>
<span class="question">How do I use the facets panel?</span>
<p>
<span class="answer">
The <b>facets panel</b> on the left can be used to turn on or off sentiment, group, people, folder and direction filters 
for the messages within the view. The sentiments that are active in the facets panel are ANDed to collect the messages in the view. 
When multiple values are active within a facet type, they are ORed, (i.e. the messages selected reflect either of the values),
with the exception of sentiment facets which are ANDed. This is because multiple sentiments can apply to the same message, unlike
the other facets. For example, if two sentiments are active, messages with both sentiments are selected for the view. If
two folders are active, messages from either folder are selected. You can 
activate or inactivate a facet by clicking on it. The number in parantheses indicates the number of messages
in the current view that reflect the corresponding facet.
</span>
<p>

<a name="message-view"></a>
<span class="question">How do I navigate the messages in a view?</span>
<p>
Summon or dismiss the jog dial by clicking anywhere in the message view.
Messages in the view are ordered chronologically, and turning the dial clockwise moves forward in time; turning it counter-clockwise moves backward. 
You can also use the left and right arrow keys, or use Tab/Shift-tab to move by month.

<p>

Inside a message view, you can always click on a hyperlinked term, or the name 
of the sender or the month or year in the date field to launch a new view with the associated messages. 
You can always open a link to a new view in a different browser tab or window to keep multiple views active.

<p>
<a name="groups-editor"></a>
<span class="question">How can I edit the set of groups automatically inferred by Muse?</span>
<p>
<span class="answer">
Click on the <b>Refine Groups link</b> to enter the groups editor. In the editor, you can 
hover on a group to display a menu at the top right corner, which lets you <b>clone, delete or merge entire groups</b>.
<b>To merge two groups</b>, click the merge icon on the first group (which then acquires a highlighted background and a dashed border)
and then click on the merge icon for the second group. To unselect the first group for merging, click on its merge icon again.
<p>
<b>To name a group</b>, type the name in its header (only multi-person groups have group names).
Until you give a group a name, Muse assigns it a default name which looks something like this: &lt;first member&gt; + &lt;the number of other members in it&gt;.
<p>
Hovering on a name in the editor will let you see all the names and email addresses associated with that individual. It also bring up a person menu that lets
you <b>delete or clone individual names</b>. You can also <b>drag and drop names between groups</b>; groups that become empty as a result are automatically deleted.
To <b>copy a name into another group</b>, clone the name, and drag the clone 
to the other group. To create an entirely new group, clone an existing group, add the new members, and delete the original members.

While editing, names retain their original colors to help you track your edits. All edits are automatically saved, and cannot be undone,
so edit with care. Click on <b>Refresh Colors</b> once you are done editing, and all the groups and elements will be assigned a consistent color.
<p>
Note that Muse <a href="#ER">merges different identities</a> that may belong to the same person (see below).
In the groups editor, only one representative name (or email address, if no name is available) is displayed for a person.
</p>
</span>

<p>
<a name="groups-editor"></a>
<span class="question">Can I save and reload groups after editing?</span>
<p>
<span class="answer">
Yes, click on the <b>Save Grouping</b> link to save a particular grouping with a name. 
If you have saved any groupings earlier, you will also see a <b>Load Grouping</b> option.
You can <b>delete groupings</b> from the <a href="settings">settings</a> page.
When you save an entire session, the session also saves its current grouping, 
which is restored when it is reloaded.
<p>
Keep in mind that the automatically derived groups come from
analysis of specified message folders. When you change groupings, the new grouping 
may or may not be relevant for the folders currently being analyzed, since they may be 
different from the ones originally used for the grouping.
</p>
</span>

<a name="ER"></a>
<p>
<span class="question">How does Muse handle cases where a single person has different names or email addresses?</span>
</p>
<p>
<span class="answer">
Muse merges identities that have the same &quot;RFC-822&quot; name (this is the name
that appears in parantheses alongside the email address), and treats them as the same person. It will also apply some intelligent
guesses, for example, the name "Barack Obama" will be treated the same as "Obama, Barack" and an email address like john.doe@example.com
will automatically infer the name "John Doe", even if the email address does not have a name associated with it.
In most places in the Muse interface,  you can hover on a name or email address to see a list of all email addresses and names associated with that person.
<p>
In case you need to edit the address book that Muse generates after resolving identities, you can do so under Settings &rarr; Archive management.

</p>
</span>
</p>

<p>
<span class="question">Does Muse process just the original content in an email, or the quoted parts of other messages too? </span>
</p>
<p>
<span class="answer">Muse processes all the content in each email. However, the monthly summaries that it generates only use the original text in each message.
</span>
</p>
<p>
<span class="question">Can I use Muse to copy all the attachments from emails as files?</span>
</p>
<p>
Yes, if you're running Muse locally, just go to the attachments page, and below the piclens wall,
you'll see a message saying "Attachment files are stored in the folder...". You can copy
the files from this folder. Because multiple attachments may have the same name, Muse gives them
a unique numeric prefix. This folder contains all attachments from emails processed with a given base folder. 
<p>
Use the settings page to set different base folders if you want to separate attachments from disparate datasets.
</p>
</span>


<span class="question">How do I use filters?</span>
</p>
<p>
Filters affect all Muse operations until they are removed. If you set a filter, it will
restrict the set of messages considered by Muse for the purpose of showing monthly summaries,
sentiment graphs, attachments, etc. to the ones matching the filter. 

For the search term field in the filter, regular expression search can be 
invoked on by enclosing the query in //. For example, the search term <i>/Ham.*t/</i> will match the word <i>Hamlet</i>.
Regular expression search cannot cross word boundaries.

</span>
<hr/>
<a name="lens"></a>
<h2>The browsing lens</h2>
(Note: this is a new feature and actively under test!)
<p>
<span class="question">What is the purpose of the browsing lens?</span>
<p>
<span class="answer">
The browsing lens acts like an extended brain that carries your entire personal archive in its memory. While browsing the web,
enabling the lens identifies the names on the page that also appear in your archives and inserts
yellow highlights under these names. The lens currently uses two kinds of highlights: strong and soft, based on a scoring policy. 
It also inserts a callout at the bottom of the page, listing high scoring terms. Click on a term in the callout to 
scroll the page to its first occurrence. Hover over a highlighted term to see a list of people you know who 
are most closely connected to that term.
</span>
<p>
<span class="question">When is the browsing lens useful?</span>
<p>
<span class="answer">
You tell us when its useful to you! We ourselves find it useful in many settings, for example, to personalize
news sites, peruse conference listings, or skim large documents. In general, any page that has a lot of text to read
manually can use the kind of automation the lens brings to browsing and reading.

<p>
<span class="question">Why are some irrelevant or noisy terms highlighted? Can I remove noisy terms from the highlights?</span>
<p>
<span class="answer">
Our scoring algorithm is not perfect -- it merely looks at the frequency of occurrence of a term, and cannot make out
when a highlighted term is actually interesting to you. Think of the lens like a personal assistant who needs some training to 
understand your unique preferences. To help this assistant help you, hover over a term at the callout at the bottom of the page, 
and vote it up or down. Voting a term down ensures that the lens will not highlight the term any more on any site. 
Voting a term up makes it more likely to be strongly highlighted in the future.
</span>

<p>
<span class="question">Can I see the messages containing the highlighted term?</span>
<p>
<span class="answer">
Yes, just click on a highlighted term to see a popup listing headers for messages in your archive containing the term.
You can click through from the popup to the actual messages themselves in a new tab. These messages are ordered chronologically.
</span>

<p>
<span class="question">However, the highlight is on a term that is a hyperlink. How can I follow the link instead of seeing the popup?</span>
<p>
<span class="answer">
Use Shift-click instead of click for the original behavior of the link. 
</span>

<p>
<span class="question">Can I run the lens on my own text that is not on a web page?</span>
<p>
<span class="answer">
Sure, you can provide your own text <a href="archivist-tools">on this page</a>, and Muse will reflect the text
back to you. Enable the lens to highlight relevant portions.
</span>
<p>
<span class="question">What are the red underlines that the lens also inserts on the page?</span>
<p>
<span class="answer">
The red underlines are just a debug feature indicating terms that were identified as names on the page, and checked in the index.
If a term is not highlighted but red-underlined, that means the term was looked up in your archive, but did not hit. If the term
is neither highlighted nor red-underlined, the term was not recognized as a name. To control noise and improve performance, 
the Muse lens currently only looks up named entities on the page (names of people, places, organizations, etc.). Please contact
us if this is inadequate for you, and we will try to add features that handle your use cases.
</span>

<p>
<span class="question">How do I enable the lens?</span>
<p>
<span class="answer">
You can either install a Chrome or Firefox extension that will automatically run highlighting on every page that you visit,
or install a bookmarklet for any browser that will run the lens only when the bookmarklet is clicked. See <a href="lens.jsp">this page</a>
for installation details.
</span>

<p>
<span class="question">Are there any limitations on which pages the lens works on?</span>
<p>
<span class="answer">
The lens may not work on pages that make heavy use of Javascript (such as Gmail). Support for pages with frames is 
not tested extensively at this time. In addition, if a page dynamically updates its contents using Javascript, you
will need to use the lens bookmarklet to re-highlight the page.
</span>

<hr/>

<a name="formats"></a>
<h2>Formats and accounts</h2>
<p>
<span class="question">How can I Muse to read email stored on my hard drive?</span>
<p>
<span class="answer">

Muse can read files stored on your hard drive in the "mbox" format. However, it cannot directly read Outlook's PST files, 
which are based on a proprietary Microsoft format. 
<p>
<b>For long-term archival, we highly recommend that you store your email archives in open and simple formats like mbox. </b></span> 
There is no certainty that you will be able to read PST &ndash; or any other proprietary format &ndash; in a few years, 
while it is highly likely that there will be free converters to and from the mbox format, versions of which have been around for 30 years or more.
In the worst case, mbox files can be viewed even in a simple text editor. 
</p>
<p>
If you need to convert email from different formats (like Outlook PST) to mbox, several commercial programs are 
available. Although we do not endorse any programs, some of our users have recommended Emailchemy and Mailstore Home 5 for translation. 

You can also try to install <a href="http://www.mozillamessaging.com/">Mozilla Thunderbird</a> (a cousin of the Firefox browser)
and/or use any of <a href="http://kb.mozillazine.org/Import_.pst_files">these methods</a> to convert PST files to mbox format. 

In addition, if your email has attachments in old or hard to read formats, you may find 
a tool such as Quickview Plus useful. Please let us know if you 
recommend any other free or commercial tools for dealing with different formats.
</p>

<p>
The format for Eudora archives is close to (but not identical with) mbox; in this case, 
a small script can be used to convert Eudora to mbox.  If you have Eudora archives, please contact us and 
we will try to help you out.
</p>

<p>
<a name="hotmail"></a>
<span class="question">What if I have a Gmail, Yahoo, Hotmail, &lt;other public email provider&gt; account?</span>
</p>
<p>
If you have Gmail or Google apps account, you can use Oauth to authenticate. For Yahoo or Hotmail, just login with your email address and password. 
A full list of supported public email providers is <a href="https://autoconfig.thunderbird.net/v1.1/">here</a>. 
Muse uses this list from Mozilla Thunderbird's auto-configuration database.
<p>
If you have trouble with any of these providers, a good option is to use 
<a href="http://mail.google.com/support/bin/static.py?page=guide.cs&guide=25413&topic=25414">Gmail's importers</a>
to first copy your email folders and messages into a Gmail account.  This process assigns imported messages two tags:
the name of the message's folder in the original account, and the email address of the original account.
The import process leaves your original account unchanged. It may take a few hours to complete.
</p>

<p>
<span class="question">Can I Muse with my company or university server?</span>
</p>
<p>
<span class="answer">

Typically, yes -- because these servers are configured to support the IMAP
protocol. Looking at the account setup of your email client should tell you if
it is using IMAP, and the name of your email server. Ask your email administrator if you have questions.
<p>
Some Microsoft Exchange servers need to be explicitly configured to enable IMAP. If you access the server with
a non-Outlook desktop client (like Mozilla Thunderbird), the account probably already supports IMAP, and will work with Muse.  
Please let us know if you have problems with an Exchange server, since Muse is currently not well tested with Exchange.

</span> </p>

<p>
<span class="question">How can I get messages from a mail account configured in Apple Mail?</span>
<p>
<span class="answer">
On Mac OS X 10.7 or later, you can select one or more folder names, and select "export messages" from the context menu (control-click). 
This allows you to export the mail folder as an mbox file. You can then feed the path to the mbox file to Muse.

<p>
<span class="question">Can I use Muse on multiple accounts simultaneously?</span>
</p>
<p>
Yes, just enter login or mbox information for multiple sources of email by clicking on the little + button below the account information on the login page.
<p>
Even if your messages are duplicated across folders or accounts, Muse will detect duplicates and ignore them.
</p>
 
<p>
<span class="question">How can I get Muse to process my chat messages?</span>
<p>
Muse can process saved Gmail chats because they are exported via IMAP under the folder [Gmail]/Chats.
You may have to enable IMAP access to this folder. See <a href="http://www.readwriteweb.com/archives/google_liberates_gmail_chat_logs_via_imap.php">this article</a>.
</p> 

<p>
<span class="question">How can I get Muse to process messages from a mailing lists?</span>
<p>
Muse has preliminary support for mailing lists from the Mailman program.  
On the Mailman page for the list archive, save the messages for each month as flat text files, 
which are in something close to the mbox format. 
Just point Muse to the directory these files are saved in. You can also concatenate all the files into a single file.
Current limitations are that sender names are not exported by Mailman, so Muse will show empty sender and receiver names.
Mailman also does not export attachments, so they cannot be viewed on the attachment wall, 
though the messages themselves have a link to the attachment.
 
</p> 
<hr/>

<a name="using"></a>
<h2>Setting up Muse</h2>
<p>
<span class="question">Which email folders does Muse analyze? </span>
</p>
<p>
<span class="answer">By default, Muse processes your Sent mail folder on accounts with standard email providers like Gmail and Yahoo. 
For other accounts, or if you uncheck the "Sent messages" checkbox on the login screen, you can select
the folders yourself. Users often prefer to analyze their sent message folders first, because it avoids the problem of spam,
inconsistent folder-archiving practices, etc, and because it is automatically archived, leading to a detailed and consistent 
life-log. However, if there are special folders that hold email important for you, please include them.
</span>
</p>

<p>
<span class="question">Can I restrict Muse to analyzing only some messages within a folder?</span>
</p>

<p>
<span class="answer">Yes: apart from selecting specific folders, you can specify filters in the Advanced controls panel. 
You can tell Muse to process only messages sent by you, or messages within a certain date range or that involve a specific person.</span>
</p>

<p>
<span class="question">How can I speed up Muse?</span>
</p>

<span class="answer">
If you're going to run Muse several times over a large archive, we recommend you install Thunderbird and copy over the message folders to Local Folders first.
If possible, always use a local server (like your university or company account) rather than a remote server like Gmail (which can be up to 10 times slower).
You should try out each feature with a small folder first... Gmail/All Mail is not the best folder to start with.
</span>


<p>
<span class="question">What languages are supported?</span>
</p>
<p>
Muse can process email in all languages using character sets encoded with the UTF-8 encoding.
Other character encodings may work, but if you have trouble, please let us know.

Features of Muse that depend on linguistic processing, including sentiment detection currently work best only 
for English. (We welcome help in developing lexicons for other languages.)
</p>

<p>
<span class="question">What's the .muse directory?</span>
</p>
<p>
<span class="answer">
The .muse directory is a cache of message headers, text and attachments that Muse has processed once. This is an optimization to reduce fetch time when Muse has to process messages that its already seen.
However, you can always delete this directory or choose Settings &rarr; Clear cache in Muse (or choose the option when prompted at Logout), which has the same effect. We recommend you clear the cache if you are not going
to run Muse again in the near future.
</span>
</p>

<hr/>
<a name="requirements"></a>
<h2>Starting and stopping Muse</h2>

<p>
<span class="question">What do I need to run Muse?</span>
<p>
Most people prefer to download and run Muse on their own computers. Your system should have a minimum of 1GB of memory. 
You need to have installed a recent versions of Firefox, Chrome, Safari or Opera. 
Most features might work on IE 9, but this is not well tested. 
Muse cannot run on IE 8 and below, due to the lack of SVG support in these versions.
</p>

<p>
<span class="question">How do I stop running Muse?</span>
</p>
<p>
<span class="answer">
By default, the Muse application will stop running 24 hours after it has been started. 
Note that just exiting the browser window connected to Muse does not stop the real Muse application that is running in the background. 
If you need to terminate it yourself: use the menu on the Muse icon in the task bar.
Switch to the application and type Alt-F4, or kill the javaw process from the task manager (Ctrl-shift-esc). 
</span>
</p>

<p>
<span class="question">Why is Muse not starting on my computer?</span>
</p>
<p>
<span class="answer">Please see the troubleshooting section below.</span>
</p>

<hr/>

<a name="algorithms"></a>
<h2>Muse algorithms</h2>
<a name="groups-algorithm"></a>
<p>
<span class="question">How does Muse derive its groups?</span>
<span class="answer">
<p>
Muse has an automatic grouping algorithm that identifies your top N (default 20) groups (<a href="#groups-algorithm">more details</a>). 
The top groups may be overlapping or nested, and may even be individuals. 
	Muse detects groups automatically by considering several factors. 
	It considers co-recipiency in messages (see <a href="http://cs.stanford.edu/~hangal/snakdd11.pdf">this technical paper</a> for details), 
	and how long you've known a person (people in long-lasting relationships are considered more important to represent in the groups).
	It also tries to spread the coverage of groups to ensure that important people are reflected in some group. 
	You can set the number of groups inferred from the advanced options. Note that if you select more than 20 groups, 
	colors assigned to the groups may be rotated.
</span>
</p>

<p>
<span class="question">How does Muse use these groups?</span>
<span class="answer">
<p>
Muse groups people primarily as a way to organize the message collection.
These groups are used in multiple places in the Muse interface: 1) To see the amount of communication with different
groups over time.  2) To color-code terms in the monthly calendar view, so you can quickly scan terms related
to a particular group. You can also export messages for a particular group by clicking on its layer in the 
group communication graph. 

For considering which group to assign a message to, Muse considers similarity of the group members to the people
associated with the message. It assigns each message to the group to which the message recipients are most similar. 
Ties are broken arbitrarily, and a message is left unassigned if it does not share a person with any group.

</span>
</p>


<hr/>
<a name="privacy"></a>
<h2>Security and Privacy</h2>
<p>
<span class="question">How do I know my private email is safe?</span>
<span class="answer">
<p>
We are very aware that email is extremely private and sensitive. Therefore Muse takes a lot of security precautions.
<p>

Your password is never saved or logged by Muse.
<p>
We recommend launching Muse on your own computer, so that no other computer or person
ever has access to your messages. This requires Java (1.6 or higher), which should already
be installed on most computers.
All communication between the Muse server and your email server is SSL-encrypted (using IMAPS/POPS).
<p>
Please use our secure server only in case you have technical problems running Muse on your computer. 
If you do this, you should use a secure (HTTPS) URL to connect to the server. When 
you are running Muse on your own machine its fine to connect to a plain http://localhost URL.
<p>
And if you are still paranoid about security, you could run Muse on mbox
files on a physical or virtual machine with the network switched off. Wait for the
browser page to launch a login screen, then switch the network off, and use
the local folders option. Never use the VM again.
<p>
If you can recommend any further steps to address privacy concerns, please let us know.
We are happy to implement them.
</p>
</span>

<span class="question">Does Muse modify my email in any way?</span>
<span class="answer">
<p>
It does not. It processes all email in read-only mode, so the original
messages will not change.
</span>
</p>
<hr/>

<a name="problems"></a>
<h2>Troubleshooting</h2>
<p>
<span class="question">Muse can't connect to my Gmail or other server...</span>
<p>
Sometimes firewalls or other network configurations prevent Muse from reaching the email server.
Verify your network setup by clicking on Check Setup on the <a href="settings">settings page</a>. This conducts a test to
confirm that your computer can talk with Gmail.
<p>
<span class="question">I don't see folders with just one message...</span>
<p>
Some email servers report non-mail folders as containing one message, so Muse ignores any folder with just one message.
In general, IMAP servers are notoriously idiosyncratic. If you are trying out a new IMAP server,
please be patient and report any bugs to us.

<p>
<span class="question">I can't launch Muse on my own machine...</span>
<p>
Some things to try:<br/>
1. Try downloading the standalone jar version (see <a href="http://mobisocial.stanford.edu/muse/standalone.html">this</a> page).<br/>
2. Try running javaws /path/to/muse.jnlp. <br/>
3. Click on the "Report an error" link at the bottom of each page, examine the error report and submit it if it does
not contain sensitive information.<br/>
4. For other kinds of debugging, you can enable the Java Webstart console window
by running javaws -viewer and enabling "show console" under the Advanced tab. It is always useful to clear the Java webstart cache and retry.

<p>
<span class="question">I can't see a particular folder in my email account, or the message count is incorrect.</span>
<p>
Make sure you can see the folder in an email client like Thunderbird. The folders and message
counts reported should be exactly the same as Thunderbird. If there's a discrepancy, we'd love to hear from you!

<p>
<span class="question">I can't access my Gmail even though the password is correct...</span>
<p>
Try <a href="http://www.google.com/accounts/DisplayUnlockCaptcha">unlocking your account</a>.

<p>
<span class="question">I don't see Gmail/All Mail and other folders...</span>
<p>
Make sure you turn off IMAP Advanced controls under Google Labs if you have it turned on.

<p>
<span class="question">Muse is stuck in the detecting groups phase...</span>
</p>
<p>
Grouping people normally takes under 30 seconds. If it takes longer, a common reason 
is that you didn't specify all of your alternate email addresses on the login screen.
Muse needs this to figure out which email is incoming and which is outgoing.
Though Muse will try to guess which email addresses are yours, its better if you specify all the email addresses that you have had. 
</p>

<p>
<span class="question">I have other problems... </span>
</p>
<p>
Here are some things to try:

<p>
On the <a href="settings">settings page</a>, select Clear Cache. This reinitializes the Muse cache. <br/>
From the same page, check out the <a href="dataReport">data quality report</a>, which reports bad or inconsistent data. Muse can hit errors are due to bad input data.<br/>
Clear your browser cache, especially if you have used an older version of Muse before.<br/>
Clear your Java Webstart cache, especially if you have used an older version of Muse before.<br/>
Check your browser version and make sure it is relatively recent. Occasionally, the default system browser that Muse launches is an outdated version. If this happens, simply launch a new browser and go to the same URL as in the old browser.<br/>
<p>If you are unsure about doing any of the above, or your problem is still not fixed, please report it to us.</p>
</p>
<hr/>
<a name="developers"></a>
<h2>For developers</h2>

<p>
<span class="question">Can I design more themes for Muse?</span>
</p>
<p>
Absolutely. We welcome people who want to create new themes for Muse, and will give you credit for it. Please <a href="feedback">contact us</a>.
</p>

<p>
<span class="question">How is Muse implemented?</span>
</p>
<p>
Muse uses various Java components, including the <a href="http://www.oracle.com/technetwork/java/javamail/index.html">JavaMail API</a>, 
the <a href="http://wiki.modularity.net.au/mstor/index.php?title=Main_Page">mstor</a> mailbox interface by Ben Fortuna, various Apache libraries including the Lucene indexing engine,
and the <a href="http://opennlp.apache.org/">Open NLP</a> toolkit (<a href="LICENSES.txt">Licenses</a>). We use <a href="http://jquery.org">Jquery</a>, <a href="http://vis.stanford.edu/protovis">Protovis</a>,
Jgrowl and FancyBox, and other Javascript libraries for some user interface components. 
Some graphics are used with permission from <a href="http://psdgraphics.com">PSD Graphics</a>; see <a href="images/CREDITS.txt">CREDITS</a> for a full list of graphics credits.
</p>

<p>
<span class="question">Can I use the Muse infrastrucure for my own email analysis?</span>
</p>

You are welcome to -- please contact us. 
The Muse infrastructure is fairly re-usable and provides support for login, caching, attachments, address book and 
entity resolution, automatic grouping, text indexing, and other functions.
</p>

<hr/>
<a name="about"></a>
<h2>About</h2>
<p>
Muse is a research project in the <a href="http://mobisocial.stanford.edu">Mobisocial</a> computing laboratory at Stanford.
The Muse team consists of 
<a href="http://cs.stanford.edu/~hangal">Sudheendra Hangal</a>, 
<a href="http://www.linkedin.com/pub/chaiyasit-sit-manovit/1/57a/900">Sit Manovit</a>,
<a href="http://www.stanford.edu/~pchan3">Peter Chan</a>, 
<a href="http://www.stanford.edu/~abhinay/">Abhinay Nagpal</a>, 
<a href="http://www.linkedin.com/in/dianlynnmaclean">Diana MacLean</a>, 
<a href="http://cargocollective.com/cindy">Cindy Chang</a>, 
<a href="http://cs.stanford.edu/~lam">Monica Lam</a> 
and <a href="http://jheer.org">Jeffrey Heer</a>. 
The research is funded by an NSF POMI (Programmable Open Mobile Internet) 2020 Expedition Grant and
the Stanford MobiSocial Lab.
</p>

<p>&nbsp;</p>
</div>
</div>
<jsp:include page="footer.jsp"/>
</body>
</html>

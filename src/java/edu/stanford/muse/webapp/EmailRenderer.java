package edu.stanford.muse.webapp;

import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.index.*;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.*;

/** This class has util methods to display an email message in an html page */

public class EmailRenderer {

	static final int	TEXT_WRAP_WIDTH	= 80;	// used to be 80, but that wraps
												// around too soon. 120 is too
												// much with courier font.

    public static Pair<DataSet, String> pagesForDocuments(Collection<Document> ds, Archive archive, String datasetTitle,
                                                          Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed)
            throws Exception{
        return pagesForDocuments(ds, archive, datasetTitle, null, highlightTermsStemmed, highlightTermsUnstemmed, null, MultiDoc.ClusteringType.MONTHLY);
    }

    public static Pair<DataSet, String> pagesForDocuments(Collection<Document> ds, Archive archive, String datasetTitle,
                                                          Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed, Collection<Blob> highlightAttachments)
            throws Exception{
        return pagesForDocuments(ds, archive, datasetTitle, null, highlightTermsStemmed, highlightTermsUnstemmed, highlightAttachments, MultiDoc.ClusteringType.MONTHLY);
    }

    public static Pair<DataSet, String> pagesForDocuments(Collection<Document> ds, Archive archive, String datasetTitle,
														  Set<Integer> highlightContactIds, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed)
			throws Exception{
		return pagesForDocuments(ds, archive, datasetTitle, highlightContactIds, highlightTermsStemmed, highlightTermsUnstemmed, null, MultiDoc.ClusteringType.MONTHLY);
	}

	public static Pair<DataSet, String> pagesForDocuments(Collection<Document> ds, Archive archive, String datasetTitle,
                                                          Set<Integer> highlightContactIds, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed, Collection<Blob> highlightAttachments)
            throws Exception{
        return pagesForDocuments(ds, archive, datasetTitle, highlightContactIds, highlightTermsStemmed, highlightTermsUnstemmed, highlightAttachments, MultiDoc.ClusteringType.MONTHLY);
    }

	/*
	 * returns pages and html for a collection of docs, which can be put into a
	 * jog frame. indexer clusters are used to
	 *
	 * Changed the first arg type from: Collection<? extends EmailDocument> to Collection<Document>, as we get Collection<Document> in browse page or from docsforquery, its a hassle to make them all return EmailDocument
	 * especially when no other document type is used anywhere
	 */
	public static Pair<DataSet, String> pagesForDocuments(Collection<Document> ds, Archive archive, String datasetTitle,
			Set<Integer> highlightContactIds, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed, Collection<Blob> highlightAttachments, MultiDoc.ClusteringType coptions)
			throws Exception
	{
		StringBuilder html = new StringBuilder();
		int pageNum = 0;
		List<String> pages = new ArrayList<String>();

		// need clusters which map to sections in the browsing interface
		List<MultiDoc> clusters;

        // indexer may or may not have indexed all the docs in ds
		// if it has, use its clustering (could be yearly or monthly or category
		// wise)
		// if (indexer != null && indexer.clustersIncludeAllDocs(ds))
		// if (indexer != null)
		clusters = archive.clustersForDocs(ds, coptions);
		/*
		 * else { // categorize by month if the docs have dates if
		 * (EmailUtils.allDocsAreDatedDocs(ds)) clusters =
		 * IndexUtils.partitionDocsByInterval(new ArrayList<DatedDocument>((Set)
		 * ds), true); else // must be category docs clusters =
		 * CategoryDocument.clustersDocsByCategoryName((Collection) ds); }
		 */

		List<Document> datasetDocs = new ArrayList<>();

		// we build up a hierarchy of <section, document, page>
		for (MultiDoc md : clusters)
		{
			if (md.docs.size() == 0)
				continue;

			String description = md.description;
			description = description.replace("\"", "\\\""); // escape a double
																// quote if any
																// in the
																// description
			html.append("<div class=\"section\" name=\"" + description + "\">\n");

			List<List<String>> clusterResult = new ArrayList<List<String>>();

			for (Document d : md.docs)
			{
				String pdfAttrib = "";
				/*
				 * if (d instanceof PDFDocument) pdfAttrib = "pdfLink=\"" +
				 * ((PDFDocument) d).relativeURLForPDF + "\"";
				 */
				html.append("<div class=\"document\" " + pdfAttrib + ">\n");

				datasetDocs.add(d);
				pages.add(null);
				clusterResult.add(null);
				// clusterResult.add(docPageList);
				// for (String s: docPageList)
				{
					String comment = Util.escapeHTML(d.comment);
					html.append("<div class=\"page\"");
					if (!Util.nullOrEmpty(comment))
						html.append(" comment=\"" + comment + "\"");

					if (!Util.nullOrEmpty(comment) && (d instanceof EmailDocument))
					{
						String messageId = d.getUniqueId();
						html.append(" messageID=\"" + messageId + "\"");
					}

					if (d.isLiked())
						html.append(" liked=\"true\"");
					if (d instanceof EmailDocument && ((EmailDocument) d).doNotTransfer)
						html.append(" doNotTransfer=\"true\"");
					if (d instanceof EmailDocument && ((EmailDocument) d).transferWithRestrictions)
						html.append(" transferWithRestrictions=\"true\"");
					if (d instanceof EmailDocument && ((EmailDocument) d).reviewed)
						html.append(" reviewed=\"true\"");
					if (d instanceof EmailDocument && ((EmailDocument) d).addedToCart)
						html.append(" addToCart=\"true\"");
					html.append(" pageId='" + pageNum++ + "' docId='" + d.getUniqueId() + "'></div>\n");
				}

				html.append("</div>"); // document
			}
			html.append("</div>\n"); // section
		}

		DataSet dataset = new DataSet(datasetDocs, archive, datasetTitle, highlightContactIds, highlightTermsStemmed, highlightTermsUnstemmed, highlightAttachments);

		return new Pair<>(dataset, html.toString());
	}

	/**
	 * format given addresses as comma separated html, linewrap after given
	 * number of chars
	 * 
	 * @param addressBook
	 */
	public static String formatAddressesAsHTML(Address addrs[], AddressBook addressBook, int lineWrap, Set<String> highlightUnstemmed, Set<String> highlightNames, Set<String> highlightAddresses)
	{
		StringBuilder sb = new StringBuilder();
		int outputLineLength = 0;
		for (int i = 0; i < addrs.length; i++)
		{
			String thisAddrStr;

			Address a = addrs[i];
			if (a instanceof InternetAddress)
			{
				InternetAddress ia = (InternetAddress) a;
				Pair<String, String> p = JSPHelper.getNameAndURL((InternetAddress) a, addressBook);
				String url = p.getSecond();
				String str = ia.toString();
                String addr = ia.getAddress();
                boolean match = false;
                if(str!=null) {
                    //The goal here is to explain why a doc is selected and hence we should replicate Lucene doc selection and Lucene is case insensitive most of the times
                    String lc = str.toLowerCase();
                    if (highlightUnstemmed != null)
                        for (String hs : highlightUnstemmed)
                            if (lc.contains(hs.toLowerCase())) {
                                match = true;
                                break;
                            }
                    if (!match && highlightNames != null)
                        for (String hn : highlightNames)
                            if (lc.contains(hn.toLowerCase())) {
                                match = true;
                                break;
                            }
                }
                if(addr!=null){
                    if (!match && highlightAddresses != null)
                        for (String ha : highlightAddresses)
                            if (addr.contains(ha)) {
                                match = true;
                                break;
                            }
                }

                if(match)
                    thisAddrStr = ("<a href=\"" + url + "\"><span class=\"hilitedTerm rounded\">" + Util.escapeHTML(str) + "</span></a>");
                else
                    thisAddrStr = ("<a href=\"" + url + "\">" + Util.escapeHTML(str) + "</a>");

				if (str != null)
	                outputLineLength += str.length();
			}
			else
			{
				String str = a.toString();
				thisAddrStr = str;
				outputLineLength += str.length();
                JSPHelper.log.warn("Address is not an instance of InternetAddress - is of instance: "+a.getClass().getName() + ", highlighting won't work.");
			}

			if (i + 1 < addrs.length)
				outputLineLength += 2; // +2 for the comma that will follow...

			if (outputLineLength + 2 > lineWrap)
			{
				sb.append("<br/>\n");
				outputLineLength = 0;
			}
			sb.append(thisAddrStr);
			if (i + 1 < addrs.length)
				sb.append(", ");
		}

		return sb.toString();
	}

	/**
	 * returns a string for documents.
	 * 
	 * @param highlightAttachments
	 * @throws Exception
	 */
	public static Pair<String, Boolean> htmlForDocument(Document d, Archive archive, String datasetTitle, BlobStore attachmentsStore,
			Boolean sensitive, Set<Integer> highlightContactIds, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed, Set<Blob> highlightAttachments, Map<String, Map<String, Short>> authorisedEntities,
			boolean IA_links, boolean inFull, boolean debug) throws Exception
	{
		JSPHelper.log.debug("Generating HTML for document: " + d);
		EmailDocument ed = null;
		String html = null;
		boolean overflow = false;
		if (d instanceof EmailDocument)
		{
			// for email docs, 1 doc = 1 page
			ed = (EmailDocument) d;
			StringBuilder page = new StringBuilder();
			page.append("<div class=\"muse-doc\">\n");

			page.append("<div class=\"muse-doc-header\">\n");
			page.append(EmailRenderer.getHTMLForHeader(archive, ed, sensitive, highlightContactIds, highlightTermsStemmed, highlightTermsUnstemmed, IA_links, debug));
			page.append("</div>"); // muse-doc-header

			/*
			 * Map<String, List<String>> sentimentMap =
			 * indexer.getSentiments(ed); for (String emotion:
			 * sentimentMap.keySet()) { page.append ("<b>" + emotion +
			 * "</b>: "); for (String word: sentimentMap.get(emotion))
			 * page.append (word + " "); page.append ("<br/>\n");
			 * page.append("<br/>\n"); }
			 */
			page.append("\n<div class=\"muse-doc-body\">\n");
			Pair<StringBuilder, Boolean> contentsHtml = archive.getHTMLForContents(d, ((EmailDocument) d).getDate(), d.getUniqueId(), sensitive, highlightTermsStemmed,
					highlightTermsUnstemmed, authorisedEntities, IA_links, inFull, true);
			StringBuilder htmlMessageBody = contentsHtml.first;
			overflow = contentsHtml.second;
			// page.append(ed.getHTMLForContents(indexer, highlightTermsStemmed,
			// highlightTermsUnstemmed, IA_links));
			page.append(htmlMessageBody);
			page.append("\n</div> <!-- .muse-doc-body -->\n"); // muse-doc-body

			// page.append("\n<hr class=\"end-of-browse-contents-line\"/>\n");
			List<Blob> attachments = ed.attachments;
			if (attachments != null && attachments.size() > 0)
			{
				// show thumbnails of all the attachments

				if (ModeConfig.isPublicMode()) {
					page.append(attachments.size() + " attachment" + (attachments.size() == 1 ? "" : "s") + ".");
				} else {
					page.append("<hr/>\n<div class=\"attachments\">\n");
					page.append("<table>\n");
					int i = 0;
					for (; i < attachments.size(); i++)
					{
						if (i % 4 == 0)
							page.append((i == 0) ? "<tr>\n" : "</tr><tr>\n");
						page.append("<td>");

						Blob attachment = attachments.get(i);
						String thumbnailURL = null, attachmentURL = null;
						boolean is_image = Util.is_image_filename(attachment.filename);

						if (attachmentsStore != null)
						{
							String contentFileDataStoreURL = attachmentsStore.get_URL(attachment);
							attachmentURL = "serveAttachment.jsp?file=" + Util.URLtail(contentFileDataStoreURL);
							String tnFileDataStoreURL = attachmentsStore.getViewURL(attachment, "tn");
							if (tnFileDataStoreURL != null)
								thumbnailURL = "serveAttachment.jsp?file=" + Util.URLtail(tnFileDataStoreURL);
							else
							{
								if (attachment.is_image())
									thumbnailURL = attachmentURL;
								else
									thumbnailURL = "images/sorry.png";
							}
						}
						else
							JSPHelper.log.warn("attachments store is null!");

						// toString the filename in any case,
						String s = attachment.filename;
						// cap to a length of 25, otherwise the attachment name
						// overflows the tn
						String display = Util.ellipsize(s, 25);
                        boolean highlight = highlightAttachments != null && highlightAttachments.contains(attachment);
                        page.append("&nbsp;" + "<span title=\"" + Util.escapeHTML(s) + "\" class='" + (highlight?"highlight":"") + "'>"+ Util.escapeHTML(display) + "</span>&nbsp;");
						page.append("<br/>");

						String css_class = "attachment-preview" + (is_image ? " img" : "") + (highlight ? " highlight" : "");
						String leader = "<img class=\"" + css_class + "\" ";

						// punt on the thumbnail if the attachment tn or content
						// URL is not found
						if (thumbnailURL != null && attachmentURL != null)
						{
							// d.hashCode() is just something to identify this
							// page/message
							page.append("<a rel=\"page" + d.hashCode() + "\" title=\"" + attachment.filename + "\" class=\"" + (highlight?"highlight":"") + "\" href=\"" + attachmentURL + "\">");
							page.append(leader + "href=\"" + attachmentURL + "\" src=\"" + thumbnailURL + "\"></img>\n");
							page.append("<a>\n");
						}
						else
						{
							// page.append
							// ("&nbsp;<br/>&nbsp;<br/>Not fetched<br/>&nbsp;<br/>&nbsp;&nbsp;&nbsp;");
							// page.append("<a title=\"" + attachment.filename +
							// "\" href=\"" + attachmentURL + "\">");
							page.append(leader + "src=\"images/no-attachment.png\"></img>\n");
							// page.append ("<a>\n");

							if (thumbnailURL == null)
								JSPHelper.log.info("No thumbnail for " + attachment);
							if (attachmentURL == null)
								JSPHelper.log.info("No attachment URL for " + attachment);
						}
						page.append("</td>\n");
					}
					if (i % 4 != 0)
						page.append("</tr>");
					page.append("</table>");
					page.append("\n</div>  <!-- .muse-doc-attachments -->\n"); // muse-doc-attachments
				}

			}
			page.append("\n</div>  <!-- .muse-doc -->\n"); // .muse-doc
			html = page.toString();
		}
		else if (d instanceof DatedDocument)
		{
			/*
			 * DatedDocument dd = (DatedDocument) d; StringBuilder page = new
			 * StringBuilder();
			 * 
			 * page.append (dd.getHTMLForHeader()); // directly jam in contents
			 * page.append ("<div class=\"muse-doc\">\n"); page.append
			 * (dd.getHTMLForContents(indexer)); // directly jam in contents
			 * page.append ("\n</div>"); // doc-contents return page.toString();
			 */
			html = "To be implemented";
		}
		else
		{
			JSPHelper.log.warn("Unsupported Document: " + d.getClass().getName());
			html = "";
		}

		return new Pair<String, Boolean>(html, overflow);
	}

	/**
	 * returns a HTML table string for the doc header
	 * 
	 * @param sensitive
	 *            - when set will highlight any sensitive info in subject based
	 *            on preset regexs
	 * @throws IOException
	 */
	public static StringBuilder getHTMLForHeader(Archive archive, EmailDocument ed, Boolean sensitive, Set<Integer> highlightContactIds, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed,
			boolean IA_links, boolean debug) throws IOException
	{
		AddressBook addressBook = archive.addressBook;
		GroupAssigner groupAssigner = archive.groupAssigner;
        Set<String> contactNames = new LinkedHashSet<>();
        Set<String> contactAddresses = new LinkedHashSet<>();
        if(highlightContactIds!=null)
            for(Integer hci: highlightContactIds) {
                if(hci == null)
                    continue;
                Contact c = archive.addressBook.getContact(hci);
                if(c==null)
                    continue;
                contactNames.addAll(c.names);
                contactAddresses.addAll(c.emails);
            }
        contactNames.addAll(highlightTermsStemmed);

		StringBuilder result = new StringBuilder();
		// header table
		result.append("<table class=\"docheader rounded\">\n");
		// result.append
		// ("<tr><td width=\"100px\" align=\"right\" class=\"muted\">Folder:</td><td>"
		// + this.folderName + "</td></tr>\n");
		if(debug)
			result.append("<tr><td>docId: </td><td>"+ed.getUniqueId()+"</td></tr>\n");
		result.append(JSPHelper.getHTMLForDate(ed.date));

		final String style = "<tr><td align=\"right\" class=\"muted\" valign=\"top\">";

		// email specific headers
		result.append(style + "From: </td><td align=\"left\">");
		Address[] addrs = ed.from;
		if (addrs != null)
		{
			result.append(formatAddressesAsHTML(addrs, addressBook, TEXT_WRAP_WIDTH, highlightTermsUnstemmed, contactNames, contactAddresses));
		}

		result.append(style + "To: </td><td align=\"left\">");
		addrs = ed.to;
		if (addrs != null)
			result.append(formatAddressesAsHTML(addrs, addressBook, TEXT_WRAP_WIDTH, highlightTermsUnstemmed, contactNames, contactAddresses) + "");

		result.append("\n</td></tr>\n");

		if (ed.cc != null && ed.cc.length > 0)
		{
			result.append(style + "Cc: </td><td align=\"left\">");
			result.append(formatAddressesAsHTML(ed.cc, addressBook, TEXT_WRAP_WIDTH, highlightTermsUnstemmed, contactNames, contactAddresses) + "");
			result.append("\n</td></tr>\n");
		}

		if (ed.bcc != null && ed.bcc.length > 0)
		{
			result.append(style + "Bcc: </td><td align=\"left\">");
			result.append(formatAddressesAsHTML(ed.bcc, addressBook, TEXT_WRAP_WIDTH, highlightTermsUnstemmed, contactNames, contactAddresses) + "");
			result.append("\n</td></tr>\n");
		}

		if (groupAssigner != null)
		{
			SimilarGroup<String> g = groupAssigner.getClosestGroup(ed);
			if (g != null && g.size() > 1) // if its just a singleton group, no
											// point explicitly listing a group
											// line
			{
				String url = "browse?groupIdx=" + groupAssigner.getClosestGroupIdx(ed);
				result.append(style + "Group: </td>\n");
				result.append("<td align=\"left\">");
				String description = g.elementsToString();
				result.append("<span class=\"facet\" style=\"padding-left:2px;padding-right:2px\" onclick=\"javascript:window.open('" + url + "');\" title=\""
						+ Util.escapeHTML(description) + "\">" + g.name + "</span></br>");
				result.append("</td>\n</tr>\n");
			}
		}

		String x = ed.description;
		if (x == null)
			x = "<None>";

		result.append(style + "Subject: </td>");
		// <pre> to escape special chars if any in the subject. max 70 chars in
		// one line, otherwise spill to next line
		result.append("<td align=\"left\"><b>");
		x = DatedDocument.formatStringForMaxCharsPerLine(x, 70).toString();
		if (x.endsWith("\n"))
			x = x.substring(0, x.length() - 1);
        List<String> cpeople = archive.getEntitiesInDoc(ed, NER.EPER_TITLE, true);
        List<String> corgs = archive.getEntitiesInDoc(ed, NER.EORG_TITLE, true);
        List<String> cplaces = archive.getEntitiesInDoc(ed, NER.ELOC_TITLE, true);
        List<String> entities = new ArrayList<String>();
        entities.addAll(cpeople);
        entities.addAll(cplaces);
        entities.addAll(corgs);

        // Contains all entities and id if it is authorised else null
        Map<String, Archive.Entity> entitiesWithId = new HashMap<String, Archive.Entity>();
        for (String entity : entities) {
            Set<String> types = new HashSet<String>();
            if (cpeople.contains(entity))
                types.add("cp");
            if (cplaces.contains(entity))
                types.add("cl");
            if (corgs.contains(entity))
                types.add("co");
            String ce = IndexUtils.canonicalizeEntity(entity);
            if (ce == null)
                continue;
            entitiesWithId.put(entity, new Archive.Entity(entity, null, types));
        }
        x = archive.annotate(x, ed.getDate(), ed.getUniqueId(), sensitive, highlightTermsStemmed, highlightTermsUnstemmed, entitiesWithId, IA_links, false);

		result.append(x);
		result.append("</b>\n");
		result.append("\n</td></tr>\n");

		result.append("</table>\n"); // end docheader table

		if (ModeConfig.isPublicMode())
			return new StringBuilder(Util.maskEmailDomain(result.toString()));

		return result;
	}

}

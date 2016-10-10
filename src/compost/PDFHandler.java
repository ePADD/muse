/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse.index;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.email.DocCache;
import edu.stanford.muse.email.JarDocCache;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.util.Util;

public class PDFHandler implements StatusProvider {
	private static Log log = LogFactory.getLog(PDFHandler.class);

	boolean cancelled = false;
	private int nPassed, nFailed, nTotal;

	/** handles a pdf file. philosophy: create png files and text file in topLevelFromDir
	 * and copy them to webappTopLevelDir.
	 * because webappTopLevelDir could get wiped out when the web server restarts.
	 * always copies pdf and png's.
	 * updates docCache with contents and header if needed. */
	private PDFDocument handlePDF(DocCache docCache, String topLevelFromDir, String webappTopLevelDir, String libDir, String userKey, String datasetTitle, int num, String description, String section, File pdfFile)
	{

		// x.pdf: text goes to x.pdf.txt, and page images to go x.pdf.1.png, x.pdf.2.png, etc
		String filename = pdfFile.getName(); // not full path, just the name of the file
		String topLevelToDir = webappTopLevelDir + File.separator + datasetTitle;
		String txtFilename = filename + ".txt";
		String pagePNGPrefix = filename + ".";

		// working dirs are just <top level>/<section>
		String workingFromDir = topLevelFromDir + File.separator + section;
		String workingToDir = topLevelToDir + File.separator + section;

		// create the working dir for this section if it doesn't exist
		new File(workingToDir + File.separator + "dummy").mkdirs();

		// given the pdf, create a .txt and .png files for each page.
		// copy the pdf and png's to the todir because the browser may need to access them directly
		// the txt file never needs to be directly accessible.
		try {
			if (!pdfFile.exists())
				throw new FileNotFoundException();

			// copy the pdf
			Util.copyFileIfItDoesntExist(workingFromDir, workingToDir, filename);

			String txtFileFromFullPath = workingFromDir + File.separator + txtFilename;
			String cp = libDir + File.separator + "log4j-1.2.15.jar" + File.pathSeparator;
			cp += libDir + File.separator + "pdfbox-1.1.0.jar" + File.pathSeparator;
			cp += libDir + File.separator + "fontbox-1.1.0.jar" + File.pathSeparator;
			cp += libDir + File.separator + "commons-logging-1.1.1.jar" + File.pathSeparator;

			// generate the txt file if it doesn't exist
			// we launch in a separate vm because it has a tendency to crash the VM
			if (!docCache.hasContents(datasetTitle, num))
			{
				String cmd = "java -cp " + cp + " org.apache.pdfbox.ExtractText " + filename + " " + txtFilename;
				Util.run_command(cmd, workingFromDir);
				if (!new File(txtFileFromFullPath).exists())
				{
					// create a dummy file
					FileOutputStream fos = new FileOutputStream (txtFileFromFullPath);
					fos.close();
				}

				String contents = Util.getFileContents(workingFromDir + File.separator + txtFilename);
				docCache.saveContents(contents, datasetTitle, num);
			}

			// handle images
			File dirFile = new File(workingFromDir);
			File files[] = dirFile.listFiles(new Util.MyFilenameFilter(workingFromDir + File.separator + pagePNGPrefix, ".png"));
			if (files.length == 0)
			{
				// png files not generated
				// resolution 200 makes the images readable
				String cmd = "java -cp " + cp + " org.apache.pdfbox.PDFToImage -imageType png -outputPrefix " + pagePNGPrefix + " -resolution 100 " + filename;
				Util.run_command(cmd, workingFromDir);
				files = dirFile.listFiles(new Util.MyFilenameFilter(workingFromDir + File.separator + pagePNGPrefix, ".png"));
				if (files.length == 0)
				{
					// if still no files, something must have failed. copy a sorry file.

					String dummyFile = workingFromDir + pagePNGPrefix + ".0.png";
					Util.copy_file (webappTopLevelDir + File.separator + "images" + File.separator + "sorry.png", dummyFile);
					files = new File[1];
					files[0] = new File(dummyFile);
				}
			}

			Util.sortFilesByTime(files);

			// copy over PNG Files
			for (File f: files)
				Util.copyFileIfItDoesntExist(workingFromDir, workingToDir, f.getName());

			// note it's important to escape url's below.
			// this is because the dataset or section etc can contain special chars like '#'
			// but we can't use URLEncoder.encode because that causes problems by converting each space to a +.
			// this breaks the relative URL in the browser.
			// therefore, we reuse the "light encoding" of Util.URLEncodeFilePath
			// may need fixing if other special chars pop up
			List<String> relativeURLsForImages = new ArrayList<String>();
			for (@SuppressWarnings("unused") File f: files)
			{
				String relativeURL = userKey + "/" + datasetTitle + "/" + section + "/" + filename;
//				relativeURL = URLEncoder.encode(relativeURL, "UTF-8");
				relativeURL = Util.URLEncodeFilePath(relativeURL);
				relativeURLsForImages.add(relativeURL);
			}
			String relativeURLForPDF = userKey + "/" + datasetTitle + "/" + section + "/" + filename;
	//		relativeURLForPDF = URLEncoder.encode(relativeURLForPDF, "UTF-8");
			relativeURLForPDF = Util.URLEncodeFilePath(relativeURLForPDF);

			PDFDocument doc = new PDFDocument(num, "PDF", section, relativeURLForPDF, relativeURLsForImages);
			// text contents will remain in topLevelFromDir
			// sanitize path to ensure we escape odd chars like # and ? in the file path
			doc.url = docCache.getContentURL(datasetTitle, num);
			if (!docCache.hasHeader(datasetTitle, num))
				docCache.saveHeader(doc, datasetTitle, num);
			return doc;

//			cmd = "java -cp " + cp + "/ org.apache.pdfbox.ExtractImages -prefix " + filename + " " + filename;
//			Util.run_command(cmd, workingDir);
		} catch (Exception e)
		{
			Util.print_exception(e, log);
			return null;
		}
	}

	/** topDirForPDFs = top level dir for input pdfs
	 * webappTopLevelDir = top level for this app in the container. (does not contain user name)
	 * user: subdir under webappTopLevelDir
	 * datasetTitle: specific dataset title
	 * libdir is dir where the pdfbox libs are present.
	 * PDF and PNG files will go to webappTopLevelDir/user/datasetTitle/
	 * @throws IOException *
	 */
	public List<Document> preparePDFs(String topDirForPDFs, String webappTopLevelDir, String libDir, String userKey, String datasetTitle) throws IOException
	{
		List<Document> returnDocs = new ArrayList<Document>();
		cancelled = false;

		// weird: sometimes we get a double-slash or double-backslash which kills the matching...
		// better canonicalize first, which calling new File() and then getAbsolutePath does
		File topDirFile = new File(topDirForPDFs).getAbsoluteFile();

		String topDirForExtractedImages = topDirForPDFs + File.separator + "pdfimages";
		new File(topDirForExtractedImages).mkdirs();

		String datasetTopLevelDir = webappTopLevelDir + File.separator + datasetTitle;
		// make target dir if needed. delete if already exists ? maybe.
		// + "1" just to indicate a dummy file - mkdirs will create all *parent* dirs of this imaginary file
		new File(datasetTopLevelDir + File.separator + "1").mkdirs();

		nPassed = nFailed = 0;

		File files[] = topDirFile.listFiles();

		// first pass just to find #pdf's so we can report it in the status message
		nTotal = 0;
		for (File file: files)
		{
			if (file.isDirectory())
			{
				File dir = file;
				File dirFiles[] = dir.listFiles();
				for (File f: dirFiles)
				{
					if (cancelled)
						return returnDocs;
					if (f.getName().endsWith(".pdf"))
						nTotal++;
				}
			}
		}

		int count = -1;
		DocCache docCache = new JarDocCache(topDirForPDFs);
		// fragile: pdf's are indexed using count.
		// so have to recompute contents/headers once any subdir has a delta in its pdf's. :-(
		// need to fix this. probably can be done easily by changing the num in the doccache to a
		// key string
		out:
		for (File file: files)
		{
			if (file.isDirectory())
			{
				File dir = file;
				File dirFiles[] = dir.listFiles();
				for (File f: dirFiles)
				{
					if (cancelled)
						break out;

					if (f.getName().endsWith(".pdf"))
					{
						count++;
						String section = dir.getName();
						log.info("Converting PDF: " + f.getAbsolutePath());
						PDFDocument pdfDoc = handlePDF (docCache, topDirForPDFs, webappTopLevelDir, libDir, userKey, datasetTitle, count, "PDF", section, f);
						if (pdfDoc != null)
						{
							nPassed++;
							returnDocs.add(pdfDoc);
						}
						else
							nFailed++;
					}
				}
			}
		}

		docCache.pack();

		System.out.println (nPassed + " PDF documents found and prepared in " + topDirForPDFs + ((nFailed > 0) ? " [WARNING: " + nFailed + " failed]" : ""));
		return returnDocs;
	}

	public void cancel() {
		cancelled = true;
	}

	public String getStatusMessage() {
		String result = "Prepared " + nPassed + " of " + nTotal + " PDF documents<br/>&nbsp;";
		if (nFailed > 0)
			result += "(" + nFailed + " errors)";
		return result;
	}

	public boolean isCancelled() {
		return cancelled;
	}
}

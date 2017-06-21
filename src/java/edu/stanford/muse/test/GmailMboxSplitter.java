package edu.stanford.muse.test;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.util.Util;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Program to break up a Gmail takeout file into folders (based on gmail labels, as read by the X-Gmail-Label header field).
 * assume not too many labels are present (as all written file descriptors are kept open simultaneously).
 * Unread folder is skipped.
 * Messages may get duplicated if they have multiple labels. */
class GmailMboxSplitter {
    private static String OUT_FILE = "GmailMboxSplitter.out.txt"; // writes into current directory
    private static StringBuilder currentMessageContents = new StringBuilder(); // this will contain the current message
    private static List<String> currentMessageLabels = new ArrayList<>();
    private static int currentMessageNum = 1;

    // we keep all output files open to avoid perf. overhead of closing and opening files
    // we assume not too many labels are present. Revisit this and cache the most frequently written file descriptors if it becomes a problem */
    private static Map<String, PrintWriter> folderToWriter = new LinkedHashMap<>(); // folder name to they current open file.

    // note messages to unread folder are skipped
    private static void emitMessage() throws FileNotFoundException {
        for (String f: currentMessageLabels) {
            if ("Unread".equals(f)) // could make this optional
                continue;

            PrintWriter pw = folderToWriter.get(f);
            if (pw == null) {
                pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)), 8 * 1024 * 1024));
                folderToWriter.put(f, pw);
            }
            pw.println(currentMessageContents);

        }
        currentMessageContents = new StringBuilder();
        currentMessageLabels.clear();
        currentMessageNum++;
    }

    public static void main(String args[]) throws IOException {
        if (args.length == 0) {
            System.err.println("No arguments! Please provide the Gmail takeout file as a program argument.");
            return;
        }

        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(args[0]), 8 * 1024 * 1024)));
        int lineCount = 0;

        PrintWriter out = new PrintWriter(System.out); // new PrintWriter(new FileOutputStream(OUT_FILE));
        Multimap<String, Integer> folderToMessageStartLines = HashMultimap.create();

        String line0 = "", line1 = "", line2; // // line2 will hold the current line, line1 the previous one, and line0 the one before that.
        while (true) {
            line2 = lnr.readLine();
            if (line2 == null)
                break;

            lineCount++;
            if (lineCount % 1000000 == 0)
                System.out.println (lineCount + " lines");


            String labelsHeader = "X-Gmail-Labels: ";
            if (line2.startsWith(labelsHeader)) {
                // line2 looks like: "X-Gmail-Labels: =To-do,Sent,Important"
                String labels = line2.substring(labelsHeader.length());

                // labels: =To-do,Sent,Important
                currentMessageLabels = Util.tokenize(labels, ",");
                for (String folder: currentMessageLabels)
                    folderToMessageStartLines.put(folder, currentMessageNum);
            }

 //       if (line0.length() == 0 && line1.length() == 0 && line2.startsWith("From ")) { // use for 2 blank lines followed by "From " rule
            if (line1.length() == 0 && line2.startsWith("From ")) { // previous line blank and this line starts with "From "
                // ok the previous message got over, write it out
                if (currentMessageNum % 10000 == 0)
                    System.out.println ("#messages so far = " + currentMessageNum);

                emitMessage();
            }

            currentMessageContents.append(line2).append("\n");

            line0 = line1;
            line1 = line2;
        }

        // the final message still needs to be written out
        emitMessage();

        // close all files to flush the buffers
        for (PrintWriter pw: folderToWriter.values())
            pw.close();

        out.println ("Total of " + currentMessageNum + " unique messages");

        long totalMessages = 0;
        for (String folder: folderToMessageStartLines.keySet()) {
            int nMessages = folderToMessageStartLines.get(folder).size();
            out.println (folder + " - " + nMessages + " messages");
            totalMessages += nMessages;
        }
        out.println ("sum of message counts across all folders: " + totalMessages);
        out.close();
    }
}

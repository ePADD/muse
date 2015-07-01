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
package edu.stanford.muse.email;


import java.io.Serializable;

import edu.stanford.muse.util.Util;


/** an email processor thread operates on (a range of messages in) a single email folder.
 * multiple fetchers can run in parallel and be merged */



abstract public class EmailProcessorThread implements Runnable, Serializable {
	protected int threadID;
	protected EmailStore emailStore;
	protected String folder_name; // current folder name
	protected boolean isCancelled;

	public static boolean verbose = false;
	public static boolean debug = false;

	// notes: begin_msg_index is always correct. end_msg_index = -1  means nMessages in folder.
	// note: msg # begin_msg_index will be processed. msg # end_msg_index will not be processed.
	protected int begin_msg_index = 0, end_msg_index = -1;

	EmailFetcherStats stats = new EmailFetcherStats();
	String currentStatus;

	// stats
	int nMessagesProcessedSuccess, nUncachedMessagesProcessed, nMessagesCached;	// running count of # of messages processed successfully
	int nErrors = 0;

	public EmailProcessorThread() { /* */ }

	public EmailProcessorThread(EmailStore emailStore, String folder_name)
	{
		this.emailStore = emailStore;
		this.folder_name = folder_name;
	}

	public void cancel() { isCancelled = true; }

	public EmailProcessorThread(EmailStore emailStore, String folderName, int begin_msg_index, int end_msg_index)
	{
		this.emailStore = emailStore;
		this.folder_name = folderName;
		stats.nTotalMessages = end_msg_index - begin_msg_index;
		this.begin_msg_index = begin_msg_index;
		this.end_msg_index = end_msg_index;		
	}

	public int getThreadID() {
		return threadID;
	}

	public void setThreadID(int threadID) {
		this.threadID = threadID;
	}

	public int getNMessagesProcessed() {
		return nMessagesProcessedSuccess;
	}

	public int getNUncachedMessagesProcessed() {
		return nUncachedMessagesProcessed;
	}

	public void setFolderName(String mbox)
	{
		this.folder_name = mbox;
	}

	abstract public void run();
	abstract public void verify();
//	abstract public void merge(EmailProcessorThread other);

}

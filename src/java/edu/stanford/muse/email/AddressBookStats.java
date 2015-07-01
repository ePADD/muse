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

public class AddressBookStats implements Serializable {
	private static final long serialVersionUID = 1L;

	public int nOwnEmails, nOwnNames;
	// Calendar firstMessageDate, lastMessageDate;
	// int spanInMonths;
	public int nContacts;
	public int nNames = 0, nEmailAddrs = 0;

	public String toString()
	{
		// do not use html special chars here!

		String s = "own_email_address: " + nOwnEmails + " own_names: " + nOwnNames + "\n";
		s += "contacts: " + nContacts + " non_zero_contacts: " + "\n";
		return s;
	}
}

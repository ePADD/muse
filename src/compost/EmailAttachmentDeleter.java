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


import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.MessageIDTerm;
import javax.mail.search.SearchTerm;

import edu.stanford.muse.util.Util;


class EmailAttachmentDeleter {

private MimeMessage locate_message_in_folder(Folder f, String messageID) throws MessagingException
{
    SearchTerm st = new MessageIDTerm (messageID);
    Message m[] = f.search (st);

    MimeMessage soughtMessage = null;

    if (m.length != 1)
    {
        // unfortunately, we have to redo the messageID check becase f.search sometimes returns a false -ve
        System.out.println ("WARNING: m.length = " + m.length + " for message ID " + messageID);
        Message all_m[] = f.getMessages();
        for (int i = 0; i < all_m.length; i++)
        {
            if (((MimeMessage) all_m[i]).getMessageID().equals (messageID))
            {
                soughtMessage = (MimeMessage) all_m[i];
                System.out.println ("message " + i + " matches!");
                break;
            }
        }
        Util.ASSERT (soughtMessage != null);
    }
    else
        soughtMessage = (MimeMessage) m[0];

    return soughtMessage;
}
// returns the part of p that corresponds to d
@SuppressWarnings("unused")
private Part locate_part_in_message(MimeMessage m, Part p, EmailAttachmentBlob b) throws MessagingException, IOException
{
    String ct = null;
    try {
        ct = p.getContentType();
    } catch (Exception pex) {
        System.out.println("WARNING: Unable to get CONTENT-TYPE: " + ct + " size = " + p.getSize() + " subject: " + m.getSubject() + " Date : " + m.getSentDate().toString() + "\n");
   //     return null;
    }

    String filename = p.getFileName();
    if ((filename != null) && Util.is_image_filename (filename))
    {
        EmailAttachmentBlob b1 = new EmailAttachmentBlob(p.getFileName(), p.getSize(), m, p);
        if (b1.equals(b))
            return p;
    }


    if (p.isMimeType("multipart/*")) 
    {
        // System.out.println("This is a Multipart");
        // System.out.println("---------------------------");
        Multipart mp = (Multipart)p.getContent();
        int count = mp.getCount();
        for (int i = 0; i < count; i++)
        {
            Part p1 = locate_part_in_message(m, mp.getBodyPart(i), b);
            if (p1 != null)
                return p1;
        }
    }
    else if (p.isMimeType("message/rfc822")) 
    {
        return locate_part_in_message(m, (Part)p.getContent(), b);
    } 

    return null;
}

private Folder f1 = null; // caches the folder which we last deleted from

// delete a bunch of parts with the same message ID
public void delete_datas (Store message_store, List<EmailAttachmentBlob> datas) throws MessagingException, IOException
{
    String messageID = datas.get(0).messageID;
    String folder = datas.get(0).folderName;

    if ((f1 == null) || (!f1.getFullName().equals(folder)))
    {
        if (f1 != null)
        {
            System.out.println ("Folder change: from " + f1.getFullName());
            f1.close(false);
        }
        System.out.println ("Folder opening: now " + folder);
        f1 = message_store.getDefaultFolder();
        f1 = f1.getFolder(folder);
        f1.open(Folder.READ_WRITE);
    }

    Util.ASSERT (f1 != null);

    Message m = locate_message_in_folder (f1, messageID);
    if (m == null)
    {
        System.out.println ("WARNING: Could not find message id " + messageID);
        return;
    }

    // MimeMessage new_message = clone_omitting_parts(((MimeMessage) m), datas);
    MimeMessage new_message = new MimeMessage((MimeMessage) m);
    // new_message.saveChanges();
    f1.close(false);
    f1 = message_store.getDefaultFolder();
    f1 = f1.getFolder(folder);
    f1.open(Folder.READ_WRITE);
    append_message(f1, new_message);

    // delete_message (message_store, f1, m);
}

// returns whether datas contain the given part
private boolean contains_part (List<EmailAttachmentBlob> datas, Part p) throws MessagingException
{
    for (EmailAttachmentBlob b : datas)
    {
        // our only metric of equivalence currently, may need improvement
        if (b.filename.equals(p.getFileName())  
            && (p.getSize() == b.size))
            return true;
    }
    return false;
}

// clone a mime message, omitting some parts corresponding to datas_to_omit.
// datas_to_omit must all have the same messageID.
public MimeMessage clone_omitting_parts (MimeMessage m, List<EmailAttachmentBlob> datas_to_omit) throws MessagingException, IOException
{
    MimeMessage new_message = new MimeMessage(m);
    String type = new_message.getContentType();
    System.out.println ("initial message content type = " + type);
    System.out.println ("initial new message content type = " + new_message.getContentType());
m.writeTo (new FileOutputStream ("/tmp/old"));
new_message.writeTo (new FileOutputStream ("/tmp/mid"));
    System.out.println ("old is " + m);
    System.out.println ("old content type = " + type);
    System.out.println ("old encoding = " + m.getEncoding());
    Object content = m.getContent();
    MimeMultipart multi_content = (MimeMultipart) content;
    System.out.println ("content's content type = " + multi_content.getContentType());
    // multi_content.removeBodyPart ((BodyPart) part_to_omit);
//    multi_content.addBodyPart ((BodyPart) part_to_omit);
//    multi_content.setParent(new_message);

    int count = multi_content.getCount();
    MimeMultipart new_multi_content = new MimeMultipart();
    for (int i = 0; i < count; i++)
    {
        BodyPart p = multi_content.getBodyPart(i);
        if (!contains_part(datas_to_omit, p))
            new_multi_content.addBodyPart(p);
    }
    new_multi_content.setParent(new_message);
    new_message.setContent(new_multi_content, type);
    new_message.saveChanges();

    System.out.println ("new is " + new_message);
    System.out.println ("new content type = " + new_message.getContentType());
    System.out.println ("old # parts = " + count);
    System.out.println ("new # parts = " + new_multi_content.getCount());

    new_message.writeTo (new FileOutputStream ("/tmp/new"));

    /*
    new_message.setContent (new_multi_content);
    new_multi_content.writeTo (new FileOutputStream ("/tmp/out"));
    new_message.saveChanges();
    */
    return new_message;
}

public static void append_message (Folder f, MimeMessage m) throws MessagingException
{
    Message[] x = new Message[1]; x[0] = m;
    f.appendMessages(x);
}

public void delete_message (Store message_store, Folder f, Message m) throws MessagingException
{
    Message[] x = new Message[1]; 
    x[0] = m;
    Folder f1 = message_store.getDefaultFolder();
    f1 = f1.getFolder("tmp");
    f.copyMessages (x, f1);
    // f.setFlags (x, new Flags(Flags.Flag.DELETED), true);
}

}

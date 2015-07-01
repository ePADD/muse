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
package edu.stanford.muse.util;


import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import edu.stanford.muse.memory.MemoryStudy;

public class CryptoUtils {
	 private static final byte[] salt = {
	        (byte)0x11, (byte)0xaf, (byte)0x21, (byte)0x17,
	        (byte)0x24, (byte)0x96, (byte)0x14, (byte)0xa5
	    };

	 private static String pw;
	 static {
		 pw = System.getProperty("encpw");
		 if (Util.nullOrEmpty(pw))
			 pw = "ga104tes";
	 }
	 private static Cipher getCipher(int mode) throws GeneralSecurityException
	 {
		 // Create PBE parameter set
		 
		 PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, 20);
		 PBEKeySpec pbeKeySpec = new PBEKeySpec(pw.toCharArray());
		 SecretKey pbeKey = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(pbeKeySpec);
		 Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
		 pbeCipher.init(mode, pbeKey, pbeParamSpec);
		 return pbeCipher;
	 }

	 public static void writeEncryptedString (String s, String filename) throws IOException, GeneralSecurityException
	 {
		 writeEncryptedBytes(s.getBytes("UTF-8"), filename); // is utf-8 ok ? java strings are actually utf-16
	 }

	 public static byte[] getEncryptedBytes (byte[] clearText) throws IOException, GeneralSecurityException
	 {
		 Cipher cipher = getCipher(Cipher.ENCRYPT_MODE);
		 byte[] ciphertext = cipher.doFinal(clearText);
		 return ciphertext;
	 }

	 public static void writeEncryptedBytes (byte[] clearText, String filename) throws IOException, GeneralSecurityException
	 {
		 byte[] ciphertext = getEncryptedBytes(clearText);
		 FileOutputStream fos = new FileOutputStream(filename);
		 fos.write(ciphertext);
		 fos.close();
	 }

	public static byte[] readEncryptedBytes (String filename) throws IOException, GeneralSecurityException
	{
		return decryptBytes(Util.getBytesFromFile(filename));
	}

	public static byte[] decryptBytes(byte[] b) throws GeneralSecurityException
	{
		Cipher cipher = getCipher(Cipher.DECRYPT_MODE);
		byte[] clearBytes = cipher.doFinal(b);
		return clearBytes;
	}

	public static String readEncryptedString (String filename) throws IOException, GeneralSecurityException
	{
		return new String(readEncryptedBytes(filename));
	}

	public static List<String> readEncryptedFileAsLines (String filename) throws IOException, GeneralSecurityException
	{
		String fileStr = new String(readEncryptedBytes(filename));
		LineNumberReader lnr = new LineNumberReader(new StringReader(fileStr));
		List<String> result = new ArrayList<String>();
		while (true)
		{
			String line = lnr.readLine();
			if (line == null)
				break;
			result.add(line);			
		}
		return result;
	}

	public static void main (String[] args) throws Exception
	{		 
		//writeEncryptedBytes("String to encode".getBytes("utf-8"), "/tmp/TEST");
		String file = (args.length == 0) ? "/tmp/TEST" : args[0];
		
		byte b[] = readEncryptedBytes(file);
		// for users file only, print the contents
		if (file.endsWith("users")) {
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b));
			List<MemoryStudy.UserStats> users = (List<MemoryStudy.UserStats>) ois.readObject();
			for (int i = 0; i < users.size(); i++)
				System.out.println (i + ". " + Util.fieldsToString(users.get(i)));
		}
		else // otherwise, print as a string
			System.out.println (new String(b, "UTF-8"));
	}
}

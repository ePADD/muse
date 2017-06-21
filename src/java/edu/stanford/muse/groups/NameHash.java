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
package edu.stanford.muse.groups;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.util.HashMap;

class NameHash
	{
	private static String convertToHex(byte[] data)
            {
                StringBuffer buf = new StringBuffer();
                for (int i = 0; i < data.length; i++)
                {
                        int halfbyte = (data[i] >>> 4) & 0x0F;
                        int two_halfs = 0;
                        do
                        {
                            if ((0 <= halfbyte) && (halfbyte <= 9))
                                buf.append((char) ('0' + halfbyte));
                            else
                                    buf.append((char) ('a' + (halfbyte - 10)));
                            halfbyte = data[i] & 0x0F;
                        } while(two_halfs++ < 1);
                }
                return buf.toString();
            }
         
            public static String SHA1(String text)
            {
                    try
                        {
                            MessageDigest md;
                                md = MessageDigest.getInstance("SHA-1");
                                byte[] sha1hash = new byte[40];
                                md.update(text.getBytes("iso-8859-1"), 0, text.length());
                                sha1hash = md.digest();
                                return convertToHex(sha1hash);
                        }
                    catch(Exception e)
                    {
                            return null;
                    }
            }
        }
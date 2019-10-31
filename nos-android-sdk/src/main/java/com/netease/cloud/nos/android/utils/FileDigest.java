package com.netease.cloud.nos.android.utils;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;


public class FileDigest {

    private static String byteArrayToHex(byte[] hashBytes) {
        String returnVal = "";
        for (int i = 0; i < hashBytes.length; i++) {
            returnVal += Integer.toString((hashBytes[i] & 0xff) + 0x100, 16).substring(1);
        }
        return returnVal.toLowerCase();
    }


    public static String getFileMD5(InputStream inputStream) {
        int bufferSize = 512 * 1024;
        DigestInputStream digestInputStream = null;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            inputStream.mark(inputStream.available() + 1);
            digestInputStream = new DigestInputStream(inputStream, messageDigest);

            byte[] buffer = new byte[bufferSize];
            while (digestInputStream.read(buffer) > 0)
                ;

            messageDigest = digestInputStream.getMessageDigest();
            byte[] resultByteArray = messageDigest.digest();
            return byteArrayToHex(resultByteArray);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                inputStream.reset();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

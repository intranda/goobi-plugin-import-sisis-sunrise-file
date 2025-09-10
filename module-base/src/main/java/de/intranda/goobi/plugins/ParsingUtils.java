/*************************************************************************
 * 
 * Copyright intranda GmbH
 * 
 * ************************* CONFIDENTIAL ********************************
 * 
 * [2003] - [2015] intranda GmbH, Bertha-von-Suttner-Str. 9, 37085 Göttingen, Germany
 * 
 * All Rights Reserved.
 * 
 * NOTICE: All information contained herein is protected by copyright.
 * The source code contained herein is proprietary of intranda GmbH.
 * The dissemination, reproduction, distribution or modification of
 * this source code, without prior written permission from intranda GmbH,
 * is expressly forbidden and a violation of international copyright law.
 * 
 *************************************************************************/
package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class.
 **/
public final class ParsingUtils {

    /**
     * Read file using UTF_8
     * 
     * @param file
     * @return
     * @throws IOException
     * @should read file correctly
     * @should throw IOException on error
     */
    public static String readFileToString(File file) throws IOException {

        byte[] encoded = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        String strOrig = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(encoded)).toString();

        //remove BOM markers:
        String strFinal = strOrig.replace("\uFEFF", "");
        return strFinal;
    }

}
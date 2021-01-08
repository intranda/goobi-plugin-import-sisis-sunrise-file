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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filter;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.transform.XSLTransformer;
import org.jdom2.xpath.XPathBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

/**
 * Utility class.
 **/
public final class ParsingUtils {

    private static final Logger logger = LoggerFactory.getLogger(ParsingUtils.class);

    /**
     * 
     * @param sourceDoc The source <code>Document</code>.
     * @param stylesheetPath
     * @return The transformed document as string.
     * @throws FileNotFoundException
     */
    public static Document convertViaXSLT(Document sourceDoc, String stylesheetPath) throws FileNotFoundException {
        if (sourceDoc == null) {
            throw new IllegalArgumentException("sourceDoc may not be null");
        }
        if (stylesheetPath == null) {
            throw new IllegalArgumentException("stylesheetPath may not be null");
        }
        if (!new File(stylesheetPath).isFile()) {
            throw new FileNotFoundException(stylesheetPath);
        }

        try {
            XSLTransformer transformer = new XSLTransformer(stylesheetPath);
            return transformer.transform(sourceDoc);
        } catch (JDOMException e) {
            logger.error(e.getMessage(), e);
        }

        return null;
    }

    /**
     * Converts a <code>String</code> from one given encoding to the other.
     * 
     * @param string The string to convert.
     * @param from Source encoding.
     * @param to Destination encoding.
     * @return The converted string.
     */
    public static String convertStringEncoding(String string, String from, String to) {
        try {
            Charset charsetFrom = Charset.forName(from);
            Charset charsetTo = Charset.forName(to);
            CharsetEncoder encoder = charsetFrom.newEncoder();
            CharsetDecoder decoder = charsetTo.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.IGNORE);
            ByteBuffer bbuf = encoder.encode(CharBuffer.wrap(string));
            CharBuffer cbuf = decoder.decode(bbuf);
            return cbuf.toString();
        } catch (CharacterCodingException e) {
            logger.error(e.getMessage(), e);
        }

        return string;
    }

    public static String convertToHtmlString(String string) {
        if (string != null) {
            // string = string.replaceAll("<", "&lt;");
            // string = string.replaceAll(">", "&gt;");
            // string = string.replaceAll("\"", "&quot;");
            string = string.replaceAll("<", "");
            string = string.replaceAll(">", "");
            string = string.replaceAll("\"", "'");
        }

        return string;
    }

    /**
     * Uses ICU4J to determine the charset of the given InputStream.
     * 
     * @param input
     * @return Detected charset name; null if not detected.
     * @throws IOException
     */
    public static String getCharset(InputStream input) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(input)) {
            CharsetDetector cd = new CharsetDetector();
            cd.setText(bis);
            CharsetMatch cm = cd.detect();
            if (cm != null) {
                return cm.getName();
            }
        }

        return null;
    }

    /**
     * 
     * @param string
     * @return
     * @throws IOException
     * @throws JDOMException
     * @should build document correctly
     * @should throw IOException on error
     * @should throw JDOMException on error
     */
    public static Document stringToJDOM(String string) throws JDOMException, IOException {
        try (StringReader reader = new StringReader(string)) {
            return new SAXBuilder().build(reader);
        }
    }

    /**
     * 
     * @param file
     * @return
     * @throws IOException
     * @should read file correctly
     * @should throw IOException on error
     */
    public static String readFileToString(File file) throws IOException {
        
        byte[] encoded = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
//        return StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(encoded)).toString();
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(encoded)).toString();
    }

    /**
     * Replaces non-sort characters with the Pica+ variant.
     * 
     * @param title
     * @return Title converted to Pica+ non-sort part convention.
     */
    public static String picafyNonSortCharacters(String title) {
        if (title == null) {
            return null;
        }

        if (title.contains("<<") && title.contains(">> ")) {
            title = title.replace("<<", "").replace(">> ", " @");
        }

        return title;
    }

    /**
     * Evaluates the given XPath expression to a list of elements.
     * 
     * @param expr XPath expression to evaluate.
     * @param parent If not null, the expression is evaluated relative to this element.
     * @param namespaces
     * @return {@link ArrayList}
     * @should return all values
     */
    public static List<Element> evaluateToElements(String expr, Element element, List<Namespace> namespaces) {
        List<Object> list = evaluate(expr, element, Filters.element(), namespaces);
        if (list == null) {
            return Collections.emptyList();
        }
        List<Element> retList = new ArrayList<>(list.size());
        for (Object object : list) {
            if (object instanceof Element) {
                retList.add((Element) object);
            }
        }
        return retList;
    }

    /**
     * XPath evaluation with a given return type filter.
     * 
     * @param expr XPath expression to evaluate.
     * @param parent If not null, the expression is evaluated relative to this element.
     * @param filter Return type filter.
     * @param namespaces
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<Object> evaluate(String expr, Object parent, Filter filter, List<Namespace> namespaces) {
        XPathBuilder<Object> builder = new XPathBuilder<>(expr.trim().replace("\n", ""), filter);

        if (namespaces != null && !namespaces.isEmpty()) {
            builder.setNamespaces(namespaces);
        }

        XPathExpression<Object> xpath = builder.compileWith(XPathFactory.instance());
        return xpath.evaluate(parent);
    }

    /**
     * 
     * @param text
     * @return
     * @should replace chars correctly
     */
    public static String stripIllegalXMLChars(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c < 0x1A) {
                // Replace illegal characters with spaces
                if (c >= 0x9 && c <= 0xD) {
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            } else if (c > 0x1F) {
                // Regular ASCII chars
                sb.append(c);
            }
        }

        return sb.toString();
    }
    
    
}
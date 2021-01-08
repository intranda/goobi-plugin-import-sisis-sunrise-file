package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.SystemUtils;
import org.jfree.util.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

public class SGMLParser {

    private String strOutputPath;
    private String strImagePath;

    private MetsMods mm;
    private DigitalDocument dd;
    private SubnodeConfiguration config;

    private String strConfigRulesetPath = "rulesetPath";
    private String strConfigOutputPath = "outputPath";
    private String strConfigImagePathFile = "imagePathFile";
    private Prefs prefs;
    private int iCurrentPageNo;
    private DocStruct physical;
    private DocStruct logical;
    private DocStruct currentVolume;

    private String strCurrentId;

    private String strIdPrefix = "";

    public Boolean boVerbose;
    //special case: save everything as Monograph:
    private Boolean boAllMono = false;

    public SGMLParser(SubnodeConfiguration config) throws ConfigurationException, PreferencesException {

        this.config = config;
        strOutputPath = config.getString(strConfigOutputPath);
        strImagePath = config.getString(strConfigImagePathFile);
        prefs = new Prefs();
        prefs.loadPrefs(config.getString(strConfigRulesetPath));

        boAllMono = config.getBoolean("allMono", false);
        strIdPrefix = config.getString("idPrefix", "");
    }

    public void addSGML(MetsMods mm, DocStruct currentVolume, String strId) throws IOException, UGHException {

        if (boVerbose) {
            System.out.println("Add SGML " + strId);
        }

        iCurrentPageNo = 1;
        this.mm = mm;
        this.dd = mm.getDigitalDocument();
        this.physical = dd.getPhysicalDocStruct();
        this.logical = dd.getLogicalDocStruct();
        this.currentVolume = currentVolume;

        //        String strId = mm.getGoobiID();
        this.strCurrentId = strId;

        File sgml = new File(config.getString("sgmlPath") + strId + ".sgm");

        if (sgml.exists()) {
            parse(sgml);
        } else if (boVerbose) {
            System.out.println("No SGML for " + strId);
        }
    }

    private void parse(File sgml) throws IOException, UGHException {

        //        String text = ParsingUtils.readFileToString(sgml);

        Document doc = getDoc(sgml);

        //        //for testing
        //        final File f = new File("/home/joel/git/rechtsgeschichte/test/html.txt");
        //        FileUtils.writeStringToFile(f, doc.outerHtml(), "UTF-8");
        //        //

        for (Element elt : doc.getElementsByTag("html")) {

            parse(elt);
            break;
        }
    }

    public void parse(Element elt) throws IOException, UGHException {

        Boolean boWithEbind = false;

        //For Diss, look like this:
        for (Element elt1 : elt.getElementsByTag("ebind")) {
            boWithEbind = true;

            for (Element eltHeader : elt1.getElementsByTag("ebindheader")) {
                for (Element eltDesc : eltHeader.getElementsByTag("filedesc")) {

                    addHeader(eltDesc);
                    break;
                }
            }

            Elements elts = elt1.children();
            addPrepages(elts);
            
            for (Element elt2 : elts) {

                if (elt2.tagName().equalsIgnoreCase("div")) {

                    if (currentVolume != null) {
                        addDiv(elt2, currentVolume);
                    } else {

                        try {
                            addDiv(elt2, logical);
                        } catch (TypeNotAllowedAsChildException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }

            }

        }

        //for Privatrecht, looks like this: 
        if (!boWithEbind) {
            for (Element elt1 : elt.getElementsByTag("body")) {
                Elements elts = elt1.children();

                addPrepages(elts);
                
                for (Element elt2 : elts) {
                      if (elt2.tagName().equalsIgnoreCase("div")) {
                        if (currentVolume != null) {
                            addDiv(elt2, currentVolume);
                        } else {
                            try {
                                addDiv(elt2, logical);
                            } catch (TypeNotAllowedAsChildException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }
        }

    }

    private void addPrepages(Elements elts) throws TypeNotAllowedForParentException, UGHException, IOException, TypeNotAllowedAsChildException {
    
        DocStruct dsEintrag = dd.createDocStruct(prefs.getDocStrctTypeByName("Prepage"));
        Boolean boPrepages = false;
      
        DocStruct dsTop = currentVolume;
        if (dsTop == null) {
            dsTop = logical;
        }

        for (Element elt2 : elts) {

            try {
                //catch case with images outside div:
                String strDocType = dsTop.getType().getName();
                if (elt2.tagName().equalsIgnoreCase("page") && !strDocType.contentEquals("MultiVolumeWork")) {

                    for (Element eltImg : elt2.getElementsByTag("img")) {
                        DocStruct page = getAndSavePage(eltImg);
                        if (page != null) {
                            //create prepage, if necessary
                            physical.addChild(page);

                            dsTop.addReferenceTo(page, "logical_physical");
                            if (dsTop != logical) {
                                logical.addReferenceTo(page, "logical_physical");
                            }
                            dsEintrag.addReferenceTo(page, "logical_physical");
                            boPrepages = true;
                        }
                    }
                }

            } catch (TypeNotAllowedAsChildException e) {
                // TODO Auto-generated catch block
                Log.error(e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (boPrepages) {
            dsTop.addChild(dsEintrag);
            System.out.println("Prepages: " + strCurrentId);
        }

    }

    private ArrayList<DocStruct> addDiv(Element elt2, DocStruct dsParent) throws UGHException, IOException {

        DocStruct dsEintrag = dd.createDocStruct(prefs.getDocStrctTypeByName(getDocStructName(elt2)));
        //title metadata:
        //        String strType = dsEintrag.getType().getName();
        //        if (strType.equalsIgnoreCase("Chapter") || strType.equalsIgnoreCase("PartOfWork") || strType.equalsIgnoreCase("OtherDocStrct")) {
        MetadataType type = prefs.getMetadataTypeByName("TitleDocMain");
        Metadata md = new Metadata(type);
        String strTxt = elt2.ownText();
        md.setValue(strTxt);
        dsEintrag.addMetadata(md);
        //        }

        ArrayList<DocStruct> pages = new ArrayList<DocStruct>();

        Elements children = elt2.children();
        for (Element eltPage : children) {

            if (eltPage.tagName().equalsIgnoreCase("div")) {
                ArrayList<DocStruct> childPages = addDiv(eltPage, dsEintrag);

                //add the child pages to the parent struct:
                for (DocStruct childPage : childPages) {
                    if (dsParent != currentVolume && dsParent != logical) {
                        dsParent.addReferenceTo(childPage, "logical_physical");
                    }

                    pages.add(childPage);
                }
            }

            if (eltPage.tagName().equalsIgnoreCase("page")) {

                for (Element eltImg : eltPage.getElementsByTag("img")) {
                    DocStruct page = getAndSavePage(eltImg);
                    if (page != null) {
                        physical.addChild(page);
                        if (currentVolume != null) {
                            currentVolume.addReferenceTo(page, "logical_physical");
                            dsEintrag.addReferenceTo(page, "logical_physical");
                        } else {
                            logical.addReferenceTo(page, "logical_physical");
                            dsEintrag.addReferenceTo(page, "logical_physical");
                        }

                        //add the page reference to the parent struct:
                        if (dsParent != currentVolume && dsParent != logical) {
                            dsParent.addReferenceTo(page, "logical_physical");
                        }

                        pages.add(page);
                    }
                }
            }
        }

        dsParent.addChild(dsEintrag);
        return pages;
    }

    private void addHeader(Element eltHeader) throws MetadataTypeNotAllowedException {

        DocStruct docStruct = logical;
        if (currentVolume != null) {
            docStruct = currentVolume;
        }

        for (Element elt : eltHeader.getAllElements()) {

            String strName = elt.tagName();
            if (strName.equalsIgnoreCase("titlestmt")) {

                for (Element eltTitle : elt.getElementsByTag("titleproper")) {
                    MetadataType typeTitle = prefs.getMetadataTypeByName("TitleDocMain");
                    Metadata mdTitle = new Metadata(typeTitle);

                    if (docStruct.getAllMetadataByType(typeTitle).size() != 0) {

                        //check that it is correctly formed:
                        String strNew = eltTitle.text();

                        if (strNew.contains("�")) {
                            continue;
                        }

                        //check that it is not just the original title:
                        String strTitle = docStruct.getAllMetadataByType(typeTitle).get(0).getValue();

                        strTitle = strTitle.replace("¬", "");
                        int iCheck = Math.min(10, strTitle.length());
                        iCheck = Math.min(iCheck, strNew.length());

                        if (strTitle.substring(0, iCheck).contentEquals(strNew.substring(0, iCheck))) {
                            continue;
                        }

                        //if not original title, then make it a subtitle:
                        typeTitle = prefs.getMetadataTypeByName("TitleDocSub1");
                        mdTitle = new Metadata(typeTitle);
                    }

                    mdTitle.setValue(eltTitle.text());
                    docStruct.addMetadata(mdTitle);
                }

                //                for (Element eltTitle : elt.getElementsByTag("author")) {
                //                    MetadataType typeTitle = prefs.getMetadataTypeByName("Author");
                //                    Metadata mdTitle = new Metadata(typeTitle);
                //
                //                    //                        if (docStruct.getAllMetadataByType(typeTitle).size() == 0) {
                //                    mdTitle.setValue(eltTitle.text());
                //
                //                    docStruct.addMetadata(mdTitle);
                //                    //                        }
                //
                //                }
            }

            if (strName.equalsIgnoreCase("publicationstmt")) {

                for (Element eltTitle : elt.getElementsByTag("pubplace")) {
                    MetadataType typeTitle = prefs.getMetadataTypeByName("PlaceOfPublication");

                    if (!copyOfMetadata(docStruct, typeTitle, eltTitle.text())) {
                        Metadata mdTitle = new Metadata(typeTitle);
                        mdTitle.setValue(eltTitle.text());
                        docStruct.addMetadata(mdTitle);
                    }
                }

                for (Element eltTitle : elt.getElementsByTag("date")) {
                    MetadataType typeTitle = prefs.getMetadataTypeByName("PublicationYear");
                    if (!copyOfMetadata(docStruct, typeTitle, eltTitle.text())) {
                        Metadata mdTitle = new Metadata(typeTitle);
                        mdTitle.setValue(eltTitle.text());

                        docStruct.addMetadata(mdTitle);
                    }
                }
            }

        }
    }

    //Check we are not just copying a metadatum
    private boolean copyOfMetadata(DocStruct docStruct, MetadataType typeTitle, String text) {

        if (docStruct.getAllMetadataByType(typeTitle).size() == 0) {
            return false;
        }

        if (docStruct.getAllMetadataByType(typeTitle).get(0).getValue().contains(text)) {
            return true;
        }

        //otherwise
        return false;
    }

    private String getDocStructName(Element elt1) {

        String strName = elt1.text();
        if (strName.equalsIgnoreCase("[Gesamttitelblatt]") || strName.equalsIgnoreCase("[Titelblatt]")) {

            return "TitlePage";
        }
        if (strName.equalsIgnoreCase("Vorwort")) {

            return "Preface";
        }
        if (strName.equalsIgnoreCase("Inhaltsverzeichnis")) {

            return "Index";
        }

        //otherwise:
        for (Element child : elt1.children()) {

            if (child.tagName().equalsIgnoreCase("div")) {
                return "PartOfWork";
            }
        }
        return "Chapter";
    }

    /**
     * Find the specified image file in the hashmap. If it is there, copy the file to a (new, if necessary) subfolder of the main folder, named after
     * the ID of the MetsMods file. In this folder, .tif files are saved in a subfolder "master_MMName_media", .jpgs in "MMName", so that import to
     * Goobi works. Return a new DocStruct with the filename and the location of the file.
     * 
     * If page is not null, then a page for this image already exists, and the image (derivative) should be added to it, and null returned.
     * 
     * @param strDatei
     * @param mm
     * @return
     * @throws UGHException
     * @throws IOException
     */
    private DocStruct getAndSavePage(Element elt1) throws UGHException, IOException {

        //create subfolder for images, as necessary:
        String strImageFolder = strOutputPath + this.strCurrentId + "/images/";
        new File(strImageFolder).mkdirs();

        //find the file: it has 8 digits.
        String seqNo = elt1.attr("seqno");
        String strFile = seqNo + ".tif";
        int digits = seqNo.length();
        for (int i = digits; i < 8; i++) {
            strFile = "0" + strFile;
        }

        String strFilePath = strImagePath + this.strCurrentId + "/" + strFile;

        File fileCopy = null;

        //copy original file:
        String strMasterPrefix = "master_";
        String strMediaSuffix = "_media";
        String strMasterPath = strImageFolder + strMasterPrefix + this.strIdPrefix + this.strCurrentId + strMediaSuffix + File.separator;
        //        String strNormalPath = strImageFolder +this.strCurrentId  + strMediaSuffix + File.separator;

        new File(strMasterPath).mkdirs();
        //        new File(strNormalPath).mkdirs();

        Path pathSource = Paths.get(strFilePath);
        Path pathDest = Paths.get(strMasterPath + strFile);

        //if no image, return null:
        File image = new File(pathSource.toString());
        if (!image.exists()) {
            return null;
        }

        Files.copy(pathSource, pathDest, StandardCopyOption.REPLACE_EXISTING);

        //        Path pathDest2 = Paths.get(strNormalPath + pathSource.getFileName());
        //        Files.copy(pathSource, pathDest2, StandardCopyOption.REPLACE_EXISTING);

        fileCopy = new File(pathDest.toString());

        DocStructType pageType = prefs.getDocStrctTypeByName("page");
        DocStruct dsPage = mm.getDigitalDocument().createDocStruct(pageType);

        //physical page number : just increment for this folio
        MetadataType typePhysPage = prefs.getMetadataTypeByName("physPageNumber");
        Metadata mdPhysPage = new Metadata(typePhysPage);
        mdPhysPage.setValue(String.valueOf(this.iCurrentPageNo));
        dsPage.addMetadata(mdPhysPage);

        //logical page number : take the name from the img
        MetadataType typeLogPage = prefs.getMetadataTypeByName("logicalPageNumber");
        Metadata mdLogPage = new Metadata(typeLogPage);

        String strPage = elt1.attr("nativeno");

        mdLogPage.setValue(strPage);
        dsPage.addMetadata(mdLogPage);

        iCurrentPageNo++;

        ContentFile cf = new ContentFile();
        if (SystemUtils.IS_OS_WINDOWS) {
            cf.setLocation("file:" + fileCopy.getCanonicalPath());
        } else {
            cf.setLocation("file:/" + fileCopy.getCanonicalPath());
        }
        dsPage.addContentFile(cf);
        dsPage.setImageName(fileCopy.getName());

        return dsPage;

    }

    private Document getDoc(File sgml) {

        String charset = "ISO-8859-1";

        Document document = null;
        try {

            document = Jsoup.parse(sgml, charset, "");
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return document;
    }

}

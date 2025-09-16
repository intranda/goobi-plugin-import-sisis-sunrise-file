package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.SystemUtils;
import org.jdom2.JDOMException;
import org.xml.sax.SAXException;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import lombok.extern.log4j.Log4j2;
import ugh.dl.ContentFile;
import ugh.dl.Corporate;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@Log4j2
/**
 * Create MetMods files from MAB files, and save them
 * 
 * @author joel
 *
 */
public class MakeMetsMods {

    Boolean boVerbose = false;

    //these are the xml config fields:
    private String strRulesetPath = "rulesetPath";
    //    private String strOutputPath = "outputPath";
    private String strImagePathFile = "imagePathFile";

    private String strIdPrefix = "";

    private String strCurrentPath;
    private SGMLParser sgmlParser;

    //if this is not null, only import datasets with these ids
    ArrayList<String> lstIdsToImport;

    private Prefs prefs;
    private HashMap<String, String> mapTags;
    private SubnodeConfiguration config;

    private int iStopImportAfter = 0;

    //MultiVolumeWorks, keyed by their Ids
    private static HashMap<String, MetsMods> mapMVWs;

    private MetadataMaker metaMaker;
    private Boolean boWithSGML;
    //keep track of id numbers:
    ArrayList<String> lstIds;

    //and page numbers:
    int iCurrentPageNo = 1;

    //and all top level metadata:
    ArrayList<String> lstTopLevelMetadata;
    private DocStruct currentVolume;

    //special case: save everything as Monograph:
    private Boolean boAllMono = false;

    public String tempFolder;

    private MakeVolumeMap mapper;

    /**
     * ctor
     * 
     * @param config
     * @param volMaker
     * @throws PreferencesException
     * @throws ConfigurationException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public MakeMetsMods(SubnodeConfiguration config, MakeVolumeMap volMaker)
            throws PreferencesException, ConfigurationException, ParserConfigurationException, SAXException, IOException {
        setup(config);

        this.mapper = volMaker;
    }

    /**
     * Setup: creates the MetadataMaker, reads the tags list and loads config
     * 
     * @param config
     * @throws PreferencesException
     * @throws ConfigurationException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    private void setup(SubnodeConfiguration config)
            throws PreferencesException, ConfigurationException, ParserConfigurationException, SAXException, IOException {

        if (mapMVWs == null) {
            mapMVWs = new HashMap<>();
        }
        this.config = config;
        this.prefs = new Prefs();
        prefs.loadPrefs(config.getString(strRulesetPath));
        lstIds = new ArrayList<>();
        lstTopLevelMetadata = new ArrayList<>();

        iStopImportAfter = config.getInt("importFirst", 0);

        boAllMono = config.getBoolean("allMono", false);

        boWithSGML = config.getBoolean("withSGML");

        strIdPrefix = config.getString("idPrefix", "");

        if (boWithSGML) {
            sgmlParser = new SGMLParser(config);
        }

        metaMaker = new MetadataMaker(prefs);

        this.tempFolder = ConfigurationHelper.getInstance().getTemporaryFolder();
        //        this.mapMVWPath = this.tempFolder + "mapMVW.txt";
        //        this.mapChildrenPath = this.tempFolder + "mapChildren.txt";

        readTagsList();
        readIdsList();
    }

    /**
     * Go through all the datasets in the file, and create mappings for which are parents to which.
     * 
     * @param mabFile
     * @throws IOException
     * @throws UGHException
     * @throws JDOMException
     */
    public void collectMultiVolumeWorks(String mabFile) throws IOException, UGHException, JDOMException {

        String text = ParsingUtils.readFileToString(new File(mabFile));
        collectMultiVolumeWorksFromText(text);
    }

    /**
     * Go through all the datasets in the file, and create mappings for which are parents to which.
     * 
     * @param text
     * @throws IOException
     * @throws UGHException
     * @throws JDOMException
     */
    public void collectMultiVolumeWorksFromText(String text) throws IOException, UGHException, JDOMException {
        if ((text != null) && (text.length() != 0)) {

            //            mapper.readJson();

            MetsMods mm = makeMM("MultiVolumeWork");
            DocStruct logical = mm.getDigitalDocument().getLogicalDocStruct();

            //collection:
            Metadata mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("collection"));
            logical.addMetadata(mdCollection);

            BufferedReader reader = new BufferedReader(new StringReader(text));
            String str = "";

            Boolean boMVW = false;
            String strCurrentId = "";

            int iLine = 0;

            while ((str = reader.readLine()) != null) {
                str = str.trim();

                if (str.length() < 4) {
                    continue;
                }

                String tag = str.substring(0, 4);

                //finished one ?
                if (tag.contentEquals("9999") && boMVW) {

                    if (boWithSGML) {
                        sgmlParser.addSGML(mm, currentVolume, strCurrentId);
                    }

                    mapMVWs.put(strCurrentId, mm);

                    //start next
                    mm = makeMM("MultiVolumeWork");
                    logical = mm.getDigitalDocument().getLogicalDocStruct();
                    //collection:
                    mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("collection"));
                    logical.addMetadata(mdCollection);
                }

                try {

                    if (str.length() > 5) {

                        // Data field
                        int iValue = str.indexOf(":");
                        String content = str.substring(iValue + 1, str.length());

                        //Check for parent:
                        if (mapper.map != null && tag.contentEquals("0000")) {
                            boMVW = mapper.map.containsKey(content);
                        }

                        //only carry on for parents
                        if (!boMVW) {
                            continue;
                        }

                        //note: no images for MVWs

                        Metadata md = metaMaker.getMetadata(mapTags.get(tag), content);

                        if (md != null) {

                            //already have title? then include as OtherTitle
                            if ("TitleDocMain".equals(md.getType().getName())) {

                                if (logical.getAllMetadataByType(prefs.getMetadataTypeByName("TitleDocMain")).size() != 0) {
                                    md = metaMaker.getMetadata("OtherTitle", content);
                                }
                            }

                            if (md.getType().getIsPerson()) {
                                logical.addPerson((Person) md);
                            } else if (md.getType().isCorporate()) {
                                logical.addCorporate((Corporate) md);
                            } else {
                                logical.addMetadata(md);
                            }

                            //set GoobiId:
                            if ("CatalogIDDigital".equals(md.getType().getName())) {
                                strCurrentId = content;

                                //add catalogId
                                Metadata mdCatId = metaMaker.getMetadata("CatalogIdentifier", content);
                                logical.addMetadata(mdCatId);
                            }
                        }

                    }
                } catch (Exception e) {
                    log.error("Problem with " + strCurrentId + " at line " + iLine, e);
                }

                iLine++;
            }

        }
    }

    /**
     * Save the MetsMods files
     * 
     * @param mabFile
     * @throws IOException
     * @throws UGHException
     * @throws JDOMException
     */
    public void saveMMs(String mabFile) throws IOException, UGHException, JDOMException {

        String text = ParsingUtils.readFileToString(new File(mabFile));
        saveMMsFromText(text);
    }

    /**
     * Save the MetsMods files Only returns the last MetsMods, in practice it is called with text from one dataset only.
     * 
     * @param text
     * @return
     * @throws IOException
     * @throws UGHException
     * @throws JDOMException
     */
    public MetsMods saveMMsFromText(String text) throws IOException, UGHException, JDOMException {

        //        mapper.readJson();

        int iImported = 0;

        MetsMods mm = null;
        Boolean boMVW = false;
        String strMMId = "";

        if ((text != null) && (text.length() != 0)) {

            mm = makeMM(config.getString("defaultPublicationType"));
            DocStruct logical = mm.getDigitalDocument().getLogicalDocStruct();

            //collection:
            Metadata mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("collection"));
            logical.addMetadata(mdCollection);

            BufferedReader reader = new BufferedReader(new StringReader(text));
            String str = "";

            Boolean boChild = false;
            String strCurrentId = "";

            int iLine = 0;

            Boolean boIgnore = false;

            DocStruct containedWork = null;

            while ((str = reader.readLine()) != null) {
                str = str.trim();

                //finished one ?
                if (strCurrentPath != null && str.startsWith("9999") && !boMVW) {

                    //add the ContainedWork if it exists:
                    if (containedWork != null) {
                        logical.addChild(containedWork);
                        containedWork = null;
                    }

                    //                    if (boSave) {
                    try {
                        if (boWithSGML) {
                            sgmlParser.addSGML(mm, currentVolume, strCurrentId);
                        }

                        //                            log.debug("Save " + strCurrentId + " line " + iLine);
                        //                            saveMM(mm, strCurrentPath);
                    } catch (Exception e) {
                        log.error("Problem saving " + strCurrentId, e);
                    }
                    //                    }

                    //stop the import?
                    iImported++;
                    if (iStopImportAfter != 0 && iImported >= iStopImportAfter) {
                        System.out.println("Imported first" + iStopImportAfter);
                        break;
                    }
                }

                if (str.length() < 4) {
                    continue;
                }

                String tag = str.substring(0, 4);

                try {

                    if (str.length() > 5) {
                        // Data field
                        int iValue = str.indexOf(":");
                        String content = str.substring(iValue + 1, str.length());

                        //Check for parent:
                        if (tag.contentEquals("0000")) {

                            boMVW = (mapper.map != null) && mapper.map.containsKey(content);

                            //Only a child if the parent exists
                            boChild = !boAllMono && (mapper.revMap != null) && mapper.revMap.containsKey(content);
                            if (boChild) {
                                String strParent = mapper.revMap.get(content);
                                if (!mapper.map.containsKey(strParent)) {
                                    boChild = false;
                                }
                            }

                            if (!boChild) {
                                currentVolume = null;
                            }

                            //only carry if not parent
                            if (boMVW) {
                                continue;
                            }

                            strCurrentId = content;
                            strCurrentPath = tempFolder + strCurrentId + "/";

                            if (lstIdsToImport != null && !lstIdsToImport.isEmpty() && !lstIdsToImport.contains(strCurrentId)) {
                                boIgnore = true;
                                continue;
                            } else {
                                boIgnore = false;
                            }

                            if (boVerbose) {
                                System.out.println("Current path: " + strCurrentPath);
                            }

                            new File(strCurrentPath).mkdir();

                            //start new MM:
                            String strType = "Monograph";
                            if (boChild) {
                                strType = "Volume";
                            }

                            mm = makeMM(strType);

                            if (boChild) {
                                //for a volume, get the logical docstruct of the parent, and add the Volume to it:
                                String strParent = mapper.revMap.get(strCurrentId);

                                if (mapMVWs.containsKey(strParent)) {
                                    MetsMods mmParent = mapMVWs.get(strParent);
                                    mmParent = clone(mmParent);

                                    logical = mmParent.getDigitalDocument().getLogicalDocStruct();
                                    Metadata idOld = logical.getAllMetadataByType(prefs.getMetadataTypeByName("CatalogIDDigital")).get(0);
                                    idOld.setValue(strIdPrefix + idOld.getValue());
                                    //                                    logical.changeMetadata(theOldMd, theNewMd)

                                    DocStruct volume = mmParent.getDigitalDocument().createDocStruct(prefs.getDocStrctTypeByName("Volume"));
                                    currentVolume = volume;
                                    mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("collection"));
                                    volume.addMetadata(mdCollection);

                                    logical.addChild(volume);

                                    mm = mmParent;

                                    //for the rest of the data, the logical structure is that of the volume:
                                    logical = volume;
                                } else {
                                    //no parent? then treat it as a normal mono
                                    boChild = false;
                                    currentVolume = null;
                                }

                            }
                            //for a monograph, just make the MM
                            if (!boChild) {
                                logical = mm.getDigitalDocument().getLogicalDocStruct();
                                //collection:
                                mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("collection"));
                                logical.addMetadata(mdCollection);
                                currentVolume = null;
                            }

                        }

                        //only carry if not parent, and not excluded from the id list
                        if (boMVW || boIgnore) {
                            continue;
                        }

                        if (!boWithSGML && "1040".equals(tag)) {
                            addImageFiles(content, mm, strCurrentId);
                            continue;
                        }

                        String strTag = mapTags.get(tag);

                        if (strTag != null && "ContainedWork".equals(strTag)) {

                            //create ContainedWork:
                            DocStructType contWorkType = prefs.getDocStrctTypeByName("ContainedWork");
                            containedWork = mm.getDigitalDocument().createDocStruct(contWorkType);
                            containedWork.addMetadata(metaMaker.getMetadata("TitleDocMain", content));
                            continue;
                        } else if (containedWork != null && "0365".equalsIgnoreCase(tag)) {

                            //add data to ContainedWork
                            containedWork.addMetadata(metaMaker.getMetadata("Note", content));
                            continue;
                        } else if (containedWork != null && "0369".equalsIgnoreCase(tag)) {

                            //add data to ContainedWork
                            containedWork.addMetadata(metaMaker.getMetadata("PublisherName", content));
                            continue;
                        }

                        Metadata md = metaMaker.getMetadata(strTag, content);

                        if (md != null) {

                            //already have title? then include as OtherTitle
                            if ("TitleDocMain".equals(md.getType().getName())) {

                                if (logical.getAllMetadataByType(prefs.getMetadataTypeByName("TitleDocMain")).size() != 0) {
                                    md = metaMaker.getMetadata("OtherTitle", content);
                                }
                            } else if ("DocLanguage".equals(md.getType().getName())) {

                                String val = md.getValue();
                                if (val.contains(";")) {
                                    for (String part : val.split(";")) {
                                        Metadata lang = new Metadata(md.getType());
                                        lang.setValue(part);
                                        logical.addMetadata(lang);
                                    }
                                } else {
                                    logical.addMetadata(md);
                                }

                            }
                            if (md.getType().getIsPerson()) {

                                logical.addPerson((Person) md);
                            } else if (md.getType().isCorporate()) {
                                logical.addCorporate((Corporate) md);
                            } else if ("CatalogIDDigital".equals(md.getType().getName())) {

                                strCurrentId = content;
                                md.setValue(strIdPrefix + md.getValue());
                                strMMId = md.getValue();
                                logical.addMetadata(md);

                                //add catalogId
                                Metadata mdCatId = metaMaker.getMetadata("CatalogIdentifier", content);
                                logical.addMetadata(mdCatId);

                            } else {

                                //CatalogIDMainSeries from 0004 trump it from 0001
                                if ("0004".equals(tag) && !logical.getAllMetadataByType(md.getType()).isEmpty()) {
                                    logical.removeMetadata(logical.getAllMetadataByType(md.getType()).get(0));
                                }

                                logical.addMetadata(md);
                            }

                        }

                    }

                } catch (Exception e) {
                    log.error("Problem with " + strCurrentId + " at line " + iLine, e);
                }

                iLine++;
            }

        }

        Boolean boSave = true;
        if (lstIdsToImport != null && !lstIdsToImport.isEmpty() && !lstIdsToImport.contains(strMMId)) {
            boSave = false;
        }

        //for a parent, return null so that it is not saved;
        if (boMVW || !boSave) {
            return null;
        } else {
            return mm;
        }
    }

    /**
     * Deep copy. Used for creating multiple anchor files.
     * 
     * @param mmParent
     * @return
     * @throws UGHException
     */
    private MetsMods clone(MetsMods mmParent) throws UGHException {

        DocStruct logi = mmParent.getDigitalDocument().getLogicalDocStruct().copy(true, true);

        MetsMods mm = makeMM("MultiVolumeWork");
        mm.getDigitalDocument().setLogicalDocStruct(logi);

        return mm;
    }

    /**
     * Find the specified image file in the list of files and paths. If it does not exist, save it in a text file ""MISSING_Urkunden.txt" etc
     * 
     * @param strFilename
     * @param mm
     * @param strCurrentId
     * @param physical
     * @throws UGHException
     * @throws IOException
     */
    private void addImageFiles(String strValue, MetsMods mm, String strCurrentId) throws UGHException, IOException {

        String strTitel = "Dateinamen Bilder Dissprojekt: Titelblatt: ";

        String strRem = strValue.replace(strTitel, "");

        ArrayList<String> lstImages = new ArrayList<>();

        if (!strRem.contains(";")) {
            lstImages.add(strRem);
        } else {
            String[] lstStrings = strRem.split(";");
            for (String lstString : lstStrings) {
                String strImage = lstString.replace("Widmung01:", "");
                strImage = strImage.replace("Widmung02:", "");
                strImage = strImage.replace("Widmung03:", "");
                strImage = strImage.replace("Widmung04:", "");
                strImage = strImage.replace("Widmung05:", "");
                strImage = strImage.replace("Widmung06:", "");
                strImage = strImage.replace("Widmung07:", "");
                strImage = strImage.replace("Widmung08:", "");
                strImage = strImage.replace("Widmung09:", "");
                strImage = strImage.replace("Widmung10:", "");
                strImage = strImage.replace("Widmung11:", "");
                lstImages.add(strImage.trim());
            }
        }

        //Add any image files explicitly named:
        String[] words = strValue.split("\\s+");
        for (String word : words) {
            if (word.endsWith(".jpg") || word.endsWith(".tif") || word.endsWith(".tiff")) {
                lstImages.add(word);
            }
        }

        for (String strImage : lstImages) {
            DigitalDocument dd = mm.getDigitalDocument();
            DocStruct physical = dd.getPhysicalDocStruct();
            DocStruct logical = dd.getLogicalDocStruct();

            String strFilename = strImage;
            if (!strFilename.contains(".")) {
                strFilename = strFilename + ".jpg";
            }

            DocStruct page = null;

            //this returns null if the page already exists; in that case, add the file to the existing page
            page = getAndSavePage(strFilename, mm, page, strCurrentId);
            if (page != null) {
                physical.addChild(page);
                if (currentVolume != null) {
                    currentVolume.addReferenceTo(page, "logical_physical");
                } else {
                    logical.addReferenceTo(page, "logical_physical");
                }
            }
        }
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
    private DocStruct getAndSavePage(String strDatei, MetsMods mm, DocStruct page, String strCurrentId) throws UGHException, IOException {

        File file = getImageFile(strDatei, strCurrentId);
        if (file == null || !file.exists()) {

            if (boVerbose) {
                System.out.println(strCurrentId + "  -  " + strDatei);
            }
            return null;
        }

        String strId = strCurrentId;

        //otherwise:
        //create subfolder for images, as necessary:
        String strImageFolder = strCurrentPath + "images/";
        Path path = Paths.get(strImageFolder);
        StorageProvider.getInstance().createDirectories(path);

        //copy original file:
        String strMasterPrefix = "master_";
        String strMediaSuffix = "_media";
        String strMasterPath = strImageFolder + strMasterPrefix + strIdPrefix + strCurrentId + strMediaSuffix + File.separator;

        Path path2 = Paths.get(strMasterPath);
        StorageProvider.getInstance().createDirectories(path2);

        Path pathSource = Paths.get(file.getAbsolutePath());
        Path pathDest = Paths.get(strMasterPath + strDatei.toLowerCase());

        StorageProvider.getInstance().copyFile(pathSource, pathDest);
        File fileCopy = new File(pathDest.toString());

        //first time for this image?
        if (page == null) {
            DocStructType pageType = prefs.getDocStrctTypeByName("page");
            DocStruct dsPage = mm.getDigitalDocument().createDocStruct(pageType);

            //physical page number : just increment for this folio
            MetadataType typePhysPage = prefs.getMetadataTypeByName("physPageNumber");
            Metadata mdPhysPage = new Metadata(typePhysPage);
            mdPhysPage.setValue(String.valueOf(this.iCurrentPageNo));
            dsPage.addMetadata(mdPhysPage);

            //logical page number : take the file name
            MetadataType typeLogPage = prefs.getMetadataTypeByName("logicalPageNumber");
            Metadata mdLogPage = new Metadata(typeLogPage);

            String strPage = FilenameUtils.getBaseName(file.getAbsolutePath());

            if (strPage.lastIndexOf("-") != -1) {
                strPage = strPage.substring(strPage.lastIndexOf("-") + 1, strPage.length());
            }

            //remove the ID number and leading 0s from the beginning:
            if (strId != null && strId.length() > 3) {
                strPage = strPage.replace(strId, "");
                strPage = strPage.replaceFirst("^0+(?!$)", "");
            }

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
        } else {

            ContentFile cf = new ContentFile();
            if (SystemUtils.IS_OS_WINDOWS) {
                cf.setLocation("file:" + fileCopy.getCanonicalPath());
            } else {
                cf.setLocation("file:/" + fileCopy.getCanonicalPath());
            }
            page.addContentFile(cf);
            page.setImageName(fileCopy.getName());
            return null;
        }

    }

    /**
     * Find the image file, looking in the image folder and subfolders named after the Id of the current data
     * 
     * @param strImage
     * @param strCurrentId
     * @return
     */
    private File getImageFile(String strImage, String strCurrentId) {

        String strFolder = strImage.substring(0, 4).toUpperCase();
        String strPath = config.getString(strImagePathFile) + strFolder + "/" + strImage;
        File file = new File(strPath);

        if (file.exists()) {
            return file;
        }

        //otherwise just look in folder:
        strPath = config.getString(strImagePathFile);
        if (!strPath.endsWith("/")) {
            strPath = strPath + "/";
        }
        file = new File(strPath + strImage);
        if (file.exists()) {
            return file;
        }

        //otherwise look in folder named after the document:
        strPath = strPath + strCurrentId + "/";
        file = new File(strPath + strImage);
        return file;
    }

    /**
     * we have created a file listing mab tags together with their corresp MM datatypes. This method reads that file, taking it into a local hashmap
     * to be used in the xml conversion.
     * 
     * @param strFileList
     * @throws IOException
     */
    private void readTagsList() throws IOException {

        String strFileList = config.getString("tags");
        File toRead = new File(strFileList);
        FileInputStream fis = new FileInputStream(toRead);
        Scanner sc = new Scanner(fis);

        try (sc) {
            mapTags = new HashMap<>();

            //read data from file line by line:
            String currentLine;
            while (sc.hasNextLine()) {
                currentLine = sc.nextLine();

                if (currentLine.length() < 3) {
                    continue;
                }

                //now tokenize the currentLine:
                StringTokenizer st = new StringTokenizer(currentLine, " ", false);
                //put tokens ot currentLine in map
                mapTags.put(st.nextToken(), st.nextToken());
            }
        } finally {
            fis.close();
        }
    }

    /**
     * If there is a list of Ids which are to be imported, read it.
     * 
     * @throws IOException
     */
    private void readIdsList() throws IOException {

        String strFileList = config.getString("listIDs");

        if (strFileList == null || strFileList.isEmpty()) {

            Boolean boOnlyFamilies = config.getBoolean("onlyFamilies", false);

            if (boOnlyFamilies) {
                lstIdsToImport = new ArrayList<>();
                for (String parent : mapper.map.keySet()) {
                    lstIdsToImport.add(parent);
                }
                for (String child : mapper.revMap.keySet()) {
                    lstIdsToImport.add(child);
                }

                Collections.sort(lstIdsToImport);
            }

            return;
        }

        File toRead = new File(strFileList);
        FileInputStream fis = new FileInputStream(toRead);
        Scanner sc = new Scanner(fis);

        try (sc) {
            lstIdsToImport = new ArrayList<>();

            //read data from file line by line:
            String currentLine;
            while (sc.hasNextLine()) {
                currentLine = sc.nextLine();

                if (currentLine.length() < 3) {
                    continue;
                }

                String strId = currentLine.trim();
                if (!lstIdsToImport.contains(strId)) {
                    lstIdsToImport.add(strId);
                }
            }

            Collections.sort(lstIdsToImport);
        } finally {
            fis.close();
        }
    }

    /**
     * Make a Mets/Mods object, and add it to the physical DocStruct.
     * 
     * @param strType
     * @return
     * @throws UGHException
     */
    private MetsMods makeMM(String strType) throws UGHException {

        MetsMods newMM = new MetsMods(prefs);
        DigitalDocument newDD = new DigitalDocument();

        newMM.setDigitalDocument(newDD);
        DocStruct logical = newDD.createDocStruct(prefs.getDocStrctTypeByName(strType));
        newDD.setLogicalDocStruct(logical);

        DocStruct physical = newDD.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
        newDD.setPhysicalDocStruct(physical);

        //needed for some reason in Goobi:
        MetadataType MDTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");

        // check for valid filepath
        try {
            List<? extends Metadata> filepath = physical.getAllMetadataByType(MDTypeForPath);
            if (filepath == null || filepath.isEmpty()) {
                Metadata mdForPath = new Metadata(MDTypeForPath);
                if (SystemUtils.IS_OS_WINDOWS) {
                    mdForPath.setValue("file:/");
                } else {
                    mdForPath.setValue("file://");
                }
                physical.addMetadata(mdForPath);
            }
        } catch (Exception e) {
            throw new UGHException(e);
        }

        return newMM;
    }

    //    /**
    //     * Save the MetsMods file
    //     *
    //     * @param mmNew
    //     * @param strFolderForMM
    //     * @throws UGHException
    //     * @throws IOException
    //     */
    //    private void saveMM(MetsMods mmNew, String strFolderForMM) throws UGHException, IOException {
    //
    //        String strFolder = strFolderForMM;
    //
    //        if (!strFolder.endsWith("/")) {
    //            strFolder = strFolder + "/";
    //        }
    //
    //        File folder = new File(strFolder);
    //        Path path = Paths.get(strFolder);
    //        StorageProvider.getInstance().createDirectories(path);
    //        //        if (config.isMoveImage()) {
    //        //            StorageProvider.getInstance().move(imageSourceFolder, path);
    //        //        } else {
    //        //            StorageProvider.getInstance().copyDirectory(imageSourceFolder, path);
    //        //        }
    //
    //        //remove any old files:
    //        for (File file : folder.listFiles()) {
    //            if (!file.isDirectory()) {
    //                StorageProvider.getInstance().deleteFile(file.toPath());
    //            }
    //        }
    //
    //        String strFilename = strFolder + "meta.xml";
    //        mmNew.write(strFilename);
    //
    //        //reset page numbers:
    //        this.iCurrentPageNo = 1;
    //    }
}

package de.intranda.goobi.plugins;

import java.lang.reflect.Type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import ugh.dl.ContentFile;
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

public class MakeMetsMods {

    Boolean boVerbose = false;

    //these are the xml config fields:
    private String strRulesetPath = "rulesetPath";
    private String strOutputPath = "outputPath";
    private String strImagePathFile = "imagePathFile";

    private String strIdPrefix = "";

    private String strCurrentPath;
    private SGMLParser sgmlParser;

    //if this is not null, only import datasets with these ids
    ArrayList<String> lstIdsToImport;

    //Map with parents as key, and lists of their children as value
    HashMap<String, List<String>> map;

    //Map with children as key, parent as value.
    HashMap<String, String> mapRev;

    //
    private Prefs prefs;
    private HashMap<String, String> mapTags;
    private SubnodeConfiguration config;
    //    private ArrayList<MetsMods> lstMM;

    private int iStopImportAfter = 0;

    //MultiVolumeWorks, keyed by their Ids
    private HashMap<String, MetsMods> mapMVWs;

    private MetadataMaker metaMaker;
    private Boolean boWithSGML;
    //keep track of id numbers:
    ArrayList<String> lstIds;

    //and page numbers:
    int iCurrentPageNo = 1;

    //and missing files:
    ArrayList<String> lstMissingFiles;

    //and all top level metadata:
    ArrayList<String> lstTopLevelMetadata;
    private DocStruct currentVolume;

    //special case: save everything as Monograph:
    private Boolean boAllMono = false;

    //    public static void main(String[] args)
    //            throws ConfigurationException, ParserConfigurationException, SAXException, IOException, UGHException, JDOMException {
    //
    //        //        String strConfig = "/home/joel/git/rechtsgeschichte/testdiss/diss-config.xml";
    //        //        String strConfig = "/home/joel/git/rechtsgeschichte/testprivr/privrecht-config.xml";
    //        //        String strConfig = "/home/joel/git/rechtsgeschichte/data/config.xml";
    //        String strConfig = "/home/joel/git/rechtsgeschichte/testdiss4/diss-config4.xml";
    //
    //        if (args.length > 0) {
    //            strConfig = args[0];
    //        }
    //
    //        XMLConfiguration xmlConfig = new XMLConfiguration(strConfig); //ConfigPlugins.getPluginConfig("whatever");
    //        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
    //        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());
    //
    //        SubnodeConfiguration myconfig = null;
    //        myconfig = xmlConfig.configurationAt("/config[./project = 'Project']");
    //
    //        MakeMetsModsDiss maker = new MakeMetsModsDiss(myconfig);
    //
    //        maker.parse();
    //
    //    }

    public MakeMetsMods(SubnodeConfiguration config)
            throws PreferencesException, ConfigurationException, ParserConfigurationException, SAXException, IOException {
        setup(config);

    }

    private void setup(SubnodeConfiguration config)
            throws PreferencesException, ConfigurationException, ParserConfigurationException, SAXException, IOException {

        this.mapMVWs = new HashMap<String, MetsMods>();
        this.config = config;
        //        lstMM = new ArrayList<MetsMods>();
        this.prefs = new Prefs();
        prefs.loadPrefs(config.getString(strRulesetPath));
        lstIds = new ArrayList<String>();
        lstMissingFiles = new ArrayList<String>();
        lstTopLevelMetadata = new ArrayList<String>();

        iStopImportAfter = config.getInt("importFirst", 0);

        boAllMono = config.getBoolean("allMono", false);

        boWithSGML = config.getBoolean("withSGML");

        strIdPrefix = config.getString("idPrefix", "");

        if (boWithSGML) {
            sgmlParser = new SGMLParser(config);
            sgmlParser.boVerbose = boVerbose;
        }

        metaMaker = new MetadataMaker(prefs);

        readTagsList();
    }

    private void readJson() throws JsonIOException, JsonSyntaxException, FileNotFoundException {

        Gson gson = new Gson();
        Type typeMap = new TypeToken<HashMap<String, List<String>>>() {
        }.getType();
        Type typeRevMap = new TypeToken<HashMap<String, String>>() {
        }.getType();

        this.map = gson.fromJson(new FileReader(config.getString("mapMVW")), typeMap);
        this.mapRev = gson.fromJson(new FileReader(config.getString("mapChildren")), typeRevMap);

    }

    public void parseNewMabFile(String mabFile) throws IOException, UGHException, JDOMException {

        //        if (boVerbose) {
        System.out.println("1");
        //        }

        if (map == null) {
            readJson();
        }

        //        if (boVerbose) {
        System.out.println("2");
        //        }

        readTagsList();

        //        if (boVerbose) {
        System.out.println("3");
        //        }

        readIdsList();

        //        if (boVerbose) {
        System.out.println("4");
        //        }

        collectMultiVolumeWorks(mabFile);

        //        if (boVerbose) {
        System.out.println("5");
        //        }

        //now remove all map entries which do not exist as MVWs:
        removeEmptyParents();

        saveMMs(mabFile);
    }

    //Parse the MAB file specified in the config
    public void parse() throws IOException, UGHException, JDOMException {

        //        if (boVerbose) {
        System.out.println("1");
        //        }

        readJson();

        //        if (boVerbose) {
        System.out.println("2");
        //        }

        readTagsList();

        //        if (boVerbose) {
        System.out.println("3");
        //        }

        readIdsList();

        //        if (boVerbose) {
        System.out.println("4");
        //        }

        String mabFile = config.getString("mabFile");

        collectMultiVolumeWorks(mabFile);

        //        if (boVerbose) {
        System.out.println("5");
        //        }

        //now remove all map entries which do not exist as MVWs:
        removeEmptyParents();

        saveMMs(mabFile);
    }

    //Remove parents from list if they have not been created.
    private void removeEmptyParents() {

        ArrayList<String> lstEmptyParents = new ArrayList<String>();

        for (String strParent : map.keySet()) {
            if (!mapMVWs.containsKey(strParent)) {
                lstEmptyParents.add(strParent);
            }
        }

        for (String strMissingParent : lstEmptyParents) {
            map.remove(strMissingParent);
        }
    }

    public void collectMultiVolumeWorks(String mabFile) throws IOException, UGHException, JDOMException {

        String text = ParsingUtils.readFileToString(new File(mabFile));
        collectMultiVolumeWorksFromText(text);
    }

    public void collectMultiVolumeWorksFromText(String text) throws IOException, UGHException, JDOMException {
        if ((text != null) && (text.length() != 0)) {

            MetsMods mm = makeMM("MultiVolumeWork");
            DocStruct logical = mm.getDigitalDocument().getLogicalDocStruct();

            //collection:
            Metadata mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("singleDigCollection"));
            logical.addMetadata(mdCollection);

            BufferedReader reader = new BufferedReader(new StringReader(text));
            String str = "";

            Boolean boMVW = false;
            String strCurrentId = "";

            int iLine = 0;

            while ((str = reader.readLine()) != null) {
                str = str.trim();

                //finished one ?
                if (str.length() == 0 && boMVW) {

                    if (boWithSGML) {
                        sgmlParser.addSGML(mm, currentVolume, strCurrentId);
                    }

                    mapMVWs.put(strCurrentId, mm);

                    //start next
                    mm = makeMM("MultiVolumeWork");
                    logical = mm.getDigitalDocument().getLogicalDocStruct();
                    //collection:
                    mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("singleDigCollection"));
                    logical.addMetadata(mdCollection);
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
                        if (map != null && tag.contentEquals("0000")) {
                            boMVW = map.containsKey(content);

                            //                            if (boVerbose) {
                            //                                System.out.println("Id: " + content);
                            //                            }
                        }

                        //only carry on for parents
                        if (!boMVW) {
                            continue;
                        }

                        //no images for MVWs
                        //                        if (!boWithSGML && tag.equals("1040")) {
                        //                            addImageFiles(content, mm);
                        //                            continue;
                        //                        }

                        //                        //For MVW, use SeriesOrder instead of CurrentNoSorting
                        //                        if (boMVW && tag.equals("0024")) {
                        //                            tag = "0089";
                        //                        }
                        //                        
                        Metadata md = metaMaker.getMetadata(mapTags.get(tag), content);

                        if (md != null) {

                            //already have title? then include as OtherTitle
                            if (md.getType().getName().equals("TitleDocMain")) {

                                if (logical.getAllMetadataByType(prefs.getMetadataTypeByName("TitleDocMain")).size() != 0) {
                                    md = metaMaker.getMetadata("OtherTitle", content);
                                }
                            }

                            if (md.getType().getIsPerson()) {
                                logical.addPerson((Person) md);
                            } else {
                                logical.addMetadata(md);
                            }

                            //set GoobiId:
                            if (md.getType().getName().equals("CatalogIDDigital")) {
                                strCurrentId = content;

                                //add catalogId
                                Metadata mdCatId = metaMaker.getMetadata("CatalogIdentifier", content);
                                logical.addMetadata(mdCatId);
                            }
                        }

                    }
                } catch (Exception e) {
                    // TODO: handle exception
                    System.out.println("Problem with " + strCurrentId + " at line " + iLine);
                    System.out.println(e.getMessage());
                }

                iLine++;
            }

        }
    }

    public void saveMMs(String mabFile) throws IOException, UGHException, JDOMException {

        String text = ParsingUtils.readFileToString(new File(mabFile));
        saveMMsFromText(text);
    }

    //Only returns the last MetsMods.
    public MetsMods saveMMsFromText(String text) throws IOException, UGHException, JDOMException {

        int iImported = 0;
        String strFolder = config.getString(strOutputPath);
        if (!strFolder.endsWith("/")) {
            strFolder = strFolder + "/";
        }

        MetsMods mm = null;

        if ((text != null) && (text.length() != 0)) {

            mm = makeMM(config.getString("defaultPublicationType"));
            DocStruct logical = mm.getDigitalDocument().getLogicalDocStruct();

            //collection:
            Metadata mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("singleDigCollection"));
            logical.addMetadata(mdCollection);

            BufferedReader reader = new BufferedReader(new StringReader(text));
            String str = "";
            Boolean boMVW = false;
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

                    Boolean boSave = true;
                    if (lstIdsToImport != null && !lstIdsToImport.isEmpty() && !lstIdsToImport.contains(strCurrentId)) {
                        //                        System.out.println("Not saving " + strCurrentId);
                        boSave = false;
                    }

                    if (boSave) {

                        if (boWithSGML) {
                            sgmlParser.addSGML(mm, currentVolume, strCurrentId);
                        }

                        if (boVerbose) {
                            System.out.println("Save " + strCurrentId + " line " + iLine);
                        }
                        saveMM(mm, strCurrentPath);
                    }

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

                            boMVW = (map != null) && map.containsKey(content);

                            //                            if (boVerbose && boMVW) {
                            //                                System.out.println("Elt is parent: " + content);
                            //                            }

                            //Only a child if the parent exists
                            boChild = !boAllMono && (mapRev != null) && mapRev.containsKey(content);
                            if (boChild) {
                                String strParent = mapRev.get(content);
                                if (!map.containsKey(strParent)) {
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
                            strCurrentPath = strFolder + strCurrentId + "/";

                            if (lstIdsToImport != null && !lstIdsToImport.isEmpty() && !lstIdsToImport.contains(strCurrentId)) {
                                //                                System.out.println("Not saving " + strCurrentId);
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
                                String strParent = mapRev.get(strCurrentId);

                                if (mapMVWs.containsKey(strParent)) {
                                    MetsMods mmParent = mapMVWs.get(strParent);
                                    mmParent = clone(mmParent);

                                    logical = mmParent.getDigitalDocument().getLogicalDocStruct();
                                    Metadata idOld = logical.getAllMetadataByType(prefs.getMetadataTypeByName("CatalogIDDigital")).get(0);
                                    idOld.setValue(strIdPrefix + idOld.getValue());
                                    //                                    logical.changeMetadata(theOldMd, theNewMd)

                                    DocStruct volume = mmParent.getDigitalDocument().createDocStruct(prefs.getDocStrctTypeByName("Volume"));
                                    currentVolume = volume;
                                    mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("singleDigCollection"));
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
                                mdCollection = metaMaker.getMetadata("singleDigCollection", config.getString("singleDigCollection"));
                                logical.addMetadata(mdCollection);
                                currentVolume = null;
                            }

                        }

                        //only carry if not parent, and not excluded from the id list
                        if (boMVW || boIgnore) {
                            continue;
                        }

                        if (!boWithSGML && tag.equals("1040")) {
                            addImageFiles(content, mm, strCurrentId);
                            continue;
                        }

                        String strTag = mapTags.get(tag);

                        if (strTag != null && strTag.equals("ContainedWork")) {

                            //create ContainedWork:
                            DocStructType contWorkType = prefs.getDocStrctTypeByName("ContainedWork");
                            containedWork = mm.getDigitalDocument().createDocStruct(contWorkType);
                            containedWork.addMetadata(metaMaker.getMetadata("TitleDocMain", content));
                            continue;
                        } else if (containedWork != null && tag.equalsIgnoreCase("0365")) {

                            //add data to ContainedWork
                            containedWork.addMetadata(metaMaker.getMetadata("Note", content));
                            continue;
                        } else if (containedWork != null && tag.equalsIgnoreCase("0369")) {

                            //add data to ContainedWork
                            containedWork.addMetadata(metaMaker.getMetadata("PublisherName", content));
                            continue;
                        }

                        Metadata md = metaMaker.getMetadata(strTag, content);

                        if (md != null) {

                            //already have title? then include as OtherTitle
                            if (md.getType().getName().equals("TitleDocMain")) {

                                if (logical.getAllMetadataByType(prefs.getMetadataTypeByName("TitleDocMain")).size() != 0) {
                                    md = metaMaker.getMetadata("OtherTitle", content);
                                }
                            }

                            if (md.getType().getIsPerson()) {

                                logical.addPerson((Person) md);

                            } else if (md.getType().getName().equals("CatalogIDDigital")) {

                                strCurrentId = content;
                                md.setValue(strIdPrefix + md.getValue());
                                logical.addMetadata(md);

                                //add catalogId
                                Metadata mdCatId = metaMaker.getMetadata("CatalogIdentifier", content);
                                logical.addMetadata(mdCatId);

                            } else {

                                //CatalogIDMainSeries from 0004 trump it from 0001
                                if (tag.equals("0004") && !logical.getAllMetadataByType(md.getType()).isEmpty()) {
                                    logical.removeMetadata(logical.getAllMetadataByType(md.getType()).get(0));
                                }

                                logical.addMetadata(md);
                            }

                        }

                    }

                    iLine++;
                } catch (Exception e) {
                    // TODO: handle exception
                    System.out.println("Problem with " + strCurrentId + " at line " + iLine);
                    System.out.println(e.getMessage());

                    iLine++;
                }
            }

        }

        return mm;
    }

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
        if (!strValue.startsWith(strTitel)) {
            return;
        }

        String strRem = strValue.replace(strTitel, "");

        ArrayList<String> lstImages = new ArrayList<String>();

        if (!strRem.contains(";")) {
            lstImages.add(strRem);
        } else {
            String[] lstStrings = strRem.split(";");
            for (int i = 0; i < lstStrings.length; i++) {
                String strImage = lstStrings[i].replace("Widmung01:", "");
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

        for (String strImage : lstImages) {
            DigitalDocument dd = mm.getDigitalDocument();
            DocStruct physical = dd.getPhysicalDocStruct();
            DocStruct logical = dd.getLogicalDocStruct();

            String strFilename = strImage + ".jpg"; // config.getString(strImagePathFile) + strImage + ".jpg";

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
        String strImageFolder = strCurrentPath + "/images/";
        new File(strImageFolder).mkdirs();

        //copy original file:
        String strMasterPrefix = "master_";
        String strMediaSuffix = "_media";
        String strMasterPath = strImageFolder + strMasterPrefix + strIdPrefix + strCurrentId + strMediaSuffix + File.separator;
        //        String strNormalPath = strImageFolder + strCurrentId + strMediaSuffix + File.separator;

        new File(strMasterPath).mkdirs();
        //        new File(strNormalPath).mkdirs();

        Path pathSource = Paths.get(file.getAbsolutePath());
        //        Path pathDest = Paths.get(strNormalPath + strDatei.toLowerCase());
        Path pathDest = Paths.get(strMasterPath + strDatei.toLowerCase());

        //        //first aufruf: make the master file
        //        if (page == null) {
        //            pathDest = Paths.get(strMasterPath + pathSource.getFileName());
        //        }

        Files.copy(pathSource, pathDest, StandardCopyOption.REPLACE_EXISTING);

        //                Path pathDest2 = Paths.get(strNormalPath + pathSource.getFileName());
        //                Files.copy(pathSource, pathDest2, StandardCopyOption.REPLACE_EXISTING);

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

            //TODO: why is the whole path written out here, but not above??
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

        try {
            mapTags = new HashMap<String, String>();

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
            sc.close();
            fis.close();
        }
    }

    private void readIdsList() throws IOException {

        String strFileList = config.getString("listIDs");

        if (strFileList == null || strFileList.isEmpty()) {

            Boolean boOnlyFamilies = config.getBoolean("onlyFamilies", false);

            if (boOnlyFamilies) {
                lstIdsToImport = new ArrayList<String>();
                for (String parent : map.keySet()) {
                    lstIdsToImport.add(parent);
                }
                for (String child : mapRev.keySet()) {
                    lstIdsToImport.add(child);
                }

                Collections.sort(lstIdsToImport);
            }

            return;
        }

        File toRead = new File(strFileList);
        FileInputStream fis = new FileInputStream(toRead);
        Scanner sc = new Scanner(fis);

        try {
            lstIdsToImport = new ArrayList<String>();

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
            sc.close();
            fis.close();
        }
    }

    /**
     * Make a Mets/Mods object, and add it to the physical DocStruct as a BoundBook.
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

    private void saveMM(MetsMods mmNew, String strFolderForMM) throws UGHException {

        //        lstMM.add(mmNew);
        if (boVerbose) {
            System.out.println("Saving MM " + strFolderForMM);
        }

        String strFolder = strFolderForMM;

        if (!strFolder.endsWith("/")) {
            strFolder = strFolder + "/";
        }

        File folder = new File(strFolder);
        folder.mkdirs();

        //remove any old files:
        for (File file : folder.listFiles()) {
            if (!file.isDirectory()) {
                file.delete();
            }
        }

        String strFilename = strFolder + "meta.xml";
        mmNew.write(strFilename);

        //reset page numbers:
        this.iCurrentPageNo = 1;
    }
}

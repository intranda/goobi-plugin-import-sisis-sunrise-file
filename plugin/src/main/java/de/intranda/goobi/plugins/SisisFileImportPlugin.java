package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.properties.ImportProperty;
import org.jdom2.JDOMException;
import org.xml.sax.SAXException;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.UGHException;

@PluginImplementation
@Log4j2
public class SisisFileImportPlugin implements IImportPluginVersion2 {

    @Getter
    private String title = "intranda_import_sisis_sunrise_file";
    @Getter
    private PluginType type = PluginType.Import;

    @Getter
    private List<ImportType> importTypes;

    @Getter
    @Setter
    private Prefs prefs;
    @Getter
    @Setter
    private String importFolder;

    @Setter
    private MassImportForm form;

    @Setter
    private boolean testMode = false;

    @Getter
    @Setter
    private File file;

    @Setter
    private String workflowTitle;

    private boolean runAsGoobiScript = false;

    SubnodeConfiguration myconfig;
    private MakeVolumeMap volMaker;
    private MakeMetsMods mmMaker;
    private String collection;

    /**
     * Import plugin for MAB files
     * 
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws ConfigurationException
     * @throws PreferencesException
     */
    public SisisFileImportPlugin() throws PreferencesException, ConfigurationException, ParserConfigurationException, SAXException, IOException {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.FILE);

        readConfig();

        volMaker = new MakeVolumeMap(myconfig);
        mmMaker = new MakeMetsMods(myconfig, volMaker);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        try {
            myconfig = xmlConfig.configurationAt("//config[./template = '" + workflowTitle + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template = '*']");
        }

        if (myconfig != null) {
            runAsGoobiScript = myconfig.getBoolean("/runAsGoobiScript", false);
            collection = myconfig.getString("/collection", "");
        }
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        List<Record> recordList = new ArrayList<>();

        InputStream fileInputStream = null;
        try {
            String text = ParsingUtils.readFileToString(file);

            if ((text != null) && (text.length() != 0)) {

                BufferedReader reader = new BufferedReader(new StringReader(text));
                String str = "";
                String strCurrent = "";
                String strId = "";

                while ((str = reader.readLine()) != null) {
                    str = str.trim();

                    strCurrent += str;
                    strCurrent += System.lineSeparator();

                    if (str.length() < 4) {
                        continue;
                    }

                    String tag = str.substring(0, 4);

                    if (str.length() > 5) {

                        // Data field
                        int iValue = str.indexOf(":");
                        String content = str.substring(iValue + 1, str.length());

                        //Id:
                        if (tag.contentEquals("0000")) {
                            strId = myconfig.getString("idPrefix", "") + content;
                        }
                    }

                    //finished one ?
                    if (tag.startsWith("9999")) {

                        Record r = new Record();
                        r.setId(strId);
                        r.setData(strCurrent);
                        recordList.add(r);

                        strCurrent = "";
                        strId = "";
                    }
                }
                if (recordList.isEmpty()) {
                    Record r = new Record();
                    r.setId(strId);
                    r.setData(strCurrent);
                    recordList.add(r);
                }
            }

        } catch (Exception e) {
            log.error(e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }

        //While we have all the records, make a parent-child map:
        updateVolMaker(recordList);

        //now give each child record its parent, and remove parents from the list:
        return addParentsToChildRecords(recordList);
    }

    //When we have all the records, make a parent-child map:
    private void updateVolMaker(List<Record> recordList) {
        for (Record rec : recordList) {
            String strText = rec.getData();
            try {
                volMaker.addTextToMap(strText);
            } catch (IOException e) {
                log.error(e);
            }
        }

        try {
            volMaker.makeReverseMap();
        } catch (FileNotFoundException e) {
            log.error(e);
        }
    }

    //for each child, get the parent record and add it to the child record
    private List<Record> addParentsToChildRecords(List<Record> recordList) {

        String prefix = myconfig.getString("idPrefix", "");
        List<Record> noParentsList = new ArrayList<>();
        HashMap<String, Record> mapRecords = new HashMap<>();

        for (Record record : recordList) {
            mapRecords.put(record.getId(), record);
        }

        for (String childId : volMaker.revMap.keySet()) {
            Record recChild = mapRecords.get(prefix + childId);
            Record recParent = mapRecords.get(prefix +  volMaker.revMap.get(childId));

            if (recParent != null) {
                String strParent = recParent.getData();
                String strChild = recChild.getData();

                recChild.setData(strParent + System.lineSeparator() + strChild);
            }
        }

        //finally remove all the parents from the list,. returning only the children:
        for (Record record : recordList) {
            String strId = record.getId().replace(prefix, "");
            if (!volMaker.map.containsKey(strId)) {
                noParentsList.add(record);
            }
        }

        return noParentsList;
    }

    /**
     * This method is used to actually create the Goobi processes this is done based on previously created records
     */
    @Override
    public List<ImportObject> generateFiles(List<Record> records) {

        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        updateVolMaker(records);

        //collect parents:
        for (Record rec : records) {
            String strText = rec.getData();
            try {
                mmMaker.collectMultiVolumeWorksFromText(strText);
            } catch (IOException | UGHException | JDOMException e) {
                log.error("Error while creating collecting parents in the MabFileImportPlugin", e);
            }
        }

        //        mmMaker.removeEmptyParents();

        List<ImportObject> answer = new ArrayList<>();

        //then save the mms:
        for (Record rec : records) {

            ImportObject io = new ImportObject();

            String strText = rec.getData();
            try {
                Fileformat fileformat = mmMaker.saveMMsFromText(strText);

                if (fileformat == null) {
                    continue;
                }

                io.setProcessTitle(rec.getId());
                String fileName = getImportFolder() + io.getProcessTitle() + ".xml";
                io.setMetsFilename(fileName);
                fileformat.write(fileName);

                // copy all files with new folder names
                String imagefolder = mmMaker.tempFolder + rec.getId();
                String strPrefix = myconfig.getString("idPrefix", "");
                imagefolder = imagefolder.replace(strPrefix, "");

                moveSourceData(imagefolder, io.getProcessTitle());

                io.setImportReturnValue(ImportReturnValue.ExportFinished);

            } catch (IOException | UGHException | JDOMException e) {
                log.error("Error while creating Goobi processes in the MabFileImportPlugin", e);
                io.setImportReturnValue(ImportReturnValue.WriteError);
            }

            answer.add(io);
        }

        return answer;
    }

    /**
     * Move any folder containing images to the correct Goobi Process folder.
     * 
     * @param source
     * @param strProcessTitle
     * @throws IOException
     */
    private void moveSourceData(String source, String strProcessTitle) throws IOException {
        Path destinationRootFolder = Paths.get(importFolder, strProcessTitle);
        Path destinationImagesFolder = Paths.get(destinationRootFolder.toString(), "images");

        Path sourceRootFolder = Paths.get(source);
        Path sourceImageFolder = Paths.get(sourceRootFolder.toString(), "images");

        if (!Files.exists(destinationRootFolder)) {
            try {
                StorageProvider.getInstance().createDirectories(destinationRootFolder);
            } catch (IOException e) {
                log.error(e);
            }
        }

        // images
        if (Files.exists(sourceImageFolder)) {
            if (!Files.exists(destinationImagesFolder)) {
                try {
                    StorageProvider.getInstance().createDirectories(destinationImagesFolder);
                    //                    Files.createDirectories(destinationImagesFolder);
                } catch (IOException e) {
                    log.error(e);
                }
            }
            List<Path> dataInSourceImageFolder = StorageProvider.getInstance().listFiles(sourceImageFolder.toString());

            for (Path currentData : dataInSourceImageFolder) {
                if (Files.isDirectory(currentData)) {
                    try {
                        moveFolder(currentData, destinationImagesFolder);
                    } catch (IOException e) {
                        log.error(e);
                        throw e;
                    }
                } else {
                    try {
                        moveFile(currentData, Paths.get(destinationImagesFolder.toString(), currentData.getFileName().toString()));
                    } catch (IOException e) {
                        log.error(e);
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Move the folder
     * 
     * @param currentData
     * @param destinationFolder
     * @throws IOException
     */
    private void moveFolder(Path currentData, Path destinationFolder) throws IOException {
        Path destinationSubFolder;

        String foldername = currentData.getFileName().toString();
        destinationSubFolder = Paths.get(destinationFolder.toString(), foldername);

        if (!Files.exists(destinationSubFolder)) {
            StorageProvider.getInstance().createDirectories(destinationSubFolder);
        }

        StorageProvider.getInstance().move(currentData, destinationSubFolder);

    }

    /**
     * Move the file
     * 
     * @param file
     * @param destination
     * @throws IOException
     */
    private void moveFile(Path file, Path destination) throws IOException {

        StorageProvider.getInstance().move(file, destination);
    }

    /**
     * decide if the import shall be executed in the background via GoobiScript or not
     */
    @Override
    public boolean isRunnableAsGoobiScript() {
        readConfig();
        return runAsGoobiScript;
    }

    /* *************************************************************** */
    /*                                                                 */
    /* the following methods are mostly not needed for typical imports */
    /*                                                                 */
    /* *************************************************************** */

    @Override
    public List<Record> splitRecords(String string) {
        List<Record> answer = new ArrayList<>();
        return answer;
    }

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public void deleteFiles(List<String> arg0) {
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> arg0) {
        return null;
    }

    @Override
    public List<String> getAllFilenames() {
        return null;
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public String getProcessTitle() {
        return null;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public void setData(Record arg0) {
    }

    @Override
    public void setDocstruct(DocstructElement arg0) {
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        return null;
    }

}
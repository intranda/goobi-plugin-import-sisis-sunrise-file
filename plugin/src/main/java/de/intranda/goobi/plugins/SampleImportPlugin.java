package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class SampleImportPlugin implements IImportPluginVersion2 {

    @Getter
    private String title = "intranda_import_sample";
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
    private String collection;

    /**
     * define what kind of import plugin this is
     */
    public SampleImportPlugin() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.FILE);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
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
     * This method is used to generate records based on the imported data
     * these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // the list where the records are stored
        List<Record> recordList = new ArrayList<>();

        try {
            // read the file in to generate the records
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        
            // run through the content line by line
            String lines[] = content.split("\\r?\\n");

            // generate a record for each process to be created
            for (String line : lines) {
                
                // Split the string and generate a hashmap for all needed metadata
                String fields[] = line.split(";");
                HashMap<String, String> map = new HashMap<String, String>();
                String id = fields[0].trim();
                
                // put all fields into the hashmap
                map.put("ID", id);
                map.put("Author first name", fields[1].trim());
                map.put("Author last name", fields[2].trim());
                map.put("Title", fields[3].trim());
                map.put("Year", fields[4].trim());
                
                // create a record and put the hashmap with data to it
                Record r = new Record();
                r.setId(id);
                r.setObject(map);
                recordList.add(r);                
            }
        
        } catch (IOException e) {
            log.error("Error while reading the uploaded file", e);
        }

        // return the list of all generated records
        return recordList;
    }

    /**
     * This method is used to actually create the Goobi processes
     * this is done based on previously created records
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // some general preparations
        DocStructType physicalType = prefs.getDocStrctTypeByName("BoundBook");
        DocStructType logicalType = prefs.getDocStrctTypeByName("Monograph");
        MetadataType pathimagefilesType = prefs.getMetadataTypeByName("pathimagefiles");
        List<ImportObject> answer = new ArrayList<>();

        // run through all records and create a Goobi process for each of it
        for (Record record : records) {
            ImportObject io = new ImportObject();

            String id = record.getId().replaceAll("\\W", "_");
            HashMap<String, String> map = (HashMap<String, String>) record.getObject();

            // create a new mets file
            try {
                Fileformat fileformat = new MetsMods(prefs);

                // create digital document
                DigitalDocument dd = new DigitalDocument();
                fileformat.setDigitalDocument(dd);

                // create physical DocStruct
                DocStruct physical = dd.createDocStruct(physicalType);
                dd.setPhysicalDocStruct(physical);

                // set imagepath
                Metadata newmd = new Metadata(pathimagefilesType);
                newmd.setValue("/images/");
                physical.addMetadata(newmd);

                // create logical DocStruct
                DocStruct logical = dd.createDocStruct(logicalType);
                dd.setLogicalDocStruct(logical);

                // create metadata field for CatalogIDDigital with cleaned value
                Metadata md1 = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                md1.setValue(map.get("ID").replaceAll("\\W", "_"));
                logical.addMetadata(md1);

                // create metadata field for main title
                Metadata md2 = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
                md2.setValue(map.get("Title"));
                logical.addMetadata(md2);
                
                // create metadata field for year
                Metadata md3 = new Metadata(prefs.getMetadataTypeByName("PublicationYear"));
                md3.setValue(map.get("Year"));
                logical.addMetadata(md3);
                
                // add author
                Person per = new Person(prefs.getMetadataTypeByName("Author"));
                per.setFirstname(map.get("Author first name"));
                per.setLastname(map.get("Author last name"));
                //per.setRole("Author");
                logical.addPerson(per);

                // create metadata field for configured digital collection
                MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
                if (StringUtils.isNotBlank(collection)) {
                    Metadata mdc = new Metadata(typeCollection);
                    mdc.setValue(collection);
                    logical.addMetadata(mdc);
                }

                // and add all collections that where selected
                if (form != null) {
                    for (String c : form.getDigitalCollections()) {
                        if (!c.equals(collection.trim())) {
                            Metadata md = new Metadata(typeCollection);
                            md.setValue(c);
                            logical.addMetadata(md);
                        }
                    }
                }

                // set the title for the Goobi process
                io.setProcessTitle(id);
                String fileName = getImportFolder() + File.separator + io.getProcessTitle() + ".xml";
                io.setMetsFilename(fileName);
                fileformat.write(fileName);
                io.setImportReturnValue(ImportReturnValue.ExportFinished);
            } catch (UGHException e) {
                log.error("Error while creating Goobi processes in the SampleImportPlugin", e);
                io.setImportReturnValue(ImportReturnValue.WriteError);
            }

            // now add the process to the list
            answer.add(io);
        }
        return answer;
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
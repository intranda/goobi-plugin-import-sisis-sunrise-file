## Documentation for importing SISIS Sunrise files

## Description

The programme examines the stored MAB2 file and translates the metadata fields for a METS-MODS file. If available, an SGML file is also examined to specify the structural data.


## Installation and configuration

The programme consists of three files:

```
plugin_intranda_import_sisis_sunrise_file.jar
plugin_intranda_import_sisis_sunrise_file.xml
tags.txt
```

The file `"plugin_intranda_import_sisis_sunrise_file.jar"` contains the program logic and is an executable file, and should be copied into
`/opt/digiverso/goobi/plugins/import`.

The file ``goobi-plugin-import-sisis_sunrise_file.xml`` is the config file, and should be copied into `/opt/digiverso/goobi/config/`.



The file is used to configure the plug-in and must be structured as follows:

```xml
<config_plugin>
    <config>

        <!-- which workflow template shall be used -->
        <template>*</template>

        <!-- define if import shall use GoobiScript to run in the background -->
        <runAsGoobiScript>true</runAsGoobiScript>

        <!-- Ruleset for the MM files -->
        <rulesetPath>src/test/resources/ruleset.xml</rulesetPath>

        <!-- Path to images: -->
        <imagePathFile>/opt/digiverso/import/images</imagePathFile>

        <!-- Folder where the files are to be copied -->
        <outputPath>/opt/digiverso/import/test_import/</outputPath>

        <!-- Ruleset for the MM files -->
        <tags>/opt/digiverso/import/tags.txt</tags>

        <!-- Use SGML files? -->
        <withSGML>false</withSGML>

        <!-- Path to SGML files, if withSGML: -->
        <sgmlPath></sgmlPath>

        <!-- default publication type if it cannot be detected. If missing or empty, no record will be created -->
        <defaultPublicationType>Monograph</defaultPublicationType>

        <!-- Collection name -->
        <collection>Disserationen test</collection>

       <!-- Mapping for MultiVolumeWork to child Volumes -->
       <mapMVW>/opt/digiverso/import/test_import/map.txt</mapMVW>
    
       <!-- Mapping for child Volumes to parent MultiVolumeWork -->
       <mapChildren>/opt/digiverso/import/test_import/reverseMap.txt</mapChildren>

        <!-- Prefix to add to every ID number -->        
        <idPrefix>mpirg_sisis_</idPrefix>
        
        <!-- Remove the image files from their original folders -->   
        <moveFiles>false</moveFiles>
        
       <!-- List of IDs to import. If empty, import all files -->
       <listIDs></listIDs>
       
    </config>
</config_plugin>
```

A copy is in this repro, in the "resources" folder.

The element `"rulesetPath"`
returns the path to the ruleset for the MetsMods files.

The element `"imagePathFile"`
is the path to the image files, which are located either in the folder itself or in subfolders with the name of the CatalogId. 

The element `"outputPath"`
is where the MM folders are temporarily copied to, in subfolders named after the CatalogId.

The element `"tags"`
element specifies the translation file that translates mab2 codes into MM metadata.

If `"withSGML"` is `true`, then the `"sgmlPath"` folder is searched for SGMl files, with CatalogID as name. These are used to give structure to the MM.

The element `"defaultPublicationType"`
specifies the MM Type of the document if it has no children or parents. A document with children is imported as MultiVolumeWork, the children are imported as Volumes.

The element `"collection"`
specifies the metadata singleDigCollection for the MM files.

The element `"mapMVW"`
specifies the path to a JSON file where the MultiVolumeWork IDs are stored, together with a list of the IDs of each volume that belongs to it.

The element `"mapChildren"`
specifies the path to a JSON file that stores the same mapping in reverse. So for each volume ID belonging to a WMD, the ID of the parent is mapped.  

The element `"listIDs"`
specifies the path to a text file containing a list fo Ids. If this is specified, then only datasets with these ids will be imported from the sisis file. 

## Mode of operation

The working method is as follows: To use the import, the mass import area must be opened in the process templates and the `intranda_import_sisis_sunrise_file` must be selected in the File upload import tab. A MAB file can then be uploaded and imported.


### Import

* The mappings mapMVW and mapChildren are created, and saved as json files in `outputPath`
* For each dataset in the file, a MetsMods document is created, with anchor file if necessary. The translation of each field happens using the tags file.
* For each page in the document, images are searched for in the `"imagePathFile"` folder, in the folder istelf and in subfolders with CatalogID as name. These are then copied to the image folder, and references made in the structmap.
* For each new MetsMods a folder with the CatalogID as name is created in `outputPath`, containing the MM files and images subfolders. 
* Each of these folder is then imported into Goobi Workflow as a Process, named with CatalogId and a prefix as specified in the config file.


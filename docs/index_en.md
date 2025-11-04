---
title: Import of Sisis SunRise Files
identifier: intranda_import_sisis_sunrise_files
published: true
description: This is technical documentation for the plugin to import Sisis SunRise files to processes in Goobi workflow.
keywords:
    - Goobi workflow
    - Plugin
    - Import Plugin
---
## Introduction
This documentation describes the installation, configuration and use of the plugin to import Sisis SunRise files.


## Installation
The plugin must be installed in the following folder:

```bash
/opt/digiverso/goobi/plugins/import/plugin_intranda_import_sisis_sunrise_file-base.jar
```

There is also a configuration file, which must be located at the following location:

```bash
/opt/digiverso/goobi/config/plugin_intranda_import_sisis_sunrise_file.xml
```

Additionally there is a `tags` file, whose location is specified in the configuration file:

```xml
<tags>/path/to/tags.txt</tags>
```


## Overview and functionality
To use the import, the mass import area must be opened in the process templates and the `intranda_import_sisis_sunrise_file` plugin must be selected in the `File upload import` tab. A Sisis SunRise file can then be uploaded and imported.

The import takes place in several steps. First the whole file is read, and the maps child-parent and parent-children are created and saved (as JSON files) in the Goobi `temp` folder for the current user. These maps are used to create anchor files in the next step.

The Sisis SunRise file is then broken into individual records. For each record, the process title is generated from the Catalogue Identifier (and any prefix specified in the configuration file) and checked to see whether the process already exists in Goobi. If this is not the case, the process is created and the configured metadata for `anchor` and `volume` is saved temporarily in a folder in the output path specified in the configuration. Any images are copied into an ´images\` subfolder.

In the next step all these folders, containing the MetsMods files and the images, are imported into Goobi workflow as processes, and moved to the appropriate folders in Goobi.


## Configuration
The configuration is done via the configuration file `plugin_intranda_import_sisis_sunrise_file.xml` and can be adapted during operation.

```xml
<config_plugin>
    <config>
        <!-- which workflow template shall be used -->
        <template>*</template>

        <!-- define if import shall use GoobiScript to run in the background -->
        <runAsGoobiScript>true</runAsGoobiScript>

        <!-- Ruleset for the MM files: -->
        <rulesetPath>src/test/resources/ruleset.xml</rulesetPath>

        <!-- Path to images: -->
        <imagePathFile>/opt/digiverso/import/images</imagePathFile>

        <!-- Ruleset for the MM files -->
        <tags>/opt/digiverso/import/tags.txt</tags>

        <!-- Use SGML files? -->
        <withSGML>false</withSGML>

        <!-- Path to SGML files, if withSGML -->
        <sgmlPath></sgmlPath>

        <!-- default publication type if it cannot be detected. If missing or empty, no record will be created -->
        <defaultPublicationType>Monograph</defaultPublicationType>

        <!-- Collection name -->
        <collection>Disserationen</collection>

        <!-- Prefix to add to every ID number -->        
        <idPrefix>mpilhlt_sisis_</idPrefix>

        <!-- Remove the image files from their original folders -->   
        <moveFiles>false</moveFiles>

       <!-- List of IDs to import. If empty, import all files -->
       <listIDs></listIDs>

    </config>
</config_plugin>
```

The configuration allows different configurations for different process templates. For this purpose, only the name of the desired template must be entered in the `template` field. The entry with the value `*` is used for all templates for which no separate configuration exists.

| Configuration Element | Use |
| :--- | :--- |
| `rulesetPath` | This is the path to the ruleset for the MetsMods files. |
| `imagePathFile` | This parameter defines the path to the image files, which are located either in the folder itself or in subfolders with the name of the Catalogue identifier. |
| `tags` | This parameter defines the translation file that translates the codes into metadata. |
| `withSGML` | If this parameter is set to `true`, then SGML files are used. Note that this is currently not in use, but intended for a later version. |
| `sgmlPath` | If SGML files are used, this is the folder in which they are found. |
| `defaultPublicationType` | With this parameter the Type of the document is defined if it has no children or parents. A document with children is imported as MultiVolumeWork, the children are imported as Volumes. |
| `collection` | This specifies the metadata `singleDigCollection` for the MetsMods files, the name of the collection to which the works belong. |
| `listIDs` | Here you define the path to a text file containing a list of Catalogue Identifiers. If this field is not empty, then only datasets with these Catalogue Identifiers will be imported from the Sisis SunRise file. |


## Tags

A tags file may look something like this:

```bash
0000 CatalogIDDigital
0014 shelfmarksource
0015 DocLanguage
0024 CurrentNoSorting
0025 CurrentNoSorting
0089 CurrentNo
0100 Author
0101 OtherPerson
0200 Corporation
0201 Corporation
0304 OtherTitle
0310 TitleDocMain
0331 TitleDocMain
0335 TitleDocSub1
0341 OtherTitle
0359 StatementOfResponsibility
0361 Note
0403 EditionStatement
0410 PlaceOfPublication
0412 PublisherName
0413 Printer
0425 PublicationYear
0432 Stock
0433 physicalDescriptionExtent
0440 PrintingLocation
0451 TitleDocMainSeries
0501 Note
0519 DissertationNote
0540 ISBN
0590 Resource
0655 URLbib
0902 SubjectTopic
0907 SubjectTopic
0912 SubjectTopic
0917 SubjectTopic
0922 SubjectTopic
0927 SubjectTopic
0932 SubjectTopic
0937 SubjectTopic
1371 OtherTitle
```

Each line contains a code, followed by the name of the metadata which it should be translated to. Every metadata type in the list must be defined in the ruleset used for the project into which the file is to be imported, and the `CatalogIDDigital` must be defined, as it is used to create the process ID.

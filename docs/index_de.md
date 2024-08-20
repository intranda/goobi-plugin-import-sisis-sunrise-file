---
title: Import von Sisis SunRise Dateien
identifier: intranda_import_sisis_sunrise_files
published: true
description: Dies ist die technische Dokumentation für das Plugin zum Import von Sisis SunRise-Dateien als Vorgänge in Goobi workflow.
---
## Einführung
Diese Dokumentation beschreibt die Installation, Konfiguration und Verwendung des Plugins zum Importieren von Sisis SunRise-Dateien.


## Installation
Das Plugin muss in folgendem Ordner installiert werden:

```bash
/opt/digiverso/goobi/plugins/import/plugin_intranda_import_sisis_sunrise_file-base.jar
```

Es gibt auch eine Konfigurationsdatei, die sich an folgendem Ort befinden muss:

```bash
/opt/digiverso/goobi/config/plugin_intranda_import_sisis_sunrise_file.xml
```

Zusätzlich gibt es eine `tags`-Datei, deren Speicherort in der Konfigurationsdatei angegeben wird:

```xml
<tags>/path/to/tags.txt</tags>
```


## Überblick und Funktionsweise
Um den Import zu nutzen, muss in den Produktionsvorlagen der Bereich "Massenimport" geöffnet werden und im Reiter "Datei-Upload-Import" das Plugin "intranda_import_sisis_sunrise_file" ausgewählt werden. Anschließend kann eine Sisis SunRise-Datei hochgeladen und importiert werden.

Der Import findet in mehreren Schritten statt. Zunächst wird die gesamte Datei eingelesen, und die Maps child-parent und parent-children werden erstellt und (als JSON-Dateien) im Goobi `temp` Ordner für den aktuellen Benutzer gespeichert. Diese Maps werden im nächsten Schritt zur Erstellung von Ankerdateien verwendet.

Die Sisis SunRise-Datei wird dann in einzelne Datensätze zerlegt. Für jeden Datensatz wird aus dem Katalog-Identifier (und einem eventuell in der Konfigurationsdatei angegebenen Präfix) der Prozesstitel generiert und geprüft, ob der Prozess bereits in Goobi existiert. Ist dies nicht der Fall, wird der Prozess angelegt und die konfigurierten Metadaten für `Anchor` und `Volume` in einem Ordner in dem in der Konfiguration angegebenen Ausgabepfad zwischengespeichert. Eventuelle Bilder werden in einen Unterordner `images` kopiert.

Im nächsten Schritt werden alle diese Ordner, die die MetsMods-Dateien und die Bilder enthalten, als Vorgänge in Goobi workflow importiert und in die entsprechenden Ordner in Goobi verschoben.


## Konfiguration
Die Konfiguration erfolgt über die Konfigurationsdatei `plugin_intranda_import_sisis_sunrise_file.xml` und kann im laufenden Betrieb angepasst werden.

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

Die Konfiguration erlaubt unterschiedliche Konfigurationen für verschiedene Produktionsvorlagen. Dazu muss im Feld `Template` nur der Name des gewünschten Vorlage eingetragen werden. Der Eintrag mit dem Wert `*` wird für alle Vorlagen verwendet, für die keine eigene Konfiguration existiert.

| Konfigurationselement | Verwendung |
| :--- | :--- |
| `rulesetPath` | Dies ist der Pfad zum Regelsatz für die MetsMods-Dateien. |
| `imagePathFile` | Dieser Parameter definiert den Pfad zu den Bilddateien, die sich entweder im Ordner selbst oder in Unterordnern mit dem Namen der Katalogkennung befinden. |
| `tags` | Dieser Parameter definiert die Übersetzungsdatei, die die Codes in Metadaten übersetzt. |
| `withSGML` | Wenn dieser Parameter auf `true` gesetzt ist, dann werden SGML-Dateien verwendet. Beachten Sie, dass dies derzeit nicht verwendet wird, sondern für eine spätere Version vorgesehen ist. |
| `sgmlPath` | Wenn SGML-Dateien verwendet werden, ist dies der Ordner, in dem sie sich befinden. |
| `defaultPublicationType` | Mit diesem Parameter wird der Typ des Dokuments definiert, wenn es keine Kinder oder Eltern hat. Ein Dokument mit Kindern wird als MultiVolumeWork importiert, die Kinder werden als Volumes importiert. |
| `collection` | Dies gibt die Metadaten `singleDigCollection` für die MetsMods-Dateien an, den Namen der Sammlung, zu der die Werke gehören. |
| `listIDs` | Hier definieren Sie den Pfad zu einer Textdatei, die eine Liste von Katalogkennungen enthält. Wenn dieses Feld nicht leer ist, dann werden nur Datensätze mit diesen Katalogkennungen aus der Sisis SunRise-Datei importiert. |


## Tags
Eine Tag-Datei kann etwa so aussehen:

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

Jede Zeile enthält einen Code, gefolgt von dem Namen der Metadaten, in die er übersetzt werden soll. Jeder Metadatentyp in der Liste muss in dem Regelsatz definiert sein, der für das Projekt verwendet wird, in das die Datei importiert werden soll, und die `CatalogIDDigital` muss definiert sein, da sie zur Erstellung der Prozess-ID verwendet wird.

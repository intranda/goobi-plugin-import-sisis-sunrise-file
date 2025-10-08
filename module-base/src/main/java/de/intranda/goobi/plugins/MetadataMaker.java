package de.intranda.goobi.plugins;

import ugh.dl.Corporate;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;

/**
 * Class for making Metadata out of strings
 * 
 */
public class MetadataMaker {

    private Prefs prefs;

    /**
     * ctor
     *
     * @param prefs
     */
    public MetadataMaker(Prefs prefs) {

        this.prefs = prefs;

    }

    /**
     * Get a Metadata object of the specified type and content
     *
     * @param strElt
     * @param content
     * @return
     * @throws MetadataTypeNotAllowedException
     */
    public Metadata getMetadata(String strElt, String content) throws MetadataTypeNotAllowedException {

        if (strElt == null) {
            return null;
        }

        MetadataType type = prefs.getMetadataTypeByName(strElt);

        if (type.getIsPerson()) {
            Person person = new Person(type);
            String[] parts = content.split(",");
            person.setLastname(parts[0]);
            person.setFirstname("");

            if (parts.length > 1) {
                StringBuilder strFirst = new StringBuilder().append(parts[1]);
                for (int i = 2; i < parts.length; i++) {
                    strFirst.append(parts[i]);
                }

                person.setFirstname(strFirst.toString());
            }

            return person;
        } else if (type.isCorporate()) {
            Corporate c = new Corporate(type);
            c.setMainName(content);
            return c;
        } else {
            Metadata md = new Metadata(type);
            if ("CatalogIDMainSeries".equals(strElt) && content.startsWith("000")) {
                content = content.replaceFirst("000", "");
            }
            md.setValue(content);
            return md;
        }
    }
}

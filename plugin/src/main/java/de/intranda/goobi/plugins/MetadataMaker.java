package de.intranda.goobi.plugins;

import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;

public class MetadataMaker {

    private Prefs prefs;

    public MetadataMaker(Prefs prefs) {

        this.prefs = prefs;

    }

    public Metadata getMetadata(String strElt, String content) throws MetadataTypeNotAllowedException {

        if (strElt == null) {
            return null;
        }

        MetadataType type = prefs.getMetadataTypeByName(strElt);
        
        Metadata md = new Metadata(type);
        if (type.getIsPerson()) { // .getName().equals("Author")) {
            md = new Person(type);
            setPersonData(md, content);
        } else {
            if (strElt.equals("CatalogIDMainSeries")&& content.startsWith("000")) {
                content = content.replaceFirst("000", "");
            }
            md.setValue(content);
        }

        return md;

    }

    private void setPersonData(Metadata md, String content) {

        Person person = (Person) md;
        String[] parts = content.split(",");
        person.setLastname(parts[0]);
        person.setFirstname("");
        
        if (parts.length > 1) {
            String strFirst = parts[1];
            for (int i = 2; i < parts.length; i++) {
                strFirst += parts[i];
            }

            person.setFirstname(strFirst);
        }
    }

}

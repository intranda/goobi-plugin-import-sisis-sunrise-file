package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.jdom2.JDOMException;
import org.xml.sax.SAXException;

import com.google.gson.Gson;

import ugh.exceptions.UGHException;

public class MakeVolumeMap {

    //    private ArrayList<String> lstFilesMulti;
    public ArrayList<String> lstFilesVol;
    private ArrayList<String> lstTops;
    private ArrayList<String> lstChildren;

    //    private String strMap = "/home/joel/git/rechtsgeschichte/map.xml";
    private String strIds = "/home/joel/git/rechtsgeschichte/ids.txt";
    public String mapFile = "/home/joel/git/rechtsgeschichte/map.txt";
    public String reverseMapFile = "/home/joel/git/rechtsgeschichte/reverseMap.txt";
    private HashMap<String, ArrayList<String>> map;
    private HashMap<String, String> reverseMap;

    public static void main(String[] args) throws ConfigurationException, ParserConfigurationException, SAXException, IOException, UGHException,
            JDOMException, TransformerException {

        MakeVolumeMap maker = new MakeVolumeMap(null);
        //        maker.lstFilesMulti = new ArrayList<String>();
        //        maker.lstFilesMulti.add("/home/joel/git/rechtsgeschichte/final_data/mw_nicht_uw");
        //        maker.lstFilesMulti.add("/home/joel/git/rechtsgeschichte/final_data/stueck_nicht_uw");
        //        maker.lstFilesMulti.add("/home/joel/git/rechtsgeschichte/final_data/bd_nicht_uw");

        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/uw_stueck");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/uw_mw");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/uw_bd");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/mw_nicht_uw");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/stueck_nicht_uw");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/bd_nicht_uw");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/uw_rest");
        maker.lstFilesVol.add("/home/joel/git/rechtsgeschichte/final_data/mono");

        maker.parse();

    }

    public MakeVolumeMap(SubnodeConfiguration config) {

        lstTops = new ArrayList<String>();
        lstFilesVol = new ArrayList<String>();
        lstChildren = new ArrayList<String>();
        map = new HashMap<String, ArrayList<String>>();
        reverseMap = new HashMap<String, String>();

        if (config != null) {

            mapFile = config.getString("mapMVW");
            reverseMapFile = config.getString("mapChildren");

            strIds = config.getString("mabFile");
        }

    }

    public void parse() throws IOException, JDOMException, ParserConfigurationException, TransformerException {

        //        makeTextFile();

        makeTree1(strIds);

        makeTree2(strIds);

        makeReverseMap();

        saveGson();

        System.out.println("Done creating JSON File");
    }

    void makeReverseMap() {

        for (String parent : map.keySet()) {

            for (String child : map.get(parent)) {

                reverseMap.put(child, parent);
            }
        }
    }

    public void addFileToMap(String strFile) throws IOException {

        makeTree1(strFile);
        makeTree2(strFile);
    }
    
    public void addTextToMap(String strText) throws IOException {

        makeTree1FromText(strText);
        makeTree2FromText(strText);
    }

    //fisrt, make sure all 0001s are parents
    private void makeTree1(String strFile) throws IOException {

        String text = ParsingUtils.readFileToString(new File(strFile));
        makeTree1FromText(text);
    }

    private void makeTree1FromText(String text) throws IOException {

        String strIdCurrent = "";
        String str1 = "";
        String str4 = "";
      
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String str = "";

        while ((str = reader.readLine()) != null) {

            str = str.trim();

            if (str.length() < 4) {
                continue;
            }

            String tag = str.substring(0, 4);

            try {
                //get current id
                if (tag.equals("0000")) {
                    int iValue = str.indexOf(":");
                    strIdCurrent = str.substring(iValue + 1, str.length()).trim();
                }

                if (tag.equals("0001")) {
                    int iValue = str.indexOf(":");
                    str1 = str.substring(iValue + 1, str.length()).trim();
                }

                if (tag.equals("0004")) {
                    int iValue = str.indexOf(":");

                    //if no 1, use 4 instead:
                    if (str1.contentEquals("")) {
                        str1 = str.substring(iValue + 1, str.length()).trim();
                    } else {
                        str4 = str.substring(iValue + 1, str.length()).trim();
                    }
                }

                if (tag.equals("9999")) {

                    if (!str1.isEmpty() && !map.keySet().contains(str1)) {
                        map.put(str1, new ArrayList<String>());
                    }

                    if (!str1.isEmpty() && map.keySet().contains(str1)) {
                        ArrayList<String> lstSub = map.get(str1);
                        if (!lstSub.contains(strIdCurrent)) {
                            lstSub.add(strIdCurrent);
                            lstChildren.add(strIdCurrent);

                            map.put(str1, lstSub);
                        }

                        if (!str4.isEmpty() && !lstSub.contains(str4)) {
                            lstSub.add(str4);
                            lstChildren.add(str4);

                            map.put(str1, lstSub);
                        }
                    }

                    strIdCurrent = "";
                    str1 = "";
                    str4 = "";
                }

            } catch (Exception e) {
                // TODO: handle exception
                System.out.println(e.getMessage());
            }
        }
    }

    //Now all 0001s are parents; add any 0004s not children
    private void makeTree2(String strFile) throws IOException {

        String text = ParsingUtils.readFileToString(new File(strFile));
        
        makeTree2FromText(text);
    }

    private void makeTree2FromText(String text) throws IOException {
        
        String strIdCurrent = "";
        String str1 = "";
        String str4 = "";
        
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String str = "";

        while ((str = reader.readLine()) != null) {

            str = str.trim();

            if (str.length() < 4) {
                continue;
            }

            String tag = str.substring(0, 4);

            try {
                //get current id
                if (tag.equals("0000")) {
                    int iValue = str.indexOf(":");
                    strIdCurrent = str.substring(iValue + 1, str.length()).trim();
                }

                if (tag.equals("0001")) {
                    int iValue = str.indexOf(":");
                    str1 = str.substring(iValue + 1, str.length()).trim();
                }

                if (tag.equals("0004")) {
                    int iValue = str.indexOf(":");
                    str4 = str.substring(iValue + 1, str.length()).trim();
                }

                if (tag.equals("9999")) {

                    if (!str4.isEmpty() && !map.keySet().contains(str4) && !lstChildren.contains(str4)) {
                        map.put(str4, new ArrayList<String>());
                    }

                    if (!str4.isEmpty() && map.keySet().contains(str4) && !lstChildren.contains(strIdCurrent)) {
                        ArrayList<String> lstSub = map.get(str4);
                        if (!lstSub.contains(strIdCurrent)) {
                            lstSub.add(strIdCurrent);
                            lstChildren.add(strIdCurrent);

                            map.put(str4, lstSub);
                        }
                    }

                    strIdCurrent = "";
                    str1 = "";
                    str4 = "";
                }

            } catch (Exception e) {
                // TODO: handle exception
                System.out.println(e.getMessage());
            }
        }
    }

    private void makeTextFile() throws IOException {

        String strIdCurrent = "";

        try (PrintWriter out = new PrintWriter(strIds)) {

            for (String strFile : lstFilesVol) {

                out.println();
                out.println("##########################################################");
                out.println(FilenameUtils.getName(strFile));
                out.println();

                System.out.println("Parsing " + strFile);
                String text = ParsingUtils.readFileToString(new File(strFile));

                if ((text != null) && (text.length() != 0)) {

                    BufferedReader reader = new BufferedReader(new StringReader(text));
                    String str = "";

                    while ((str = reader.readLine()) != null) {

                        str = str.trim();

                        if (str.length() < 4) {
                            continue;
                        }

                        String tag = str.substring(0, 4);

                        try {
                            //get current id
                            if (tag.equals("0000")) {
                                int iValue = str.indexOf(":");
                                strIdCurrent = str.substring(iValue + 1, str.length()).trim();

                                out.println(tag + ":" + strIdCurrent);
                            }

                            if (tag.equals("0001")) {
                                int iValue = str.indexOf(":");
                                String idVerweisSSW = str.substring(iValue + 1, str.length()).trim();
                                if (idVerweisSSW.startsWith("000")) {
                                    idVerweisSSW = idVerweisSSW.replaceFirst("000", "");
                                }

                                out.println(tag + ":" + idVerweisSSW);
                                lstTops.add(idVerweisSSW);
                            }

                            if (tag.equals("0004")) {
                                int iValue = str.indexOf(":");
                                String idVerweisSSW = str.substring(iValue + 1, str.length()).trim();

                                out.println(tag + ":" + idVerweisSSW);
                            }

                            if (tag.equals("9999")) {
                                out.println("9999");
                                out.println();
                            }

                        } catch (Exception e) {
                            // TODO: handle exception
                            System.out.println(e.getMessage());
                        }
                    }

                }
            }

        }
    }

    public String getMapGson() {
        Gson gson = new Gson();
        return gson.toJson(map);
    }

    public String getRevMapGson() {
        Gson gson = new Gson();
        return gson.toJson(reverseMap);
    }

    private void saveGson() throws FileNotFoundException {
        Gson gson = new Gson();
        String str = gson.toJson(map);

        try (PrintWriter out = new PrintWriter(mapFile)) {
            out.println(str);
        }

        String str2 = gson.toJson(reverseMap);

        try (PrintWriter out = new PrintWriter(reverseMapFile)) {
            out.println(str2);
        }
    }

}

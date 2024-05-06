package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import de.sub.goobi.config.ConfigurationHelper;
import lombok.extern.log4j.Log4j2;

/**
 * Class for making parent-child maps from data in a MAB file.
 * 
 */
@Log4j2
public class MakeVolumeMap {

    public ArrayList<String> lstFilesVol;
    private ArrayList<String> lstChildren;

//    public String mapFile = "";
//    public String reverseMapFile = "";
    public HashMap<String, ArrayList<String>> map;
    public HashMap<String, String> revMap;

    /**
     * ctor
     * 
     * @param config
     */
    public MakeVolumeMap(SubnodeConfiguration config) {

        lstFilesVol = new ArrayList<String>();
        lstChildren = new ArrayList<String>();
        map = new HashMap<String, ArrayList<String>>();
        revMap = new HashMap<String, String>();

//        String tempFolder = ConfigurationHelper.getInstance().getTemporaryFolder();
//        this.mapFile = tempFolder + "mapMVW.txt";
//        this.reverseMapFile = tempFolder + "mapChildren.txt";

    }

    /**
     * Make the child-parent HasMap
     * @throws FileNotFoundException 
     */
    void makeReverseMap() throws FileNotFoundException {

        for (String parent : map.keySet()) {

            for (String child : map.get(parent)) {

                revMap.put(child, parent);
            }
        }
        
//        saveGson();
    }

    /**
     * Parse the specified file
     * 
     * @param strFile
     * @throws IOException
     */
    public void addFileToMap(String strFile) throws IOException {

        makeTree1(strFile);
        makeTree2(strFile);
    }

    /**
     * Parse the spcecified text
     * 
     * @param strText
     * @throws IOException
     */
    public void addTextToMap(String strText) throws IOException {

        makeTree1FromText(strText);
        makeTree2FromText(strText);
    }

    /**
     * first, make sure all 0001s are parents
     * 
     * @param strFile
     * @throws IOException
     */
    private void makeTree1(String strFile) throws IOException {

        String text = ParsingUtils.readFileToString(new File(strFile));
        makeTree1FromText(text);
    }

    /**
     * first, make sure all 0001s are parents
     * 
     * @param text
     * @throws IOException
     */
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
                log.error(e);
            }
        }
    }

    /**
     * Now all 0001s are parents; add any 0004s not children
     * 
     * @param strFile
     * @throws IOException
     */
    private void makeTree2(String strFile) throws IOException {

        String text = ParsingUtils.readFileToString(new File(strFile));

        makeTree2FromText(text);
    }

    /**
     * Now all 0001s are parents; add any 0004s not children
     * 
     * @param text
     * @throws IOException
     */
    private void makeTree2FromText(String text) throws IOException {

        String strIdCurrent = "";
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
                    str.substring(iValue + 1, str.length()).trim();
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
                    str4 = "";
                }

            } catch (Exception e) {
                log.error(e);
            }
        }
    }
//
//    private void saveGson() throws FileNotFoundException {
//        Gson gson = new Gson();
//        String str = gson.toJson(map);
//
//        try (PrintWriter out = new PrintWriter(mapFile)) {
//            out.println(str);
//        }
//
//        String str2 = gson.toJson(revMap);
//
//        try (PrintWriter out = new PrintWriter(reverseMapFile)) {
//            out.println(str2);
//        }
//    }
//    
//    public void readJson() throws JsonIOException, JsonSyntaxException, FileNotFoundException {
//
//        Gson gson = new Gson();
//        Type typeMap = new TypeToken<HashMap<String, List<String>>>() {
//        }.getType();
//        Type typeRevMap = new TypeToken<HashMap<String, String>>() {
//        }.getType();
//
//        
//        this.map = gson.fromJson(new FileReader(mapFile), typeMap);
//        this.revMap = gson.fromJson(new FileReader(reverseMapFile), typeRevMap);
//
//    }

}

package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import com.google.gson.Gson;

/**
 * Class for making parent-child maps from data in a MAB file.
 * 
 */
public class MakeVolumeMap {

    public ArrayList<String> lstFilesVol;
    private ArrayList<String> lstTops;
    private ArrayList<String> lstChildren;

    private String strIds = "";
    public String mapFile = "";
    public String reverseMapFile = "t";
    private HashMap<String, ArrayList<String>> map;
    private HashMap<String, String> reverseMap;

    /**
     * ctor
     * 
     * @param config
     */
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

    /**
     * Make the child-parent HasMap
     */
    void makeReverseMap() {

        for (String parent : map.keySet()) {

            for (String child : map.get(parent)) {

                reverseMap.put(child, parent);
            }
        }
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
     * @param strText
     * @throws IOException
     */
    public void addTextToMap(String strText) throws IOException {

        makeTree1FromText(strText);
        makeTree2FromText(strText);
    }

    /**
     * first, make sure all 0001s are parents
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
                // TODO: handle exception
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Now all 0001s are parents; add any 0004s not children
     * @param strFile
     * @throws IOException
     */
    private void makeTree2(String strFile) throws IOException {

        String text = ParsingUtils.readFileToString(new File(strFile));

        makeTree2FromText(text);
    }

    /**
     * Now all 0001s are parents; add any 0004s not children
     * @param text
     * @throws IOException
     */
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

    /**
     * The Parent-Children map
     * @return
     */
    public String getMapGson() {
        Gson gson = new Gson();
        return gson.toJson(map);
    }

    /**
     * The Child-Parent map
     * @return
     */
    public String getRevMapGson() {
        Gson gson = new Gson();
        return gson.toJson(reverseMap);
    }

}

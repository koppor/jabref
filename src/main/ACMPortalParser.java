package org.jabref.logic.importer.fileformat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.Parser;
import org.jabref.logic.net.URLDownload;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import org.apache.http.client.utils.URIBuilder;

public class ACMPortalParser implements Parser {
    public static final String DOI_URL = "https://dl.acm.org/action/exportCiteProcCitation";
    public List<BibEntry> list;
    public String xmlFile;

    public ACMPortalParser () {
       list = new ArrayList<>();
    }

    /*
     * This methode is used at line 78 in the SearchBasedParserFetcher.
     * We are given the InputStream for the DoSearch-URL
     */
    @Override
    public List<BibEntry> parseEntries(InputStream stream) throws ParseException {
        String htmlFile = "";
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            while(in.readLine()!= null){
                htmlFile = htmlFile + (char)10 + in.readLine();
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        List <String> dois = parseSearchPage(htmlFile);
        try {
            getBibEntriesFromDoilist(dois);
        } catch (FetcherException e) {
            e.printStackTrace();
        }

        return list;
    }

    /*
     * Analysis ACM website with search results and extracts DOIs.
     */
    public List<String> parseSearchPage(String htmlFile){
        int i = 0;
        LinkedList<String> doiList = new LinkedList<>();
        BufferedReader reader = new BufferedReader(new StringReader(htmlFile));
        try{
            String line;
            while ((line=reader.readLine())!=null && i < 10){
                if (line.contains("<div class=\"issue-item-checkbox-container\"><label class=\"checkbox--primary\"><input name=")){
                    int start = line.indexOf("input name=") + 12;
                    int end = line.indexOf("\"", start + 2);
                    String doi = line.substring(start,end);
                    //System.out.println(doi);
                    doiList.add(doi);
                    i++;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return doiList;
    }

    /*
     * Second step: Takes every DOI and gets the entry
     */
    public void getBibEntriesFromDoilist(List<String> doiList) throws FetcherException {
        CookieHandler.setDefault(new CookieManager());
        for (String s : doiList) {
            try (InputStream stream = new URLDownload(getURLForDoi(s)).asInputStream()) {
                fetchEntry(stream);
            } catch (IOException e) {
                throw new FetcherException("A network error occurred while fetching from ", e);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * Constructing the url for the doiparser
     */
    public URL getURLForDoi(String doi) throws URISyntaxException, MalformedURLException, FetcherException {
        URIBuilder uriBuilder = new URIBuilder(DOI_URL);
        uriBuilder.addParameter("dois", doi);
        uriBuilder.addParameter("targetFile", "custom-bibtex");
        uriBuilder.addParameter("format", "bibTex");
        return uriBuilder.build().toURL();
    }

    /*
     * Given the InputStream, this method gets the whole text from the website
     * and analyses the String for information about a BibEntry
     */
    public void fetchEntry(InputStream stream) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            // the "https://dl.acm.org/action/exportCiteProcCitation?dois=..."-website contains out of one line only
            xmlFile = in.readLine();
            in.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Checks if there is an object with this DOI at all.
        if (!xmlFile.contains("items")) {
            return;
        }

        // Deletes all unnecessary text about the xml-file / JSON-file itself.
        this.xmlFile = xmlFile.substring(xmlFile.indexOf("items"));

        BibEntry oneAndOnly;

        oneAndOnly = new BibEntry(StandardEntryType.Article);

        if (xmlFile.contains("author")) {
            String author = getMoreThanOneInfo("author");
            String allNames = "";

            String[] names = author.split("\"");

            for (int i = 0; i < names.length; i++) {
                if (names[i].contains("family")) {
                    try {
                        allNames += names[i+2] + ", ";
                    } catch (Exception e) {
                        System.out.println("There must be a problem with extracting a family name");
                    }
                } else if (names[i].contains("given")) {

                    if (i != names.length - 4) {
                        try {
                            allNames += names[i+2] + " and ";
                        } catch (Exception e) {
                            System.out.println("There must be a problem with extracting a first name");
                        }
                    } else {
                        try {
                            allNames += names[i+2];
                        } catch (Exception e) {
                            System.out.println("There must be a problem with extracting the last first name");
                        }
                    }
                }
            }


            oneAndOnly.setField(StandardField.AUTHOR, allNames);
        }

        if (xmlFile.contains("date-parts")) {
            String date = getMoreThanOneInfo("date-parts");
            date = date.substring(date.indexOf('[')+1);

            if (date.contains(",")) {
                String[] YearMonthDay = date.split(",");
                try {
                    oneAndOnly.setField(StandardField.YEAR, YearMonthDay[0]);
                } catch (Exception e) {
                    System.out.println("Something went wrong with getting the year");
                }

                try {
                    oneAndOnly.setField(StandardField.MONTH, YearMonthDay[1]);
                } catch (Exception e) {
                    System.out.println("Something went wrong with getting the month");
                }

                try {
                    oneAndOnly.setField(StandardField.DAY, YearMonthDay[2]);
                } catch (Exception e) {
                    System.out.println("Something went wrong with getting the day");
                }


            }
        }

        if (xmlFile.contains("abstract")) {
            String theAbstract = getOneInfo("abstract");
            oneAndOnly.setField(StandardField.ABSTRACT, theAbstract);
        }

        if (xmlFile.contains("DOI")) {
            String theAbstract = getOneInfo("DOI");
            try {
                oneAndOnly.setField(StandardField.DOI, theAbstract);
            } catch (Exception e) {
                System.out.println("Something went wrong with getting the DOI");
            }
        }

        if (xmlFile.contains("publisher")) {
            String publisher = getOneInfo("publisher");
            oneAndOnly.setField(StandardField.PUBLISHER, publisher);
        }

        if (xmlFile.contains("publisher-place")) {
            String publisherPlace = getOneInfo("publisher-place");
            oneAndOnly.setField(StandardField.PUBSTATE, publisherPlace);
        }

        if (xmlFile.contains("title")) {
            String title = getOneInfo("title");
            oneAndOnly.setField(StandardField.TITLE, title);
        }

        list.add(oneAndOnly);
    }

    public String getOneInfo(String type) {
        /*
         * Types with one information have "" after the colon.
         *  For example: "DOI":"10.1145/1015706.1015800"
         */
        return splitWithLastSymbol(type,'\"');
    }

    public String getMoreThanOneInfo(String type) {
        /*
         * Types with more than one information have [].
         *  For example: "date-parts":[[2004,8,1]]
         */
        return splitWithLastSymbol(type,']');
    }

    public String splitWithLastSymbol(String type, Character c) {

        try {
            int indexOfType = xmlFile.indexOf(type);
            int end = 0;
            String text = "";

            //a lot of entries cause exceptions because indexOfType =-1
            if (indexOfType < 0) {
                return type;
            }
            String info = xmlFile.substring(indexOfType); // from keyword to end

            if (c == '\"') {
                info = info.substring(info.indexOf(":") + 2);
                String[] allParts = info.split("\"");
                boolean nextQuotation = true;
                for (int i = 0; nextQuotation; i++) {

                    if (!allParts[i].endsWith("\\")) {
                        nextQuotation = false;
                        xmlFile = xmlFile.substring(xmlFile.indexOf(allParts[i]));
                        text += allParts[i];
                    } else {
                        text += allParts[i].substring(0, allParts[i].length() - 1) + "\"";
                    }
                }

                return text;
            } else {
                // shortening used text parts in the whole file
                xmlFile = xmlFile.substring(indexOfType + info.indexOf(c));
                info = info.substring(info.indexOf(":") + 2);

                return info.substring(0, info.indexOf(c));
            }
        } catch (Exception e) {
            return "";
        }

    }

    public List<BibEntry> example() {
        List<BibEntry> e = new ArrayList<>();
        BibEntry e1 = new BibEntry(StandardEntryType.Book);
        e1.setCiteKey("brooks1987no");
        e1.setField(StandardField.AUTHOR, "Brooks, Frederic");
        e1.setField(StandardField.TITLE, "No silver bullet");
        e1.setField(StandardField.YEAR, "1987");
        e.add(e1);
        return e;
    }




}

package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.jabref.gui.sharelatex.ShareLatexLoginDialogView;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.sharelatex.ShareLatexProject;
import org.jabref.model.util.FileUpdateMonitor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import netscape.javascript.JSObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.w3c.dom.html.HTMLFormElement;
import org.w3c.dom.html.HTMLInputElement;

public class SharelatexConnector {

    private static final Log LOGGER = LogFactory.getLog(SharelatexConnector.class);

    public WebView webView = new WebView();
    public WebEngine webEngine = webView.getEngine();
    public OverleafProject overleafProject = new OverleafProject();
    public AceEditor aceEditor = new AceEditor();

    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:80.0) Gecko/20100101 Firefox/80.0";
    private final Map<String, String> loginCookies = new HashMap<>();
    private String server;
    private String loginUrl;
    private String projectUrl;
    private final WebSocketClientWrapper client = new WebSocketClientWrapper();

    public class OverleafProject {
        public void fullyLoaded() {
            // Todo, probably replace document.get...By() with querySelector which is likely more suitable given angular
            System.out.println("Overleaf has finished loading!");
            System.out.println(webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().state.load_progress"));
            System.out.println("Doc paths in this project");
            System.out.println(webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().docs.map(e => e.path).join(', ')"));
            System.out.println(webEngine.executeScript("Object.keys(angular.element(document.getElementById('ide-body')).scope())"));
            // Todo, check if document is opened
            // Todo, find out what this actually means
            webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().$watch(" +
                    "'editor.opening'," +
                    "function(newValue,oldValue){" +
                    "if(!newValue){overleafProject.afterOpeningDocument()}});");
            System.out.println();
        }

        public void afterOpeningDocument() {
            // `opening: false` in EditorManager.js
            // Todo, the content of the ace-editor might not be loaded (which means current session text length = 1)
            System.out.println("Overleaf has opened the document named");
            System.out.println(webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().editor.open_doc_name;"));
            // Listen to when the the local reference to the ace-editor is created (used to know when the editor can be accessed)
            webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().$watch(" +
                    "'editor.sharejs_doc.ace'," +
                    "function(newValue,oldValue){" +
                    "jabrefAceAdapter.bindToCurrentDocument()});");
            // Todo REMOVE, prints the full document once
            // Todo, the full document cannot be accessed here yet... must write something in Overleaf before...
            printFullDocument();
        }

        public void printFullDocument() {
            // Todo, replace with code that deals with changes in the document
            System.out.println(webEngine.executeScript("ace.edit(document.getElementsByClassName('ace-editor-body')[0]).getSession().getDocument().getValue()"));
        }

        public void printProgress() {
            System.out.println(webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().state.load_progress"));
        }
    }

    public class AceEditor {
        public void getDefaultEditor() {
            // var defaultEditor = (JSObject) webEngine.executeScript("ace.edit(document.getElementsByClassName('ace-editor-body')[0])");
            // webEngine.executeScript("ace.edit(document.getElementsByClassName('ace-editor-body')[0])");
        }

        public void bindToCurrentDocument() {
            // Todo, must improve what changes are listened to and which methods should deal with them
            webEngine.executeScript("ace.edit(document.getElementsByClassName('ace-editor-body')[0])" +
                    ".getSession()" +
                    ".getDocument()" +
                    ".on('change'," +
                    "function(obj){overleafProject.printFullDocument()})");
        }
    }

    public void connectWebEngineToServer(String serverUri, String user, String password, ShareLatexLoginDialogView dialogView, Consumer<List<ShareLatexProject>> updateProjects) {
        // Todo does not register wrong login information or any other kind of error
        this.server = serverUri;
        this.loginUrl = server + "/login";
        var stateProperty = webEngine.getLoadWorker().stateProperty();
        var projectPageListener = new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                if (newValue.equals(Worker.State.SUCCEEDED)) {
                    org.w3c.dom.Document document = webEngine.getDocument();
                    System.out.println("Attempting to parse projects at URI");
                    System.out.println(document.getDocumentURI());
                    System.out.println("Fetched project?");
                    var data = Optional.ofNullable(document.getElementById("data"));
                    if (data.isPresent()) {
                        System.out.println("Found data store for project data!");
                        System.out.println(data.get().getTextContent());
                        var jsonData = JsonParser.parseString(data.get().getTextContent());
                        JsonObject obj = jsonData.getAsJsonObject();
                        updateProjects.accept(new ShareLatexParser().getProjectFromJson(obj));
                    } else {
                        System.out.println("Data is not present!!!");
                        updateProjects.accept(Collections.emptyList());
                    }
                    dialogView.showShareLatexProjectDialogView();
                    stateProperty.removeListener(this);
                }
            }
        };
        var loginListener = new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                if (newValue.equals(Worker.State.SUCCEEDED)) {
                    // Todo, must be heavily redone
                    org.w3c.dom.Document document = webEngine.getDocument();
                    var formElements = document.getElementsByTagName("form");
                    System.out.println("Found " + formElements.getLength() + " elements named form");
                    for (int i = 0; i < formElements.getLength(); i++) {
                        var element = (org.w3c.dom.Element) formElements.item(i);
                        if (element.getAttribute("name").equalsIgnoreCase("loginForm")) {
                            var loginForm = (HTMLFormElement) element;
                            System.out.println("Found login form");
                            var formInputs = loginForm.getElementsByTagName("input");
                            for (int k = 0; k < formInputs.getLength(); k++) {
                                if (formInputs.item(k) instanceof HTMLInputElement) {
                                    var inputElement = (HTMLInputElement) formInputs.item(k);
                                    var nameOfElement = inputElement.getAttribute("name");
                                    System.out.println("Found input element " + nameOfElement);
                                    if (nameOfElement.equalsIgnoreCase("email")) {
                                        inputElement.setValue(user);
                                        System.out.println("Username is set");
                                    } else if (nameOfElement.equalsIgnoreCase("password")) {
                                        inputElement.setValue(password);
                                        System.out.println("Password is set");
                                    }
                                }
                            }
                            System.out.println("Attempting to submit login information");
                            stateProperty.removeListener(this);
                            loginForm.submit();
                            stateProperty.addListener(projectPageListener);
                            break;
                        }
                    }
                }
            }
        };
        stateProperty.addListener(loginListener);
        webEngine.load(loginUrl);
    }

    public Optional<JsonObject> getProjects() throws IOException {
        projectUrl = server + "/project";
        Connection.Response projectsResponse = Jsoup.connect(projectUrl)
                .referrer(loginUrl).cookies(loginCookies).method(Method.GET).userAgent(userAgent).execute();

        Optional<Element> scriptContent = Optional.ofNullable(projectsResponse.parse()
                                                                              .select("script#data")
                                                                              .first());

        if (scriptContent.isPresent()) {
            String data = scriptContent.get().data();
            JsonElement jsonTree = JsonParser.parseString(data);

            JsonObject obj = jsonTree.getAsJsonObject();

            return Optional.of(obj);
        }
        return Optional.empty();
    }

    public void openWebEngineProject(String projectId, BibDatabaseContext database, ImportFormatPreferences prefs, FileUpdateMonitor fileMonitor) {
        webEngine.load("https://www.overleaf.com/project/" + projectId);
        var statePropery = webEngine.getLoadWorker().stateProperty();
        statePropery.addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                if (newValue == Worker.State.SUCCEEDED) {
                    System.out.println("Waiting for Overleaf to finish loading the project");
                    var window = (JSObject) webEngine.executeScript("window");
                    window.setMember("overleafProject", overleafProject);
                    window.setMember("jabrefAceAdapter", aceEditor);
                    // Todo, I either need to wait for, or manually set which document gets loaded...
                    // Todo check before setting up a watcher, if iy is already loaded setting up a watcher doesn't make sense
                    webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().$watch(" +
                            "'state.loading'," +
                                "function(newValue,oldValue){" +
                                    "if(!newValue){overleafProject.fullyLoaded()}});");
                    webEngine.executeScript("angular.element(document.getElementById('ide-body')).scope().$watch(" +
                            "'state.loading'," +
                            "function(newValue,oldValue){overleafProject.printProgress();});");
                    statePropery.removeListener(this);
                }
            }
        });
    }

    public void sendNewDatabaseContent(String newContent) throws InterruptedException {
        client.sendNewDatabaseContent(newContent);
    }

    public void registerListener(Object listener) {
        client.registerListener(listener);

    }

    public void unregisterListener(Object listener) {
        client.unregisterListener(listener);
    }

    public void disconnectAndCloseConn() {
        try {
            client.leaveDocAndCloseConn();
        } catch (IOException e) {
            LOGGER.error("Problem leaving document and closing websocket", e);
        }

    }

    private void setDatabaseName(BibDatabaseContext database) {
        String dbName = database.getDatabasePath().map(Path::getFileName).map(Path::toString).orElse("");
        client.setDatabaseName(dbName);
    }

}

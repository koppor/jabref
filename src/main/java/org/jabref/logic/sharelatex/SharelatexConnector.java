package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.sharelatex.ShareLatexProject;
import org.jabref.model.util.FileUpdateMonitor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import netscape.javascript.JSObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLFormElement;
import org.w3c.dom.html.HTMLInputElement;

public class SharelatexConnector {

    private static final Log LOGGER = LogFactory.getLog(SharelatexConnector.class);

    public WebView webView = new WebView();
    public WebEngine webEngine = webView.getEngine();
    public OverleafProject overleafProject = new OverleafProject();
    public AceEditor aceEditor = new AceEditor();

    private String server;
    private String user;
    private String password;
    private Pattern loginPage;
    private Pattern projectPage;
    private Pattern projectListPage;
    private final ListProperty<ShareLatexProject> projectList = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final WebSocketClientWrapper client = new WebSocketClientWrapper();

    public SharelatexConnector() {
        // Todo deal with a "default" Overleaf server
        initializePageMatchers("https://www.overleaf.com");
        // Todo, non-default page, alternative logins?
        ChangeListener<Worker.State> overleafPagesHandler = (observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                var currentPage = webEngine.getDocument().getDocumentURI().toLowerCase(Locale.ROOT);
                if (loginPage.matcher(currentPage).matches()) {
                    loginPage();
                } else if (projectPage.matcher(currentPage).matches()) {
                    projectPage();
                } else if (projectListPage.matcher(currentPage).matches()) {
                    projectListPage();
                } else {
                    // Todo, non-default page, alternative logins?
                }
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(overleafPagesHandler);
    }

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

    private void loginPage() {
        // Todo, must be heavily redone, it is probably better to use JavaScript for querying HTML elements
        org.w3c.dom.Document document = webEngine.getDocument();
        // Find all forms on the page
        var formElements = document.getElementsByTagName("form");
        for (int i = 0; i < formElements.getLength(); i++) {
            // Among the form elements, find the login form
            var element = (org.w3c.dom.Element) formElements.item(i);
            if (element.getAttribute("name").equalsIgnoreCase("loginForm")) {
                HTMLFormElement loginForm = (HTMLFormElement) element;
                NodeList formInputs = loginForm.getElementsByTagName("input");

                // Find and fill the name and password input
                for (int k = 0; k < formInputs.getLength(); k++) {
                    if (formInputs.item(k) instanceof HTMLInputElement) {
                        var inputElement = (HTMLInputElement) formInputs.item(k);
                        var nameOfElement = inputElement.getAttribute("name");
                        if (nameOfElement.equalsIgnoreCase("email")) {
                            inputElement.setValue(user);
                        } else if (nameOfElement.equalsIgnoreCase("password")) {
                            inputElement.setValue(password);
                        }
                    }
                }
                loginForm.submit(); // Submit the login-form
                break;
            }
        }
    }

    private void projectPage() {
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
    }

    private void projectListPage() {
        org.w3c.dom.Document document = webEngine.getDocument();
        var data = Optional.ofNullable(document.getElementById("data"));
        if (data.isPresent()) {
            var jsonData = JsonParser.parseString(data.get().getTextContent());
            JsonObject obj = jsonData.getAsJsonObject();
            projectList.setAll(new ShareLatexParser().getProjectFromJson(obj));
        } else {
            projectList.clear();
        }
    }

    private void initializePageMatchers(String server) {
        // Todo, trailing slashes in server URI?
        // Todo, should the base be quoted? (parenthesis is reserved in URI)
        String base = "^\\Q" + server + "\\E/";

        loginPage = Pattern.compile(base + "login/?$");
        projectListPage = Pattern.compile(base + "project/?$");
        projectPage = Pattern.compile(base + "project/\\p{XDigit}+/?$");
    }

    public void connectWebEngineToServer(String serverUri, String user, String password) {
        // Todo move to initialization?
        this.server = serverUri;
        this.user = user;
        this.password = password;
        initializePageMatchers(server);

        // Todo does not register wrong login information or any other kind of error
        webEngine.load(server + "/login");
    }

    public void openWebEngineProject(String projectId, BibDatabaseContext database, ImportFormatPreferences prefs, FileUpdateMonitor fileMonitor) {
        webEngine.load(this.server + "/project/" + projectId);
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

    public ListProperty<ShareLatexProject> projectListProperty() {
        return projectList;
    }
}

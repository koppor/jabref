---
parent: Set up a local workspace
grand_parent: Getting into the code
nav_order: 13
---

# Step 3: Set up JabRef's code style

Contributions to JabRef's source code need to have a code formatting that is consistent with existing source code.
For that purpose, JabRef relies on both the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) and [checkstyle](https://checkstyle.sourceforge.io/).

## Google Java Style (AOSP)

### Install google-java-format plugin

Install the [google-java-format IDEA plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format):
Navigate to **File > Settings... > Plugins"**.
On the top, click on "Marketplace".
Then, search for "google-java-format".
Click on "Install".

### Configure google-java-format plugin

JabRef likes to have 4 spaces indentation.
This is called the "AOSP" style in the Google Java Format style.
Navigate to **File > Settings... > Other Settings > google-java-format Settings"**.
Change "Code style" to "Android Open Source Project (AOSP) style".

{% figure caption:"Enable AOSP style in Google Java Format" %}
![Enable GJF AOSP](guidelines-intellij-gfm-aosp.png)
{% endfigure %}

The google-java-format plugin uses some internal classes that are not available
without extra configuration. To use the plugin, you need to
[add some options to your IDE's Java runtime](https://www.jetbrains.com/help/idea/tuning-the-ide.html#procedure-jvm-options).
To do that, go to `Help→Edit Custom VM Options...` and paste in these lines:

```text
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
```

Once you have done that, restart the IDE.

(This howto is based on the [google-java-format plugin documentation](https://github.com/google/google-java-format/blob/master/README.md#intellij-jre-config).)

## Checkstyle

## Install checkstyle plugin

Install the [CheckStyle-IDEA plugin](http://plugins.jetbrains.com/plugin/1065?pr=idea), it can be found via the plug-in repository:
Navigate to **File > Settings... > Plugins"**.
On the top, click on "Marketplace".
Then, search for "Checkstyle".
Click on "Install" choose "CheckStyle-IDEA".<br>
**Note:** In some MacBooks, `Settings` can be found at the "IntelliJ" button of the app menu instead of at "File".

{% figure caption:"Install CheckStyle" %}
![Install CheckStyle](guidelines-intellij-install-checkstyle.png)
{% endfigure %}

After clicking, IntelliJ asks for confirmation:

{% figure caption:"Third Party Plugin Privacy Notice" %}
![Third Party Plugin Privacy Notice](guidelines-intellij-checkstyle-confirmation.png)
{% endfigure %}

If you agree, click on "Agree" and you can continue.

Afterwards, use the "Restart IDE" button to restart IntelliJ.

{% figure caption:"IntelliJ restart IDE" %}
![IntelliJ restart IDE](guidelines-intellij-checkstyle-restart-ide.png)
{% endfigure %}

Click on "Restart" to finally restart.

### Put JabRef's checkstyle configuration in place

Now, put the checkstyle configuration file is in place:

Go to **File > Settings... > Tools > Checkstyle > Configuration File**

Trigger the import dialog of a CheckStyle style by clicking the \[+] button:

{% figure caption:"Trigger the rule import dialog" %}
![Trigger the rule import dialog](guidelines-intellij-checkstyle-start-import.png)
{% endfigure %}

Then:

* Put "JabRef" as description.
* Browse for `config/checkstyle/checkstyle.xml`
* Tick "Store relative to project location"
* Click "Next"

{% figure caption:"Filled Rule Import Dialog" %}
![Filled Rule Import Dialog](guidelines-intellij-checkstyle-import-file.png)
{% endfigure %}

Click on "Finish"

Activate the CheckStyle configuration file by ticking it in the list

{% figure caption:"JabRef's checkstyle config is activated" %}
![JabRef's checkstyle config is activated](guidelines-intellij-checkstyle-jabref-active.png)
{% endfigure %}

Ensure that the [latest CheckStyle version](https://checkstyle.org/releasenotes.html) is selected (10.3.4 or higher).
Also, set the "Scan Scope" to "Only Java sources (including tests)".

{% figure caption:"Checkstyle is the highest version - and tests are also scanned" %}
![Checkstyle is the highest version - and tests are also scanned](guidelines-intellij-checkstyle-final-settings.png)
{% endfigure %}

Save settings by clicking "Apply" and then "OK"

## Enable proper import cleanup

To enable "magic" creation and auto cleanup of imports, go to **File > Settings... > Editor > General > Auto Import**.
There, enable both "Add unambiguous imports on the fly" and "Optimize imports on the fly"
(Source: [JetBrains help](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html#automatically-add-import-statements)).

{% figure caption:"Auto import enabled" %}
![Enable auto import](guidelines-intellij-editor-autoimport.png)
{% endfigure %}

Press "OK".

## Have auto format working properly in JavaDoc

To have auto format working properly in the context of JavaDoc and line wrapping, "Wrap at right margin" has to be disabled. Details are found in [IntelliJ issue 240517](https://youtrack.jetbrains.com/issue/IDEA-240517).

Go to **File > Settings... > Editor > Code Style > Java > JavaDoc**.

At "Other", disable "Wrap at right margin"

{% figure caption:"”Wrap at right margin” disabled" %}
!["Wrap at right margin" disabled](guidelines-intellij-editor-javadoc-do-not-wrap.png)
{% endfigure %}

## Disable too advanced code folding

Go to **File > Settings... > Editor > General > Code Folding**.
At section "General", disable "File header" and "Imports".
At section "Java", disable "One-line methods".

{% figure caption:"Code foldings disabled" %}
![Code foldings disabled](guidelines-settings-intellij-code-foldings.png)
{% endfigure %}

Press "OK".

## Run checkstyle

In the lower part of IntelliJ's window, click on "Checkstyle".
In "Rules", change to "JabRef".
Then, you can run a check on all modified files.

{% figure caption:"JabRef's style is active - and we are ready to run a check on all modified files" %}
![JabRef's style is active - and we are ready to run a check on all modified files](guidelines-intellij-checkstyle-window.png)
{% endfigure %}

## Final comments

{: .highlight }
> Now you have configured IntelliJ completely.
> You can run the main application using Gradle and the test cases using IntelliJ.
> The code formatting rules are imported - and the most common styling issue at imports is automatically resolved by IntelliJ.
> Finally, you have Checkstyle running locally so that you can check for styling errors before submitting the pull request.

Got it running? GREAT! You are ready to lurk the code and contribute to JabRef. Please make sure to also read our [contribution guide](https://devdocs.jabref.org/contributing#contribute-code).

<!-- markdownlint-disable-file MD033 -->

# Design of Overleaf Integration

* Status: in-progress
* Deciders: @koppor @Siedlerchr
* Date: 2021-01-26

Technical Story: [description | ticket/issue URL] <!-- optional -->

## Context and Problem Statement

Overleaf is an open source project with paid developers.
It would be great to take as much advantage as possible of their code base and, down the line, benefit from future features, bug fixes and improvements.

## Decision Drivers <!-- optional -->

1. Feasability and complexity of taking advantage of Overleaf's codebase
2. Multiple simultaneous users
3. Compatibility with JabRef's representation/use of `.bib` files
4. Persistence to drive and offline use

## Considered Options

1. Mostly JavaScript
2. A hybrid approach using some JavaScript and some Java
3. Mostly Java

## Decision Outcome

Chosen option: "[option 1]", because [justification. e.g., only option, which meets k.o. criterion decision driver | which resolves force force | … | comes out best (see below)].

### Positive Consequences <!-- optional -->

* [e.g., improvement of quality attribute satisfaction, follow-up decisions required, …]
* …

### Negative Consequences <!-- optional -->

* [e.g., compromising quality attribute, follow-up decisions required, …]
* …

## Pros and Cons of the Options <!-- optional -->

### Mostly JavaScript

Import Overleaf.

* Good, because code changes to Overleaf can be integrated directly 
* Bad, because even if in theory the code changes can be integrated directly, it is rarely the case in practice
* Bad, because less than 10% of the features will be used by JabRef

#### Is it possible?

* Is there a license issue?
* Programming issues
  * GraalVM/JavaFX integration
    * There are a lot of information regarding (Oracle?) JavaFX and Nashorn (e.g., [communication between java and javascript](https://riptutorial.com/javafx/example/19313/communication-between-java-app-and-javascript-in-the-web-page), neither should be used by JabRef.
  * Access and bind the methods/variables within Overleaf

### Hybrid approach, some JavaScript, some Java

[example | description | pointer to more information | …] <!-- optional -->

* Good, because most of the Java code that has already been written can be re-used.
* Good, because [argument b]
* Bad, because [argument c]

### Mostly Java

[example | description | pointer to more information | …] <!-- optional -->

* Good, because large parts of the basic functionality is already implemented.

## Links <!-- optional -->

* [Link type] [Link to ADR] <!-- example: Refined by [ADR-0005](0005-example.md) -->
* … <!-- numbers of links can vary -->


## Order of appearance of citation groups

This seems trivial at first. Take the textual order of citation markers, and 

In the presence of figures, tables, footnotes/endnotes, tables with
footnotes possibly either far from the location they are referred to
in the text, or wrapped around with text it becomes obvious what is
the correct order.

For example:

- In a two-column layout, a text frame or figure mostly, but not fully
  in the second column: shall we consider it part of the second column?


Technically LibreOffice allows several types of
[XTextContent](https://api.libreoffice.org/docs/idl/ref/interfacecom_1_1sun_1_1star_1_1text_1_1XTextContent.html)
to be inserted.

- Some of these allow text inside with further insertions.
- Many, but not all of them supports getting a "technical" insertion point or text range
through [getAnchor](https://api.libreoffice.org/docs/idl/ref/interfacecom_1_1sun_1_1star_1_1text_1_1XTextContent.html#ae82a8b42f6b2578549b68b4483a877d3). In Libreoffice positioning both anchors and for example a frame seems hard, moving the frame
tends to also move the anchor.
- Consequence: producing an order of appearance for the citation
  groups requires to handle many cases, may still turn out
  insufficient and may require the user to 


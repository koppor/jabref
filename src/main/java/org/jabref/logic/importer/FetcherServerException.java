package org.jabref.logic.importer;

import org.jabref.http.dto.SimpleHttpResponse;

import java.net.URL;

/**
 *  Should be thrown when you encounter a http status code error >= 500
 */
public class FetcherServerException extends FetcherException {
    public FetcherServerException(URL source, SimpleHttpResponse httpResponse) {
        super(source, httpResponse);
    }
}

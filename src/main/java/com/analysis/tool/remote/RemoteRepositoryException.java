package com.analysis.tool.remote;

import java.io.IOException;

/**
 * A remote Git repository could not be cloned or inspected (PHASE 2/13).
 *
 * This is a distinct type so the command-line layer can map it to exit code 4
 * ("repository cloning/network failure") while other IO problems keep their
 * own exit codes. The message is always safe to print: it never contains a
 * password or access token (see {@link RemoteRepository#redactCredentials}).
 */
public class RemoteRepositoryException extends IOException {

    public RemoteRepositoryException(String message) {
        super(message);
    }

    public RemoteRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}

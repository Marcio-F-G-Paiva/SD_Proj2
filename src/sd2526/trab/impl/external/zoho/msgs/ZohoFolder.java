package sd2526.trab.impl.external.zoho.msgs;

public record ZohoFolder(
    String path,
    int isArchived,
    String folderName,
    boolean imapAccess,
    String folderType,
    String URI,
    String folderId
) {}

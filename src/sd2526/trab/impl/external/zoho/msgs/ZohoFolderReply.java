package sd2526.trab.impl.external.zoho.msgs;

import java.util.List;

public record ZohoFolderReply(
    String status,
    List<ZohoFolder> data
) {

}

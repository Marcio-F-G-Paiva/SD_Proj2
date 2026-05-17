package sd2526.trab.impl.external.zoho.msgs;

import java.util.List;

import sd2526.trab.impl.external.Zoho;

public record ZohoMessageReply(ZohoStatus status, List<ZohoMessage> data) {

}

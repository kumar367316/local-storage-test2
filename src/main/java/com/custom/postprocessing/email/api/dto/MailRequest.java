package com.custom.postprocessing.email.api.dto;

import java.util.List;

import lombok.Data;

/**
 * @author kumar.charanswain
 *
 */

@Data
public class MailRequest {

	private String to;
	private String from;
	private String mailSubjectMessage;
	private List<String> pclFileNames;

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getMailSubjectMessage() {
		return mailSubjectMessage;
	}

	public void setMailSubjectMessage(String mailSubjectMessage) {
		this.mailSubjectMessage = mailSubjectMessage;
	}

	public List<String> getPclFileNames() {
		return pclFileNames;
	}

	public void setPclFileNames(List<String> pclFileNames) {
		this.pclFileNames = pclFileNames;
	}
}

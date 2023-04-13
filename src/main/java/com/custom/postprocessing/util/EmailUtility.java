package com.custom.postprocessing.util;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.custom.postprocessing.constant.PostProcessingConstant;
import com.custom.postprocessing.email.api.dto.MailRequest;
import com.custom.postprocessing.email.api.dto.MailResponse;

/**
 * @author kumar.charanswain
 *
 */

@Component
public class EmailUtility {

	public static final Logger logger = LoggerFactory.getLogger(EmailUtility.class);

	@Value("${mail-from}")
	private String mailForm;

	@Value("${mail-to}")
	private String mailTo;

	@Value("${mail-smtp-starttls-key}")
	private String starttlsKey;

	@Value("${mail-smtp-starttls-value}")
	private String starttlsValue;

	@Value("${mail-smtp-host-key}")
	private String hostKey;

	@Value("${mail-smtp-host-value}")
	private String hostValue;

	@Value("${mail-smtp-port-key}")
	private String portKey;

	@Value("${mail-smtp-port-value}")
	private String portValue;

	@Value("${mail-smtp-auth-key}")
	private String authKey;

	@Value("${mail-smtp-auth-value}")
	private String authValue;
	
	String exceptionMessage = "";

	public MailResponse sendEmail(MailRequest request, String currentDate) {
		MailResponse response = new MailResponse();
		try {
			Properties props = new Properties();
			props.put(starttlsKey, starttlsValue);
			props.put(hostKey, hostValue);
			props.put(portKey, portValue);
			props.put(authKey, authValue);
			Session session = Session.getDefaultInstance(props);
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(mailForm));
			message.setRecipient(RecipientType.TO, new InternetAddress(mailTo));
			message.setSubject(request.getMailSubjectMessage());
			StringBuilder builder = new StringBuilder();
			if (request.getPclFileNames().size() >= 1) {
				builder.append(request.getMailSubjectMessage());
				builder.append("<html><body><div>Summary</div><br/>"
						+ "<table style='border:2px solid black'>process pcl file list<br/>");
				builder.append("<tr><th>PCL FILE NAME</th></tr>");
				for (String fileName : request.getPclFileNames()) {
					builder.append("<tr><td>" + fileName + "</td></tr>");
				}
				builder.append("</table></br></body></html>");
				builder.append("<br/>PostProcessing archive completed successfully");
				message.setContent(builder.toString(), "text/html");
			} else {
				builder.append("<br/>PostProcessing archive completed successfully");
				message.setContent(builder.toString(), "text/html");
			}
			Transport.send(message);

			response.setMessage("mail send successfully : " + request.getTo());
			response.setStatus(Boolean.TRUE);
		} catch (AddressException addressException) {
			logger.info("email address invalid sendEmail() address exception type" + addressException.getMessage());
			response.setMessage(PostProcessingConstant.MAIL_FAILURE);
			response.setStatus(Boolean.FALSE);
			response.setErrorMessage(PostProcessingConstant.EXCEPTION_MSG);
		} catch (MessagingException messageException) {
			logger.info("message invalid sendEmail() message exception:" + messageException.getMessage());
			response.setMessage(PostProcessingConstant.MAIL_FAILURE);
			response.setStatus(Boolean.FALSE);
			response.setErrorMessage(PostProcessingConstant.EXCEPTION_MSG);
		} catch (Exception exception) {
			logger.info("exception sendEmail() :" + exception.getMessage());
			response.setMessage(PostProcessingConstant.MAIL_FAILURE);
			response.setStatus(Boolean.FALSE);
			response.setErrorMessage(PostProcessingConstant.EXCEPTION_MSG);
		}
		return response;
	}

	public MailResponse emailProcess(List<String> pclFileList, String currentDate, String mailStatusMessage) {
		MailResponse mailResponse = new MailResponse();
		try {
			MailRequest mailRequest = new MailRequest();
			mailRequest.setFrom(mailForm);
			mailRequest.setTo(mailTo);
			mailRequest.setMailSubjectMessage(mailStatusMessage);
			mailRequest.setPclFileNames(pclFileList);

			mailResponse = sendEmail(mailRequest, currentDate);
			if (Objects.nonNull(mailResponse.getFile()))
				mailResponse.getFile().delete();
		} catch (Exception exception) {
			logger.info("exception emailProcess():" + exception.getMessage());
			mailResponse.setErrorMessage(PostProcessingConstant.EXCEPTION_MSG);
		}
		return mailResponse;
	}

	public void addFileNameList(List<String> fileNames, List<String> updateFileNames) {
		for (String fileName : fileNames) {
			updateFileNames.add(fileName);
		}
	}

}
package com.custom.postprocessing.scheduler;

import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.BANNER_PAGE;
import static com.custom.postprocessing.constant.PostProcessingConstant.LOG_FILE;
import static com.custom.postprocessing.constant.PostProcessingConstant.OUTPUT_DIRECTORY;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.aspose.pdf.License;
import com.aspose.pdf.facades.PdfFileEditor;
import com.custom.postprocessing.constant.PostProcessingConstant;
import com.custom.postprocessing.email.api.dto.MailResponse;
import com.custom.postprocessing.entity.PostProcessingJsonEntity;
import com.custom.postprocessing.util.EmailUtility;
import com.custom.postprocessing.util.PostProcessUtil;
import com.microsoft.azure.storage.StorageException;

/**
 * @author kumar.charanswain
 *
 */

@Service
public class PostProcessingScheduler {

	public static final Logger logger = LoggerFactory.getLogger(PostProcessingScheduler.class);

	@Value("#{'${state-allow-type}'.split(',')}")
	private List<String> stateBatchType;

	@Value("#{'${ediforms-type}'.split(',')}")
	private List<String> ediFormsType;

	@Value("#{'${page-type}'.split(',')}")
	private List<String> pageTypeList;

	@Value("${selfAddressed-type}")
	private String selfAddressedType;

	@Value("${pcl-evaluation-copies}")
	private boolean pclEvaluationCopies;

	@Value("${mail-pcl-subject}")
	private String mailPclSubject;

	@Value("${license-file-name}")
	private String licenseFileName;

	@Value("${transit-folder}")
	private String transitFolder;

	@Value("${archive-folder}")
	private String archiveFolder;

	@Value("${license-folder}")
	private String licenseFolder;
	
	@Value("${banner-folder}")
	private String bannerFolder;

	@Autowired
	EmailUtility emailUtility;

	@Autowired
	private PostProcessUtil postProcessUtil;

	PostProcessingJsonEntity postProcessingJsonEntity = new PostProcessingJsonEntity();

	List<String> invalidFileList = new LinkedList<>();

	List<String> pclFileList = new LinkedList<>();

	Set<String> failedFileList = new LinkedHashSet<>();
	List<String> archiveFileList = new LinkedList<>();

	String exceptionMessage = " Successfully";

	@Scheduled(cron = "${cron-job-print-interval}")
	public void postProcessing() {
		smartCommPostProcessing();
	}

	public PostProcessingJsonEntity smartCommPostProcessing() {
		logger.info("postprocessing started");
		String currentDate = currentDate();
		String currentDateTime = currentDateTimeStamp();
		String statusMessage = "SmartComm PostProcessing";
		archiveFileList = new LinkedList<>();
		failedFileList = new LinkedHashSet<>();
		postProcessingJsonEntity = new PostProcessingJsonEntity();
		try {
			final File srcFolder = new File(archiveFolder);
			if (fileExistForProcessing(srcFolder.toString())) {
				moveFilesToTempBackup(srcFolder, currentDateTime);
				final File tempFolder = new File(transitFolder + currentDateTime + "-temp/");
				archiveFileList = moveSourceToTargetDirectory(tempFolder, currentDateTime, currentDate,archiveFileList);
				File printDirectory = new File(transitFolder + currentDateTime + "-print/");
				postProcessingJsonEntity = processMetaDataInputFile(printDirectory, currentDateTime, currentDate,archiveFileList);
				logger.info("postprcoessing json entity " + postProcessingJsonEntity);
				processCompleteFile(currentDateTime);
			} else {
				logger.info("no file for processing :archive folder is empty ");
			}
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception smartComPostProcessing() " + exception.getMessage());
		}
		logger.info(statusMessage + exceptionMessage);
		logger.info("postprocessing ended");
		try {
			String logFile = LOG_FILE;
			File logFileName = new File(logFile + ".log");
			File updateLogFile = new File(logFile + "_" + currentDateTime + ".log");
			if (!(updateLogFile.exists())) {
				Files.copy(logFileName.toPath(), updateLogFile.toPath());
				copyFileToTargetDirectory(updateLogFile.toString(), transitFolder);
				logFileName.delete();
				updateLogFile.delete();
				deleteFiles(invalidFileList);
			}
		} catch (Exception exception) {
			logger.info("exception:" + exception.getMessage());
		}
		exceptionMessage = " Successfully";
		return postProcessingJsonEntity;
	}

	public void processCompleteFile(String currentDateTime) {
		try {
			String documentFileName = "process-completed-" + currentDateTime + ".txt";
			File file = new File(documentFileName);
			final FileOutputStream outputStream = new FileOutputStream(file);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.println("process completed" + '\n');
			copyFileToTargetDirectory(file.toString(), transitFolder);
			outputStream.close();
			writer.close();
			file.delete();
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception processCompleteFile() :" + exception.getMessage());
		}
	}

	public PostProcessingJsonEntity processMetaDataInputFile(final File folder, String currentDateTime, String currentDate,List<String> archiveFileList) {
		Map<String, List<String>> postProcessMap = new HashMap<>();
		List<String> processFileList = new LinkedList<>();
		List<String> pclFiles = new LinkedList<String>();
		try {
			if (checkIfFolderExists(folder.toString())) {
				for (final File fileEntry : folder.listFiles()) {
					String fileName = fileEntry.getName();
					Files.copy(Paths.get(transitFolder + currentDateTime + "-print/" + fileEntry.getName()),
							Paths.get(fileEntry.getName()));
					logger.info("process file:" + fileName);
					processFileList.add(fileName);
					boolean stateType = checkStateType(fileName);
					boolean ediFormsType = checkEdiFormsType(fileName);
					if (stateType) {
						if (stateType && !(fileName.contains("_CC_"))) {
							if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), "xml")) {
								new File(fileName).delete();
								continue;
							}
							String fileNameNoExt = FilenameUtils.removeExtension(fileName);
							String[] stateAndSheetNameList = StringUtils.split(fileNameNoExt, "_");
							String stateAndSheetName = stateAndSheetNameList.length > 0
									? stateAndSheetNameList[stateAndSheetNameList.length - 1]
									: "";
							prepareMap(postProcessMap, stateAndSheetName, fileName);
						} else if (fileName.contains("_CC_") && !(fileName.contains("_Primary"))) {
							if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							prepareMap(postProcessMap, getSheetNumber(fileName),
									StringUtils.replace(fileName, ".xml", "pdf"));
						} else if (fileName.contains("_Primary")) {
							if ("xml".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							String fileNameNoExt = FilenameUtils.removeExtension(fileName);
							String[] stateAndSheetNameList = fileNameNoExt.split("_ST_");
							if (stateAndSheetNameList.length >= 1) {
								String stateName = stateAndSheetNameList[stateAndSheetNameList.length - 1];
								String stateAndSheetName = stateName.substring(0, 2);
								prepareMap(postProcessMap, stateAndSheetName, fileName);
							}
						}
					} else if (ediFormsType) {
						if (ediFormsType && !(fileName.contains("_CC_"))) {
							if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							String ediBannerPage = ediFormsBannerFileGenerate(fileName);
							prepareMap(postProcessMap, ediBannerPage, StringUtils.replace(fileName, ".xml", ".pdf"));
						} else if (fileName.contains("_CC_") && !(fileName.contains("_Primary"))) {
							if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							String ediBannerPage = ediFormsBannerFileGenerate(fileName);
							prepareMap(postProcessMap, ediBannerPage, StringUtils.replace(fileName, ".xml", ".pdf"));
						} else if (fileName.contains("_Primary")) {
							if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}

							String ediBannerPage = ediFormsBannerFileGenerate(fileName);
							prepareMap(postProcessMap, ediBannerPage, StringUtils.replace(fileName, ".xml", ".pdf"));
						}
					} else if (checkPageType(fileName)) {
						if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
							new File(fileName).delete();
							continue;
						}
						prepareMap(postProcessMap, getSheetNumber(fileName),
								StringUtils.replace(fileName, ".xml", ".pdf"));
					} else if (fileName.contains(selfAddressedType)) {
						if (fileName.contains(selfAddressedType) && !(fileName.contains("_CC_"))) {
							if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), "xml")) {
								new File(fileName).delete();
								continue;
							}
							String fileNameNoExt = FilenameUtils.removeExtension(fileName);
							String[] selfAddressedTypeList = StringUtils.split(fileNameNoExt, "_");
							String selfAddressedType = selfAddressedTypeList.length > 0
									? selfAddressedTypeList[selfAddressedTypeList.length - 1]
									: "";
							prepareMap(postProcessMap, selfAddressedType, fileName);
						} else if (fileName.contains("_CC_") && !(fileName.contains("_Primary"))) {
							if ("pdf".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							prepareMap(postProcessMap, getSheetNumber(fileName),
									StringUtils.replace(fileName, ".xml", ".pdf"));
						} else if (fileName.contains("_Primary")) {
							if ("xml".equals(FilenameUtils.getExtension(fileName))) {
								new File(fileName).delete();
								continue;
							}
							String stateAndSheetName = "SelfAddressed";
							prepareMap(postProcessMap, stateAndSheetName, fileName);
						}
					} else {
						logger.info("processing batch type is not supported");
						continue;
					}
					new File(fileName).delete();
				}
			}

			if (postProcessMap.size() > 0) {
				pclFiles = mergePDF(postProcessMap, currentDateTime, currentDate,pclFiles);
			} else {
				logger.info("no file for postprocessing ");
			}
			postProcessingJsonEntity.setProcessingFiles(processFileList);
			postProcessingJsonEntity.setArchiveFiles(archiveFileList);
			postProcessingJsonEntity.setFailedFiles(failedFileList);
			postProcessingJsonEntity.setPclFiles(pclFiles);
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception processMetaDataInputFile()" + exception.getMessage());
		}
		return postProcessingJsonEntity;
	}

	public static boolean checkIfFolderExists(String folderName) {
		boolean found = false;
		try {
			File file = new File(folderName);
			if (file.exists() && file.isDirectory()) {
				found = true;
			}
		} catch (Exception exception) {
			logger.info("exception is checkIfFolderExists():" + exception.getMessage());
		}
		return found;
	}

	public static boolean fileExistForProcessing(String folderName) {
		boolean found = true;
		try {
			File directory = new File(folderName);
			String[] flist = directory.list();
			if (flist.length == 0) {
				found = false;
			}
		} catch (Exception exception) {
			logger.info("exception is fileExistForProcessing():" + exception.getMessage());
		}
		return found;
	}

	public String ediFormsBannerFileGenerate(String fileName) {
		String ediFormValue = getSheetNumber(fileName);
		String fileNameNoExt = FilenameUtils.removeExtension(fileName);
		String[] ediFormList = StringUtils.split(fileNameNoExt, "_");
		String ediForm = ediFormList.length > 0 ? ediFormList[2] : "";
		if (ediFormValue.equals("2")) {
			ediFormValue = ediForm + ediFormValue;
		} else if (ediFormValue.equals("3")) {
			ediFormValue = ediForm + ediFormValue;
		} else {
			ediFormValue = ediForm + "Default";
		}
		return ediFormValue;
	}

	public boolean checkPageType(String fileName) {
		for (String pageType : pageTypeList) {
			if (fileName.contains(pageType)) {
				return true;
			}
		}
		return false;
	}

	private String getSheetNumber(String fileName) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			File file = new File(fileName);
			Document document = xmlFileDocumentReader(fileName);
			Element root = document.getDocumentElement();
			if (Objects.isNull(root.getElementsByTagName("totalSheet").item(0))) {
				logger.info("xml file don't have totalSheet element tag:" + fileName);
				file.delete();
				return PostProcessingConstant.ZEROPAGE;
			}
			int sheetNumber = Integer.parseInt(root.getElementsByTagName("totalSheet").item(0).getTextContent());
			if (sheetNumber <= 10) {
				file.delete();
				return String.valueOf(sheetNumber);
			}
			file.delete();
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception getSheetNumber()", exception.getMessage());
		}
		return PostProcessingConstant.MULTIPAGE;
	}

	public void prepareMap(Map<String, List<String>> postProcessMap, String key, String fileName) {
		if (postProcessMap.containsKey(key)) {
			List<String> existingFileNameList = postProcessMap.get(key);
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		} else {
			List<String> existingFileNameList = new ArrayList<>();
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		}
	}

	public boolean checkStateType(String fileName) {
		for (String state : stateBatchType) {
			if (fileName.contains(state) && fileName.contains("_ST_") && !(fileName.contains("ATTACH"))) {
				return true;
			}
		}
		return false;
	}

	public List<String> mergePDF(Map<String, List<String>> postProcessMap, String currentDateTime, String currentDate,List<String> pclFiles)
			throws IOException {
		List<String> fileNameList = new LinkedList<>();
		MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
		for (String fileType : postProcessMap.keySet()) {
			try {
				List<String> claimNbrSortedList = new LinkedList<>();
				PDFMergerUtility pdfMerger = new PDFMergerUtility();
				fileNameList = postProcessMap.get(fileType);
				String bannerFileName = getBannerPage(fileType);
				logger.info("banner file is:" + bannerFileName);
				File bannerFile = new File(bannerFileName);

				String blankPage = getEmptyPage();
				pdfMerger.addSource(bannerFileName);
				pdfMerger.addSource(blankPage);
				for (String fileName : fileNameList) {
					File file = new File(fileName);
					Files.copy(Paths.get(transitFolder + currentDateTime + "-print/" + file.toString()),
							Paths.get(file.toString()));
					logger.info("process file for pcl is:" + fileName);
					String claimNbr = fileName.substring(14, fileName.length());
					File claimNbrFileName = new File(claimNbr);
					file.renameTo(claimNbrFileName);
					claimNbrSortedList.add(claimNbrFileName.toString());
					Collections.sort(claimNbrSortedList);
				}
				for (String fileName : claimNbrSortedList) {
					File file = new File(fileName);
					pdfMerger.addSource(file.getPath());
				}
				fileType = postProcessUtil.getFileType(fileType);
				String currentDateTimeStamp = currentDateTimeStamp();
				String mergePdfFile = fileType + "_" + currentDateTimeStamp + ".pdf";
				pdfMerger.setDestinationFileName(mergePdfFile);

				pdfMerger.mergeDocuments(memoryUsageSetting);

				pclFiles = convertPDFToPCL(mergePdfFile, currentDateTime,pclFiles);
				bannerFile.delete();
				new File(mergePdfFile).delete();
				new File(blankPage).delete();
				deleteFiles(claimNbrSortedList);
			} catch (StorageException storageException) {
				exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
				logger.info("invalid file or may be banner file is missing");
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				continue;
			} catch (Exception exception) {
				exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				logger.info("Exception mergePDF()" + exception.getMessage());
				continue;
			}
		}

		if (postProcessMap.size() > 0) {
			MailResponse mailResponse = emailUtility.emailProcess(pclFileList, currentDate,
					mailPclSubject + "-" + currentDate);
			exceptionMessage = mailResponse.getErrorMessage();
		}

		File licenseFile = new File(licenseFileName);
		licenseFile.delete();
		deleteFiles(pclFileList);
		pclFileList.clear();
		return pclFiles;
	}

	// post processing PDF to PCL conversion
	public List<String> convertPDFToPCL(String mergePdfFile, String currentDateTime,List<String> pclFiles) throws IOException {
		List<String> pclFileCreation = new LinkedList<>();
		String outputPclFile = FilenameUtils.removeExtension(mergePdfFile) + ".pcl";
		try {
			Files.copy(Paths.get(licenseFolder + licenseFileName), Paths.get(licenseFileName));
			License license = new License();
			license.setLicense(licenseFileName);
			pclFileCreation = pclFileCreation(mergePdfFile, outputPclFile, currentDateTime,pclFiles);
		} catch (Exception exception) {
			logger.info("The license has expired:no need to print pcl file with evaluation copies");
		}
		if (pclEvaluationCopies) {
			logger.info("The license has expired:print pcl file with evaluation copies");
			pclFileCreation = pclFileCreation(mergePdfFile, outputPclFile, currentDateTime, pclFiles);
		}
		new File(outputPclFile).delete();
		return pclFileCreation;
	}

	public List<String> pclFileCreation(String mergePdfFile, String outputPclFile, String currentDateTime,List<String> pclFiles) {
		try {
			PdfFileEditor fileEditor = new PdfFileEditor();
			final InputStream stream = new FileInputStream(mergePdfFile);
			final InputStream[] streamList = new InputStream[] { stream };
			final OutputStream outStream = new FileOutputStream(outputPclFile);
			fileEditor.concatenate(streamList, outStream);
			stream.close();
			outStream.close();
			fileEditor.setCloseConcatenatedStreams(true);
			pclFileList.add(outputPclFile);
			pclFiles.add(outputPclFile);
			copyFileToTargetDirectory(outputPclFile, transitFolder);
			logger.info("generated pcl file is:" + outputPclFile);
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception pclFileCreation() " + exception.getMessage());
		}
		return pclFiles;
	}

	public String getEmptyPage() throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		String blankPage = "Blank.pdf";
		try {
			Files.copy(Paths.get(bannerFolder + blankPage), Paths.get(blankPage));
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception getEmptyPage() " + exception.getMessage());
		}
		return blankPage;
	}

	public void copyFileToTargetDirectory(String fileName, String targetFolder) {
		try {
			Files.copy(Paths.get(fileName), Paths.get(targetFolder + fileName));
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("incorrect files for processing: " + exception.getMessage());
		}
	}

	public String getBannerPage(String key)
			throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		String bannerFileName = BANNER_PAGE + key + ".pdf";
		Files.copy(Paths.get(bannerFolder + bannerFileName), Paths.get(bannerFileName));
		return bannerFileName;
	}

	public boolean checkEdiFormsType(String fileName) {
		for (String ediFormName : ediFormsType) {
			if (fileName.contains(ediFormName)) {
				return true;
			}
		}
		return false;
	}

	private void moveFilesToTempBackup(final File folder, String currentDateTime) {
		File tempFolder = new File(transitFolder + currentDateTime + "-temp/");
		tempFolder.mkdir();
		try {
			for (final File fileEntry : folder.listFiles()) {
				logger.info("process file is " + fileEntry.getName());
				File inputFile = new File(fileEntry.getName());
				if (!inputFile.exists()) {
					Files.move(Paths.get(archiveFolder + fileEntry.getName()),
							Paths.get(transitFolder + currentDateTime + "-temp/" + fileEntry.getName()));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void copyFileToTargetDirectory(File sourceFile, File destFile) {
		try {
			FileUtils.copyFile(sourceFile, destFile);
		} catch (IOException ioException) {
			logger.info("IOException:" + ioException.getMessage());
		} catch (Exception exception) {
			logger.info("Exception:" + exception.getMessage());
		}
	}

	private List<String> moveSourceToTargetDirectory(final File folder, String currentDateTime, String currentDate,List<String> archiveList) {
		List<String> batchDetailsList = postProcessingBatchDetails();
		List<String> archiveListCollection = new LinkedList<>();
		try {
			for (final File fileEntry : folder.listFiles()) {
				logger.info("process file is " + fileEntry.getName());
				File inputFile = new File(fileEntry.getName());
				String fileExt = FilenameUtils.getExtension(inputFile.toString());
				if (!inputFile.exists()) {
					Files.copy(Paths.get(folder.toString() + "/" + fileEntry.getName()),
							Paths.get(fileEntry.getName()));
				}
				if (fileExt.equals("xml")) {
					boolean validXmlInputFIle = xmlFileDocumentReader(inputFile.toString(), currentDateTime,
							currentDate);
					boolean validBatchType = checkBatchTypeOperation(inputFile.toString(), batchDetailsList,
							currentDateTime);
					boolean documentTagValidate = validateXmlInputFile(inputFile.toString());
					if (!validXmlInputFIle || !validBatchType || !documentTagValidate) {
						failedFileProcessing(fileEntry.getName(), currentDateTime);
						failedFileProcessing(fileEntry.getName().replace(".xml", ".pdf"), currentDateTime);
						new File(fileEntry.getName()).delete();
						new File(fileEntry.getName().replace(".xml", ".pdf")).delete();
						continue;
					}
					archiveList = fileSeparateOperation(inputFile.toString(), currentDate, currentDateTime,archiveList);
					archiveListCollection.addAll(archiveList);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return archiveList;
	}

	public List<String> fileSeparateOperation(String fileName, String currentDate, String currentDateTime,List<String> archiveFileList) {
		try {
			String xmlInputFile = fileName;
			String pdfInputFile = fileName.replace(".xml", ".pdf");
			if (fileName.contains("archiveOnly")) {
				boolean validFileCheck = inputXmlFileValidation(xmlInputFile);
				if (validFileCheck) {
					logger.info("archiveOnly file processing");
					archiveOnlyOperation(pdfInputFile, currentDateTime);
					archiveOnlyOperation(xmlInputFile, currentDateTime);
					archiveFileList.add(pdfInputFile);
					archiveFileList.add(xmlInputFile);
				} else {
					failedFileProcessing(xmlInputFile, currentDateTime);
					failedFileProcessing(pdfInputFile, currentDateTime);
				}
			} else if (fileName.contains("printArchive") && !(fileName.contains("_CC_"))) {
				boolean validFileCheck = inputXmlFileValidation(xmlInputFile);
				if (validFileCheck) {
					archiveOnlyOperation(pdfInputFile, currentDateTime);
					archiveOnlyOperation(xmlInputFile, currentDateTime);
					printArchiveFileProcessing(pdfInputFile, currentDateTime);
					printArchiveFileProcessing(xmlInputFile, currentDateTime);
				} else {
					failedFileProcessing(xmlInputFile, currentDateTime);
					failedFileProcessing(pdfInputFile, currentDateTime);
				}
			} else if (fileName.contains("_CC_")) {
				boolean ccRecipientCountCheck = validateCCRRecentFileType(fileName);
				boolean validFileCheck = inputXmlFileValidation(xmlInputFile);
				if (!(ccRecipientCountCheck) || !validFileCheck) {
					failedFileProcessing(xmlInputFile, currentDateTime);
					failedFileProcessing(pdfInputFile, currentDateTime);
				} else {
					archiveOnlyOperation(pdfInputFile, currentDateTime);
					archiveOnlyOperation(xmlInputFile, currentDateTime);
					fileName = FilenameUtils.removeExtension(fileName);
					String fileNameSplit[] = fileName.split("_");
					int ccNumber = 0;
					if (fileNameSplit.length >= 1) {
						ccNumber = Integer.parseInt(fileNameSplit[fileNameSplit.length - 1]);
					}
					primaryRecipientOperation(xmlInputFile, pdfInputFile, ccNumber, currentDate, currentDateTime);
				}
			} else if (fileName.contains("printOnly")) {
				exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
				logger.info("printOnly functionality is not available ");
				failedFileProcessing(xmlInputFile, currentDateTime);
				failedFileProcessing(pdfInputFile, currentDateTime);
			}
			new File(xmlInputFile).delete();
			new File(pdfInputFile).delete();
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("printOnly functionality is not available ");
		}
		return archiveFileList;
	}

	public void primaryRecipientOperation(String xmlFile, String pdfFile, int ccNumberCount, String currentDate,
			String currentDateTime) {
		try {
			File pdfInputFile = new File(pdfFile.toString());
			splitCCRecipientPDFFile(pdfInputFile, ccNumberCount, currentDate, currentDateTime);
			pdfInputFile.delete();

			File xmlInputFile = new File(xmlFile);
			Document document = xmlFileDocumentReader(xmlInputFile.toString());
			Element root = document.getDocumentElement();
			Integer sheetNumber = Integer.parseInt(root.getElementsByTagName("totalSheet").item(0).getTextContent());
			sheetNumber = sheetNumber - ccNumberCount;
			Integer numberOfPages = Integer
					.parseInt(root.getElementsByTagName("NumberOfPages").item(0).getTextContent());
			numberOfPages = numberOfPages - (ccNumberCount * 2);

			final Node node = document.getElementsByTagName("Document").item(0);
			final NodeList nodeList = node.getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				final Node documentNode = nodeList.item(i);
				if (documentNode.getNodeName().equals("NumberOfPages")) {
					documentNode.setTextContent(numberOfPages.toString());
				}
				if (documentNode.getNodeName().equals("totalSheet")) {
					documentNode.setTextContent(sheetNumber.toString());
				}
			}
			document.normalize();
			TransformerFactory transferFactory = TransformerFactory.newInstance();
			Transformer transformerReference = transferFactory.newTransformer();
			transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");
			String updatePrimaryXmlName = FilenameUtils.removeExtension(xmlFile);

			File updateXmlFile = new File(updatePrimaryXmlName + "_Primary" + ".xml");
			final DOMSource source = new DOMSource(document);
			final StreamResult streamResult = new StreamResult(xmlInputFile);
			transformerReference.transform(source, streamResult);

			xmlInputFile.renameTo(updateXmlFile);

			printArchiveFileProcessing(updateXmlFile.toString(), currentDateTime);

			splitCCRRecipientXmlFile(updateXmlFile, sheetNumber, numberOfPages, ccNumberCount, currentDate,
					currentDateTime);
			updateXmlFile.delete();

		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("exception primaryRecipientOperation() " + exception.getMessage());
		}
	}

	public void splitCCRRecipientXmlFile(File xmlFile, Integer sheetNumber, Integer numberOfPages, int ccNumberCount,
			String currentDate, String currentDateTime) {
		String fileName = xmlFile.toString();
		try {
			for (int i = 1; i <= ccNumberCount; i++) {
				String newFileName = xmlFile.toString();
				newFileName = newFileName.replace("_Primary", "_" + i);

				Document document = xmlFileDocumentReader(fileName);
				if (i == 1) {
					sheetNumber = sheetNumber + 1;
					numberOfPages = numberOfPages + 2;
				}
				final Node node = document.getElementsByTagName("Document").item(0);
				final NodeList nodeList = node.getChildNodes();
				for (int j = 0; j < nodeList.getLength(); j++) {
					final Node documentNode = nodeList.item(j);
					if (documentNode.getNodeName().equals("NumberOfPages")) {
						documentNode.setTextContent(numberOfPages.toString());
					}
					if (documentNode.getNodeName().equals("totalSheet")) {
						documentNode.setTextContent(sheetNumber.toString());
					}
				}
				document.normalize();
				TransformerFactory transferFactory = TransformerFactory.newInstance();
				Transformer transformerReference = transferFactory.newTransformer();
				transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");

				File updateXmlFile = new File(newFileName);
				final DOMSource source = new DOMSource(document);
				final StreamResult streamResult = new StreamResult(updateXmlFile);
				transformerReference.transform(source, streamResult);
				xmlFile.renameTo(updateXmlFile);
				printArchiveFileProcessing(updateXmlFile.toString(), currentDateTime);

				updateXmlFile.delete();
			}
			xmlFile.delete();
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception splitCCRRecipientXmlFile() " + exception.getMessage());
		}
	}

	public void splitCCRecipientPDFFile(File fileName, int recipientCount, String currentDate, String currentDateTime) {
		try {
			PDDocument splitDocument = PDDocument.load(fileName);
			Splitter splitter = new Splitter();
			splitter.setSplitAtPage(2);
			List<PDDocument> Pages = splitter.split(splitDocument);
			Iterator<PDDocument> iterator = Pages.listIterator();
			int i = 1;
			int count = 0;
			List<String> pdfListFile = new LinkedList<>();
			String fileSplitName = FilenameUtils.removeExtension(fileName.toString());
			while (iterator.hasNext()) {
				String splitFileName = "split" + i++ + ".pdf";
				pdfListFile.add(splitFileName);
				PDDocument pdDocument = iterator.next();
				count++;
				pdDocument.save(splitFileName);
				pdDocument.close();
			}
			splitDocument.close();
			MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
			PDFMergerUtility splitPdfMerger = new PDFMergerUtility();
			for (int a = recipientCount + 1; a <= count; a++) {
				splitPdfMerger.addSource("split" + a + ".pdf");
			}
			splitPdfMerger.setDestinationFileName("primary" + ".pdf");
			splitPdfMerger.mergeDocuments(memoryUsageSetting);
			File primaryCCRecipient = new File("primary" + ".pdf");
			File updatePrimaryFileName = new File(fileSplitName + "_Primary" + ".pdf");
			primaryCCRecipient.renameTo(updatePrimaryFileName);
			printArchiveFileProcessing(updatePrimaryFileName.toString(), currentDateTime);

			for (int j = 1; j <= recipientCount; j++) {
				PDFMergerUtility pdfMerger = new PDFMergerUtility();
				pdfMerger.addSource("split" + j + ".pdf");
				pdfMerger.addSource(updatePrimaryFileName.toString());
				pdfMerger.setDestinationFileName(fileSplitName + "_" + j + ".pdf");
				pdfMerger.mergeDocuments(memoryUsageSetting);
				String ccRecipientPDF = fileSplitName + "_" + j + ".pdf";
				printArchiveFileProcessing(ccRecipientPDF, currentDateTime);
				new File(ccRecipientPDF).delete();
			}
			deleteFiles(pdfListFile);
			updatePrimaryFileName.delete();
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("exception:" + exception.getMessage());
		}
	}

	public void deleteFiles(List<String> fileNameList) {
		for (String fileName : fileNameList) {
			File file = new File(fileName);
			if (file.exists()) {
				file.delete();
			}
		}
	}

	public boolean validateCCRRecentFileType(String fileName) {
		String fileNames[] = fileName.split("_");
		String updateCCNumber = fileNames[fileNames.length - 1];
		return updateCCNumber.matches(".*\\d.*");
	}

	public boolean inputXmlFileValidation(String inputXmlFile) {
		return validateXmlInputFile(inputXmlFile);
	}

	public boolean validateXmlInputFile(String fileName) {
		boolean validXmlFile = true;
		try {
			Document document = xmlFileDocumentReader(fileName);
			final Node node = document.getElementsByTagName("Document").item(0);
			if (Objects.isNull(node)) {
				logger.info("missing " + "Document" + " tag element");
				validXmlFile = false;
			}
			Node numberOfPagesList = document.getElementsByTagName("NumberOfPages").item(0);
			Node totalSheetTagList = document.getElementsByTagName("totalSheet").item(0);
			Node dcnNbr = document.getElementsByTagName("DCN").item(0);
			if (Objects.isNull(numberOfPagesList) || Objects.isNull(totalSheetTagList) || Objects.isNull(dcnNbr)) {
				logger.info("invalid xml input file : missing Document,totalSheet and DCN tag:" + fileName);
				validXmlFile = false;
			}
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception validateXmlInputFile() " + exception.getMessage());
		}
		return validXmlFile;
	}

	public Document xmlFileDocumentReader(String fileName)
			throws ParserConfigurationException, SAXException, IOException {
		Document document = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(new File(fileName));
			document.getDocumentElement().normalize();
		} catch (Exception documentException) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception xmlFileDocumentReader() :" + documentException.getMessage());
		}
		return document;
	}

	public void archiveOnlyOperation(String inputFile, String currentDateTime) {
		try {
			String fileExt = FilenameUtils.getExtension(inputFile);
			File file = new File(inputFile);
			File copyOriginalFile = new File("copyoriginal_" + inputFile);
			if (!(copyOriginalFile.exists())) {
				Files.copy(file.toPath(), copyOriginalFile.toPath());
			}
			if ("pdf".equals(fileExt)) {
				String[] splitFileName = file.toString().split("_");
				File updatePDFFile = new File(splitFileName[0] + ".pdf");
				copyOriginalFile.renameTo(updatePDFFile);
				archiveOnlyFileProcessing(updatePDFFile.toString(), currentDateTime);

				updatePDFFile.delete();
			} else if ("xml".equals(fileExt)) {
				Document document = xmlFileDocumentReader(file.toString());
				Element root = document.getDocumentElement();
				String claimNumber = "";

				final Node node = document.getElementsByTagName("Document").item(0);
				final NodeList nodeList = node.getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					final Node documentNode = nodeList.item(i);
					if (documentNode.getNodeName().equals("totalSheet")) {
						node.removeChild(documentNode);
					}
					if (documentNode.getNodeName().equals("DCN")) {
						claimNumber = root.getElementsByTagName("DCN").item(0).getTextContent();
					}
				}
				document.normalize();
				TransformerFactory transferFactory = TransformerFactory.newInstance();
				Transformer transformerReference = transferFactory.newTransformer();
				transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");

				File updateXMLFile = new File(claimNumber + ".xml");

				final DOMSource source = new DOMSource(document);
				final StreamResult streamResult = new StreamResult(copyOriginalFile);
				transformerReference.transform(source, streamResult);
				copyOriginalFile.renameTo(updateXMLFile);

				archiveOnlyFileProcessing(updateXMLFile.toString(), currentDateTime);
				updateXMLFile.delete();
			}

		} catch (TransformerException fileTransferException) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception archiveOnlyOperation() " + fileTransferException.getMessage());
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("Exception archiveOnlyOperation() " + exception.getMessage());
		}
	}

	public boolean checkBatchTypeOperation(String fileName, List<String> batchDetailsList, String currentDateTime) {
		boolean validBatchType = true;
		boolean batchTypeCheckOperation = checkBatchTypeOperation(fileName, batchDetailsList);
		if (!batchTypeCheckOperation) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			String xmlInputFile = fileName;
			String pdfInputFile = fileName.replace(".xml", ".pdf");
			invalidFileList.add(xmlInputFile);
			invalidFileList.add(pdfInputFile);
			logger.info("processing batch type is not supported");
			failedFileProcessing(xmlInputFile, currentDateTime);
			failedFileProcessing(pdfInputFile, currentDateTime);
			validBatchType = false;
		}
		return validBatchType;
	}

	public boolean checkBatchTypeOperation(String fileName, List<String> batchDetailsList) {
		for (String ediFormName : batchDetailsList) {
			if (fileName.contains(ediFormName)) {
				return true;
			}
		}
		return false;
	}

	public List<String> postProcessingBatchDetails() {
		List<String> batchDetailsList = new LinkedList<String>();
		for (String stateBatch : stateBatchType) {
			batchDetailsList.add(stateBatch);
		}
		for (String pageBatch : pageTypeList) {
			batchDetailsList.add(pageBatch);
		}
		for (String ediBatch : ediFormsType) {
			batchDetailsList.add(ediBatch);
		}
		batchDetailsList.add(selfAddressedType);
		return batchDetailsList;
	}

	public boolean xmlFileDocumentReader(String fileName, String currentDate, String currentDateTime)
			throws ParserConfigurationException, SAXException, IOException {
		boolean validaXmlFile = true;
		String pdfInputFile = fileName.replace(".xml", ".pdf");
		File pdfFile = new File(pdfInputFile);
		try {
			if (!(pdfFile.exists())) {
				failedFileProcessing(fileName, currentDateTime);
				failedFileProcessing(pdfFile.toString(), currentDateTime);
				pdfFile.delete();
				return false;
			}
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(new File(fileName));
			document.getDocumentElement().normalize();
		} catch (Exception documentException) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("invalid xml for processing :" + fileName + " " + documentException.getMessage());
			validaXmlFile = false;
			invalidFileList.add(fileName);
		}
		if (!validaXmlFile) {
			failedFileProcessing(fileName, currentDateTime);
			failedFileProcessing(pdfInputFile, currentDateTime);
			pdfFile.delete();
		}
		return validaXmlFile;
	}

	public void failedFileProcessing(String fileName, String currentDateTime) {
		try {
			File failedFolder = new File(transitFolder + currentDateTime + "-failed/");
			failedFolder.mkdir();
			logger.info("incorrect files for processing: " + fileName);
			Files.copy(Paths.get(fileName), Paths.get(transitFolder + currentDateTime + "-failed/" + fileName));
			failedFileList.add(fileName);
			failedFileList.add(fileName.replace(".xml", ".pdf"));
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("incorrect files for processing: " + exception.getMessage());
		}
	}

	public void printArchiveFileProcessing(String fileName, String currentDateTime) {
		try {
			File printFolder = new File(transitFolder + currentDateTime + "-print/");
			printFolder.mkdir();
			logger.info("incorrect files for processing: " + fileName);
			Files.copy(Paths.get(fileName), Paths.get(transitFolder + currentDateTime + "-print/" + fileName));
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("incorrect files for processing: " + exception.getMessage());
		}
	}

	public void archiveOnlyFileProcessing(String fileName, String currentDateTime) {
		File archiveFolder = new File(transitFolder + currentDateTime + "-archive/");
		archiveFolder.mkdir();
		try {
			Files.move(Paths.get(fileName), Paths.get(transitFolder + currentDateTime + "-archive/" + fileName));
		} catch (Exception exception) {
			exceptionMessage = PostProcessingConstant.EXCEPTION_MSG;
			logger.info("incorrect files for processing: " + exception.getMessage());
		}
	}

	public String getFileName(String blobName) {
		return blobName.replace(OUTPUT_DIRECTORY + ARCHIVE_DIRECTORY, "");
	}

	public String currentDate() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return dateFormat.format(date);
	}

	public String currentDateTimeStamp() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
		return dateFormat.format(date);
	}
}

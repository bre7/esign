package uy.com.agesic.firma;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tomcat.util.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.gemalto.ics.rnd.egov.dss.sdk.verify.DSSResult;
import com.gemalto.ics.rnd.egov.dss.sdk.verify.DSSResultSuccess;
import com.gemalto.ics.rnd.egov.dss.sdk.verify.api.DefaultResponseParserFactory;
import com.gemalto.ics.rnd.egov.dss.sdk.verify.api.ResponseParser;
import com.gemalto.ics.rnd.egov.dss.sdk.verify.signature.JCAKeyStoreTrustStore;

@Controller
public class ResponseDSSController {

	/*
	 * DSS response configuration parameters
	 */
	@Value("${dss.signed.document.path}")
	private String signedDocumentPath;

	/*
	 * DSS TrustStore configuration parameters
	 */
	@Value("${truststore.route}")
	private String trustStoreRoute;

	@Value("${truststore.key}")
	private String trustStoreKey;

	@Value("${truststore.alias}")
	private String trustStoreAlias;

	private static final int BUFFER_SIZE = 4096;

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	/**
	 * Method for handling signed response from the DSS server
	 */
	@RequestMapping(value = "/respuestaDSS", method = RequestMethod.POST)
	public String response(@RequestParam("SignResponse") String[] signResponse, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		String sessionId = request.getSession().getId();

		Security.addProvider(new BouncyCastleProvider());
		InputStream ts = new FileInputStream(trustStoreRoute);

		JCAKeyStoreTrustStore trustStore = new JCAKeyStoreTrustStore("BC", "PKCS12", ts, trustStoreKey,
				trustStoreAlias);

		ResponseParser responseParser = DefaultResponseParserFactory.getResponseParser(trustStore, null);

		String signResponseBase64 = signResponse[0];
		String responseDocument = new String(Base64.decodeBase64(signResponseBase64));

		DSSResult result = responseParser.parseAndGetResult(responseDocument);

		if (result instanceof DSSResultSuccess) {
			byte[] documento = ((DSSResultSuccess) result).getDocumentData();
			// Convertir arreglo de bytes en archivo
			FileOutputStream salida = new FileOutputStream(signedDocumentPath + RequestDSSController.uploadName);
			salida.write(documento);
			salida.close();
		}

		log.info(sessionId + " RECIBIO EL ARCHIVO DEL DSS");

		return "respuestaDSS";
	}

	/**
	 * Method for handling file download request from client
	 */
	@RequestMapping(value = "/download", method = RequestMethod.GET)
	public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {

		String sessionId = request.getSession().getId();

		// get absolute path of the application
		ServletContext context = request.getServletContext();

		// construct the complete absolute path of the file
		String fullPath = signedDocumentPath + RequestDSSController.uploadName;
		File downloadFile = new File(fullPath);
		FileInputStream inputStream = new FileInputStream(downloadFile);

		// get MIME type of the file
		String mimeType = context.getMimeType(fullPath);
		if (mimeType == null) {
			// set to binary type if MIME mapping not found
			mimeType = "application/octet-stream";
		}

		// set content attributes for the response
		response.setContentType(mimeType);
		response.setContentLength((int) downloadFile.length());

		// set headers for the response
		String headerKey = "Content-Disposition";
		String headerValue = String.format("attachment; filename=\"%s\"", downloadFile.getName());
		response.setHeader(headerKey, headerValue);

		// get output stream of the response
		OutputStream outStream = response.getOutputStream();

		byte[] buffer = new byte[BUFFER_SIZE];
		int bytesRead = -1;

		// write bytes read from the input stream into the output stream
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outStream.write(buffer, 0, bytesRead);
		}

		inputStream.close();
		outStream.close();

		log.info(sessionId + " DESCARGO EL ARCHIVO");

		TimeSingleton.getInstance().setFirstTime();

		TimeSingleton.getInstance().setSecondTime();
		if (TimeSingleton.getInstance().getCurrentTime()[0] != null) {

			Long timeResult = (TimeSingleton.getInstance().getCurrentTime()[1]
					- TimeSingleton.getInstance().getCurrentTime()[0]) / 1000;
			log.info(sessionId + " TARDO " + timeResult + "s EN FIRMAR PDF");
		}

	}

}

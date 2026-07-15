package com.keykomi.jack.processing.application;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

final class SecureXmlParser {

	private SecureXmlParser() {
	}

	static Document parse(String content) throws ParserConfigurationException, IOException, SAXException {
		var factory = newFactory();
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
	}

	static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

		// Office/EPUB containers недоверенные: DTD и внешние сущности запрещаем на
		// всех уровнях, чтобы parser не мог читать локальные файлы или ходить в сеть.
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}
}

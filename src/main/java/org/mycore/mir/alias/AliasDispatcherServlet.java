package org.mycore.mir.alias;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRPathContent;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.frontend.servlets.MCRContentServlet;
import org.mycore.solr.MCRSolrClientFactory;
import org.mycore.solr.MCRSolrUtils;
import org.xml.sax.SAXException;

/**
 *
 *
 * The {@code AliasDispatcherServlet} is a resolving mechanism to use meaningful
 * names for IDs in mycore.
 *
 * @author Paul Rochowski
 */
public class AliasDispatcherServlet extends MCRContentServlet {

	/**
	 * 
	 * default generated serial id
	 */
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LogManager.getLogger(AliasDispatcherServlet.class);

	// Solr Fieldnames
	private static final String OBJECT_ID = "id";
	private static final String ALIAS = "alias";

	public static final String FILE_PATTERN_LAYOUTSERVICE = "MCR.Alias.Filepattern";

	/**
	 * 
	 * Client which communicates with MyCoRe Solr Server
	 */
	SolrClient solrClient = MCRSolrClientFactory.getMainSolrClient();

	MCRConfiguration configuration = MCRConfiguration.instance();

	@Override
	public MCRContent getContent(HttpServletRequest request, HttpServletResponse response) throws IOException {

		SolrDocumentList results = new SolrDocumentList();
		String path = request.getPathInfo();

		boolean isforwarded = false;

		if (path != null) {

			List<String> pathParts = new ArrayList<String>(Arrays.asList(path.split("/")));

			pathParts.removeIf(pathPart -> pathPart.isEmpty());

			if (!pathParts.isEmpty()) {

				LOGGER.debug("Try to resolve Alias with path: " + path);

				path = parsePath(path);
				/*
				 * Try to resolve path as an alias
				 */
				results = getMCRObjectsFromSolr(path);

				/*
				 * Use request dispatcher to forward alias URL to equal MyCoRe Object URL
				 */
				if (!results.isEmpty() && results.get(0) != null) {

					RequestDispatcher dispatcher = request.getServletContext()
							.getRequestDispatcher("/receive/" + results.get(0).getFieldValue(OBJECT_ID));

					try {
						dispatcher.forward(request, response);
						isforwarded = true;

						LOGGER.info("Alias was found with Object id: " + results.get(0).getFieldValue(OBJECT_ID));
						LOGGER.info("Alias will be dispatched to to path /reveive/"
								+ results.get(0).getFieldValue(OBJECT_ID));
					} catch (ServletException e) {

						LOGGER.error("Error on dispatching object with id: " + results.get(0).getFieldValue(OBJECT_ID));
					}
				}

				/*
				 * If MyCoRe Object wasn't found, check higher path
				 */
				if (pathParts.size() > 1 && !isforwarded) {

					String aliasPart = "/";

					for (int i = 0; i < pathParts.size() - 1; i++) {

						aliasPart = aliasPart + pathParts.get(i) + "/";
					}

					String possibleFilename = pathParts.get(pathParts.size() - 1);

					LOGGER.debug("Try to resolve file with filename: " + possibleFilename);
					LOGGER.debug("Looking for derivates in alias: " + aliasPart);

					/*
					 * try to resolve mycore object belongs to filename
					 */
					results = getMCRObjectsFromSolr(parsePath(aliasPart));

					if (!results.isEmpty()) {

						Object documentId = results.get(0).getFieldValue(OBJECT_ID);

						if (documentId != null && documentId instanceof String) {

							/*
							 * Get doument Id as MCRObjectID
							 */
							MCRObjectID mcrObjectId = MCRObjectID.getInstance((String) documentId);

							/*
							 * get derivatives
							 */
							List<MCRObjectID> derivatesForDocument = MCRMetadataManager.getDerivateIds(mcrObjectId, 0,
									TimeUnit.MILLISECONDS);

							if (derivatesForDocument != null) {

								for (MCRObjectID mcrDerivateID : derivatesForDocument) {

									if (!isforwarded) {

										LOGGER.debug("Looking in derivate " + mcrDerivateID.toString()
												+ " for filename: " + possibleFilename);

										MCRPath mcrPath = MCRPath.getPath(mcrDerivateID.toString(), possibleFilename);

										Files.readAttributes(mcrPath, BasicFileAttributes.class);

										LOGGER.info(possibleFilename + " was found in derivate "
												+ mcrDerivateID.toString());

										/*
										 * Should the file be transformed via getLayoutService() ?
										 */
										final String aliasFilePattern = configuration
												.getString(FILE_PATTERN_LAYOUTSERVICE, "");

										if (!aliasFilePattern.isEmpty() && possibleFilename.matches(aliasFilePattern)) {

											MCRContent mcrContent = new MCRPathContent(mcrPath);

											try {

												LOGGER.info("Alias was requested on MCR Session: " + MCRSessionMgr.getCurrentSessionID());
												
												LOGGER.info("Alias won't be dispatched!");
												LOGGER.info("File will be transformed via MCRLayoutService: "
														+ mcrDerivateID.toString() + "/" + possibleFilename);
												return getLayoutService().getTransformedContent(request, response,
														mcrContent);

											} catch (TransformerException | SAXException e) {
												throw new IOException("could not transform content", e);
											}

										} else {

											RequestDispatcher dispatcher = request.getServletContext()
													.getRequestDispatcher("/servlets/MCRFileNodeServlet/"
															+ mcrDerivateID.toString() + "/" + possibleFilename);

											try {
												dispatcher.forward(request, response);
												isforwarded = true;

												LOGGER.info(
														"Alias will be dispatched to to path /servlets/MCRFileNodeServlet/"
																+ mcrDerivateID.toString() + "/" + possibleFilename);

											} catch (ServletException e) {

												LOGGER.error("Error on dispatching file to MCRFileNodeServlet: "
														+ mcrDerivateID.toString() + "/" + possibleFilename);
											}
										}
									}
								}
							}
						}
					}
				}
				
				LOGGER.info("Alias was requested on MCR Session: " + MCRSessionMgr.getCurrentSessionID());
			}
		}

		// Error redirect
		if (!isforwarded) {

			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested Alias was not found: " + path);
		}
		
		return null;
	}

	/**
	 * 
	 * Helper method for resolve an alias in solr
	 * 
	 * @param aliasPart alias part
	 * @return Documentlist associated with given alias from solr
	 */
	protected SolrDocumentList getMCRObjectsFromSolr(String aliasPart) {

		SolrDocumentList results = new SolrDocumentList();

		try {

			String searchStr = ALIAS + ":%filter%".replace("%filter%",
					aliasPart != null && !aliasPart.isEmpty() ? MCRSolrUtils.escapeSearchValue(aliasPart) : "*");

			results = resolveSolrDocuments(searchStr);

			if (results.size() > 1) {

				LOGGER.info("Multiple MCR Objects found for Alias " + aliasPart);
			}

		} catch (SolrServerException | IOException e) {

			LOGGER.error(e.getMessage());
			LOGGER.error("Error in communication with solr server: " + e.getMessage());
		}

		return results;
	}

	/**
	 * Parses additional chars to given path
	 * 
	 * @param path searchpath
	 * @return path without additional chars
	 */
	private String parsePath(String path) {

		/*
		 * remove "/" character at beginning and end
		 */
		if (path.charAt(path.length() - 1) == '/') {

			path = StringUtils.chop(path);
		}

		return path.substring(1);
	}

	/**
	 * 
	 * Resolving Solr Documents with given searchStr
	 * 
	 *
	 * @param searchStr search String for solr query
	 * @return if there is an indexing in solr, then get the resolved document
	 * @throws SolrServerException
	 * @throws IOException
	 */
	public SolrDocumentList resolveSolrDocuments(String searchStr) throws SolrServerException, IOException {

		SolrQuery query = new SolrQuery();

		query.setQuery(searchStr);
		query.setStart(0);

		QueryResponse response;
		response = solrClient.query(query);

		return response.getResults();
	}
}
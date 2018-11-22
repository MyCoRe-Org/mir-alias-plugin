package org.mycore.alias.servlets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import org.mycore.access.MCRAccessManager;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRPathContent;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
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
* The {@code MCRAliasContentServlet} is a resolving mechanism to use meaningful
* names for IDs in mycore.
*
* @author Paul Rochowski
*/
public class MCRAliasContentServlet extends MCRContentServlet {

    /**
     * 
     * default generated serial id
     */
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LogManager.getLogger(MCRAliasContentServlet.class);

    // Solr Fieldnames
    private static final String OBJECT_ID = "id";
    private static final String ALIAS = "alias";

    public static final String FILE_PATTERN_LAYOUTSERVICE = "MCR.Alias.Filepattern";

    /**
     * 
     * Client which communicates with MyCoRe Solr Server
     */
    private SolrClient solrClient = MCRSolrClientFactory.getMainSolrClient();

    private MCRXMLMetadataManager metadataManager = MCRXMLMetadataManager.instance();

    MCRConfiguration configuration = MCRConfiguration.instance();

    @Override
    public MCRContent getContent(HttpServletRequest request, HttpServletResponse response) throws IOException {

        SolrDocumentList results = new SolrDocumentList();
        String path = request.getPathInfo();

        if (path == null || path.equals("/")) {

            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No Alias path was set!");
            return null;
        }

        List<String> pathParts = new ArrayList<String>(Arrays.asList(path.split("/")));

        pathParts.removeIf(pathPart -> pathPart.isEmpty());
        path = parsePath(path);
        /*
         * Try to resolve path as an alias
         */
        results = getMCRObjectsFromSolr(path);

        if (!results.isEmpty() && results.get(0) != null) {

            LOGGER.info("Alias was found with Object id: " + results.get(0).getFieldValue(OBJECT_ID));

            /*
             * Get document Id as MCRObjectID
             */
            MCRObjectID mcrObjId = MCRObjectID.getInstance((String) results.get(0).getFieldValue(OBJECT_ID));

            LOGGER.info("Check read permission on MyCoRe Object Id " + results.get(0).getFieldValue(OBJECT_ID)
                    + " with current user.");
            if (MCRAccessManager.checkPermission(mcrObjId, MCRAccessManager.PERMISSION_READ)) {
                if (MCRMetadataManager.exists(mcrObjId)) {
                    MCRContent metadataContent = metadataManager.retrieveContent(mcrObjId);

                    LOGGER.info("Start to do layout transformation with retreived metadata content");

                    try {
                        return getLayoutService().getTransformedContent(request, response, metadataContent);
                    } catch (TransformerException | SAXException e) {
                        throw new IOException("could not transform metadata Content", e);
                    }
                }
            }
        }

        /*
         * If MyCoRe Object wasn't found, check higher path
         */
        if (pathParts.size() > 1) {

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
                     * Get document Id as MCRObjectID
                     */
                    MCRObjectID mcrObjIdFromAliasPart = MCRObjectID.getInstance((String) documentId);

                    /*
                     * get derivatives
                     */
                    List<MCRObjectID> derivatesForDocument = MCRMetadataManager.getDerivateIds(mcrObjIdFromAliasPart, 0,
                            TimeUnit.MILLISECONDS);

                    if (derivatesForDocument != null) {

                        for (MCRObjectID mcrDerivateID : derivatesForDocument) {

                            LOGGER.debug("Looking in derivate " + mcrDerivateID.toString() + " for filename: "
                                    + possibleFilename);

                            MCRPath mcrPath = MCRPath.getPath(mcrDerivateID.toString(), possibleFilename);

                            /*
                             * Is the specified filename existing in the derivate?
                             *  -> If no, continue iteration through derivate list
                             */
                            try {
                                Files.readAttributes(mcrPath, BasicFileAttributes.class);
                            } catch (Exception exc) {

                                LOGGER.info(mcrDerivateID.toString() + ":/ -> " + possibleFilename
                                        + " : file does not exist");
                                continue;
                            }
                            LOGGER.info(possibleFilename + " was found in derivate " + mcrDerivateID.toString());

                            /*
                             * permission check on derivate
                             */
                            if (!MCRAccessManager.checkPermissionForReadingDerivate(mcrDerivateID.toString())) {
                                LOGGER.info("AccessForbidden to {}", request.getPathInfo());
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                                return null;
                            }

                            MCRContent mcrContent = new MCRPathContent(mcrPath);

                            final String aliasFilePattern = configuration.getString(FILE_PATTERN_LAYOUTSERVICE, "");

                            /*
                             * Should the file be transformed via getLayoutService() ?
                             */
                            if (!aliasFilePattern.isEmpty() && possibleFilename.matches(aliasFilePattern)) {

                                try {

                                    LOGGER.info("File will be transformed via MCRLayoutService: "
                                            + mcrDerivateID.toString() + "/" + possibleFilename);
                                    return getLayoutService().getTransformedContent(request, response, mcrContent);

                                } catch (TransformerException | SAXException e) {
                                    throw new IOException("could not transform content", e);
                                }

                            } else {

                                LOGGER.info("File will be return as MCRPathContent: " + mcrDerivateID.toString() + "/"
                                        + possibleFilename);

                                return mcrContent;
                            }
                        }
                    }
                }

            }
        }

        // Error redirect
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested Alias was not found: " + path);
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
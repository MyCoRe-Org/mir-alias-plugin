package org.mycore.alias.servlets;

import static org.mycore.access.MCRAccessManager.PERMISSION_READ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration2;
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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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


    /**
     * 
     * Client which communicates with MyCoRe Solr Server
     */
    private SolrClient solrClient = MCRSolrClientFactory.getMainSolrClient();

    private MCRXMLMetadataManager metadataManager = MCRXMLMetadataManager.instance();
    private String aliasFilePattern;


    @Override
    public void init() throws ServletException {
        super.init();
        this.aliasFilePattern = MCRConfiguration2.getString("MCR.Alias.Filepattern").get();
    }
    
    @Override
    public MCRContent getContent(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = request.getPathInfo();

        if (path == null || path.equals("/")) {

            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No Alias path was set!");
            return null;
        }

        LOGGER.info("Start try to get MCRContent via MCRAliasContentServlet on path: " + path);
        LOGGER.info("Use MCRSession: " + MCRSessionMgr.getCurrentSessionID());

        /*
         * Take the whole path and decrease it to get root alias!
         */
        SolrDocumentList rootAlias = new SolrDocumentList();
        MCRContent contentFromAliasPath = null;

        String decreasedPath = parsePath(path);
        int lastIndexSlash = decreasedPath.lastIndexOf('/');

        boolean isContentResolved = false;

        while (!decreasedPath.isEmpty() && !isContentResolved) {

            /*
             * Try to resolve decreased path as an alias
             */
            rootAlias = getMCRObjectsFromSolr(decreasedPath);

            if (!rootAlias.isEmpty() && rootAlias.get(0) != null) {

                LOGGER.info("Alias was found with Object id: " + rootAlias.get(0).getFieldValue(OBJECT_ID));

                String aliasPathContextOrig = parsePath(path).replaceFirst(decreasedPath, "");
                String aliasPathContext = aliasPathContextOrig.toLowerCase(Locale.ROOT);

                contentFromAliasPath = getContentFromAliasPath(aliasPathContext, path,
                        (String) rootAlias.get(0).getFieldValue(OBJECT_ID), request, response);

                isContentResolved = contentFromAliasPath != null;
            }

            if (!isContentResolved) {

                if (lastIndexSlash != -1) {
                    decreasedPath = decreasedPath.substring(0, lastIndexSlash);
                    lastIndexSlash = decreasedPath.lastIndexOf('/');
                } else {

                    LOGGER.info("The alias path " + path
                            + " can not be resolved. Return 'Requested Alias was not found' error message.");
                    decreasedPath = "";
                }
            }
        }

        if (contentFromAliasPath == null) {
            // Error redirect
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested Alias cannot be resolved on path: " + path);
        }
        return contentFromAliasPath;
    }

    private static String getAliasFromDocument(SolrDocument d) {
        return (String) d.getFieldValue(ALIAS);
    }

    private static void logPossibleAliasDocument(SolrDocument doc) {
        LOGGER.debug("Possible alias found {}", getAliasFromDocument(doc).toLowerCase(Locale.ROOT));
    }

    private static boolean isDocumentAliasMatching(String cleanAliasPathContext, SolrDocument doc) {
        return cleanAliasPathContext.startsWith(getAliasFromDocument(doc).toLowerCase(Locale.ROOT));
    }

    private static boolean documentHasAlias(SolrDocument doc) {
        return doc.getFieldValue(ALIAS) != null && doc.getFieldValue(ALIAS) instanceof String;
    }

    private MCRContent getContentFromAliasPath(String aliasPathContext, String fullPath, String objectId,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!aliasPathContext.isEmpty()) {

            if (aliasPathContext.startsWith("/")) {

                aliasPathContext = parsePath(aliasPathContext);

                if (!aliasPathContext.contains("/")) {

                    String possibleFilename = aliasPathContext;

                    /*
                     * Get document Id as MCRObjectID
                     */
                    MCRObjectID mcrObjIdFromAliasPart = MCRObjectID.getInstance(objectId);

                    /*
                     * get derivatives
                     */
                    List<MCRObjectID> derivatesForDocument = MCRMetadataManager.getDerivateIds(mcrObjIdFromAliasPart, 0,
                            TimeUnit.MILLISECONDS);

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

                            LOGGER.info(
                                    mcrDerivateID.toString() + ":/ -> " + possibleFilename + " : file does not exist");
                            continue;
                        }
                        LOGGER.info(possibleFilename + " was found in derivate " + mcrDerivateID.toString());

                        /*
                         * permission check on derivate
                         */
                        if (!MCRAccessManager.checkDerivateContentPermission(mcrDerivateID, PERMISSION_READ)) {
                            LOGGER.info("AccessForbidden to {}", request.getPathInfo());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            return null;
                        }

                        MCRContent mcrContent = new MCRPathContent(mcrPath);

                        /*
                         * Should the file be transformed via getLayoutService() ?
                         */
                        if (!aliasFilePattern.isEmpty() && possibleFilename.matches(aliasFilePattern)) {

                            try {

                                LOGGER.info("File will be transformed via MCRLayoutService: " + mcrDerivateID.toString()
                                        + "/" + possibleFilename);
                                request.setAttribute("XSL.MCRObjectID", mcrObjIdFromAliasPart.toString());
                                request.setAttribute("XSL.MCRDerivateID", mcrDerivateID.toString());
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

                /*
                 * look for related items
                 *
                 */
                LOGGER.info("Check if alias path context " + aliasPathContext
                        + " exists for document/derivate relations on " + objectId);

                String searchStr = "mods.relatedItem:" + objectId;

                try {
                    SolrDocumentList relatedDocuments = resolveSolrDocuments(searchStr);
                    String nextAliasPathContextAfter = aliasPathContext;
                    String relatedObjectId = null;

                    LOGGER.debug("Process Alias Path Context: Try to shrink Alias Path Context " + aliasPathContext);
                    List<String> pathParts = Stream.of(aliasPathContext.split("/"))
                        .filter(Predicate.not(String::isEmpty))
                        .map(p -> p.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toList());

                    String cleanAliasPathContext = String.join("/", pathParts);

                    // the longer the alias of the document is, the more likely it is the correct one
                    Comparator<SolrDocument> compareBestMatchingAlias = Comparator
                        .comparing((doc) -> getAliasFromDocument(doc).length(), Integer::compareTo);

                    Optional<SolrDocument> bestMatchingOptionalDocument = relatedDocuments.stream()
                        .filter(MCRAliasContentServlet::documentHasAlias)
                        .filter(doc -> isDocumentAliasMatching(cleanAliasPathContext, doc))
                        .peek(MCRAliasContentServlet::logPossibleAliasDocument)
                        .max(compareBestMatchingAlias);

                    if (bestMatchingOptionalDocument.isPresent()) {
                        SolrDocument relatedDocument = bestMatchingOptionalDocument.get();

                        relatedObjectId = (String) relatedDocument.getFieldValue(OBJECT_ID);
                        String alias = getAliasFromDocument(relatedDocument).toLowerCase(Locale.ROOT);

                        nextAliasPathContextAfter = cleanAliasPathContext.substring(alias.length());
                        LOGGER.info("---- Process Alias Path Context: " + alias + " found in "
                            + aliasPathContext + ". Shrink aliasPathContext into " + nextAliasPathContextAfter);
                    }
                    
                    if (relatedObjectId != null) {

                        LOGGER.debug("Process Alias Path Context: Alias Path Context " + aliasPathContext
                            + " found in Document " + relatedObjectId);
                        
                        aliasPathContext = nextAliasPathContextAfter;

                        LOGGER.info("Process Alias Path Context:  New Alias Path Context is [" + aliasPathContext + "]");

                        return getContentFromAliasPath(aliasPathContext, fullPath, relatedObjectId, request, response);
                    }
                    
                    
                    if (relatedDocuments.getNumFound() == 0) {
                        LOGGER.debug("Process Alias Path Context: No Documents found with searchStr: [" + searchStr + "]");
                    } else {
                        LOGGER.debug("Process Alias Path Context: Solr query [" + searchStr + "] has got "
                            + relatedDocuments.getNumFound()
                            + " documents, but these documents does not include aliasPathContext " + aliasPathContext);
                    }

                } catch (SolrServerException | IOException e) {
                    LOGGER.error("Error in communication with solr server: " + e.getMessage());
                }
            }

            LOGGER.info("The alias path context " + aliasPathContext
                    + " does not exist for document/derivate relations on " + objectId);
        } else {

            /*
             * Get document Id as MCRObjectID
             */
            MCRObjectID mcrObjId = MCRObjectID.getInstance(objectId);

            LOGGER.info("Check read permission on MyCoRe Object Id " + objectId + " with current user.");
            if (MCRAccessManager.checkPermission(mcrObjId, MCRAccessManager.PERMISSION_READ)
                    && (MCRMetadataManager.exists(mcrObjId))) {

                MCRContent metadataContent = metadataManager.retrieveContent(mcrObjId);

                LOGGER.info("Start to do layout transformation with retreived metadata content");

                try {
                    return getLayoutService().getTransformedContent(request, response, metadataContent);
                } catch (TransformerException | SAXException e) {
                    throw new IOException("could not transform metadata Content", e);
                }
            }

            /*
             * user have not the permission
             */
            LOGGER.info("Current user have not the permission to resolve the document via alias " + fullPath);
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
        query.setRows(300);
        query.setStart(0);

        QueryResponse response;
        response = solrClient.query(query);

        return response.getResults();
    }
}
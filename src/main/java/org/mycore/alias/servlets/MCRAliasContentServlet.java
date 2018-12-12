package org.mycore.alias.servlets;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.content.MCRContent;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
import org.mycore.frontend.servlets.MCRContentServlet;
import org.mycore.solr.MCRSolrClientFactory;
import org.mycore.solr.MCRSolrUtils;

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

        while (!decreasedPath.isEmpty()) {

            /*
             * Try to resolve decreased path as an alias
             */
            rootAlias = getMCRObjectsFromSolr(decreasedPath);

            if (!rootAlias.isEmpty() && rootAlias.get(0) != null) {

                LOGGER.info("Alias was found with Object id: " + rootAlias.get(0).getFieldValue(OBJECT_ID));

                String aliasPathContext = parsePath(path).replaceFirst(decreasedPath, "");

                contentFromAliasPath = getContentFromAliasPath(aliasPathContext,
                        (String) rootAlias.get(0).getFieldValue(OBJECT_ID));
            }

            if (lastIndexSlash != -1) {
                decreasedPath = decreasedPath.substring(0, lastIndexSlash);
                lastIndexSlash = decreasedPath.lastIndexOf('/');
            } else {

                LOGGER.info("The path " + path
                        + " does not contain a root alias. Return 'Requested Alias was not found' error message.");
                decreasedPath = "";
            }
        }

        if (contentFromAliasPath == null) {
            // Error redirect
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested Alias was not found: " + path);
        }
        return contentFromAliasPath;
    }

    private MCRContent getContentFromAliasPath(String aliasPathContext, String objectId) {

        if (!aliasPathContext.isEmpty()) {

            if (aliasPathContext.startsWith("/")) {

                aliasPathContext = parsePath(aliasPathContext);

                /*
                 * look for related items
                 * 
                 */
                LOGGER.info("Check if alias path context " + aliasPathContext
                        + " exists for document/derivate relations on " + objectId);

                String searchStr = "mods.relatedItem:" + objectId;

                try {
                    SolrDocumentList relatedDocuments = resolveSolrDocuments(searchStr);

                    for (SolrDocument relatedDocument : relatedDocuments) {

                        String currentAlias = (String) relatedDocument.getFieldValue(ALIAS);

                        if (aliasPathContext.startsWith(currentAlias)) {

                            aliasPathContext = aliasPathContext
                                    .replaceFirst((String) relatedDocument.getFieldValue(ALIAS), "");

                            return getContentFromAliasPath(aliasPathContext,
                                    (String) relatedDocument.getFieldValue(OBJECT_ID));
                        }
                    }
                } catch (SolrServerException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            LOGGER.info("The alias path context " + aliasPathContext
                    + " does not exist for document/derivate relations on " + objectId);

            return null;
        } else {

            // return mcrcontent!

            LOGGER.info("Alias exists!");

            return null;
        }
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
package org.mycore.alias;

import java.util.StringTokenizer;
import org.mycore.common.events.MCRStartupHandler.AutoExecutable;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.alias.servlets.MCRAliasContentServlet;
import org.mycore.common.config.MCRConfiguration2;

public class MCRAliasStartupHandler implements AutoExecutable {

    private static final String HANDLER_NAME = MCRAliasStartupHandler.class.getName();

    private static final Logger LOGGER = LogManager.getLogger(MCRAliasStartupHandler.class);

    public MCRAliasStartupHandler() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public String getName() {
        return HANDLER_NAME;
    }

    @Override
    public int getPriority() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void startUp(ServletContext servletContext) {
        if (servletContext != null) {

            String aliasPrefixSettings = MCRConfiguration2.getString("MCR.Alias.prefix").orElse("/go/");
            String aliasServletName = MCRAliasContentServlet.class.getName();

            ServletRegistration.Dynamic aliasServletRegistration = servletContext.addServlet("MCRAliasContentServlet",
                MCRAliasContentServlet.class.getName());

            LOGGER.info(
                "Servlet registration of " + aliasServletName + " was done");

            StringTokenizer aliasPrefixTokenizer = new StringTokenizer(aliasPrefixSettings, ",");

            while (aliasPrefixTokenizer.hasMoreTokens()) {

                String currentPrefix = aliasPrefixTokenizer.nextToken().replaceAll("\\s+","");
                currentPrefix = StringUtils.strip(currentPrefix, "/");

                aliasServletRegistration.addMapping("/" + currentPrefix + "/*");

                LOGGER.info("Url mapping: " + "/" + currentPrefix + "/*" + " was added successfully to "
                    + aliasServletRegistration.getClassName());
            }
        }
    }
    
}

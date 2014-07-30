/*
 * Copyright (C) 2007 Erik Swenson - erik@oreports.com
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */
package org.efs.openreports.dispatcher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import mondrian.olap.MondrianServer;
import mondrian.olap.Util;
import mondrian.server.RepositoryContentFinder;
import mondrian.server.UrlRepositoryContentFinder;
import mondrian.spi.CatalogLocator;
import mondrian.spi.impl.ServletContextCatalogLocator;
import mondrian.xmla.XmlaHandler;
import mondrian.xmla.impl.DefaultXmlaServlet;

import org.efs.openreports.providers.DirectoryProvider;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class XMLADispatcher extends DefaultXmlaServlet
{     
    private static final long serialVersionUID = 1L;
    
    private DirectoryProvider directoryProvider;
    public static final String DEFAULT_DATASOURCE_FILE = "datasources.xml";
    protected MondrianServer server;
    
    public void init(ServletConfig servletConfig) throws ServletException
    {       
        ApplicationContext appContext = WebApplicationContextUtils
            .getRequiredWebApplicationContext(servletConfig.getServletContext());
    
        directoryProvider = (DirectoryProvider) appContext.getBean("directoryProvider", DirectoryProvider.class);
       
        super.init(servletConfig);        
    }
    
    @Override
    protected XmlaHandler.ConnectionFactory createConnectionFactory(ServletConfig servletConfig) throws ServletException
    {
        if (server == null) {
            // A derived class can alter how the calalog locator object is
            // created.
            CatalogLocator catalogLocator = makeCatalogLocator(servletConfig);
            String dataSources = makeDataSourcesUrl(servletConfig);
            RepositoryContentFinder contentFinder = makeContentFinder(dataSources);
            server = MondrianServer.createWithRepository(contentFinder, catalogLocator);
        }
        return (XmlaHandler.ConnectionFactory) server;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }
    
    protected RepositoryContentFinder makeContentFinder(String dataSources) {
        return new UrlRepositoryContentFinder(dataSources);
    }

    protected CatalogLocator makeCatalogLocator(ServletConfig servletConfig) {
        ServletContext servletContext = servletConfig.getServletContext();
        return new ServletContextCatalogLocator(servletContext);
    }

    protected String makeDataSourcesUrl(ServletConfig servletConfig)
    {
        String paramValue =
                servletConfig.getInitParameter(PARAM_DATASOURCES_CONFIG);
        // if false, then do not throw exception if the file/url
        // can not be found
        boolean optional =
            getBooleanInitParameter(
                servletConfig, PARAM_OPTIONAL_DATASOURCE_CONFIG);

        URL dataSourcesConfigUrl = null;
        try {
            if (paramValue == null) {
                // fallback to default
                String defaultDS = directoryProvider.getReportDirectory() + DEFAULT_DATASOURCE_FILE;
                File realPath = new File(defaultDS);
                if (realPath.exists()) {
                    // only if it exists
                    dataSourcesConfigUrl = realPath.toURI().toURL();
                    return dataSourcesConfigUrl.toString();
                } else {
                    return null;
                }
            } else if (paramValue.startsWith("inline:")) {
                return paramValue;
            } else {
                paramValue = Util.replaceProperties(
                    paramValue,
                    Util.toMap(System.getProperties()));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                        "XmlaServlet.makeDataSources: paramValue="
                        + paramValue);
                }
                // is the parameter a valid URL
                MalformedURLException mue = null;
                try {
                    dataSourcesConfigUrl = new URL(paramValue);
                } catch (MalformedURLException e) {
                    // not a valid url
                    mue = e;
                }
                if (dataSourcesConfigUrl == null) {
                    // see if its a full valid file path
                    File f = new File(paramValue);
                    if (f.exists()) {
                        // yes, a real file path
                        dataSourcesConfigUrl = f.toURI().toURL();
                    } else if (mue != null) {
                        // neither url or file,
                        // is it not optional
                        if (! optional) {
                            throw mue;
                        }
                    }
                    return null;
                }
                return dataSourcesConfigUrl.toString();
            }
        } catch (MalformedURLException mue) {
            throw Util.newError(mue, "invalid URL path '" + paramValue + "'");
        }
    }
    
}

/*
 * Copyright (C) 2006 Erik Swenson - erik@oreports.com
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

package org.efs.openreports.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.efs.openreports.ReportConstants.ExportType;
import org.efs.openreports.engine.input.ReportEngineInput;
import org.efs.openreports.engine.output.ReportEngineOutput;
import org.efs.openreports.objects.ORProperty;
import org.efs.openreports.objects.Report;
import org.efs.openreports.objects.ReportDataSource;
import org.efs.openreports.providers.DataSourceProvider;
import org.efs.openreports.providers.DirectoryProvider;
import org.efs.openreports.providers.PropertiesProvider;
import org.efs.openreports.providers.ProviderException;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.DataFactory;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportProcessingException;
import org.pentaho.reporting.engine.classic.core.layout.output.AbstractReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.SQLReportDataFactory;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.base.PageableReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.FlowReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.StreamReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.csv.StreamCSVOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.AllItemsHtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.FileSystemURLRewriter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.StreamHtmlOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.rtf.FlowRTFOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.FlowExcelOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.StreamExcelOutputProcessor;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.DefaultNameGenerator;
import org.pentaho.reporting.libraries.repository.stream.StreamRepository;
import org.pentaho.reporting.libraries.resourceloader.Resource;
import org.pentaho.reporting.libraries.resourceloader.ResourceException;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;

/**
 *  PentahoReport ReportEngine implementation.
 * 
 * @author Erik Swenson
 * 
 */
public class PentahoReportEngine extends ReportEngine
{
	protected static Logger log = Logger.getLogger(PentahoReportEngine.class);
	
	private static final String QUERY_NAME = "ReportQuery";
	
	public PentahoReportEngine(DataSourceProvider dataSourceProvider,
			DirectoryProvider directoryProvider, PropertiesProvider propertiesProvider)
	{
		super(dataSourceProvider,directoryProvider, propertiesProvider);
		
		// Initialize the reporting engine
	    ClassicEngineBoot.getInstance().start();	
	    
	}	

	public ReportEngineOutput generateReport(ReportEngineInput input) throws ProviderException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();		
		
		try
		{
			
			ReportEngineOutput output = new ReportEngineOutput();
			
		    generatePentahoReport(input, out);			
			
			output.setContent(out.toByteArray());		
			
			output.setContentType(convertContentType(input.getExportType()));

			return output;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new ProviderException(e);
		}
		finally
		{
			try
			{
				if (out != null) out.close();				
			}
			catch (Exception e)
			{
				log.warn(e.toString());
			}
		}
	}	
	
	private String convertContentType (ExportType exportType) {
		if (exportType==null) {
			return null;
		}
		String res = null;
		switch (exportType){
		case CSV:
			res = ReportEngineOutput.CONTENT_TYPE_CSV;
			break;
		case EXCEL:
			res = ReportEngineOutput.CONTENT_TYPE_XLS;
			break;
		case HTML:
			 res = ReportEngineOutput.CONTENT_TYPE_HTML;
			break;
		case HTML_EMBEDDED:
			res = ReportEngineOutput.CONTENT_TYPE_HTML;
			break;
		case IMAGE:
			break;
		case PDF:
			res = ReportEngineOutput.CONTENT_TYPE_PDF;
			break;
		case RTF:
			res = ReportEngineOutput.CONTENT_TYPE_RTF;
			break;
		case TEXT:
			res = ReportEngineOutput.CONTENT_TYPE_TEXT;
			break;
		case XLS:
			res = ReportEngineOutput.CONTENT_TYPE_XLS;
			break;
		default:
			break;		
		}
		return res;
	}
	
	private MasterReport getReportDefinition(ReportEngineInput input) throws MalformedURLException, ResourceException
	{
	      Report report = input.getReport();
	      
	      final URL reportDefinitionURL = new File (directoryProvider.getReportDirectory() + report.getFile()).toURI().toURL();
	      // Parse the report file
	      final ResourceManager resourceManager = new ResourceManager();
	      resourceManager.registerDefaults();
	      final Resource directly = resourceManager.createDirectly(reportDefinitionURL, MasterReport.class);
	      return (MasterReport) directly.getResource();  
    }

	private DataFactory getDataFactory(ReportEngineInput input) throws ReportProcessingException, ProviderException
	{
		    Report report = input.getReport();
			
			ReportDataSource dataSource = report.getDataSource();
			if (dataSource!=null){
			    Connection conn = dataSourceProvider.getConnection(dataSource.getId());
		        final SQLReportDataFactory dataFactory = new SQLReportDataFactory(conn);
		        dataFactory.setQuery(QUERY_NAME, report.getQuery()); 
		        return dataFactory;
			}
			return null;		   
	}

    private Map<String, Object> getReportParameters(ReportEngineInput input)
	{
	    return input.getParameters();
	}
	
	private void generatePentahoReport(ReportEngineInput input, OutputStream outputStream)
	      throws IllegalArgumentException, ReportProcessingException, ProviderException, MalformedURLException, ResourceException
	{
	    if (outputStream == null)
	    {
	      throw new IllegalArgumentException("The output stream was not specified");
	    }
	    
	    if (input.getExportType()==null) {
	    	throw new ReportProcessingException("Export type is missing");
	    }

	    // Get the report and data factory
	    final MasterReport report = getReportDefinition(input);
	    final DataFactory dataFactory = getDataFactory(input);

	    // Set the data factory for the report
	    if (dataFactory != null)
	    {
	      report.setDataFactory(dataFactory);
	      report.setQuery(QUERY_NAME);
	    }
	    
	    ORProperty maxRows = propertiesProvider.getProperty(ORProperty.QUERYREPORT_MAXROWS);
		if (maxRows != null && maxRows.getValue() != null)
		{	
			report.setQueryLimit(Integer.parseInt(maxRows.getValue()));
		}

	    // Add any parameters to the report
	    final Map<String, Object> reportParameters = getReportParameters(input);
	    
	    if (null != reportParameters && ! reportParameters.isEmpty())
	    {
	      for (String key : reportParameters.keySet())
	      {
	        report.getParameterValues().put(key, reportParameters.get(key));
	      }
	    }
	    
	    // Prepare to generate the report
	    AbstractReportProcessor reportProcessor = null;
	    try
	    {
	    	
	      // Create the report processor for the specified output type
	      switch (input.getExportType())
	      {
	        case PDF:
	        {
	          final PdfOutputProcessor outputProcessor =
	              new PdfOutputProcessor(report.getConfiguration(), outputStream, report.getResourceManager());
	          reportProcessor = new PageableReportProcessor(report, outputProcessor);
	          break;
	        }
	        
	        case EXCEL:
	        {
	        	  final StreamExcelOutputProcessor target = new StreamExcelOutputProcessor(report.getConfiguration(), outputStream, report.getResourceManager());
		          reportProcessor = new StreamReportProcessor(report, target);
		          break;
	        }
	        
	        case XLS:
	        {
	          final FlowExcelOutputProcessor target =
	              new FlowExcelOutputProcessor(report.getConfiguration(), outputStream, report.getResourceManager());
	          reportProcessor = new FlowReportProcessor(report, target);
	          break;
	        }

	        case HTML:
	        {
	          final StreamRepository targetRepository = new StreamRepository(outputStream);
	          final ContentLocation targetRoot = targetRepository.getRoot();
	          final HtmlOutputProcessor outputProcessor = new StreamHtmlOutputProcessor(report.getConfiguration());
	          final HtmlPrinter printer = new AllItemsHtmlPrinter(report.getResourceManager());
	          printer.setContentWriter(targetRoot, new DefaultNameGenerator(targetRoot, "index", "html"));
	          printer.setDataWriter(null, null);
	          printer.setUrlRewriter(new FileSystemURLRewriter());
	          outputProcessor.setPrinter(printer);
	          reportProcessor = new StreamReportProcessor(report, outputProcessor);
	          break;
	        }
		    case CSV:
		    {
		          final StreamCSVOutputProcessor target = new StreamCSVOutputProcessor(outputStream);
		          reportProcessor = new StreamReportProcessor(report, target);
		          break;
		    }
		    case HTML_EMBEDDED:
		    	HtmlReportUtil.createStreamHTML(report, outputStream);
			break;
		    case IMAGE:
			break;
		    case RTF:
		    {
		          final FlowRTFOutputProcessor target =
		              new FlowRTFOutputProcessor(report.getConfiguration(), outputStream, report.getResourceManager());
		          reportProcessor = new FlowReportProcessor(report, target);
		          break;
		    }
		    case TEXT:
		    break;
		    
		    default:
			break;
	      }

	      // Generate the report
	      if (reportProcessor != null) {
	          reportProcessor.processReport();
	      }
	    }
	    finally
	    {
	      if (reportProcessor != null)
	      {
	        reportProcessor.close();
	      }
	    }
	}
	
	public List buildParameterList(Report report) throws ProviderException
	{
		throw new ProviderException("PentahoReportEngine: buildParameterList not implemented.");
	}		
}

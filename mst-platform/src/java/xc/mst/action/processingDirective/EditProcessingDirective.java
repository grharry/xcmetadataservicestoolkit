/**
 * Copyright (c) 2009 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */

package xc.mst.action.processingDirective;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.apache.struts2.interceptor.ServletRequestAware;

import xc.mst.action.BaseActionSupport;
import xc.mst.bo.processing.ProcessingDirective;
import xc.mst.bo.provider.Provider;
import xc.mst.bo.service.Service;
import xc.mst.constants.Constants;
import xc.mst.dao.DatabaseConfigException;

/**
 * The first step in editing a processing directive
 * 
 * @author Tejaswi Haramurali
 */
public class EditProcessingDirective extends BaseActionSupport implements ServletRequestAware {
    /** Serial ID */
    private static final long serialVersionUID = -4075467154916214233L;

    /** Temporary Processing Directive object which is used to display details on the JSP */
    private ProcessingDirective temporaryProcessingDirective;

    /** ID of the processing directive whose details are to be edited */
    private int processingDirectiveId;

    /** The source that is associated with a processing directive */
    private String source;

    /** Request */
    private HttpServletRequest request;

    /** A reference to the logger for this class */
    static Logger log = Logger.getLogger(Constants.LOGGER_GENERAL);

    /** List of all providers */
    private List<Provider> providerList;

    /** List of all services */
    private List<Service> serviceList;

    /** specifies the type of source i.e whether it is a provider or a service */
    private String sourceType;

    /** The service associated with the processing directive */
    private String service;

    /** Error type */
    private String errorType;

    /**
     * Sets the ID of the processing directive to be edited
     * 
     * @param processingDirectiveId
     *            ID of the processing directive
     */
    public void setProcessingDirectiveId(String processingDirectiveId) {

        this.processingDirectiveId = Integer.parseInt(processingDirectiveId);
    }

    /**
     * Returns the ID of the processing directive to be edited
     * 
     * @return ID of the processing directive
     */
    public int getProcessingDirectiveId() {

        return processingDirectiveId;
    }

    /**
     * Sets the source type
     * 
     * @param sourceType
     *            source type
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * Returns the type of the source
     * 
     * @return source type
     */
    public String getSourceType() {
        return this.sourceType;
    }

    /**
     * Sets the source for a processing directive
     * 
     * @param Source
     *            source
     */
    public void setSource(String Source) {
        this.source = Source;
    }

    /**
     * Returns the source for a processing directive
     * 
     * @return source
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets the service that is associated with the processing directive
     * 
     * @param service
     *            service object
     */
    public void setService(String service) {
        this.service = service;
    }

    /**
     * Returns the service that is associated with the processing directive
     * 
     * @return service object
     */
    public String getService() {
        return this.service;
    }

    /**
     * Sets temporary processing directive
     * 
     * @param temporaryProcessingDirective
     */
    public void setTemporaryProcessingDirective(ProcessingDirective temporaryProcessingDirective) {
        this.temporaryProcessingDirective = temporaryProcessingDirective;
    }

    /**
     * Returns processing directive
     * 
     * @return processing directive object
     */
    public ProcessingDirective getTemporaryProcessingDirective() {
        return this.temporaryProcessingDirective;
    }

    /**
     * Sets the list of all providers
     * 
     * @param providerList
     */
    public void setProviderList(List<Provider> providerList) {
        this.providerList = providerList;
    }

    /**
     * Returns the list of all providers
     * 
     * @return provider list
     */
    public List<Provider> getProviderList() {
        return this.providerList;
    }

    /**
     * Sets the list of services
     * 
     * @param serviceList
     */
    public void setServiceList(List<Service> serviceList) {
        this.serviceList = serviceList;
    }

    /**
     * Returns the list of services
     * 
     * @return list of services
     */
    public List<Service> getServiceList() {
        return this.serviceList;
    }

    /**
     * Set the servlet request.
     * 
     * @see org.apache.struts2.interceptor.ServletRequestAware#setServletRequest(javax.servlet.http.HttpServletRequest)
     */
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Overrides default implementation to view the edit processing directives (step 1) page.
     * 
     * @return {@link #SUCCESS}
     */
    @Override
    public String execute() {
        try {
            temporaryProcessingDirective = getProcessingDirectiveService().getByProcessingDirectiveId(processingDirectiveId);
            LOG.debug("****** EditProcessingDirective:execute " + " tempProcDir=" + temporaryProcessingDirective);

            request.getSession().setAttribute("processingDirectiveId", processingDirectiveId);

            if (temporaryProcessingDirective == null) {
                temporaryProcessingDirective = (ProcessingDirective) request.getSession().getAttribute("temporaryProcessingDirective");
            }

            // Place PD in session
            request.getSession().setAttribute("temporaryProcessingDirective", temporaryProcessingDirective);
            if (temporaryProcessingDirective == null) {
                this.addFieldError("editProcessingDirectiveEror", "Error occurred while displaying edit processing directives page. An email has been sent to the administrator. ");
                getUserService().sendEmailErrorReport();
                errorType = "error";
                return INPUT;
            }
            Service tempService = temporaryProcessingDirective.getSourceService();

            if (tempService != null) // source is a service
            {
                setSourceType("service");
            } else // source is a provider
            {
                setSourceType("provider");
            }

            setProviderList(getProviderService().getAllProviders());
            setServiceList(getServicesService().getAllServices());
            setTemporaryProcessingDirective(temporaryProcessingDirective);

            return SUCCESS;
        } catch (DatabaseConfigException dce) {
            log.error(dce.getMessage(), dce);
            this.addFieldError("editProcessingDirectiveEror", "Unbale to connect to the database. Database Configuration may be incorrect.");
            errorType = "error";
            return INPUT;
        }
    }

    /**
     * Step 1 in editing a Processing directive
     * 
     * @return {@link #SUCCESS}
     */
    public String editProcessingDirectives() {
        try {
            temporaryProcessingDirective = getProcessingDirectiveService().getByProcessingDirectiveId(processingDirectiveId);
            LOG.debug("****** EditProcessingDirective:editProcessingDirectives() " + " tempProcDir=" + temporaryProcessingDirective + " id=" + processingDirectiveId);

            if (temporaryProcessingDirective == null) {
                temporaryProcessingDirective = (ProcessingDirective) request.getSession().getAttribute("temporaryProcessingDirective");
            }

            if (temporaryProcessingDirective == null) {
                this.addFieldError("editProcessingDirectiveEror", "Error occurred while editing processing directive. An email has been sent to the administrator. ");
                getUserService().sendEmailErrorReport();
                errorType = "error";
                return INPUT;
            }
            Provider tempProvider = getProviderService().getProviderByName(source);
            Service tempService = getServicesService().getServiceByName(source);

            if (tempProvider != null) // source is a provider
            {
                request.getSession().setAttribute("sourceType", "provider");
            } else if (tempService != null) // source is a service
            {
                request.getSession().setAttribute("sourceType", "service");
            }
            request.getSession().setAttribute("service", getService());

            LOG.debug("****** END EditProcessingDirective:editProcessingDirectives() " + " tempProcDir=" + temporaryProcessingDirective);

            request.getSession().setAttribute("temporaryProcessingDirective", temporaryProcessingDirective);
            return SUCCESS;
        } catch (DatabaseConfigException dce) {
            log.error(dce.getMessage(), dce);
            this.addFieldError("editProcessingDirectiveError", "Unable to connect to the database. Database Configuration may be incorrect.");
            errorType = "error";
            return INPUT;
        }
    }

    /**
     * Returns error type
     * 
     * @return error type
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * Sets error type
     * 
     * @param errorType
     *            error type
     */
    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
}

/**
 * Copyright (c) 2009 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */

package xc.mst.bo.record;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;
import org.jdom.Element;

import xc.mst.bo.harvest.Harvest;
import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Provider;
import xc.mst.bo.provider.Set;
import xc.mst.bo.service.Service;
import xc.mst.manager.record.RecordService;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;
import xc.mst.utils.XmlHelper;

/**
 * Represents a record
 *
 * @author Eric Osisek
 */
public class Record implements InputRecord, OutputRecord, Comparable<Record> {

    private static final Logger LOG = Logger.getLogger(Record.class);

    public static final String STRING_MODE = "Record.STRING_MODE";
    public static final String JDOM_MODE = "Record.JDOM_MODE";
    public static final String MODE_NOT_SET = "Record.MODE_NOT_SET";

    public static final String UNCHANGED = "Record.UNCHANGED";

    public static final char ACTIVE = 'A';
    public static final char HELD = 'H';
    public static final char DELETED = 'D';
    public static final char REPLACED = 'R';
    public static final char NULL = 'N';

    public static Map<Character, String> statusNames = new HashMap<Character, String>();

    static {
        statusNames.put(ACTIVE, "active");
        statusNames.put(HELD, "held");
        statusNames.put(DELETED, "deleted");
        statusNames.put(NULL, "null");
        statusNames.put(REPLACED, "replaced");
    }

    protected String mode = JDOM_MODE;

    protected XmlHelper xmlHelper = new XmlHelper();

    protected Element oaiXmlEl = null;

    /**
     * The type of indexed Object this is
     */
    public String type = null;

    /**
     * The record's ID
     */
    protected long id = -1;

    protected List<InputRecord> predecessors = new ArrayList<InputRecord>();

    /**
     * The time when the record was created
     */
    protected Date createdAt = null;

    /**
     * The time when the record was last updated
     */
    protected Date updatedAt = null;

    /**
     * The record's format
     */
    protected Format format = null;

    /**
     * The provider from which the record was harvested, or null if the record did not come from a provider
     */
    protected Provider provider = null;

    /**
     * The harvest in which the record was harvested, or null if the record did not come from a harvest
     */
    protected Harvest harvest = null;

    /**
     * The service which generated the record, or null if the record was not generated by a service
     */
    protected Service service = null;

    /**
     * The record's OAI identifier
     */
    protected String oaiIdentifier = null;
    protected String oaiIdentifierMostSpecificToken = null;

    /**
     * The record's OAI datestamp
     */
    protected Date oaiDatestamp = null;

    /**
     * The record's OAI header
     */
    protected String oaiHeader = null;

    /**
     * The record's xml
     */
    protected String oaiXml = null;

    /**
     * The harvest schedule name
     */
    protected String harvestScheduleName = null;

    /**
     * This is not used by the Record class, but classes extending it which represent the individual FRBR levels can use this to indicate the other FRBR elements to which they are linked.
     * BDA - 2010-09-10 - unless I'm missing something, these aren't actually used anywhere.
     */
    protected List<String> upLinks = new ArrayList<String>();

    /**
     * The record's traits. These are used by the aggregation service for matching
     * jfb 1/27/12 not used by the aggregation service I am building.
     */
    protected List<String> traits = new ArrayList<String>();

    /**
     * A list of the sets to which the record belongs
     */
    protected List<Set> sets = new ArrayList<Set>();

    /**
     * A list of the records form which this record was processed
     */
    protected List<Record> processedFrom = new ArrayList<Record>();

    /**
     * A list of the records which used this record to process
     */
    protected List<OutputRecord> successors = new ArrayList<OutputRecord>();

    /**
     * A list of the services for which this record is input
     */
    protected List<Service> inputForServices = new ArrayList<Service>();

    /**
     * A list of the services which have processed this record
     */
    protected List<Service> processedByServices = new ArrayList<Service>();

    /**
     * A list of messages generated by this record
     */
    protected List<RecordMessage> messages = new ArrayList<RecordMessage>();

    /**
     * Number of predecessor
     */
    protected int numberOfPredecessors;

    /**
     * Number of Successor
     */
    protected int numberOfSuccessors;

    protected char status = Record.ACTIVE;
    protected char prevStatus = Record.NULL;

    public Record() {
        super();
    }

    public Record clone() {
        Record dupe = new Record();
        dupe.createdAt = this.createdAt;
        dupe.status = this.status;
        dupe.prevStatus = this.prevStatus;
        dupe.id = this.id;
        dupe.oaiHeader = this.oaiHeader;
        dupe.oaiIdentifier = this.oaiIdentifier;
        dupe.oaiXml = this.oaiXml;
        dupe.oaiXmlEl = this.oaiXmlEl;
        dupe.predecessors = this.predecessors;
        dupe.messages = this.messages;
        dupe.type = this.type;
        return dupe;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        if (!mode.equals(this.mode)) {
            if (mode.equals(STRING_MODE)) {
                if (this.oaiXmlEl != null) {
                    this.oaiXml = xmlHelper.getString(this.oaiXmlEl);
                } else {
                    this.oaiXml = null;
                }
            } else if (mode.equals(JDOM_MODE)) {
                if (this.oaiXml != null) {
                    try {
                        TimingLogger.start("getJDomDocument()");
                        org.jdom.Document d = xmlHelper.getJDomDocument(this.oaiXml);
                        TimingLogger.stop("getJDomDocument()");
                        this.oaiXmlEl = d.detachRootElement();
                    } catch (Throwable t) {
                        LOG.error("this.oaiXml.getBytes()");
                        LOG.error("", t);
                        System.out.println("error!!!");
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try {
                            baos.write(this.oaiXml.getBytes("UTF-8"));
                            baos.writeTo(System.out);
                        } catch (Throwable t2) {
                            LOG.error("", t2);
                        }
                        System.out.println("error!!!");
                        this.oaiXmlEl = null;
                    }
                } else {
                    this.oaiXmlEl = null;
                }
            } else {
                throw new RuntimeException("invalid mode!!!");
            }
            this.mode = mode;
        }
    }

    public Element getOaiXmlEl() {
        if (!this.mode.equals(MODE_NOT_SET) && !this.mode.equals(JDOM_MODE)) {
            throw new RuntimeException(
                    "This record is not set to JDOM_MODE.  You must explicitly " +
                            "call Record.setMode(Recrod.JDOM_MODE) before calling this method.");
        }
        if (this.oaiXmlEl != null) {
            this.oaiXmlEl.detach();
        }
        this.mode = JDOM_MODE;
        return this.oaiXmlEl;
    }

    public void setOaiXmlEl(Element oaiXmlEl) {
        if (!this.mode.equals(MODE_NOT_SET) && !this.mode.equals(JDOM_MODE)) {
            throw new RuntimeException(
                    "This record is not set to JDOM_MODE.  You must explicitly " +
                            "call Record.setMode(Recrod.JDOM_MODE) before calling this method.");
        }
        this.mode = JDOM_MODE;
        this.oaiXmlEl = oaiXmlEl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the record's ID
     *
     * @return The record's ID
     */
    public long getId() {
        return id;
    } // end method getId()

    /**
     * Sets the record's ID
     *
     * @param id
     *            The record's new ID
     */
    public void setId(long id) {
        this.id = id;
    } // end method setRecordId(long)

    /**
     * Gets the time when the record was created
     *
     * @return The time when the record was created
     */
    public Date getCreatedAt() {
        return createdAt;
    } // end method getCreatedAt()

    /**
     * Sets the time when the record was created
     *
     * @param createdAt
     *            The new time when the record was created
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    } // end method setCreatedAt(Date)

    /**
     * Gets the time when the record was last updated
     *
     * @return The new time when the record was last updated
     */
    public Date getUpdatedAt() {
        return updatedAt;
    } // end method getUpdatedAt()

    /**
     * Sets the time when the record was last updated
     *
     * @param updatedAt
     *            The new time when the record was last updated
     */
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    } // end method setUpdatedAt(Date)

    public boolean getDeleted() {
        return this.status == Record.DELETED;
    }

    public void setDeleted(boolean deleted) {
        if (deleted) {
            this.status = Record.DELETED;
        }
    }

    /**
     * Gets the record's format
     *
     * @return The record's format
     */
    public Format getFormat() {
        return format;
    } // end method getFormat()

    /**
     * Sets the record's format
     *
     * @param format
     *            The record's new format
     */
    public void setFormat(Format format) {
        this.format = format;
    } // end method setFormat(Format)

    /**
     * Gets the provider from which the record was harvested
     *
     * @return The provider from which the record was harvested, or null if the record wasn't harvested
     */
    public Provider getProvider() {
        return provider;
    } // end method getProvider()

    /**
     * Sets the provider from which the record was harvested
     *
     * @param provider
     *            The new provider from which the record was harvested, or null if the record wasn't harvested
     */
    public void setProvider(Provider provider) {
        this.provider = provider;
    } // end method setProvider(Provider)

    /**
     * Gets the service which created the record
     *
     * @return The service that create the record, or null if the record was not created by a service
     */
    public Service getService() {
        return service;
    } // end method getService()

    /**
     * Sets the service that create the record
     *
     * @param service
     *            The new service that create the record, or null if the record was not created by a service
     */
    public void setService(Service service) {
        this.service = service;
    } // end method setService(Service)

    /**
     * Gets the harvest which the record was harvested by
     *
     * @return The harvest which the record was harvested by, or null if the record did not come from a harvest
     */
    public Harvest getHarvest() {
        return harvest;
    } // end method getHarvest()

    /**
     * Sets the harvest which the record was harvested by
     *
     * @param harvest
     *            The new harvest which the record was harvested by, or null if the record did not come from a harvest
     */
    public void setHarvest(Harvest harvest) {
        this.harvest = harvest;
    } // end method setHarvest(Harvest)

    public String getOaiIdentifier() {
        return getOaiIdentifier(getId());
    }

    protected String getOaiIdentifier(long id) {
        return ((RecordService) MSTConfiguration.getInstance().getBean("RecordService")).getOaiIdentifier(
                id, getProvider(), getService());
    }

    public void setHarvestedOaiIdentifier(String oaiIdentifier) {
        this.oaiIdentifier = oaiIdentifier;
    }

    public String getHarvestedOaiIdentifier() {
        return this.oaiIdentifier;
    }

    /**
     * Gets the record's OAI datestamp
     *
     * @return The record's OAI datestamp
     */
    public Date getOaiDatestamp() {
        return oaiDatestamp;
    }

    /**
     * Sets the record's OAI datestamp
     *
     * @param oaiDatestamp
     *            The record's new OAI datestamp
     */
    public void setOaiDatestamp(Date oaiDatestamp) {
        this.oaiDatestamp = oaiDatestamp;
    }

    /**
     * Gets the the record's OAI header
     *
     * @return The record's OAI header
     */
    public String getOaiHeader() {
        return oaiHeader;
    } // end method getOaiHeader()

    /**
     * Sets the record's OAI header
     *
     * @param oaiHeader
     *            The record's new OAI header
     */
    public void setOaiHeader(String oaiHeader) {
        this.oaiHeader = oaiHeader;
    } // end method setOaiHeader(String)

    /**
     * Gets the record's OAI XML
     *
     * @return The record's OAI XML
     */
    public String getOaiXml() {
        if (!this.mode.equals(MODE_NOT_SET) && !this.mode.equals(STRING_MODE)) {
            throw new RuntimeException(
                    "This record is not set to STRING_MODE.  You must explicitly " +
                            "call Record.setMode(STRING_MODE) before calling this method.");
        }
        this.mode = STRING_MODE;
        return oaiXml;
    }

    /**
     * Sets the record's OAI XML
     *
     * @param oaiXml
     *            The record's new OAI XML
     */
    public void setOaiXml(String oaiXml) {
        if (!this.mode.equals(MODE_NOT_SET) && !this.mode.equals(STRING_MODE)) {
            throw new RuntimeException(
                    "This record is not set to STRING_MODE.  You must explicitly " +
                            "call Record.setMode(STRING_MODE) before calling this method.");
        }
        this.mode = STRING_MODE;
        this.oaiXml = oaiXml;
    }

    /**
     * Gets the sets the record belongs to
     *
     * @return The record's sets
     */
    public List<Set> getSets() {
        return sets;
    } // end method getSets()

    /**
     * Sets the sets the record belongs to
     *
     * @param sets
     *            A list of sets for the record
     */
    public void setSets(List<Set> sets) {
        this.sets = sets;
    } // end method setSets(List<Set>)

    /**
     * Adds a set to the list of sets the record belongs to
     *
     * @param set
     *            The set to add
     */
    public void addSet(Set set) {
        if (!sets.contains(set))
            sets.add(set);
    } // end method addSet(Set)

    /**
     * Removes a set from the list of sets the record belongs to
     *
     * @param set
     *            The set to remove
     */
    public void removeSet(Set set) {
        if (sets.contains(set))
            sets.remove(set);
    } // end method removeSet(Set)

    /**
     * Gets the records which this record was processed from
     *
     * @return The records which this record was processed from
     */
    public List<Record> getProcessedFrom() {
        return processedFrom;
    } // end method getProcessedFrom()

    /**
     * Sets the records which this record was processed from
     *
     * @param processedFrom
     *            A list of records which this record was processed from
     */
    public void setProcessedFrom(List<Record> processedFrom) {
        this.processedFrom = processedFrom;
    } // end method setProcessedFrom(List<Record>)

    /**
     * Adds a record to the records which this record was processed from
     *
     * @param record
     *            The record to add to the list of records which this record was processed from
     */
    public void addProcessedFrom(Record record) {
        if (!processedFrom.contains(record))
            processedFrom.add(record);
    } // end method addProcessedFrom(Record)

    /**
     * Removes a set from the list of records which this record was processed from
     *
     * @param record
     *            The set to remove from the list of records which this record was processed from
     */
    public void removeProcessedFrom(Record record) {
        if (processedFrom.contains(record))
            processedFrom.remove(record);
    } // end method removeProcessedFrom(Record)

    /**
     * Gets the records which were processed from this record
     *
     * @return A list of records which were processed from this record
     */
    public List<OutputRecord> getSuccessors() {
        return successors;
    } // end method getSuccesors()

    /**
     * Sets the records which were processed from this record
     *
     * @param successors
     *            The new list of records which were processed from this record
     */
    public void setSuccessors(List<OutputRecord> successors) {
        this.successors = successors;
    } // end method setSuccessors(List<Record>)

    /**
     * Adds a record to the list of records which were processed from this record
     *
     * @param successor
     *            The new record which was processed from this record
     */
    public void addSuccessor(Record successor) {
        this.successors.add(successor);
    } // end method addSuccessor(Record successor)

    /**
     * Gets the services which the record is input for
     *
     * @return The services which the record is input for
     */
    public List<Service> getInputForServices() {
        return inputForServices;
    } // end method getInputForServices()

    /**
     * Sets the services which the record is input for
     *
     * @param inputForServices
     *            A list of services which the record is input for
     */
    public void setInputForServices(List<Service> inputForServices) {
        this.inputForServices = inputForServices;
    } // end method setInputForServices(List<Service>)

    /**
     * Adds a service to the services which the record is input for
     *
     * @param service
     *            The service to add to the list of services which the record is input for
     */
    public void addInputForService(Service service) {
        if (!inputForServices.contains(service))
            inputForServices.add(service);
    } // end method addInputForServices(Service)

    /**
     * Removes a set from the list of services which the record is input for
     *
     * @param service
     *            The set to remove from the list of services which the record is input for
     */
    public void removeInputForService(Service service) {
        if (inputForServices.contains(service))
            inputForServices.remove(service);
    } // end method removeInputForServices(Service)

    /**
     * Gets the services which the record is output for
     *
     * @return The services which the record is output for
     */
    public List<Service> getProcessedByServices() {
        return processedByServices;
    } // end method getProcessedByServices()

    /**
     * Sets the services which the record is output for
     *
     * @param processedByServices
     *            A list of services which the record is output for
     */
    public void setProcessedByServices(List<Service> processedByServices) {
        this.processedByServices = processedByServices;
    } // end method setProcessedByServices(List<Service>)

    /**
     * Adds a service to the services which the record is output for
     *
     * @param service
     *            The service to add to the list of services which the record is output for
     */
    public void addProcessedByService(Service service) {
        if (!processedByServices.contains(service))
            processedByServices.add(service);
    } // end method addProcessedByServices(Service)

    /**
     * Removes a set from the list of services which the record is output for
     *
     * @param service
     *            The set to remove from the list of services which the record is output for
     */
    public void removeProcessedByService(Service service) {
        if (processedByServices.contains(service))
            processedByServices.remove(service);
    } // end method removeProcessedByServices(Service)

    /**
     * Gets the record's traits
     *
     * @return The record's traits
     */
    public List<String> getTraits() {
        return traits;
    } // end method getTraits()

    /**
     * Sets the record's traits
     *
     * @param traits
     *            The record's new traits
     */
    public void setTraits(List<String> traits) {
        this.traits = traits;
    } // end method setTraits(List<String>)

    /**
     * Adds a trait to the record
     *
     * @param trait
     *            The trait to add to the record
     */
    public void addTrait(String trait) {
        if (!traits.contains(trait))
            traits.add(trait);
    } // end method addTraits(String)

    /**
     * Removes a trait from the record
     *
     * @param trait
     *            The trait to remove from the record
     */
    public void removeTrait(String trait) {
        if (traits.contains(trait))
            traits.remove(trait);
    } // end method removeTrait(String)

    /**
     * Gets the record's errors
     *
     * @return The record's errors
     */
    public List<RecordMessage> getMessages() {
        return messages;
    } // end method getMessages()

    /**
     * Sets the record's errors
     *
     * @param errors
     *            The record's new errors
     */
    public void setMessages(List<RecordMessage> messages) {
        LOG.debug("Record.setMessages() called");
        this.messages = messages;
    } // end method setMessages(List<RecordMessage>)

    /**
     * Adds a error to the record
     *
     * @param error
     *            The error to add to the record
     */
    public void addMessage(RecordMessage message) {
        // LOG.debug("Record.addMessage() called on record.id:"+getId());
        if (!messages.contains(message)) {
            messages.add(message);
        }
        else {
            LOG.debug("Record.addMessage() called, but already have that message!!!, size of messages ="+messages.size());
        }
    } // end method addMessage(RecordMessage)

    /**
     * Removes a error from the record
     *
     * @param error
     *            The error to remove from the record
     */
    public void removeMessage(RecordMessage message) {
        LOG.debug("Record.removeMessage() called");
        if (messages.contains(message))
            messages.remove(message);
    } // end method removeError(RecordMessage)

    /**
     * Gets the record's up links
     *
     * @return The record's uplinks
     */
    public List<String> getUpLinks() {
        return upLinks;
    } // end method getUpLinks()

    /**
     * Sets the record's up links
     *
     * @param traits
     *            The record's new up links
     */
    public void setUpLinks(List<String> upLinks) {
        this.upLinks = upLinks;
    } // end method setUpLinks(List<Record>)

    /**
     * Adds an up link to the record
     *
     * @param upLink
     *            The record to add as an uplink
     */
    public void addUpLink(String upLink) {
        if (!upLinks.contains(upLink))
            upLinks.add(upLink);
    } // end method addUpLink(Record)

    /**
     * Removes an up link from the record
     *
     * @param upLink
     *            The record to remove as an up link
     */
    public void removeUpLink(String upLink) {
        if (upLinks.contains(upLink))
            upLinks.remove(upLink);
    } // end method removeUpLink(Record)

    /**
     * Creates a Record from an existing record with all fields the same except for the
     * record ID
     *
     * @param otherRecord
     *            The record we're copying fields from
     * @return A record identical to otherRecord except that the record ID is -1.
     */
    public static Record copyRecord(Record otherRecord) {
        // The record we'll be returning
        Record record = new Record();

        // Set all the fields on the new record to match the fields on the other record
        // except for the record ID
        // record.setCreatedAt(otherRecord.getCreatedAt());
        // record.setDeleted(otherRecord.getDeleted());
        record.setFormat(otherRecord.getFormat());
        // record.setOaiDatestamp(otherRecord.getOaiDatestamp());
        // record.setOaiHeader(otherRecord.getOaiHeader());
        // record.setOaiIdentifier(otherRecord.getOaiIdentifier());
        if (Record.JDOM_MODE.equals(otherRecord.getMode())) {
            record.setOaiXmlEl(otherRecord.getOaiXmlEl());
        } else {
            record.setOaiXml(otherRecord.getOaiXml());
        }

        // record.setProvider(otherRecord.getProvider());
        // record.setService(otherRecord.getService());
        // record.setUpdatedAt(otherRecord.getUpdatedAt());

        // Return the copied record
        return record;
    } // end method copyRecord(Record)

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Id=" + id);
        buffer.append(" createdAt=" + createdAt);
        buffer.append(" deleted=" + getDeleted());
        if (format != null) {
            buffer.append(" format=" + format.getName());
        }
        if (provider != null) {
            buffer.append(" provider=" + provider.getName());
        }
        if (service != null) {
            buffer.append(" service=" + service.getName());
        }
        buffer.append(" updatedAt=" + updatedAt);
        buffer.append(" sets=" + sets);
        if (harvest != null) {
            buffer.append(" ScheduleId=" + harvest.getHarvestSchedule().getId());
        }
        buffer.append(" oaiDatestamp=" + oaiDatestamp);
        buffer.append(" oaiHeader=" + oaiHeader);
        buffer.append(" oaiIdentifier=" + oaiIdentifier);
        buffer.append(" messages=" + messages);
        buffer.append(" oaiXml=" + oaiXml);

        return buffer.toString();
    }

    /**
     * Returns the number of predecessors of the record
     *
     * @return The number of predecessors the record has
     */
    public int getNumberOfPredecessors() {
        return predecessors.size();
    }

    /**
     * Returns the number of successors of the record
     *
     * @return The number of successors the record has
     */
    public int getNumberOfSuccessors() {
        return this.successors.size();
    }

    /**
     * Get harvest schedule name
     *
     * @return name of schedule
     */
    public String getHarvestScheduleName() {
        return harvestScheduleName;
    }

    /**
     * Set harvest schedule name
     *
     * @param harvestScheduleName
     *            name of schedule
     */
    public void setHarvestScheduleName(String harvestScheduleName) {
        this.harvestScheduleName = harvestScheduleName;
    }

    public void setNumberOfPredecessors(int numberOfPredecessors) {
        this.numberOfPredecessors = numberOfPredecessors;
    }

    public void setNumberOfSuccessors(int numberOfSuccessors) {
        this.numberOfSuccessors = numberOfSuccessors;
    }

    public void removeSucessor(Record successor) {
        this.successors.remove(successor);
    }

    public void removePredecessor(Record r) {
        predecessors.remove(r);
    }

    public void addPredecessor(Record r) {
        if (!predecessors.contains(r)) {
            predecessors.add(r);
        }
    }

    public List<InputRecord> getPredecessors() {
        return predecessors;
    }

    public char getStatus() {
        return status;
    }

    public void setStatus(char status) {
        this.status = status;
    }

    public char getPreviousStatus() {
        return prevStatus;
    }

    public void setPreviousStatus(char prevStatus) {
        this.prevStatus = prevStatus;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else {
            Record r = (Record) o;
            if (r.getId() == -1 && getId() == -1) {
                return super.equals(o);
            } else if (r.getId() == -1) {
                return false;
            } else {
                return getId() == r.getId();
            }
        }
    }

    public int hashCode() {
        if (getId() == -1) {
            return super.hashCode();
        } else {
            return new Long(getId()).hashCode();
        }
    }

    @Override
    public int compareTo(Record o) {
        if (o == null) {
            return 1;
        } else {
            if (o.getId() == -1 && getId() == -1) {
                return new Long(hashCode()).compareTo(new Long(o.hashCode()));
            } else if (o.getId() == -1) {
                return 1;
            } else {
                return new Long(getId()).compareTo(new Long(o.getId()));
            }
        }
    }
}

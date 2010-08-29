/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.services.normalization;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.jdom.Element;
import org.jdom.Namespace;

import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Set;
import xc.mst.bo.record.InputRecord;
import xc.mst.bo.record.OutputRecord;
import xc.mst.bo.record.Record;
import xc.mst.bo.record.RecordMessage;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.services.ServiceValidationException;
import xc.mst.services.impl.GenericMetadataService;
import xc.mst.utils.TimingLogger;
import xc.mst.utils.XmlHelper;

/**
 * A Metadata Service which for each unprocessed marcxml record creates a new
 * record which is a copy of the original record after cleaning up common problems.
 *
 * @author Eric Osisek
 */
public class NormalizationService extends GenericMetadataService {

	protected Namespace marcNamespace = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

	/**
	 * The Properties file with information on which Normalization steps to run
	 */
	protected Properties enabledSteps = null;

	/**
	 * The Properties file with the location name mappings
	 */
	protected Properties locationNameProperties = null;
    
	/**
	 * The Properties file with the location limit name mappings
	 */
	protected Properties locationLimitNameProperties = null;

    /**
     * The Properties file with the DCMI type information for the leader 06
     */
	protected Properties dcmiType06Properties = null;

    /**
     * The Properties file with the MARC Vocabulary information for the leader 06
     */
	protected Properties leader06MarcVocabProperties = null;

    /**
     * The Properties file with the vocab information for the leader 06
     */
	protected Properties vocab06Properties = null;

    /**
     * The Properties file with the mode of issuance information in it
     */
	protected Properties modeOfIssuanceProperties = null;

    /**
     * The Properties file with the DCMI type information for the 00 offset 07
     */
	protected Properties dcmiType0007Properties = null;

    /**
     * The Properties file with the vocab information for the 007 offset 00
     */
	protected Properties vocab007Properties = null;

    /**
     * The Properties file with the smd type information for the 007 offset 00
     */
	protected Properties smdType007Properties = null;

    /**
     * The Properties file with the language term information
     */
	protected Properties languageTermProperties = null;

    /**
     * The Properties file with the audience information for the 008 offset 22
     */
	protected Properties audienceFrom008Properties = null;

    /**
	 * The output format (marcxml) for records processed from this service
	 */
	protected Format marcxmlFormat = null;
	
	/**
	 * A list of errors to add to the record currently being processed
	 */
	protected List<RecordMessage> errors = new ArrayList<RecordMessage>();

	/**
	 * A list of errors to add to the output record
	 */
	protected List<RecordMessage> outputRecordErrors = new ArrayList<RecordMessage>();
	
	protected XmlHelper xmlHelper = new XmlHelper();

    /**
	 * Construct a NormalizationService Object
	 */
	public void init() {
		// Initialize the XC format
		try  {
			marcxmlFormat = getFormatService().getFormatByName("marcxml");
			loadConfiguration(getUtil().slurp("service.xccfg", getClass().getClassLoader()));
		} catch (DatabaseConfigException e) {
			LOG.error("Could not connect to the database with the parameters in the configuration file.", e);
		}
	}

	@Override
	public List<OutputRecord> process(InputRecord recordIn) {
		TimingLogger.start("processRecord");
		try {
			List<OutputRecord> results = null;
			// If the record was deleted, delete and reprocess all records that were processed from it
			if(recordIn.getDeleted()) {
				results = new ArrayList<OutputRecord>();
				TimingLogger.start("processRecord.getDeleted");
				List<OutputRecord> successors = recordIn.getSuccessors();

				// If there are successors then the record exist and needs to be deleted. Since we are
				// deleting the record, we need to decrement the count.
				if (successors != null && successors.size() > 0) {
					inputRecordCount--;
				}
				
				// Handle reprocessing of successors
				for(OutputRecord successor : successors){
					successor.setStatus(Record.DELETED);
				}
				TimingLogger.stop("processRecord.getDeleted");
			} else {
				// Get the results of processing the record
				TimingLogger.start("processRecord.convertRecord");
				
				//TODO: make sure that recordIn is used to create the successors
				results = convertRecord(recordIn);

				TimingLogger.stop("processRecord.convertRecord");
				
				TimingLogger.stop("processRecord");
			}
			
			return results;
		} catch (Throwable t) {
			getUtil().throwIt(t);
		}
		return null;
	}
	
	private List<OutputRecord> convertRecord(InputRecord record) {
		
		// Empty the lists of errors because we're beginning to process a new record
		errors = new ArrayList<RecordMessage>();
		outputRecordErrors = new ArrayList<RecordMessage>();
		
		// The list of records resulting from processing the incoming record
		ArrayList<OutputRecord> results = new ArrayList<OutputRecord>();
		
		try {
			if(LOG.isDebugEnabled())
				LOG.debug("Normalizing record with ID " + record.getId() + ".");

			// The XML after normalizing the record
			Element marcXml = null;

			TimingLogger.start("create dom");
			record.setMode(Record.JDOM_MODE);
			marcXml = record.getOaiXmlEl();
			TimingLogger.stop("create dom");

			// Create a MarcXmlManagerForNormalizationService for the record
			MarcXmlManager normalizedXml = new MarcXmlManager(marcXml, getOrganizationCode());

			// Get the Leader 06.  This will allow us to determine the record's type, and we'll put it in the correct set for that type
			char leader06 = normalizedXml.getLeader().charAt(6);
			
			// Run these steps only if the record is a bibliographic record
			if("abcdefghijkmnoprt".contains(""+leader06))
			{
				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_REMOVE_OCOLC_003, "0").equals("1"))
					normalizedXml = removeOcolc003(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DCMI_TYPE_06, "0").equals("1"))
					normalizedXml = dcmiType06(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_LEADER_06_VOCAB, "0").equals("1"))
					normalizedXml = leader06MarcVocab(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_007_VOCAB_06, "0").equals("1"))
					normalizedXml = vocab06(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_MODE_OF_ISSUANCE, "0").equals("1"))
					normalizedXml = modeOfIssuance(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_MOVE_MARC_ORG_CODE, "0").equals("1"))
					normalizedXml = moveMarcOrgCode(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DCMI_TYPE_00_07, "0").equals("1"))
					normalizedXml = dcmiType0007(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_007_VOCAB, "0").equals("1"))
					normalizedXml = vocab007(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_007_SMD_TYPE, "0").equals("1"))
					normalizedXml = smdType007(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FICTION_OR_NONFICTION, "0").equals("1"))
					normalizedXml = fictionOrNonfiction(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_008_DATE_RANGE, "0").equals("1"))
					normalizedXml = dateRange(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_LANGUAGE_SPLIT, "0").equals("1"))
					normalizedXml = languageSplit(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_LANGUAGE_TERM, "0").equals("1"))
					normalizedXml = languageTerm(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_008_AUDIENCE, "0").equals("1"))
					normalizedXml = audienceFrom008(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_008_THESIS, "0").equals("1"))
					normalizedXml = thesisFrom008(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_ISBN_CLEANUP, "0").equals("1"))
					normalizedXml = isbnCleanup(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_SUPPLY_MARC_ORG_CODE, "0").equals("1"))
					normalizedXml = supplyMARCOrgCode(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FIX_035, "0").equals("1") 
						|| enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_035_LEADING_ZERO, "0").equals("1")
						|| enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FIX_035_CODE_9, "0").equals("1")) {
					normalizedXml = fix035(normalizedXml);
				}

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DEDUP_035, "0").equals("1"))
					normalizedXml = dedup035(normalizedXml);
				
				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_ROLE_AUTHOR, "0").equals("1"))
					normalizedXml = roleAuthor(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_ROLE_COMPOSER, "0").equals("1"))
					normalizedXml = roleComposer(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_UNIFORM_TITLE, "0").equals("1"))
					normalizedXml = uniformTitle(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_NRU_GENRE, "0").equals("1"))
					normalizedXml = nruGenre(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_TOPIC_SPLIT, "0").equals("1"))
					normalizedXml = topicSplit(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_CHRON_SPLIT, "0").equals("1"))
					normalizedXml = chronSplit(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_GEOG_SPLIT, "0").equals("1"))
					normalizedXml = geogSplit(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_GENRE_SPLIT, "0").equals("1"))
					normalizedXml = genreSplit(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DEDUP_DCMI_TYPE, "0").equals("1"))
					normalizedXml = dedupDcmiType(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DEDUP_007_VOCAB, "0").equals("1"))
					normalizedXml = dedup007Vocab(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_SEPERATE_NAME, "0").equals("1"))
					normalizedXml = seperateName(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_DEDUP_9XX, "0").equals("1"))
					normalizedXml = dedup9XX(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_TITLE_ARTICLE, "0").equals("1"))
					normalizedXml = titleArticle(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_BIB_LOCATION_NAME, "0").equals("1"))
					normalizedXml = bibLocationName(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_III_LOCATION_NAME, "0").equals("1"))
					normalizedXml = IIILocationName(normalizedXml);
				
				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_REMOVE_945_FIELD, "0").equals("1"))
					normalizedXml = remove945Field(normalizedXml);

			}

			// Run these steps only if the record is a holding record
			if("uvxy".contains(""+leader06))
			{
				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_HOLDINGS_LOCATION_NAME, "0").equals("1"))
					normalizedXml = holdingsLocationName(normalizedXml);

				if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_LOCATION_LIMIT_NAME, "0").equals("1"))
					normalizedXml = locationLimitName(normalizedXml);
			}
			
			if(LOG.isDebugEnabled())
				LOG.debug("Adding errors to the record.");

			record.setMessages(errors);
			
			if(LOG.isDebugEnabled())
				LOG.debug("Creating the normalized record.");

			// Get any records which were processed from the record we're processing
			// If there are any (there should be at most 1) we need to update them
			// instead of inserting a new Record
			
			// If there was already a processed record for the record we just processed, update it
			if(record.getSuccessors() != null && record.getSuccessors().size() > 0) {
				if(LOG.isDebugEnabled())
					LOG.debug("Updating the record which was processed from an older version of the record we just processed.");

				// Get the record which was processed from the record we just processed
				// (there should only be one)
				OutputRecord oldNormalizedRecord = record.getSuccessors().get(0);

				// Set the XML to the new normalized XML
				oldNormalizedRecord.setOaiXmlEl(normalizedXml.getModifiedMarcXml());
				
				// Add errors
				oldNormalizedRecord.setMessages(outputRecordErrors);
				
				// Add the normalized record after modifications were made to it to
				// the list of modified records.
				results.add(oldNormalizedRecord);

				return results;
			}
			// We need to create a new normalized record since we haven't normalized an older version of the original record
			// Do this only if the record we're processing is not deleted
			else
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Inserting the record since it was not processed from an older version of the record we just processed.");

				// Create the normalized record
				OutputRecord normalizedRecord = getRecordService().createRecord();
				normalizedRecord.setOaiXmlEl(normalizedXml.getModifiedMarcXml());
				normalizedRecord.setFormat(marcxmlFormat);

				// Add errors
				normalizedRecord.setMessages(outputRecordErrors);
				
				// Insert the normalized record

				// The setSpec and set Description of the "type" set we should add the normalized record to
				String setSpec = null;
				String setDescription = null;
				String setName = null;

				// Setup the setSpec and description based on the leader 06
				if("abcdefghijkmnoprt".contains(""+leader06)) {
					setSpec = "MARCXMLbibliographic";
					setName = "MARCXML Bibliographic Records";
					setDescription = "A set of all MARCXML Bibliographic records in the repository.";
				} else if(leader06 == 'u' || leader06 == 'v' || leader06 == 'x' || leader06 == 'y') {
					setSpec = "MARCXMLholding";
					setName = "MARCXML Holding Records";
					setDescription = "A set of all MARCXML Holding records in the repository.";
					
				} else if(leader06 == 'z') {
					setSpec = "MARCXMLauthority";
					setName = "MARCXML Authority Records";
					setDescription = "A set of all MARCXML Authority records in the repository.";
				} else { // If leader 6th character is invalid, then log error and do not process that record.
					logError("Record Id " + record.getId() + " with leader character " + leader06 + " not processed.");
					return new ArrayList<OutputRecord>();
				}
				
				if(setSpec != null) {
					// Get the set for the provider
					Set recordTypeSet = getSetService().getSetBySetSpec(setSpec);
					
					// Add the set if it doesn't already exist
					if(recordTypeSet == null)
						recordTypeSet = addSet(setSpec, setName, setDescription);
					
					// Add the set to the record
					normalizedRecord.addSet(recordTypeSet);
				}

				// Add the record to the list of records resulting from processing the
				// incoming record
				results.add(normalizedRecord);
				if(LOG.isDebugEnabled())
					LOG.debug("Created normalized record from unnormalized record with ID " + record.getId());
				return results;
			}
		}
		catch(Exception e)
		{
			LOG.error("An error occurred while normalizing the record with ID " + record.getId(), e);

			if(LOG.isDebugEnabled())
				LOG.debug("Adding errors to the record.");
			
			record.setMessages(errors);
			
			return results;
		}
	}

	/**
	 * If the 003's value is "OCoLC", remove it.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager removeOcolc003(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering RemoveOCoLC003 normalization step.");

		// Check if the 003 is "OCoLC"
		String field003 = marcXml.getField003();
		if(field003 != null && field003.equals("OCoLC"))
			marcXml.removeControlField("003");

		return marcXml;
	}

	/**
	 * Creates a DCMI Type field based on the record's Leader 06 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dcmiType06(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering DCMIType06 normalization step.");

		// The character at offset 6 of the leader field
		char leader06 = marcXml.getLeader().charAt(6);

		// Pull the DCMI type mapping from the configuration file based on the leader 06 value.
		String dcmiType = dcmiType06Properties.getProperty(""+leader06, null);

		// If there was no mapping for the provided leader 06, we can't create the field.  In this case return the unmodified MARCXML
		if(dcmiType == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Cannot find a DCMI Type mapping for the leader 06 value of " + leader06 + ", returning the unmodified MARCXML.");
		}
		else
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Found the DCMI Type " + dcmiType + " for the leader 06 value of " + leader06 + ".");

			// Add a MARCXML field to store the DCMI Type
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_DCMI_TYPE, dcmiType);
		}

		String field006 = marcXml.getField006();
		if(field006 != null)
		{
			// The character at offset 6 of the leader field
			char field006_0 = field006.charAt(0);

			// Pull the DCMI type mapping from the configuration file based on the leader 06 value.
			String dcmiType006_0 = dcmiType06Properties.getProperty(""+field006_0, null);

			// If there was no mapping for the provided leader 06, we can't create the field.  In this case return the unmodified MARCXML
			if(dcmiType006_0 == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a DCMI Type mapping for the 006 offset 0 value of " + dcmiType006_0 + ".");
			}

			else
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Found the DCMI Type " + dcmiType006_0 + " for the 006 offset 0 value of " + field006_0 + ".");

				// Add a MARCXML field to store the DCMI Type
				marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_DCMI_TYPE, dcmiType006_0);
			}
		}

		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates a vocabulary field based on the record's Leader 06 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager leader06MarcVocab(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering Leader06Vocab normalization step.");

		// The character at offset 6 of the leader field
		char leader06 = marcXml.getLeader().charAt(6);

		// Pull the SMD Vocab mapping from the configuration file based on the leader 06 value.
		String marcVocab = leader06MarcVocabProperties.getProperty(""+leader06, null);

		// If there was no mapping for the provided leader 06, we can't create the field.  In this case return the unmodified MARCXML
		if(marcVocab == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Cannot find a MARC vocabulary mapping for the leader 06 value of " + leader06 + ".");
			
			if(leader06 != ' ') {
				errors.add(new RecordMessage(service.getId(), "102", "error", "Invalid leader 06 value: " + leader06));
				outputRecordErrors.add(new RecordMessage(service.getId(), "102", "error", "Invalid leader 06 value: " + leader06));
			}
		}
		else
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Found the MARC vocabulary " + marcVocab + " for the leader 06 value of " + leader06 + ".");

			// Add a MARCXML field to store the SMD Vocab
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_007_MARC_VOCAB, marcVocab);
		}

		String field006 = marcXml.getField006();
		if(field006 != null)
		{
			// The character at offset 6 of the leader field
			char field006_0 = field006.charAt(0);

			// Pull the DCMI type mapping from the configuration file based on the leader 06 value.
			String marcVocab006_0 = leader06MarcVocabProperties.getProperty(""+field006_0, null);

			// If there was no mapping for the provided leader 06, we can't create the field.  In this case return the unmodified MARCXML
			if(marcVocab006_0 == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a MARC vocabulary mapping for the 006 offset 0 value of " + marcVocab006_0 + ".");
			}

			else
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Found the MARC vocabulary " + marcVocab006_0 + " for the 006 offset 0 value of " + marcVocab006_0 + ".");

				// Add a MARCXML field to store the SMD Vocab
				marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_007_MARC_VOCAB, marcVocab006_0);
			}
		}

		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates a vocabulary field based on the record's Leader 06 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager vocab06(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 007Vocab06 normalization step.");

		char leader06 = ' ';
		// The character at offset 6 of the leader field
		if (marcXml.getLeader() != null && marcXml.getLeader().length() >= 7 ) {
			leader06 = marcXml.getLeader().charAt(6);
		}

		// Pull the SMD Vocab mapping from the configuration file based on the leader 06 value.
		String marcVocab = vocab06Properties.getProperty(""+leader06, null);

		// If there was no mapping for the provided leader 06, we can't create the field.  In this case return the unmodified MARCXML
		if(marcVocab == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Cannot find a vocab mapping for the leader 06 value of " + leader06 + ", returning the unmodified MARCXML.");

			return marcXml;
		}

		if(LOG.isDebugEnabled())
			LOG.debug("Found the vocab " + marcVocab + " for the leader 06 value of " + leader06 + ".");

		// Add a MARCXML field to store the SMD Vocab
		marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_007_VOCAB, marcVocab);

		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates a mode of issuance field based upon single letter in Leader07
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager modeOfIssuance(MarcXmlManager marcXml)
	{
		
		if(LOG.isDebugEnabled())
			LOG.debug("Entering ModeOfIssuance normalization step.");

		// The character at offset 7 of the leader field
		char leader07 = marcXml.getLeader().charAt(7);

		// Pull the mode of issuance mapping from the configuration file based on the leader 07 value.
		String modeOfIssuance = modeOfIssuanceProperties.getProperty(""+leader07, null);
		// If there was no mapping for the provided leader 07, we can't create the field.  In this case return the unmodified MARCXML
		if(modeOfIssuance == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Cannot find a mode of issuance mapping for the leader 07 value of " + leader07 + ", returning the unmodified MARCXML.");

			errors.add(new RecordMessage(service.getId(), "103", "error", "Invalid leader 07 value: " + leader07));
			outputRecordErrors.add(new RecordMessage(service.getId(), "103", "error", "Invalid leader 07 value: " + leader07));
			
			return marcXml;
		}

		if(LOG.isDebugEnabled())
			LOG.debug("Found the mode of issuance " + modeOfIssuance + " for the leader 07 value of " + leader07 + ".");

		// Add a MARCXML field to store the mode of issuance
		marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_MODE_OF_ISSUANCE, modeOfIssuance);

		return marcXml;
	}

	/**
	 * Creates a new 035 field on the record based on the existing 001 and 003 fields
	 * for example, if 001 = 12345 and 003 = NRU, the new 035 will be (NRU)12345.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager moveMarcOrgCode(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering MoveMarcOrgCode normalization step.");

		// Get the 001 and 003 control fields
		String control001 = marcXml.getField001();
		String control003 = marcXml.getField003();

		if(control001 == null) {
			outputRecordErrors.add(new RecordMessage(service.getId(), "101", "error", " - Cannot create 035 from 001."));
			errors.add(new RecordMessage(service.getId(), "101", "error"));
		}
	
		// If either control field didn't exist, we don't have to do anything
		if(control001 == null || control003 == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("The record was missing either an 001 or an 003 control field, so we do not have to move the old marc organization code into a new 035 field.");

			return marcXml;
		}

		boolean moveAllOrgCodes = enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_MOVE_ALL_MARC_ORG_CODES, "0").equals("1");

		// Create the new 035 field
		if(moveAllOrgCodes || control003.equalsIgnoreCase(getOrganizationCode())) {
			String new035 = null;
			
			if (Character.isLetter(control001.charAt(0))) {
				int index = 0;
				StringBuffer s = new StringBuffer();
				while (Character.isLetter(control001.charAt(index))) {
					s.append(control001.charAt(index));
					index++;
				}
				String new003 = s.toString();
				
				control001 = control001.substring(new003.length());
				
				new035 = "(" + new003 + ")" + control001;
			} else {
				new035 = "(" + control003 + ")" + control001;
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Moving the record's organization code to a new 035 field with value " + new035 + ".");

			// Add the new 035 field
			marcXml.addMarcXmlField("035", new035);
		}

		return marcXml;
	}

	/**
	 * Creates a DCMI Type field based on the record's 007 offset 00 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dcmiType0007(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering DCMIType0007 normalization step.");

		// The value of field 007
		String field007 = marcXml.getField007();

		// The character at offset 00 of the 007 field
		char field007offset00 = ((field007 != null && field007.length() > 0) ? field007.charAt(0) : ' ');

		// Pull the DCMI type mapping from the configuration file based on the leader 06 value.
		String dcmiType = dcmiType0007Properties.getProperty(""+field007offset00, null);

		// If there was no mapping for the provided 007 offset 00, we can't create the field.  In this case return the unmodified MARCXML
		if(dcmiType == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Cannot find a DCMI Type mapping for the 007 offset 00 value of " + field007offset00 + ", returning the unmodified MARCXML.");

			return marcXml;
		}

		if(LOG.isDebugEnabled())
			LOG.debug("Found the DCMI Type " + dcmiType + " for the 007 offset 00 value of " + field007offset00 + ".");

		// Add a MARCXML field to store the DCMI Type
		marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_DCMI_TYPE, dcmiType);

		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates an 007 vocabulary field based on the record's 007 offset 00 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager vocab007(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 007Vocab normalization step.");

		// The value of field 007
		String field007 = marcXml.getField007();
		
		if (field007 != null && field007.length() > 0) {
	
			// The character at offset 00 of the 007 field
			char field007offset00 = field007.charAt(0);
			
			// Pull the 007 Vocab mapping from the configuration file based on the leader 06 value.
			String smdVocab = vocab007Properties.getProperty(""+field007offset00, null);
	
			// If there was no mapping for the provided 007 offset 00, we can't create the field.  In this case return the unmodified MARCXML
			if(smdVocab == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find an 007 Vocab mapping for the 007 offset 00 value of " + field007offset00 + ", returning the unmodified MARCXML.");
	
				errors.add(new RecordMessage(service.getId(), "104", "error", "Invalid value in Control Field 007 offset 00: " + field007offset00));
				outputRecordErrors.add(new RecordMessage(service.getId(), "104", "error", "Invalid value in Control Field 007 offset 00: " + field007offset00));
				
				return marcXml;
			}
	
			if(LOG.isDebugEnabled())
				LOG.debug("Found the 007 Vocab " + smdVocab + " for the 007 offset 00 value of " + field007offset00 + ".");
	
			// Add a MARCXML field to store the SMD Vocab
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_007_VOCAB, smdVocab);
		}
		
		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates an SMD vocabulary field based on the record's 007 offset 00 and offset 01 values.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager smdType007(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 007SMDVocab normalization step.");

		// The value of field 007
		String field007 = marcXml.getField007();

		// The character at offsets 00 and 01 of the 007 field
		String field007offset00and01 = ((field007 != null && field007.length() >= 3) ? field007.substring(0, 2) : "  ");

		if (!field007offset00and01.equals("  ")) {
			// Pull the SMD type mapping from the configuration file based on the leader 06 value.
			String smdVocab = smdType007Properties.getProperty(field007offset00and01, null);
	
			// If there was no mapping for the provided 007 offset 00, we can't create the field.  In this case return the unmodified MARCXML
			if(smdVocab == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a SMD Vocab mapping for the 007 offset 00 and 01 values of " + field007offset00and01 + ", returning the unmodified MARCXML.");
				
				errors.add(new RecordMessage(service.getId(), "104", "error", "Invalid value in Control Field 007 offset 00: " + field007offset00and01));
				outputRecordErrors.add(new RecordMessage(service.getId(), "104", "error", "Invalid value in Control Field 007 offset 00: " + field007offset00and01));
	
	
				return marcXml;
			}
			if(LOG.isDebugEnabled())
				LOG.debug("Found the SMD type " + smdVocab + " for the 007 offset 00 and 01 values of " + field007offset00and01 + ".");

			// Add a MARCXML field to store the SMD Vocab
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_SMD_VOCAB, smdVocab);

		}


		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates a field with a value of "Fiction" if the Leader 06 value is 'a'
	 * and the 008 offset 33 value is '1', otherwise creates the field with a value
	 * of "Non-Fiction"
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager fictionOrNonfiction(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering FictionOrNonfiction normalization step.");

		// The character at offset 6 of the leader field
		char leader06 = marcXml.getLeader().charAt(6);

		// Return the unmodified MARCXML if the leader06 is neither 'a' nor 't'
		if(leader06 != 'a' && leader06 != 't')
			return marcXml;

		// The value of field 008
		String field008 = marcXml.getField008();

		// The character at offset 33 of the 008 field
		char field008offset33 = ((field008 != null && field008.length() >= 34) ? field008.charAt(33) : ' ');

		if(LOG.isDebugEnabled())
			LOG.debug("Leader 06 = " + leader06 + " and 008 offset 33 is " + field008offset33 + ".");

		// Add the fiction or nonfiction field
		if(field008offset33 == '1' || field008offset33 == 'd' || field008offset33 == 'j' || field008offset33 == 'f' || field008offset33 == 'p')
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_FICTION_OR_NONFICTION, "Fiction");
		else
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_FICTION_OR_NONFICTION, "Non-Fiction");

		// Return the modified MARCXML record
		return marcXml;
	}

	/**
	 * Creates a field with the date range specified in the 008 control field
	 * if 008 offset 06 is 'r'
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dateRange(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 008DateRange normalization step.");

		// The value of field 008
		String field008 = marcXml.getField008();

		// If 008 offset 06 is not 'c', 'd', or 'k' we don't need to do anything.
		if(field008 == null || (field008.charAt(6) != 'c' && field008.charAt(6) != 'd' && field008.charAt(6) != 'k'))
		{
			if(LOG.isDebugEnabled())
				LOG.debug("008 offset 6 was not 'c', 'd' or 'k' so we will not add a field with the date range.");

			return marcXml;
		}

		// If we got here, 008 offset 06 was 'r' and we need to add a field with the date range
		String dateRange = field008.substring(7, 11) + "-" + field008.substring(11, 15);

		// If either date is '9999', replace it with the empty string.  So "1983-9999" becomes "1983-    "
		dateRange = dateRange.replaceAll("9999", "    ");

		if(LOG.isDebugEnabled())
			LOG.debug("008 offset 6 was 'c', 'd' or 'k' so we will add a field with the date range " + dateRange + ".");

		// Add the date range field
		marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_DATE_RANGE, dateRange);

		return marcXml;
	}

	/**
	 * Creates a field for each language found in the original record's 008 offset 35-38
	 * and 041 $a and $d fields.  Only one field is added when the original record
	 * would produce duplicates of it.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager languageSplit(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering LanguageSplit normalization step.");

		// A list of language fields we're adding as fields.
		ArrayList<String> languages = new ArrayList<String>();

		// The value of field 008
		String field008 = marcXml.getField008();

		// If the 008 field non null, add the language it specified so the list of languages
		// provided that languages is valid
		if(field008 != null && field008.length() >= 39)
		{
			String langFrom008 = field008.substring(35, 38);

			if(isLanguageValid(langFrom008))
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Found the valid language " + langFrom008 + " in the 008 field.");

				languages.add(langFrom008.toLowerCase());
			}
		}

		// The 041 $a and $d fields
		ArrayList<String> fields041a = marcXml.getField041subfieldA();
		ArrayList<String> fields041d = marcXml.getField041subfieldD();

		// Add the languages in 041 $a to the list of languages assuming doing so wouldn't create duplicates
		for(String field041a : fields041a)
		{
			// Every group of three characters should be treated as a separate language code.
			// So characters 1-3 are one language, 4-6 are another, etc.
			for(int counter = 0; counter + 3 <= field041a.length(); counter += 3)
			{
				String language = field041a.substring(counter, counter+3);

				if(!languages.contains(language.toLowerCase()))
				{
					if(isLanguageValid(language))
					{
						if(LOG.isDebugEnabled())
							LOG.debug("Found the valid language " + language + " in the 041 $a field.");

						languages.add(language.toLowerCase());
					}
				}
			}
		}

		// Add the languages in 041 $d to the list of languages assuming doing so wouldn't create duplicates
		for(String field041d : fields041d)
		{
			// Every group of three characters should be treated as a separate language code.
			// So characters 1-3 are one language, 4-6 are another, etc.
			for(int counter = 0; counter + 3 <= field041d.length(); counter += 3)
			{
				String language = field041d.substring(counter, counter+3);

				if(!languages.contains(language.toLowerCase()))
				{
					if(isLanguageValid(language))
					{
						if(LOG.isDebugEnabled())
							LOG.debug("Found the valid language " + language + " in the 041 $d field.");

						languages.add(language.toLowerCase());
					}
				}
			}
		}

		// Add each language to the MARCXML in a new field
		for(String language : languages)
			if (!language.equals("   ")){
				marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_LANGUAGE_SPLIT, language);
			}

		return marcXml;
	}

	/**
	 * Creates a field which contains the full name of the language based for
	 * each language code from the LanguageSplit step.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager languageTerm(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering LanguageTerm normalization step.");

		// A list of language code fields we've add.
		ArrayList<String> languageCodes = marcXml.getAddedLanguageCodes();

		// Add each language term to the MARCXML in a new field
		for(String languageCode : languageCodes)
		{
			// Pull the language term mapping from the configuration file based on the language code.
			String languageTerm = languageTermProperties.getProperty(languageCode, null);
			
			// If no mapping for language code, then check for capitalized language code
			if (languageTerm == null) {
				languageTerm = languageTermProperties.getProperty(languageCode.toUpperCase(), null);
			}

			// If there was no mapping for the provided language code, we can't create the field.  In this case continue to the next language code
			if(languageTerm == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a language term mapping for the language code " + languageCode + ".");

				errors.add(new RecordMessage(service.getId(), "106", "error", "Unrecognized language code: " + languageCode));
				outputRecordErrors.add(new RecordMessage(service.getId(), "106", "error", "Unrecognized language code: " + languageCode));
				
				continue;
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Found the language term " + languageTerm + " for the language code " + languageCode + ".");

			// Add a MARCXML field to store the SMD Vocab

			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_LANGUAGE_TERM, languageTerm);
		}

		return marcXml;
	}

	/**
	 * If leader 06 contains certain values, create a field with the intended audience from the
	 * 008 offset 22 value.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager audienceFrom008(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 008Audience normalization step.");

		// The character at offset 6 of the leader field
		char leader06 = marcXml.getLeader().charAt(6);

		// If the leader 06 value is one of those which suggests this record needs an audience field,
		// add one based on the 008 offset 22 field.
		if(leader06 == 'a' || leader06 == 'c' || leader06 == 'd' || leader06 == 'g' || leader06 == 'k' || leader06 == 'm' || leader06 == 'o' || leader06 == 'r')
		{
			// The value of field 008
			String field008 = marcXml.getField008();

			// The character at offset 22 of the 008 field
			char field008offset22 = (field008 != null ? field008.charAt(22) : ' ');

			// Pull the audience mapping from the configuration file based on the 008 offset 22 value.
			String audience = audienceFrom008Properties.getProperty(""+field008offset22, null);

			// If there was no mapping for the provided 008 offset 22, we can't create the field.  In this case return the unmodified MARCXML
			if(audience == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find an audience mapping for the 008 offset 22 value of " + field008offset22 + ", returning the unmodified MARCXML.");

				return marcXml;
			}
			else if(field008offset22 != ' ' && (field008offset22 != 'a' && field008offset22 != 'b' && field008offset22 != 'c' && field008offset22 != 'd' && field008offset22 != 'e' && field008offset22 != 'f' && field008offset22 != 'g' && field008offset22 != 'j'&& field008offset22 != '|' && field008offset22 != '#')) {
				errors.add(new RecordMessage(service.getId(), "105", "error", "Invalid value in Control Field 008 offset 22: " + field008offset22));
				outputRecordErrors.add(new RecordMessage(service.getId(), "105", "error", "Invalid value in Control Field 008 offset 22: " + field008offset22));
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Found the audience " + audience + " for the 008 offset 22 value of " + field008offset22 + ".");

			// Add a MARCXML field to store the audience
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_AUDIENCE, audience);
		}

		return marcXml;
	}

	/**
	 * If there is no 502 field, create one with the value "Thesis" if 08 offset 24, 25, 26 or 27 is 'm'.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager thesisFrom008(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering 008Thesis normalization step.");

		// If there is already a 502 field, don't make any changes
		if(marcXml.getField502() != null && marcXml.getField502().size() > 0)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("502 field already exists, so we won't add another.");

			return marcXml;
		}

		// Get the leader 06
		char leader06 = marcXml.getLeader().charAt(6);

		// If the leader 06 is not 'a' return without doing anything
		if(leader06 != 'a')
		{
			if(LOG.isDebugEnabled())
				LOG.debug("The leader 06 is not 'a', so this cannot be a Thesis.");

			return marcXml;
		}

		// The value of field 008
		String field008 = marcXml.getField008();

		// If the 008 offset 24, 25, 26, or 27 is 'm', add a 502 tag with the value "Thesis."
		if(field008 != null && field008.substring(24, 28).contains("m"))
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Adding 502 field with the value \"Thesis.\"");

			// Add a MARCXML 502 field with the value "Thesis"
			marcXml.addMarcXmlField("502", "Thesis.");
		}
		else if(LOG.isDebugEnabled())
			LOG.debug("Not adding 502 field with the value \"Thesis.\"");

		return marcXml;
	}

	/**
	 * Creates a field for each 020 field with the same $a value except that
	 * everything after the first left parenthesis is removed.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager isbnCleanup(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering ISBNCleanup normalization step.");

		// The 020 $a field
		ArrayList<String> fields020a = marcXml.getField020();

		// Return if there was no 020 $a
		if(fields020a.size() == 0)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("The record did not have an 020 $a field so we don't have to normalize the ISBN number.");

			return marcXml;
		}

		// The index of the first left parenthesis in the 020 $a
		for(String field020a : fields020a)
		{
			int leftParenIndex = field020a.indexOf('(');
			int colonIndex = field020a.indexOf(':');
			int endIndex = (leftParenIndex < 0 ? colonIndex : (colonIndex < 0 || leftParenIndex < colonIndex ? leftParenIndex : colonIndex));

			// The cleaned up ISBN number.  This is the 020 $a with everything after the first left parenthesis removed
			String cleanIsbn = (endIndex >= 0 ? field020a.substring(0, endIndex) : field020a);

			if(LOG.isDebugEnabled())
				LOG.debug("Adding the cleaned up ISBN number " + cleanIsbn + " to the normalized record.");

			// Add the cleaned up ISBN to the MARCXML in a new field
			marcXml.addMarcXmlField(NormalizationServiceConstants.FIELD_9XX_CLEAN_ISBN, cleanIsbn);
		}

		return marcXml;
	}

	/**
	 * Creates a new 035 field on the record based on the existing 001 field if
	 * there is no 003 field.  For example, if 001 = 12345 and the organization
	 * code in the configuration file is NRU, the new 035 will be (NRU)12345.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager supplyMARCOrgCode(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering SupplyMARCOrgCode normalization step.");

		// Get the 001 and 003 control fields
		String control001 = marcXml.getField001();
		String control003 = marcXml.getField003();

		// If either control field didn't exist, we don't have to do anything
		if(control003 != null || control001 == null)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("The record was missing either an 001 or contained an 003 control field, so we do not have to supply a marc organization code into a new 035 field.");

			return marcXml;
		}
		
		String new003 = null;

		if (Character.isLetter(control001.charAt(0))) {
			int index = 0;
			StringBuffer s = new StringBuffer();
			while (Character.isLetter(control001.charAt(index))) {
				s.append(control001.charAt(index));
				index++;
			}
			new003 = s.toString();
			
			control001 = control001.substring(new003.length());
		} else {
			// Add an 003 to thee header
			new003 = getOrganizationCode();
		}

		if(LOG.isDebugEnabled())
			LOG.debug("Supplying the record's organization code to a new 003 field with value " + new003 + ".");

		// Add the new 003 field
		marcXml.addMarcXmlControlField("003", new003);
				
		// Create the new 035 field
		String new035 = "(" + getOrganizationCode() + ")" + control001;

		if(LOG.isDebugEnabled())
			LOG.debug("Supplying the record's organization code to a new 035 field with value " + new035 + ".");

		// Add the new 035 field
		marcXml.addMarcXmlField("035", new035);

		return marcXml;
	}
	
	/**
	 * Edits OCLC 035 records with common incorrect formats to take the format
	 * (OCoLC)%CONTROL_NUMBER%.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	@SuppressWarnings("unchecked")
	private MarcXmlManager fix035(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering fix035 normalization step.");

		// Get the original list of 035 elements.  We know that any 035 we
		// supplied had the correct format, so all incorrect 035 records must
		// be contained in this list
		ArrayList<Element> field035Elements = marcXml.getOriginal035Fields();

		// Loop over the 035 elements
		for(Element field035 : field035Elements)
		{
			// The $a and $b subfields of
			Element aSubfield = null;
			Element bSubfield = null;
			Element subfield9 = null;

			// Get the control fields
			List<Element> subfields = field035.getChildren("subfield", marcNamespace);

			// Iterate over the subfields to find the $a and $b subfields
			for(Element subfield : subfields)
			{
				// Initialize the aSubfield if we found the $a
				if(subfield.getAttribute("code").getValue().equals("a"))
					aSubfield = subfield;

				// Initialize the bSubfield if we found the $b
				if(subfield.getAttribute("code").getValue().equals("b")) {
					bSubfield = subfield;
					errors.add(new RecordMessage(service.getId(), "107", "error", "Invalid 035 Data Field (035s should not contain a $" + subfield.getAttribute("code").getValue() + " subfield)"));
				}

				// Initialize the subfield9 if we found the $9
				if(subfield.getAttribute("code").getValue().equals("9")) {
					subfield9 = subfield;
					errors.add(new RecordMessage(service.getId(), "107", "error", "Invalid 035 Data Field (035s should not contain a $" + subfield.getAttribute("code").getValue() + " subfield)"));
				}
					
			} // end loop over 035 subfields

			// Execute only if Fix035 step is enabled
			if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FIX_035, "0").equals("1")) {
				// First case: $b = ocm or $b = ocn or $b = ocl, and $a contains only the control number
				if(bSubfield != null)
				{
					// Check if the $b subfield was "ocm", "ocn", or "ocl"
					if(bSubfield.getText().equals("ocm") || bSubfield.getText().equals("ocn") || bSubfield.getText().equals("ocl"))
					{
						// Try to parse out the control number from the $a subfield
						if(aSubfield != null)
						{
							try
							{
								String controlNumber = aSubfield.getText().trim();
	
								// Set $a to (OCoLC)%CONTROL_NUMBER%
								aSubfield.setText("(OCoLC)" + controlNumber);
							}
							catch(NumberFormatException e)
							{
							}
						}
					}
	
					// Remove the b subfield as we shouldn't ever have one
					field035.removeContent(bSubfield);
				}
	
				// Second case: $a = (OCoLC)ocm%CONTROL_NUMBER% or (OCoLC)ocn%CONTROL_NUMBER% or (OCoLC)ocl%CONTROL_NUMBER%
				if(aSubfield != null && (aSubfield.getText().startsWith("(OCoLC)ocm") || aSubfield.getText().startsWith("(OCoLC)ocn") || aSubfield.getText().startsWith("(OCoLC)ocl")))
					aSubfield.setText("(OCoLC)" + aSubfield.getText().substring(10));
	
				// Third case: $a = ocm%CONTROL_NUMBER% or ocn%CONTROL_NUMBER% or ocl%CONTROL_NUMBER%
				if(aSubfield != null && (aSubfield.getText().startsWith("ocm") || aSubfield.getText().startsWith("ocn") || aSubfield.getText().startsWith("ocl")))
					aSubfield.setText("(OCoLC)" + aSubfield.getText().substring(3));
			}

			// Execute only if Fix035Code9 step is enabled
			if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FIX_035_CODE_9, "0").equals("1")) {
				// Forth case: $9 = ocm%CONTROL_NUMBER% or ocn%CONTROL_NUMBER% or ocl%CONTROL_NUMBER%
				if(subfield9 != null && (subfield9.getText().startsWith("ocm") || subfield9.getText().startsWith("ocn") || subfield9.getText().startsWith("ocl")))
				{
					// Add an $a subfield if there wasn't one
					if(aSubfield == null)
					{
						aSubfield = new Element("subfield", marcNamespace);
						aSubfield.setAttribute("code", "a");
						field035.addContent("\t").addContent(aSubfield).addContent("\n");
					}
	
					aSubfield.setText("(OCoLC)" + subfield9.getText().substring(3));
					field035.removeContent(subfield9);
				}
			}
		
			// Execute only if Fix035 step is enabled
			if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_FIX_035, "0").equals("1")) {
				// If the $a has more than one prefix, only use the first one
				if(aSubfield != null)
				{
					String aSubfieldText = aSubfield.getText();
					if(aSubfieldText.contains("(") && aSubfieldText.contains(")"))
						aSubfield.setText(aSubfieldText.substring(0, aSubfieldText.indexOf(')') + 1) + aSubfieldText.substring(aSubfieldText.lastIndexOf(')') + 1));
				}
			}

			// Execute only if 035LeadingZero step is enabled. Removes leading zeros in 035 $a records. Changes (OCoLC)00021452 to (OCoLC)21452 
			if(enabledSteps.getProperty(NormalizationServiceConstants.CONFIG_ENABLED_035_LEADING_ZERO, "0").equals("1")) {
				
				if (aSubfield != null) {
					// Get value of $a
					String value = aSubfield.getText();
					String newValue = "";

					// Remove leading zeros in value. Ex. Change (OCoLC)000214052 to (OCoLC)214052 
					int indexOfBracket = value.indexOf(")");
					newValue = value.substring(0, indexOfBracket + 1);
					
					boolean numericValueStarts = false;
					String regex = "[1-9]";
					
					for (int i=indexOfBracket + 1;i < value.length();i++) {
						
						if (String.valueOf(value.charAt(i)).matches(regex)) {
							numericValueStarts = true;
						}
						
						if (value.charAt(i) != '0') {
							newValue = newValue + value.charAt(i);
						} else if (numericValueStarts) {
							newValue = newValue + value.charAt(i);
						}
					}
					
					// Set the new value back in subfield $a
					aSubfield.setText(newValue);
				}

			}
		}

		return marcXml;
	}

	/**
	 * Removes duplicate 035 fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dedup035(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering dedup035 normalization step.");

		marcXml.deduplicateMarcXmlField("035");

		return marcXml;
	}

	/**
	 * If 100 $4, 110 $4, or 111 $4 are empty and leader 06 is 'a',
	 * set the empty $4 subfields to "aut".
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager roleAuthor(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering RoleAuthor normalization step.");

		// If leader 06 is 'a', set the $4 subfields of 100, 110 and 111 to "aut" if they're not already set
		if(marcXml.getLeader().charAt(6) == 'a')
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Setting 100, 110, and 111 $4 to \"aut\" if they're not set to something else already.");

			for(Element field100 : marcXml.getField100Element())
			{
				if(marcXml.getSubfieldsOfField(field100, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 100 with value aut.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("aut");
					field100.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}

			for(Element field110 : marcXml.getField110Element())
			{
				if(marcXml.getSubfieldsOfField(field110, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 110 with value aut.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("aut");
					field110.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}

			for(Element field111 : marcXml.getField111Element())
			{
				if(marcXml.getSubfieldsOfField(field111, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 111 with value aut.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("aut");
					field111.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}
		}

		return marcXml;
	}

	/**
	 * If 100 $4, 110 $4, or 111 $4 are empty and leader 06 is 'c',
	 * set the empty $4 subfields to "cmp".
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager roleComposer(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering RoleComposer normalization step.");

		// If leader 06 is 'c', set the $4 subfields of 100, 110 and 111 to "cmp" if they're not already set
		if(marcXml.getLeader().charAt(6) == 'c')
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Setting 100, 110, and 111 $4 to \"cmp\" if they're not set to something else already.");

			for(Element field100 : marcXml.getField100Element())
			{
				if(marcXml.getSubfieldsOfField(field100, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 100 with value cmp.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("cmp");
					field100.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}

			for(Element field110 : marcXml.getField110Element())
			{
				if(marcXml.getSubfieldsOfField(field110, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 110 with value cmp.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("cmp");
					field110.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}

			for(Element field111 : marcXml.getField111Element())
			{
				if(marcXml.getSubfieldsOfField(field111, '4').size() <= 0)
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding $4 to 111 with value cmp.");

					// Add the subfield to the field with the specified value
					Element newSubfield = new Element("subfield", marcNamespace);
					newSubfield.setAttribute("code", "4");
					newSubfield.setText("cmp");
					field111.addContent("\t").addContent(newSubfield).addContent("\n\t");
				}
			}
		}

		return marcXml;
	}

	/**
	 * If 130, 240, and 243 all don't exist and 245 does exist, copy the 245 into
	 * a new 243 field.  Only copies subfields afknp
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager uniformTitle(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering UniformTitle normalization step.");

		// If 130, 240, and 243 all don't exist and 245 does exist, copy the 245 into a new 240 field.
		// Only copy subfields afknp.
		if(marcXml.getField130() == null && marcXml.getField240() == null && marcXml.getField243() == null && marcXml.getField245() != null)
		{
			if(marcXml.getField100Element().size() > 0 || marcXml.getField110Element().size() > 0 || marcXml.getField111Element().size() > 0)
				marcXml.copyMarcXmlField("245", "240", "afknp", "0", "0", true);
			else
				marcXml.copyMarcXmlField("245", "130", "afknp", "0", " ", true);
		}

		return marcXml;
	}

	/**
	 * Changes 655 $2 subfield to "NRUgenre" and deletes the 655 $5 subfield
	 * for each 655 field with $2 = "local" and $5 = "NRU"
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	@SuppressWarnings("unchecked")
	private MarcXmlManager nruGenre(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering nruGenre normalization step.");

		// A list of all the 655 fields in the MARCXML record
		ArrayList<Element> field655elements = marcXml.getField655Elements();

		// Fix each 655 with $2 = "local" and $5 = "NRU"
		for(Element field655 : field655elements)
		{
			// The $2 and $5 subfields
			Element subfield2 = null;
			Element subfield5 = null;

			// Get the control fields
			List<Element> subfields = field655.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the subject display if it
			// is in the list of key 655 subfields
			for(Element subfield : subfields)
			{
				// Save the current subfield if it's the $2 subfield
				if(subfield.getAttribute("code").getValue().equals("2"))
					subfield2 = subfield;

				// Save the current subfield if it's the $5 subfield
				if(subfield.getAttribute("code").getValue().equals("5"))
					subfield5 = subfield;
			}

			// If the $2 is "local" and the $5 is "NRU", delete the $5 and set
			// the $2 to "NRUgenre"
			if(subfield2 != null && subfield5 != null && subfield2.getText().equals("local") && subfield5.getText().equals("NRU"))
			{
				field655.removeContent(subfield5);
				subfield2.setText("NRUgenre");
			}
		}

		return marcXml;
	}

	/**
	 * Copies relevant fields from the 600, 610, 611, 630, and 650 datafields into
	 * 9xx fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager topicSplit(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering TopicSplit normalization step.");

		// A list of the tags to copy
		ArrayList<String> tagsToCopy = new ArrayList<String>();
		tagsToCopy.add("600");
		tagsToCopy.add("610");
		tagsToCopy.add("611");
		tagsToCopy.add("630");
		tagsToCopy.add("650");

		// Copy the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_TOPIC_SPLIT, "vxyz");

		// Add the fields which we want to copy just the $x subfield from
		tagsToCopy.add("648");
		tagsToCopy.add("651");
		tagsToCopy.add("652");
		tagsToCopy.add("653");
		tagsToCopy.add("654");
		tagsToCopy.add("655");

		// Copy just the $x subfields of the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_TOPIC_SPLIT, 'x');

		return marcXml;
	}

	/**
	 * Copies relevant fields from the 648 datafield into 9xx fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager chronSplit(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering ChronSplit normalization step.");

		// A list of the tags to copy
		ArrayList<String> tagsToCopy = new ArrayList<String>();
		tagsToCopy.add("648");

		// Copy the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_CHRON_SPLIT, "vxyz");

		// Add the fields which we want to copy just the $y subfield from
		tagsToCopy.add("600");
		tagsToCopy.add("610");
		tagsToCopy.add("611");
		tagsToCopy.add("630");
		tagsToCopy.add("650");
		tagsToCopy.add("651");
		tagsToCopy.add("652");
		tagsToCopy.add("653");
		tagsToCopy.add("654");
		tagsToCopy.add("655");

		// Copy just the $y subfields of the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_CHRON_SPLIT, 'y');

		return marcXml;
	}

	/**
	 * Copies relevant fields from the 651 datafield into 9xx fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager geogSplit(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering GeogSplit normalization step.");

		// A list of the tags to copy
		ArrayList<String> tagsToCopy = new ArrayList<String>();
		tagsToCopy.add("651");

		// Copy the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_GEOG_SPLIT, "vxyz");

		// Add the fields which we want to copy just the $z subfield from
		tagsToCopy.add("600");
		tagsToCopy.add("610");
		tagsToCopy.add("611");
		tagsToCopy.add("630");
		tagsToCopy.add("650");
		tagsToCopy.add("648");
		tagsToCopy.add("652");
		tagsToCopy.add("653");
		tagsToCopy.add("654");
		tagsToCopy.add("655");

		// Copy just the $z subfields of the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_GEOG_SPLIT, 'z');

		return marcXml;
	}

	/**
	 * Copies relevant fields from the 655 datafield into 9xx fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager genreSplit(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering GenreSplit normalization step.");

		// A list of the tags to copy
		ArrayList<String> tagsToCopy = new ArrayList<String>();
		tagsToCopy.add("655");

		// Copy the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_GENRE_SPLIT, "vxyz");

		// Reset tags to copy to contain only those fields which we want to copy just the $v subfield from
		tagsToCopy.add("600");
		tagsToCopy.add("610");
		tagsToCopy.add("611");
		tagsToCopy.add("630");
		tagsToCopy.add("650");
		tagsToCopy.add("648");
		tagsToCopy.add("652");
		tagsToCopy.add("653");
		tagsToCopy.add("654");
		tagsToCopy.add("651");

		// Copy just the $v subfields of the fields
		marcXml.splitField(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_GENRE_SPLIT, 'v');

		return marcXml;
	}

	/**
	 * Removes duplicate DCMI type fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dedupDcmiType(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering DedupDCMIType normalization step.");

		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_DCMI_TYPE);

		return marcXml;
	}

	/**
	 * Removes duplicate 007 vocab fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dedup007Vocab(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering Dedup007Vocab normalization step.");

		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_007_VOCAB);

		return marcXml;
	}

	/**
	 * Replaces the location code in 852 $b with the name of the location it represents.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager bibLocationName(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering bibLocationName normalization step.");

		// The 852 $l value
		ArrayList<String> field852subfieldBs = marcXml.getField852subfieldBs();

		// Pull the location mapping from the configuration file based on the 852 $l value.
	    for(String field852subfieldB : field852subfieldBs)
	    {
			String location = locationNameProperties.getProperty(field852subfieldB.replace(' ', '_'), null);

			// If there was no mapping for the provided 852 $b, we can't create the field.  In this case return the unmodified MARCXML
			if(location == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a location mapping for the 852 $b value of " + field852subfieldB + ", returning the unmodified MARCXML.");

				return marcXml;
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Found the location " + location + " for the 852 $b value of " + field852subfieldB + ".");

			// Set the 852 $l value to the location we found for the location code.
			marcXml.setMarcXmlSubfield("852", "b", location, field852subfieldB);
	    }

		return marcXml;
	}

	/**
	 * For the location code in 852 $b, it assigns corresponding location name to new 852 $c.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager holdingsLocationName(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering holdingsLocationName normalization step.");

		// Get dataFiled with tag=852
		List<Element> dataFields = marcXml.getDataFields("852");
		
		// Lopp through the 852
		for(Element dataField:dataFields) {
			
			// Get all $b for that field
			List<Element> subFieldsB = marcXml.getSubfieldsOfField(dataField, 'b');
			
			// Loop through the $b values
			for (Element subFieldB:subFieldsB) {
				int index = dataField.indexOf(subFieldB);
				String limitName = locationNameProperties.getProperty(subFieldB.getText().replace(' ', '_'), null);
				
				// If there was no mapping for the provided 852 $b, we can't set the value.  In this case do nothing and start looking at next $b
				if(limitName == null)
				{
					continue;
				}

				if(LOG.isDebugEnabled())
					LOG.debug("Found the location " + limitName + " for the 852 $b value of " + subFieldB.getText() + ".");

				// Add the $c subfield to the MARC XML field 852
				Element newSubfieldC = new Element("subfield", marcNamespace);
				newSubfieldC.setAttribute("code", "c");
				newSubfieldC.setText(limitName);
			
				// Add the $c subfield to the new datafield
				dataField.addContent("\n\t").addContent(index + 1, newSubfieldC).addContent("\n");
			}
	    }

		return marcXml;
	}
	
	/**
	 * For the location code in 852 $b, it assigns corresponding location limit name to 852 $b.
	 * In case of more than 1 limit name exist then put each limit name in separate $b within same 852 
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager locationLimitName(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering locationLimitName normalization step.");

		// Get dataFiled with tag=852
		List<Element> dataFields = marcXml.getDataFields("852");
		
		// Lopp through the 852
		for(Element dataField:dataFields) {
			
			// Get all $b for that field
			List<Element> subFieldsB = marcXml.getSubfieldsOfField(dataField, 'b');
			
			// Loop through the $b values
			for (Element subFieldB:subFieldsB) {
				String limitNames = locationLimitNameProperties.getProperty(subFieldB.getText().replace(' ', '_'), null);
				
				// If there was no mapping for the provided 852 $b, we can't set the value.  In this case do nothing and start looking at next $b
				if(limitNames == null)
				{
					continue;
				}

				if(LOG.isDebugEnabled())
					LOG.debug("Found the location " + limitNames + " for the 852 $b value of " + subFieldB.getText() + ".");

				// Limit names are delimited by |
				StringTokenizer tokenizer = new StringTokenizer(limitNames, "|");
				
				int tokenCount = 1;
				while (tokenizer.hasMoreTokens()) {
					String limitName = tokenizer.nextToken().trim();
					
					if (tokenCount ==1) {
						subFieldB.setText(limitName);
					} else {
						// Add the $b subfield to the MARC XML field 852
						Element newSubfieldB = new Element("subfield", marcNamespace);
						newSubfieldB.setAttribute("code", "b");
						newSubfieldB.setText(limitName);
						
						// Add the $b subfield to the new datafield
						dataField.addContent("\n\t").addContent(newSubfieldB).addContent("\n");
					}
					tokenCount++;
					
				}
			}
		}


		return marcXml;
	}
	
	/**
	 * Replaces the location code in 945 $l with the name of the location it represents.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager IIILocationName(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering IIILocationName normalization step.");

		// The 945 $l value
		ArrayList<String> field945subfieldLs = marcXml.getField945subfieldLs();

		// Pull the location mapping from the configuration file based on the 852 $l value.
	    for(String field945subfieldL : field945subfieldLs)
	    {
			String location = locationNameProperties.getProperty(field945subfieldL.replace(' ', '_'), null);

			// If there was no mapping for the provided 945 $l, we can't create the field.  In this case return the unmodified MARCXML
			if(location == null)
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Cannot find a location mapping for the 945 $l value of " + field945subfieldL + ", returning the unmodified MARCXML.");

				return marcXml;
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Found the location " + location + " for the 945 $l value of " + field945subfieldL + ".");

			// Set the 945 $l value to the location we found for the location code.
			marcXml.setMarcXmlSubfield("945", "l", location, field945subfieldL);
	    }

		return marcXml;
	}
	
	/**
	 * Removes 945 field if there is no $5 field with organization code
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	@SuppressWarnings("unchecked")
	private MarcXmlManager remove945Field(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering remove945Field normalization step.");
		
		// The 945 $l value
		List<Element> field945s = marcXml.getField945();

	    for(Element field945 : field945s)
	    {
	    	// Get the subfields of the current element
			List<Element> subfields = field945.getChildren("subfield",marcNamespace);

			boolean remove945 = true;
			
			// Check if $5 is present as a subfield with institution code
			for (Element subfield : subfields) {
				
				if(subfield.getAttribute("code").getValue().equals("5") &&
						subfield.getText().equals(getOrganizationCode())) {
				
					// Remove this field
					remove945 = false;
				}
			}
			
			if (remove945) {
				marcXml.remove945(field945);
				
			}
	    }

		return marcXml;
	}
	
	/**
	 * Copies relevant fields from the 651 datafield into 9xx fields.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager seperateName(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering SeperateName normalization step.");

		// A list of the tags to copy
		ArrayList<String> tagsToCopy = new ArrayList<String>();
		tagsToCopy.add("700");
		tagsToCopy.add("710");
		tagsToCopy.add("711");

		// Copy the fields, but only if they contain a $t subfield
		marcXml.seperateNames(tagsToCopy, NormalizationServiceConstants.FIELD_9XX_SEPERATE_NAME);

		return marcXml;
	}
	
	/**
	 * Create a 246 field without an initial article whenever a 245 exists with an initial article.
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager titleArticle(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering TitleArticle normalization step.");

		// Get dataFiled with tag=245
		List<Element> dataFields = marcXml.getDataFields("245");
		
		for(Element dataField:dataFields) {

			// Execute step only if 1st indicator is 1 & 2nd indicator is not 0 for tag=245
			if (marcXml.getIndicatorOfField(dataField, "1").equals("1") && !marcXml.getIndicatorOfField(dataField, "2").equals("0") 
						&& !marcXml.getIndicatorOfField(dataField, "2").equals(" ") && !marcXml.getIndicatorOfField(dataField, "2").equals("")) {
				
				// Create field 246
				marcXml.copyMarcXmlField("245", "246", "anpf", "3", "0", true);
				// Deduplicate 246
				marcXml.deduplicateMarcXmlField("246");
			}	
		}
		
		return marcXml;
	}

	/**
	 * Removes duplicate 959, 963, 965, 967, and 969 fields
	 *
	 * @param marcXml The original MARCXML record
	 * @return The MARCXML record after performing this normalization step.
	 */
	private MarcXmlManager dedup9XX(MarcXmlManager marcXml)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Entering Dedup9XX normalization step.");

		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_CHRON_SPLIT);
		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_TOPIC_SPLIT);
		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_GEOG_SPLIT);
		marcXml.deduplicateMarcXmlField(NormalizationServiceConstants.FIELD_9XX_GENRE_SPLIT);
		marcXml.deduplicateMarcXml959Field();

		return marcXml;
	}

	/**
	 * Returns true if the passed language is valid and false otherwise.  The following languages are invalid:
	 * "mul", "N/A", "xxx", "und", "   ", and languages with more or less than three characters
	 *
	 *
	 * @param language The language code we're testing
	 * @return true if the passed language is valid and false otherwise.
	 */
	private boolean isLanguageValid(String language)
	{
		String languageLower = (language != null ? language.toLowerCase() : null);

		return (languageLower != null && languageLower.length() == 3 && !languageLower.contains("|")&& !languageLower.equals("mul") && !languageLower.equals("n/a") && !languageLower.equals("xxx") && !languageLower.equals("und")&& !languageLower.equals("zxx"));
	}

	@Override
	public void loadConfiguration(String configuration)
	{	
		String[] configurationLines = configuration.split("\n");
	
		// The Properties file we're currently populating
		Properties current = null;
		LOG.debug("loadConfiguration: "+this);
		
	    for(String line : configurationLines)
	    {
    		line = line.trim();
    		
	    	// Skip comments and blank lines
	    	if(line.startsWith("#") || line.length() == 0)
	    		continue;
	    	// If the line contains a property, add it to the current Properties Object
	    	else if(line.contains("="))
	    	{
	    		String key = line.substring(0, line.indexOf('=')).trim();
	    		String value = (line.contains("#") ? line.substring(line.indexOf('=')+1, line.indexOf('#')) : line.substring(line.indexOf('=')+1)).trim();
	    		current.setProperty(key, value);
	    	}
	    	// Otherwise check whether the line contains a valid properties heading
	    	else
	    	{
	    		if(line.equals("LOCATION CODE TO LOCATION"))
	    		{
	    			if(locationNameProperties == null)
	    				locationNameProperties = new Properties();
	    			current = locationNameProperties;
	    		}
	    		if(line.equals("LOCATION CODE TO LOCATION LIMIT NAME"))
	    		{
	    			if(locationLimitNameProperties == null)
	    				locationLimitNameProperties = new Properties();
	    			current = locationLimitNameProperties;
	    		}	    		
	    		else if(line.equals("LEADER 06 TO DCMI TYPE"))
	    		{
	    			if(dcmiType06Properties == null)
	    				dcmiType06Properties = new Properties();
	    			current = dcmiType06Properties;
	    		}
	    		else if(line.equals("LEADER 06 TO MARC VOCAB"))
	    		{
	    			if(leader06MarcVocabProperties == null)
	    				leader06MarcVocabProperties = new Properties();
	    			current = leader06MarcVocabProperties;
	    		}
	    		else if(line.equals("LEADER 06 TO FULL TYPE"))
	    		{
	    			if(vocab06Properties == null)
	    				vocab06Properties = new Properties();
	    			current = vocab06Properties;
	    		}
	    		else if(line.equals("LEADER 07 TO MODE OF ISSUANCE"))
	    		{
	    			if(modeOfIssuanceProperties == null)
	    				modeOfIssuanceProperties = new Properties();
	    			current = modeOfIssuanceProperties;
	    		}
	    		else if(line.equals("FIELD 007 OFFSET 00 TO DCMI TYPE"))
	    		{
	    			if(dcmiType0007Properties == null)
	    				dcmiType0007Properties = new Properties();
	    			current = dcmiType0007Properties;
	    		}
	    		else if(line.equals("FIELD 007 OFFSET 00 TO FULL TYPE"))
	    		{
	    			if(vocab007Properties == null)
	    				vocab007Properties = new Properties();
	    			current = vocab007Properties;
	    		}
	    		else if(line.equals("FIELD 007 OFFSET 00 TO SMD TYPE"))
	    		{
	    			if(smdType007Properties == null)
	    				smdType007Properties = new Properties();
	    			current = smdType007Properties;
	    		}
	    		else if(line.equals("LANGUAGE CODE TO LANGUAGE"))
	    		{
	    			if(languageTermProperties == null)
	    				languageTermProperties = new Properties();
	    			current = languageTermProperties;
	    		}
	    		else if(line.equals("FIELD 008 OFFSET 22 TO AUDIENCE"))
	    		{
	    			if(audienceFrom008Properties == null)
	    				audienceFrom008Properties = new Properties();
	    			current = audienceFrom008Properties;
	    		}
	    		else if(line.equals("ENABLED STEPS"))
	    		{
	    			if(enabledSteps == null)
	    				enabledSteps = new Properties();
	    			current = enabledSteps;
	    		}
	    	}
	    }
	}
	
	@Override
	protected void validateService() throws ServiceValidationException 
	{
		if(locationNameProperties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LOCATION CODE TO LOCATION");
		else if(dcmiType06Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LEADER 06 TO DCMI TYPE");
		else if(leader06MarcVocabProperties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LEADER 06 TO MARC VOCAB");
		else if(vocab06Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LEADER 06 TO FULL TYPE");
		else if(modeOfIssuanceProperties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LEADER 07 TO MODE OF ISSUANCE");
		else if(dcmiType0007Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: FIELD 007 OFFSET 00 TO DCMI TYPE");
		else if(vocab007Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: FIELD 007 OFFSET 00 TO FULL TYPE");
		else if(smdType007Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: FIELD 007 OFFSET 00 TO SMD TYPE");
		else if(languageTermProperties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LANGUAGE CODE TO LANGUAGE");
		else if(audienceFrom008Properties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: FIELD 008 OFFSET 22 TO AUDIENCE");
		else if(enabledSteps == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: ENABLED STEPS");
		else if(locationLimitNameProperties == null)
			throw new ServiceValidationException("Service configuration file is missing the required section: LOCATION CODE TO LOCATION LIMIT NAME");

	}
	
	protected String getOrganizationCode() {
		return enabledSteps.getProperty("OrganizationCode");
	}
	
	@Override
	public void setInputRecordCount(int inputRecordCount) {
		this.inputRecordCount = inputRecordCount;
	}
	
}

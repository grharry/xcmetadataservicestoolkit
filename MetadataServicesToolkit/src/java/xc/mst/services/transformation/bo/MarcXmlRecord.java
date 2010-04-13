/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.services.transformation.bo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.jconfig.Configuration;
import org.jconfig.ConfigurationManager;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import xc.mst.constants.Constants;
import xc.mst.utils.MSTConfiguration;


/**
 * This class contains methods to add, update, and get the values of various MARC XML fields.
 *
 * @author Eric Osisek
 */
public class MarcXmlRecord
{
	/**
	 * The logger object
	 */
	protected static Logger log = Logger.getLogger(Constants.LOGGER_PROCESSING);

	/**
	 * The namespace for MARC XML
	 */
	protected static Namespace marcNamespace = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

	/**
	 * The MARC XML Document we're managing
	 */
	protected Document marcXml = null;

	/**
	 * The value of the leader field
	 */
	protected String leader = null;
	
	/** Organization code */
	protected String orgCode;

	/**
	 * A map whose keys are MARCXML tags and whose values are a list of elements with a given tag
	 */
	private HashMap<String, List<Element>> tagToFields = new HashMap<String, List<Element>>();

	/**
	 * A map whose keys are Strings with the format <tag>$<subfield> and whose values are a
	 * list of values for the given subfield/tag pair
	 */
	private HashMap<String, List<String>> tagSubfieldsToValues = new HashMap<String, List<String>>();

	/**
	 * A map whose keys are MARCXML tags and whose values are a list of 880
	 * fields whose $6 equals that tag.
	 */
	private HashMap<String, List<Element>> tagTo880s = new HashMap<String, List<Element>>();

	/**
	 * Gets the leader field
	 *
	 * @return the MARC XML's leader
	 */
	public String getLeader() { return leader; }

	/**
	 * An Object used to read properties from the configuration file for the Metadata Services Toolkit
	 */
	protected static final Configuration configuration = ConfigurationManager.getConfiguration();

	/**
	 * Constructs a MarcXmlRecord based on a MARC XML record.
	 *
	 * @param marcXml The MARC XML record we're managing
	 */
	public MarcXmlRecord(Document marcXml)
	{
		this.marcXml = marcXml;
		// Get the MARC XML's leader
		leader = this.marcXml.getRootElement().getChildText("leader", marcNamespace);

		if(log.isDebugEnabled())
			log.debug("Found the value of the leader to be " + leader + ".");

		
		// Get the ORG code from the 035 field
		if(getControlField("003") != null) {
			orgCode = getControlField("003");
		} else if(getControlField("035") != null) {
			orgCode = getControlField("035");
		} else {
			orgCode = "";
		}
		
		initializeMarcDataFields();

		// Get the 880 fields so we can set up the tag to 880 map.
		List<Element> field880s = getDataFields("880");

		// Populate the tag to 880s map
		for(Element field880 : field880s)
		{
			List<String> tags = getSubfieldOfField(field880, '6');

			for(String tag : tags)
			{
				String tagValue = tag.substring(0, 3);

				if(!tagTo880s.containsKey(tagValue))
					tagTo880s.put(tagValue, new ArrayList<Element>());

				tagTo880s.get(tagValue).add(field880);
			}
		}
	} // end constructor

	/**
	 * Gets the value of a MARC XML control field
	 *
	 * @param targetField The control field to retrieve (for example, "008")
	 * @return The value of the requested control field
	 */
	@SuppressWarnings("unchecked")
	public String getControlField(String targetField)
	{
		try
		{
			if(log.isDebugEnabled())
				log.debug("Getting the control field " + targetField);

			// An XPATH expression to get the requested control field
			XPath xpath = XPath.newInstance("//marc:controlfield[@tag='" + targetField + "']");
			xpath.addNamespace(marcNamespace);

			// Get the control field.  There should not be more than one Element in this list.
			List<Element> elements = xpath.selectNodes(marcXml);

			if(elements.size() == 0)
			{
				if(log.isDebugEnabled())
					log.debug("The " + targetField + " control field did not exist in the MARC XML record.");

				return null;
			}
			else
			{
				// The value of the requested control field
				String value = elements.get(0).getText();

				if(log.isDebugEnabled())
					log.debug("The " + targetField + " control field had a value of " + value + ".");

				return value;
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting control field " + targetField);
			return null;
		}
	}

	/**
	 * Gets all MARC XML data fields with a given tag
	 *
	 * @param targetField The tag of the data fields to retrieve (for example, "035")
	 * @return A list of all data fields with the requested tag
	 */
	@SuppressWarnings("unchecked")
	public List<Element> getDataFields(String targetField)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the " + targetField + " fields.");

		try
		{
			// Get the data fields.  If the target field was not a 9xx field we can return the entire
			// list, otherwise we need to filter out those results with the wrong organization code.
			if(!targetField.startsWith("9"))
			{
				List<Element> results = null;

				if(tagToFields.containsKey(targetField))
					results = tagToFields.get(targetField);
				else
				{
					// An XPATH expression to get the requested control field
					XPath xpath = XPath.newInstance("//marc:datafield[@tag='" + targetField + "']");
					xpath.addNamespace(marcNamespace);
					results = xpath.selectNodes(marcXml);
				}

				// Get the 880 fields that match the requested tag
				if(tagTo880s.containsKey(targetField))
					results.addAll(tagTo880s.get(targetField));

				return results;
			}
			else
			{
				List<Element> potentialResults = null;
				
				if(tagToFields.containsKey(targetField)) {
					potentialResults = tagToFields.get(targetField);
				}
				else
				{
					// An XPATH expression to get the requested control field
					XPath xpath = XPath.newInstance("//marc:datafield[@tag='" + targetField + "']");
					xpath.addNamespace(marcNamespace);
					potentialResults = xpath.selectNodes(marcXml);
				}
				ArrayList<Element> results = new ArrayList<Element>();

				// Get the 880 fields that match the requested tag
				if(tagTo880s.containsKey(targetField)) {
					potentialResults.addAll(tagTo880s.get(targetField));
				}
				
				for(Element potentialResult : potentialResults) {
					if(getSubfieldOfField(potentialResult, '5').contains(orgCode)) {
						results.add(potentialResult);
					}
				}
				return results;
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the " + targetField + " fields.", e);
			return new ArrayList<Element>();
		}
	}
	
	/**
	 * Gets data field 945. Separate method is used for 945 because it needs to return 
	 * field 945 irrespective of $5 subfield value being organization code.
	 *
	 * @return A list of all data fields with the requested tag
	 */
	@SuppressWarnings("unchecked")
	public List<Element> get945()
	{
		if(log.isDebugEnabled())
			log.debug("Getting the " + 945 + " fields.");

		try
		{
			// An XPATH expression to get the requested control field
			XPath xpath = XPath.newInstance("//marc:datafield[@tag='" + 945 + "']");
			xpath.addNamespace(marcNamespace);

			return xpath.selectNodes(marcXml);
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the " + 945 + " fields.", e);
			return new ArrayList<Element>();
		}
	}

	/**
	 * Gets the values of subfields of a MARC XML data fields matching the specified credentials
	 *
	 * @param targetField The tag of the data fields to retrieve subfields of (for example, "035")
	 * @param targetSubfield The subfield code of the subfields to retrieve (for example "a" to retrieve the $a subfield)
	 * @return A list of the values of all subfields matching the target field and target subfield
	 */
	public List<String> getSubfield(String targetField, char targetSubfield)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the " + targetField + " $" + targetSubfield + " subfields' values.");
		return tagSubfieldsToValues.get(targetField + "$" + targetSubfield);
	}

	/**
	 * Given an Element containing a MARC XML datafield, return the value of the specified subfield of that Element
	 *
	 * @param datafield The Element we're getting the subfield of
	 * @param subfield The subfield to get
	 * @return The value of the requested subfield of the datafield
	 */
	@SuppressWarnings("unchecked")
	public static List<String> getSubfieldOfField(Element datafield, char subfield)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the " + subfield + " of the passed datafield.");

		// Holds the results
		ArrayList<String> results = new ArrayList<String>();

		try
		{
			// An XPATH expression to get the requested subfields
			XPath xpath = XPath.newInstance("marc:subfield[@code='" + subfield + "']");
			xpath.addNamespace(marcNamespace);

			// Get the subfields.
			List<Element> elements = xpath.selectNodes(datafield);

			// Return the empty list if there were no matching subfields
			if(elements.size() == 0)
			{
				if(log.isDebugEnabled())
					log.debug("The passed datafield did not have a $" + subfield + " subfield.");

				return results;
			}
			else
			{
				// Loop over the elements with the correct field and subfield, and add value of
				// each to the list of results
				for(Element element : elements)
				{
					// The value of the requested control field
					String value = element.getText();

					if(log.isDebugEnabled())
						log.debug("Found a $" + subfield + " subfield with a value of " + value + ".");

					results.add(value);
				}

				return results;
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the $" + subfield + " subfields of the passed datafields.", e);
			return results;
		}
	}

	/**
	 * Given an Element containing a MARC XML datafield, return the value of the specified subfield of that Element
	 *
	 * @param datafield The Element we're getting the subfield of
	 * @return The value of the requested subfield of the datafield
	 */
	@SuppressWarnings("unchecked")
	public static Element getSiblingOfField(Element datafield)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the sibling of the passed datafield.");

		try
		{
			// An XPATH expression to get the requested subfields
			XPath xpath = XPath.newInstance("following-sibling::*[1]");

			// Get the subfields.
			List<Element> elements = xpath.selectNodes(datafield);

			// Return the empty list if there were no matching subfields
			if(elements.size() == 0)
			{
				if(log.isDebugEnabled())
					log.debug("The passed datafield did not have a sibling.");

				return null;
			}
			else
			{
				if(log.isDebugEnabled())
					log.debug("Found the sibling of the passed datafield.");

				return elements.get(0);
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the sibling of the passed datafields.", e);
			return null;
		}
	}

	/**
	 * Given an Element containing a MARC XML datafield, return the value of the specified subfield of that Element
	 *
	 * @param datafield The Element we're getting the subfield of
	 * @param subfield The subfield to get
	 * @return The value of the requested subfield of the datafield
	 */
	@SuppressWarnings("unchecked")
	public static List<String> getSiblingOfSubfield(Element datafield, char subfield)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the " + subfield + " sibling of the passed subfield.");

		// Holds the results
		ArrayList<String> results = new ArrayList<String>();

		try
		{
			// An XPATH expression to get the requested subfields
			XPath xpath = XPath.newInstance("../marc:subfield[@code='" + subfield + "']");
			xpath.addNamespace(marcNamespace);

			// Get the subfields.
			List<Element> elements = xpath.selectNodes(datafield);

			// Return the empty list if there were no matching subfields
			if(elements.size() == 0)
			{
				if(log.isDebugEnabled())
					log.debug("The passed datafield did not have a $" + subfield + " subfield.");

				return results;
			}
			else
			{
				// Loop over the elements with the correct field and subfield, and add value of
				// each to the list of results
				for(Element element : elements)
				{
					// The value of the requested control field
					String value = element.getText();

					if(log.isDebugEnabled())
						log.debug("Found a $" + subfield + " subfield with a value of " + value + ".");

					results.add(value);
				}

				return results;
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the $" + subfield + " subfields of the passed datafields.", e);
			return results;
		}
	}

	/**
	 * Given an Element containing a MARC XML datafield, return the value of the specified indicator of that Element
	 *
	 * @param datafield The Element we're getting the indicator of
	 * @param indicator The indicator to get
	 * @return The value of the requested indicator of the datafield
	 */
	@SuppressWarnings("unchecked")
	public static String getIndicatorOfField(Element datafield, String indicator)
	{
		if(log.isDebugEnabled())
			log.debug("Getting the ind" + indicator + " of the passed datafield.");

		try
		{
			// An XPATH expression to get the requested indicator
			XPath xpath = XPath.newInstance("@ind" + indicator);
			xpath.addNamespace(marcNamespace);

			// Get the subfields.
			List<Attribute> attributes = xpath.selectNodes(datafield);

			// Return the empty list if there were no matching subfields
			if(attributes.size() == 0)
			{
				if(log.isDebugEnabled())
					log.debug("The passed datafield did not have a ind" + indicator + ".");

				return null;
			}
			else
			{
				// The value of the requested control field
				String value = attributes.get(0).getValue();

				if(log.isDebugEnabled())
					log.debug("Found a ind" + indicator + " with a value of " + value + ".");

				return value;
			}
		}
		catch(JDOMException e)
		{
			log.error("An error occurred getting the ind" + indicator + " of the passed datafield.", e);
			return null;
		}
	}

	/**
	 * Initializes the MARC XML data fields' cached values.
	 */
	@SuppressWarnings("unchecked")
	private void initializeMarcDataFields()
	{
		if(log.isDebugEnabled())
			log.debug("Initializing MARC XML data fields.");

		// Get the data fields
		List<Element> fields = marcXml.getRootElement().getChildren("datafield", marcNamespace);

		// Iterate over the fields and find the one with the correct tag
		for(Element field : fields)
		{
			String tag = field.getAttributeValue("tag");

			// If the tag is "9XX", ignore it unless it has the correct organization code
			if(tag.startsWith("9"))
			{
				List<String> orgCodes = getSubfieldOfField(field, '5');

				if(!orgCodes.contains(orgCode))
					continue;
			}

			// If the tag is "880", treat it like the tag from its $6 subfield
			if(tag.equals("880"))
			{
				List<String> realTags = getSubfieldOfField(field, '6');

				if(realTags.size() > 0)
					tag = realTags.get(0).substring(0, 3);
			}

			if(!tagToFields.containsKey(tag))
				tagToFields.put(tag, new ArrayList<Element>());

			List<Element> subfields = field.getChildren("subfield", marcNamespace);

			tagToFields.get(tag).add(field);

			for(Element subfield : subfields)
			{
				String tagAndCode = tag + "$" + subfield.getAttributeValue("code");
				if(!tagSubfieldsToValues.containsKey(tagAndCode))
					tagSubfieldsToValues.put(tagAndCode, new ArrayList<String>());

				tagSubfieldsToValues.get(tagAndCode).add(subfield.getText());
			}
		} // end loop over data fields
	} // end method initializeMarcDataFields

	public String getOrgCode() {
		return orgCode;
	}

	public void setOrgCode(String orgCode) {
		this.orgCode = orgCode;
	}
}

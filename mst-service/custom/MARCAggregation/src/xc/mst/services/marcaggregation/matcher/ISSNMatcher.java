package xc.mst.services.marcaggregation.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import xc.mst.bo.record.InputRecord;
import xc.mst.bo.record.SaxMarcXmlRecord;
import xc.mst.bo.record.marc.Field;
import xc.mst.services.marcaggregation.MarcAggregationService;
import xc.mst.services.marcaggregation.dao.MarcAggregationServiceDAO;
import xc.mst.utils.Util;

/**
 *
 * The Library of Congress Control Number corresponds to the
 * <a href="http://www.loc.gov/marc/bibliographic/bd022.html">MARC 022 field</a>
 *
 * 022$a
 *
 * Step 2. Exact match on multiple identifiers.
 * This step allows the service to consider multiple combinations of identifiers,
 * when each of the identifiers on its own is not considered reliable enough to be used alone.
 * If the content of ALL of the fields listed within one group of identifiers in an incoming record
 * (as defined in the Configuration File) matches the content of the same fields in an existing record,
 * then the records are to be considered a MATCH. If a match is not found for Step A,
 * the service would attempt Step B, and so forth. The default setting would use the following steps:
 *
    Step 2B:
        LCCN: 010a (current LCCN) AND
        ISSN: 022a (current ISSN)
 *
 *022  (R)    $a (NR) - International Standard Serial Number
           Example: 022      ##$a0376-4583
           Note the '-' between the groups of digits.  All examples showed this,
           will it always be there?  Answer:  It should always be there, unless someone enters the data incorrectly.
           However, the eight numbers are always considered as a single identifier, never as two sets of four.
           The dash is just a display convention to make the number easier to read.

 * For the matching itself it would be okay to strip extraneous characters like dashes,
 * and just do an integer comparison, but if it used as a record of source, it would need to be kept intact,
 * so must store it as a string if it is a string for use as source record data.
 *
 * but, note, the int can contain the char value 'X' so don't bother:
 *           <marc:datafield tag="022" ind1="0" ind2=" ">
            <marc:subfield code="a">0738-100X</marc:subfield>

 *
 * @author JohnB
 *
 */
public class ISSNMatcher extends FieldMatcherService {

    // you can have multiple 022$a fields within a record (mult 022, each w/1 $a)
    // thus use a list of string pairs, the pair values are the original string
    //    and the normalized string
    // 8 digit number so thought Integer would cover it.
    //    But can include 'X' at end so must use String.
    //
    // TODO, to make this perform better could 1st see if match on the integer version
    //
    // note, orig saved orig string too, now I don't see point.
    //
    protected Map<Long, List<String>> inputId2issn = new HashMap<Long, List<String>>();

    // multiple records might have the same normalized 022$a, this would be an indication of a match
    protected Map<String, List<Long>> issn2inputIds = new HashMap<String, List<Long>>();

    private static final Logger LOG = Logger.getLogger(ISSNMatcher.class);

    private boolean debug = false;
    private boolean debug2 = false;
    
    MarcAggregationService mas = null;

    public static String getAllButDash(final String s) {
        String stripped = s.replaceAll("[-]", "");
        return stripped;
    }
    
    private MarcAggregationService getMAS() {
		if (mas == null) {
			mas = (MarcAggregationService)config.getBean("MarcAggregationService");
		}
		return mas;
	}


    @Override
    // return all matching records!!! a match means the same int part of issn.
    public List<Long> getMatchingInputIds(SaxMarcXmlRecord ir) {

        MarcAggregationServiceDAO masDao = getMAS().getMarcAggregationServiceDAO();

        ArrayList<Long> results = new ArrayList<Long>();
        List<Field> fields = ir.getDataFields(22);

        final Long id = new Long(ir.recordId);
        for (Field field : fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');

            for (String subfield : subfields) {
                String issn = getAllButDash(subfield);
                List<Long> m = issn2inputIds.get(issn);
                if (m != null && m.size() > 0) {
                    results.addAll(m);
                    if (results.contains(id)) {
                        results.remove(id);
                    }
                }

                // now look in the database too!
                //mysql -u root --password=root -D xc_marcaggregation -e 'select input_record_id  from matchpoints_022a where string_id = "24094664" '
                List<Long> records = masDao.getMatchingRecords(MarcAggregationServiceDAO.matchpoints_022a_table, MarcAggregationServiceDAO.input_record_id_field,MarcAggregationServiceDAO.string_id_field,issn);
                LOG.debug("ISSN, DAO, getMatching records for "+issn+", numResults="+records.size());
                for (Long record: records) {
                    if (!record.equals(id)) {
                        if (!results.contains(record)) {
                            results.add(record);
                            LOG.debug("**ISSN, DAO,  record id: "+record +" matches id "+id);
                        }
                    }
                }
            }
        }
        LOG.debug("getMatchingInputIds, irId=" + ir.recordId + " results.size=" + results.size());
        return results;
    }

    @Override
    /**
     * when a record is updated/deleted, need to use this to
     */
    public void removeRecordFromMatcher(InputRecord ir) {
        Long id   = new Long(ir.getId());
        List<String> issns = inputId2issn.get(id);
        if (issns != null) {
            for (String issn: issns) {
                List<Long> inputIds = issn2inputIds.get(issn);
                if (inputIds != null) {
                    inputIds.remove(id);
                    if (inputIds.size() > 0) {
                        issn2inputIds.put(issn, inputIds);
                    }
                    else {
                        issn2inputIds.remove(issn);
                    }
                }
            }
        }
        inputId2issn.remove(id);

        // keep database in sync.  Don't worry about the one-off performance hit...yet.
        getMAS().getMarcAggregationServiceDAO().deleteMergeRow(MarcAggregationServiceDAO.matchpoints_022a_table, id);
    }

    @Override
    public void addRecordToMatcher(SaxMarcXmlRecord r, InputRecord ir) {
        List<Field> fields = r.getDataFields(22);

        for (Field field : fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');
            for (String subfield : subfields) {
                Long id = new Long(r.recordId);
                LOG.debug("here we go, processing subfield: "+subfield+" recordId:"+id+" numSubfields="+subfields.size()+ "numFields="+fields.size());
                if (debug2) {
                    Util.getUtil().printStackTrace("who got me here?");
                }
                String issn = getAllButDash(subfield);
                if (StringUtils.isEmpty(issn)) {
                    LOG.error("** problem with 022$a ISSN in: " + r.recordId);
                    continue;   // bad data will cause trouble up the road.
                }
                List<String> issnList = inputId2issn.get(id);

                if (issnList == null) {
                    issnList = new ArrayList<String>();
                    issnList.add(issn);
                    inputId2issn.put(id, issnList);
                    LOG.debug("*** 1.adding to inputId2issn, for id: " + id + " for issn: " + issn);
                    if (debug) {
                        LOG.info("*** 1.adding to inputId2issn, for id: " + id + " for issn: " + issn);
                    }
                }
                // Just because we have seen it, it is not an error, it just means multiple match rules use this matcher.
                else if (issnList.contains(issn)) {
                    LOG.debug("** We have already seen issn " + issn + " for recordId: " + r.recordId);
                    if (debug) {
                        LOG.info("** We have already seen issn " + issn + " for recordId: " + r.recordId);
                    }
                }
                else {
                    issnList.add(issn);
                    inputId2issn.put(id, issnList);
                    LOG.debug("*** 2.adding to inputId2issn, for id: " + id + " for issn: " + issn);
                    if (debug) {
                        LOG.info("*** 2.adding to inputId2issn, for id: " + id + " for issn: " + issn);
                    }
                }

                List<Long> ids = issn2inputIds.get(issn);
                if (ids == null) {
                    ids = new ArrayList<Long>();
                }
                if (!ids.contains(r.recordId)) {
                    ids.add(r.recordId);
                    issn2inputIds.put(issn, ids);
                    LOG.debug("*** adding to issn2recordIds, for issn: " + issn);
                    if (debug) {
                        LOG.info("*** adding to issn2recordIds, for issn: " + issn);
                    }
                }
                // Just because we have seen it, it is not an error, it just means multiple match rules use this matcher.
                else {
                    LOG.debug("** We have already seen recordId: " + r.recordId);
                    if (debug) {
                        LOG.info("** We have already seen recordId: " + r.recordId);
                    }
                }
            }
        }
    }
    
    public boolean matchpointsHaveChanged(SaxMarcXmlRecord r, InputRecord ir) {
    	LOG.debug("issn matchpointsHaveChanged? ID: " + ir.getId()); 
    	Map<Long, List<String>> cachedListId2issn = getMAS().getMarcAggregationServiceDAO().get1StrMatchpointsRecordsCache(Long.valueOf(ir.getId()), MarcAggregationServiceDAO.matchpoints_022a_table);
    	LOG.debug("cachedListId2isbn: " + cachedListId2issn);    	
    	    	
    	List<String> cachedId2issn = new ArrayList<String>();
    	if (cachedListId2issn.containsKey(ir.getId())) {
    		cachedId2issn = cachedListId2issn.get(ir.getId());
    		LOG.debug("cachedId2issn: " + cachedId2issn);  
    	}
    	
        List<String> thisId2issn = new ArrayList<String>();
        
        List<Field> fields = r.getDataFields(22);
        for (Field field : fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');
            for (String subfield : subfields) {
                String issn = getAllButDash(subfield);
                if (StringUtils.isEmpty(issn)) {
                    continue;   // bad data will cause trouble up the road.
                }
                LOG.debug("adding thisId2issn: " + issn);  
                thisId2issn.add(issn);
            }
        }
        LOG.error("gonna compare cachedId2issn: " + cachedId2issn + "  ...with... thisId2issn: " + thisId2issn);
        
        Set<String> setA = new HashSet<String>(cachedId2issn);
        Set<String> setB = new HashSet<String>(thisId2issn);
        boolean same = setA.containsAll(thisId2issn) && setB.containsAll(cachedId2issn);
	        
       	return (! same);        
    }

    @Override
    public void load(boolean firstTime) {
        // TODO Auto-generated method stub

    }

    @Override
    public void flush(boolean freeUpMemory) {
        getMAS().getMarcAggregationServiceDAO().persist1StrMatchpointMaps(inputId2issn, MarcAggregationServiceDAO.matchpoints_022a_table);
        inputId2issn.clear();
        issn2inputIds.clear();
    }

    public Collection<Long> getRecordIdsInMatcher() {
        return inputId2issn.keySet();
    }

    /**
     * For testing.
     * @return
     */
    public int getNumRecordIdsInMatcher() {
        //return inputId2issn.size();

        MarcAggregationService s = getMAS();
        LOG.debug("** 022 matcher contains "+s.getMarcAggregationServiceDAO().getNumUniqueRecordIds(MarcAggregationServiceDAO.matchpoints_022a_table)+ " unique records in dB & "+inputId2issn.size() +" records in mem.");
        return s.getMarcAggregationServiceDAO().getNumUniqueRecordIds(MarcAggregationServiceDAO.matchpoints_022a_table);
    }

    /**
     * For testing.
     * @return
     */
    public int getNumMatchPointsInMatcher() {
        //return issn2inputIds.size();

        MarcAggregationService s = getMAS();
        LOG.debug("** 022 matcher contains "+s.getMarcAggregationServiceDAO().getNumUniqueStringIds(MarcAggregationServiceDAO.matchpoints_022a_table)+ " unique strings in dB & "+issn2inputIds.size() +" strs in mem.");
        return s.getMarcAggregationServiceDAO().getNumUniqueStringIds(MarcAggregationServiceDAO.matchpoints_022a_table);
    }

}

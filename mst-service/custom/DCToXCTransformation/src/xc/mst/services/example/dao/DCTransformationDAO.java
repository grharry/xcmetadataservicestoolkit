/**
  * Copyright (c) 2010 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */
package xc.mst.services.example.dao;

//import gnu.trove.TLongArrayList;
import gnu.trove.TLongLongHashMap;
import gnu.trove.TLongLongProcedure;
//import gnu.trove.TLongProcedure;

import java.sql.PreparedStatement;
import java.sql.SQLException;
//import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
//import org.springframework.jdbc.core.PreparedStatementSetter;

//import xc.mst.bo.record.Record;
//import xc.mst.dao.BaseDAO;
//import xc.mst.repo.RepositoryDAO;
import xc.mst.services.impl.dao.GenericMetadataDAO;
import xc.mst.utils.TimingLogger;

/**
 * 
 * @author Benjamin D. Anderson
 *
 */
public class DCTransformationDAO extends GenericMetadataDAO {
	
	private final static Logger LOG = Logger.getLogger(DCTransformationDAO.class);
	
	protected final static String bibsProcessedLongId_table = "bibsProcessedLongId";
	protected final static String bibsProcessedStringId_table = "bibsProcessedStringId";
	protected final static String bibsYet2ArriveLongId_table = "bibsYet2ArriveLongId";
	protected final static String bibsYet2ArriveStringId_table = "bibsYet2ArriveStringId";
	
	@SuppressWarnings("unchecked")
	public void loadBibMaps	(
			TLongLongHashMap bibsProcessedLongId, 
			Map<String, Long> bibsProcessedStringId,
			TLongLongHashMap bibsYet2ArriveLongId,
			Map<String, Long> bibsYet2ArriveStringId ) {
		
		Object[] objArr = new Object[] {
				bibsProcessedLongId, bibsProcessedLongId_table,
				bibsProcessedStringId, bibsProcessedStringId_table,
				bibsYet2ArriveLongId, bibsYet2ArriveLongId_table,
				bibsYet2ArriveStringId, bibsYet2ArriveStringId_table};
		
		for (int i=0; i<objArr.length; i+=2){
			String tableName = (String)objArr[i+1];
			String sql = "select bib_001, record_id from " +tableName;
			List<Map<String, Object>> results = this.jdbcTemplate.queryForList(sql);
			if (results != null) {
				for (Map<String, Object> row : results) {
					if (objArr[i] instanceof TLongLongHashMap) {
						((TLongLongHashMap)objArr[i]).put((Long)row.get("bib_001"), (Long)row.get("record_id"));
					} else if (objArr[i] instanceof Map) {
						((Map)objArr[i]).put((String)row.get("bib_001"), (Long)row.get("record_id"));
					}		
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void persistBibMaps(
			TLongLongHashMap bibsProcessedLongId, 
			Map<String, Long> bibsProcessedStringId,
			TLongLongHashMap bibsYet2ArriveLongId,
			Map<String, Long> bibsYet2ArriveStringId ) {
		
		// TODO: probably change these string literals to the above statics
		Object[] objArr = new Object[] {
				bibsProcessedLongId, bibsProcessedLongId_table,
				bibsProcessedStringId, bibsProcessedStringId_table,
				bibsYet2ArriveLongId, bibsYet2ArriveLongId_table,
				bibsYet2ArriveStringId, bibsYet2ArriveStringId_table};
		
		for (int i=0; i<objArr.length; i+=2){
			String tableName = (String)objArr[i+1];
			final List<Object[]> params = new ArrayList<Object[]>();
			if (objArr[i] instanceof TLongLongHashMap) {
				TLongLongHashMap longKeyedMap = (TLongLongHashMap)objArr[i];
				if (longKeyedMap != null && longKeyedMap.size() > 0) {
					longKeyedMap.forEachEntry(new TLongLongProcedure() {
						public boolean execute(long key, long value) {
							params.add(new Object[] {key, value});
							return true;
						}
					});		
				}
			} else if (objArr[i] instanceof Map) {
				Map<String, Long> stringKeyedMap = (Map<String, Long>)objArr[i];
				if (stringKeyedMap != null && stringKeyedMap.size() > 0) {
					for (Map.Entry<String, Long> me : stringKeyedMap.entrySet()) {
						params.add(new Object[] {me.getKey(), me.getValue()});
					}
				}
			}
			
			if (params.size() > 0) {
				TimingLogger.start(tableName+".insert");
				String sql =
					"insert ignore into "+tableName+
					" (bib_001, record_id) "+
					"values (?,?) ;";
		        int[] updateCounts = this.simpleJdbcTemplate.batchUpdate(sql, params);
		        
		        TimingLogger.stop(tableName+".insert");	
			}
		}
		
	}
}

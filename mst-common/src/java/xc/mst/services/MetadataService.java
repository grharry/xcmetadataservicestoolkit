/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.services;

import java.util.List;

import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Set;
import xc.mst.bo.service.Service;
import xc.mst.constants.Status;
import xc.mst.repo.Repository;

public interface MetadataService {

	public void install();
	
	public void uninstall();
	
	public void update(String currentVersion);
	
	//public void process(Record r);
	
	public Repository getRepository();
	
	/**
	 * This method not only creates new records (inserts), but it can
	 * also update records and delete records.
	 * 
	 * @param repo
	 * @param inputFormat
	 * @param inputSet
	 * @param outputSet
	 */
	public void process(Repository repo, Format inputFormat, Set inputSet, Set outputSet);
	public void pause();
	public void resume();
	public void cancel();
	public void finish();
	public Service getService();
	public void setService(Service service);
	public String getServiceName();
	
	// leftover methods that I think can be eventually deleted
	public void runService(int serviceId, int outputSetId);
	public void setStatus(Status status);
	public boolean sendReportEmail(String problem);
	public void setCanceled(boolean isCanceled);
	public void setPaused(boolean isPaused);
	public Status getServiceStatus();
	public int getProcessedRecordCount();
	public int getTotalRecordCount();
	public List<String> getUnprocessedErrorRecordIdentifiers();
	public void setUnprocessedErrorRecordIdentifiers(List<String> l);

}
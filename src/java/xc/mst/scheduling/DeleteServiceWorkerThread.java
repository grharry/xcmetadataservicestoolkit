package xc.mst.scheduling;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import xc.mst.bo.processing.Job;
import xc.mst.bo.record.Record;
import xc.mst.bo.service.Service;
import xc.mst.constants.Constants;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.manager.IndexException;
import xc.mst.manager.processingDirective.DefaultJobService;
import xc.mst.manager.processingDirective.DefaultServicesService;
import xc.mst.manager.processingDirective.JobService;
import xc.mst.manager.processingDirective.ServicesService;
import xc.mst.manager.record.DefaultRecordService;
import xc.mst.manager.record.RecordService;
import xc.mst.utils.index.RecordList;
import xc.mst.utils.index.SolrIndexManager;

/**
 * A Thread which deletes the service and its records .
 * 
 * @author Sharmila Ranganathan
 */
public class DeleteServiceWorkerThread extends WorkerThread 
{
	/**
	 * A reference to the logger for this class
	 */
	static Logger log = Logger.getLogger(Constants.LOGGER_GENERAL);
	
	/** 
	 * Type of thread 
	 */
	public static final String type = Constants.THREAD_DELETE_SERVICE;

	/**
	 * The flag for indicating cancel service operation.
	 */
	private boolean isCanceled;

	/**
	 * The flag for indicating service operation pause.
	 */
	private boolean isPaused;
	
	/**
	 * The ID of the service to be deleted
	 */
	private int serviceId;
	
	/**
	 * The service to be deleted
	 */
	private Service service;
	
	/**
	 * Manager for getting, inserting and updating records
	 */
	private static RecordService recordService = new DefaultRecordService();
	
	/**
	 * Manager for getting, inserting and updating jobs
	 */
	private static JobService jobService = new DefaultJobService();
	
	/**
	 * Manager for getting, inserting and updating service
	 */
	private static ServicesService serviceManager = new DefaultServicesService();
	
	@Override
	public void run() 
	{
		try
		{
			
	    	service = serviceManager.getServiceById(serviceId);

	    	log.info("Starting thread to delete service " + service.getName());
	    	
	    	// Delete the records processed by the service and send the deleted
	    	// records to subsequent services so they know about the delete
			RecordList records = recordService.getByServiceId(serviceId);

			// A list of services which must be run after this one
			List<Service> affectedServices = new ArrayList<Service>();
			List<Record> updatedPredecessors = new ArrayList<Record>();
			
			for(Record record : records)
			{
				// set as deleted
				record.setDeleted(true);
				
				// Get all predecessors & remove the current record as successor
				List<Record> predecessors =  record.getProcessedFrom();
				for (Record predecessor : predecessors) {
					
					for (Iterator<String> iterator = predecessor.getErrors().iterator(); iterator.hasNext();) {
						
						String error = iterator.next();
						if(error.startsWith(Integer.valueOf(serviceId).toString() + "-"))
							iterator.remove();
					}
					
					int index = updatedPredecessors.indexOf(predecessor);
					if (index < 0) {
						predecessor =  recordService.getById(predecessor.getId());
					} else {
						predecessor = updatedPredecessors.get(index);
					}
					predecessor.removeSucessor(record);

					// Remove the reference for the service which is being deleted from its predecessor
					predecessor.removeProcessedByService(service);
					updatedPredecessors.add(predecessor);
				}
				
				record.setUpdatedAt(new Date());
				for(Service nextService : record.getProcessedByServices())
				{
					record.addInputForService(nextService);
					if(!affectedServices.contains(nextService))
						affectedServices.add(nextService);
				}
				recordService.update(record);
			}
			
			// Update all predecessor records
			for (Record updatedPredecessor:updatedPredecessors) {
				recordService.update(updatedPredecessor);				
			}
			
			SolrIndexManager.getInstance().commitIndex();

			// Schedule subsequent services to process that the record was deleted
			for(Service nextSerivce : affectedServices)
			{
				try {
					Job job = new Job(nextSerivce, 0, Constants.THREAD_SERVICE);
					job.setOrder(jobService.getMaxOrder() + 1); 
					jobService.insertJob(job);
				} catch (DatabaseConfigException dce) {
					log.error("DatabaseConfig exception occured when ading jobs to database", dce);
				}
			}

			serviceManager.deleteService(service);
			
			log.info("Finished deleting service " + service.getName());

		} catch (DataException de) {
			log.error("Exception occured while updating records.", de);
		} catch (IndexException ie) {
			log.error("Exception occured while commiting to Solr index.", ie);
		}
	}

	@Override
	public void cancel() 
	{
		isCanceled = true;
	}

	@Override
	public void pause() 
	{
		isPaused = true;
	}

	@Override
	public void proceed() 
	{
		isPaused = false;
	}

	@Override
	public String getJobName() 
	{
		return "Deleting service and its records";
	}

	@Override
	public String getJobStatus() 
	{
		if(isCanceled)
			return Constants.STATUS_SERVICE_CANCELED;
		else if(isPaused)
			return Constants.STATUS_SERVICE_PAUSED;
		else
			return Constants.STATUS_SERVICE_RUNNING;
	}

	@Override
	public String getType() 
	{
		return type;
	}

	@Override
	public int getProcessedRecordCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getTotalRecordCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	public Service getService() {
		return service;
	}

	public int getServiceId() {
		return serviceId;
	}

	public void setServiceId(int serviceId) {
		this.serviceId = serviceId;
	}
}

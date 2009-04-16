<!--
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the  
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/. 
  *
  -->

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
 <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>


<!--  document type -->
<c:import url="/inc/doctype-frag.jsp"/>

<html>
    <head>
        <title>Browse Records</title>
        <c:import url="/inc/meta-frag.jsp"/>
        
        <LINK href="page-resources/yui/reset-fonts-grids/reset-fonts-grids.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/assets/skins/sam/skin.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/base-mst.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/menu/assets/skins/sam/menu.css"  rel="stylesheet" type="text/css" >       
        <LINK href="page-resources/css/global.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/tables.css" rel="stylesheet" type="text/css" >
		<LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">
		<LINK href="page-resources/css/main_menu.css" rel="stylesheet" type="text/css" >

        <SCRIPT LANGUAGE="JavaScript" SRC="pages/js/base_path.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/utilities.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/yahoo-dom-event/yahoo-dom-event.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/connection/connection-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/container/container-min.js"></SCRIPT>    
    	<SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/element/element-beta-min.js"></script>          
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/menu/menu-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/main_menu.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/button/button-min.js"></script> 
        
        <SCRIPT LANGUAGE="JavaScript" SRC="pages/js/base_path.js"></SCRIPT>
    </head>
    
    <body class="yui-skin-sam">
        <!--  yahoo doc 2 template creates a page 950 pixles wide -->
        <div id="doc2">  

		<!-- page header - this uses the yahoo page styling -->
		<div id="hd">
   
            <!--  this is the header of the page -->
            <c:import url="/inc/header.jsp"/>
            
            <c:import url="/inc/menu.jsp"/>
            <jsp:include page="/inc/breadcrumb.jsp"> 

                    <jsp:param name="bread" value="Browse Records" />

            </jsp:include>
            
 		</div>
		<!--  end header -->
		
		<!-- body -->
		<div id="bd">
   			
				<div class="facet_search_results">
					
					 	<div class="facetContainer">
					 		<c:forEach var="facet" items="${result.facets}">
						       <div class="facetTitle">
							   	<p><strong>
							   	<c:if test="${facet.name == 'format_name'}">
							   		Schema 
							   	</c:if>
								<c:if test="${facet.name == 'set_name'}">
							   		Set 
							   	</c:if>							   	
							   	<c:if test="${facet.name == 'provider_name'}">
							   		Repository 
							   	</c:if>
							   	<c:if test="${facet.name == 'service_name'}">
							   		Service 
							   	</c:if>
<!--							   	
								<c:if test="${facet.name == 'harvest_end_time'}">
							   		Harvest 
							   	</c:if>
-->							   	
							   	<c:if test="${facet.name == 'warning'}">
							   		Warning 
							   	</c:if>
							   	<c:if test="${facet.name == 'error'}">
							   		Error 
							   	</c:if>
							   	
							   	
							   	</strong>
							   		<c:forEach var="filter" items="${result.facetFilters}">
								   		<c:if test="${facet.name == filter.name}">
										  	 <c:url var="removeFacet" value="browseRecords.action">
												  <c:param name="query" value="${query}"/>
												  <c:param name="searchXML" value="${searchXML}"/>
												  <c:param name="removeFacetName" value="${filter.name}"/>
												  <c:param name="removeFacetValue" value="${filter.value}"/>
												  <c:param name="selectedFacetNames" value="${selectedFacetNames}"/>
												  <c:param name="selectedFacetValues" value="${selectedFacetValues}"/>
												  
											  </c:url>
										  	: ${filter.value} (<a href="${removeFacet}">Remove</a>)
								  		</c:if>
								  	</c:forEach>
							   	</p>
						       </div>

						       <div class="facetContent">
								<p>
								<c:forEach var="fcount" items="${facet.values}">
									<c:set var="facetExist" value="false"/>
									<c:forEach var="filter" items="${result.facetFilters}">
										<c:if test="${fcount.name == filter.value}">
											<c:set var="facetExist" value="true"/>
										</c:if>
									</c:forEach>
									
									<c:if test="${facetExist == false}">
											<c:url var="facetFilter" value="browseRecords.action">
												  <c:param name="query" value="${query}"/>
												  <c:param name="searchXML" value="${searchXML}"/>
												  <c:param name="addFacetName" value="${facet.name}"/>
												  <c:param name="addFacetValue" value="${fcount.name}"/>
												  <c:param name="selectedFacetNames" value="${selectedFacetNames}"/>
												  <c:param name="selectedFacetValues" value="${selectedFacetValues}"/>
												  
											 </c:url>

											<a href="${facetFilter}">${fcount.name} (${fcount.count})</a><br/>
									</c:if>
								</c:forEach> 
								</p>
								<br>

						       </div>
						        </c:forEach>
					   	</div>	
					
					</div>
					<!-- facet_search_results  end -->

					<div >	
						<!-- Display of filters -->
						<c:if test="${query != ''}">
							<p class="searched_for">You Searched for : "${query}"<c:if test="${result.facetFilters != '[]'}">, </c:if>
							
<c:forEach var="filter" items="${result.facetFilters}"  varStatus="status"><c:if test="${status.count > 1}">, </c:if><c:if test="${filter.name == 'format_name'}">Schema</c:if><c:if test="${filter.name == 'set_name'}">Set</c:if><c:if test="${filter.name == 'provider_name'}">Repository</c:if><c:if test="${filter.name == 'service_name'}">Service</c:if><!--<c:if test="${filter.name == 'harvest_end_time'}">Harvest</c:if>--><c:if test="${filter.name == 'warning'}">Warning</c:if><c:if test="${filter.name == 'error'}">Error</c:if>:${filter.value}</c:forEach>							
							</p>
						</c:if>
						
						<!-- Display of filters In case of predecessor  - begin-->
						<c:if test="${predecessorRecord != null}">
							<p class="searched_for">You Searched for : All Precedessors of:
									${predecessorRecord.provider.name} ${predecessorRecord.id}
									<br>
									Schema: ${predecessorRecord.format.name}
									<br>
									Provider: ${predecessorRecord.provider.name}	
							       <c:if test="${predecessorRecord.numberOfPredecessors > 0 && predecessorRecord.numberOfSuccessors > 0}">
										${predecessorRecord.numberOfPredecessors} 
										<c:if test="${predecessorRecord.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${predecessorRecord.numberOfPredecessors > 1}">
											Predecessors
										</c:if></a> 
										&nbsp;<img src="page-resources/img/white-book-both.jpg">&nbsp;
										<a href="${viewSuccessorRecord}">${predecessorRecord.numberOfSuccessors} 
										<c:if test="${predecessorRecord.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${predecessorRecord.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a>
								    </c:if>
								    <c:if test="${predecessorRecord.numberOfPredecessors > 0 && predecessorRecord.numberOfSuccessors < 1}">
										${predecessorRecord.numberOfPredecessors} 
										<c:if test="${predecessorRecord.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${predecessorRecord.numberOfPredecessors > 1}">
											Predecessors
										</c:if> 
										</a> 
									      &nbsp;<img src="page-resources/img/white-book-left.jpg">
								    </c:if>
									<c:if test="${predecessorRecord.numberOfSuccessors > 0 && predecessorRecord.numberOfPredecessors < 1}">
										&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
										<img src="page-resources/img/white-book-right.jpg">
										&nbsp;<a href="${viewSuccessorRecord}">${predecessorRecord.numberOfSuccessors} 
										<c:if test="${predecessorRecord.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${predecessorRecord.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a> 
										
								    </c:if>                                    
							<br>								
							<c:forEach var="filter" items="${result.facetFilters}"  varStatus="status"><c:if test="${status.count > 1}">, </c:if><c:if test="${filter.name == 'format_name'}">Schema</c:if><c:if test="${filter.name == 'set_name'}">Set</c:if><c:if test="${filter.name == 'provider_name'}">Repository</c:if><c:if test="${filter.name == 'service_name'}">Service</c:if><!--<c:if test="${filter.name == 'harvest_end_time'}">Harvest</c:if>--><c:if test="${filter.name == 'warning'}">Warning</c:if><c:if test="${filter.name == 'error'}">Error</c:if>:${filter.value}</c:forEach>							
							</p>
						</c:if>
						<!-- Display of filters In case of predecessor - end -->

						<!-- Display of filters In case of successor - begin -->
						<c:if test="${successorRecord != null}">
							<p class="searched_for">You Searched for : All Precedessors of:
									${successorRecord.provider.name} ${successorRecord.id}
									<br>
									Schema: ${successorRecord.format.name}
									<br>
									Provider: ${successorRecord.provider.name}	
							       <c:if test="${successorRecord.numberOfPredecessors > 0 && successorRecord.numberOfSuccessors > 0}">
										${successorRecord.numberOfPredecessors} 
										<c:if test="${successorRecord.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${successorRecord.numberOfPredecessors > 1}">
											Predecessors
										</c:if></a> 
										&nbsp;<img src="page-resources/img/white-book-both.jpg">&nbsp;
										<a href="${viewSuccessorRecord}">${successorRecord.numberOfSuccessors} 
										<c:if test="${successorRecord.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${successorRecord.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a>
								    </c:if>
								    <c:if test="${successorRecord.numberOfPredecessors > 0 && successorRecord.numberOfSuccessors < 1}">
										${successorRecord.numberOfPredecessors} 
										<c:if test="${successorRecord.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${successorRecord.numberOfPredecessors > 1}">
											Predecessors
										</c:if> 
										</a> 
									      &nbsp;<img src="page-resources/img/white-book-left.jpg">
								    </c:if>
									<c:if test="${successorRecord.numberOfSuccessors > 0 && successorRecord.numberOfPredecessors < 1}">
										&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
										<img src="page-resources/img/white-book-right.jpg">
										&nbsp;<a href="${viewSuccessorRecord}">${successorRecord.numberOfSuccessors} 
										<c:if test="${successorRecord.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${successorRecord.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a> 
										
								    </c:if>                                    
							<br>								
							<c:forEach var="filter" items="${result.facetFilters}"  varStatus="status"><c:if test="${status.count > 1}">, </c:if><c:if test="${filter.name == 'format_name'}">Schema</c:if><c:if test="${filter.name == 'set_name'}">Set</c:if><c:if test="${filter.name == 'provider_name'}">Repository</c:if><c:if test="${filter.name == 'service_name'}">Service</c:if><!--<c:if test="${filter.name == 'harvest_end_time'}">Harvest</c:if>--><c:if test="${filter.name == 'warning'}">Warning</c:if><c:if test="${filter.name == 'error'}">Error</c:if>:${filter.value}</c:forEach>							
							</p>
						</c:if>	
						<!-- Display of filters In case of successor - end -->					
					</div>
					
					<!-- Display of Search text box - begin -->
					<div class="search_box_div">
						<form name="browseRecordsForm" method="post" action="browseRecords.action">

							<input type="text" id="search_text" name="query" value="${query}" size="40"/>
							<button class="xc_button" type="submit" name="save" >Search</button>
							<br><input type="checkbox" id="search_xml" name="searchXML" value="true" 
							<c:if test="${searchXML}">
								checked
							</c:if>/> Search Full XML content
						</form>	
					</div>
					<!-- Display of Search text box - end -->
					
				<div class="facet_line"/>
			
				<!-- Display of Search results -->
				<c:if test="${!initialLoad}">
					<div class="search_results_div">
						<c:if test="${result.totalNumberOfResults > 0}">
						
								<c:if test="${result.totalNumberOfResults % numberOfResultsToShow == 0}">
									<c:set var="totalNumOfPages" value="${result.totalNumberOfResults / numberOfResultsToShow}"/>
								</c:if>
								<c:if test="${result.totalNumberOfResults % numberOfResultsToShow != 0}">
									<c:set var="totalNumOfPages" value="${result.totalNumberOfResults / numberOfResultsToShow + 1}"/>
								</c:if>
							Page <strong>${currentPageNumber}</strong> of <strong> ${fn:substringBefore(totalNumOfPages,".")} </strong> &nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp; <strong>${result.totalNumberOfResults}</strong> results
									
							<div class="search_div_pager">
								<c:import url="browse_records_pager.jsp"/>
							</div>
						</c:if>	
						

						<c:forEach var="record" items="${result.records}" varStatus="rowCounter">
						<c:if test="${rowCounter.count % 2 != 0}">
							<div class="record_result_odd_div">
						</c:if>
						<c:if test="${rowCounter.count % 2 == 0}">
							<div class="record_result_even_div">
						</c:if>
							<div class="record_number">
								${rowStart + rowCounter.count}.
							</div>
						
							<div class="record_text">
								<c:url var="viewRecord" value="viewRecord.action">
									  <c:param name="recordId" value="${record.id}"/>
									  <c:param name="providerName" value="${record.provider.name}"/>
									  <c:param name="query" value="${query}"/>
									  <c:param name="searchXML" value="${searchXML}"/>
									  <c:param name="selectedFacetNames" value="${selectedFacetNames}"/>
								  	  <c:param name="selectedFacetValues" value="${selectedFacetValues}"/>
									  <c:param name="rowStart" value="${rowStart}"/>
									  <c:param name="startPageNumber" value="${startPageNumber}"/>
									  <c:param name="currentPageNumber" value="${currentPageNumber}"/>
									  <c:param name="recordXML" value="${record.oaiXml}"/>
									  	  
								  </c:url>
								<a href="${viewRecord}">${record.provider.name} ${record.id}</a>
								<br>
								Schema: ${record.format.name}
								<br>
								Provider: ${record.provider.name}
								<br>
<!--							
								<c:if test="${record.harvest != null}">
									Harvest: ${record.provider.name} ${record.harvest.endTime}
									<br>
								</c:if>
-->								<div class="redError">
								<c:if test="${record.errors != '[]'}">
									Error:
									<c:forEach var="error" items="${record.errors}" varStatus="status"><c:if test="${status.count > 1}">, </c:if>${error}</c:forEach>
									<br>
								</c:if>
								</div>

							   <c:url var="viewPredecessorRecord" value="browseRecords.action">
									  <c:param name="query" value=""/>
									  <c:param name="addFacetName" value="successor"/>
									  <c:param name="addFacetValue" value="${record.id}"/>
									  <c:param name="searchXML" value="false"/>
							   </c:url>
							   <c:url var="viewSuccessorRecord" value="browseRecords.action">
									  <c:param name="query" value=""/>
									  <c:param name="addFacetName" value="processed_from"/>
									  <c:param name="addFacetValue" value="${record.id}"/>
									  <c:param name="searchXML" value="false"/>
							   </c:url>										
							       <c:if test="${record.numberOfPredecessors > 0 && record.numberOfSuccessors > 0}">
										<a href="${viewPredecessorRecord}">${record.numberOfPredecessors} 
										<c:if test="${record.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${record.numberOfPredecessors > 1}">
											Predecessors
										</c:if></a> 
										&nbsp;<img src="page-resources/img/white-book-both.jpg">&nbsp;
										<a href="${viewSuccessorRecord}">${record.numberOfSuccessors} 
										<c:if test="${record.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${record.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a>
								    </c:if>
								    <c:if test="${record.numberOfPredecessors > 0 && record.numberOfSuccessors < 1}">
										<a href="${viewPredecessorRecord}">${record.numberOfPredecessors} 
										<c:if test="${record.numberOfPredecessors == 1}">
											Predecessor
										</c:if>
										<c:if test="${record.numberOfPredecessors > 1}">
											Predecessors
										</c:if> 
										</a> 
									      &nbsp;<img src="page-resources/img/white-book-left.jpg">
								    </c:if>
									<c:if test="${record.numberOfSuccessors > 0 && record.numberOfPredecessors < 1}">
										&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
										<img src="page-resources/img/white-book-right.jpg">
										&nbsp;<a href="${viewSuccessorRecord}">${record.numberOfSuccessors} 
										<c:if test="${record.numberOfSuccessors == 1}">
											Successor
										</c:if>
										<c:if test="${record.numberOfSuccessors > 1}">
											Successors
										</c:if> 
										</a> 
										
								    </c:if>                                    
							</div>
						</div>

						</c:forEach>						   			



				    </div>
				</c:if>		    
						    

			
 		</div>
		<!--  end body -->		
            
        </div>
        <!-- end doc -->
    </body>
</html>

    

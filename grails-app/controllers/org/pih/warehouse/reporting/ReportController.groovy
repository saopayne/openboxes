/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/ 
package org.pih.warehouse.reporting

import grails.converters.JSON
import grails.plugin.springcache.annotations.CacheFlush
import grails.plugin.springcache.annotations.Cacheable
import org.apache.commons.lang.StringEscapeUtils
import org.pih.warehouse.core.Location
import org.pih.warehouse.inventory.Transaction
import org.pih.warehouse.report.ChecklistReportCommand
import org.pih.warehouse.report.InventoryReportCommand
import org.pih.warehouse.report.ProductReportCommand

class ReportController {
	
	def documentService
	def inventoryService
	def productService
	def reportService
    def messageService

    def getCsv(list) {
        println list

        def csv = "";

        csv+= "Status,"
        csv+= "Product group,"
        csv+= "Product codes,"
        csv+= "Min,"
        csv+= "Reorder,"
        csv+= "Max,"
        csv+= "QoH,"
        csv+= "Value"
        csv+= "\n"

                //StringEscapeUtils.escapeCsv(product?.name?:"")
        // "${warehouse.message(code: 'inventoryLevel.currentQuantity.label', default: 'Current quantity')}"
        list.each { row ->
            csv += row.status + ","
            csv += StringEscapeUtils.escapeCsv(row.name) + ","
            csv += StringEscapeUtils.escapeCsv(row.productCodes.join(",")) + ","
            csv += row.minQuantity + ","
            csv += row.reorderQuantity + ","
            csv += row.maxQuantity + ","
            csv += row.onHandQuantity + ","
            csv += row.totalValue + ","
            csv += "\n"
        }

        return csv;
    }

    def getCsvForListOfMapEntries(List list) {
        def csv = ""
        if (list) {
            list[0].eachWithIndex { k, v, index ->
                csv += StringEscapeUtils.escapeCsv(k) + ","
            }
            csv+= "\n"

            list.each { entry ->
                entry.eachWithIndex { k, v, index ->
                    csv += StringEscapeUtils.escapeCsv(v ? v.toString() : "") + ","
                }
                csv += "\n"
            }
        }
        return csv
    }


    def getCsvForListOfMapEntries(List list, Closure csvHeader, Closure csvRow) {
        def csv = ""
        if (list) {

            csv += csvHeader(list[0])

            list.each { entry ->
                csv += csvRow(entry)
            }
        }
        return csv
    }

    def binLocationCsvHeader = { binLocation ->
        String csv = ""
        if (binLocation) {
            csv += g.message(code:'default.status.label') + ","
            csv += g.message(code:'product.productCode.label') + ","
            csv += g.message(code:'product.label') + ","
            csv += g.message(code:'productGroup.label') + ","
            csv += g.message(code:'category.label') + ","
            csv += g.message(code:'inventoryItem.lotNumber.label') + ","
            csv += g.message(code:'inventoryItem.expirationDate.label') + ","
            csv += g.message(code:'location.binLocation.label') + ","
            csv += g.message(code:'default.quantity.label') + ","
            csv += "\n"
        }
        return csv;

    }

    def binLocationCsvRow = { binLocation ->
        String csv = ""
        if (binLocation) {
            String defaultBinLocation = g.message(code: 'default.label')
            String expirationDate = g.formatDate(date: binLocation?.inventoryItem?.expirationDate, format: "MMM yyyy")
            csv += binLocation.status + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.product?.productCode) + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.product?.name) + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.product?.genericProduct?.name) + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.product?.category?.name) + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.inventoryItem?.lotNumber) + ","
            csv += StringEscapeUtils.escapeCsv(expirationDate) + ","
            csv += StringEscapeUtils.escapeCsv(binLocation?.binLocation?.name?:defaultBinLocation) + ","
            csv += binLocation.quantity + ","
            csv += "\n"
        }
        return csv;
    }


	def exportBinLocation = {
        long startTime = System.currentTimeMillis()
        log.info "Export by bin location " + params
		Location location = Location.get(session.warehouse.id)
        Location binLocation = (params.binLocation) ? Location.findByParentLocationAndNameLike(location, "%" + params.binLocation + "%") : null



		List binLocations = inventoryService.getQuantityByBinLocation(location, binLocation)

        def products = binLocations.collect { it.product.productCode }.unique()
        binLocations = binLocations.collect { [productCode: it.product.productCode,
                                               productName: it.product.name,
                                               lotNumber: it.inventoryItem.lotNumber,
                                               expirationDate: it.inventoryItem.expirationDate,
                                               binLocation: it?.binLocation?.name?:"Default Bin",
                                               quantity: it.quantity]}

        long elapsedTime = System.currentTimeMillis() - startTime

        if (params.downloadFormat == "csv") {
            String csv = getCsvForListOfMapEntries(binLocations)
            String binLocationName = binLocation ? binLocation?.name : "All Bins"
            def filename = "Bin location report - " + location.name + " - " + binLocationName + ".csv"
            response.setHeader("Content-disposition", "attachment; filename='" + filename + "'")
            render(contentType: "text/csv", text: csv)
            return
        }

		render([elapsedTime: elapsedTime, binLocationCount: binLocations.size(), productCount: products.size(), binLocations: binLocations] as JSON)
	}


    def exportInventoryReport = {
        println "Export inventory report " + params
        def map = []
        def location = Location.get(session.warehouse.id)
        if (params.list("status")) {
            def data = reportService.calculateQuantityOnHandByProductGroup(location.id)
            params.list("status").each {
                println it
                map += data.productGroupDetails[it].values()
            }
            map.unique()
        }

        def filename = "Stock report - " + location.name + ".csv"
        response.setHeader("Content-disposition", "attachment; filename='" + filename + "'")
        render(contentType: "text/csv", text:getCsv(map))
        return;
    }

	def showInventoryReport = {


	}


    def showInventorySamplingReport = {

        def sw = new StringWriter()
        def count = (params.n?:10).toInteger()
        def location = Location.get(session.warehouse.id)
        def inventoryItems = []

        try {
            inventoryItems = inventoryService.getInventorySampling(location, count);

            if (inventoryItems) {

                println inventoryItems
                //sw.append(csvrows[0].keySet().join(",")).append("\n")
                sw.append("Product Code").append(",")
                sw.append("Product").append(",")
                sw.append("Lot number").append(",")
                sw.append("Expiration date").append(",")
                sw.append("Bin location").append(",")
                sw.append("On hand quantity").append(",")
                sw.append("\n")
                inventoryItems.each { inventoryItem ->
                    if (inventoryItem) {
                        def inventoryLevel = inventoryItem?.product?.getInventoryLevel(location.id)
                        sw.append('"' + (inventoryItem?.product?.productCode?:"").toString()?.replace('"','""') + '"').append(",")
                        sw.append('"' + (inventoryItem?.product?.name?:"").toString()?.replace('"','""') + '"').append(",")
                        sw.append('"' + (inventoryItem?.lotNumber?:"").toString()?.replace('"','""') + '"').append(",")
                        sw.append('"' + inventoryItem?.expirationDate.toString()?.replace('"','""') + '"').append(",")
                        sw.append('"' + (inventoryLevel?.binLocation?:"")?.toString()?.replace('"','""') + '"').append(",")
                        sw.append("\n")
                    }
                }
            }

        } catch (RuntimeException e) {
            log.error (e.message)
            sw.append(e.message)
        }




        //render sw.toString()

        response.setHeader("Content-disposition", "attachment; filename='Inventory-sampling-${new Date().format("yyyyMMdd-hhmmss")}.csv'")
        render(contentType:"text/csv", text: sw.toString(), encoding:"UTF-8")

    }



    def showConsumptionReport = {

        def transactions = Transaction.findAllByTransactionDateBetween(new Date()-10, new Date())

        [transactions: transactions]
    }


	def showProductReport = { ProductReportCommand command -> 	
		
		//if (!command?.product) { 
		//	throw new Exception("Unable to locate product " + params?.product?.id)
		//}
		
		if (!command?.hasErrors()) {			
			reportService.generateProductReport(command)
		}
						
		[command : command]
		
		
	}
	
	
	def showTransactionReport = { 
		
		InventoryReportCommand command = new InventoryReportCommand();
		command.rootCategory = productService.getRootCategory();
		
		
		[command : command ]
	}
	
	
	def generateTransactionReport = { InventoryReportCommand command -> 
		// We always need to initialize the root category 
		command.rootCategory = productService.getRootCategory();
		if (!command?.hasErrors()) { 			
			reportService.generateTransactionReport(command);			
		}
		render(view: 'showTransactionReport', model: [command : command])
	}
	
	def showShippingReport = { ChecklistReportCommand command ->
		command.rootCategory = productService.getRootCategory();
		if (!command?.hasErrors()) {
			reportService.generateShippingReport(command);
		}
		[command : command]
	}
	
	def showPaginatedPackingListReport = { ChecklistReportCommand command ->
		command.rootCategory = productService.getRootCategory();
		if (!command?.hasErrors()) {
			reportService.generateShippingReport(command);
		}
		[command : command]
	}	
	
	def printShippingReport = { ChecklistReportCommand command ->
		command.rootCategory = productService.getRootCategory();
		if (!command?.hasErrors()) {
			reportService.generateShippingReport(command);
		}
		[command : command]
	}

	def printPickListReport = { ChecklistReportCommand command ->

		Map binLocations
		//command.rootCategory = productService.getRootCategory();
		if (!command?.hasErrors()) {
			reportService.generateShippingReport(command);
			binLocations = inventoryService.getBinLocations(command.shipment)
		}
		[command : command, binLocations: binLocations]
	}


	def printPaginatedPackingListReport = { ChecklistReportCommand command ->
		try {
			command.rootCategory = productService.getRootCategory();
			if (!command?.hasErrors()) {
				reportService.generateShippingReport(command);
			}
		} catch (Exception e) {
			log.error("error", e)
			e.printStackTrace()
		}
		[command : command]
	}
	

	def downloadTransactionReport = {		
		def baseUri = request.scheme + "://" + request.serverName + ":" + request.serverPort

		// JSESSIONID is required because otherwise the login page is rendered
		def url = baseUri + params.url + ";jsessionid=" + session.getId()		
		url += "?print=true" 
		url += "&location.id=" + params.location.id
		url += "&category.id=" + params.category.id
		url += "&startDate=" + params.startDate
		url += "&endDate=" + params.endDate
		url += "&showTransferBreakdown=" + params.showTransferBreakdown
		url += "&hideInactiveProducts=" + params.hideInactiveProducts
		url += "&insertPageBreakBetweenCategories=" + params.insertPageBreakBetweenCategories
		url += "&includeChildren=" + params.includeChildren
		url += "&includeEntities=true" 

		// Let the browser know what content type to expect
		//response.setHeader("Content-disposition", "attachment;") // removed filename=
		response.setContentType("application/pdf")

		// Render pdf to the response output stream
		log.info "BaseUri is $baseUri"	
		log.info("Session ID: " + session.id)
		log.info "Fetching url $url"
		reportService.generatePdf(url, response.getOutputStream())
	}
	
	def downloadShippingReport = {		
		if (params.format == 'docx') { 
			def tempFile = documentService.generateChecklistAsDocx()
	//		def filename = "shipment-checklist.docx"
			//response.setHeader("Content-disposition", "attachment; filename=" + filename);
			response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
			response.outputStream << tempFile.readBytes()
		} 
		else if (params.format == 'pdf') { 
			def baseUri = request.scheme + "://" + request.serverName + ":" + request.serverPort
			def url = baseUri + params.url + ";jsessionid=" + session.getId()
			url += "?print=true&orientation=portrait"
			url += "&shipment.id=" + params.shipment.id
			url += "&includeEntities=true" 
			log.info "Fetching url $url"	
			response.setContentType("application/pdf")
			//response.setHeader("Content-disposition", "attachment;") // removed filename=	
			reportService.generatePdf(url, response.getOutputStream())
		}
		else { 
			throw new UnsupportedOperationException("Format '${params.format}' not supported")
		}
	}

    @CacheFlush(["binLocationReportCache", "binLocationSummaryCache"])
    def clearBinLocationCache = {
        flash.message = "Cache have been flushed"
        redirect(action: "showBinLocationReport")
    }



    def showBinLocationReport = {

        log.info "showBinLocationReport " + params
        def startTime = System.currentTimeMillis()
        String locationId = params?.location?.id ?: session?.warehouse?.id
        Location location = Location.get(locationId)

        def quantityMap = [:]
        List binLocations = []
        List statuses = ["inStock", "outOfStock"]

        statuses = statuses.collect { status ->
            String messageCode = "binLocationSummary.${status}.label"
            String label = messageService.getMessage(messageCode)
            [status: status, label: label]
        }

        try {
            if (params.button == "download") {
                def binLocationReport = inventoryService.getBinLocationReport(location)

                binLocations = binLocationReport.data
                statuses = binLocationReport.summary

                // Filter on status
                if (params.status) {
                    binLocations = binLocations.findAll { it.status == params.status }
                }

                String csv = getCsvForListOfMapEntries(binLocations, binLocationCsvHeader, binLocationCsvRow)
                def filename = "Bin Location Report - ${location?.name} - ${params.status?:'All'}.csv"
                response.setHeader("Content-disposition", "attachment; filename='" + filename + "'")
                render(contentType: "text/csv", text: csv)
                return
            }


        } catch (Exception e) {
            log.error("Unable to generate bin location report due to error: " + e.message, e)
            flash.message = e.message
        }

        log.info("Show bin location report: " + (System.currentTimeMillis() - startTime) + " ms");
        [
                location: location,
                elapsedTime: (System.currentTimeMillis() - startTime),
                quantityMap: quantityMap,
                binLocations: binLocations,
                statuses: statuses
        ]

    }






}
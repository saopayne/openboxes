/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/ 
package org.pih.warehouse.report

import org.apache.http.client.HttpClient
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.docx4j.org.xhtmlrenderer.pdf.ITextRenderer
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.inventory.Inventory
import org.pih.warehouse.inventory.InventoryItem
import org.pih.warehouse.inventory.InventoryLevel
import org.pih.warehouse.inventory.InventoryStatus
import org.pih.warehouse.inventory.TransactionEntry
import org.pih.warehouse.product.Product
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.w3c.dom.Document
import org.xml.sax.InputSource
import util.InventoryUtil

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.text.NumberFormat

class ReportService implements ApplicationContextAware {
	
	def productService
	def inventoryService
	def shipmentService
	def localizationService
	def grailsApplication
	
	ApplicationContext applicationContext
	
	boolean transactional = true

	public void generateShippingReport(ChecklistReportCommand command) {		
		def shipmentItems = command?.shipment?.shipmentItems?.sort()
		shipmentItems.each { shipmentItem -> 
			command.checklistReportEntryList << new ChecklistReportEntryCommand(shipmentItem: shipmentItem)
		}
	}

	public void generateProductReport(ProductReportCommand command) { 
		
		command.inventoryItems = InventoryItem.findAllByProduct(command?.product)
		command.quantityInitial = inventoryService.getInitialQuantity(command?.product, command?.location, command?.startDate)

		def transactionEntries = inventoryService.getTransactionEntries(command?.product, command?.location, command?.startDate, command?.endDate)
				
		// Calculate quantity at each transaction entry point.
		def quantity = command?.quantityInitial;
		transactionEntries.each { transactionEntry ->
			def productReportEntry = new ProductReportEntryCommand(transactionEntry: transactionEntry, balance: 0)
			productReportEntry.balance = inventoryService.adjustQuantity(quantity, transactionEntry)
			command.productReportEntryList << productReportEntry
			
			// Need to keep track of the running total so we can adjust the balance as we go
			quantity = productReportEntry.balance
		}
		//command.quantityFinal = quantity;
		command.quantityFinal = inventoryService.getCurrentQuantity(command?.product, command?.location, command?.endDate)
	}

	TransactionEntry getEarliestTransactionEntry(Product product, Inventory inventory) { 
		def list = TransactionEntry.createCriteria().list() {
			and { 
				inventoryItem {
					eq("product.id", product?.id)
				}
				transaction {
					eq("inventory", inventory)
					order("transactionDate", "asc")
					order("dateCreated", "asc")
				}
			}
			maxResults(1)
		}
		
		return list[0]
	}
	
	TransactionEntry getLatestTransactionEntry(Product product, Inventory inventory) { 
		def list = TransactionEntry.createCriteria().list() {
			and {
				inventoryItem {
					eq("product.id", product?.id)
				}
				transaction {
					eq("inventory", inventory)
					order("transactionDate", "desc")
					order("dateCreated", "desc")
				}
			}
			maxResults(1)
		}
		return list[0]
	}
	
	/**
	 * 
	 * @param command
	 */
	public void generateTransactionReport(InventoryReportCommand command) { 
		
		def products = 
			//inventoryService.getProductsByNestedCategory(command.category)
			(command?.includeChildren) ? inventoryService.getProductsByNestedCategory(command.category) : 
				inventoryService.getProductsByCategory(command.category) 
				
		if (command?.showEntireHistory) { 
			def earliestDate = getEarliestTransactionEntry(command?.product, command?.location?.inventory)?.transaction?.transactionDate			
			//def latestDate = getLatestTransactionEntry(command?.product, command?.location?.inventory)?.transaction?.transactionDate
			command.startDate = earliestDate?:command.startDate
			command.endDate = new Date() + 1
		}
		
		// 
		//command.startDate = command.startDate?:new Date()
		//command.endDate = command.endDate?:new Date()
		// TODO Need to restrict by date and category
		def transactionEntries = inventoryService.getTransactionEntries(command.location, command.category, command?.startDate, command?.endDate);
		def transactionEntriesByProduct = transactionEntries.groupBy { it?.inventoryItem?.product }

				
		log.info "Products (" + products.size() + ") -> " + products
		// Initialize the report map to reference all products to be displayed		 
		products.each { product ->
			
			def productTransactionEntries = transactionEntriesByProduct[product]
			def includeProduct = (command?.hideInactiveProducts && productTransactionEntries || !command?.hideInactiveProducts)
				
			if (includeProduct) { 
				def productEntry = command.entries[product];
				if (!productEntry) {
					productEntry = new InventoryReportEntryCommand(product: product);
					command.entries[product] = productEntry
				}
				productEntry.quantityInitial = inventoryService.getInitialQuantity(product, command?.location, command?.startDate?:null)
				productEntry.quantityFinal = inventoryService.getCurrentQuantity(product, command?.location, command?.endDate?:new Date());
	
				// Initialize the product map to reference all inventory items for that product
				def inventoryItems = inventoryService.getInventoryItemsByProduct(product)
				inventoryItems?.each { inventoryItem ->				
					//log.info "inventory item -> " + inventoryItem
					def inventoryItemEntry = productEntry?.entries[inventoryItem];
					if (!inventoryItemEntry) { 
						inventoryItemEntry = new InventoryReportEntryCommand(product: product, inventoryItem: inventoryItem);
						productEntry.entries[inventoryItem] = inventoryItemEntry;
					}
					inventoryItemEntry.quantityInitial = inventoryService.getQuantity(inventoryItem, command.location, command.startDate?:null)
					inventoryItemEntry.quantityFinal = inventoryService.getQuantity(inventoryItem, command.location, command.endDate?:new Date())
					inventoryItemEntry.quantityRunning = inventoryItemEntry.quantityInitial
					
					//inventoryItemEntry.quantityInitial = inventoryService.getInitialQuantity(inventoryItem, command?.location, command?.startDate)
					//inventoryItemEntry.quantityFinal = inventoryService.getCurrentQuantity(inventoryItem, command?.location, command?.endDate);
					
				}
			}
		}
		
		
		log.info "transactionEntries (" + transactionEntries.size() + ") -> " + transactionEntries
		// Iterate over the transaction entries for the given time period to tabulate totals.
		// Each time we encounter an INVENTORY, compare that quantity with the running total,
		// and add / subract to "adjustment" as appropriate.  Then set the running
		// total to the new inventory and continue with the running total...
		transactionEntries.each {
			def inventoryItem = it?.inventoryItem
			def transactionType = it?.transaction?.transactionType
			
			log.info "transactionEntry -> " + it.transaction.transactionType.name + " = " + it.quantity
			
			def productEntry = command.entries[inventoryItem.product]
			if (productEntry) { 
				def inventoryItemEntry = productEntry.entries[inventoryItem];		
						
				
				if (inventoryItemEntry) {					
					
					if (transactionType?.id == Constants.CONSUMPTION_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning += it.quantity
						inventoryItemEntry.quantityConsumed += it.quantity
						inventoryItemEntry.quantityTotalOut += it.quantity
					}
					else if (transactionType?.id == Constants.ADJUSTMENT_CREDIT_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning += it.quantity
						inventoryItemEntry.quantityFound += it.quantity
						inventoryItemEntry.quantityAdjusted += it.quantity
						inventoryItemEntry.quantityTotalIn += it.quantity
					}
					else if (transactionType?.id == Constants.EXPIRATION_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning -= it.quantity
						inventoryItemEntry.quantityExpired += it.quantity
						inventoryItemEntry.quantityTotalOut += it.quantity
					}
					else if (transactionType?.id == Constants.DAMAGE_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning -= it.quantity
						inventoryItemEntry.quantityDamaged += it.quantity
						inventoryItemEntry.quantityTotalOut += it.quantity
					}
					else if (transactionType?.id == Constants.TRANSFER_IN_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning += it.quantity
						inventoryItemEntry.quantityTransferredIn += it.quantity
						inventoryItemEntry.quantityTotalIn += it.quantity
						if (!inventoryItemEntry.quantityTransferredInByLocation[it.transaction.source]) { 
							inventoryItemEntry.quantityTransferredInByLocation[it.transaction.source] = 0
						}
						inventoryItemEntry.quantityTransferredInByLocation[it.transaction.source] += it.quantity					
					}
					else if (transactionType?.id == Constants.TRANSFER_OUT_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning -= it.quantity
						inventoryItemEntry.quantityTransferredOut += it.quantity
						inventoryItemEntry.quantityTotalOut += it.quantity
						if (!inventoryItemEntry.quantityTransferredOutByLocation[it.transaction.destination]) { 
							inventoryItemEntry.quantityTransferredOutByLocation[it.transaction.destination] = 0;
						}
						inventoryItemEntry.quantityTransferredOutByLocation[it.transaction.destination] += it.quantity					
					}
					else if (transactionType?.id == Constants.ADJUSTMENT_DEBIT_TRANSACTION_TYPE_ID) {
						inventoryItemEntry.quantityRunning -= it.quantity
						inventoryItemEntry.quantityLost += it.quantity
						inventoryItemEntry.quantityAdjusted -= it.quantity
						inventoryItemEntry.quantityTotalOut += it.quantity
					}
					else if (transactionType?.id == Constants.INVENTORY_TRANSACTION_TYPE_ID) {
						def diff = it.quantity - inventoryItemEntry.quantityRunning
						inventoryItemEntry.quantityAdjusted += diff					
						inventoryItemEntry.quantityRunning = it.quantity;
						if (diff > 0) { 
							inventoryItemEntry.quantityFound += diff;
							inventoryItemEntry.quantityTotalIn += diff
						}
						else {  
							inventoryItemEntry.quantityLost += diff	
							inventoryItemEntry.quantityTotalOut += diff
						}
					}
					else if (transactionType?.id == Constants.PRODUCT_INVENTORY_TRANSACTION_TYPE_ID) {
						def diff = it.quantity - inventoryItemEntry.quantityRunning
						inventoryItemEntry.quantityAdjusted += diff
						inventoryItemEntry.quantityRunning = it.quantity;
						if (diff > 0) { 
							inventoryItemEntry.quantityFound += diff;
							inventoryItemEntry.quantityTotalIn += diff
						}
						else {  
							inventoryItemEntry.quantityLost += diff	
							inventoryItemEntry.quantityTotalOut += diff
						}
					}
					
					// Add transaction entry
					def balance = inventoryItemEntry.quantityRunning
					inventoryItemEntry.transactionEntries << new ProductReportEntryCommand(transactionEntry: it, balance: balance)
					
				}
			}
		}		
	}

	void generatePdf(String url, OutputStream outputStream) {
        def html = ""
		log.info "Generate PDF for URL " + url
		try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            //Document document = loadXMLFromString(html)
            //Document document = builder.parse(new StringBufferInputStream(html));

            html = getHtmlContent(url)

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);

            //renderer.setDocument(document, url);
			renderer.layout();
			renderer.createPDF(outputStream);

            outputStream.close();
            outputStream = null;

		} catch (Exception e) { 
			log.error("Cannot generate pdf due to error: " + e.message, e);
            log.error "Error caused by: " + html

		} finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                // ignore
                }
            }
        }
	}

    public static Document loadXMLFromString(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }

	private getHtmlContent(String url) { 
		
		HttpClient httpclient = new DefaultHttpClient();
		try {
			HttpGet httpget = new HttpGet(url);
			// Create a response handler
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String responseBody = httpclient.execute(httpget, responseHandler);
            println responseBody
			return responseBody;
			
			
		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();
		}
	}

    private byte[] buildPdf(url) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocument(url);
        renderer.layout();
        renderer.createPDF(baos);
        return baos.toByteArray();
    }

    //@Cacheable("quantityOnHandCache")
    def calculateQuantityOnHandByProductGroup(locationId) {
        def items = []
        def startTime = System.currentTimeMillis()
        def location = Location.get(locationId)
        //def quantityMap = inventoryService.getQuantityByProductMap(session.warehouse.id)
        def quantityMap = inventoryService.getInventoryStatusAndLevel(location)

        quantityMap.each { product, map ->

            def status = map.status
            def onHandQuantity = map.onHandQuantity
            def inventoryLevel = map.inventoryLevel

            println product.name + " = " + status

            //def inventoryLevel = product.getInventoryLevel(session.warehouse.id)
            def imageUrl = (product.thumbnail)?'/openboxes/product/renderImage/${product?.thumbnail?.id}':''
            items << [
                    id:product.id,
                    name: product.name,
                    status: status,
                    productCode: product.productCode,
                    genericProductId:product?.genericProduct?.id,
                    genericProduct:product?.genericProduct?.name?:product.name,
                    hasProductGroup: (product?.genericProduct?.id!=null),
                    unitOfMeasure: product.unitOfMeasure,
                    imageUrl: imageUrl,
                    inventoryLevel: inventoryLevel,
                    minQuantity: inventoryLevel?.minQuantity?:0,
                    maxQuantity: inventoryLevel?.maxQuantity?:0,
                    reorderQuantity: inventoryLevel?.reorderQuantity?:0,
                    unitPrice: product.pricePerUnit?:0.0,
                    //onHandQuantity:value?:0.0,
                    onHandQuantity:onHandQuantity,
                    totalValue: (product.pricePerUnit?:0) * (onHandQuantity?:0)
            ]
        }

        // Group all items by status
        def statusSummary = items.inject([:].withDefault { [count:0,items:[]] } ) { map, item ->
            map[item.status].count++
            //map[item.status].items << item
            map
        }



        // Group entries by product group

        // Removed products:[]
        def productGroupMap = items.inject([:].withDefault { [id:null,name:null,status:null,productCodes:[],unitPrice:0,totalValue:0,numProducts:0,numInventoryLevels:0,
                onHandQuantity:0,minQuantity:0,maxQuantity:0,reorderQuantity:0,inventoryStatus:null,hasInventoryLevel:false,hasProductGroup:false,inventoryLevelId:null] } ) { map, item ->
            //map[item.genericProduct].products << item;
            map[item.genericProduct].id = item.genericProductId
            map[item.genericProduct].name = item.genericProduct
            map[item.genericProduct].hasProductGroup = item.hasProductGroup
            map[item.genericProduct].numProducts++;
            map[item.genericProduct].onHandQuantity += item.onHandQuantity;
            //map[item.genericProduct].products << item
            map[item.genericProduct].productCodes << item.productCode
            map[item.genericProduct].totalValue += item.totalValue;
            map[item.genericProduct].unitPrice += item.unitPrice;

            if (item.inventoryLevel) {
                map[item.genericProduct].numInventoryLevels++
                map[item.genericProduct].hasInventoryLevel = true

                // Make sure we're using the latest version of the inventory level (the one where values are not set to 0)
                def currentInventoryLevel = map[item.genericProduct].inventotryLevel
                if (!currentInventoryLevel) {
                    // || item?.inventoryLevel?.lastUpdated?.after(currentInventoryLevel.lastUpdated)
                    map[item.genericProduct].inventoryLevelId = item?.inventoryLevel?.id
                    map[item.genericProduct].inventoryLevel = item.inventoryLevel
                    map[item.genericProduct].inventoryStatus = item?.inventoryLevel?.status?.name()
                    map[item.genericProduct].minQuantity = item.minQuantity;
                    map[item.genericProduct].reorderQuantity = item.reorderQuantity;
                    map[item.genericProduct].maxQuantity = item.maxQuantity;
                    //map[item.genericProduct].status =
                }
            }

            map
        }


        NumberFormat numberFormat = NumberFormat.getNumberInstance()
        //numberFormat.maximumFractionDigits = 2
		String currencyCode = grailsApplication.config.openboxes.locale.defaultCurrencyCode?:"USD"
		numberFormat.currency = Currency.getInstance(currencyCode)
        numberFormat.maximumFractionDigits = 2
        numberFormat.minimumFractionDigits = 2

        // Set status for all rows
        productGroupMap.each { k, v ->
            v.status = InventoryUtil.getStatusMessage(v?.inventoryLevel?.status, v?.inventoryLevel?.minQuantity?:0,v?.inventoryLevel?.reorderQuantity?:0,v?.inventoryLevel?.maxQuantity?:0,v?.onHandQuantity?:0)
            v.unitPriceFormatted = numberFormat.format(v.unitPrice)
            v.totalValueFormatted = numberFormat.format(v.totalValue)
        }


        //def noInventoryLevels = productGroupMap.findAll { k,v -> !v.inventoryLevel }
        def hasInventoryLevel = productGroupMap.findAll { k,v -> v.hasInventoryLevel }
        def hasNoInventoryLevel = productGroupMap.findAll { k,v -> !v.hasInventoryLevel }
        def zeroInventoryLevels = productGroupMap.findAll { k,v -> v.numInventoryLevels == 0 }
        def multipleInventoryLevels = productGroupMap.findAll { k,v -> v.numInventoryLevels > 1 }
        def singleInventoryLevel = productGroupMap.findAll { k,v -> v.numInventoryLevels == 1 }

        def hasInventoryLevelCount = hasInventoryLevel.size()
        def hasNoInventoryLevelCount = hasNoInventoryLevel.size()

        def zeroInventoryLevelsCount = zeroInventoryLevels.size()
        def multipleInventoryLevelsCount = multipleInventoryLevels.size()
        def singleInventoryLevelCount = singleInventoryLevel.size()
        def inventoryLevelsCount = zeroInventoryLevelsCount + multipleInventoryLevelsCount + singleInventoryLevelCount

        // Process all product groups
        def notStocked = productGroupMap.findAll { k,v -> v.onHandQuantity <= 0 && !v.hasInventoryLevel }
        def outOfStock = productGroupMap.findAll { k,v -> v.onHandQuantity <= 0 && v.hasInventoryLevel }
        def lowStock = productGroupMap.findAll { k,v -> v.onHandQuantity > 0 && v.onHandQuantity <= v.minQuantity && v.minQuantity > 0 } //&& v.minQuantity > 0
        def reorderStock = productGroupMap.findAll { k,v -> v.onHandQuantity > v.minQuantity && v.onHandQuantity <= v.reorderQuantity && v.reorderQuantity > 0 }//v.reorderQuantity > 0
        def inStock = productGroupMap.findAll { k,v -> v.onHandQuantity > 0 && v.reorderQuantity == 0 && v.maxQuantity == 0 && v.minQuantity == 0 }
        def idealStock = productGroupMap.findAll { k,v -> v.onHandQuantity > v.reorderQuantity && v.onHandQuantity <= v.maxQuantity }//&& v.maxQuantity > 0
        def overStock = productGroupMap.findAll { k,v -> v.onHandQuantity > v.maxQuantity && v.maxQuantity > 0 } //v.maxQuantity > 0

        println "Not stocked: " + notStocked.size()
        println "Out of stock: " + outOfStock.size()


        // Get product group sizes
        def notStockedCount = notStocked.size()
        def outOfStockCount = outOfStock.size()
        def lowStockCount = lowStock.size()
        def reorderStockCount = reorderStock.size()
        def inStockCount = inStock.size()
        def idealStockCount = idealStock.size()
        def overStockCount = overStock.size()

        def all = productGroupMap
        def accounted = notStocked + outOfStock + lowStock + reorderStock + idealStock + overStock + inStock
        def invalid = all - accounted
        def invalidCount = invalid.size()

        def totalCountActual = outOfStockCount + lowStockCount + reorderStockCount + idealStockCount + inStockCount + overStockCount + notStockedCount + invalidCount;
        def totalCountFromSummary = statusSummary.values()*.count.sum()




        def elapsedTime = (System.currentTimeMillis() - startTime)/1000

        //render([quantityMap:quantityMap] as JSON)
        return [
                responseTime: elapsedTime + "s",
                productSummary:[
                        statusSummary:statusSummary,
                        totalCount:totalCountFromSummary
                ],
                inventoryLevelSummary:[
                        totalCount:inventoryLevelsCount,
                        hasInventoryLevel: hasInventoryLevelCount,
                        hasNoInventoryLevel: hasNoInventoryLevelCount,
                        zeroInventoryLevels: zeroInventoryLevelsCount,
                        singleInventoryLevel: singleInventoryLevelCount,
                        multipleInventoryLevels:multipleInventoryLevelsCount
                ],
                productGroupSummary:[
                        totalCountProductsExpected: productGroupMap.values().sum{ it.numProducts },
                        totalCountProductGroupsExpected: productGroupMap.keySet().size(),
                        totalCountProductGroupsActual: totalCountActual,
                        "NOT_STOCKED": [numProductGroups: notStockedCount, percentage: notStockedCount/totalCountActual, numProducts: notStocked.values().sum { it.numProducts }],
                        "STOCK_OUT": [numProductGroups: outOfStockCount, percentage: outOfStockCount/totalCountActual, numProducts: outOfStock.values().sum { it.numProducts }],
                        "LOW_STOCK": [numProductGroups: lowStockCount, percentage: lowStockCount/totalCountActual, numProducts: lowStock.values().sum { it.numProducts }],
                        "REORDER": [numProductGroups: reorderStockCount, percentage: reorderStockCount/totalCountActual, numProducts: reorderStock.values().sum { it.numProducts }],
                        "IN_STOCK": [numProductGroups: inStockCount, percentage: inStockCount/totalCountActual, numProducts: inStock.values().sum { it.numProducts }],
                        "IDEAL_STOCK": [numProductGroups: idealStockCount, percentage: idealStockCount/totalCountActual, numProducts: idealStock.values().sum { it.numProducts }],
                        "OVERSTOCK": [numProductGroups: overStockCount, percentage: overStockCount/totalCountActual, numProducts: overStock.values().sum { it.numProducts }],
                        "INVALID": [numProductGroups: invalidCount, percentage: invalidCount/totalCountActual, numProducts: invalid.values().sum { it.numProducts }]
                ],
                productGroupDetails: [
                        "ALL":productGroupMap,
                        "NOT_STOCKED":notStocked,
                        "STOCK_OUT":outOfStock,
                        "LOW_STOCK":lowStock,
                        "REORDER":reorderStock,
                        "IN_STOCK":inStock,
                        "IDEAL_STOCK":idealStock,
                        "OVERSTOCK":overStock,
                        "INVALID":invalid
                ]
        ]
    }

    /*
    def getStatusMessage(InventoryLevel inventoryLevel, Integer currentQuantity) {

        def statusMessage = "UNKNOWN"
        if (inventoryLevel) {
            if (inventoryLevel.status == InventoryStatus.SUPPORTED  || !inventoryLevel.status) {
                if (currentQuantity <= 0) {
                    statusMessage = "STOCK_OUT"
                }
                else if (inventoryLevel.minQuantity && currentQuantity <= inventoryLevel.minQuantity && inventoryLevel.minQuantity > 0) {
                    statusMessage = "LOW_STOCK"
                }
                else if (inventoryLevel.reorderQuantity && currentQuantity <= inventoryLevel.reorderQuantity && inventoryLevel.reorderQuantity > 0) {
                    statusMessage = "REORDER"
                }
                else if (inventoryLevel.maxQuantity && currentQuantity > inventoryLevel.maxQuantity && inventoryLevel.maxQuantity > 0) {
                    statusMessage = "OVERSTOCK"
                }
                else if (inventoryLevel.maxQuantity && currentQuantity > inventoryLevel.reorderQuantity && currentQuantity <= inventoryLevel.maxQuantity && inventoryLevel.maxQuantity > 0 ) {
                    statusMessage = "IDEAL_STOCK"
                }
                else {
                    statusMessage = "IN_STOCK"
                }
            }
            else if (inventoryLevel.status == InventoryStatus.NOT_SUPPORTED) {
                statusMessage = "NOT_SUPPORTED"
            }
            else if (inventoryLevel.status == InventoryStatus.SUPPORTED_NON_INVENTORY) {
                statusMessage = "SUPPORTED_NON_INVENTORY"
            }
            else {
                statusMessage = "UNAVAILABLE"
            }
        }
        else {
            if (currentQuantity <= 0) {
                statusMessage = "NOT_STOCKED"
            }
            else if (currentQuantity > 0 ) {
                statusMessage = "IN_STOCK"
            }
        }

        return statusMessage
    }
    */

}

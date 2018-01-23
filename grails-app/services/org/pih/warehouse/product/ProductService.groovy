/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/ 
package org.pih.warehouse.product

import grails.validation.ValidationException
import groovy.xml.Namespace
import org.pih.warehouse.auth.AuthService
import org.pih.warehouse.core.ApiException
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Tag
import org.pih.warehouse.core.UnitOfMeasure
import org.pih.warehouse.importer.ImportDataCommand
import org.pih.warehouse.inventory.InventoryLevel
import org.pih.warehouse.inventory.TransactionCode
import org.pih.warehouse.inventory.TransactionEntry

import java.text.SimpleDateFormat

import org.grails.plugins.csv.CSVWriter

/**
 * @author jmiranda
 *
 */
class ProductService {

	def sessionFactory
	def grailsApplication
	def identifierService
	
	/**
	 * 	
	 * @param query
	 * @return
	 */
	def getNdcProduct(query) {
		return getNdcResults("getcode", query)
	}

	/**
	 * 
	 */
	def findNdcProducts(search) {
		return getNdcResults("search", search.searchTerms?:"")
	}

	def getNdcResults(operation, q) {
		def hipaaspaceApiKey = grailsApplication.config.hipaaspace.api.key
		if (!hipaaspaceApiKey) {
			throw new ApiException(message: "Your administrator must specify Hipaaspace.com API key (hipaaspace.api.key) in configuration file (openboxes-config.properties).  Sign up at <a href='http://www.hipaaspace.com/myaccount/login.aspx?ReturnUrl=%2fmyaccount%2fdefault.aspx' target='_blank'>hipaaspace.com</a>.")
		}
		try {
			def url = new URL("http://www.HIPAASpace.com/api/ndc/search?q=${q?.encodeAsURL()}&rt=xml&key=${hipaaspaceApiKey}")
			def connection = url.openConnection()
			if(connection.responseCode == 200){
				def xml = connection.content.text
				//println xml

				return processNdcProducts(xml)

				//search.results << product
			}
		} catch (Exception e) {
			log.error("Error trying to get products from NDC API ", e);
			throw new ApiException(message: "Unable to query NDC database: " + e.message)
		}
	}

	def processNdcProducts(xml) {
		def results = []
		def ndcList = new XmlParser(false, true).parseText(xml)
		ndcList.NDC.each {
			ndc ->

			if (ndc.NDCCode) {
				println "NDC: " + ndc
				def product = new ProductDetailsCommand()
				product.title = ndc.PackageDescription.text()
				product.ndcCode = ndc.NDCCode
				product.productType = ndc.ProductTypeName.text()
				product.packageDescription = ndc.PackageDescription.text()
				product.ndcCode = ndc.NDCCode.text()
				product.productNdcCode = ndc.ProductNDC.text()
				product.labelerName = ndc.LabelerName.text()
				product.strengthNumber = ndc.StrengthNumber.text()
				product.strengthUnit = ndc.StrengthUnit.text()
				product.pharmClasses = ndc.PharmClasses.text()
				product.dosageForm = ndc.DosageFormName.text()
				product.route = ndc.RouteName.text()
				product.proprietaryName = ndc.ProprietaryName.text()
				product.nonProprietaryName = ndc.NonProprietaryName.text()
				results << product
			}
		}
		return results;
	}


	/**
	 *	<rxnormdata>
	 * 		<displayTermsList>
	 * 			<term></term>
	 * 		</displayTermsList>
	 * 	</rxnormdata>	
	 * @return
	 */

	def findRxNormDisplayNames() {
		String url = "http://rxnav.nlm.nih.gov/REST/displaynames"
		return processXml(url, "displayTermsList.term")
	}

	def processXml(urlString, itemName) {
		try {
			def results = []
			println "URL " + urlString
			def url = new URL(urlString)
			def connection = url.openConnection()
			if (connection.responseCode == 200) {
				def xml = connection.content.text

				def list = new XmlParser(false, true).parseText(xml)
				for (item in list.displayTermsList.term) {
					println "item: " + item.class.name
					results << item.text()
				}
				return results
			}
		} catch (Exception e) {
			log.error("Error trying to get products from NDC API ", e);
			throw e
		}
	}

	/**
	 * @param q
	 * @return
	 */
	def findGoogleProducts(search) {

		def googleProductSearchKey = grailsApplication.config.google.api.key
		if (!googleProductSearchKey) {
			throw new ApiException(message: "Your administrator must specify Google API key (google.api.key) in configuration file (openboxes-config.properties).  For more information, see Google's <a href='https://developers.google.com/shopping-search/v1/getting_started#getting-started' target='_blank'>Getting Started</a> guide")
		}
		def products = new ArrayList();
		int startIndex = search.startIndex;
		String q = search.searchTerms;
		boolean spellingEnabled = search.spellingEnabled

		def urlString = "https://www.googleapis.com/shopping/search/v1/public/products?" +
		"key=${googleProductSearchKey}&country=US&q=${q.encodeAsURL()}&alt=atom&crowdBy=brand:1";
		if (startIndex > 0) {
			urlString += "&startIndex=" + startIndex
		}
		if (spellingEnabled) {
			urlString += "&spelling.enabled=true"
		}
		def url = new URL(urlString)
		def connection = url.openConnection()
		if(connection.responseCode == 200){
			def xml = connection.content.text
			def feed = new XmlParser(false, true).parseText(xml)

			def ns = new Namespace("http://www.google.com/shopping/api/schemas/2010", "s")
			def openSearch = new Namespace("http://a9.com/-/spec/opensearchrss/1.0/", "openSearch")

			search.totalResults = Integer.valueOf(feed[openSearch.totalResults].text())
			search.startIndex = Integer.valueOf(feed[openSearch.startIndex].text())
			search.itemsPerPage = Integer.valueOf(feed[openSearch.itemsPerPage].text())

			feed.entry.each {
				entry ->

				def product = new ProductDetailsCommand()

				product.link = entry[ns.product][ns.link].text()
				product.author = entry.author.name.text()


				entry.link.each {
					link ->
					product.links[link.'@rel'] = link.'@href'
				}
				//println "categories: " + entry[ns.product][ns.categories][ns.category]

				product.id = entry.id.text()
				product.googleId = entry[ns.product][ns.googleId].text()
				product.title = entry[ns.product][ns.title].text()
				product.description = entry[ns.product][ns.description].text()
				product.brand = entry[ns.product][ns.brand].text()
				//product.gtins << entry[ns.product][ns.gtin].text()
				// HACK iterates over all images, but only keeps the last one
				// Need to add these to product->documents
				//def imageLinks = ""
				product.gtin = entry[ns.product][ns.gtin].text()
				entry[ns.product][ns.gtins][ns.gtin].each { gtin ->
					product.gtins << gtin.text()
				}
				entry[ns.product][ns.images][ns.image].each { image ->
					product.images << image.'@link'
				}
				search.results << product
			}
		}
		else {
			log.info("URL: " + url)
			log.info("Response Code: " + connection.responseCode)
			log.info("Response Message: " + connection.responseMessage)
			//log.info("Response: " + connection.content)
			throw new ApiException("Unable to connect to Google Product Search API using connection URL [" + urlString + "]: " + connection.responseMessage)
		}

	}


	/**
	 * @param searchTerms
	 * @param categories
	 * @return
	 */
	List<Product> findProducts(List searchTerms) {
		// Get all products, including hidden ones
		def products = Product.list()
		def searchResults = Product.createCriteria().list() {
			or {
				or {
					searchTerms.each {
						ilike("name", "%" + it + "%")
					}
				}
				or {
					searchTerms.each {
						ilike("manufacturer", "%" + it + "%")
					}
				}
				or {
					searchTerms.each {
						ilike("manufacturerCode", "%" + it + "%")
					}
				}
				or {
					searchTerms.each {
						ilike("productCode", "%" + it + "%")
					}
				}
			}
		}
		searchResults = products.intersect(searchResults);

		//if (!showHiddenProducts) {
		//   searchResults.removeAll(getHiddenProducts())
		//}

		// now localize to only match products for the current locale
		// TODO: this would also have to handle the category filtering
		//  products = products.findAll { product ->
		//  def localizedProductName = getLocalizationService().getLocalizedString(product.name);  // TODO: obviously, this would have to use the actual locale
		// return productFilters.any {
		//   localizedProductName.contains(it)  // TODO: this would also have to be case insensitive
		// }
		// }
		return searchResults;
	}

    /*
    def getProducts(List ids) {
        return getProducts(ids.toArray())

    }
    */

    List<Product> getProducts(String query, Category category, List<Tag> tags, params) {
        //def params = [name: query]

        return getProducts(category, tags, params)

    }

    /**
     * Get all products that match the given product identifiers.
     *
     * @param ids
     * @return
     */
	List<Product> getProducts(String [] ids) {
		def products = []
		if (ids) {
			products = Product.createCriteria().list() { 'in'("id", ids) }
		}
		return products
	}

    /**
     * Get all products that match category, tags, and other search parameters.
     *
     * @param category
     * @param tags
     * @param params
     * @return
     */
    def getProducts(Category category, List<Tag> tagsInput, Map params) {
        println "get products where category=" + category + ", tags=" + tagsInput + ", params=" + params

        def criteria = Product.createCriteria()
        def results = criteria.list(max:params.max?:10, offset:params.offset?:0, sort:params.sort?:"name", order:params.order?:"asc") {
            and {
                if (category) {
                    if (params.includeCategoryChildren) {
                        def categories = category.children?:[]
                        categories << category
                        //categories = categories.collect { it.id }

                        println "Categories to search in " + categories
                        'in'("category", categories)
                    }
                    else {
                        println "Equality search " + category
                        eq("category", category)
                    }
                }



                if (tagsInput) {
                    tags {
                        'in'("id", tagsInput.collect { it.id } )
                    }
                }

                or {
                    if (params.name) ilike("name", params.name + "%")
                    if (params.description) ilike("description", params.description + "%")
                    if (params.brandName) ilike("brandName", "%" + params?.brandName?.trim() + "%")
                    if (params.manufacturer) ilike("manufacturer", "%" + params?.manufacturer?.trim() + "%")
                    if (params.manufacturerCode) ilike("manufacturerCode", "%" + params?.manufacturerCode?.trim() + "%")
                    if (params.vendor) ilike("vendor", "%" + params?.vendor?.trim() + "%")
                    if (params.vendorCode) ilike("vendorCode", "%" + params?.vendorCode?.trim() + "%")
                    if (params.productCode) ilike("productCode", "%" + params.productCode + "%")
                    if (params.unitOfMeasure) ilike("unitOfMeasure", "%" + params.unitOfMeasure + "%")
                    if (params.createdById) eq("createdBy.id", params.createdById)
                    if (params.updatedById) eq("updatedBy.id", params.updatedById)

                    if (params.unitOfMeasureIsNull) isNull("unitOfMeasure")
                    if (params.productCodeIsNull) isNull("productCode")
                    if (params.brandNameIsNull) isNull("brandName")
                    if (params.manufacturerIsNull) isNull("manufacturer")
                    if (params.manufacturerCodeIsNull) isNull("manufacturerCode")
                    if (params.vendorIsNull) isNull("vendor")
                    if (params.vendorCodeIsNull) isNull("vendorCode")
                }
            }
        }

        return results
    }

    /**
     * Get the root category.
     *
     * @return
     */
	Category getRootCategory() {
		def rootCategory = Category.getRootCategory()
		if (!rootCategory) { 
			def categories = Category.findAllByParentCategoryIsNull();
			if (categories && categories.size() == 1) {
				rootCategory = categories.get(0);
			}
			else {
				rootCategory = new Category();
				rootCategory.categories = [];
				categories.each { rootCategory.categories << it; }
			}
		}
		return rootCategory;
	}

	List getCategoryTree() {
		return Category.list();
	}

	List getQuickCategories() {
		List quickCategories = new ArrayList();
		String quickCategoryConfig = grailsApplication.config.inventoryBrowser.quickCategories;
		if (quickCategoryConfig) {
			quickCategoryConfig.split(",").each {
				Category c = Category.findByName(it);
				if (c != null) {
					quickCategories.add(c);
				}
			};
		}
		return quickCategories;
	}

    /**
     * Validate the data in the given import command object.
     *
     * @param command
     */
	public void validateData(ImportDataCommand command) {
		log.info "validate data test "
		// Iterate over each row and validate values
		command?.data?.each { Map params ->
			//log.debug "Inventory item " + importParams
			log.info "validate data " + params
			//command?.data[0].newField = 'new field'
			//command?.data[0].newDate = new Date()
			params.prompts = [:]
			params.prompts["product.id"] = Product.findAllByNameLike("%" + params.search1 + "%")

			//def lotNumber = (params.lotNumber) ? String.valueOf(params.lotNumber) : null;
			//if (params?.lotNumber instanceof Double) {
			//	errors.reject("Property 'Serial Number / Lot Number' with value '${lotNumber}' should be not formatted as a Double value");
			//}
			//else if (!params?.lotNumber instanceof String) {
			//	errors.reject("Property 'Serial Number / Lot Number' with value '${lotNumber}' should be formatted as a Text value");
			//}


		}

	}

    /**
     * Import the data in the given import command object
     * @param command
     */
	public void importData(ImportDataCommand command) {
		log.info "import data"

		try {
			// Iterate over each row
			command?.data?.each { Map params ->

				log.info "import data " + params

				/*
				 // Create product if not exists
				 Product product = Product.findByName(params.productDescription);
				 if (!product) {
				 product = new Product(params)
				 product.name = params.productDescription
				 //upc:params.upc,
				 //ndc:params.ndc,
				 //category:category,
				 //manufacturer:manufacturer,
				 //manufacturerCode:manufacturerCode,
				 //unitOfMeasure:unitOfMeasure);
				 if (!product.save()) {
				 command.errors.reject("Error saving product " + product?.name)
				 }
				 //log.debug "Created new product " + product.name;
				 }
				 */
			}

		} catch (Exception e) {
			log.error("Error importing data ", e);
			throw e;
		}

	}

    /**
     * Search product and product groups by name.
     *
     * @param term
     * @return
     */
    public def searchProductAndProductGroup(String term) {
        return searchProductAndProductGroup(term, false)
    }

	/**
	 * Search product and product groups by name using wildcard search.
     *
	 * @param term
	 * @return
	 */
	public def searchProductAndProductGroup(String term, Boolean wildcards){
		long startTime = System.currentTimeMillis()
		def text = (wildcards) ? "%${term.toLowerCase()}%" : "${term.toLowerCase()}%"
		def products = Product.executeQuery(
			"""select p.id, p.name, p.productCode 
				from Product as p 				
				where lower(p.name) like ? 
				or lower(p.productCode) like ?""", [text, text])
		// products.each{ println it}
		println " * Search product and product group: " + (System.currentTimeMillis() - startTime) + " ms"
		
		return products
	}

	/**
     * Get the column delimiter used in the given data set.
     *
	 * @param data
	 * @return
	 */
	public String getDelimiter(String data) {
		// Check to make sure the format is comma-separated
		def lines = data.split("\n")
		def delimiters = [",", "\t", ";"]
		for (def delimiter : delimiters) {
			def headerColumns = lines[0].split(delimiter)
            println "*** Actual:   " + headerColumns + " [" + headerColumns.size() + "]"
			if (headerColumns.size() == Constants.EXPORT_PRODUCT_COLUMNS.size()) {
				return delimiter
			}
		}			
		throw new RuntimeException("""Invalid file format: File must contain the following columns: ${Constants.EXPORT_PRODUCT_COLUMNS};
            Columns must be separated by a comma (,) or tab (\\t);
            lines must be separated by a linefeed (\\n); If you're using Mac Excel, save the file as Windows Comma Separated (.csv) and upload again.""")
	}
	
	/**
     * Get a list of columns for the data set using the default column delimiter.
     *
	 * @param csv
	 * @return
	 */
	public List<String> getColumns(String csv) {
		def delimiter = getDelimiter(csv)
		return getColumns(csv, delimiter)
	}
	
	/**
     * Get a list of columns for the data set using the given column delimiter.
     *
	 * @param csv
	 * @param delimiter
	 * @return
	 */
	public List<String> getColumns(String csv, String delimiter) {
		// Check to make sure the format is comma-separated
		def lines = csv.split("\n")
		def columns = lines[0].split(delimiter)
		if (columns.size() != Constants.EXPORT_PRODUCT_COLUMNS.size()) {
			throw new RuntimeException("File must contain the following columns:" + Constants.EXPORT_PRODUCT_COLUMNS)
		}
		return columns;
	}


	/**
	 * Gets a list of products that already exist in the database
	 * @param csv
	 * @return
	 */
	public List<Product> getExistingProducts(String csv) { 
		def delimiter = getDelimiter(csv)
		return getExistingProducts(csv, delimiter)
	}
	
	/**
	 * Gets a list of products that already exist in the database.
     *
	 * @param csv
	 * @param delimiter
	 * @return
	 */
	public List<Product> getExistingProducts(String csv, String delimiter) {
		def products = new ArrayList<Product>()
		
		// Iterate over each line and either update an existing product or create a new product
		csv.toCsvReader(['skipLines':1, 'separatorChar':delimiter]).eachLine { tokens ->
			def product = Product.findByIdOrProductCode(tokens[0], tokens[1])
            println "GET EXISTING PRODUCT " + tokens[0] + " OR " + tokens[1]
			//def product = Product.findById(tokens[0])
			if (product) {
				product = Product.get(product.id)
                println "FOUND EXISTING PRODUCT " + product?.id + " " + product?.name
				products << product
			}
		}
		return products

	}



	/**
	 * Import products from csv
	 * 
	 * ID,Name,Category,Description,Product Code,Unit of Measure,Manufacturer,Manufacturer Code,Cold Chain,UPC,NDC,Date Created,Date Updated
	 * 
	 * @param csv
	 */
	public List validateProducts(String csv) {
		return validateProducts(csv, getDelimiter(csv))
	}
	
	/**
	 * 
	 * @param csv
	 * @param delimiter
	 * @param saveToDatabase
	 * @return
	 */
	public List validateProducts(String csv, String delimiter) {
		println "CSV: " + csv
		
		def products = []
		if (!csv) {
			throw new RuntimeException("CSV cannot be empty")
		}

		// Check to make sure the format is comma-separated
		def lines = csv.split("\n")
		def columns = lines[0].split(delimiter)
		if (columns.size() != Constants.EXPORT_PRODUCT_COLUMNS.size()) {
			throw new RuntimeException("Invalid data format")
		}
		
		def rowCount = 1;

		// Iterate over each line and either update an existing product or create a new product
		csv.toCsvReader(['skipLines':1, 'separatorChar':delimiter]).eachLine { tokens ->
			
			rowCount++
			println "Processing line: " + tokens
			def productId = tokens[0]
			def productCode = tokens[1]
			def productName = tokens[2]
			def categoryName = tokens[3]
			def description = tokens[4]
			def unitOfMeasure = tokens[5]
            def productTags = tokens[6]?.split(",")
            def unitPrice
            try {
                unitPrice = tokens[7]?Float.valueOf(tokens[7]):null
            } catch (NumberFormatException e) {
                throw new RuntimeException("Unit price for product '${productCode}' at row ${rowCount} must be a valid decimal (value = '${tokens[7]}')", e)
            }
			def manufacturer = tokens[8]
			def brandName = tokens[9]
			def manufacturerCode = tokens[10]
			def manufacturerName = tokens[11]
			def vendor = tokens[12]
			def vendorCode = tokens[13]
			def vendorName = tokens[14]
			def coldChain = Boolean.valueOf(tokens[15])
			def upc = tokens[16]
			def ndc = tokens[17]
			//def dateCreated = tokens[11]?Date.parse("dd/MMM/yyyy hh:mm:ss", tokens[11]):null
			//def dateUpdated = tokens[12]?Date.parse("dd/MMM/yyyy hh:mm:ss", tokens[12]):null

			if (!productName) {
				throw new RuntimeException("Product name cannot be empty at row " + rowCount)
			}

			def category = findOrCreateCategory(categoryName)
			def product = Product.findByIdOrProductCode(productId, productCode)

            // If the identifier is incorrect/missing we should display the ID of the product found using the product code instead of the missing/incorrect product identifier
            products << [id: product?.id?:productId, name: productName, category: category, description: description,
                    productCode: productCode, upc: upc, ndc: ndc, coldChain: coldChain, pricePerUnit: unitPrice,tags:productTags,
                    unitOfMeasure: unitOfMeasure, manufacturer: manufacturer, manufacturerCode: manufacturerCode, brandName: brandName,
                    manufacturerName: manufacturerName, vendor: vendor, vendorCode: vendorCode, vendorName: vendorName, product: product]

			
		}

		return products;
	}


    def importProducts(products) {
        return importProducts(products, null)
    }

    def importProducts(products, tags) {
        log.info ("Importing products " + products + " tags: " + tags)

		// Create all tags that don't already exist
        tags.each { tagName ->
			Tag tag = Tag.findByTag(tagName)
			if (!tag) {
				log.info "Tag ${tagName} does not exist so creating it"
				tag = new Tag(tag: tagName)
				tag.save(flush:true)
			}
        }

        products.each { productProperties ->

            log.info "Import product code = " + productProperties.productCode + ", name = " + productProperties.name
            // Update existing
            def product = Product.findByIdOrProductCode(productProperties.id, productProperties.productCode)
            if (product) {
                product.properties = productProperties
            }
            // ... or create a new product
            else {
                product = new Product(productProperties)
            }

            if (!product.validate()) {
                throw new ValidationException("Product is invalid", product.errors)
            }

            addTagsToProduct(product, tags)
            addTagsToProduct(product, productProperties.tags)

            if (!product.save(flush:true)) {
                throw new ValidationException("Could not save product '" + product.name + "'", product.errors)
            }
        }
    }


	/**
	 * Export all products in the database.
     *
	 * @return
	 */
	String exportProducts() {
        def products = Product.list()
		return exportProducts(products)
	}

	
	/**
	 * Export given products.
     *
	 * @param products
	 * @return
	 */
	String exportProducts(products) {
		def formatDate = new SimpleDateFormat("dd/MMM/yyyy hh:mm:ss")
		def sw = new StringWriter()
		
		def csvWriter = new CSVWriter(sw, {
			"Product ID" { it.id }
			"Product Code" { it.productCode }
			"Name" { it.name }
			"Category" { it.category }
			"Description" { it.description }
			"Unit of Measure" { it.unitOfMeasure }
            "Tags" { it.tags }
            "Unit price" { it.unitPrice }
			"Manufacturer" { it.manufacturer }
			"Brand" { it.brandName }
			"Manufacturer Code" { it.manufacturerCode }
			"Manufacturer Name" { it.manufacturerName }			
			"Vendor" { it.vendor }
			"Vendor Code" { it.vendorCode }
			"Vendor Name" { it.vendorName }
			"Cold Chain" { it.coldChain }
			"UPC" { it.upc }
			"NDC" { it.ndc }
			"Date Created" { it.dateCreated }
			"Date Updated" { it.lastUpdated }
		})
		
		products.each { product ->
			def row =  [
				id: product?.id,
				productCode: product.productCode?:'',
				name: product.name,
				category: product?.category?.name,
				description: product?.description?:'',
				unitOfMeasure: product.unitOfMeasure?:'',
                tags: product.tagsToString()?:'',
                unitPrice: product.pricePerUnit?:'',
				manufacturer: product.manufacturer?:'',
				brandName: product.brandName?:'',
				manufacturerCode: product.manufacturerCode?:'',
				manufacturerName: product.manufacturerName?:'',
				vendor: product.vendor?:'',
				vendorCode: product.vendorCode?:'',
				vendorName: product.vendorName?:'',
				coldChain: product.coldChain?:Boolean.FALSE,
				upc: product.upc?:'',
				ndc: product.ndc?:'',
				dateCreated: product.dateCreated?"${formatDate.format(product.dateCreated)}":"",
				lastUpdated: product.lastUpdated?"${formatDate.format(product.lastUpdated)}":"",
			]
			// We just want to make sure that these match because we use the same format to
			// FIXME It would be better if we could drive the export off of this array of columns,
			// but I'm not sure how.  It's possible that the constant could be a map of column
			// names to closures (that might work)
			assert row.keySet().size() == Constants.EXPORT_PRODUCT_COLUMNS.size()
			csvWriter << row
		}
		return sw.toString()
    }
	
	/**
	 * Find or create a category with the given name.
     *
	 * @param categoryName
	 * @return
	 */
	Category findOrCreateCategory(String categoryName) {
		def rootCategory = Category.getRootCategory()

		if (!categoryName)
		return rootCategory

		def category = Category.findByName(categoryName)
		if (!category) {
			category = new Category(parentCategory: rootCategory, name: categoryName)
			category.save(failOnError:true)
		}
		return category;
	}
	
	/**
	 * Find all top-level categories (e.g. children of the root category)
     *
	 * @return
	 */
	def getTopLevelCategories() {
		def rootCategory = Category.getRootCategory()
		return rootCategory ? Category.findAllByParentCategory(rootCategory) : []
	}

	/**
     *
     * Get all unique and active tags in the database.
     *
	 * @return all tag labels
	 */
	def getAllTagLabels() {
		return Tag.findAllByIsActive(true).collect { it.tag }.unique()
	}

	/**
     * Get all active tags in the database.
     *
	 * @return	all tags
	 */
	def getAllTags() { 
		def tags = Tag.findAllByIsActive(true);
        return tags;
	}

	/**
     * Get all popular tags
     *
	 * @return  all tags that have a product
	 */
	def getPopularTags(Integer limit) {
		def popularTags = [:]
		String sql = """
            select tag.id, count(*) as count
            from product_tag
            join tag on tag.id = product_tag.tag_id
            where tag.is_active = true
            group by tag.tag
            order by count(*) desc
            """


        // FIXME Convert the query above to HQL so we don't have to worry about N+1 query below
		def sqlQuery = sessionFactory.currentSession.createSQLQuery(sql)
        if (limit > 0) {
            sqlQuery.setMaxResults(limit)
        }
        def list = sqlQuery.list()
		list.each {
            Tag tag = Tag.load(it[0])
			popularTags[tag] = it[1]
		}
		return popularTags		
	}

	def getPopularTags() {
		return getPopularTags(0)
	}


    /**
     * Add a list of tags to each of the given products.
     *
     * @param products
     * @param tags
     * @return
     */
    def addTagsToProducts(products, tags) {
        log.info "Add tags ${tags} to products ${products}"
        products.each { product ->
            addTagsToProduct(product, tags)
        }
    }
	
	/**
     * Add the list of tags to the given product.
     *
	 * @param product
	 * @param tags
	 */
	def addTagsToProduct(product, tags) {
        log.info "Add tags ${tags} to product ${product}"
		if (tags) {
			tags.each { tagName ->
                if (tagName) {
                    addTagToProduct(product, tagName)
                }
			}
		}
	}

    /**
     * Add the given tag to the given product.
     *
     * @param product
     * @param tagName
     * @return
     */
    def addTagToProduct(product, tagName) {
        log.info "Add tags ${tagName} to product ${product}"

        // Check if the product already has the given tag
        def tag = product.tags.find { it.tag == tagName }

        if (!tag) {
            // Otherwise try to find an existing tag that matches the tag
            //Tag.withNewSession {
            tag = Tag.findByTag(tagName)

            // Or create a brand new one
            if (!tag) {
                log.info "Tag ${tagName} does not exist so creating it"
                tag = new Tag(tag: tagName)
                tag.save(flush:true)
            }
            product.addToTags(tag)
            product.save(flush:true)
        }
        else {
            log.info "Product ${product} already contains tag ${tag}"
        }
    }


	
	/**
	 * Delete a tag from the given product and delete the tag.
     *
	 * @param product
	 * @param tag
	 * @return
	 */
	def deleteTag(product, tag) { 
		product.removeFromTags(tag)
		tag.delete();
	}

    /**
     * Ensure that the given product code does not exist
     *
     * @param productCode
     * @return
     */
	def validateProductIdentifier(productCode) {
        if (!productCode) return false
        def count = Product.executeQuery( "select count(p.productCode) from Product p where productCode = :productCode", [productCode: productCode] );
        return count ? (count[0] == 0) : false
    }
	
	/**
	 * Generate a product identifier.
     *
	 * @return
	 */
	def generateProductIdentifier() {
        def productCode

        try {
            productCode = identifierService.generateProductIdentifier()
            if (validateProductIdentifier(productCode)) {
                return productCode
            }

        } catch (Exception e) {
            log.warn("Error generating unique product code " + e.message, e)
        }
        return productCode
	}

    /**
     * Download a document at the given URL.
     *
     * @param url
     */
	def downloadDocument(url) { 
		// move code from ProductController		
	}

    /**
     * Save the given product
     *
     * @param product
     * @return
     */
    def saveProduct(Product product) {
        return saveProduct(product, null)
    }

	/**
	 * Saves the given product 
	 * @param product
     * @param tags
     *
	 * @return
	 */
	def saveProduct(Product product, String tags) {
		//def productInstance = Product.get(product.id)
		if (product) {

            // Generate product code if it doesn't already exist
            if (!product.productCode) {
                product.productCode = generateProductIdentifier();
            }
			// Handle tags
			try {
				if (tags) {
					tags.split(",").each { tagText ->
                        def tag = findOrCreateTag(tagText)
                        if (tag) {
                            product.addToTags(tag)
                        }
					}
				}
			} catch (Exception e) {
				log.error("Error occurred: " + e.message)
				throw new ValidationException(e.message, product?.errors)
			}
			
			// Handle attributes
			/*
			Map existingAtts = new HashMap();
			productInstance.attributes.each() {
				existingAtts.put(it.attribute.id, it)
			}
			Attribute.list().each() {
				String attVal = params["productAttributes." + it.id + ".value"]
				if (attVal == "_other" || attVal == null || attVal == '') {
					attVal = params["productAttributes." + it.id + ".otherValue"]
				}
				ProductAttribute existing = existingAtts.get(it.id)
				if (attVal != null && attVal != '') {
					if (!existing) {
						existing = new ProductAttribute(["attribute":it])
						productInstance.attributes.add(existing)
					}
					existing.value = attVal;
				}
				else {
					productInstance.attributes.remove(existing)
				}
			}
			*/

			/*
			log.info("Categories " + productInstance?.categories);

			// find the phones that are marked for deletion
			def _toBeDeleted = productInstance.categories.findAll {(it?.deleted || (it == null))}

			log.info("toBeDeleted: " + _toBeDeleted )

			// if there are phones to be deleted remove them all
			if (_toBeDeleted) {
				productInstance.categories.removeAll(_toBeDeleted)
			}
			*/
			
			//if (!product.validate()) {
			//	throw new ValidationException("Product is not valid", product.errors)
			//}

			return product.save(flush: true)
		}
	}

    /**
     * Find or create a tag with the given tag text.
     *
     * @param tagText
     * @return
     */
    def findOrCreateTag(tagText) {
        Tag tag = Tag.findByTagAndIsActive(tagText, true)
        if (!tag) {
            tag = new Tag(tag:tagText)
            tag.save();
        }
        return tag;
    }

    /**
     * Find products that are similar to the given product.
     *
     * @param product
     * @return
     */
	def findSimilarProducts(Product product) { 
		def similarProducts = []
		/*
		def productsInSameProductGroup = ProductGroup.findByProduct(product).products
		if (productsInSameProductGroup) {
			similarProducts.addAll(productsInSameProductGroup)
		}
		*/;
		/*
		def productsInSameCategory = Product.findByCategory(product.category)
		if (productsInSameCategory) { 
			similarProducts.addAll(productsInSameCategory)
		}*/
		def searchTerms = product.name.split(",")
		if (searchTerms) { 
			similarProducts.addAll(Product.findAllByNameLike("%" + searchTerms[0] +"%"))
		}
		/*
		if (!similarProducts) { 
			searchTerms = product.name.split(" ")
			searchTerms.each {
				similarProducts.addAll(Product.findAllByNameLike("%" + it +"%"))
			}
		}
		*/
		similarProducts.unique()

		similarProducts.remove(product)
		
		return similarProducts
	}


	Product addProductComponent(String assemblyProductId, String componentProductId, BigDecimal quantity, String unitOfMeasureId) {
		def assemblyProduct = Product.get(assemblyProductId)
		if (assemblyProduct) {
			def componentProduct = Product.get(componentProductId)
			if (componentProduct) {
				def unitOfMeasure = UnitOfMeasure.get(unitOfMeasureId)
                log.info "Adding " + componentProduct.name + " to " + assemblyProduct.name

				ProductComponent productComponent = new ProductComponent(componentProduct: componentProduct,
                        quantity: quantity, unitOfMeasure: unitOfMeasure, assemblyProduct: assemblyProduct)
				assemblyProduct.addToProductComponents(productComponent)
                assemblyProduct.save(flush:true, failOnError: true)
			}
		}
		return assemblyProduct
	}


}

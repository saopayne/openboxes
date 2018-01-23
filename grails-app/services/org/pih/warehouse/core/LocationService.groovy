/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/ 
package org.pih.warehouse.core

import org.apache.commons.collections.comparators.NullComparator
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import util.ConfigHelper

import javax.xml.bind.ValidationException;

// import java.text.DecimalFormat
// import java.text.SimpleDateFormat

class LocationService {
	
	def grailsApplication
	boolean transactional = true
	
	
	def getAllLocations() {
		return Location.findAllByActiveAndParentLocationIsNull(true);
	}

	def getLoginLocations(Integer currentLocationId) {
		return getLoginLocations(Location.get(currentLocationId))
	}
	
	def getLoginLocations(Location currentLocation) {
        log.info "Get login locations (currentLocation=${currentLocation?.name})"

        // Get all locations that match the required activity (using inclusive OR)
		def locations = new HashSet()
		def requiredActivities = ConfigHelper.listValue(grailsApplication.config.openboxes.chooseLocation.requiredActivities)
		if (requiredActivities) {
			requiredActivities.each { activity ->
				locations += getAllLocations()?.findAll { it.supports(activity) }
			}			
		}
		return locations
	}


	Map getLoginLocationsMap(Location currentLocation) {
        log.info "Get login locations map (currentLocation=${currentLocation?.name})"
        def locationMap = [:]
        def nullHigh = new NullComparator(true)
        def locations = getLoginLocations(currentLocation)
        if (locations) {

			locations = locations.collect { [id: it?.id, name: it?.name, locationType: it.locationType?.name, locationGroup: it?.locationGroup?.name,  ]}
            locationMap = locations.groupBy { it?.locationGroup }
            locationMap = locationMap.sort { a, b -> nullHigh.compare(a?.key, b?.key) }
        }
        return locationMap;
        //return getLoginLocations(currentLocation).sort { it?.locationGroup }.reverse().groupBy { it?.locationGroup }
	}
	
	List getDepots() {
		return getAllLocations()?.findAll { it.supports(ActivityCode.MANAGE_INVENTORY) }
	}

	List getNearbyLocations(Location currentLocation) { 
		return Location.findAllByActiveAndLocationGroup(true, currentLocation.locationGroup)
	}
	
	List getExternalLocations() { 
		return getAllLocations()?.findAll { it.supports(ActivityCode.EXTERNAL) } 
	}
	
	List getDispensaries(Location currentLocation) { 
		return getNearbyLocations(currentLocation)?.findAll { it.supports(ActivityCode.RECEIVE_STOCK) && !it.supports(ActivityCode.EXTERNAL) } 
	}
	
	List getLocationsSupportingActivity(ActivityCode activity) { 
		return getAllLocations()?.findAll { it.supports(activity) }
		
	}
	
	List getShipmentOrigins() { 
		return getLocationsSupportingActivity(ActivityCode.SEND_STOCK)
	}
	
	List getShipmentDestinations() {
		return getLocationsSupportingActivity(ActivityCode.RECEIVE_STOCK)
	}

	List getOrderSuppliers(Location currentLocation) {
		return getLocationsSupportingActivity(ActivityCode.FULFILL_ORDER) - currentLocation
	}

	List getRequestOrigins(Location currentLocation) {
		return getLocationsSupportingActivity(ActivityCode.FULFILL_REQUEST)// - currentLocation
	}

	List getRequestDestinations(Location currentLocation) {
		return getLocationsSupportingActivity(ActivityCode.FULFILL_REQUEST)// - currentLocation
	}

	List getTransactionSources(Location currentLocation) { 
		return getLocationsSupportingActivity(ActivityCode.SEND_STOCK) - currentLocation
	}
	
	List getTransactionDestinations(Location currentLocation) { 
		// Always get nearby locations		
		def locations = getNearbyLocations(currentLocation);		
		
		// Get all external locations (if supports external) 
		if (currentLocation.supports(ActivityCode.EXTERNAL)) { 			
			locations += getExternalLocations();			
		}

		// Of those locations remaining, we need to return only locations that can receive stock
		locations = locations.findAll { it.supports(ActivityCode.RECEIVE_STOCK) }
		
		// Remove current location from list
		locations = locations?.unique() - currentLocation

		return locations
		
	}

	boolean importBinLocations(String locationId, InputStream inputStream) {
		try {

			Location location = Location.get(locationId)
			if (!location) {
				throw new ValidationException("location.cannotImportBinLocationsWithoutParentLocation.message")
			}

			LocationType defaultLocationType = LocationType.findByLocationTypeCode(LocationTypeCode.BIN_LOCATION)
			if (!defaultLocationType) {
				throw new ValidationException("locationType.noDefaultForBinLocation.message")
			}

			List binLocations = parseBinLocations(inputStream)
			log.info "Bin locations " + binLocations
			if (binLocations) {
				binLocations.each {
					Location binLocation = Location.findByNameAndParentLocation(it.name, location)
					if (!binLocation) {
						binLocation = new Location()
						binLocation.name = it.name
						binLocation.locationNumber = it.name
						binLocation.parentLocation = location
						binLocation.locationType = defaultLocationType
						location.addToLocations(binLocation)
					}
					else {
						log.info "Bin location ${it.name} already exists"
					}
				}
				location.save()

			} else {
				throw new ValidationException("location.cannotImportEmptyBinLocations.message")
			}
		} catch (Exception e) {
			log.error("Unable to bin locations due to exception: " + e.message, e)
			throw new RuntimeException(e.message)
		}
		finally {
			inputStream.close();
		}

		return true
	}


	List parseBinLocations(InputStream inputStream) {

		List binLocations = []

		HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
		HSSFSheet worksheet = workbook.getSheetAt(0);

		Iterator<Row> rowIterator = worksheet.iterator();
		int cellIndex = 0
		Row row
		while (rowIterator.hasNext()) {
			row = rowIterator.next();

			// Skip the first row
			if (row.getRowNum() == 0) {
				continue
			}

			try {
				cellIndex = 0;
				def name = getStringCellValue(row.getCell(cellIndex++))
				binLocations << [name: name]
			}
			catch (IllegalStateException e) {
				log.error("Error parsing XLS file " + e.message, e)
				throw new RuntimeException("Error parsing XLS file at row " + (row.rowNum+1) + " column " + cellIndex + " caused by: " + e.message, e)
			}
			catch (Exception e) {
				log.error("Error parsing XLS file " + e.message, e)
				throw new RuntimeException("Error parsing XLS file at row " + (row.rowNum+1) + " column " + cellIndex + " caused by: " + e.message, e)

			}


		}
		return binLocations
	}

	String getStringCellValue(Cell cell) {
		String value = null
		if (cell) {
			try {
				value = cell.getStringCellValue()
			}
			catch (IllegalStateException e) {
				log.warn("Error parsing string cell value [${cell}]: " + e.message, e)
				value = Integer.valueOf((int) cell.getNumericCellValue())
			}
		}
		return value?.trim()
	}



}

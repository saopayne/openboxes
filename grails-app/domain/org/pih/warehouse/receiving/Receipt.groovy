/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/ 
package org.pih.warehouse.receiving

import org.pih.warehouse.core.Person
import org.pih.warehouse.shipping.Shipment

// import java.io.Serializable;
// import java.util.Date

class Receipt implements Serializable {

	String id
	Date expectedDeliveryDate			 
	Date actualDeliveryDate				 
	Person recipient					
	Date dateCreated;
	Date lastUpdated;

	static hasMany = [ receiptItems : ReceiptItem ]
	static belongsTo = [ shipment : Shipment ]
	
	static mapping = {
		id generator: 'uuid'
		receiptItems cascade: "all-delete-orphan"
	}

	// Constraints
	static constraints = {
		expectedDeliveryDate(nullable:true)
		actualDeliveryDate(nullable:false, 
			// can't be delivered in the future
			// can't be delivered before it is shipped!
			validator: { value, obj-> 
				//println "max: " + it + " <= " + new Date();				
				//println "obj.shipment.actualShippingDate is notNull: " + obj.shipment.actualShippingDate 
				//println "value + 1: " + (value + 1) 
				//println "(value + 1).after(obj.shipment.actualShippingDate): " + (value + 1).after(obj.shipment.actualShippingDate) 
				if (!(value <= new Date())) {
					//println "value <= new Date(): " + (value <= new Date())
					return ["invalid.mustOccurOnOrBeforeToday", value, new Date()]
				}				
				if (!(value + 1).after(obj.shipment.actualShippingDate)) { 
					return ["invalid.mustOccurOnOrAfterActualShippingDate", value, obj.shipment.actualShippingDate]
				}
			}
		)
		recipient(nullable:true)
	}
	
}

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

// import java.util.Date

class Person implements Comparable, Serializable {
    
	String id;
	String firstName;
	String lastName;
	String email;
	String phoneNumber;
	Date dateCreated;
	Date lastUpdated;
	
	static mapping = { 
		tablePerHierarchy false
		table 'person' 
		id generator: 'uuid'
	}

	static transients = ["name"]
	
	static constraints = { 
		firstName(blank:false, maxSize: 255)
		lastName(blank:false, maxSize: 255)	
		phoneNumber(nullable:true, maxSize: 255)
		email(blank:false, unique: true, email: true, maxSize: 255)
	}
	
	int compareTo(Object obj) {

		def sortOrder =
			lastName <=> obj.lastName?:
				firstName <=> obj.firstName ?:
					email <=> obj.email ?:
						id <=> obj?.id
		return sortOrder;

	}
	
	String toString() {
		return "$firstName $lastName";
	}
	String getName() {
		return "$firstName $lastName";
	}
	
}

/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 * */
package org.pih.warehouse.requisition

import org.pih.warehouse.auth.AuthService;
import org.pih.warehouse.core.Comment;
import org.pih.warehouse.core.Document;
import org.pih.warehouse.core.Event;


import org.pih.warehouse.core.Location;
import org.pih.warehouse.core.Person;
import org.pih.warehouse.core.User;
import org.pih.warehouse.fulfillment.Fulfillment
import org.pih.warehouse.inventory.Transaction
import org.pih.warehouse.picklist.Picklist;

class Requisition implements Comparable<Requisition>, Serializable {

    def beforeInsert = {
        def currentUser = AuthService.currentUser.get()
        if (currentUser) {
            createdBy = currentUser
            updatedBy = currentUser
        }
    }
    def beforeUpdate = {
        def currentUser = AuthService.currentUser.get()
        if (currentUser) {
            updatedBy = currentUser
        }
    }

    String id
    String name
    String description         // a user-defined, searchable name for the order
    String requestNumber     // an auto-generated reference number

    // Dates
    Date dateRequested = new Date()
    Date dateReviewed
    Date dateVerified
    Date dateChecked
    Date dateDelivered
    Date dateIssued
    Date dateReceived

    Date requestedDeliveryDate = new Date()

    // Frequency - for stock requisitions we should know how often (monthly, weekly, daily)

    // Requisition type, status, and commodity class
    RequisitionType type;
    RequisitionStatus status;
    CommodityClass commodityClass

    // where the requisition came from
    Location origin

    // who the requisition will be fulfilled by
    Location destination

    // Person who submitted the initial requisition paper form
    Person requestedBy

    // Person who reviewed the requisition
    Person reviewedBy

    // Pharmacist who verified the requisition before it was issued
    Person verifiedBy

    // Person who reviewed the requisition
    Person checkedBy

    // Pharmacist or nurse who signed for the issued stock
    Person deliveredBy

    // Pharmacist or nurse who signed for the issued stock
    Person issuedBy

    // Pharmacist or nurse who signed for the issued stock
    Person receivedBy

    // Intended recipient
    Person recipient

    // Intended recipient program
    String recipientProgram

    // Stock requisitions will need to be handled through a template version of a requisition
    Boolean isTemplate = false
    Boolean isPublished = false
    Date datePublished

    // Not used yet
    Date dateValidFrom
    Date dateValidTo

    Fulfillment fulfillment;

    //List requisitionItems

    // Audit fields
    Date dateCreated
    Date lastUpdated
    User createdBy
    User updatedBy
    //String weekRequested
    //String monthRequested
    String monthRequested
    //String yearRequested

    // Removed comments, documents, events for the time being.
    //static hasMany = [ requisitionItems: RequisitionItem, comments : Comment, documents : Document, events : Event ]
    static hasOne = [picklist: Picklist]
    static hasMany = [requisitionItems: RequisitionItem, transactions: Transaction]
    static mapping = {
        id generator: 'uuid'
        requisitionItems cascade: "all-delete-orphan", sort: "orderIndex", order: 'asc', batchSize: 100

        //week formula('WEEK(date_requested)')    //provide the exact column name of the date field
        //month formula('MONTH(date_requested)')
        monthRequested formula: "date_format(date_requested, '%M %Y')"
        //yearRequested formula: "date_format(date_requested, '%Y')"
        //comments cascade: "all-delete-orphan"
        //documents cascade: "all-delete-orphan"
        //events cascade: "all-delete-orphan"
    }

    static constraints = {
        status(nullable: true)
        type(nullable: true)
        name(nullable: true)
        description(nullable: true)
        requestNumber(nullable: true, maxSize: 255)
        origin(nullable: false)
        destination(nullable: false)
        fulfillment(nullable: true)
        recipient(nullable: true)
        requestedBy(nullable: false)
        reviewedBy(nullable: true)
        verifiedBy(nullable: true)
        checkedBy(nullable: true)
        issuedBy(nullable: true)
        deliveredBy(nullable: true)
        receivedBy(nullable: true)
        picklist(nullable: true)
        dateRequested(nullable: false)
        //validator: { value -> value <= new Date()})
        requestedDeliveryDate(nullable: false)

        // FIXME Even though Grails complains that "derived properties may not be constrained", when you remove the constraint there are validation errors on Requisition
        // OB-3180 Derived properties may not be constrained. Property [monthRequested] of domain class org.pih.warehouse.requisition.Requisition will not be checked during validation.
        monthRequested(nullable: true)
        //validator: { value ->
        //    def tomorrow = new Date().plus(1)
        //    tomorrow.clearTime()
        //    return value >= tomorrow
        //})
        dateCreated(nullable: true)
        dateChecked(nullable: true)
        dateReviewed(nullable: true)
        dateVerified(nullable: true)
        dateDelivered(nullable: true)
        dateReceived(nullable: true)
        dateIssued(nullable: true)
        lastUpdated(nullable: true)
        dateValidFrom(nullable: true)
        dateValidTo(nullable: true)
        createdBy(nullable: true)
        updatedBy(nullable: true)
        recipientProgram(nullable: true)
        commodityClass(nullable: true)
        isTemplate(nullable: true)
        isPublished(nullable: true)
        datePublished(nullable: true)
    }

    /*
    def getPicklist() {
        return Picklist.findByRequisition(this)
    }
    */


    //def getTransactions() {
    //    return Transaction.findAllByRequisition(this)
    //}

    def getRequisitionItemCount() {
        return getOriginalRequisitionItems()?.size()
    }


    def calculatePercentageCompleted() {
        def numerator = getCompleteRequisitionItems()?.size()?:0
        def denominator = getInitialRequisitionItems()?.size()?:1
        if (denominator) {
            return (numerator / denominator)*100
        }
        else {
            return 0;
        }
    }

    /**
     * @return  all requisition items that have been completed (canceled or fulfilled)
     */
    def getCompleteRequisitionItems() {
        return initialRequisitionItems?.findAll { it.isCompleted() }
    }

    /**
     * @return  all requisition items that have not been completed
     */
    def getIncompleteRequisitionItems() {
        return initialRequisitionItems?.findAll { !it.isCompleted() }
    }

    /**
     * @return  all requisition items that were apart of the original requisition
     */
    def getInitialRequisitionItems() {
        return requisitionItems?.findAll { !it.parentRequisitionItem }
    }

    def getOriginalRequisitionItems() {
        return requisitionItems?.findAll { it.requisitionItemType == RequisitionItemType.ORIGINAL }
    }

    /**
     * @return  all requisition items that have been added as substitutions or supplements
     */
    def getAdditionalRequisitionItems() {
        return requisitionItems?.findAll { it.parentRequisitionItem }
    }

    Boolean isWardRequisition() {
        return (type in [RequisitionType.NON_STOCK, RequisitionType.STOCK, RequisitionType.ADHOC])
    }

    Boolean isOpen() {
        return (status in [RequisitionStatus.CREATED, RequisitionStatus.EDITING])
    }

    Boolean isPending() {
        return (status in [RequisitionStatus.CREATED, RequisitionStatus.EDITING, RequisitionStatus.VERIFYING, RequisitionStatus.PICKING, RequisitionStatus.PENDING]);
    }

    Boolean isRequested() {
        return (status in [RequisitionStatus.VERIFYING, RequisitionStatus.PICKING, RequisitionStatus.PENDING, RequisitionStatus.ISSUED, RequisitionStatus.RECEIVED])
    }


    /**
     * Sort by sort order, name
     *
     * Sort requisitions by receiving location (alphabetical), requisition type, commodity class (consumables or medications), then date requested, then date created,
     */
    int compareTo(Requisition requisition) {
        return origin <=> requisition.origin ?:
            type <=> requisition.type ?:
                commodityClass <=> requisition.commodityClass ?:
                    requisition.dateRequested <=> dateRequested ?:
                        requisition.dateCreated <=> dateCreated
    }

    String toString() {
        return id
    }

    Requisition newInstance() {
        def requisition = new Requisition()
        requisition.origin = origin
        requisition.destination = destination
        requisition.type = type
        requisition.commodityClass = commodityClass
        requisition.requisitionItems = []
        requisitionItems.each {
            def requisitionItem = new RequisitionItem()
            requisitionItem.product = it.product
            requisitionItem.productPackage = it.productPackage
            requisitionItem.quantity = it.quantity
            requisitionItem.orderIndex = it.orderIndex
            requisition.addToRequisitionItems(requisitionItem)
        }

        return requisition
    }

    Boolean isRelatedToMe(Integer userId) {
        return (createdBy?.id == userId || updatedBy?.id == userId || requestedBy?.id == userId)
    }

    Map toJson() {
        [
                id: id,
                name: name,
                version: version,
                requestedById: requestedBy?.id,
                requestedByName: requestedBy?.name,
                description: description,
                dateRequested: dateRequested.format("MM/dd/yyyy"),
                requestedDeliveryDate: requestedDeliveryDate.format("MM/dd/yyyy"),
                lastUpdated: lastUpdated?.format("dd/MMM/yyyy hh:mm a"),
                status: status?.name(),
                type: type?.name(),
                originId: origin?.id,
                originName: origin?.name,
                destinationId: destination?.id,
                destinationName: destination?.name,
                recipientProgram: recipientProgram,
                requisitionItems: requisitionItems?.sort()?.collect { it?.toJson() }
        ]
    }
}

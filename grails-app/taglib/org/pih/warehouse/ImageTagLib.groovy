/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse

import com.google.zxing.BarcodeFormat
import org.pih.warehouse.core.Location

class ImageTagLib {

    def grailsApplication

    def displayLogo = { attrs, body ->

        Location location = Location.get(attrs.location)
        def logo = [
                url: grailsApplication.config.openboxes.logo.url,
                label: grailsApplication.config.openboxes.logo.label
        ]

        attrs.location = location
        attrs.logo = logo
        attrs.showLabel = (attrs.showLabel!=null)?attrs.showLabel:true

        out << g.render(template: '/taglib/displayLogo', model: [attrs:attrs]);
    }


    def displayBarcode = { attrs, body ->

        def defaultFormat = grailsApplication.config.openboxes.identifier.barcode.format
        if (!attrs.format && defaultFormat) {
            try {
                attrs.format = BarcodeFormat.valueOf(defaultFormat)
            } catch (Exception e) {
                println ("Unable to locate default barcode format ${defaultFormat}")
            }
        }

        def defaultHeight = grailsApplication.config.openboxes.identifier.barcode.height
        if (!attrs.height && defaultHeight) {
            try {
                attrs.height = Integer.parseInt(defaultHeight)
            } catch (Exception e) {
                println ("Unable to parse default barcode height ${defaultHeight}")
            }
        }

        def defaultWidth = grailsApplication.config.openboxes.identifier.barcode.width
        if (!attrs.width && defaultWidth) {
            try {
                attrs.width = Integer.parseInt(defaultWidth)
            } catch (Exception e) {
                println ("Unable to parse default barcode width ${defaultWidth}")
            }
        }

        attrs.showData = (attrs.showData!=null)?attrs.showData:true


        out << g.render(template: '/taglib/displayBarcode', model: [attrs:attrs])

    }
}

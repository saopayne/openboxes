<%@ page import="org.pih.warehouse.inventory.Transaction" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="custom" />
        
        <title><warehouse:message code="inventory.expiringStock.label"/></title>    
    </head>    

	<body>
		<div class="body">
       		
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<div class="summary">
                <h3><warehouse:message code="inventory.expiringStock.label"/></h3>
            </div>

            <div class="buttons" style="text-align: left">
                <g:render template="./actionsExpiringStock" />
            </div>


            <div class="yui-gf">
				<div class="yui-u first">
		            <g:form action="listExpiringStock" method="get">
						<div class="dialog box">
                            <h2>
                                <warehouse:message code="default.filters.label" default="Filters"/>
                            </h2>
			           		<div class="filter-list-item">
		           				<label><warehouse:message code="category.label"/></label>
				           		<g:select name="category"  class="chzn-select-deselect"
												from="${categories}"
												optionKey="id" optionValue="${{format.category(category:it)}}"
                                                value="${categorySelected?.id}"
												noSelection="['': warehouse.message(code:'default.all.label')]" />
							</div>
							<div class="filter-list-item">
		           				<label><warehouse:message code="inventory.expiresWithin.label"/></label>
				           		<g:select name="threshold" class="chzn-select-deselect"
									from="['7': warehouse.message(code:'default.week.oneWeek.label'),
                                            '14': warehouse.message(code:'default.week.twoWeeks.label'),
										    '30': warehouse.message(code:'default.month.oneMonth.label'),
                                            '60': warehouse.message(code:'default.month.twoMonths.label'),
										    '90': warehouse.message(code:'default.month.threeMonths.label'),
                                            '120': warehouse.message(code:'default.month.fourMonths.label'),
                                            '150': warehouse.message(code:'default.month.fiveMonths.label'),
                                            '180': warehouse.message(code:'default.month.sixMonths.label'),
                                            '210': warehouse.message(code:'default.month.sevenMonths.label'),
                                            '240': warehouse.message(code:'default.month.eightMonths.label'),
                                            '270': warehouse.message(code:'default.month.nineMonths.label'),
                                            '300': warehouse.message(code:'default.month.tenMonths.label'),
                                            '330': warehouse.message(code:'default.month.elevenMonths.label'),
                                            '365': warehouse.message(code:'default.year.oneYear.label'),
                                            '730': warehouse.message(code:'default.year.twoYears.label'),
                                            '1095': warehouse.message(code:'default.year.threeYears.label'),
                                            '1460': warehouse.message(code:'default.year.fourYears.label'),
                                            '1825': warehouse.message(code:'default.year.fiveYears.label')]"
									optionKey="key" optionValue="value" value="${thresholdSelected}" 
									noSelection="['': warehouse.message(code:'default.all.label')]" />   
				           	</div>

				           	<div class="filter-list-item right">
								<button name="filter" class="button icon search">
									<warehouse:message code="default.button.filter.label"/> </button>


                            </div>
							<div class="clear"></div>
						</div>
		            </g:form>  
		   		</div>
		   		<div class="yui-u">
		   		
					<div class="box">
                        <h2>
                            <warehouse:message code="inventoryItems.expiring.label" default="Expiring inventory items"/> (${inventoryItems.size()} <warehouse:message code="default.results.label" default="Results"/>)
                        </h2>
                        <div class="dialog">
                            <form id="inventoryActionForm" name="inventoryActionForm" action="createTransaction" method="POST">
                                <table>
                                    <thead>
                                        <tr class="odd" style="height:50px;">
                                            <th class="center middle">
                                                <input type="checkbox" id="toggleCheckbox" class="middle"/>
                                            </th>
                                            <th><warehouse:message code="product.productCode.label"/></th>
                                            <th><warehouse:message code="product.label"/></th>
                                            <th><warehouse:message code="category.label"/></th>
                                            <th><warehouse:message code="inventory.lotNumber.label"/></th>
                                            <th class="center"><warehouse:message code="inventory.expires.label"/></th>
                                            <th class="center"><warehouse:message code="default.qty.label"/></th>
                                            <th class="center"><warehouse:message code="product.uom.label"/></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <g:set var="counter" value="${0 }" />
                                        <g:each var="inventoryItem" in="${inventoryItems}" status="i">
                                            <g:set var="quantity" value="${0 }"/>
                                            <tr class="${(counter++ % 2) == 0 ? 'even' : 'odd'}">
                                                <td class="center">
                                                    <g:checkBox id="${inventoryItem?.id }" name="inventoryItem.id"
                                                        class="checkbox" style="top:0em;" checked="${false }"
                                                            value="${inventoryItem?.id }" />

                                                </td>
                                                <td class="checkable" >
                                                    <g:link controller="inventoryItem" action="showStockCard" params="['product.id':inventoryItem?.product?.id]">
                                                        ${inventoryItem?.product?.productCode}
                                                    </g:link>

                                                </td>
                                                <td class="checkable" >
                                                    <g:link controller="inventoryItem" action="showStockCard" params="['product.id':inventoryItem?.product?.id]">
                                                        <format:product product="${inventoryItem?.product}"/>
                                                    </g:link>

                                                </td>
                                                <td class="checkable left">
                                                    <span class="fade"><format:category category="${inventoryItem?.product?.category}"/> </span>

                                                </td>
                                                <td class="checkable" >
                                                    <span class="lotNumber">
                                                        ${inventoryItem?.lotNumber }
                                                    </span>
                                                </td>
                                                <td class="checkable center" >
                                                    <span class="fade">
                                                        <g:formatDate date="${inventoryItem?.expirationDate}" format="d MMM yyyy"/>
                                                    </span>
                                                </td>
                                                <td class="checkable center">
                                                    ${quantityMap[inventoryItem]}
                                                </td>
                                                <td class="checkable center" >
                                                    ${inventoryItem?.product?.unitOfMeasure?:"EA" }
                                                </td>
                                            </tr>
                                        </g:each>
                                        <g:unless test="${inventoryItems }">
                                            <tr>
                                                <td colspan="8">
                                                    <div class="padded center fade">
                                                        <warehouse:message code="inventory.noExpiringStock.label" />
                                                    </div>
                                                </td>
                                            </tr>
                                        </g:unless>
                                    </tbody>
                                </table>
                            </form>
                        </div>

					</div>		   		
		   		</div>
		   	</div>   
             
			
		</div>
		<script>
			$(document).ready(function() {
				$(".checkable a").click(function(event) {
					event.stopPropagation();
				});
				$('.checkable').toggle(
					function(event) {
						$(this).parent().find('input').click();
						//$(this).parent().addClass('checked');
						return false;
					},
					function(event) {
						$(this).parent().find('input').click();
						//$(this).parent().removeClass('checked');
						return false;
					}
				);
				
				$("#toggleCheckbox").click(function(event) {
					//$(".checkbox").attr("checked", $(this).attr("checked"));
                    var checked = ($(this).attr("checked") == 'checked');
                    $(".checkbox").attr("checked", checked);
				});	
			});	
		</script>	
		
	</body>

</html>

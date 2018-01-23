<g:if test="${userInstance}">
    <div class="${(userInstance?.active) ? 'active':'inactive'} summary">
        <table>
            <tr>
                <td class="top" width="1%">
                    <g:render template="actions"/>
                </td>
                <td width="1%">
                    <div class="nailthumb-container">
                        <g:userPhoto user="${userInstance}"/>
                    </div>
                </td>
                <td width="10%">
                    <div class="title">
                        <g:link action="edit" id="${userInstance?.id}">
                            ${fieldValue(bean: userInstance, field: "firstName")} ${fieldValue(bean: userInstance, field: "lastName")}
                        </g:link>
                    </div>
                    <%--
                    <span style="font-size: 1.2em">
                        <b>${userInstance?.active ? warehouse.message(code: 'user.active.label') : warehouse.message(code: 'user.inactive.label')}</b>
                        <g:if test="${userInstance?.active}">
                            <img class="photo" src="${resource(dir: 'images/icons/silk', file: 'status_online.png') }"
                                style="vertical-align: bottom;" />
                        </g:if>
                        <g:else>
                            <img class="photo" src="${resource(dir: 'images/icons/silk', file: 'status_offline.png') }"
                                style="vertical-align: bottom;" />
                        </g:else>
                    </span>
                    --%>
                </td>
                <td class="top right">
                    <div class="right">
                        <span class="tag">
                            ${userInstance?.active ? warehouse.message(code:'user.active.label') : warehouse.message(code:'user.inactive.label')}
                        </span>
                    </div>
                </td>
            </tr>
        </table>
    </div>
</g:if>
<g:else>
    <div class="summary">
        <div class="title"><g:message code="users.label"/></div>
    </div>
</g:else>
<div class="button-bar">
    <g:link class="button icon search" action="list" controller="user">
        <warehouse:message code="default.button.list.label" args="[g.message(code: 'users.label')]" />
    </g:link>
    <g:link class="button icon add" action="create" controller="user">
        <warehouse:message code="default.button.create.label" args="[g.message(code: 'user.label')]" />
    </g:link>
    <g:if test="${userInstance}">
        <g:link class="button icon edit" action="edit" controller="user" id="${userInstance?.id}">
            <warehouse:message code="default.button.edit.label" args="[g.message(code: 'user.label')]" />
        </g:link>
    </g:if>
</div>


<script src="${createLinkTo(dir:'js/jquery.nailthumb', file:'jquery.nailthumb.1.1.js')}" type="text/javascript" ></script>
<script>
    $(document).ready(function() {
        $('.nailthumb-container').nailthumb({ width: 24, height: 24 });
    });
</script>
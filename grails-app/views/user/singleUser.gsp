<button class="btn btn-primary" type="button">User <div class="badge badge-success">Bob${Math.random()}</div></button>
<g:if test="${Math.random() > 0.2}">
    <g:link action="permissions" controller="userGroup"><span class="badge">Public</span></g:link>
</g:if>
<g:if test="${Math.random() > 0.2}">
    <g:link action="permissions" controller="userGroup"><span class="badge">Elsik Internal</span></g:link>
</g:if>
<g:if test="${Math.random() > 0.2}">
    <g:link action="permissions" controller="userGroup"><span class="badge">Elsik Collaborator</span></g:link>
</g:if>
<table>
    <thead>
    <tr>
        <th>Sequence</th>
        <th>Admin</th>
        <th>Write</th>
        <th>Export</th>
        <th>Read</th>
    </tr>
    </thead>
    <tbody>
    <g:each in="${(0..<Math.random() * 5.0.intValue())}">
        <tr>
            <td>
                <g:link controller="sequence" action="permissions" id="1">Group1.2</g:link>
            </td>
            <td>
                <g:checkBox name="checkbox" class="checkbox" value="${Math.random() > 0.5}"/>
            </td>
            <td>
                <g:checkBox name="checkbox" class="checkbox" value="${Math.random() > 0.5}"/>
            </td>
            <td>
                <g:checkBox name="checkbox" class="checkbox" value="${Math.random() > 0.5}"/>
            </td>
            <td>
                <g:checkBox name="checkbox" class="checkbox" value="${Math.random() > 0.5}"/>
            </td>
        </tr>
    </g:each>
    </tbody>
</table>

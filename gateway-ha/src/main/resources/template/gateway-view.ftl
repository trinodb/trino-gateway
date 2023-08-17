<#-- @ftlvariable name="" type="com.lyft.data.gateway.resource.GatewayViewResource$GatewayView" -->
<#setting datetime_format = "MM/dd/yyyy hh:mm:ss a '('zzz')'">
    <html>
<head>
    <meta charset="UTF-8"/>
    <style>
        .pull-left {
            float: left !important
        }

        .pull-right {
            float: right !important
        }

        .dataTables_filter input {
            width: 500px
        }

        .active_true {
            background-color: green;
        }

        .active_false {
            background-color: red;
        }

        #availableClusters {
            width: 75%;
            border: 1px solid #ddd;
            text-align: center;
        }
    </style>
    <link rel="stylesheet" type="text/css" href="assets/css/common.css"/>
    <link rel="stylesheet" type="text/css" href="assets/css/jquery.dataTables.min.css"/>

    <script src="assets/js/jquery-3.3.1.js"></script>
    <script src="assets/js/jquery.dataTables.min.js"></script>
    <script src="assets/js/hbar-chart.js"></script>

    <script type="application/javascript">
        $(document).ready(function () {
            $('#queryHistory').DataTable(
                {
                    "ordering": false,
                    "dom": '<"pull-left"f><"pull-right"l>tip',
                    "width": '100%'
                }
            );
            $("ul.chart").hBarChart();
            document.getElementById("active_backends_tab").style.backgroundColor = "grey";
        });
    </script>
</head>
<body>
<#include "header.ftl">
<div>
    Started at :
    <script>document.write(new Date(${gatewayStartTime?long?c}).toLocaleString());</script>
</div>

<div>
    <h3>All backends:</h3>
    <table id="availableClusters" class="display">
        <thead>
        <tr>
            <th>Name</th>
            <th>Url</th>
            <th>Group</th>
            <th>Active</th>
            <#if backendStates?keys?size != 0>
                <th>Queued<th>
                <th>Running<th>
            </#if>
        </tr>
        </thead>
        <tbody>
        <#list backendConfigurations as bc>
            <tr>
                <td>  ${bc.name}</td>
                <td><a href="${bc.externalUrl}/ui" target="_blank">${bc.externalUrl}</a></td>
                <td> ${bc.routingGroup}</td>
                <td class="active_${bc.active?c}"> ${bc.active?c} </td>
                <#if backendStates?keys?size != 0 && backendStates[bc.name]??>
                    <td>${backendStates[bc.name].state["QUEUED"]}<td>
                    <td>${backendStates[bc.name].state["RUNNING"]}<td>
                </#if>
            </tr>
        </#list>
        </tbody>
    </table>
</div>

<#include "footer.ftl">
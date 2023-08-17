<#-- @ftlvariable name="" type="com.lyft.data.gateway.resource.GatewayViewResource$GatewayView" -->
<#setting datetime_format = "MM/dd/yyyy hh:mm:ss a '('zzz')'">
    <html>
<head>
    <meta charset="UTF-8"/>
    <style>
      div {
        margin-bottom: 10px;
      }
      label {
        display: inline-block;
        width: 150px;
        text-align: right;
        font-family: Arial;
      }
      button {
        display: inline-block;
        text-align: right;
        font-family: Arial;
      }
    </style>    </style>
    <link rel="stylesheet" type="text/css" href="assets/css/common.css"/>
    <link rel="stylesheet" type="text/css" href="assets/css/jquery.dataTables.min.css"/>

    <script src="assets/js/jquery-3.3.1.js"></script>
</head>
<body>
    <table style="width:100%">
    <tr>
        <td>
           <h2>Trino Load Balancer</h2>
        </td>
    </tr>
    </table>
    <form action="/login_form" method="post">
        <div>
            <label text-align: left>Username</label>
            <input type="text" placeholder="Enter Username" name="username" required>
        </div>
        <div>
            <label>Password</label>
            <input type="password" placeholder="Enter Password" name="password" required>
        </div>
        <div>
            <label></label>
            <button type="submit">Login</button>
        </div>
    </form>
</body>

<#include "footer.ftl">
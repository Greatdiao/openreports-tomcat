<%@ taglib prefix="s" uri="/struts-tags" %>

<div id="reportFileDialog">

  <div class="hd"><s:text name="addReportFileDialog.title"/></div>
  <div class="bd">  
  
    <form id="reportUploadForm" action="addReportFileDialog.action" method="POST" enctype="multipart/form-data" >
  
      <table class="dialog">
        <tr>      
          <td colspan="2">
            <input type="file" name="reportFile" value="Browse..." size="40"/>&nbsp;            
          </td>
        </tr>   
      </table> 
  
      <input type="hidden" name="command" value="upload">      
  
    </form>  
  
  </div>

</div>





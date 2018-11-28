$(document).ready(function() {

	
	console.log('alias-modal.js: look into current mycore object metadata and all parents to get the full url')
	
	var currentMyCoreId = getUrlParameter('id');
	
	console.log('alias-modal.js: Current edited mycore object is ' +  currentMyCoreId);
	getAliasContext(currentMyCoreId);
	
	
	// helper methods
	function getAliasContext(mycoreid) {
		
		var urlMycoreObj = webApplicationBaseURL + "api/v2/objects/" + mycoreid;
		
		$.ajax({
			url : urlMycoreObj,
	        dataType: "xml",
			type : "GET",
			success : function(data) {
				
				var myCoreObjServFlagNodes = $('servflags',data);
				
				//console.log(data);
				
				return myCoreObjServFlagNodes.find('servflag[type="alias"]').text();
			},
			
			error : function(error) {
				console.log("Failed to get Alias context for " + mycoreid);
				console.log(error);
			}
		});
	}
	
	function getUrlParameter(sParam) {
		var sPageURL = decodeURIComponent(window.location.search.substring(1)), sURLVariables = sPageURL.split('&'), sParameterName, i;

		for (i = 0; i < sURLVariables.length; i++) {
			sParameterName = sURLVariables[i].split('=');

			if (sParameterName[0] === sParam) {
				return sParameterName[1] === undefined ? true : sParameterName[1];
			}
		}
	}
});
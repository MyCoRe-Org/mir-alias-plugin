$(document).ready(function() {

	
	var aliasPath = 'go';
	
	var generatedurl = webApplicationBaseURL + aliasPath + '/';
	var alias = '';
	var currentid = getUrlParameter('id');

	// observe related item
	$('.mir-related-item-search .form-inline').observe('added', 'span:first', function(record) {

		var idparent = $(".mir-related-item-search .form-inline span:first").text();

		if (!isEmpty(idparent)) {

			loadAlias(idparent, false, null);
		}
	});

	$("#mir-generated-url").each(function() {
		$(this).attr("disabled", "true");

		if (!isEmpty(currentid)) {

			loadAlias(currentid, true, null);
		}

	});

	$("#mir-aliaspart").on("change paste keyup", function() {

		$("#mir-generated-url").attr("value", generatedurl + $("#mir-aliaspart").val());
	});

	function loadAlias(mycoreid, isinitial, initialAlias) {
		$.ajax({
			url : webApplicationBaseURL + "servlets/solr/select?q=id:" + mycoreid + "&XSL.Style=xml",
			type : "GET",
			success : function(data) {

				alias = $(data).find("str[name='alias']").text() + '/';

				if (!isEmpty(alias)) {

					if (!isinitial) {
						generatedurl = webApplicationBaseURL + aliasPath + '/' + alias
					} else {

						if (initialAlias == null) {

							var parentid = $(data).find("str[name='parent']").text();

						    if (parentid && parentid.length > 0) {

						    	loadAlias(parentid, true, alias);
	                        }
						    
						} else {

							// we are in initial state, we have the parent Alias
							// we can get the part with help of diff

							var aliaspart = initialAlias.replace(alias, '');

							if (aliaspart.charAt(aliaspart.length - 1) == '/') {

								aliaspart = aliaspart.substr(0, aliaspart.length - 1);
							}
							
							generatedurl = webApplicationBaseURL + aliasPath + '/' + alias;

							$("#mir-aliaspart").attr("value", aliaspart);
						}
					}

					if (isEmpty($("#mir-aliaspart").val())) {

						$("#mir-generated-url").attr("value", generatedurl);
					} else {

						$("#mir-generated-url").attr("value", generatedurl + $("#mir-aliaspart").val());

					}

				}
			},
			error : function(error) {
				console.log("Failed to load alias for " + mycoreid);
				console.log(error);
			}
		});
	}

	// javascript helper methods

	function isEmpty(value) {
		return typeof value == 'string' && !value.trim() || typeof value == 'undefined' || value === null;
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
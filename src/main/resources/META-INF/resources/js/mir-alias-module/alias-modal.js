$(document).ready(function() {
    console.log('alias-modal.js: look into current mycore object metadata and all parents to get the full url')

    var currentMyCoreId = getUrlParameter('id');

    console.log('alias-modal.js: Current edited mycore object is ' + currentMyCoreId);

    var mycoreIds = []
    mycoreIds.push(currentMyCoreId);

    var aliasContext = getAliasContext(mycoreIds);

    // helper methods
    function getAliasContext(mycoreIds) {

        mycoreIds.forEach(function(mycoreId) {
            $.ajax({
                url : webApplicationBaseURL + "api/v2/objects/" + mycoreId,
                dataType : "xml",
                type : "GET",
                success : function(data) {

                    var alias = $(data).find('servflag[type="alias"]').text();
                    var relatedItems = [];

                    $(data).find('mods\\:relatedItem').each(function() {
                        relatedItems.push($(this).attr('xlink:href'));
                    });

                    return {
                        mycoreId : mycoreId,
                        alias : alias,
                        relatedItems : (relatedItems.length > 0 ? getAliasContext(relatedItems) : undefined)
                    };

                },

                error : function(error) {
                    console.log("Failed to get Alias context for " + mycoreid);
                    console.log(error);
                }
            });
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
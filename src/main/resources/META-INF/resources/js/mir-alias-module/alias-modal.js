$(document).ready(function() {
    console.log('alias-modal.js: look into current mycore object metadata and all parents to get the full url')

    var currentMyCoreId = getUrlParameter('id');
    console.log('alias-modal.js: Current edited mycore object is ' + currentMyCoreId);

    var mycoreIds = []
    mycoreIds.push(currentMyCoreId);

    /*
     * Use promises for the different alias level requests
     */
    var promisesAliasResolve = [];

    var aliasContextTree = new AliasContextTree(currentMyCoreId)

    getAliasContext(mycoreIds, 0);

    function requestMCRObjectMetadata(mycoreId) {

        return $.ajax({
            url : webApplicationBaseURL + "api/v2/objects/" + mycoreId,
            dataType : "xml",
            type : "GET"
        });
    }

    function getAliasContext(mycoreIds, parentTreeLevel) {

        $.each(mycoreIds, function(index, mycoreId) {

            requestMCRObjectMetadata(mycoreId).then(function(data) {

                var alias = $(data).find('servflag[type="alias"]').text();
                var relatedItems = [];

                $(data).find('mods\\:relatedItem').each(function() {
                    relatedItems.push($(this).attr('xlink:href'));
                });

                console.log('alias-modal.js: Build alias Context for mycore id: ' + mycoreId);
                console.log('alias-modal.js: {mycoreId : ' + mycoreId + ', alias : ' + alias + ', relatedItems : {' + relatedItems + '}');

                if (parentTreeLevel == 0) {
                    aliasContextTree.addChild(alias);
                } else {
                    aliasContextTree.children[parentTreeLevel - 1].addChild(alias);
                }

                getAliasContext(relatedItems, parentTreeLevel + 1);
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

    function simpletest() {
        console.log(aliasContextTree);
    }
    setTimeout(simpletest, 5000);

    function AliasContextTree(value) {
        this.value = value;
        this.children = [];
    }

    AliasContextTree.prototype.addChild = function(value) {
        var child = new AliasContextTree(value);
        this.children.push(child);
        return child;
    };

    AliasContextTree.prototype.contains = function(value) {
        if (this.value === value)
            return true;
        for (var i = 0; i < this.children.length; i++) {
            if (this.children[i].contains(value))
                return true;
        }
        return false;
    };

});
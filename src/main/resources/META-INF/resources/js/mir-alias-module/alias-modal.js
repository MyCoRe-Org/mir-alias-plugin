$(document).ready(function () {
    console.log('alias-modal.js: look into current mycore object metadata and all parents to get the full url')

    var currentMyCoreId = getUrlParameter('id');
    console.log('alias-modal.js: Current edited mycore object is ' + currentMyCoreId);

    var mycoreIds = []
    mycoreIds.push(currentMyCoreId);

    /*
     * Use promises for the different alias level requests
     */
    var promisesAliasResolve = [];

    let aliasTree = new AliasTree();

    getAliasContext(mycoreIds, 'root', aliasTree);

    function requestMCRObjectMetadata(mycoreId) {

        return $.ajax({
            url: webApplicationBaseURL + "api/v2/objects/" + mycoreId,
            dataType: "xml",
            type: "GET"
        });
    }

    function getAliasContext(mycoreIds, parent, aliasTree) {

        $.each(mycoreIds, (index, mycoreId) => {

            requestMCRObjectMetadata(mycoreId).then((data) => {

                var alias = $(data).find('servflag[type="alias"]').text();
                var relatedItems = [];

                $(data).find('mods\\:relatedItem').each(function () {
                    relatedItems.push($(this).attr('xlink:href'));
                });

                console.log('alias-modal.js: Build alias Context for mycore id: ' + mycoreId);
                console.log('alias-modal.js: {mycoreId : ' + mycoreId + ', alias : ' + alias + ', relatedItems : {' + relatedItems + '}');

                aliasTree.add(alias, parent);

                getAliasContext(relatedItems, alias, aliasTree);
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
        console.log(aliasTree);
    }

    setTimeout(simpletest, 5000);
});

class AliasTree {
    constructor() {
        this._root = null;
    }

    _traverse(callback) {
        function walk(node) {
            callback(node);
            node.children.forEach(walk);
        }

        walk(this._root);
    }

    add(value, parentValue) {
        var newNode = {
            value,
            children: []
        };

        if (null === this._root) {
            this._root = newNode;
            return;
        }

        this._traverse(function (node) {
            if (parentValue === node.value) {
                node.children.push(newNode);
            }
        });
    }

    remove(value) {
        this._traverse(function (node) {
            node.children.some(function (childNode, index) {
                if (value === childNode.value) {
                    return !!node.children.splice(index, 1);
                }
            });
        });
    }

    search(value) {
        let exists = false;

        this._traverse(function (node) {
            if (value === node.value) {
                exists = true;
            }
        });

        return exists;
    }

}
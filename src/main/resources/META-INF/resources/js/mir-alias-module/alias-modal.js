$(document).ready(function () {


    /*
     * Use promises for the different alias level requests
     */
    var promisesAliasResolve = [];

    /*
     * Start to get alias (if exists) from the current document (it will be the
     * root in alias tree)
     */
    let aliasCurrentDocument = '';
    if (!isEmpty($("#mir-aliaspart").val())) {
        
        aliasCurrentDocument = $("#mir-aliaspart").val();
        let aliasTree = new AliasTree();
        aliasTree.add(aliasCurrentDocument, 'root');
        
        
        console.log('alias-modal.js: look into related items dependency to get the full url!')

        var relatedItemIds = [];

        $.each($('.mir-related-item-search .form-inline span'), (index, element) => {

            relatedItemIds.push(element.textContent);
            
        });
        
        console.log('alias-modal.js: related Item mycore Ids are ' + relatedItemIds);
        getAliasContext(relatedItemIds, aliasCurrentDocument, aliasTree);
        
        function simpletest() {
            console.log(aliasTree);
        }

        setTimeout(simpletest, 5000); 
    }
    
    // observe related item
    $('.mir-related-item-search .form-inline').observe('added', 'span', function(change) {

        console.log('alias-modal.js: There was added a new related item. Refresh the alias tree.')
    });


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

                $(data).find('mods\\:mods > mods\\:relatedItem').each(function () {
                    relatedItems.push($(this).attr('xlink:href'));
                });

                console.log('alias-modal.js: Build alias Context for mycore id: ' + mycoreId);
                console.log('alias-modal.js: {mycoreId : ' + mycoreId + ', alias : ' + alias + ', relatedItems : {' + relatedItems + '}');

                aliasTree.add(alias, parent);

                getAliasContext(relatedItems, alias, aliasTree);
            });
        });
    }
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